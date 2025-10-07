import React from 'react';
import { observer } from 'mobx-react-lite';
import { Divider } from '../../components/Divider';

export const TrackingReview = observer(({ store = {} }) => {
    const metalTags = store.encounterData?.metalTags || [];
    return (
        <div>
            {store.metalTagsEnabled && <>
                <p>Metal Tags</p>
                {
                    store.metalTagLocation && store.metalTagLocation.length > 0 &&
                    store.metalTagLocation.map((loc, index) => {
                        return (
                            <div key={index}>
                                <p>{loc}: {metalTags.find(data => data.location === loc)?.number} </p>
                            </div>
                        );
                    })
                }
                <Divider />
                </>}

            {store.acousticTagEnabled && <>
                <p>Acoustic Tags</p>
                <p>Serial Number: {store.encounterData?.acousticTag?.serialNumber}</p>
                <p>ID: {store.encounterData?.acousticTag?.idNumber}</p>
                <Divider />
            </>}
            {store.satelliteTagEnabled && <>

            <p>Satellite Tags</p>
            <p>Name: {store.encounterData?.satelliteTag?.name}</p>
            <p>ID: {store.encounterData?.satelliteTag?.serialNumber}</p>
            <p>Argos PTT: {store.encounterData?.satelliteTag?.argosPttNumber}</p>
            <Divider />
            </>}
        </div>)
})
