import React from "react";
import { observer } from "mobx-react-lite";

export const MeasurementsReview = observer(({ store = {} }) => {
  return (
    <div>
      {store.showMeasurements && (
        <>
          {store.measurementTypes?.map((type, index) => {
            const measurement = store.encounterData?.measurements?.find(
              (m) => m.type === type,
            );
            if (!measurement)
              return (
                <div key={index}>
                  <h6>{type}</h6>
                  <p>{`${" "}`}</p>
                </div>
              );
            return (
              <div key={index}>
                <h6>{measurement.type}</h6>
                <p>{`${measurement.value}${" "}${measurement.units}(${measurement.samplingProtocol})`}</p>
              </div>
            );
          })}
        </>
      )}
    </div>
  );
});
