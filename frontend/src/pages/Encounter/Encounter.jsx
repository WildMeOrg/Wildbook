import React from "react";
import MainButton from "../../components/MainButton";
import axios from "axios";
import { observer } from "mobx-react-lite";
import EncounterStore from "./EncounterStore";
import { Container } from "react-bootstrap";
import { Row, Col } from "react-bootstrap";
import ActivePill from "../../components/ActivePill";
import InactivePill from "../../components/InactivePill";
import CardWithSaveAndCancelButtons from "../../components/CardWithSaveAndCancelButtons";
import TextInput from "../../components/TextInput";
import DateIcon from "../../components/DateIcon";
import DateCardContent from "./DateCardContent";
import IdentifyIcon from "../../components/IdentifyIcon";
import MetadataIcon from "../../components/MetaDataIcon";
import LocationIcon from "../../components/LocationIcon";
import AttributesIcon from "../../components/AttributesIcon";
import ImageCard from "./ImageCard";
import CardWithEditButton from "../../components/CardWithEditButton";

const Encounter = observer(() => {
  const store = React.useMemo(() => new EncounterStore(), []);

  const params = new URLSearchParams(window.location.search);
  const encounterId =
    params.get("number") || "4770c075-a4c7-48c3-9616-8fa87aa2d65a";

  React.useEffect(() => {
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
    <Container>
      <h2>Encounter {store.encounterData?.individualNames ?
        store.encounterData?.individualNames.map(
          (name) => !!name)[0] : "Unassigned"}
      </h2>
      <p>Encounter ID: {encounterId}</p>

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

        <Row>
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
                    <TextInput
                      label="Encounter Date"
                      value={store.getFieldValue("date", "encounterDate") ?? ""}
                      onChange={(v) => store.setFieldValue("date", "encounterDate", v)}
                    />
                    <TextInput
                      label="Verbatim Event Date"
                      value={store.getFieldValue("date", "verbatimEventDate") ?? ""}
                      onChange={(v) => store.setFieldValue("date", "verbatimEventDate", v)}
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
                    <div>Encounter Date: {store.getFieldValue("date", "encounterDate") || "None"}</div>
                    <div>Verbatim Event Date: {store.getFieldValue("date", "verbatimEventDate") || "None"}</div>
                  </div>
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
                }}
                onCancel={() => {
                  store.resetSectionDraft("identify");
                  store.setEditIdentifyCard(false);
                }}
                content={
                  <div>
                    <TextInput
                      label="Identified as"
                      value={store.getFieldValue("identify", "individualName") ?? ""}
                      onChange={(v) => store.setFieldValue("identify", "individualName", v)}
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
                    <div>Identified as: {store.getFieldValue("identify", "individualName") || "Unknown"}</div>
                    <div>Matched by: {store.getFieldValue("identify", "matchedBy") || "Unknown"}</div>
                    <div>Alternate ID: {store.getFieldValue("identify", "alternateID") || "None"}</div>
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
                        <a href={`/imports/${store.encounterData.importTaskId}`}>
                          {store.encounterData.importTaskId}
                        </a>
                      ) : "None"}
                    </div>

                    <div style={{ marginTop: 12 }}>
                      <label className="form-label">Assigned User</label>
                      <input
                        className="form-control"
                        value={store.getFieldValue("metadata", "assignedUser") ?? ""}
                        onChange={(e) => store.setFieldValue("metadata", "assignedUser", e.target.value)}
                      />
                    </div>

                    <div style={{ marginTop: 12 }}>
                      <label className="form-label">Sharing Permission</label>
                      <select
                        className="form-select"
                        value={store.getFieldValue("metadata", "sharingPermission") ?? ""}
                        onChange={(e) => store.setFieldValue("metadata", "sharingPermission", e.target.value)}
                      >
                        <option value="">Select…</option>
                        <option value="private">Only me</option>
                        <option value="link_view">Anyone with the link can view</option>
                        <option value="org_view">My organization can view</option>
                      </select>
                    </div>
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
                    <div>Date Created: {store.encounterData?.createdAt}</div>
                    <div>Last Edit: {store.encounterData?.updatedAt}</div>
                    <div>
                      Imported via:{" "}
                      {store.encounterData?.importTaskId ? (
                        <a href={`/imports/${store.encounterData.importTaskId}`}>
                          {store.encounterData.importTaskId}
                        </a>
                      ) : "None"}
                    </div>
                    <div>Assigned User: {store.getFieldValue("metadata", "assignedUser") || "None"}</div>
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
                      value={store.getFieldValue("location", "locationId") ?? ""}
                      onChange={(v) => store.setFieldValue("location", "locationId", v)}
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
                      Coordinates: {store.getFieldValue("location", "decimalLatitude") || "?"}°N{" "}
                      {store.getFieldValue("location", "decimalLongitude") || "?"}°E
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
                    <TextInput
                      label="Taxonomy"
                      value={store.getFieldValue("attributes", "taxonomy") ?? ""}
                      onChange={(v) => store.setFieldValue("attributes", "taxonomy", v)}
                    />
                    <TextInput
                      label="Status"
                      value={store.getFieldValue("attributes", "status") ?? ""}
                      onChange={(v) => store.setFieldValue("attributes", "status", v)}
                    />
                    <TextInput
                      label="Sex"
                      value={store.getFieldValue("attributes", "sex") ?? ""}
                      onChange={(v) => store.setFieldValue("attributes", "sex", v)}
                    />
                    <TextInput
                      label="Noticeable Scarring"
                      value={store.getFieldValue("attributes", "noticeableScarring") ?? ""}
                      onChange={(v) => store.setFieldValue("attributes", "noticeableScarring", v)}
                    />
                    <TextInput
                      label="Behavior"
                      value={store.getFieldValue("attributes", "behavior") ?? ""}
                      onChange={(v) => store.setFieldValue("attributes", "behavior", v)}
                    />
                    <TextInput
                      label="Group Role"
                      value={store.getFieldValue("attributes", "groupRole") ?? ""}
                      onChange={(v) => store.setFieldValue("attributes", "groupRole", v)}
                    />
                    <TextInput
                      label="Patterning Code"
                      value={store.getFieldValue("attributes", "patterningCode") ?? ""}
                      onChange={(v) => store.setFieldValue("attributes", "patterningCode", v)}
                    />
                    <TextInput
                      label="Life Stage"
                      value={store.getFieldValue("attributes", "lifeStage") ?? ""}
                      onChange={(v) => store.setFieldValue("attributes", "lifeStage", v)}
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
                    <div>Taxonomy: {store.getFieldValue("attributes", "taxonomy") || "None"}</div>
                    <div>Status: {store.getFieldValue("attributes", "status") || "None"}</div>
                    <div>Sex: {store.getFieldValue("attributes", "sex") || "Unknown"}</div>
                    <div>Noticeable Scarring: {store.getFieldValue("attributes", "noticeableScarring") || "empty"}</div>
                    <div>Behavior: {store.getFieldValue("attributes", "behavior") || "empty"}</div>
                    <div>Group Role: {store.getFieldValue("attributes", "groupRole") || "None"}</div>
                    <div>Patterning Code: {store.getFieldValue("attributes", "patterningCode") || "None"}</div>
                    <div>Life Stage: {store.getFieldValue("attributes", "lifeStage") || "empty"}</div>
                    <div>Observation Comments: {store.getFieldValue("attributes", "observationComments") || "None"}</div>
                  </div>
                }
              />
            )}

          </Col>
          <Col md={6}>
            <ImageCard />
          </Col>
        </Row>) : (
        <p>TDB</p>
      )}
    </Container>
  );
});

export default Encounter;
