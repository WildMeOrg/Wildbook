import React, { useEffect } from "react";
import axios from "axios";
import { Container } from "react-bootstrap";
import { Row, Col } from "react-bootstrap";
import ActivePill from "../../components/ActivePill";
import DateIcon from "../../components/icons/DateIcon";
import IdentifyIcon from "../../components/IdentifyIcon";
import AttributesIcon from "../../components/icons/AttributesIcon";
import CardWithoutEditButton from "../../components/CardWithoutEditButton";

export default function EncounterPageViewOnly() {
  const [data, setData] = React.useState([]);
  const params = new URLSearchParams(window.location.search);
  const encounterId = params.get("number");

  useEffect(() => {
    let cancelled = false;
    axios
      .get(`/api/v3/encounters/${encounterId}`)
      .then((res) => {
        if (!cancelled) setData(res.data);
      })
      .catch((err) => console.error("fetch encounter error:", err));
    return () => {
      cancelled = true;
    };
  }, [encounterId]);

  return (
    <Container style={{ padding: "20px" }}>
      <Row>
        <Col md={6}>
          <h2>
            Encounter{" "}
            {data?.individualDisplayName
              ? `of ${data?.individualDisplayName}`
              : "Unassigned "}
          </h2>
          <p>Encounter ID: {encounterId}</p>
        </Col>       
      </Row>

      <div style={{ marginTop: "20px", display: "flex", flexDirection: "row" }}>
        <ActivePill text="Overview" style={{ marginRight: "10px" }} />
      </div>
      {
        <Row className="mt-3 mb-3">
          <Col md={3}>
            <CardWithoutEditButton
              icon={<DateIcon />}
              title="Date"
              content={
                <div>
                  <div>Encounter Date: {data?.date}</div>
                  <div>Verbatim Event Date: {data?.verbatimEventDate}</div>
                </div>
              }
            />

            <CardWithoutEditButton
              icon={<IdentifyIcon />}
              title="Identify"
              content={
                <div>
                  <div>Identified as: {data?.individualDisplayName}</div>
                  <div>Matched by: {data?.identificationRemarks}</div>
                  <div>Alternate ID: {data?.otherCatalogNumbers}</div>
                </div>
              }
            />
          </Col>
          <Col md={3}>
            <CardWithoutEditButton
              icon={<AttributesIcon />}
              title="Attributes"
              content={
                <div>
                  <div>Taxonomy: {data?.taxonomy}</div>
                  <div>Status: {data?.livingStatus}</div>
                  <div>Sex: {data?.sex}</div>
                  <div>Noticeable Scarring: {data?.distinguishingScar}</div>
                  <div>Behavior: {data?.behavior}</div>
                  <div>Group Role: {data?.groupRole}</div>
                  <div>Patterning Code: {data?.patterningCode}</div>
                  <div>Life Stage: {data?.lifeStage}</div>
                  <div>Observation Comments: {data?.occurrenceRemarks}</div>
                </div>
              }
            />
          </Col>
          <Col md={6}>
            <CardWithoutEditButton
              icon={<DateIcon />}
              title="Images"
              content={<div>images goes here</div>}
            />
          </Col>
        </Row>
      }
    </Container>
  );
}
