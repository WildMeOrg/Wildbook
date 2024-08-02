
import React, { useState, useRef, useContext, useEffect } from 'react';
import GoogleMapReact from 'google-map-react';
import BrutalismButton from './BrutalismButton';
import ThemeContext from '../ThemeColorProvider';
import { set } from 'lodash-es';
import { FormattedMessage } from 'react-intl';

const MapComponent = ({
    center,
    zoom = 10,
    bounds,
    setBounds,
    setTempBounds,
}) => {
    const theme = useContext(ThemeContext);

    const [rectangle, setRectangle] = useState(null);
    const drawingRef = useRef(false);
    const [isDrawing, setIsDrawing] = useState(false);

    const handleApiLoaded = (map, maps) => {
        let rect = new maps.Rectangle({
            strokeColor: '#FF0000',
            strokeOpacity: 0.8,
            strokeWeight: 2,
            fillColor: '#FF0000',
            fillOpacity: 0.35,
        });

        setRectangle(rect);

        maps.event.addListener(map, 'mousedown', (e) => {
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
                const moveListener = maps.event.addListener(map, 'mousemove', mouseMoveHandler);

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
                        bottom: 30
                    });
                };
                document.addEventListener('mouseup', mouseUpHandler, { once: true });
            }
        });
    };

    const toggleDrawing = () => {
        if (rectangle) {
            rectangle.setMap(null);
        }
        drawingRef.current = !drawingRef.current;
    };

    const key = window?.wildbookGlobals?.gtmKey || "";

    return (
        <div style={{ height: '400px', width: '100%' }}>
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
                color='white'
                style={{ position: 'absolute', zIndex: 2, width: "100px", marginLeft: "10px" }}
            >
                {drawingRef.current ? <FormattedMessage id="CANCEL"/> : <FormattedMessage id="DRAW"/>}
            </BrutalismButton>
            <GoogleMapReact
                bootstrapURLKeys={{ key: key }}
                defaultCenter={center}
                defaultZoom={zoom}
                yesIWantToUseGoogleMapApiInternals
                onGoogleApiLoaded={({ map, maps }) => handleApiLoaded(map, maps)}
            />
        </div>
    );
};

export default MapComponent;
