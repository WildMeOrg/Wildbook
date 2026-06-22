import React, { useEffect, useState } from "react";
import axios from "axios";
import { Container } from "react-bootstrap";
import { Row, Col } from "react-bootstrap";
import DateIcon from "../../components/icons/DateIcon";
import MailIcon from "../../components/icons/MailIcon";
import IdentifyIcon from "../../components/IdentifyIcon";
import AttributesIcon from "../../components/icons/AttributesIcon";
import CardWithoutEditButton from "../../components/CardWithoutEditButton";
import { FormattedMessage } from "react-intl";
import LoadingScreen from "../../components/LoadingScreen";

export default function EncounterPageViewOnly() {
  const [data, setData] = React.useState([]);
  const params = new URLSearchParams(window.location.search);
  const encounterId = params.get("number");
  const [selectedImageIndex, setSelectedImageIndex] = React.useState(0);
  const [isPublic, setIsPublic] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    axios
      .get(`/api/v3/encounters/${encounterId}`)
      .then((res) => {
        if (!cancelled) setData(res.data);
        setIsPublic(res.data?.isPublic);
      })
      .catch((err) => {
        throw err;
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [encounterId]);

  if (loading) {
    return <LoadingScreen />;
  }

  if (!isPublic) {
    window.location.href = "/react/login";
    return null;
  }

  return (
    <Container style={{ padding: "20px" }}>
      <Row>
        <Col md={6}>
          <h2>
            <FormattedMessage id="ENCOUNTER" />{" "}
            {data?.individualDisplayName
              ? `of ${data?.individualDisplayName}`
              : "Unassigned "}
          </h2>
          <p>
            <FormattedMessage id="ENCOUNTER_ID" />: {encounterId}
          </p>
        </Col>
      </Row>
      <Row className="mt-3 mb-3">
        <Col md={3}>
          <CardWithoutEditButton
            icon={<DateIcon />}
            title="DATE"
            content={
              <div>
                <div>
                  <FormattedMessage id="ENCOUNTER_DATE" />: {data?.date}
                </div>
                <div>
                  <FormattedMessage id="VERBATIM_EVENT_DATE" />:{" "}
                  {data?.verbatimEventDate}
                </div>
              </div>
            }
          />
          <CardWithoutEditButton
            icon={<IdentifyIcon />}
            title="IDENTIFY"
            content={
              <div>
                <div>
                  <FormattedMessage id="IDENTIFIED_AS" />:{" "}
                  {data?.individualDisplayName}
                </div>
                <div>
                  <FormattedMessage id="MATCHED_BY" />:{" "}
                  {data?.identificationRemarks}
                </div>
                <div>
                  <FormattedMessage id="ALTERNATE_ID" />:{" "}
                  {data?.otherCatalogNumbers}
                </div>
              </div>
            }
          />
        </Col>
        <Col md={3}>
          <CardWithoutEditButton
            icon={<AttributesIcon />}
            title="ATTRIBUTES"
            content={
              <div>
                <div>
                  <FormattedMessage id="TAXONOMY" />: {data?.taxonomy}
                </div>
                <div>
                  <FormattedMessage id="STATUS" />: {data?.livingStatus}
                </div>
                <div>
                  <FormattedMessage id="SEX" />: {data?.sex}
                </div>
                <div>
                  <FormattedMessage id="DISTINGUISHING_SCAR" />:{" "}
                  {data?.distinguishingScar}
                </div>
                <div>
                  <FormattedMessage id="BEHAVIOR" />: {data?.behavior}
                </div>
                <div>
                  <FormattedMessage id="GROUP_ROLE" />: {data?.groupRole}
                </div>
                <div>
                  <FormattedMessage id="PATTERNING_CODE" />:{" "}
                  {data?.patterningCode}
                </div>
                <div>
                  <FormattedMessage id="LIFE_STAGE" />: {data?.lifeStage}
                </div>
                <div>
                  <FormattedMessage id="OBSERVATION_COMMENTS" />:{" "}
                  {data?.occurrenceRemarks}
                </div>
              </div>
            }
          />
        </Col>
        <Col md={6}>
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              height: "100%",
              width: "100%",
              borderRadius: "10px",
              boxShadow: `0px 0px 10px rgba(0, 0, 0, 0.2)`,
              overflow: "hidden",
              padding: "20px",
            }}
          >
            <div
              className="d-flex align-items-center w-100 mb-3"
              style={{ fontSize: "1rem", fontWeight: "bold" }}
            >
              <MailIcon />
              <span style={{ marginLeft: "10px" }}>
                {<FormattedMessage id="IMAGES" />}
              </span>
            </div>
            <div
              className="d-flex flex-wrap align-items-center mt-2"
              style={{ gap: 8, overflowY: "auto", maxHeight: 200 }}
            >
              {data?.mediaAssets?.length > 0 ? (
                <>
                  <img
                    src={data?.mediaAssets?.[selectedImageIndex]?.url}
                    alt={"Encounter Image"}
                    style={{ width: "100%", height: "auto" }}
                  />
                  <div>
                    {data?.mediaAssets?.length > 0 && (
                      <div className="d-flex mt-2 flex-wrap">
                        {data.mediaAssets.map((image, index) => (
                          <img
                            key={index}
                            src={image.url}
                            alt={`img-${index + 1}`}
                            style={{
                              width: "50px",
                              height: "50px",
                              margin: "0 5px",
                              cursor: "pointer",
                              borderRadius: "5px",
                              border:
                                selectedImageIndex === index
                                  ? "2px solid blue"
                                  : "2px solid transparent",
                            }}
                            onClick={() => setSelectedImageIndex(index)}
                          />
                        ))}
                      </div>
                    )}
                  </div>
                </>
              ) : (
                <p>
                  <FormattedMessage id="NO_IMAGE_AVAILABLE" />
                </p>
              )}
            </div>
          </div>
        </Col>
      </Row>
    </Container>
  );
}
