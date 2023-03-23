package com.example.androidstudio

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settingBtnWebview()
        settingBtnGame()
        settingBtnGps()
        settingBtnTest()
    }

    fun settingBtnWebview() {
        val button = findViewById<Button>(R.id.webview)
        button.setOnClickListener{
            val intent = Intent(this, WebviewContainer::class.java)
            startActivity(intent)
        }
    }

    fun settingBtnGame() {
        val button = findViewById<Button>(R.id.game)
        button.setOnClickListener{
            val intent = Intent(this, Game::class.java)
            startActivity(intent)
        }
    }

    fun settingBtnGps() {
        val button = findViewById<Button>(R.id.gps)
        button.setOnClickListener{
            val intent = Intent(this, GPSContainer::class.java)
            startActivity(intent)
        }
    }

    fun settingBtnTest() {
        val button = findViewById<Button>(R.id.test)
        button.setOnClickListener{
            val intent = Intent(this, Test::class.java)
            startActivity(intent)
        }
    }
}