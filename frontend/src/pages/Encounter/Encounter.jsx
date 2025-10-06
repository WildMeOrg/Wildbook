import React, { useEffect, useState } from "react";
import axios from "axios";
import { observer } from "mobx-react-lite";
import { Container } from "react-bootstrap";
import { Row, Col } from "react-bootstrap";
import Pill from "../../components/Pill";
import CardWithSaveAndCancelButtons from "../../components/CardWithSaveAndCancelButtons";
import TextInput from "../../components/generalInputs/TextInput";
import DateIcon from "../../components/icons/DateIcon";
import IdentifyIcon from "../../components/IdentifyIcon";
import MetadataIcon from "../../components/icons/MetaDataIcon";
import LocationIcon from "../../components/icons/LocationIcon";
import AttributesIcon from "../../components/icons/AttributesIcon";
import ImageCard from "./ImageCard";
import CardWithEditButton from "../../components/CardWithEditButton";
import SelectInput from "../../components/generalInputs/SelectInput";
import useGetSiteSettings from "../../models/useGetSiteSettings";
import PillWithDropdown from "../../components/PillWithDropdown";
import DateInput from "../../components/generalInputs/DateInput";
import FreeTextAndSelectInput from "../../components/generalInputs/FreeTextAndSelectInput";
import SearchAndSelectInput from "../../components/generalInputs/SearchAndSelectInput";
import CoordinatesInput from "../../components/generalInputs/CoordinatesInput";
import { MapDisplay } from "./MapDisplay";
import ContactIcon from "../../components/icons/ContactIcon";
import HistoryIcon from "../../components/icons/HistoryIcon";
import ContactInfoModal from "./ContactInfoModal";
import { MoreDetails } from "./MoreDetails";
import EncounterHistoryModal from "./EncounterHistoryModal";
import MatchCriteriaModal from "./MatchCriteria";
import { EncounterStore } from './stores';
import { setEncounterState } from './stores/helperFunctions';
import { Alert } from "react-bootstrap";
import { DateSectionReview } from "./DateSectionReview";
import { IdentifySectionReview } from "./IdentifySectionReview";
import { MetadataSectionReview } from "./MetadataSectionReview";
import { LocationSectionReview } from "./LocationSectionReview";
import { AttributesSectionReview } from "./AttributesSectionReview";

const Encounter = observer(() => {
  const [store] = useState(() => new EncounterStore());
  const { data: siteSettings } = useGetSiteSettings();

  useEffect(() => {
    if (!siteSettings) return;
    store.setSiteSettings(siteSettings);
  }, [siteSettings, store]);

  const params = new URLSearchParams(window.location.search);
  const encounterId = params.get("number");

  useEffect(() => {
    let cancelled = false;
    axios
      .get(`/api/v3/encounters/${encounterId}`)
      .then((res) => {
        if (!cancelled) store.setEncounterData(res.data);
      })
      .catch((err) => console.error("fetch encounter error:", err));
    return () => {
      cancelled = true;
    };
  }, [encounterId, store]);

  return (
    <Container style={{ padding: "20px" }}>
      <ContactInfoModal
        isOpen={store.modals.openContactInfoModal}
        onClose={() => store.modals.setOpenContactInfoModal(false)}
        store={store}
      />
      <EncounterHistoryModal
        isOpen={store.modals.openEncounterHistoryModal}
        onClose={() => store.modals.setOpenEncounterHistoryModal(false)}
        store={store}
      />
      <MatchCriteriaModal
        store={store}
        isOpen={store.modals.openMatchCriteriaModal}
        onClose={() => store.modals.setOpenMatchCriteriaModal(false)}
      />
      <Row>
        <Col md={6}>
          <h2>
            Encounter{" "}
            {store.encounterData?.individualDisplayName ? (
              <a
                href={`/individuals.jsp?id=${store.encounterData.individualId}`}
                target="_blank"
                rel="noopener noreferrer"
                style={{ textDecoration: "none", color: "inherit" }}
              >
                {store.encounterData.individualDisplayName}
              </a>
            ) : (
              "Unassigned"
            )}
          </h2>

          <p>Encounter ID: {encounterId}</p>
        </Col>
        <Col md={6} className="text-end">
          <PillWithDropdown
            options={
              store.siteSettingsData?.encounterState?.map((state) => ({
                value: state,
                label: state,
              })) || []
            }
            selectedOption={store.encounterData?.state || "unidentifiable"}
            onSelect={(value) => {
              setEncounterState(value, store.encounterData?.id);
              store.refreshEncounterData();
            }}
          />
        </Col>
      </Row>

      <div style={{ marginTop: "20px", display: "flex", flexDirection: "row" }}>
        <div>
          <Pill
            text="Overview"
            style={{ marginRight: "10px" }}
            active={store.overviewActive}
            onClick={() => {
              if (store.overviewActive) return;
              store.setOverviewActive(true);
            }}
          />
          <Pill
            text="More Details"
            active={!store.overviewActive}
            onClick={() => {
              if (!store.overviewActive) return;
              store.setOverviewActive(false);
            }}
          />
        </div>
        <div className="d-flex flex-row" style={{ marginLeft: "auto" }}>
          <div
            style={{ marginRight: "10px", cursor: "pointer" }}
            onClick={() => {
              store.modals.setOpenContactInfoModal(true);
            }}
          >
            <ContactIcon />
          </div>
          <div
            style={{ marginRight: "10px", cursor: "pointer" }}
            onClick={() => {
              store.modals.setOpenEncounterHistoryModal(true);
            }}
          >
            <HistoryIcon />
          </div>
        </div>
      </div>
      {store.overviewActive ? (
        <Row className="mt-3 mb-3">
          <Col md={3}>
            {store.editDateCard ? (
              <CardWithSaveAndCancelButtons
                icon={<DateIcon />}
                disabled={!!store.errors.getFieldError("date", "date")}
                title="Date"
                onSave={async () => {
                  await store.saveSection("date", encounterId);
                  store.setEditDateCard(false);
                  await store.refreshEncounterData();
                }}
                onCancel={() => {
                  store.resetSectionDraft("date");
                  store.setEditDateCard(false);
                  store.errors.setFieldError("date", "date", null);
                  store.errors.clearSectionErrors("date");
                }}
                content={
                  <div>
                    <DateInput
                      label="Encounter Date"
                      value={store.getFieldValue("date", "date") ?? null}
                      onChange={(v) => {
                        store.setFieldValue("date", "date", v);
                      }}
                      className="mb-3"
                    />
                    {store.errors.getFieldError("date", "date") && (
                      <div className="invalid-feedback d-block">
                        {store.errors.getFieldError("date", "date")}
                      </div>
                    )}
                    <TextInput
                      label="Verbatim Event Date"
                      value={
                        store.getFieldValue("date", "verbatimEventDate") ?? ""
                      }
                      onChange={(v) =>
                        store.setFieldValue("date", "verbatimEventDate", v)
                      }
                    />
                    {store.errors.hasSectionError("date") && (
                      <Alert variant="danger">
                        {store.errors.getSectionErrors("date").join(";")}
                      </Alert>
                    )}

                  </div>
                }
              />
            ) : (
              <CardWithEditButton
                icon={<DateIcon />}
                title="Date"
                onClick={() => store.setEditDateCard(true)}
                content={
                  <DateSectionReview 
                    store={store}
                  />
                }
              />
            )}

            {store.editIdentifyCard ? (
              <CardWithSaveAndCancelButtons
                icon={<IdentifyIcon />}
                title="Identify"
                onSave={async () => {
                  await store.saveSection("identify", encounterId);
                  store.setEditIdentifyCard(false);
                  await store.refreshEncounterData();
                }}
                onCancel={() => {
                  store.resetSectionDraft("identify");
                  store.setEditIdentifyCard(false);
                  store.errors.clearSectionErrors("identify");
                }}
                content={
                  <div>
                    <SelectInput
                      label="Matched by"
                      value={
                        store.getFieldValue(
                          "identify",
                          "identificationRemarks",
                        ) ?? ""
                      }
                      onChange={(v) =>
                        store.setFieldValue(
                          "identify",
                          "identificationRemarks",
                          v,
                        )
                      }
                      options={store.identificationRemarksOptions}
                      className="mb-3"
                    />

                    <SearchAndSelectInput
                      label="Individual ID"
                      value={
                        store.getFieldValue("identify", "individualID") ?? ""
                      }
                      onChange={(v) =>
                        store.setFieldValue("identify", "individualID", v)
                      }
                      options={[]}
                      loadOptions={async (q) => {
                        const resp = await store.searchIndividualsByName(q);
                        const options = resp.data.hits.map((it) => ({
                          value: String(it.id),
                          label: it.displayName,
                        }));
                        return options;
                      }}
                      debounceMs={300}
                      minChars={2}
                    />

                    <TextInput
                      label="Alternate ID"
                      value={
                        store.getFieldValue(
                          "identify",
                          "otherCatalogNumbers",
                        ) ?? ""
                      }
                      onChange={(v) =>
                        store.setFieldValue(
                          "identify",
                          "otherCatalogNumbers",
                          v,
                        )
                      }
                    />
                    <SearchAndSelectInput
                      label="Sighting ID"
                      value={
                        store.getFieldValue("identify", "occurrenceID") ?? ""
                      }
                      onChange={(v) =>
                        store.setFieldValue("identify", "sightingId", v)
                      }
                      options={[]}
                      loadOptions={async (q) => {
                        const resp = await store.searchSightingsById(q);
                        return (
                          resp.data?.items?.map((it) => ({
                            value: String(it.id),
                            label: it.displayName,
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
                }
              />
            ) : (
              <CardWithEditButton
                icon={<IdentifyIcon />}
                title="Identify"
                onClick={() => store.setEditIdentifyCard(true)}
                content={
                  <IdentifySectionReview
                    store={store}
                  />
                }
              />
            )}

            {store.editMetadataCard ? (
              <CardWithSaveAndCancelButtons
                icon={<MetadataIcon />}
                title="Metadata"
                onSave={async () => {
                  await store.saveSection("metadata", encounterId);
                  store.setEditMetadataCard(false);
                  await store.refreshEncounterData();
                }}
                onCancel={() => {
                  store.resetSectionDraft("metadata");
                  store.setEditMetadataCard(false);
                  store.errors.clearSectionErrors("metadata");
                }}
                content={
                  <div>
                    <div>Encounter ID: {store.encounterData?.id}</div>
                    <div>Date Created: {store.encounterData?.createdAt}</div>
                    <div>
                      Last Edit:{" "}
                      {store.encounterData?.version
                        ? new Date(store.encounterData.version).toLocaleString()
                        : "None"}
                    </div>
                    <div>
                      Imported via:{" "}
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
                    </div>
                    <SelectInput
                      label="Assigned User"
                      value={
                        store.getFieldValue("metadata", "submitterID") ?? ""
                      }
                      onChange={(v) =>
                        store.setFieldValue("metadata", "submitterID", v)
                      }
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
                    {store.errors.hasSectionError("metadata") && (
                      <Alert variant="danger">
                        {store.errors.getSectionErrors("metadata").join(";")}
                      </Alert>
                    )}
                  </div>
                }
              />
            ) : (
              <CardWithEditButton
                icon={<MetadataIcon />}
                title="Metadata"
                onClick={() => store.setEditMetadataCard(true)}
                content={
                  <MetadataSectionReview
                    store={store}s
                  />
                }
              />
            )}
          </Col>
          <Col md={3}>
            {store.editLocationCard ? (
              <CardWithSaveAndCancelButtons
                icon={<LocationIcon />}
                disabled={!!store.errors.getFieldError("location", "latitude") || !!store.errors.getFieldError("location", "longitude")}
                title="Location"
                onSave={async () => {
                  await store.saveSection("location", encounterId);
                  store.setEditLocationCard(false);
                  await store.refreshEncounterData();
                }}
                onCancel={() => {
                  store.resetSectionDraft("location");
                  store.setEditLocationCard(false);
                  store.errors.setFieldError("location", "latitude", null);
                  store.errors.setFieldError("location", "longitude", null);
                  store.errors.clearSectionErrors("location");

                }}
                content={
                  <div>
                    <TextInput
                      label="Location"
                      value={
                        store.getFieldValue("location", "verbatimLocality") ??
                        ""
                      }
                      onChange={(v) =>
                        store.setFieldValue("location", "verbatimLocality", v)
                      }
                    />
                    <SelectInput
                      label="Location ID"
                      value={
                        store.getFieldValue("location", "locationId") ?? ""
                      }
                      onChange={(v) =>
                        store.setFieldValue("location", "locationId", v)
                      }
                      options={store.locationIdOptions}
                      className="mb-3"
                    />
                    <SelectInput
                      label="Country"
                      value={store.getFieldValue("location", "country") ?? ""}
                      onChange={(v) =>
                        store.setFieldValue("location", "country", v)
                      }
                      options={
                        store.siteSettingsData?.country?.map((item) => {
                          return {
                            value: item,
                            label: item,
                          };
                        }) || []
                      }
                      className="mb-3"
                    />
                    <CoordinatesInput store={store} />
                    {store.errors.hasSectionError("location") && (
                      <Alert variant="danger">
                        {store.errors.getSectionErrors("location").join(";")}
                      </Alert>
                    )}

                  </div>
                }
              />
            ) : (
              <CardWithEditButton
                icon={<LocationIcon />}
                title="Location"
                onClick={() => store.setEditLocationCard(true)}
                content={
                  <LocationSectionReview
                    store={store}
                  />
                }
              />
            )}

            {store.editAttributesCard ? (
              <CardWithSaveAndCancelButtons
                icon={<AttributesIcon />}
                title="Attributes"
                onSave={async () => {
                  await store.saveSection("attributes", encounterId);
                  store.setEditAttributesCard(false);
                  await store.refreshEncounterData();
                }}
                onCancel={() => {
                  store.resetSectionDraft("attributes");
                  store.setEditAttributesCard(false);
                  store.errors.clearSectionErrors("attributes");
                }}
                content={
                  <div>
                    <SelectInput
                      label="Taxonomy"
                      value={
                        store.getFieldValue("attributes", "taxonomy") ?? ""
                      }
                      onChange={(v) => {
                        store.setFieldValue("attributes", "taxonomy", v);
                      }}
                      options={store.taxonomyOptions}
                      className="mb-3"
                    />
                    <SelectInput
                      label="Living Status"
                      value={
                        store.getFieldValue("attributes", "livingStatus") ?? ""
                      }
                      onChange={(v) =>
                        store.setFieldValue("attributes", "livingStatus", v)
                      }
                      options={store.livingStatusOptions}
                      className="mb-3"
                    />
                    <SelectInput
                      label="Sex"
                      value={store.getFieldValue("attributes", "sex") ?? ""}
                      onChange={(v) =>
                        store.setFieldValue("attributes", "sex", v)
                      }
                      options={store.sexOptions}
                      className="mb-3"
                    />
                    <TextInput
                      label="Noticeable Scarring"
                      value={
                        store.getFieldValue(
                          "attributes",
                          "distinguishingScar",
                        ) ?? ""
                      }
                      onChange={(v) =>
                        store.setFieldValue(
                          "attributes",
                          "distinguishingScar",
                          v,
                        )
                      }
                    />
                    <FreeTextAndSelectInput
                      label="Behavior"
                      value={
                        store.getFieldValue("attributes", "behavior") ?? ""
                      }
                      onChange={(v) =>
                        store.setFieldValue("attributes", "behavior", v)
                      }
                      options={store.behaviorOptions}
                      className="mb-3"
                    />
                    <TextInput
                      label="Group Role"
                      value={
                        store.getFieldValue("attributes", "groupRole") ?? ""
                      }
                      onChange={(v) =>
                        store.setFieldValue("attributes", "groupRole", v)
                      }
                    />
                    <SelectInput
                      label="Patterning Code"
                      value={
                        store.getFieldValue("attributes", "patterningCode") ??
                        ""
                      }
                      onChange={(v) =>
                        store.setFieldValue("attributes", "patterningCode", v)
                      }
                      options={store.patterningCodeOptions}
                      className="mb-3"
                    />
                    <SelectInput
                      label="Life Stage"
                      value={
                        store.getFieldValue("attributes", "lifeStage") ?? ""
                      }
                      onChange={(v) =>
                        store.setFieldValue("attributes", "lifeStage", v)
                      }
                      options={store.lifeStageOptions}
                      className="mb-3"
                    />
                    <TextInput
                      label="Observation Comments"
                      value={
                        store.getFieldValue(
                          "attributes",
                          "occurrenceRemarks",
                        ) ?? ""
                      }
                      onChange={(v) =>
                        store.setFieldValue(
                          "attributes",
                          "occurrenceRemarks",
                          v,
                        )
                      }
                    />
                    {store.errors.hasSectionError("attributes") && (
                      <Alert variant="danger">
                        {store.errors.getSectionErrors("attributes").join(";")}
                      </Alert>
                    )}
                  </div>
                }
              />
            ) : (
              <CardWithEditButton
                icon={<AttributesIcon />}
                title="Attributes"
                onClick={() => store.setEditAttributesCard(true)}
                content={
                  <AttributesSectionReview
                    store={store}
                  />
                }
              />
            )}
          </Col>
          <Col md={6}>
            <ImageCard store={store} />
          </Col>
        </Row>
      ) : (
        <MoreDetails store={store} />
      )}
    </Container>
  );
});

export default Encounter;
