import React from "react";
import { observer } from "mobx-react-lite";
import TextInput from "../../components/generalInputs/TextInput";
import { Divider } from "../../components/Divider";
import SelectInput from "../../components/generalInputs/SelectInput";
import { FormattedMessage } from "react-intl";

export const TrackingEdit = observer(({ store = {} }) => {
  return (
    <div>
      <h6>
        <FormattedMessage id="METAL_TAGS" />
      </h6>
      {store.metalTagsEnabled &&
        store.metalTagLocation &&
        store.metalTagLocation.length > 0 && (
          <div>
            {store.metalTagLocation.map((location, index) => (
              <div key={index}>
                <TextInput
                  label={location}
                  value={
                    store.metalTagValues.find(
                      (data) => data.location === location,
                    )?.number || ""
                  }
                  onChange={(value) => {
                    const arr = [...store.metalTagValues];
                    const idx = arr.findIndex(
                      (data) => data.location === location,
                    );
                    if (idx > -1) {
                      arr[idx].number = value;
                    } else {
                      arr.push({ location, number: value });
                    }
                    store.setMetalTagValues(arr);
                  }}
                />
              </div>
            ))}
          </div>
        )}
      <Divider />
      {store.acousticTagEnabled && (
        <>
          <h6>
            <FormattedMessage id="ACOUSTIC_TAGS" />
          </h6>
          <TextInput
            label="SERIAL_NUMBER"
            value={store.acousticTagValues?.serialNumber || ""}
            onChange={(value) => {
              store.setAcousticTagValues({ serialNumber: value });
            }}
          />
          <TextInput
            label="ID"
            value={store.acousticTagValues?.idNumber || ""}
            onChange={(value) => {
              store.setAcousticTagValues({ idNumber: value });
            }}
          />
          <Divider />
        </>
      )}
      {store.satelliteTagEnabled && (
        <>
          <h6>
            <FormattedMessage id="SATELLITE_TAGS" />
          </h6>
          <SelectInput
            label="NAME"
            value={store.satelliteTagValues?.name || ""}
            options={store.satelliteTagNameOptions || []}
            onChange={(value) => {
              store.setSatelliteTagValues({ name: value });
            }}
          />
          <TextInput
            label="SERIAL_NUMBER"
            value={store.satelliteTagValues?.serialNumber || ""}
            onChange={(value) => {
              store.setSatelliteTagValues({ serialNumber: value });
            }}
          />
          <TextInput
            label="PTTARGOS_PTT_NUMBER"
            value={store.satelliteTagValues?.argosPttNumber || ""}
            onChange={(value) => {
              store.setSatelliteTagValues({ argosPttNumber: value });
            }}
          />
        </>
      )}
    </div>
  );
});
