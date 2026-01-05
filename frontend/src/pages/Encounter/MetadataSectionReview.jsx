import React from "react";
import { observer } from "mobx-react-lite";
import { AttributesAndValueComponent } from "../../components/AttributesAndValueComponent";
import { FormattedMessage } from "react-intl";

export const MetadataSectionReview = observer(({ store }) => {
  return (
    <div>
      <div>
        <h6>
          <FormattedMessage id="ENCOUNTER_ID" />
          {": "}
        </h6>
        <p>{store.encounterData?.id || ""}</p>
      </div>
      <div>
        <h6>
          <FormattedMessage id="DATE_CREATED" />
          {": "}
        </h6>
        <p>
          {(() => {
            const iso = store.encounterData?.dateSubmitted;
            return iso
              ? iso
                  .replace("T", " ")
                  .replace(/\.\d+/, "")
                  .replace(/Z|([+-]\d{2}:\d{2})$/, "")
                  .trim()
              : "";
          })()}
        </p>
      </div>
      <div>
        <h6>
          <FormattedMessage id="LAST_EDIT" />
          {": "}
        </h6>
        <p>
          {(() => {
            const iso = store.encounterData?.version
              ? new Date(store.encounterData.version).toLocaleString()
              : "";
            return iso
              ? iso
                  .replace("T", " ")
                  .replace(",", "")
                  .replace(/\.\d+/, "")
                  .replace(/Z|([+-]\d{2}:\d{2})$/, "")
                  .trim()
              : "";
          })()}
        </p>
      </div>
      <div>
        <h6>
          <FormattedMessage id="IMPORTED_VIA" />
          {": "}
        </h6>
        <p>
          {store.encounterData?.importTaskId ? (
            <a
              href={`/react/bulk-import-task?id=${store.encounterData.importTaskId}`}
              target="_blank"
              rel="noopener noreferrer"
              className="text-decoration-none"
            >
              {store.encounterData.importTaskId}
            </a>
          ) : (
            ""
          )}
        </p>
      </div>
      <AttributesAndValueComponent
        attributeId="ASSIGNED_USER"
        value={store.getFieldValue("metadata", "assignedUsername") || ""}
      />
    </div>
  );
});
