import React, { useEffect } from "react";
import MainButton from "../../components/MainButton";
import axios from "axios";
import { observer } from "mobx-react-lite";
import EncounterStore from "./EncounterStore";
import { Container } from "react-bootstrap";
import { Row, Col } from "react-bootstrap";
import ActivePill from "../../components/ActivePill";
import InactivePill from "../../components/InactivePill";
import CardWithSaveAndCancelButtons from "../../components/CardWithSaveAndCancelButtons";
import TextInput from "../../components/generalInputs/TextInput";
import DateIcon from "../../components/icons/DateIcon";
import DateCardContent from "./DateCardContent";
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

const Encounter = observer(() => {
  const store = React.useMemo(() => new EncounterStore(), []);

  const { data: siteSettings } = useGetSiteSettings();

  useEffect(() => {
    if (!siteSettings) return;
    store.setSiteSettings(siteSettings);
  }, [siteSettings, store]);

  const params = new URLSearchParams(window.location.search);
  const encounterId =
    params.get("number") || "4770c075-a4c7-48c3-9616-8fa87aa2d65a";

  useEffect(() => {
    let cancelled = false;
    axios
      .get(`/api/v3/encounters/${encounterId}`)
      .then((res) => {
        if (!cancelled) store.setEncounterData(res.data);
      })
      .catch((err) => console.error("fetch encounter error:", err));
    return () => { cancelled = true; };
  }, [encounterId, store]);

  return (
    <Container style={{ padding: "20px" }}>

      <Row>
        <Col md={6}>
          <h2 >
            Encounter {store.encounterData?.individualDisplayName ? `of ${store.encounterData?.individualDisplayName}` : "Unassigned "}
          </h2>
          <p>Encounter ID: {encounterId}</p>
        </Col>
        <Col md={6} className="text-end">
          <PillWithDropdown
            options={store.siteSettingsData?.encounterState?.map((state) => ({
              value: state,
              label: state,
            })) || []}
            selectedOption={store.encounterData?.state || "unidentifiable"}
            onSelect={(value) => {
              console.log("Selected state:", value);
              // store.setFieldValue("metadata", "state", value);
              store.setEncounterState(value);
            }}
          />

        </Col>

      </Row>


      <div style={{ marginTop: "20px", display: "flex", flexDirection: "row" }}>

        <ActivePill
          text="Overview"
          style={{ marginRight: "10px" }}
          onClick={() => {
            console.log("Add Individual clicked");
            store.setOverviewActive(true);
          }}
        />
        <InactivePill
          text="More Details"
          onClick={() => {
            console.log("Add Individual clicked");
            store.setOverviewActive(false);
          }}
        />
      </div>
      {store.overviewActive ? (

        <Row className="mt-3 mb-3">
          <Col md={3}>
            {store.editDateCard ? (
              <CardWithSaveAndCancelButtons
                icon={<DateIcon />}
                title="Date"
                onSave={async () => {
                  await store.saveSection("date", encounterId);
                  store.setEditDateCard(false);
                }}
                onCancel={() => {
                  store.resetSectionDraft("date");
                  store.setEditDateCard(false);
                }}
                content={
                  <div>
                    <DateInput
                      label="Encounter Date"
                      value={store.getFieldValue("date", "date") ?? ""}
                      onChange={(v) => store.setFieldValue("date", "date", v)}
                      className="mb-3"
                    />
                    <TextInput
                      label="Verbatim Event Date"
                      value={store.getFieldValue("date", "verbatimLocality") ?? ""}
                      onChange={(v) => store.setFieldValue("date", "verbatimLocality", v)}
                    />
                  </div>
                }
              />
            ) : (
              <CardWithEditButton
                icon={<DateIcon />}
                title="Date"
                onClick={() => store.setEditDateCard(true)}
                content={
                  <div>
                    <div>Encounter Date: {store.getFieldValue("date", "date") || "None"}</div>
                    <div>Verbatim Event Date: {store.getFieldValue("date", "verbatimLocality") || "None"}</div>
                  </div>
                }
              />)}

            {store.editIdentifyCard ? (
              <CardWithSaveAndCancelButtons
                icon={<IdentifyIcon />}
                title="Identify"
                onSave={async () => {
                  await store.saveSection("identify", encounterId);
                  store.setEditIdentifyCard(false);
                }}
                onCancel={() => {
                  store.resetSectionDraft("identify");
                  store.setEditIdentifyCard(false);
                }}
                content={
                  <div>
                    <TextInput
                      label="Identified as"
                      value={store.getFieldValue("identify", "individualDisplayName") ?? ""}
                      onChange={(v) => store.setFieldValue("identify", "individualDisplayName", v)}
                    />
                    <TextInput
                      label="Matched by"
                      value={store.getFieldValue("identify", "matchedBy") ?? ""}
                      onChange={(v) => store.setFieldValue("identify", "matchedBy", v)}
                    />
                    <TextInput
                      label="Alternate ID"
                      value={store.getFieldValue("identify", "alternateID") ?? ""}
                      onChange={(v) => store.setFieldValue("identify", "alternateID", v)}
                    />
                  </div>
                }
              />
            ) : (
              <CardWithEditButton
                icon={<IdentifyIcon />}
                title="Identify"
                onClick={() => store.setEditIdentifyCard(true)}
                content={
                  <div>
                    <div>Identified as: {store.getFieldValue("identify", "individualDisplayName")}</div>
                    <div>Matched by: {store.getFieldValue("identify", "matchedBy")}</div>
                    <div>Alternate ID: {store.getFieldValue("identify", "alternateID")}</div>
                  </div>
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
                }}
                onCancel={() => {
                  store.resetSectionDraft("metadata");
                  store.setEditMetadataCard(false);
                }}
                content={
                  <div>
                    <div>Encounter ID: {store.encounterData?.id}</div>
                    <div>Date Created: {store.encounterData?.createdAt}</div>
                    <div>Last Edit: {store.encounterData?.updatedAt}</div>
                    <div>
                      Imported via:{" "}
                      {store.encounterData?.importTaskId ? (
                        <a
                          href={`/react/bulk-import-task?id=${store.encounterData.importTaskId}`}
                          target="_blank"
                          rel="noopener noreferrer">
                          {store.encounterData.importTaskId}
                        </a>
                      ) : ""}
                    </div>
                    <SelectInput
                      label="Assigned User"
                      value={store.getFieldValue("metadata", "assignedUsername") ?? ""}
                      onChange={(v) => store.setFieldValue("metadata", "assignedUsername", v)}
                      options={store.siteSettingsData?.users
                        ?.filter((item) => item.username)
                        .map((item) => {
                          return {
                            value: item.username,
                            label: item.username,
                          };
                        }) || []}
                      className="mb-3"
                    />
                    <SelectInput
                      label="Sharing Permission"
                      value={store.getFieldValue("metadata", "sharingPermission") ?? ""}
                      onChange={(v) => store.setFieldValue("metadata", "sharingPermission", v)}
                      options={[]}
                      className="mb-3"
                    />

                  </div>
                }
              />
            ) : (
              <CardWithEditButton
                icon={<MetadataIcon />}
                title="Metadata"
                onClick={() => store.setEditMetadataCard(true)}
                content={
                  <div>
                    <div>Encounter ID: {store.encounterData?.id}</div>
                    <div>Date Created: {store.encounterData?.dateSubmitted}</div>
                    <div>Last Edit: {store.encounterData?.updatedAt}</div>
                    <div>
                      Imported via:{" "}
                      {store.encounterData?.importTaskId ? (
                        <a
                          href={`/react/bulk-import-task?id=${store.encounterData.importTaskId}`}
                          target="_blank"
                          rel="noopener noreferrer">
                          {store.encounterData.importTaskId}
                        </a>
                      ) : "none"}
                    </div>
                    <div>Assigned User: {store.getFieldValue("metadata", "assignedUsername") || "None"}</div>
                    <div>Sharing Permission: {store.getFieldValue("metadata", "sharingPermission") || "None"}</div>
                  </div>
                }
              />
            )}


          </Col>
          <Col md={3}>

            {store.editLocationCard ? (
              <CardWithSaveAndCancelButtons
                icon={<LocationIcon />}
                title="Location"
                onSave={async () => {
                  await store.saveSection("location", encounterId);
                  store.setEditLocationCard(false);
                }}
                onCancel={() => {
                  store.resetSectionDraft("location");
                  store.setEditLocationCard(false);
                }}
                content={
                  <div>
                    <TextInput
                      label="Location"
                      value={store.getFieldValue("location", "locationName") ?? ""}
                      onChange={(v) => store.setFieldValue("location", "locationName", v)}
                    />
                    <TextInput
                      label="Location ID"
                      value={store.getFieldValue("location", "country") ?? ""}
                      onChange={(v) => store.setFieldValue("location", "country", v)}
                    />
                    <TextInput
                      label="Latitude"
                      value={store.getFieldValue("location", "decimalLatitude") ?? ""}
                      onChange={(v) => store.setFieldValue("location", "decimalLatitude", v)}
                    />
                    <TextInput
                      label="Longitude"
                      value={store.getFieldValue("location", "decimalLongitude") ?? ""}
                      onChange={(v) => store.setFieldValue("location", "decimalLongitude", v)}
                    />
                  </div>
                }
              />
            ) : (
              <CardWithEditButton
                icon={<LocationIcon />}
                title="Location"
                onClick={() => store.setEditLocationCard(true)}
                content={
                  <div>
                    <div>Location: {store.getFieldValue("location", "locationName") || "None"}</div>
                    <div>Location ID: {store.getFieldValue("location", "locationId") || "None"}</div>
                    <div>
                      Coordinates: {store.getFieldValue("location", "occurrenceLocationGeoPoint") || "none"}
                    </div>
                  </div>
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
                }}
                onCancel={() => {
                  store.resetSectionDraft("attributes");
                  store.setEditAttributesCard(false);
                }}
                content={
                  <div>
                    <SelectInput
                      label="Taxonomy"
                      value={store.getFieldValue("attributes", "taxonomy") ?? ""}
                      onChange={(v) => store.setFieldValue("attributes", "taxonomy", v)}
                      options={store.taxonomyOptions}
                      className="mb-3"
                    />
                    <SelectInput
                      label="Living Status"
                      value={store.getFieldValue("attributes", "livingStatus") ?? ""}
                      onChange={(v) => store.setFieldValue("attributes", "livingStatus", v)}
                      options={store.livingStatusOptions}
                      className="mb-3"
                    />
                    <SelectInput
                      label="Sex"
                      value={store.getFieldValue("attributes", "sex") ?? ""}
                      onChange={(v) => store.setFieldValue("attributes", "sex", v)}
                      options={store.sexOptions}
                      className="mb-3"
                    />
                    <TextInput
                      label="Noticeable Scarring"
                      value={store.getFieldValue("attributes", "distinguishingScar") ?? ""}
                      onChange={(v) => store.setFieldValue("attributes", "distinguishingScar", v)}
                    />
                    <SelectInput
                      label="Behavior"
                      value={store.getFieldValue("attributes", "behavior") ?? ""}
                      onChange={(v) => store.setFieldValue("attributes", "behavior", v)}
                      options={store.behaviorOptions}
                      className="mb-3"
                    />
                    <TextInput
                      label="Group Role"
                      value={store.getFieldValue("attributes", "groupRole") ?? ""}
                      onChange={(v) => store.setFieldValue("attributes", "groupRole", v)}
                    />
                    <SelectInput
                      label="Patterning Code"
                      value={store.getFieldValue("attributes", "patterningCode") ?? ""}
                      onChange={(v) => store.setFieldValue("attributes", "patterningCode", v)}
                      options={store.patterningCodeOptions}
                      className="mb-3"
                    />
                    <SelectInput
                      label="Life Stage"
                      value={store.getFieldValue("attributes", "lifeStage") ?? ""}
                      onChange={(v) => store.setFieldValue("attributes", "lifeStage", v)}
                      options={store.lifeStageOptions}
                      className="mb-3"
                    />
                    <TextInput
                      label="Observation Comments"
                      value={store.getFieldValue("attributes", "observationComments") ?? ""}
                      onChange={(v) => store.setFieldValue("attributes", "observationComments", v)}
                    />
                  </div>
                }
              />
            ) : (
              <CardWithEditButton
                icon={<AttributesIcon />}
                title="Attributes"
                onClick={() => store.setEditAttributesCard(true)}
                content={
                  <div>
                    <div>Taxonomy: {store.getFieldValue("attributes", "taxonomy")}</div>
                    <div>Status: {store.getFieldValue("attributes", "livingStatus")}</div>
                    <div>Sex: {store.getFieldValue("attributes", "sex")}</div>
                    <div>Noticeable Scarring: {store.getFieldValue("attributes", "distinguishingScar")}</div>
                    <div>Behavior: {store.getFieldValue("attributes", "behavior")}</div>
                    <div>Group Role: {store.getFieldValue("attributes", "groupRole")}</div>
                    <div>Patterning Code: {store.getFieldValue("attributes", "patterningCode")}</div>
                    <div>Life Stage: {store.getFieldValue("attributes", "lifeStage")}</div>
                    <div>Observation Comments: {store.getFieldValue("attributes", "observationComments")}</div>
                  </div>
                }
              />
            )}

          </Col>
          <Col md={6}>
            <ImageCard
              store={store}              
            />
          </Col>
        </Row>) : (
        <p>TDB</p>
      )}
    </Container>
  );
});

export default Encounter;
