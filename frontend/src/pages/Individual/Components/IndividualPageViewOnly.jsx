import React, { useEffect, useState } from "react";
import axios from "axios";
import { Container, Row, Col } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import CardWithoutEditButton from "../../../components/CardWithoutEditButton";
import DateIcon from "../../../components/icons/DateIcon";
import IdentifyIcon from "../../../components/IdentifyIcon";
import AttributesIcon from "../../../components/icons/AttributesIcon";
import MailIcon from "../../../components/icons/MailIcon";
import LoadingScreen from "../../../components/LoadingScreen";

export default function IndividualPageViewOnly() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [isPublic, setIsPublic] = useState(false);

  const params = new URLSearchParams(window.location.search);
  const individualId = params.get("id");

  useEffect(() => {
    let cancelled = false;

    axios
      .get(`/api/v3/individuals/${individualId}`)
      .then((res) => {
        if (!cancelled) {
          setData(res.data);
          setIsPublic(res.data?.isPublic !== false);
        }
      })
      .catch((err) => {
        console.error("Failed to load individual:", err);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [individualId]);

  if (loading) {
    return <LoadingScreen />;
  }

  if (!isPublic) {
    window.location.href = "/react/login";
    return null;
  }

  const displayName =
    data?.names?.[0]?.displayName || data?.id || "Unknown";
  const alternateIds =
    data?.names
      ?.filter((n) => n.displayName !== displayName)
      .map((n) => n.displayName)
      .filter(Boolean) || [];

  return (
    <Container style={{ padding: "20px" }}>
      {/* Header */}
      <Row className="mb-4">
        <Col md={8}>
          <div className="d-flex align-items-center gap-3">
            <div
              style={{
                width: "80px",
                height: "80px",
                borderRadius: "50%",
                overflow: "hidden",
                border: "3px solid #00ACCE",
                flexShrink: 0,
              }}
            >
              <img
                src={
                  data?.thumbnailUrl ||
                  `${process.env.PUBLIC_URL}/images/Avatar.png`
                }
                alt={displayName}
                style={{
                  width: "100%",
                  height: "100%",
                  objectFit: "cover",
                }}
              />
            </div>
            <div>
              <h2 className="mb-1">{displayName}</h2>
              {alternateIds.length > 0 && (
                <p className="mb-0 text-muted">
                  <FormattedMessage id="ALTERNATE_ID" />:{" "}
                  {alternateIds.join(", ")}
                </p>
              )}
            </div>
          </div>
        </Col>
      </Row>

      {/* Content */}
      <Row className="mt-3 mb-3">
        <Col md={3}>
          <CardWithoutEditButton
            icon={<DateIcon />}
            title="DETAILS"
            content={
              <div>
                <div className="mb-2">
                  <strong>
                    <FormattedMessage id="TAXONOMY" />:
                  </strong>{" "}
                  {data?.taxonomy || "-"}
                </div>
                <div className="mb-2">
                  <strong>
                    <FormattedMessage id="SEX" />:
                  </strong>{" "}
                  {data?.sex || "-"}
                </div>
                <div className="mb-2">
                  <strong>
                    <FormattedMessage id="DATE_OF_BIRTH" />:
                  </strong>{" "}
                  {data?.dateOfBirth || "-"}
                </div>
                <div className="mb-2">
                  <strong>
                    <FormattedMessage id="STATUS" />:
                  </strong>{" "}
                  {data?.livingStatus || "Alive"}
                </div>
                <div className="mb-2">
                  <strong>
                    <FormattedMessage id="IDENTIFIABLE_SCARS" />:
                  </strong>{" "}
                  {data?.identifyingScars || "-"}
                </div>
              </div>
            }
          />
        </Col>
        <Col md={3}>
          <CardWithoutEditButton
            icon={<IdentifyIcon />}
            title="IDENTITY"
            content={
              <div>
                <div className="mb-2">
                  <strong>
                    <FormattedMessage id="INDIVIDUAL_ID" />:
                  </strong>{" "}
                  {data?.id}
                </div>
                <div className="mb-2">
                  <strong>
                    <FormattedMessage id="NAME" />:
                  </strong>{" "}
                  {displayName}
                </div>
              </div>
            }
          />
          <CardWithoutEditButton
            icon={<AttributesIcon />}
            title="ATTRIBUTES"
            content={
              <div>
                <div className="mb-2">
                  <strong>
                    <FormattedMessage id="SPECIES" />:
                  </strong>{" "}
                  {data?.taxonomy || "-"}
                </div>
                <div className="mb-2">
                  <strong>
                    <FormattedMessage id="STATUS" />:
                  </strong>{" "}
                  {data?.livingStatus || "Alive"}
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
              boxShadow: "0px 0px 10px rgba(0, 0, 0, 0.2)",
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
                <FormattedMessage id="IMAGES" />
              </span>
            </div>
            <div>
              {data?.thumbnailUrl ? (
                <img
                  src={data.thumbnailUrl}
                  alt={displayName}
                  style={{ width: "100%", height: "auto", borderRadius: "8px" }}
                />
              ) : (
                <p className="text-muted">
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
