import React from "react";
import { observer } from "mobx-react-lite";
import SelectInput from "../../components/generalInputs/SelectInput";
import { Alert } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import ContainerWithSpinner from "../../components/ContainerWithSpinner";

export const MetadataSectionEdit = observer(({ store }) => {
  const siteSettingsLoading = Boolean(store?.siteSettingsLoading);
  return (
    <div>
      <h6 className="mt-2 mb-2">
        <FormattedMessage id="ENCOUNTER_ID" />: {store.encounterData?.id}
      </h6>
      <h6 className="mt-2 mb-2">
        <FormattedMessage id="DATE_CREATED" />: {store.encounterData?.createdAt}
      </h6>
      <h6 className="mt-2 mb-2">
        <FormattedMessage id="LAST_EDIT" />:{" "}
        {store.encounterData?.version
          ? new Date(store.encounterData.version).toLocaleString()
          : "None"}
      </h6>
      <h6 className="mt-2 mb-2">
        <FormattedMessage id="IMPORTED_VIA" />:{" "}
        {store.encounterData?.importTaskId ? (
          <a
            href={`/react/bulk-import-task?id=${store.encounterData.importTaskId}`}
            target="_blank"
            rel="noopener noreferrer"
          >
            {store.encounterData.importTaskId}
          </a>
        ) : (
          ""
        )}
      </h6>
      <ContainerWithSpinner loading={siteSettingsLoading}>
        <SelectInput
          label="ASSIGNED_USER"
          value={store.getFieldValue("metadata", "submitterID") ?? ""}
          onChange={(v) => store.setFieldValue("metadata", "submitterID", v)}
          options={
            store.siteSettingsData?.users
              ?.filter((item) => item.username)
              .map((item) => {
                return {
                  value: item.username,
                  label: item.username,
                };
              }) || []
          }
          className="mb-3"
        />
      </ContainerWithSpinner>
      {store.errors.hasSectionError("metadata") && (
        <Alert variant="danger">
          {store.errors.getSectionErrors("metadata").join(";")}
        </Alert>
      )}
    </div>
  );
});
