/* eslint-disable */
import { useState, useEffect } from 'react';
import { MapContainer, GeoJSON, GeoJSONProps, Marker } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import gwangjugeojson from './Gwangju.json'; // 한국 지도 GeoJSON 데이터 파일
import { Feature, Geometry, GeoJsonObject } from 'geojson';
import L, { StyleFunction, Icon, LatLngTuple } from 'leaflet';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { getMapData } from '../../api/mapApi';
import {
  GwangjustageProps,
  MapResult,
  RegionProperties,
} from '../../types/map';

export default function Gwangjustage(props: GwangjustageProps) {
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

  // 마커 클릭시 페이지 이동 ->
  const navigate = useNavigate();
  return (
    <div>
      <MapContainer
        center={[35.16, 126.85]}
        zoom={10}
        minZoom={10}
        maxZoom={10}
        zoomControl={false}
        dragging={false}
        doubleClickZoom={false}
        style={{ height: '50vh', width: '100vw' }}
      >
        <GeoJSON
          data={gwangjugeojson as unknown as GeoJsonObject}
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
