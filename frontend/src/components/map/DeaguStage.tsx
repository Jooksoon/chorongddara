/* eslint-disable */
import { useState, useEffect } from 'react';
import { MapContainer, GeoJSON, GeoJSONProps, Marker } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import Deagugeojson from './Deagu.json';
import { Feature, Geometry, GeoJsonObject } from 'geojson';
import L, { StyleFunction, Icon, LatLngTuple } from 'leaflet';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { getMapData } from '../../api/mapApi';
import { StageProps, MapResult, RegionProperties } from '../../types/map';
import { IoIosArrowBack } from '@react-icons/all-files/io/IoIosArrowBack';

export default function DeaguStage(props: StageProps) {
  const { mapDatas } = props;
  // 지역별 스타일 지정
  const regionStyle: StyleFunction<any> = (
    feature: Feature<Geometry, RegionProperties> | undefined,
  ) => {
    return {
      fillColor: 'white',
      weight: 0,
      opacity: 0,
      color: 'blue',
      fillOpacity: 1,
    };
  };
  const navigate = useNavigate();

  // 뒤로가기
  const goStage = () => {
    navigate('/stage/');
  };

  // 마커 클릭시 페이지 이동 ->
  return (
    <div>
      <MapContainer
        center={[35.83, 128.57]}
        zoom={9.7}
        minZoom={9.7}
        maxZoom={9.7}
        zoomControl={false}
        dragging={false}
        doubleClickZoom={false}
        style={{
          height: '50vh',
          width: '100vw',
          backgroundColor: '#F5F5F5',
        }}
      >
        <IoIosArrowBack
          className="absolute w-[5vh] h-[5vh]"
          style={{ top: '2vh', left: '4vw', color: '#ffcdf3' }}
          onClick={goStage}
        />
        <GeoJSON
          data={Deagugeojson as unknown as GeoJsonObject}
          style={regionStyle}
        />
        {mapDatas?.map((mapData, index) => {
          const customIcon = new Icon({
            iconUrl: `${mapData.pinImage}`,
            iconSize: [40, 40],
          });

          return (
            <Marker
              key={index}
              position={[mapData.latitude, mapData.longitude] as LatLngTuple}
              icon={customIcon}
              eventHandlers={{
                click: () => {
                  navigate(
                    `/culturalpropertydetail/${mapData.culturalPropertyId}`,
                  );
                },
              }}
            />
          );
        })}
      </MapContainer>
    </div>
  );
}
