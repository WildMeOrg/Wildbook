import React, { useEffect, useRef, useState, useMemo } from "react";
import { observer } from "mobx-react-lite";
import { Loader } from "@googlemaps/js-api-loader";
import useGetSiteSettings from "../../../models/useGetSiteSettings";
import { MarkerClusterer } from "@googlemaps/markerclusterer";
import { FormattedMessage } from "react-intl";
import FullScreenLoader from "../../../components/FullScreenLoader";

const PALETTE = ["#99D7FF", "#00A70B", "#0B619E"];

export const MapView = observer(({ store }) => {
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
  const [mode, setMode] = useState("species");

  const pill = (title) => (
    <span
      style={{
        fontSize: 12,
        background: "#2e8ebaff",
        padding: "5px 8px",
        borderRadius: 12,
        marginRight: 10,
        marginBottom: 8,
        marginTop: 8,
        display: "inline-block",
        cursor: "pointer",
        color: "#ffffff",
      }}
      onClick={() => {
        setMode(title.toLowerCase());
      }}
    >
      {title}
    </span>
  );

  useEffect(() => {
    if (!store?.searchResultsAll?.length) return;
    const arr = store?.searchResultsAll
      .filter((result) => {
        const gp = result?.occurrenceLocationGeoPoint;
        return gp && !isNaN(parseFloat(gp.lat)) && !isNaN(parseFloat(gp.lon));
      })
      .map((result) => ({
        lat: parseFloat(result.occurrenceLocationGeoPoint.lat),
        lon: parseFloat(result.occurrenceLocationGeoPoint.lon),
        title: result?.id || "",
        sex: result?.sex || "unknown",
        species: result?.taxonomy || "unknown",
      }));

    setLocationData(arr);
  }, [store?.searchResultsAll]);

  const categoryColorMap = useMemo(() => {
    if (mode === "position") return new Map();
    const map = new Map();
    let i = 0;
    const keyOf = (p) =>
      mode === "sex" ? (p.sex ?? "unknown") : (p.species ?? "unknown");
    for (const p of locationData) {
      const k = keyOf(p);
      if (!map.has(k)) {
        map.set(k, PALETTE[i % PALETTE.length]);
        i++;
      }
    }
    return map;
  }, [mode, locationData]);

  const colorFor = (p) => {
    if (mode === "position") return "#f40c0cff";
    const k = mode === "sex" ? (p.sex ?? "unknown") : (p.species ?? "unknown");
    return categoryColorMap.get(k) || "#1a73e8";
  };

  useEffect(() => {
    if (!mapKey) return;
    const loader = new Loader({ apiKey: mapKey });
    loader
      .load()
      .then(() => {
        const googleMap = new window.google.maps.Map(mapRef.current, {
          center: { lat: mapCenterLat, lng: mapCenterLon },
          zoom: mapZoom,
          gestureHandling: "greedy",
          mapTypeControl: false,
          streetViewControl: false,
          fullscreenControl: false,
        });
        setMap(googleMap);
      })
      .catch((e) => console.error("Error loading Google Maps", e));
  }, [mapCenterLat, mapCenterLon, mapZoom, mapKey]);

  useEffect(() => {
    if (!map) return;

    if (clustererRef.current) {
      clustererRef.current.clearMarkers();
      clustererRef.current = null;
    }

    if (markersRef.current.length) {
      markersRef.current.forEach((m) => m.setMap && m.setMap(null));
      markersRef.current = [];
    }

    if (!locationData.length) return;

    const markers = locationData.map((p) => {
      const isColorMode = mode !== "position";
      const icon = isColorMode
        ? {
            path: window.google.maps.SymbolPath.CIRCLE,
            scale: 6,
            fillColor: colorFor(p),
            fillOpacity: 1,
            strokeWeight: 1,
            strokeColor: "#ffffff",
          }
        : undefined;

      const marker = new window.google.maps.Marker({
        position: { lat: p.lat, lng: p.lon },
        title: p.title || "",
        ...(icon ? { icon } : {}),
      });

      if (isColorMode) marker.setMap(map);
      return marker;
    });

    markersRef.current = markers;

    if (mode === "position") {
      clustererRef.current = new MarkerClusterer({ map, markers });
    }
  }, [map, locationData, mode, categoryColorMap]);

  const legendItems = useMemo(() => {
    if (mode === "position") return [];
    return Array.from(categoryColorMap.entries()).map(([label, color]) => ({
      label,
      color,
    }));
  }, [mode, categoryColorMap]);

  return (
    <div className="d-flex flex-row mt-1" style={{ position: "relative" }}>
      {store.loadingAll && <FullScreenLoader />}
      <div
        className="mt-2 me-4"
        style={{
          width: "1200px",
          height: 800,
          borderRadius: 15,
          overflow: "hidden",
          position: "relative",
        }}
      >
        <div ref={mapRef} style={{ width: "100%", height: "100%" }} />
      </div>
      <div className="mt-2">
        {
          <div
            style={{
              width: 300,
              background: "rgba(255,255,255,0.9)",
              borderRadius: 8,
              padding: "8px 10px",
            }}
          >
            <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 6 }}>
              {<FormattedMessage id="INDEX" />}
              <br />
              {pill("Position")}
              {pill("Sex")}
              {pill("Species")}
            </div>
            <div
              className="d-flex flex-column"
              style={{ display: "flex", gap: 10 }}
            >
              {legendItems.map((it) => (
                <div
                  key={it.label}
                  style={{ display: "flex", alignItems: "center", gap: 6 }}
                >
                  <div
                    style={{
                      display: "block",
                      width: 12,
                      height: 12,
                      borderRadius: "50%",
                      background: it.color,
                      border: "1px solid rgba(0,0,0,0.2)",
                    }}
                  />
                  <div style={{ fontSize: 12 }}>{String(it.label)}</div>
                </div>
              ))}
            </div>
          </div>
        }
      </div>
    </div>
  );
});
