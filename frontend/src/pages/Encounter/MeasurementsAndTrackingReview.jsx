import React from 'react';
import { observer } from 'mobx-react-lite';
import { Col, Container } from 'react-bootstrap';

export const MeasurementsAndTrackingReview = observer(({ store = {} }) => {
    return (
        <div>
            <p>Metal Tags</p>
            <p>Left: {store.encounterData?.metalTags[0]["left"]?.number}</p>
            <p>Right: {store.encounterData?.metalTags[0]["right"]?.number}</p>
            <div style={{
                width: '100%',
                height: "10px",
                borderBottom: '1px solid #ccc',
            }}></div>
            <p>Acoustic Tags</p>
            <p>Serial Number: {store.encounterData?.acousticTag?.serialNumber}</p>
            <p>ID: {store.encounterData?.acousticTag?.idNumber}</p>
            <div style={{
                width: '100%',
                height: "10px",
                borderBottom: '1px solid #ccc',
            }}></div>
            <p>Satellite Tags</p>
            <p>Name: {store.encounterData?.satelliteTag?.name}</p>
            <p>ID: {store.encounterData?.satelliteTag?.serialNumber}</p>
            <p>Argos PTT: {store.encounterData?.satelliteTag?.argosPttNumber}</p>

            <div style={{
                width: '100%',
                height: "10px",
                borderBottom: '1px solid #ccc',
            }}></div>
        </div>)
})
