import React from "react";
import {
  Modal,
  Row,
  Col,
  Nav,
  Card,
  Button,
  Form,
  Stack,
  Spinner,
  Alert,
} from "react-bootstrap";
import { useState } from "react";
import { FormattedMessage } from "react-intl";

const downloadFunction = async (url, setLoading) => {
  setLoading(true);
  try {
    const response = await fetch(url, {
      method: "GET",
    });

    if (!response.ok) {
      throw new Error("Download failed");
    }

    const blob = await response.blob();
    const downloadUrl = window.URL.createObjectURL(blob);

    const contentDisposition = response.headers.get("content-disposition");
    let filename = "export.xlsx";
    if (contentDisposition) {
      const filenameMatch = contentDisposition.match(
        /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/,
      );
      if (filenameMatch && filenameMatch[1]) {
        filename = filenameMatch[1].replace(/['"]/g, "");
      }
    }

    const a = document.createElement("a");
    a.href = downloadUrl;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(downloadUrl);

    return { success: true };
  } catch (error) {
    console.error("Download error:", error);
    return { success: false, error: error.message };
  } finally {
    setLoading(false);
  }
};

export default function ExportDialog({ open, setOpen, searchQueryId }) {
  const [activeSection, setActiveSection] = useState("encounters");
  const [includeLocale, setIncludeLocale] = useState(false);
  const [timeline, setTimeline] = useState(false);
  const [numberSessions, setNumberSessions] = useState(3);
  const [includeQueryComments, setIncludeQueryComments] = useState(false);
  const [includeIndividialId, setIncludeIndividialId] = useState(false);

  const [loadingStates, setLoadingStates] = useState({
    standardFormat: false,
    encounterAnnotation: false,
    cocoFormat: false,
    obisFormat: false,
    emailAddresses: false,
    googleEarth: false,
    gisShapefile: false,
  });

  const [error, setError] = useState(null);

  const setLoading = (key, value) => {
    setLoadingStates((prev) => ({ ...prev, [key]: value }));
  };

  const handleDownload = async (url, loadingKey) => {
    setError(null);
    const result = await downloadFunction(url, (val) =>
      setLoading(loadingKey, val),
    );
    if (!result.success) {
      setError(`Failed to export: ${result.error}`);
    }
  };

  const scrollToSection = (sectionId) => {
    setActiveSection(sectionId);
    const element = document.getElementById(sectionId);
    if (element) {
      element.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  };

  return (
    <Modal
      show={open}
      onHide={() => setOpen(false)}
      size="xl"
      scrollable
      mountOnEnter
      unmountOnExit
    >
      <Modal.Header closeButton>
        <Modal.Title>
          <FormattedMessage id="EXPORT_ENCOUNTER_RECORDS" />
        </Modal.Title>
      </Modal.Header>

      <Modal.Body style={{ minHeight: "500px" }}>
        {error && (
          <Alert
            variant="danger"
            dismissible
            onClose={() => setError(null)}
            className="mb-3"
          >
            {error}
          </Alert>
        )}

        <Row className="g-4">
          <Col xs={12} md={3}>
            <Nav variant="pills" className="flex-md-column gap-2">
              <Nav.Item>
                <Nav.Link
                  active={activeSection === "encounters"}
                  onClick={() => scrollToSection("encounters")}
                  style={{ cursor: "pointer" }}
                >
                  <FormattedMessage id="ENCOUNTER_RECORDS" />
                </Nav.Link>
              </Nav.Item>
              <Nav.Item>
                <Nav.Link
                  active={activeSection === "gis"}
                  onClick={() => scrollToSection("gis")}
                  style={{ cursor: "pointer" }}
                >
                  <FormattedMessage id="GIS_DATA" />
                </Nav.Link>
              </Nav.Item>
              <Nav.Item>
                <Nav.Link
                  active={activeSection === "markrecapture"}
                  onClick={() => scrollToSection("markrecapture")}
                  style={{ cursor: "pointer" }}
                >
                  <FormattedMessage id="MARK_RECAPTURE_DATA" />
                </Nav.Link>
              </Nav.Item>
            </Nav>
          </Col>

          <Col xs={12} md={9}>
            <Stack gap={4}>
              <div id="encounters">
                <h5 className="mb-3">
                  <FormattedMessage id="ENCOUNTER_RECORDS" />
                </h5>
                <Stack gap={3}>
                  <Card className="shadow-sm">
                    <Card.Body>
                      <Card.Title as="h6" className="mb-1">
                        <FormattedMessage id="STANDARD_FORMAT" />
                      </Card.Title>
                      <div className="d-flex gap-2">
                        <Button
                          className="my-3"
                          variant="outline-primary"
                          size="sm"
                          onClick={() =>
                            handleDownload(
                              `/EncounterSearchExportMetadataExcel?searchQueryId=${searchQueryId}&regularQuery=true`,
                              "standardFormat",
                            )
                          }
                          disabled={loadingStates.standardFormat}
                        >
                          {loadingStates.standardFormat ? (
                            <>
                              <Spinner
                                as="span"
                                animation="border"
                                size="sm"
                                role="status"
                                aria-hidden="true"
                                className="me-2"
                              />
                              <FormattedMessage id="EXPORTING" />
                            </>
                          ) : (
                            <FormattedMessage id="EXPORT_EXCEL_FILE" />
                          )}
                        </Button>
                      </div>
                    </Card.Body>
                  </Card>

                  <Card className="shadow-sm">
                    <Card.Body>
                      <Card.Title as="h6" className="mb-1">
                        <FormattedMessage id="ENCOUNTER_ANNOTATION" />
                      </Card.Title>
                      <div className="d-flex gap-2">
                        <Button
                          className="my-3"
                          variant="outline-primary"
                          size="sm"
                          onClick={() =>
                            handleDownload(
                              `/EncounterAnnotationExportExcelFile?searchQueryId=${searchQueryId}&regularQuery=true`,
                              "encounterAnnotation",
                            )
                          }
                          disabled={loadingStates.encounterAnnotation}
                        >
                          {loadingStates.encounterAnnotation ? (
                            <>
                              <Spinner
                                as="span"
                                animation="border"
                                size="sm"
                                role="status"
                                aria-hidden="true"
                                className="me-2"
                              />
                              <FormattedMessage id="EXPORTING" />
                            </>
                          ) : (
                            <FormattedMessage id="EXPORT_EXCEL_FILE" />
                          )}
                        </Button>
                      </div>
                    </Card.Body>
                  </Card>

                  <Card className="shadow-sm">
                    <Card.Body>
                      <Card.Title as="h6" className="mb-1">
                        <FormattedMessage id="COCO_FORMAT" />
                      </Card.Title>
                      <Card.Text className="text-muted small">
                        <FormattedMessage id="COCO_FORMAT_DESCRIPTION" />
                      </Card.Text>
                      <div className="d-flex gap-2">
                        <Button
                          className="my-3"
                          variant="outline-primary"
                          size="sm"
                          onClick={() =>
                            handleDownload(
                              `/EncounterSearchExportCOCO?searchQueryId=${searchQueryId}&regularQuery=true`,
                              "cocoFormat",
                            )
                          }
                          disabled={loadingStates.cocoFormat}
                        >
                          {loadingStates.cocoFormat ? (
                            <>
                              <Spinner
                                as="span"
                                animation="border"
                                size="sm"
                                role="status"
                                aria-hidden="true"
                                className="me-2"
                              />
                              <FormattedMessage id="EXPORTING" />
                            </>
                          ) : (
                            <FormattedMessage id="EXPORT_ZIP_FILE" />
                          )}
                        </Button>
                      </div>
                    </Card.Body>
                  </Card>

                  <Card className="shadow-sm">
                    <Card.Body>
                      <Card.Title as="h6" className="mb-1">
                        <FormattedMessage id="OBIS_FORMAT" />
                      </Card.Title>
                      <Form.Check
                        type="checkbox"
                        id="includeLocale"
                        label="Include locale information for unreported GPS data."
                        className="mb-3"
                        onChange={(e) => setIncludeLocale(e.target.checked)}
                      />
                      <div className="d-flex gap-2">
                        <Button
                          className="my-3"
                          variant="outline-primary"
                          size="sm"
                          onClick={() => {
                            const url = includeLocale
                              ? `/EncounterSearchExportExcelFile?searchQueryId=${searchQueryId}&regularQuery=true&locales=true`
                              : `/EncounterSearchExportExcelFile?searchQueryId=${searchQueryId}&regularQuery=true`;
                            handleDownload(url, "obisFormat");
                          }}
                          disabled={loadingStates.obisFormat}
                        >
                          {loadingStates.obisFormat ? (
                            <>
                              <Spinner
                                as="span"
                                animation="border"
                                size="sm"
                                role="status"
                                aria-hidden="true"
                                className="me-2"
                              />
                              <FormattedMessage id="EXPORTING" />
                            </>
                          ) : (
                            <FormattedMessage id="EXPORT_EXCEL_FILE" />
                          )}
                        </Button>
                      </div>
                    </Card.Body>
                  </Card>

                  <Card className="shadow-sm">
                    <Card.Body>
                      <Card.Title as="h6" className="mb-1">
                        <FormattedMessage id="DATA_CONTRIBUTOR_EMAIL" />
                      </Card.Title>
                      <div className="d-flex gap-2">
                        <Button
                          className="my-3"
                          variant="outline-primary"
                          size="sm"
                          onClick={() =>
                            handleDownload(
                              `/EncounterSearchExportEmailAddresses?searchQueryId=${searchQueryId}&regularQuery=true`,
                              "emailAddresses",
                            )
                          }
                          disabled={loadingStates.emailAddresses}
                        >
                          {loadingStates.emailAddresses ? (
                            <>
                              <Spinner
                                as="span"
                                animation="border"
                                size="sm"
                                role="status"
                                aria-hidden="true"
                                className="me-2"
                              />
                              <FormattedMessage id="EXPORTING" />
                            </>
                          ) : (
                            <FormattedMessage id="EXPORT_EXCEL_FILE" />
                          )}
                        </Button>
                      </div>
                    </Card.Body>
                  </Card>
                </Stack>
              </div>

              <div id="gis">
                <h5 className="mb-3">
                  <FormattedMessage id="GIS_DATA" />
                </h5>
                <Stack gap={3}>
                  <Card className="shadow-sm">
                    <Card.Body>
                      <Card.Title as="h6" className="mb-1">
                        <FormattedMessage id="GOOGLE_EARTH_FILE" />
                      </Card.Title>
                      <Form.Check
                        type="checkbox"
                        id="timeline"
                        label="Include timeline data."
                        className="mb-3"
                        onChange={(e) => setTimeline(e.target.checked)}
                      />
                      <div className="d-flex gap-2">
                        <Button
                          className="my-3"
                          variant="outline-primary"
                          size="sm"
                          onClick={() => {
                            const url = timeline
                              ? `/EncounterSearchExportKML?searchQueryId=${searchQueryId}&regularQuery=true&addTimeStamp=true`
                              : `/EncounterSearchExportKML?searchQueryId=${searchQueryId}&regularQuery=true`;
                            handleDownload(url, "googleEarth");
                          }}
                          disabled={loadingStates.googleEarth}
                        >
                          {loadingStates.googleEarth ? (
                            <>
                              <Spinner
                                as="span"
                                animation="border"
                                size="sm"
                                role="status"
                                aria-hidden="true"
                                className="me-2"
                              />
                              <FormattedMessage id="EXPORTING" />
                            </>
                          ) : (
                            <FormattedMessage id="EXPORT_EXCEL_FILE" />
                          )}
                        </Button>
                      </div>
                    </Card.Body>
                  </Card>

                  <Card className="shadow-sm">
                    <Card.Body>
                      <Card.Title as="h6" className="mb-1">
                        <FormattedMessage id="GIS_SHAPEFILE" />
                      </Card.Title>
                      <div className="d-flex gap-2">
                        <Button
                          className="my-3"
                          variant="outline-primary"
                          size="sm"
                          onClick={() =>
                            handleDownload(
                              `/EncounterSearchExportShapefile?searchQueryId=${searchQueryId}&regularQuery=true`,
                              "gisShapefile",
                            )
                          }
                          disabled={loadingStates.gisShapefile}
                        >
                          {loadingStates.gisShapefile ? (
                            <>
                              <Spinner
                                as="span"
                                animation="border"
                                size="sm"
                                role="status"
                                aria-hidden="true"
                                className="me-2"
                              />
                              <FormattedMessage id="EXPORTING" />
                            </>
                          ) : (
                            <FormattedMessage id="EXPORT_EXCEL_FILE" />
                          )}
                        </Button>
                      </div>
                    </Card.Body>
                  </Card>
                </Stack>
              </div>

              <div id="markrecapture">
                <h5 className="mb-3">
                  <FormattedMessage id="MARK_RECAPTURE_DATA" />
                </h5>
                <Card className="shadow-sm">
                  <Card.Body>
                    <Card.Title as="h6" className="mb-1">
                      <FormattedMessage id="MARK_RECAPTURE_DATA" />
                    </Card.Title>
                    <Stack gap={2}>
                      <Row>
                        <Col xs={12} md={6}>
                          <Form.Control
                            type="number"
                            value={numberSessions || ""}
                            onChange={(e) => setNumberSessions(e.target.value)}
                            placeholder="Number of Sessions"
                          />
                        </Col>
                        <Col xs={12} md={6}>
                          <Button
                            variant="outline-primary"
                            size="sm"
                            onClick={() => {
                              const numberSessionsParam =
                                numberSessions !== null
                                  ? `&numberSessions=${numberSessions}`
                                  : "";
                              const includeQueryCommentsParam =
                                includeQueryComments
                                  ? "&includeQueryComments=true"
                                  : "";
                              const includeIndividialIdParam =
                                includeIndividialId
                                  ? "&includeIndividialId=true"
                                  : "";
                              const url = `/SimpleCMRSpecifySessions.jsp?searchQueryId=${searchQueryId}&regularQuery=true&encounterExport=true${numberSessionsParam}${includeQueryCommentsParam}${includeIndividialIdParam}`;
                              window.open(url, "_blank");
                            }}
                          >
                            {" "}
                            <FormattedMessage id="CONFIGURE_SESSIONS_FOR_EXPORT" />
                          </Button>
                        </Col>
                      </Row>
                      <Form.Check
                        type="checkbox"
                        id="includeQueryComments"
                        label="Include query comments."
                        onChange={(e) =>
                          setIncludeQueryComments(e.target.checked)
                        }
                      />
                      <Form.Check
                        type="checkbox"
                        id="includeIndividialId"
                        label="Include individual ID."
                        onChange={(e) =>
                          setIncludeIndividialId(e.target.checked)
                        }
                      />
                    </Stack>
                  </Card.Body>
                </Card>
              </div>
            </Stack>
          </Col>
        </Row>
      </Modal.Body>
    </Modal>
  );
}
