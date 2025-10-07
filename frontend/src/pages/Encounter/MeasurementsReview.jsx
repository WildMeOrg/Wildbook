import React from 'react';
import { observer } from 'mobx-react-lite';

export const MeasurementsReview = observer(({ store }) => {
    return <div>
        {store.showMeasurements && store.encounterData?.measurements?.length > 0 && (
            <>
                {store.encounterData.measurements.map((measurement, index) => {
                    console.log("Measurement:", JSON.stringify(measurement));
                    return <div key={index}>
                        <h6>{measurement.type}</h6>
                        <p>{`${measurement.value}${" "}${measurement.units}(${measurement.samplingProtocol})`}</p>
                    </div>
                })
                }
            </>
        )}
    </div>
})
