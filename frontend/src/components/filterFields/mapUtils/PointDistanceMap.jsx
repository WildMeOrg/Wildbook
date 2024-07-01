import React, { useEffect, useRef, useState } from 'react';
import { get } from 'lodash-es';
import GoogleMapReact from 'google-map-react';
// import useGoogleMapsApiKey from '../../../hooks/useGoogleMapsApiKey';

let lastMarker = null;
let lastCircle = null;

/* component expects distance in kilometers */
export default function PointDistanceMap({
  distance = 50,
  latitude,
  longitude,
  gps,
  openCount,
  onChange,
  ...rest
}) {

  
  const [mapObject, setMapObject] = useState(null);
  const [mapsApi, setMapsApi] = useState(null);
  const mapsApiRef = useRef(null);
  mapsApiRef.current = mapsApi;
  const mapObjectRef = useRef(null);
  mapObjectRef.current = mapsApi;



  function draw(lat, lng) {
    if (lastMarker) lastMarker.setMap(null); // remove old marker
    if (lastCircle) lastCircle.setMap(null); // remove old circle

    const safeMapsApi = mapsApi || mapsApiRef.current;
    const safeMapObject = mapObject || mapObjectRef.current;

    if (!safeMapsApi || !safeMapObject) return;

    const markerPosition = new safeMapsApi.LatLng(lat, lng);

    const circle = new safeMapsApi.Circle({
      center: markerPosition,
      radius: 1000 * distance,
      strokeColor: '#000000',
      strokeWeight: 2,
      strokeOpacity: 0.8,
      fillColor: '#000000',
      fillOpacity: 0.35,
    });

    circle.setMap(safeMapObject);

    const marker = new safeMapsApi.Marker({
      position: markerPosition,
    });
    marker.setMap(safeMapObject);

    lastMarker = marker;
    lastCircle = circle;
  }

  const mapReadyToRender = mapsApi && mapObject;

  useEffect(() => {
    if (lastCircle) {
      lastCircle.setOptions({
        radius: 1000 * distance,
      });
    }
  }, [distance]);

  useEffect(() => {
    const lat = get(gps, '0');
    const lng = get(gps, '1');
    if (lat && lng) draw(parseFloat(lat), parseFloat(lng));
  }, [gps]);

  useEffect(() => {
    if (latitude && longitude) {
      draw(parseFloat(latitude), parseFloat(longitude));
    }
  }, [mapReadyToRender]);

//   const googleMapsApiKey = useGoogleMapsApiKey();
  const googleMapsApiKey = "AIzaSyCJ9DkZBMfMVJFsGxHN9ntIqXfD6GZd1tk";

  return (
    <div
      style={{
        height: 400,
        width: 500,
        maxWidth: '100%',
        maxHeight: '50vh',
      }}
    >
      <GoogleMapReact
        bootstrapURLKeys={{ key: googleMapsApiKey }}
        defaultCenter={{
          lat: 0,
          lng: 0,
        }}
        defaultZoom={1.3}
        options={{ minZoom: 1 }}
        onClick={({ lat, lng }) => {
          onChange({ lat, lng });
          draw(lat, lng);
        }}
        yesIWantToUseGoogleMapApiInternals
        onGoogleApiLoaded={({ map, maps }) => {
          setMapObject(map);
          setMapsApi(maps);
        }}
        {...rest}
      />
    </div>
  );
}
