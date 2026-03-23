import React, { useEffect, useRef, useContext, useMemo } from "react";
import { observer } from "mobx-react-lite";
import { Loader } from "@googlemaps/js-api-loader";
import useGetSiteSettings from "../../../models/useGetSiteSettings";
import ThemeColorContext from "../../../ThemeColorProvider";
import { FormattedMessage } from "react-intl";

const EncountersMapView = observer(({ store }) => {
  const mapElRef = useRef(null);
  const { data: siteSettings } = useGetSiteSettings();
  const theme = useContext(ThemeColorContext);
  const apiKey = siteSettings?.googleMapsKey;

  const defaultCenter = useMemo(
    () => ({
      lat: siteSettings?.mapCenterLat || 0,
      lng: siteSettings?.mapCenterLon || 0,
    }),
    [siteSettings?.mapCenterLat, siteSettings?.mapCenterLon],
  );

  useEffect(() => {
    if (!apiKey || !mapElRef.current) return;

    const loader = new Loader({ apiKey });
    const locations = store.encounterLocations;

    loader.load().then(() => {
      const el = mapElRef.current;
      if (!el || !el.isConnected) return;

      // Calculate bounds if we have locations
      const bounds = new window.google.maps.LatLngBounds();
      locations.forEach((loc) => {
        bounds.extend({ lat: loc.lat, lng: loc.lon });
      });

      const map = new window.google.maps.Map(el, {
        center: locations.length > 0 ? bounds.getCenter() : defaultCenter,
        zoom: locations.length > 0 ? 4 : 2,
        disableDefaultUI: false,
        mapTypeControl: false,
        streetViewControl: false,
        fullscreenControl: true,
      });

      // Add markers for each encounter location
      locations.forEach((loc, index) => {
        const marker = new window.google.maps.Marker({
          position: { lat: loc.lat, lng: loc.lon },
          map,
          title: `${loc.date || `Encounter ${index + 1}`}${
            loc.locationName ? ` - ${loc.locationName}` : ""
          }`,
          icon: {
            path: window.google.maps.SymbolPath.CIRCLE,
            scale: 8,
            fillColor: "#FFB800",
            fillOpacity: 0.9,
            strokeColor: "#997300",
            strokeWeight: 2,
          },
        });

        // Add info window
        const infoWindow = new window.google.maps.InfoWindow({
          content: `
            <div style="padding: 8px;">
              <strong>${loc.date || "Unknown date"}</strong><br/>
              ${loc.locationName || "Unknown location"}<br/>
              <a href="/react/encounter?number=${loc.id}" target="_blank">View Encounter</a>
            </div>
          `,
        });

        marker.addListener("click", () => {
          infoWindow.open(map, marker);
        });
      });

      // Draw path between locations if we have multiple
      if (locations.length > 1) {
        const pathCoordinates = locations.map((loc) => ({
          lat: loc.lat,
          lng: loc.lon,
        }));

        new window.google.maps.Polyline({
          path: pathCoordinates,
          geodesic: true,
          strokeColor: "#D92635",
          strokeOpacity: 0.8,
          strokeWeight: 2,
          icons: [
            {
              icon: {
                path: window.google.maps.SymbolPath.FORWARD_CLOSED_ARROW,
                scale: 3,
                strokeColor: "#D92635",
              },
              offset: "100%",
              repeat: "50px",
            },
          ],
          map,
        });
      }

      // Fit map to bounds if we have locations
      if (locations.length > 0) {
        map.fitBounds(bounds, { padding: 50 });
      }
    });
  }, [apiKey, store.encounterLocations, defaultCenter]);

  if (store.encountersLoading) {
    return (
      <div
        className="d-flex justify-content-center align-items-center"
        style={{ minHeight: "400px" }}
      >
        <div className="spinner-border text-primary" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  if (store.encounterLocations.length === 0) {
    return (
      <div
        className="d-flex flex-column justify-content-center align-items-center"
        style={{
          minHeight: "400px",
          borderRadius: "10px",
          backgroundColor: theme.grayColors.gray50,
        }}
      >
        <i
          className="bi bi-map"
          style={{ fontSize: "48px", color: theme.grayColors.gray300 }}
        />
        <p className="text-muted mt-3">
          <FormattedMessage
            id="NO_LOCATION_DATA"
            defaultMessage="No location data available"
          />
        </p>
      </div>
    );
  }

  return (
    <div
      style={{
        width: "100%",
        height: "500px",
        borderRadius: "10px",
        overflow: "hidden",
        boxShadow: "0px 0px 10px rgba(0, 0, 0, 0.2)",
      }}
    >
      <div ref={mapElRef} style={{ width: "100%", height: "100%" }} />
    </div>
  );
});

export default EncountersMapView;
