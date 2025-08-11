import React, { useEffect, useRef, useState } from "react";
import { FormattedMessage } from "react-intl";
import { observer } from "mobx-react-lite";
import { Loader } from "@googlemaps/js-api-loader";
import useGetSiteSettings from "../../../models/useGetSiteSettings"
import { MarkerClusterer } from "@googlemaps/markerclusterer";

export const MappedResults = observer(({ store }) => {
  const { data } = useGetSiteSettings();
  const mapCenterLat = data?.mapCenterLat || 51;
  const mapCenterLon = data?.mapCenterLon || 7;
  const mapZoom = data?.mapZoom || 4;
  const mapKey = data?.googleMapsKey || "";
  const mapRef = useRef(null);
  const [map, setMap] = useState(null);
  const markersRef = useRef([]);
  const clustererRef = useRef(null);
  const [locationData, setLocationData] = useState([]);

  useEffect(() => {
    if (store?.searchResultsAll) {
      const locData = store?.searchResultsAll?.filter(
        (result) => result?.occurrenceLocationGeoPoint && result?.occurrenceLocationGeoPoint.lat && result?.occurrenceLocationGeoPoint.lon
      )
        .map((result) => ({
          lat: result?.occurrenceLocationGeoPoint?.lat,
          lon: result?.occurrenceLocationGeoPoint?.lon,
          title: result?.id || "",
        })) || [];
      setLocationData(locData);
    }
  }, [store?.searchResultsAll]);

  useEffect(() => {
    if (!mapKey) {
      return;
    }
    const loader = new Loader({
      apiKey: mapKey,
    });

    loader
      .load()
      .then(() => {
        const googleMap = new window.google.maps.Map(mapRef.current, {
          center: { lat: mapCenterLat, lng: mapCenterLon },
          zoom: mapZoom,
        });
        setMap(googleMap);
      })
      .catch((error) => {
        console.error("Error loading Google Maps", error);
      });
  }, [mapCenterLat, mapCenterLon, mapZoom, mapKey]);

  useEffect(() => {
    if (!map) {
      return;
    }

    if (clustererRef.current) {
      clustererRef.current.clearMarkers();
      clustererRef.current = null;
    }

    if (markersRef.current.length > 0) {
      markersRef.current.forEach(marker => marker.setMap(null));
      markersRef.current = [];
    }

    const points = locationData.filter(p => p && !isNaN(p.lat) && !isNaN(p.lon));
    if (points.length === 0) {
      return;
    }

    markersRef.current = points.map((point) => new window.google.maps.Marker({
      position: { lat: parseFloat(point.lat), lng: parseFloat(point.lon) },
      title: point.title || "",
    }));

    clustererRef.current = new MarkerClusterer({
      map,
      markers: markersRef.current,
      // imagePath: "https://developers.google.com/maps/documentation/javascript/examples/markerclusterer/m",
    });
  }, [map, locationData]);

  return (
    <div>
      <div
        className="mt-4"
        style={{
          width: "100%",
          height: "800px",
          borderRadius: "15px",
          overflow: "hidden",
        }}
      >
        <div ref={mapRef} style={{ width: "100%", height: "100%" }}></div>
      </div>
    </div>
  );
});
