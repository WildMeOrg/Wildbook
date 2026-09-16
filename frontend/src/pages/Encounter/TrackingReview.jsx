import React from "react";
import { observer } from "mobx-react-lite";
import { Divider } from "../../components/Divider";
import { FormattedMessage } from "react-intl";

export const TrackingReview = observer(({ store = {} }) => {
  const metalTags = store.encounterData?.metalTags || [];
  return (
    <div>
      {store.metalTagsEnabled && (
        <>
          <p>
            <FormattedMessage id="METAL_TAGS" />
          </p>
          {store.metalTagLocation &&
            store.metalTagLocation.length > 0 &&
            store.metalTagLocation.map((loc, index) => {
              return (
                <div key={index}>
                  <p>
                    {loc}:{" "}
                    {metalTags.find((data) => data.location === loc)?.number}{" "}
                  </p>
                </div>
              );
            })}
          <Divider />
        </>
      )}

      {store.acousticTagEnabled && (
        <>
          <p>
            <FormattedMessage id="ACOUSTIC_TAGS" />
          </p>
          <p>
            <FormattedMessage id="SERIAL_NUMBER" />:{" "}
            {store.encounterData?.acousticTag?.serialNumber}
          </p>
          <p>
            <FormattedMessage id="ID" />:{" "}
            {store.encounterData?.acousticTag?.idNumber}
          </p>
          <Divider />
        </>
      )}
      {store.satelliteTagEnabled && (
        <>
          <p>
            <FormattedMessage id="SATELLITE_TAGS" />
          </p>
          <p>
            <FormattedMessage id="NAME" />:{" "}
            {store.encounterData?.satelliteTag?.name}
          </p>
          <p>
            <FormattedMessage id="ID" />:{" "}
            {store.encounterData?.satelliteTag?.serialNumber}
          </p>
          <p>
            <FormattedMessage id="ARGOS_PTT_NUMBER" />:{" "}
            {store.encounterData?.satelliteTag?.argosPttNumber}
          </p>
          <Divider />
        </>
      )}
    </div>
  );
});
