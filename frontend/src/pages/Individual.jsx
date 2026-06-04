import React, { useState, useEffect, useCallback } from "react";
import axios from "axios";
import { useParams } from "react-router-dom";
import { Container, Row, Col } from "react-bootstrap";
import Modal from "react-bootstrap/Modal";
import { FormattedMessage } from "react-intl";
import CardWithEditButton from "../components/CardWithEditButton";
import CardWithSaveAndCancelButtons from "../components/CardWithSaveAndCancelButtons";
import { AttributesAndValueComponent } from "../components/AttributesAndValueComponent";
import SimpleDataTable from "../components/SimpleDataTable";
import LoadingScreen from "../components/LoadingScreen";
import Pill from "../components/Pill";
import useDocumentTitle from "../hooks/useDocumentTitle";
import DateIcon from "../components/icons/DateIcon";
import AttributesIcon from "../components/icons/AttributesIcon";
import MetadataIcon from "../components/icons/MetaDataIcon";

const SEX_OPTIONS = ["unknown", "male", "female"];

function formatDate(isoString) {
  if (!isoString) return null;
  return isoString.slice(0, 10);
}

export default function Individual() {
  useDocumentTitle();

  const { id } = useParams();

  const [individual, setIndividual] = useState(null);
  const [encounters, setEncounters] = useState([]);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [overviewActive, setOverviewActive] = useState(true);

  // edit states
  const [editIdentity, setEditIdentity] = useState(false);
  const [editNames, setEditNames] = useState(false);
  const [editComments, setEditComments] = useState(false);

  // drafts
  const [draftSex, setDraftSex] = useState("");
  const [draftTimeOfBirth, setDraftTimeOfBirth] = useState("");
  const [draftTimeOfDeath, setDraftTimeOfDeath] = useState("");
  const [draftNickname, setDraftNickname] = useState("");
  const [draftAlternateId, setDraftAlternateId] = useState("");
  const [draftComment, setDraftComment] = useState("");

  const canEdit = individual?.access === "write";

  const fetchIndividual = useCallback(async () => {
    try {
      const res = await axios.get(`/api/v3/individuals/${id}`);
      if (res.data?.success) {
        setIndividual(res.data);
        setNotFound(false);
      } else {
        setNotFound(true);
      }
    } catch (_err) {
      setNotFound(true);
    }
  }, [id]);

  const fetchEncounters = useCallback(async () => {
    try {
      const res = await axios.get(`/api/v3/individuals/${id}/encounters`);
      if (res.data?.success) {
        setEncounters(res.data.encounters || []);
      }
    } catch (_err) {
      setEncounters([]);
    }
  }, [id]);

  useEffect(() => {
    setLoading(true);
    setNotFound(false);
    setIndividual(null);
    setEncounters([]);
    Promise.all([fetchIndividual(), fetchEncounters()]).finally(() =>
      setLoading(false),
    );
  }, [id, fetchIndividual, fetchEncounters]);

  const patch = useCallback(
    async (ops) => {
      await axios.patch(`/api/v3/individuals/${id}`, ops);
      await fetchIndividual();
    },
    [id, fetchIndividual],
  );

  if (loading) return <LoadingScreen />;

  if (notFound) {
    return (
      <Modal show onHide={() => (window.location.href = "/react")}>
        <Modal.Header closeButton>
          <Modal.Title>
            <FormattedMessage id="INDIVIDUAL_NOT_FOUND" />
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <FormattedMessage id="INDIVIDUAL_NOT_FOUND_DESC" />
        </Modal.Body>
      </Modal>
    );
  }

  const nickName = individual?.nameMap?.Nickname?.[0] || null;
  const alternateId = individual?.nameMap?.["Alternate ID"]?.[0] || null;

  const encounterColumns = [
    {
      name: <FormattedMessage id="ENCOUNTER_ID" />,
      selector: (row) => row.id,
      cell: (row) => (
        <a
          href={`/react/encounter?number=${row.id}`}
          target="_blank"
          rel="noopener noreferrer"
        >
          {row.id}
        </a>
      ),
    },
    {
      name: <FormattedMessage id="DATE" />,
      selector: (row) => row.date,
      cell: (row) => row.date || "-",
    },
    {
      name: <FormattedMessage id="LOCATION" />,
      selector: (row) => row.locationID,
      cell: (row) => row.locationID || "-",
    },
    {
      name: <FormattedMessage id="STATE" />,
      selector: (row) => row.state,
      cell: (row) => row.state || "-",
    },
    {
      name: <FormattedMessage id="TAXONOMY" />,
      selector: (row) => row.taxonomy,
      cell: (row) => row.taxonomy || "-",
    },
  ];

  const IdentityReview = (
    <div>
      <AttributesAndValueComponent attributeId="SEX" value={individual?.sex} />
      <AttributesAndValueComponent
        attributeId="LIFE_STAGE"
        value={individual?.lifeStage}
      />
      <AttributesAndValueComponent
        attributeId="BIRTH_DATE"
        value={formatDate(individual?.timeOfBirth)}
      />
      <AttributesAndValueComponent
        attributeId="DEATH_DATE"
        value={formatDate(individual?.timeOfDeath)}
      />
      <AttributesAndValueComponent
        attributeId="TAXONOMY"
        value={individual?.taxonomy}
      />
    </div>
  );

  const IdentityEdit = (
    <div>
      <div className="mb-3">
        <label className="form-label">
          <FormattedMessage id="SEX" />
        </label>
        <select
          className="form-select form-select-sm"
          value={draftSex}
          onChange={(e) => setDraftSex(e.target.value)}
        >
          {SEX_OPTIONS.map((opt) => (
            <option key={opt} value={opt}>
              {opt}
            </option>
          ))}
        </select>
      </div>
      <div className="mb-3">
        <label className="form-label">
          <FormattedMessage id="BIRTH_DATE" />
        </label>
        <input
          type="date"
          className="form-control form-control-sm"
          value={draftTimeOfBirth}
          onChange={(e) => setDraftTimeOfBirth(e.target.value)}
        />
      </div>
      <div className="mb-3">
        <label className="form-label">
          <FormattedMessage id="DEATH_DATE" />
        </label>
        <input
          type="date"
          className="form-control form-control-sm"
          value={draftTimeOfDeath}
          onChange={(e) => setDraftTimeOfDeath(e.target.value)}
        />
      </div>
    </div>
  );

  const NamesReview = (
    <div>
      <AttributesAndValueComponent
        attributeId="NICKNAME"
        value={nickName}
      />
      <AttributesAndValueComponent
        attributeId="ALTERNATE_ID"
        value={alternateId}
      />
      {individual?.names?.filter(
        (n) => n !== nickName && n !== alternateId,
      ).map((name, i) => (
        <div key={i} className="mb-1">
          <small className="text-muted">{name}</small>
        </div>
      ))}
    </div>
  );

  const NamesEdit = (
    <div>
      <div className="mb-3">
        <label className="form-label">
          <FormattedMessage id="NICKNAME" />
        </label>
        <input
          type="text"
          className="form-control form-control-sm"
          value={draftNickname}
          onChange={(e) => setDraftNickname(e.target.value)}
        />
      </div>
      <div className="mb-3">
        <label className="form-label">
          <FormattedMessage id="ALTERNATE_ID" />
        </label>
        <input
          type="text"
          className="form-control form-control-sm"
          value={draftAlternateId}
          onChange={(e) => setDraftAlternateId(e.target.value)}
        />
      </div>
    </div>
  );

  const SocialContent = (
    <div>
      {individual?.relationships?.length > 0 ? (
        <>
          <h6 className="mb-2">
            <FormattedMessage id="SOCIAL_RELATIONSHIPS" />
          </h6>
          {individual.relationships.map((rel, i) => (
            <div key={i} className="mb-2">
              <a
                href={`/react/individual/${rel.partnerId}`}
                className="text-decoration-none"
              >
                {rel.partnerName || rel.partnerId}
              </a>
              {rel.role && (
                <small className="text-muted ms-1">({rel.role})</small>
              )}
            </div>
          ))}
        </>
      ) : null}
      {individual?.socialUnits?.length > 0 ? (
        <>
          <h6 className="mb-2 mt-3">
            <FormattedMessage id="SOCIAL_UNITS" />
          </h6>
          {individual.socialUnits.map((su, i) => (
            <div key={i} className="mb-1">
              <span>{su.name}</span>
              {su.role && (
                <small className="text-muted ms-1">({su.role})</small>
              )}
            </div>
          ))}
        </>
      ) : null}
      {!individual?.relationships?.length &&
        !individual?.socialUnits?.length && (
          <small className="text-muted">—</small>
        )}
    </div>
  );

  const CommentsReview = (
    <div>
      {individual?.comments && individual.comments !== "None" ? (
        <div
          dangerouslySetInnerHTML={{ __html: individual.comments }}
          style={{ fontSize: "0.9rem" }}
        />
      ) : (
        <small className="text-muted">—</small>
      )}
    </div>
  );

  const CommentsEdit = (
    <div>
      <div className="mb-2" style={{ fontSize: "0.85rem", opacity: 0.6 }}>
        <FormattedMessage id="ADD_COMMENT" />
      </div>
      <textarea
        className="form-control form-control-sm"
        rows={4}
        value={draftComment}
        onChange={(e) => setDraftComment(e.target.value)}
      />
    </div>
  );

  const MetadataContent = (
    <div>
      <AttributesAndValueComponent
        attributeId="INDIVIDUAL_ID"
        value={individual?.id}
      />
      <AttributesAndValueComponent
        attributeId="LAST_EDIT"
        value={
          individual?.version
            ? new Date(individual.version).toLocaleString()
            : null
        }
      />
    </div>
  );

  return (
    <Container style={{ padding: "20px" }}>
      <Row className="mb-2">
        <Col>
          <h2>{individual?.displayName || individual?.id}</h2>
          {individual?.taxonomy && (
            <p className="text-muted mb-0">
              <em>{individual.taxonomy}</em>
            </p>
          )}
          <p className="text-muted" style={{ fontSize: "0.85rem" }}>
            <FormattedMessage id="INDIVIDUAL_ID" />: {individual?.id}
          </p>
        </Col>
      </Row>

      <div style={{ marginTop: "8px", display: "flex", flexDirection: "row" }}>
        <Pill
          text="OVERVIEW"
          style={{ marginRight: "10px" }}
          active={overviewActive}
          onClick={() => setOverviewActive(true)}
        />
        <Pill
          text="MORE_DETAILS"
          active={!overviewActive}
          onClick={() => setOverviewActive(false)}
        />
      </div>

      {overviewActive ? (
        <Row className="mt-3 mb-3">
          <Col md={3}>
            {editIdentity && canEdit ? (
              <CardWithSaveAndCancelButtons
                icon={<AttributesIcon />}
                title="IDENTITY"
                content={IdentityEdit}
                onSave={async () => {
                  const ops = [];
                  if (draftSex)
                    ops.push({ op: "replace", path: "sex", value: draftSex });
                  if (draftTimeOfBirth)
                    ops.push({
                      op: "replace",
                      path: "timeOfBirth",
                      value: draftTimeOfBirth,
                    });
                  if (draftTimeOfDeath)
                    ops.push({
                      op: "replace",
                      path: "timeOfDeath",
                      value: draftTimeOfDeath,
                    });
                  if (ops.length) await patch(ops);
                  setEditIdentity(false);
                }}
                onCancel={() => {
                  setDraftSex(individual?.sex || "");
                  setDraftTimeOfBirth(formatDate(individual?.timeOfBirth) || "");
                  setDraftTimeOfDeath(formatDate(individual?.timeOfDeath) || "");
                  setEditIdentity(false);
                }}
              />
            ) : (
              <CardWithEditButton
                icon={<AttributesIcon />}
                title="IDENTITY"
                content={IdentityReview}
                showEditButton={canEdit}
                onClick={() => {
                  setDraftSex(individual?.sex || "unknown");
                  setDraftTimeOfBirth(formatDate(individual?.timeOfBirth) || "");
                  setDraftTimeOfDeath(formatDate(individual?.timeOfDeath) || "");
                  setEditIdentity(true);
                }}
              />
            )}

            {editNames && canEdit ? (
              <CardWithSaveAndCancelButtons
                icon={<MetadataIcon />}
                title="NAMES"
                content={NamesEdit}
                onSave={async () => {
                  const ops = [];
                  if (draftNickname)
                    ops.push({
                      op: "add",
                      path: "nickName",
                      value: draftNickname,
                    });
                  if (draftAlternateId)
                    ops.push({
                      op: "add",
                      path: "alternateid",
                      value: draftAlternateId,
                    });
                  if (ops.length) await patch(ops);
                  setEditNames(false);
                }}
                onCancel={() => {
                  setDraftNickname("");
                  setDraftAlternateId("");
                  setEditNames(false);
                }}
              />
            ) : (
              <CardWithEditButton
                icon={<MetadataIcon />}
                title="NAMES"
                content={NamesReview}
                showEditButton={canEdit}
                onClick={() => {
                  setDraftNickname(nickName || "");
                  setDraftAlternateId(alternateId || "");
                  setEditNames(true);
                }}
              />
            )}
          </Col>

          <Col md={3}>
            <CardWithEditButton
              icon={<DateIcon />}
              title="SOCIAL_RELATIONSHIPS"
              content={SocialContent}
              showEditButton={false}
            />
          </Col>

          <Col md={6}>
            <CardWithEditButton
              icon={<DateIcon />}
              title="ENCOUNTERS"
              content={
                <SimpleDataTable
                  columns={encounterColumns}
                  data={encounters}
                  perPage={10}
                />
              }
              showEditButton={false}
            />
          </Col>
        </Row>
      ) : (
        <Row className="mt-3 mb-3">
          <Col md={6}>
            {editComments && canEdit ? (
              <CardWithSaveAndCancelButtons
                icon={<MetadataIcon />}
                title="COMMENTS"
                content={CommentsEdit}
                onSave={async () => {
                  if (draftComment.trim()) {
                    await patch([
                      {
                        op: "add",
                        path: "comments",
                        value: draftComment.trim(),
                      },
                    ]);
                  }
                  setDraftComment("");
                  setEditComments(false);
                }}
                onCancel={() => {
                  setDraftComment("");
                  setEditComments(false);
                }}
              />
            ) : (
              <CardWithEditButton
                icon={<MetadataIcon />}
                title="COMMENTS"
                content={CommentsReview}
                showEditButton={canEdit}
                onClick={() => setEditComments(true)}
              />
            )}
          </Col>
          <Col md={6}>
            <CardWithEditButton
              icon={<MetadataIcon />}
              title="METADATA"
              content={MetadataContent}
              showEditButton={false}
            />
          </Col>
        </Row>
      )}
    </Container>
  );
}
