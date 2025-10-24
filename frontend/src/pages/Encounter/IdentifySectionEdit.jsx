import React, { useEffect } from "react";
import { observer } from "mobx-react-lite";
import TextInput from "../../components/generalInputs/TextInput";
import SelectInput from "../../components/generalInputs/SelectInput";
import SearchAndSelectInput from "../../components/generalInputs/SearchAndSelectInput";
import { Alert } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import MainButton from "../../components/MainButton";

export const IdentifySectionEdit = observer(({ store }) => {
  useEffect(() => {
    const id = store.getFieldValue("identify", "individualId");
    const name = store.getFieldValue("identify", "individualDisplayName");
    if (!id) return;

    const exists = store.individualOptions?.some((o) => o.value === String(id));
    if (!exists) {
      store.setIndividualOptions([
        { value: String(id), label: name || String(id).slice(0, 8) },
        ...(store.individualOptions || []),
      ]);
    }
  }, [store]);

  return (
    <div>
      <SelectInput
        label="MATCHED_BY"
        value={store.getFieldValue("identify", "identificationRemarks") ?? ""}
        onChange={(v) =>
          store.setFieldValue("identify", "identificationRemarks", v)
        }
        options={store.identificationRemarksOptions}
        className="mb-3"
      />
      {store.encounterData?.individualId && (
        <MainButton
          noArrow={true}
          style={{ marginLeft: 0 }}
          color="red"
          backgroundColor="white"
          borderColor="red"
          shadowColor="red"
          onClick={() => {
            store.setEditIdentifyCard(false);
            store.removeIndividualFromEncounter();
          }}
        >
          <FormattedMessage id="UNASSIGN_INDIVIDUAL" />
        </MainButton>
      )}
      <SearchAndSelectInput
        label="INDIVIDUAL_ID"
        value={store.getFieldValue("identify", "individualId") ?? ""}
        onChange={(v) => store.setFieldValue("identify", "individualId", v)}
        options={store.individualOptions}
        loadOptions={async (q) => {
          const resp = await store.searchIndividualsByName(q);
          const options = (resp.data.hits || []).map((it) => ({
            value: String(it.id),
            label: it.displayName,
          }));
          store.setIndividualOptions(options);
          return options;
        }}
        debounceMs={300}
        minChars={2}
      />

      <TextInput
        label="ALTERNATE_ID"
        value={store.getFieldValue("identify", "otherCatalogNumbers") ?? ""}
        onChange={(v) =>
          store.setFieldValue("identify", "otherCatalogNumbers", v)
        }
      />
      {store.encounterData?.occurrenceId && (
        <MainButton
          noArrow={true}
          style={{ marginLeft: 0 }}
          onClick={() => {
            store.removeOccurrenceIdFromEncounter();
            store.setEditIdentifyCard(false);
          }}
          color="red"
          backgroundColor="white"
          borderColor="red"
          shadowColor="red"
        >
          <FormattedMessage id="REMOVE_FROM_SIGHTING" />
        </MainButton>
      )}

      <SearchAndSelectInput
        label="SIGHTING_ID"
        value={store.getFieldValue("identify", "occurrenceId") ?? ""}
        onChange={(v) => store.setFieldValue("identify", "occurrenceId", v)}
        options={[]}
        loadOptions={async (q) => {
          const resp = await store.searchSightingsById(q);
          return (
            resp.data?.hits
              ?.filter((data) => data.access === "full")
              .map((it) => ({
                value: it.id,
                label: it.id,
              })) ?? []
          );
        }}
        debounceMs={300}
        minChars={2}
      />
      {store.errors.hasSectionError("identify") && (
        <Alert variant="danger">
          {store.errors.getSectionErrors("identify").join(";")}
        </Alert>
      )}
    </div>
  );
});
