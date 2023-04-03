/* eslint-disable jsx-a11y/media-has-caption */

import React, { useRef, useEffect, useState } from 'react';
import ReactModal from 'react-modal';

import { useDispatch, useSelector } from 'react-redux';
import CameraRoundedIcon from '@mui/icons-material/CameraRounded';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import CachedIcon from '@mui/icons-material/Cached';
import { setImg } from '../../store/camera/slice';
import { CulturalPropertyState } from '../../store/culturalproperty/slice';
import { AppState } from '../../store';

export default function Camera() {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [front, setFront] = useState<boolean>(false);

  const [imageCapture, setImageCapture] = useState<ImageCapture>();
  const value = useSelector<AppState, CulturalPropertyState['value']>(
    (state) => state.culturalProperty.value,
  );
  const pose = value?.result.culturalProperty.pose;
  const cultural = value?.result.culturalProperty;
  const dispatch = useDispatch();
  const setPlay = () => {
    videoRef.current?.play();
  };
  const setCamera = () => {
    setFront((prev) => !prev);
  };
  const [modalIsOpen, setModalIsOpen] = useState(true);
  const handleClose = () => {
    setModalIsOpen(false);
  };
  const navigate = useNavigate();
  useEffect(() => {
    const facing = front ? 'user' : 'environment';
    navigator.mediaDevices
      .getUserMedia({
        video: {
          width: { min: 480, ideal: 640, max: 1920 },
          height: { min: 320, ideal: 400 },
          aspectRatio: 1.777777778,
          frameRate: { max: 15 },
          facingMode: facing,
        },
      })
      .then(function (stream) {
        if (!videoRef.current) return;

        videoRef.current.srcObject = stream;
        videoRef.current.play();

        const track = stream.getVideoTracks()[0];
        setImageCapture(new ImageCapture(track));
      })
      .catch(function (err) {
        console.log(`An error occurred: ${err}`);
      });
  }, [front]);
  function onTakePhotoButtonClick() {
    imageCapture
      ?.takePhoto()
      .then((blob: Blob) => {
        createImageBitmap(blob);
        // const file = new File([blob], 'test2.jpg');
        dispatch(setImg(blob));

        navigate('/camera/after', {
          state: {
            culturalId: cultural?.culturalPropertyId,
            poseId: pose?.poseId,
          },
        });
      })
      .catch((error: Error) => console.error(error));
  }

  return (
    <div>
      <video ref={videoRef} />
      <div className="flex justify-center gap-10 m-10">
        <button type="button" onClick={setPlay}>
          play
        </button>
        <CachedIcon fontSize="large" onClick={setCamera} />

        <CameraRoundedIcon fontSize="large" onClick={onTakePhotoButtonClick} />
        <InfoOutlinedIcon
          fontSize="large"
          onClick={() => {
            setModalIsOpen(true);
          }}
        />
      </div>

      {modalIsOpen && (
        <ReactModal
          style={{
            overlay: { backgroundColor: 'transperent' },
            content: {
              border: '0px',
              background: 'transperent',
            },
          }}
          isOpen={modalIsOpen}
          onRequestClose={handleClose}
        >
          <img src="/pose/manse.png" alt={pose?.poseName} />
          {/* <img src={pose?.posePicture} alt={pose?.poseName} /> */}
        </ReactModal>
      )}
    </div>
  );
}

// export default function Camera() {
//   return <div>Camera</div>;
// }
