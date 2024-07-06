// import React, { useState } from "react";
// import GoogleMapReact from 'google-map-react';


// const AnyReactComponent = ({ text }) => (
//     <div className={`text-white bg-secondary p-2 d-inline-flex text-center align-items-center justify-content-center rounded-circle`} style={{
//         transform: 'translate(-50%, -50%)'
//     }}>
//         {text}
//     </div>
// );

// export default function SimpleMap() {
//     const defaultProps = {
//         center: {
//             lat: 59.95, lng: 30.33
//         },
//         zoom: 11
//     };

//     const [draggable, setDraggable] = React.useState(true);
//     const [drawing, setDrawing] = React.useState(false);

//     const [bounds, setBounds] = useState(null);
//     const [mapApi, setMapApi] = useState(null);
//     const [mapInstance, setMapInstance] = useState(null);

//     const handleGoogleMapApi = ({ map, maps }) => {
//         setMapApi(maps);
//         setMapInstance(map);
//         map.addListener('mousedown', (e) => {
//             console.log('Mouse down at:', e.latLng.lat(), e.latLng.lng());
//         });

//         map.addListener('mousemove', (e) => {
//             if (!drawing) return;

//             //draw rectangle

//                 });
//         map.addListener('mouseup', (e) => {
//             console.log('handleMouseUp');
//             setDraggable(true);
//             if (!drawing) return;
//             setDrawing(false);
//             // stop drawing
//         }
//         );
//     };

//     return (
//         <div className="container-fluid" style={{ height: '400px', width: '800px' }}>
//             <GoogleMapReact
//                 bootstrapURLKeys={{ key: "AIzaSyCJ9DkZBMfMVJFsGxHN9ntIqXfD6GZd1tk", language: 'en', }}
//                 defaultCenter={defaultProps.center}
//                 defaultZoom={defaultProps.zoom}
//                 draggable={draggable}
//                 onGoogleApiLoaded={handleGoogleMapApi}
//                 onChange={() => {
//                     console.log('onChange');
//                 }}
//             >
//                 <div
//                     lat={59.955413}
//                     lng={30.337844}
//                     onClick={() => {
//                         setDrawing(true);
//                         setDraggable(false);
//                     }}
//                 >
//                     <h1>Click here</h1>
//                 </div>

//                 <AnyReactComponent
//                     lat={59.955413}
//                     lng={30.337844}
//                     text="My Marker"
//                 />
//             </GoogleMapReact>
//         </div>
//     );
// }

// import React, { useState } from "react";
// import GoogleMapReact from 'google-map-react';

// const AnyReactComponent = ({ text }) => (
//     <div className={`text-white bg-secondary p-2 d-inline-flex text-center align-items-center justify-content-center rounded-circle`} style={{
//         transform: 'translate(-50%, -50%)'
//     }}>
//         {text}
//     </div>
// );

// export default function SimpleMap() {
//     const defaultProps = {
//         center: {
//             lat: 59.95, lng: 30.33
//         },
//         zoom: 11
//     };

//     const [draggable, setDraggable] = useState(true);
//     const [drawing, setDrawing] = useState(false);


//     const [bounds, setBounds ]  = useState({
//         north: 59.95,
//         south: 69.95,
//         east: 30.33,
//         west: 40.33
//     });

//     const handleGoogleMapApi = ({ map, maps }) => {
//         let rectangle = null;

//         map.addListener('mousedown', (e) => {
//             console.log("drawing");
//             setBounds({
//                 north: e.latLng.lat(),
//                 // south: e.latLng.lat(),
//                 // east: e.latLng.lng(),
//                 west: e.latLng.lng()
//             });

//             rectangle = new maps.Rectangle({
//                 bounds: bounds,
//                 fillColor: '#FF0000',
//                 fillOpacity: 0.35,
//                 strokeColor: '#FF0000',
//                 strokeWeight: 2,
//                 map: map,
//                 editable: true
//             });

//             setDrawing(true);
//             setDraggable(false);

//             map.addListener('mousemove', (e) => {
//                 // console.log("drawing....moving");
//                 if (!drawing || !rectangle) return;

//                 // let newBounds = rectangle.getBounds();
//                 // newBounds.extend(e.latLng);
//                 // rectangle.setBounds({
//                 //     north: e.latLng.lat(),
//                 //     south: e.latLng.lat(),
//                 //     east: e.latLng.lng(),
//                 //     west: e.latLng.lng()
//                 // });

//                 setBounds({
//                     // north: e.latLng.lat(),
//                     south: e.latLng.lat(),
//                     east: e.latLng.lng(),
//                     // west: e.latLng.lng()
//                 });
//             });

//             map.addListener('mouseup', () => {
//                 if (rectangle) {
//                     console.log('Rectangle bounds:', rectangle.getBounds().toUrlValue());
//                 }
//                 setDrawing(false);
//                 setDraggable(true);
//             });
//         });
//     };        

//     return (
//         <div className="container-fluid" style={{ height: '400px', width: '800px' }}>
//             <GoogleMapReact
//                 bootstrapURLKeys={{ key: "AIzaSyCJ9DkZBMfMVJFsGxHN9ntIqXfD6GZd1tk", language: 'en', }}
//                 defaultCenter={defaultProps.center}
//                 defaultZoom={defaultProps.zoom}
//                 draggable={draggable}
//                 onGoogleApiLoaded={handleGoogleMapApi}
//                 onChange={() => {
//                     console.log('onChange');
//                 }}
//             >

//                 <AnyReactComponent
//                     lat={59.955413}
//                     lng={30.337844}
//                     text="Click here to draw"
//                     onClick={() => setDrawing(true)}
//                 />
//             </GoogleMapReact>
//         </div>
//     );
// }


// import React, { useState } from "react";
// import GoogleMapReact from 'google-map-react';

// const AnyReactComponent = ({ text, onClick }) => (
//     <div className={`text-white bg-secondary p-2 d-inline-flex text-center align-items-center justify-content-center rounded-circle`} style={{
//         transform: 'translate(-50%, -50%)'
//     }} onClick={onClick}>
//         {text}
//     </div>
// );

// export default function SimpleMap() {
//     const defaultProps = {
//         center: {
//             lat: 59.95,
//             lng: 30.33
//         },
//         zoom: 11
//     };

//     const [draggable, setDraggable] = useState(true);
//     const [rectangle, setRectangle] = useState(null);

//     const handleGoogleMapApi = ({ map, maps }) => {

//         let moveListener;
//         const drawing = (e) => {

//             if (rectangle) {
//                 rectangle.setMap(null);
//                 setRectangle(null);
//             }

//             if(map.isMoving) {
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
//                 editable: true,
//                 draggable: false
//             });

//             if(newRectangle) {
//                 console.log('rectangle is not null');
//                 newRectangle.addListener('mouseup', () => {
//                     console.log('mouseup2');
//                     map.isMoving = false;
//                     maps.event.removeListener(moveListener);
//                     setDraggable(true);
//                 }
//                 );
//             }

//             setRectangle(newRectangle);
//             setDraggable(false);

//             const moveHandler = (e) => {
//                 console.log(`mousemove, drawing=${map.isMoving}; newRectangle=${JSON.stringify(newRectangle.getBounds())}`);
//                 if(!map.isMoving) return;
//                 if (!newRectangle) return;
//                 const currentBounds = newRectangle.getBounds();
//                 console.log(`e.latLng=${JSON.stringify(e.latLng)}`);                
//                 currentBounds.extend({
//                     lat: e.latLng.lat(),
//                     lng: e.latLng.lng(),

//                 });
//                 console.log(`currentBounds=${JSON.stringify(currentBounds)}`);
//                 newRectangle.setBounds(currentBounds);
//             };
//             moveListener = maps.event.addListener(map, 'mousemove', moveHandler);
//         }

//         maps.event.addListener(map, 'mousedown', drawing);

//     };

//     return (
//         <div className="container-fluid" style={{ height: '400px', width: '800px' }}>
//             <GoogleMapReact
//                 bootstrapURLKeys={{ key: "", language: 'en', }}
//                 defaultCenter={defaultProps.center}
//                 defaultZoom={defaultProps.zoom}
//                 draggable={draggable}
//                 onGoogleApiLoaded={handleGoogleMapApi}
//             >
//                 <AnyReactComponent
//                     lat={59.955413}
//                     lng={30.337844}
//                     text="Click here to draw"
//                     onClick={() => console.log('Component Clicked')}
//                 />
//             </GoogleMapReact>
//         </div>
//     );
// }

import React, { useState, useRef } from "react";
import GoogleMapReact from 'google-map-react';

const AnyReactComponent = ({ text, onClick }) => (
    <div className={`text-white bg-secondary p-2 d-inline-flex text-center align-items-center justify-content-center rounded-circle`} style={{
        transform: 'translate(-50%, -50%)'
    }} onClick={onClick}>
        {text}
    </div>
);

export default function SimpleMap() {
    const defaultProps = {
        center: {
            lat: 59.95,
            lng: 30.33
        },
        zoom: 11
    };
    const [bounds, setBounds] = useState(null);

    const [draggable, setDraggable] = useState(true);
    const rectangleRef = useRef(null);
    console.log('bounds:', bounds?.south);

    const handleGoogleMapApi = ({ map, maps }) => {
        let moveListener;

        const clearRectangle = () => {
            if (rectangleRef.current) {
                rectangleRef.current.setMap(null);
                rectangleRef.current = null;
            }
        };

        const drawing = (e) => {
            let finalBounds = null;
            clearRectangle();

            if (map.isMoving) {
                console.log(`second click.`);
                map.isMoving = false;
                maps.event.removeListener(moveListener);
                setDraggable(true);
                return;
            }

            map.isMoving = true;

            const initialBounds = {
                north: e.latLng.lat(),
                south: e.latLng.lat(),
                east: e.latLng.lng(),
                west: e.latLng.lng()
            };

            const newRectangle = new maps.Rectangle({
                bounds: initialBounds,
                fillColor: '#FF0000',
                fillOpacity: 0.35,
                strokeColor: '#FF0000',
                strokeWeight: 2,
                map: map,
                editable: true,
                draggable: false
            });

            rectangleRef.current = newRectangle;

            if (newRectangle) {
                console.log('rectangle is not null');
                newRectangle.addListener('mouseup', () => {
                    console.log('mouseup2');
                    map.isMoving = false;
                    maps.event.removeListener(moveListener);
                    setDraggable(true);
                    const ne = finalBounds?.getNorthEast();
                    const sw = finalBounds?.getSouthWest();
                    setBounds({
                        north: ne?.lat(),
                        south: sw?.lat(),
                        east: ne?.lng(),
                        west: sw?.lng()
                    });
                });
            }

            setDraggable(false);

            const moveHandler = (e) => {
                // console.log(`mousemove, drawing=${map.isMoving}; newRectangle=${JSON.stringify(rectangleRef.current.getBounds())}`);
                if (!map.isMoving) return;
                if (!rectangleRef.current) return;
                const currentBounds = rectangleRef.current.getBounds();
                currentBounds.extend({
                    lat: e.latLng.lat(),
                    lng: e.latLng.lng(),
                });
                finalBounds = currentBounds;
                // console.log(`currentBounds=${JSON.stringify(currentBounds)}`);
                rectangleRef.current.setBounds(currentBounds);
            };
            moveListener = maps.event.addListener(map, 'mousemove', moveHandler);
        }

        maps.event.addListener(map, 'mousedown', drawing);
    };

    return (
        <div className="container-fluid" style={{ height: '400px', width: '800px' }}>
            <GoogleMapReact
                bootstrapURLKeys={{ key: "", language: 'en', }}
                defaultCenter={defaultProps.center}
                defaultZoom={defaultProps.zoom}
                draggable={draggable}
                onGoogleApiLoaded={handleGoogleMapApi}
            >
                <AnyReactComponent
                    lat={59.955413}
                    lng={30.337844}
                    text="Click here to draw"
                    onClick={() => console.log('Component Clicked')}
                />
            </GoogleMapReact>
        </div>
    );
}