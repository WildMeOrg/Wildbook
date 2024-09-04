import React, { useState } from "react";
import GoogleMapReact from "google-map-react";
// import useGoogleMapsApiKey from '../../../../hooks/useGoogleMapsApiKey';

let lastMarker = null;

export default function LatLngMap({ rest }) {
  const [mapObject, setMapObject] = useState(null);
  const [mapsApi, setMapsApi] = useState(null);
  //   const googleMapsApiKey = useGoogleMapsApiKey();

  return (
    <div className="w-100 h-100">
      <GoogleMapReact
        bootstrapURLKeys={{ key: "AIzaSyCJ9DkZBMfMVJFsGxHN9ntIqXfD6GZd1tk" }}
        defaultCenter={{
          lat: 0,
          lng: 0,
        }}
        defaultZoom={1.3}
        options={{ minZoom: 1 }}
        onClick={({ lat, lng }) => {
          //   onChange([lat, lng]);
          if (lastMarker) lastMarker.setMap(null); // remove old marker

          const markerPosition = new mapsApi.LatLng(lat, lng);
          const marker = new mapsApi.Marker({
            position: markerPosition,
          });
          marker.setMap(mapObject);

          lastMarker = marker;
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
