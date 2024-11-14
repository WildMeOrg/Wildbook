import React, { useState, useRef, useContext } from "react";
import GoogleMapReact from "google-map-react";
import BrutalismButton from "./BrutalismButton";
import ThemeContext from "../ThemeColorProvider";
import { FormattedMessage } from "react-intl";
import useGetSiteSettings from "../models/useGetSiteSettings";

const MapComponent = ({ center, zoom = 10, setBounds, setTempBounds = () => {} }) => {
  const theme = useContext(ThemeContext);
  const key = useGetSiteSettings()?.data?.googleMapsKey;

  const [rectangle, setRectangle] = useState(null);
  const drawingRef = useRef(false);
  const [isDrawing, setIsDrawing] = useState(false);

  const handleApiLoaded = (map, maps) => {
    let rect = new maps.Rectangle({
      strokeColor: "#FF0000",
      strokeOpacity: 0.8,
      strokeWeight: 2,
      fillColor: "#FF0000",
      fillOpacity: 0.35,
    });

    setRectangle(rect);

    maps.event.addListener(map, "mousedown", (e) => {
      if (drawingRef.current) {
        const initialBounds = {
          north: e.latLng.lat(),
          south: e.latLng.lat(),
          east: e.latLng.lng(),
          west: e.latLng.lng(),
        };
        rect.setMap(map);
        rect.setBounds(initialBounds);
        map.setOptions({ draggable: false });

        const mouseMoveHandler = (ev) => {
          const updatedBounds = {
            north: Math.max(initialBounds.north, ev.latLng.lat()),
            south: Math.min(initialBounds.south, ev.latLng.lat()),
            east: Math.max(initialBounds.east, ev.latLng.lng()),
            west: Math.min(initialBounds.west, ev.latLng.lng()),
          };
          rect.setBounds(updatedBounds);
        };
        const moveListener = maps.event.addListener(
          map,
          "mousemove",
          mouseMoveHandler,
        );

        const mouseUpHandler = () => {
          drawingRef.current = false;
          setIsDrawing(false);
          map.setOptions({ draggable: true });
          maps.event.removeListener(moveListener);
          setBounds(rect.getBounds().toJSON());
          map.fitBounds(rect.getBounds(), {
            left: 30,
            right: 30,
            top: 30,
            bottom: 30,
          });
        };
        document.addEventListener("mouseup", mouseUpHandler, { once: true });
      }
    });
  };

  const toggleDrawing = () => {
    if (rectangle) {
      rectangle.setMap(null);
    }
    drawingRef.current = !drawingRef.current;
  };

  return (
    <div style={{ height: "400px", width: "100%" }}>
      <BrutalismButton
        onClick={() => {
          toggleDrawing();
          setIsDrawing(!isDrawing);
          setBounds(null);
          setTempBounds(null);
        }}
        noArrow
        backgroundColor={theme.primaryColors.primary700}
        borderColor={theme.primaryColors.primary700}
        color="white"
        style={{
          position: "absolute",
          zIndex: 2,
          width: "100px",
          marginLeft: "10px",
        }}
      >
        {drawingRef.current ? (
          <FormattedMessage id="CANCEL" />
        ) : (
          <FormattedMessage id="DRAW" />
        )}
      </BrutalismButton>
      {key ? (
        <GoogleMapReact
          key={key}
          bootstrapURLKeys={{ key: key }}
          defaultCenter={center}
          defaultZoom={zoom}
          yesIWantToUseGoogleMapApiInternals
          onGoogleApiLoaded={({ map, maps }) => handleApiLoaded(map, maps)}
        />
      ) : (
        <div
          className="d-flex justify-content-center align-items-center text-center w-100 h-100"
          style={{
            fontSize: "24px",
          }}
        >
          <FormattedMessage id="MAP_IS_LOADING" />
        </div>
      )}
    </div>
  );
};

export default MapComponent;
