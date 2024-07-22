
// import React, { useState, useRef } from "react";
// import GoogleMapReact from 'google-map-react';
// import { Button } from 'react-bootstrap';

// export default function Map({
//     setBounds
// }) {
//     const defaultProps = {
//         center: {
//             lat: 59.95,
//             lng: 30.33
//         },
//         zoom: 5
//     };

//     const [draggable, setDraggable] = useState(true);
//     const rectangleRef = useRef(null);

//     let myMap;

//     const handleGoogleMapApi = ({ map, maps }) => {
//         myMap = map;
//         let moveListener;

//         console.log("draggable", draggable);

//         const clearRectangle = () => {
//             if (rectangleRef.current) {
//                 rectangleRef.current.setMap(null);
//                 rectangleRef.current = null;
//             }
//         };

//         const drawing = (e) => {
//             if (!map.enableDrawing) {
//                 return;
//             }
//             let finalBounds = null;
//             clearRectangle();

//             if (map.isMoving) {
//                 console.log(`second click.`);
//                 map.isMoving = false;
//                 maps.event.removeListener(moveListener);
//                 setDraggable(true);
//                 return;
//             }

//             map.isMoving = true;

//             const initialBounds = {
//                 north: e.latLng.lat(),
//                 south: e.latLng.lat(),
//                 east: e.latLng.lng(),
//                 west: e.latLng.lng()
//             };

//             const newRectangle = new maps.Rectangle({
//                 bounds: initialBounds,
//                 fillColor: '#FF0000',
//                 fillOpacity: 0.35,
//                 strokeColor: '#FF0000',
//                 strokeWeight: 2,
//                 map: map,
//             });

//             rectangleRef.current = newRectangle;

//             if (newRectangle) {
//                 newRectangle.addListener('mouseup', () => {
//                     map.isMoving = false;
//                     map.enableDrawing = false;
//                     maps.event.removeListener(moveListener);
//                     setDraggable(true);
//                     const ne = finalBounds?.getNorthEast();
//                     const sw = finalBounds?.getSouthWest();
//                     setBounds({
//                         north: ne?.lat(),
//                         south: sw?.lat(),
//                         east: ne?.lng(),
//                         west: sw?.lng()
//                     });
//                 });
//             }

//             setDraggable(false);

//             const moveHandler = (e) => {
//                 if (!map.isMoving) return;
//                 if (!rectangleRef.current) return;
//                 const currentBounds = rectangleRef.current.getBounds();
//                 currentBounds.extend({
//                     lat: e.latLng.lat(),
//                     lng: e.latLng.lng(),
//                 });
//                 finalBounds = currentBounds;
//                 rectangleRef.current.setBounds(currentBounds);
//             };
//             moveListener = maps.event.addListener(map, 'mousemove', moveHandler);
//         }

//         maps.event.addListener(map, 'mousedown', drawing);
//     };

//     return (
//         <div className="container-fluid" style={{ position: "relative", height: '400px', width: '100%'}}>

//             <GoogleMapReact
//                 bootstrapURLKeys={{ key: "AIzaSyCJ9DkZBMfMVJFsGxHN9ntIqXfD6GZd1tk", language: 'en', }}
//                 defaultCenter={defaultProps.center}
//                 defaultZoom={defaultProps.zoom}
//                 draggable={draggable}
//                 onGoogleApiLoaded={handleGoogleMapApi}
//             >               
//                 <Button
//                     lat="59.95"
//                     lng="30.33"

//                     style={{
//                         position: 'absolute',
//                         top: 0,
//                         left: 12,
//                         zIndex: 1
//                     }}
//                     onClick={() => myMap.enableDrawing = true}
//                 >
//                     Draw
//                 </Button>
//             </GoogleMapReact>

//         </div>
//     );
// }

import React, { useState, useRef, useContext, useEffect } from 'react';
import GoogleMapReact from 'google-map-react';
import { Button } from 'react-bootstrap';
import BrutalismButton from './BrutalismButton';
import ThemeContext from '../ThemeColorProvider';

const MapComponent = ({
    center,
    zoom = 10,
    bounds,
    setBounds,
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
                        left: 50,
                        right: 50,
                        top: 50,
                        bottom: 50
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
    console.log("Key:", key);

    return (
        <div style={{ height: '400px', width: '100%' }}>
            <BrutalismButton
                onClick={() => {
                    toggleDrawing();
                    setIsDrawing(!isDrawing);
                }}
                backgroundColor={theme.primaryColors.primary700}
                borderColor={theme.primaryColors.primary700}
                color='white'
                style={{ position: 'absolute', zIndex: 2, width: "100px" }}
                disabled={isDrawing}
            >
                {drawingRef.current ? 'Drawing' : 'Draw'}
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
