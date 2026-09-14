import React, { useContext } from "react";
import { Container, Alert } from "react-bootstrap";
import { useIntl } from "react-intl";
import ThemeColorContext from "../ThemeColorProvider";

export default function AboutUsPage() {
  const intl = useIntl();
  const themeColor = useContext(ThemeColorContext);
  const t = (id) => intl.formatMessage({ id });

  return (
    <Container className="py-4">
      <div id="about-us">
        <h2 className="mb-2" style={{ fontWeight: 600 }}>
          {t("ABOUT_US_PAGE_TITLE")}
        </h2>

        <p className="mb-3" style={{ lineHeight: 1.6, color: "#555" }}>
          {t("ABOUT_US_INTRO_P1")}
        </p>

        <Alert
          className="py-2 px-3 mb-4 d-flex align-items-center"
          style={{
            backgroundColor: themeColor?.primaryColors?.primary50,
            border: "none",
            color: themeColor?.primaryColors?.primary700,
          }}
        >
          <i
            className="bi bi-info-circle-fill me-2"
            aria-hidden="true"
            style={{
              color: themeColor?.primaryColors?.primary500,
              fontSize: "14px",
              flexShrink: 0,
            }}
          />
          <span>
            {t("ABOUT_US_CONTACT_BANNER_PREFIX")}{" "}
            <a
              href="mailto:info@wildme.org"
              style={{
                textDecoration: "underline",
              }}
            >
              info@wildme.org
            </a>
            {"."}
          </span>
        </Alert>

        <h4 className="mt-4 mb-2" style={{ fontWeight: 600 }}>
          {t("ABOUT_US_SECTION_WHY_USE_TITLE")}
        </h4>
        <p className="mb-2" style={{ lineHeight: 1.6, color: "#555" }}>
          {t("ABOUT_US_SECTION_WHY_USE_P1")}
        </p>
        <p className="mb-3" style={{ lineHeight: 1.6, color: "#555" }}>
          {t("ABOUT_US_SECTION_WHY_USE_P2")}
        </p>

        <h4 className="mt-4 mb-2" style={{ fontWeight: 600 }}>
          {t("ABOUT_US_SECTION_WHAT_DOES_TITLE")}
        </h4>
        <p className="mb-3" style={{ lineHeight: 1.6, color: "#555" }}>
          {t("ABOUT_US_SECTION_WHAT_DOES_P1")}
        </p>

        <h4 className="mt-4 mb-2" style={{ fontWeight: 600 }}>
          {t("ABOUT_US_SECTION_MANAGE_BACKUP_TITLE")}
        </h4>
        <p className="mb-3" style={{ lineHeight: 1.6, color: "#555" }}>
          {t("ABOUT_US_SECTION_MANAGE_BACKUP_P1")}
        </p>

        <h4 className="mt-4 mb-2" style={{ fontWeight: 600 }}>
          {t("ABOUT_US_SECTION_RESEARCHERS_CONTROL_TITLE")}
        </h4>
        <p className="mb-2" style={{ lineHeight: 1.6, color: "#555" }}>
          {t("ABOUT_US_SECTION_RESEARCHERS_CONTROL_P1")}
        </p>
        <p className="mb-3" style={{ lineHeight: 1.6, color: "#555" }}>
          {t("ABOUT_US_SECTION_RESEARCHERS_CONTROL_P2")}
        </p>

        <h4 className="mt-4 mb-2" style={{ fontWeight: 600 }}>
          {t("ABOUT_US_SECTION_ANALYSIS_TOOLS_TITLE")}
        </h4>
        <p className="mb-3" style={{ lineHeight: 1.6, color: "#555" }}>
          {t("ABOUT_US_SECTION_ANALYSIS_TOOLS_P1")}
        </p>

        <h4 className="mt-4 mb-2" style={{ fontWeight: 600 }}>
          {t("ABOUT_US_SECTION_ARCHIVE_TITLE")}
        </h4>
        <p className="mb-3" style={{ lineHeight: 1.6, color: "#555" }}>
          {t("ABOUT_US_SECTION_ARCHIVE_P1")}
        </p>

        <h4 className="mt-4 mb-2" style={{ fontWeight: 600 }}>
          {t("ABOUT_US_SECTION_BUILD_NETWORKS_TITLE")}
        </h4>
        <p className="mb-0" style={{ lineHeight: 1.6, color: "#555" }}>
          {t("ABOUT_US_SECTION_BUILD_NETWORKS_P1")}
        </p>
      </div>
    </Container>
  );
}
