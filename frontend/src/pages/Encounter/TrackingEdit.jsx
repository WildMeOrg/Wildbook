import React from 'react';
import { observer } from 'mobx-react-lite';
import TextInput from '../../components/generalInputs/TextInput';
import { Divider } from '../../components/Divider';
import MainButton from '../../components/MainButton';

export const TrackingEdit = observer(({ store = {} }) => {
    return (
        <div>
            <h6>Metal Tags</h6>
            {
                store.metalTagLocation && store.metalTagLocation.length > 0 &&
                <div>
                    {
                        store.metalTagLocation.map((location, index) => (
                            <div key={index}>
                                <TextInput
                                    label={location}
                                    value={store.metalTagValues.find(data => data.location === location)?.number || ''}
                                    onChange={(value) => {
                                        const arr = [...store.metalTagValues];
                                        const idx = arr.findIndex(data => data.location === location);
                                        if (idx > -1) {
                                            arr[idx].number = value;
                                        } else {
                                            arr.push({ location, number: value });
                                        }
                                        store.setMetalTagValues(arr);
                                        console.log('Updated Metal Tag Values:', JSON.stringify(store.metalTagValues));
                                    }
                                    }
                                />
                            </div>
                        ))
                    }
                </div>
            }
            <Divider />
            <h6>Acoustic Tags</h6>
            <TextInput
                label="Serial Number"
                value={store.acousticTagValues?.serialNumber || ''}
                onChange={(value) => {
                    store.setAcousticTagValues({ serialNumber: value });
                    console.log('Updated Acoustic Tag Serial Number:', JSON.stringify(store.acousticTagValues));
                }}
            />
            <TextInput
                label="ID"
                value={store.acousticTagValues?.idNumber || ''}
                onChange={(value) => {
                    store.setAcousticTagValues({ idNumber: value });
                    console.log('Updated Acoustic Tag ID Number:', JSON.stringify(store.acousticTagValues));
                }}
            />
            <Divider />
            <h6>Satellite Tags</h6>
            <TextInput
                label="Name"
                value={store.satelliteTagValues?.name || ''}
                onChange={(value) => {
                    store.setSatelliteTagValues({ name: value });
                    console.log('Updated Satellite Tag Name:', JSON.stringify(store.satelliteTagValues));
                }}
            />
            <TextInput
                label="Serial Number"
                value={store.satelliteTagValues?.serialNumber || ''}
                onChange={(value) => {
                    store.setSatelliteTagValues({ serialNumber: value });
                    console.log('Updated Satellite Tag Serial Number:', JSON.stringify(store.satelliteTagValues));
                }}
            />
            <TextInput
                label="Argos PTT"
                value={store.satelliteTagValues?.argosPttNumber || ''}
                onChange={(value) => {
                    store.setSatelliteTagValues({ argosPttNumber: value });
                }}
            />
            <div

            >
                <MainButton
                    onClick={() => {
                        store.patchTracking();
                        store.setEditTracking(false);
                    }}
                >
                    Save
                </MainButton>
                <MainButton
                    onClick={() => {
                        store.setEditTracking(false);                    
                    }}
                >
                    Cancel
                </MainButton>
            </div>
        </div>)
})
