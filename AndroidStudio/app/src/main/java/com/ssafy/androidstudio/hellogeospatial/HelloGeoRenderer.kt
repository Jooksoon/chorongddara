/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ssafy.androidstudio.hellogeospatial;

import android.icu.util.Calendar
import android.location.Location
import android.opengl.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.ar.core.Anchor
import com.google.ar.core.TrackingState
import com.ssafy.androidstudio.hellogeospatial.helpers.SuccessfulFragment
import com.ssafy.androidstudio.common.helpers.DisplayRotationHelper
import com.ssafy.androidstudio.common.helpers.TrackingStateHelper
import com.ssafy.androidstudio.common.samplerender.*
import com.ssafy.androidstudio.common.samplerender.arcore.BackgroundRenderer
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.ssafy.androidstudio.MainActivity
import java.io.IOException
import java.util.*
import com.ssafy.androidstudio.R



class HelloGeoRenderer(val activity: HelloGeoActivity, val accessToken: String?, val culturalProperty: String?) :
  SampleRender.Renderer, DefaultLifecycleObserver {
  //<editor-fold desc="ARCore initialization" defaultstate="collapsed">
  companion object {
    val TAG = "HelloGeoRenderer"

    private val Z_NEAR = 0.1f
    private val Z_FAR = 1000f
  }

  lateinit var backgroundRenderer: BackgroundRenderer
  lateinit var virtualSceneFramebuffer: Framebuffer
  var hasSetTextureNames = false

  // Virtual object (ARCore pawn)
  lateinit var virtualObjectMesh: Mesh
  lateinit var virtualObjectShader: Shader
  lateinit var virtualObjectTexture: Texture

  // Temporary matrix allocated here to reduce number of allocations for each frame.
  val modelMatrix = FloatArray(16)
  val viewMatrix = FloatArray(16)
  val projectionMatrix = FloatArray(16)
  val modelViewMatrix = FloatArray(16) // view x model

  val modelViewProjectionMatrix = FloatArray(16) // projection x view x model

  val session
    get() = activity.arCoreSessionHelper.session

  val displayRotationHelper = DisplayRotationHelper(activity)
  val trackingStateHelper = TrackingStateHelper(activity)

  override fun onResume(owner: LifecycleOwner) {
    displayRotationHelper.onResume()
    hasSetTextureNames = false
  }

  override fun onPause(owner: LifecycleOwner) {
    displayRotationHelper.onPause()
  }

  override fun onSurfaceCreated(render: SampleRender) {
    // Prepare the rendering objects.
    // This involves reading shaders and 3D model files, so may throw an IOException.
    try {
      backgroundRenderer = BackgroundRenderer(render)
      virtualSceneFramebuffer = Framebuffer(render, /*width=*/ 1, /*height=*/ 1)

      // Virtual object to render (Geospatial Marker)
      virtualObjectTexture =
        Texture.createFromAsset(
          render,
          "models/star.png",
          Texture.WrapMode.CLAMP_TO_EDGE,
          Texture.ColorFormat.SRGB
        )

      virtualObjectMesh = Mesh.createFromAsset(render, "models/stars_v2.obj")
      virtualObjectShader =
        Shader.createFromAssets(
          render,
          "shaders/ar_unlit_object.vert",
          "shaders/ar_unlit_object.frag",
          /*defines=*/ null)
          .setTexture("u_Texture", virtualObjectTexture)

      backgroundRenderer.setUseDepthVisualization(render, false)
      backgroundRenderer.setUseOcclusion(render, false)
    } catch (e: IOException) {
      Log.e(TAG, "Failed to read a required asset file", e)
      showError("Failed to read a required asset file: $e")
    }
  }

  override fun onSurfaceChanged(render: SampleRender, width: Int, height: Int) {
    displayRotationHelper.onSurfaceChanged(width, height)
    virtualSceneFramebuffer.resize(width, height)
  }
  //</editor-fold>

  // OpenGL ES에서 지정된 렌더링 프레임워크에서 호출되는 메서드로, 각 프레임마다 화면에 새로운 프레임을 그리기 위해 호출
  override fun onDrawFrame(render: SampleRender) {
    val session = session ?: return

    //<editor-fold desc="ARCore frame boilerplate" defaultstate="collapsed">
    // Texture names should only be set once on a GL thread unless they change. This is done during
    // onDrawFrame rather than onSurfaceCreated since the session is not guaranteed to have been
    // initialized during the execution of onSurfaceCreated.
    if (!hasSetTextureNames) {
      session.setCameraTextureNames(intArrayOf(backgroundRenderer.cameraColorTexture.textureId))
      hasSetTextureNames = true
    }

    // -- Update per-frame state

    // Notify ARCore session that the view size changed so that the perspective matrix and
    // the video background can be properly adjusted.
    displayRotationHelper.updateSessionIfNeeded(session)

    // Obtain the current frame from ARSession. When the configuration is set to
    // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
    // camera framerate.
    val frame =
      try {
        session.update()
      } catch (e: CameraNotAvailableException) {
        Log.e(TAG, "Camera not available during onDrawFrame", e)
        showError("Camera not available. Try restarting the app.")
        return
      }

    val camera = frame.camera

    // BackgroundRenderer.updateDisplayGeometry must be called every frame to update the coordinates
    // used to draw the background camera image.
    backgroundRenderer.updateDisplayGeometry(frame)

    // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
    trackingStateHelper.updateKeepScreenOnFlag(camera.trackingState)

    // -- Draw background
    if (frame.timestamp != 0L) {
      // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
      // drawing possible leftover data from previous sessions if the texture is reused.
      backgroundRenderer.drawBackground(render)
    }

    // If not tracking, don't draw 3D objects.
    if (camera.trackingState == TrackingState.PAUSED) {
      return
    }

    // Get projection matrix.
    camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR)

    // Get camera matrix and draw.
    camera.getViewMatrix(viewMatrix, 0)

    render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f)
    //</editor-fold>

    // TODO: Obtain Geospatial information and display it on the map.
    for (item in earthAnchors){
      item.let {
        render.renderCompassAtAnchor(it)
      }
    }
    val earth = session.earth
    if (earth?.trackingState == TrackingState.TRACKING) {
      // TODO: the Earth object may be used here.
      val cameraGeospatialPose = earth.cameraGeospatialPose
      activity.view.mapView?.updateMapPosition(
        latitude = cameraGeospatialPose.latitude,
        longitude = cameraGeospatialPose.longitude,
        heading = cameraGeospatialPose.heading
      )
      val handler = Handler(Looper.getMainLooper())
      handler.post {
        var nearestAnchorIndex: Pair<Int?,Float?> = Pair(null,null)
        val distances = arrayListOf<Float>()
        markers.forEachIndexed{index, item ->
          val itemLoc = Location("itemLoc")
          val userLoc = Location("userLoc")
          itemLoc.latitude = item.position.latitude
          itemLoc.longitude = item.position.longitude
          userLoc.latitude = cameraGeospatialPose.latitude
          userLoc.longitude = cameraGeospatialPose.longitude
          val dist = itemLoc.distanceTo(userLoc)
          distances.add(dist)
          if(dist < (nearestAnchorIndex.second ?: Float.MAX_VALUE)){
            nearestAnchorIndex = Pair(index,dist)
          }
        }
        val collectButton: Button = activity.findViewById(R.id.collect_button)
        val consoleSwitch: Switch = activity.findViewById(R.id.console_switch)
        val statusText: TextView = activity.findViewById(R.id.statusText)
        val nearestDist = nearestAnchorIndex.second
        if (nearestDist != null && nearestDist < 25) {
          collectButton.visibility = View.VISIBLE
          collectButton.setOnClickListener() {
            collectButton.visibility = View.INVISIBLE
            val bagTextView: TextView = activity.findViewById(R.id.bag_textview)
            val currentBag = bagTextView.text.toString()
            val bagNum = currentBag.first().toString().toInt().inc()
            val newBagNum = "$bagNum/3"
            bagTextView.text = newBagNum
            nearestAnchorIndex.first?.let { it1 -> collectStars(it1) }
          }
        }
        else {
          collectButton.visibility = View.INVISIBLE
        }
        if (started) {
          if(consoleSwitch.isChecked){
            statusText.visibility = View.VISIBLE
            activity.view.updateStatusText(earth,cameraGeospatialPose,distances)
          }else{
            statusText.visibility = View.INVISIBLE
          }
          val currentPos = LatLng(cameraGeospatialPose.latitude,cameraGeospatialPose.longitude)
          if(steps.isEmpty()){
            steps.add(currentPos)
            activity.view.mapView?.drawRoute(steps)
          }
          else if (!currentPos.equals(steps.last())) {
            steps.add(currentPos)
            activity.view.mapView?.drawRoute(steps)
          }
        }
      }
    }


    // Compose the virtual scene with the background.
    backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR)
  }

  var earthAnchors: ArrayList<Anchor> = arrayListOf()
  var markers: ArrayList<Marker> = arrayListOf()
  var steps: ArrayList<LatLng> = arrayListOf()
  var started: Boolean = false
  lateinit var startTime: Date


  fun startRundom(meterRadius: Int) {
    steps = arrayListOf()
    started = true
    val c = Calendar.getInstance()
    startTime = c.time
    val earth = session?.earth ?: return
    if (earth.trackingState != TrackingState.TRACKING) {
      return
    }
    val startButton: Button = activity.findViewById(R.id.start_button)
    val bagTextView: TextView = activity.findViewById(R.id.bag_textview)
    startButton.visibility = View.INVISIBLE
    bagTextView.visibility = View.VISIBLE
    val result = activity.view.mapView?.generateStars(earth, meterRadius, culturalProperty) ?: Pair(arrayListOf(),arrayListOf())
    earthAnchors = result.first
    markers = result.second
  }

  private fun millisecondsToHMS(milliSeconds: Long): String {
    val s: Long = milliSeconds / 1000 % 60

    val m: Long = milliSeconds / (1000*60) % 60

    val h: Long = milliSeconds / (1000*60*60) % 24

    return String.format("%02d:%02d:%02d", h, m, s)
  }
  private fun getTotalDistance(coords: ArrayList<LatLng>): Double {
    var distance = 0.0
    for (i in 0..(coords.size - 2)) {
      val currentCoord = Location("currentCoord")
      val nextCoord = Location("nextCoord")
      currentCoord.latitude = coords[i].latitude
      currentCoord.longitude = coords[i].longitude
      nextCoord.latitude = coords[i+1].latitude
      nextCoord.longitude = coords[i+1].longitude
      val dist = currentCoord.distanceTo(nextCoord)
      distance += dist
    }
    return distance
  }

   fun collectStars(index: Int){
     earthAnchors[index].detach()
     markers[index].remove()
     earthAnchors.removeAt(index)
     markers.removeAt(index)
     if(markers.size == 0){
       val c = Calendar.getInstance()
       val bagTextView: TextView = activity.findViewById(R.id.bag_textview)
       val endTime = c.time
       val diff = (endTime.time - startTime.time)
       val elapsedTime = millisecondsToHMS(diff)
       val elapsedDist = String.format("%.2f", getTotalDistance(steps))
       val bundle = Bundle()
       bundle.putString("time", elapsedTime)
       bundle.putString("distance", elapsedDist)
       val fragInfo = SuccessfulFragment(accessToken, culturalProperty)
       fragInfo.isCancelable = false
       fragInfo.arguments = bundle
       fragInfo.show(activity.supportFragmentManager, "success-dialog")
       bagTextView.text = "0/3"
       bagTextView.visibility = View.INVISIBLE
       val startButton: Button = activity.findViewById(R.id.start_button)
       startButton.visibility = View.VISIBLE
       started = false
       activity.view.mapView?.clearRoute()
     }
   }

  private fun SampleRender.renderCompassAtAnchor(anchor: Anchor) {
    // Get the current pose of the Anchor in world space. The Anchor pose is updated
    // during calls to session.update() as ARCore refines its estimate of the world.
    // ARCore anchor의 pose 정보를 행렬로 변환하여 modelMatrix 배열에 저장
    anchor.pose.toMatrix(modelMatrix, 0)

    // Calculate model/view/projection matrices
    // 안드로이드에서 제공하는 4x4 행렬 계산 메서드이다. 이 메서드는 입력된 두 개의 4x4 행렬을 곱하여 결과 행렬
    Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
    Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

    // Update shader properties and draw
    // shader 모델의 좌표, 색상 및 조명 정보를 입력으로 받아 화면에 렌더링할 수 있는 이미지를 출력
    // 메쉬는 표면의 모양, 텍스처 등을 결정
    // OpenGL ES에서 사용되는 쉐이더 프로그램
    virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix)
    draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer)
  }

  private fun showError(errorMessage: String) =
    activity.view.snackbarHelper.showError(activity, errorMessage)
}
