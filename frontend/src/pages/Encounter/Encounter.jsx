import React, { useEffect, useState } from "react";
import axios from "axios";
import { observer } from "mobx-react-lite";
import { Container } from "react-bootstrap";
import { Row, Col } from "react-bootstrap";
import Pill from "../../components/Pill";
import CardWithSaveAndCancelButtons from "../../components/CardWithSaveAndCancelButtons";
import DateIcon from "../../components/icons/DateIcon";
import IdentifyIcon from "../../components/IdentifyIcon";
import MetadataIcon from "../../components/icons/MetaDataIcon";
import LocationIcon from "../../components/icons/LocationIcon";
import AttributesIcon from "../../components/icons/AttributesIcon";
import ImageCard from "./ImageCard";
import CardWithEditButton from "../../components/CardWithEditButton";
import useGetSiteSettings from "../../models/useGetSiteSettings";
import PillWithDropdown from "../../components/PillWithDropdown";
import ContactIcon from "../../components/icons/ContactIcon";
import HistoryIcon from "../../components/icons/HistoryIcon";
import ContactInfoModal from "./ContactInfoModal";
import { MoreDetails } from "./MoreDetails";
import EncounterHistoryModal from "./EncounterHistoryModal";
import MatchCriteriaModal from "./MatchCriteria";
import { EncounterStore } from "./stores";
import { DateSectionReview } from "./DateSectionReview";
import { IdentifySectionReview } from "./IdentifySectionReview";
import { MetadataSectionReview } from "./MetadataSectionReview";
import { LocationSectionReview } from "./LocationSectionReview";
import { AttributesSectionReview } from "./AttributesSectionReview";
import { DateSectionEdit } from "./DateSectionEdit";
import { IdentifySectionEdit } from "./IdentifySectionEdit";
import { MetadataSectionEdit } from "./MetadataSectionEdit";
import { LocationSectionEdit } from "./LocationSectionEdit";
import { AttributesSectionEdit } from "./AttributesSectionEdit";
import { FormattedMessage, useIntl } from "react-intl";
import DeleteEncounterCard from "./DeleteEncounterCard";
import Modal from "react-bootstrap/Modal";
import { Divider } from "antd";
import { get } from "lodash-es";
import CollabModal from "./CollabModal";
import Alert from "react-bootstrap/Alert";

const Encounter = observer(() => {
  const [store] = useState(() => new EncounterStore());
  const { data: siteSettings } = useGetSiteSettings();
  const [encounterValid, setEncounterValid] = useState(true);
  const [encounterDeleted, setEncounterDeleted] = useState(false);
  const intl = useIntl();

  const encounterStates = store.siteSettingsData?.encounterState;
  const encounterStatesLoaded = encounterStates !== undefined;
  const encounterStatesOptions = encounterStatesLoaded
    ? encounterStates.map((state) => ({ value: state, label: state }))
    : [{ value: "loading", label: "loading" }];

	console.log("testing claude");

  const rawState = store.encounterData?.state || "";
  const selectedState = !encounterStatesLoaded
    ? "loading"
    : encounterStates.includes(rawState)
      ? rawState
      : "";

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
        store.setAccess(get(res.data, "access", "read"));
      })
      .catch((_err) => setEncounterValid(false));
    return () => {
      cancelled = true;
    };
  }, [encounterId, store]);

  if (store.access === "read") {
    return (
      <Container style={{ padding: "20px" }}>
        {!encounterValid && (
          <Modal
            show
            onHide={() => {
              window.location.href = "/react";
            }}
          >
            <Modal.Header closeButton>
              <Modal.Title>
                <FormattedMessage id="ENCOUNTER_NOT_FOUND" />
              </Modal.Title>
            </Modal.Header>
            <Modal.Body>
              <p>
                <FormattedMessage id="ENCOUNTER_NOT_FOUND_DESC" />
              </p>
            </Modal.Body>
          </Modal>
        )}
        {encounterDeleted && (
          <Modal show onHide={() => (window.location.href = "/react")}>
            <Modal.Header closeButton>
              <Modal.Title>
                {encounterDeleted === "success" ? (
                  <FormattedMessage id="ENCOUNTER_DELETED" />
                ) : (
                  <FormattedMessage id="ENCOUNTER_DELETED_ERROR" />
                )}
              </Modal.Title>
            </Modal.Header>
            <Modal.Body>
              <p>
                {encounterDeleted === "success" ? (
                  <FormattedMessage id="ENCOUNTER_DELETED_DESC" />
                ) : (
                  <FormattedMessage id="ENCOUNTER_DELETED_ERROR_DESC" />
                )}
              </p>
            </Modal.Body>
          </Modal>
        )}
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
        <Row>
          <Col md={6}>
            <h2>
              <FormattedMessage id="ENCOUNTER" />{" "}
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

            <p>
              <FormattedMessage id="ENCOUNTER_ID" />: {encounterId}
            </p>
          </Col>
          <Col md={6} className="text-end">
            <Pill
              text={store.encounterData?.state || "loading"}
              style={{ marginRight: "10px" }}
              active={false}
            />
          </Col>
        </Row>

        <div
          style={{ marginTop: "20px", display: "flex", flexDirection: "row" }}
        >
          <div>
            <Pill
              text="OVERVIEW"
              style={{ marginRight: "10px" }}
              active={store.overviewActive}
              onClick={() => {
                if (store.overviewActive) return;
                store.setOverviewActive(true);
              }}
            />
            <Pill
              text="MORE_DETAILS"
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
              <CardWithEditButton
                icon={<DateIcon />}
                title="DATE"
                content={<DateSectionReview store={store} />}
                showEditButton={false}
              />
              <CardWithEditButton
                icon={<IdentifyIcon />}
                title="IDENTITY"
                content={<IdentifySectionReview store={store} />}
                showEditButton={false}
              />
              <CardWithEditButton
                icon={<MetadataIcon />}
                title="METADATA"
                content={<MetadataSectionReview store={store} />}
                showEditButton={false}
              />
            </Col>
            <Col md={3}>
              <CardWithEditButton
                icon={<LocationIcon />}
                title="LOCATION"
                content={<LocationSectionReview store={store} />}
                showEditButton={false}
              />
              <CardWithEditButton
                icon={<AttributesIcon />}
                title="ATTRIBUTES"
                content={<AttributesSectionReview store={store} />}
                showEditButton={false}
              />
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
  }

  if (store.access === "none") {
    return <CollabModal store={store} />;
  }

  return (
    <Container style={{ padding: "20px" }}>
      {!encounterValid && (
        <Modal
          show
          onHide={() => {
            window.location.href = "/react";
          }}
        >
          <Modal.Header closeButton>
            <Modal.Title>
              <FormattedMessage id="ENCOUNTER_NOT_FOUND" />
            </Modal.Title>
          </Modal.Header>
          <Modal.Body>
            <p>
              <FormattedMessage id="ENCOUNTER_NOT_FOUND_DESC" />
            </p>
          </Modal.Body>
        </Modal>
      )}
      {encounterDeleted && (
        <Modal show onHide={() => (window.location.href = "/react")}>
          <Modal.Header closeButton>
            <Modal.Title>
              {encounterDeleted === "success" ? (
                <FormattedMessage id="ENCOUNTER_DELETED" />
              ) : (
                <FormattedMessage id="ENCOUNTER_DELETED_ERROR" />
              )}
            </Modal.Title>
          </Modal.Header>
          <Modal.Body>
            <p>
              {encounterDeleted === "success" ? (
                <FormattedMessage id="ENCOUNTER_DELETED_DESC" />
              ) : (
                <FormattedMessage id="ENCOUNTER_DELETED_ERROR_DESC" />
              )}
            </p>
          </Modal.Body>
        </Modal>
      )}
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
            <FormattedMessage id="ENCOUNTER" />{" "}
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

          <p>
            <FormattedMessage id="ENCOUNTER_ID" />: {encounterId}
          </p>
        </Col>
        <Col md={6} className="text-end">
          <PillWithDropdown
            options={encounterStatesOptions}
            selectedOption={selectedState}
            onSelect={async (value) => {
              await store.changeEncounterState(value);
            }}
          />
          {!!store.errors.getFieldError("header", "state") && (
            <div className="mt-2 d-flex justify-content-end">
              <Alert
                variant="danger"
                className="mb-0 py-1 px-2"
                style={{ maxWidth: 420 }}
              >
                {store.errors.getFieldError("header", "state")}
              </Alert>
            </div>
          )}
        </Col>
      </Row>

      <div style={{ marginTop: "20px", display: "flex", flexDirection: "row" }}>
        <div>
          <Pill
            text="OVERVIEW"
            style={{ marginRight: "10px" }}
            active={store.overviewActive}
            onClick={() => {
              if (store.overviewActive) return;
              store.setOverviewActive(true);
            }}
          />
          <Pill
            text="MORE_DETAILS"
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
            title={intl.formatMessage({ id: "CONTACT_INFORMATION" })}
            onClick={() => {
              store.modals.setOpenContactInfoModal(true);
            }}
          >
            <ContactIcon />
          </div>
          <div
            style={{ marginRight: "10px", cursor: "pointer" }}
            title={intl.formatMessage({ id: "ENCOUNTER_HISTORY" })}
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
                title="DATE"
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
                content={<DateSectionEdit store={store} />}
                styles={{
                  overflow: "visible",
                }}
              />
            ) : (
              <CardWithEditButton
                icon={<DateIcon />}
                title="DATE"
                onClick={() => store.setEditDateCard(true)}
                content={<DateSectionReview store={store} />}
              />
            )}

            {store.editIdentifyCard ? (
              <CardWithSaveAndCancelButtons
                icon={<IdentifyIcon />}
                title="IDENTITY"
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
                content={<IdentifySectionEdit store={store} />}
              />
            ) : (
              <CardWithEditButton
                icon={<IdentifyIcon />}
                title="IDENTITY"
                onClick={() => store.setEditIdentifyCard(true)}
                content={<IdentifySectionReview store={store} />}
              />
            )}

            {store.editMetadataCard ? (
              <CardWithSaveAndCancelButtons
                icon={<MetadataIcon />}
                title="METADATA"
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
                content={<MetadataSectionEdit store={store} />}
              />
            ) : (
              <CardWithEditButton
                icon={<MetadataIcon />}
                title="METADATA"
                onClick={() => store.setEditMetadataCard(true)}
                content={<MetadataSectionReview store={store} />}
              />
            )}
          </Col>
          <Col md={3}>
            {store.editLocationCard ? (
              <CardWithSaveAndCancelButtons
                icon={<LocationIcon />}
                disabled={
                  !!store.errors.getFieldError("location", "latitude") ||
                  !!store.errors.getFieldError("location", "longitude")
                }
                title="LOCATION"
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
                content={<LocationSectionEdit store={store} />}
              />
            ) : (
              <CardWithEditButton
                icon={<LocationIcon />}
                title="LOCATION"
                onClick={() => store.setEditLocationCard(true)}
                content={<LocationSectionReview store={store} />}
              />
            )}

            {store.editAttributesCard ? (
              <CardWithSaveAndCancelButtons
                icon={<AttributesIcon />}
                title="ATTRIBUTES"
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
                content={<AttributesSectionEdit store={store} />}
              />
            ) : (
              <CardWithEditButton
                icon={<AttributesIcon />}
                title="ATTRIBUTES"
                onClick={() => store.setEditAttributesCard(true)}
                content={<AttributesSectionReview store={store} />}
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
      <Divider />
      <Row className="mt-3">
        <Col md={12}>
          <DeleteEncounterCard
            id={encounterId}
            individualId={store.encounterData?.individualId}
            setEncounterDeleted={setEncounterDeleted}
          />
        </Col>
      </Row>
    </Container>
  );
});

export default Encounter;
