import React from "react";
import { observer } from "mobx-react-lite";
import SelectInput from "../../components/generalInputs/SelectInput";
import { Alert } from "react-bootstrap";
import TextInput from "../../components/generalInputs/TextInput";
import FreeTextAndSelectInput from "../../components/generalInputs/FreeTextAndSelectInput";
import ContainerWithSpinner from "../../components/ContainerWithSpinner";

export const AttributesSectionEdit = observer(({ store }) => {
  const siteSettingsLoading = Boolean(store?.siteSettingsLoading);
  return (
    <div>
      <ContainerWithSpinner loading={siteSettingsLoading}>
        <SelectInput
          label="TAXONOMY"
          value={store.getFieldValue("attributes", "taxonomy") ?? ""}
          onChange={(v) => {
            store.setFieldValue("attributes", "taxonomy", v);
          }}
          options={store.taxonomyOptions}
          className="mb-3"
        />
      </ContainerWithSpinner>
      <ContainerWithSpinner loading={siteSettingsLoading}>
        <SelectInput
          label="LIVING_STATUS"
          value={store.getFieldValue("attributes", "livingStatus") ?? ""}
          onChange={(v) => store.setFieldValue("attributes", "livingStatus", v)}
          options={store.livingStatusOptions}
          className="mb-3"
        />
      </ContainerWithSpinner>
      <ContainerWithSpinner loading={siteSettingsLoading}>
        <SelectInput
          label="SEX"
          value={store.getFieldValue("attributes", "sex") ?? ""}
          onChange={(v) => store.setFieldValue("attributes", "sex", v)}
          options={store.sexOptions}
          className="mb-3"
        />
      </ContainerWithSpinner>
      <TextInput
        label="DISTINGUISHING_SCAR"
        value={store.getFieldValue("attributes", "distinguishingScar") ?? ""}
        onChange={(v) =>
          store.setFieldValue("attributes", "distinguishingScar", v)
        }
      />
      <ContainerWithSpinner loading={siteSettingsLoading}>
        <FreeTextAndSelectInput
          label="BEHAVIOR"
          value={store.getFieldValue("attributes", "behavior") ?? ""}
          onChange={(v) => store.setFieldValue("attributes", "behavior", v)}
          options={store.behaviorOptions}
          className="mb-3"
        />
      </ContainerWithSpinner>
      <TextInput
        label="GROUP_ROLE"
        value={store.getFieldValue("attributes", "groupRole") ?? ""}
        onChange={(v) => store.setFieldValue("attributes", "groupRole", v)}
      />
      <ContainerWithSpinner loading={siteSettingsLoading}>
        <SelectInput
          label="PATTERNING_CODE"
          value={store.getFieldValue("attributes", "patterningCode") ?? ""}
          onChange={(v) =>
            store.setFieldValue("attributes", "patterningCode", v)
          }
          options={store.patterningCodeOptions}
          className="mb-3"
        />
      </ContainerWithSpinner>
      <ContainerWithSpinner loading={siteSettingsLoading}>
        <SelectInput
          label="LIFE_STAGE"
          value={store.getFieldValue("attributes", "lifeStage") ?? ""}
          onChange={(v) => store.setFieldValue("attributes", "lifeStage", v)}
          options={store.lifeStageOptions}
          className="mb-3"
        />
      </ContainerWithSpinner>
      <TextInput
        label="OBSERVATION_COMMENTS"
        value={store.getFieldValue("attributes", "occurrenceRemarks") ?? ""}
        onChange={(v) =>
          store.setFieldValue("attributes", "occurrenceRemarks", v)
        }
      />
      {store.errors.hasSectionError("attributes") && (
        <Alert variant="danger">
          {store.errors.getSectionErrors("attributes").join(";")}
        </Alert>
      )}
    </div>
  );
});
