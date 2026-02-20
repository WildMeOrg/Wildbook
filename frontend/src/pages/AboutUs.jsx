import React from "react";
import { Container, Row, Col, Nav } from "react-bootstrap";
import { useIntl } from "react-intl";

export default function AboutUsPage() {
  const intl = useIntl();
  const t = (id) => intl.formatMessage({ id });

  return (
    <Container className="py-4">
      <Row className="g-4">
        <Col xs={12} md={3} lg={3}>
          <div className="mb-3 text-muted" style={{ fontWeight: 400 }}>
            {t("ABOUT_US")}
          </div>

          <Nav className="flex-column">
            <Nav.Link
              href="#about-us"
              className="d-flex align-items-center justify-content-between px-0"
              style={{ fontWeight: 400 }}
            >
              <span>{t("ABOUT_US_LEFTNAV_ABOUT_WILDBOOK")}</span>
              <span aria-hidden="true">›</span>
            </Nav.Link>

            {/* save for future use
                            <Nav.Link
                            href="#"
                            onClick={(e) => e.preventDefault()}
                            className="d-flex align-items-center justify-content-between px-0 text-muted"
                            style={{ cursor: "not-allowed" }}
                            aria-disabled="true"
                            tabIndex={-1}
                            >
                            <span>{t("ABOUT_US_LEFTNAV_CONTACT_US")}</span>
                            <span aria-hidden="true">›</span>
                            </Nav.Link>

                            <Nav.Link
                            href="#"
                            onClick={(e) => e.preventDefault()}
                            className="d-flex align-items-center justify-content-between px-0 text-muted"
                            style={{ cursor: "not-allowed" }}
                            aria-disabled="true"
                            tabIndex={-1}
                            >
                            <span>{t("ABOUT_US_LEFTNAV_RESOURCES")}</span>
                            <span aria-hidden="true">›</span>
                            </Nav.Link> */}
          </Nav>
        </Col>

        <Col xs={12} md={9} lg={9}>
          <div id="about-us">
            <h3 className="mb-3" style={{ fontWeight: 500 }}>
              {t("ABOUT_US_PAGE_TITLE")}
            </h3>

            <p className="text-muted mb-4" style={{ lineHeight: 1.65 }}>
              {t("ABOUT_US_INTRO_P1")}
            </p>

            <h3 className="mt-4 mb-2" style={{ fontWeight: 500 }}>
              {t("ABOUT_US_SECTION_WHY_USE_TITLE")}
            </h3>
            <p className="text-muted" style={{ lineHeight: 1.65 }}>
              {t("ABOUT_US_SECTION_WHY_USE_P1")}
            </p>
            <p className="text-muted mb-4" style={{ lineHeight: 1.65 }}>
              {t("ABOUT_US_SECTION_WHY_USE_P2")}
            </p>

            <h3 className="mt-4 mb-2" style={{ fontWeight: 500 }}>
              {t("ABOUT_US_SECTION_WHAT_DOES_TITLE")}
            </h3>
            <p className="text-muted mb-4" style={{ lineHeight: 1.65 }}>
              {t("ABOUT_US_SECTION_WHAT_DOES_P1")}
            </p>

            <h3 className="mt-4 mb-2" style={{ fontWeight: 500 }}>
              {t("ABOUT_US_SECTION_MANAGE_BACKUP_TITLE")}
            </h3>
            <p className="text-muted mb-4" style={{ lineHeight: 1.65 }}>
              {t("ABOUT_US_SECTION_MANAGE_BACKUP_P1")}
            </p>

            <h3 className="mt-4 mb-2" style={{ fontWeight: 500 }}>
              {t("ABOUT_US_SECTION_RESEARCHERS_CONTROL_TITLE")}
            </h3>
            <p className="text-muted" style={{ lineHeight: 1.65 }}>
              {t("ABOUT_US_SECTION_RESEARCHERS_CONTROL_P1")}
            </p>
            <p className="text-muted mb-2" style={{ lineHeight: 1.65 }}>
              {t("ABOUT_US_SECTION_RESEARCHERS_CONTROL_P2")}
            </p>
            <h3 className="mt-4 mb-2" style={{ fontWeight: 500 }}>
              {t("ABOUT_US_SECTION_ANALYSIS_TOOLS_TITLE")}
            </h3>
            <p className="text-muted mb-4" style={{ lineHeight: 1.65 }}>
              {t("ABOUT_US_SECTION_ANALYSIS_TOOLS_P1")}
            </p>

            <h3 className="mt-4 mb-2" style={{ fontWeight: 500 }}>
              {t("ABOUT_US_SECTION_ARCHIVE_TITLE")}
            </h3>
            <p className="text-muted mb-4" style={{ lineHeight: 1.65 }}>
              {t("ABOUT_US_SECTION_ARCHIVE_P1")}
            </p>

            <h3 className="mt-4 mb-2" style={{ fontWeight: 500 }}>
              {t("ABOUT_US_SECTION_BUILD_NETWORKS_TITLE")}
            </h3>
            <p className="text-muted" style={{ lineHeight: 1.65 }}>
              {t("ABOUT_US_SECTION_BUILD_NETWORKS_P1")}
            </p>
          </div>
        </Col>
      </Row>
    </Container>
  );
}
