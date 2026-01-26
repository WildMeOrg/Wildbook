import React, { useContext, useEffect, useMemo, useState } from "react";
import { Container, Row, Col, Spinner, ListGroup } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { useLocation, useNavigate } from "react-router-dom";
import LocaleContext from "../../IntlProvider";
import ThemeColorContext from "../../ThemeColorProvider";
import CitingWildbook from "../../pages/Citation";

const SECTIONS = [
  {
    key: "PRIVACY_POLICY",
    labelId: "MENU_LEARN_PRIVACYPOLICY",
    baseName: "privacy_policy",
    type: "pdf",
  },
  {
    key: "TERMS_OF_USE",
    labelId: "MENU_LEARN_TERMSOFUSE",
    baseName: "terms_of_use",
    type: "pdf",
  },
  {
    key: "CITING_WILDBOOK",
    labelId: "MENU_LEARN_CITINGWILDBOOK",
    type: "component",
    Component: CitingWildbook,
  },
];

const DEFAULT_KEY = "CITING_WILDBOOK";

const SECTION_PARAM_TO_KEY = {
  privacy_policy: "PRIVACY_POLICY",
  terms_of_use: "TERMS_OF_USE",
  citing_wildbook: "CITING_WILDBOOK",

  // tolerant
  PRIVACY_POLICY: "PRIVACY_POLICY",
  TERMS_OF_USE: "TERMS_OF_USE",
  CITING_WILDBOOK: "CITING_WILDBOOK",
};

const KEY_TO_SECTION_PARAM = {
  PRIVACY_POLICY: "privacy_policy",
  TERMS_OF_USE: "terms_of_use",
  CITING_WILDBOOK: "citing_wildbook",
};

const publicPath = (p) =>
  `${process.env.PUBLIC_URL || ""}${p.startsWith("/") ? p : `/${p}`}`;

async function exists(url) {
  try {
    const res = await fetch(url, { method: "HEAD" });
    return res.ok;
  } catch {
    return false;
  }
}

export default function PoliciesAndData() {
  const { locale } = useContext(LocaleContext);
  const theme = useContext(ThemeColorContext);

  const location = useLocation();
  const navigate = useNavigate();

  const sectionParam = useMemo(() => {
    const sp = new URLSearchParams(location.search);
    return sp.get("section");
  }, [location.search]);

  const initialKey = SECTION_PARAM_TO_KEY[sectionParam] || DEFAULT_KEY;

  const [activeKey, setActiveKey] = useState(initialKey);
  const [pdfUrl, setPdfUrl] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const nextKey = SECTION_PARAM_TO_KEY[sectionParam] || DEFAULT_KEY;
    setActiveKey(nextKey);
  }, [sectionParam]);

  const active = useMemo(
    () => SECTIONS.find((s) => s.key === activeKey) || SECTIONS[0],
    [activeKey],
  );

  const isPdf = active.type === "pdf";
  const ActiveComponent = active.Component;

  const handleSelect = (key) => {
    setActiveKey(key);

    const sp = new URLSearchParams(location.search);
    sp.set("section", KEY_TO_SECTION_PARAM[key] || key);
    navigate({ pathname: location.pathname, search: sp.toString() }, { replace: true });
  };

  useEffect(() => {
    let cancelled = false;

    if (!isPdf) {
      setLoading(false);
      setPdfUrl(null);
      return () => {
        cancelled = true;
      };
    }

    (async () => {
      setLoading(true);
      setPdfUrl(null);

      const localUrl = publicPath(`/files/${active.baseName}_${locale}.pdf`);
      const enUrl = publicPath(`/files/${active.baseName}_en.pdf`);
      const picked = (await exists(localUrl)) ? localUrl : (await exists(enUrl)) ? enUrl : null;

      if (!cancelled) {
        setPdfUrl(picked);
        setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [active.baseName, locale, isPdf]);

  const handleDownload = async () => {
    if (!pdfUrl) return;

    try {
      const res = await fetch(pdfUrl);
      if (!res.ok) throw new Error("download failed");

      const blob = await res.blob();
      const objUrl = URL.createObjectURL(blob);

      const a = document.createElement("a");
      a.href = objUrl;
      a.download = `${active.baseName}_${locale}.pdf`;
      document.body.appendChild(a);
      a.click();
      a.remove();

      URL.revokeObjectURL(objUrl);
    } catch {
      window.open(pdfUrl, "_blank", "noopener,noreferrer");
    }
  };

  const handlePrint = (e) => {
    if (!pdfUrl) return;
    const w = window.open(pdfUrl, "_blank", "noopener,noreferrer");
    if (!w) return;

    const tryPrint = () => {
      try {
        w.focus();
        w.print();
      } catch {
        console.error(e);
       }
    };

    w.addEventListener?.("load", tryPrint);
    setTimeout(tryPrint, 800);
  };

  const iconBtnStyle = {
    color: theme?.primaryColors?.primary500,
    textDecoration: "none",
  };

  const iconStyle = {
    fontSize: 20,
    fontWeight: 600,
  };

  return (
    <Container className="mt-5 px-0 px-md-3">
      <Row className="g-3">
        <Col xs={12} md={2} lg={2} className="pe-4">
          <div>
            <div className="fw-semibold mb-2">
              <FormattedMessage id="MENU_POLICIES_AND_DATA" />
            </div>

            <ListGroup variant="flush">
              {SECTIONS.map((s) => {
                const isActive = s.key === activeKey;
                const color = isActive ? theme.primaryColors.primary500 : "GrayText";

                return (
                  <ListGroup.Item
                    key={s.key}
                    action
                    onClick={() => handleSelect(s.key)}
                    className="d-flex align-items-center justify-content-between px-0 py-3"
                    style={{
                      background: "transparent",
                      border: "none",
                      cursor: "pointer",
                      color,
                    }}
                  >
                    <span className={isActive ? "fw-semibold" : ""}>
                      <FormattedMessage id={s.labelId} defaultMessage={s.key} />
                    </span>

                    <i className="bi bi-chevron-right" aria-hidden="true" style={{ color }} />
                  </ListGroup.Item>
                );
              })}
            </ListGroup>
          </div>
        </Col>

        <Col xs={12} md={10} lg={10} >
          <div style={{overflowX: "auto" }}>
            {/* {!isPdf && (
              <div className="d-flex px-3 py-2">
                <div className="d-flex gap-4" style={{ marginLeft: "auto" }}>
                  <Button
                    variant="link"
                    onClick={handlePrint}
                    disabled={!pdfUrl || loading}
                    title="Print"
                    className="p-0"
                    style={iconBtnStyle}
                  >
                    <i className="bi bi-printer" style={iconStyle} />
                  </Button>

                  <Button
                    variant="link"
                    onClick={handleDownload}
                    disabled={!pdfUrl || loading}
                    title="Download"
                    className="p-0"
                    style={iconBtnStyle}
                  >
                    <i className="bi bi-download" style={iconStyle} />
                  </Button>
                </div>
              </div>
            )} */}

            {!isPdf && ActiveComponent && <ActiveComponent />}

            {isPdf && (
              <>
                {loading && (
                  <div className="p-3 text-muted d-flex align-items-center gap-2">
                    <Spinner size="sm" /> Loading…
                  </div>
                )}

                {!loading && !pdfUrl && (
                  <div className="p-3 text-muted">
                    PDF not found for <b>{locale}</b>. Put files in <code>public/files</code>.
                  </div>
                )}

                {!loading && pdfUrl && (
                  <iframe
                    title={`${active.key}-pdf`}
                    src={pdfUrl}
                    style={{ width: "100%", height: "75vh", border: "none" }}
                  />
                )}
              </>
            )}
          </div>
        </Col>
      </Row>
    </Container>
  );
}
