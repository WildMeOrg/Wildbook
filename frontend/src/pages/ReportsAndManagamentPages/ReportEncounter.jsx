import React, { useContext, useState, useRef, useEffect } from "react";
import { Container, Row, Col, Form, Alert, Modal } from "react-bootstrap";
import ThemeColorContext from "../../ThemeColorProvider";
import MainButton from "../../components/MainButton";
import AuthContext from "../../AuthProvider";
import { FormattedMessage } from "react-intl";
import ImageSection from "./ImageSection";
import { DateTimeSection } from "./DateTimeSection";
import { PlaceSection } from "./PlaceSection";
import { AdditionalCommentsSection } from "../../components/AdditionalCommentsSection";
import { FollowUpSection } from "../../components/FollowUpSection";
import { observer, useLocalObservable } from "mobx-react-lite";
import { ReportEncounterStore } from "./ReportEncounterStore";
import { ReportEncounterSpeciesSection } from "./SpeciesSection";
import { useNavigate } from "react-router-dom";
import useGetSiteSettings from "../../models/useGetSiteSettings";
import "./recaptcha.css";

export const ReportEncounter = observer(() => {

  const themeColor = useContext(ThemeColorContext);
  const { isLoggedIn } = useContext(AuthContext);
  const Navigate = useNavigate();
  const { data } = useGetSiteSettings();
  const procaptchaSiteKey = data?.procaptchaSiteKey;
  const store = useLocalObservable(() => new ReportEncounterStore());
  const [missingField, setMissingField] = useState(false);
  const [loading, setLoading] = useState(false);
  const isHuman = data?.isHuman;
  if (isHuman) {
    store.setIsHumanLocal(true);
  }

  store.setImageRequired(!isLoggedIn);

  useEffect(() => {
    localStorage.getItem("species") &&
      store.setSpeciesSectionValue(localStorage.getItem("species"));
    localStorage.getItem("followUpSection.submitter.name") &&
      store.setSubmitterName(
        localStorage.getItem("followUpSection.submitter.name"),
      );
    localStorage.getItem("followUpSection.submitter.email") &&
      store.setSubmitterEmail(
        localStorage.getItem("followUpSection.submitter.email"),
      );
    localStorage.getItem("followUpSection.photographer.name") &&
      store.setPhotographerName(
        localStorage.getItem("followUpSection.photographer.name"),
      );
    localStorage.getItem("followUpSection.photographer.email") &&
      store.setPhotographerEmail(
        localStorage.getItem("followUpSection.photographer.email"),
      );
    localStorage.getItem("followUpSection.additionalEmails") &&
      store.setAdditionalEmails(
        localStorage.getItem("followUpSection.additionalEmails"),
      );
    localStorage.getItem("additionalCommentsSection") &&
      store.setCommentsSectionValue(
        localStorage.getItem("additionalCommentsSection"),
      );
    localStorage.getItem("uploadedFiles") &&
      store.setImagePreview(JSON.parse(localStorage.getItem("uploadedFiles")));
    localStorage.getItem("submissionId") &&
      store.setImageSectionSubmissionId(localStorage.getItem("submissionId"));
    localStorage.getItem("fileNames") &&
      JSON.parse(localStorage.getItem("fileNames")).forEach((fileName) => {
        store.setImageSectionFileNames(fileName, "add");
      });
    localStorage.getItem("datetime") &&
      store.setDateTimeSectionValue(new Date(localStorage.getItem("datetime")));
    localStorage.getItem("exifDateTime") &&
      store.setExifDateTime(localStorage.getItem("exifDateTime"));
    localStorage.getItem("locationID") && store.setLocationId(localStorage.getItem("locationID"));
    localStorage.getItem("lat") && store.setLat(localStorage.getItem("lat"));
    localStorage.getItem("lon") && store.setLon(localStorage.getItem("lon"));

    localStorage.removeItem("species");
    localStorage.removeItem("followUpSection.submitter.name");
    localStorage.removeItem("followUpSection.submitter.email");
    localStorage.removeItem("followUpSection.photographer.name");
    localStorage.removeItem("followUpSection.photographer.email");
    localStorage.removeItem("followUpSection.additionalEmails");
    localStorage.removeItem("additionalCommentsSection");
    localStorage.removeItem("uploadedFiles");
    localStorage.removeItem("placeSection");
    localStorage.removeItem("fileNames");
    localStorage.removeItem("datetime");
    localStorage.removeItem("exifDateTime");
    localStorage.removeItem("locationID");
    localStorage.removeItem("lat");
    localStorage.removeItem("lon");
    localStorage.removeItem("submissionId");

  }, []);

  const handleSubmit = async () => {
    setLoading(true);
    if (!store.validateFields()) {
      store.setShowSubmissionFailedAlert(true);
      setMissingField(true);
      setLoading(false);
      return;
    } else {
      setMissingField(false);
      const responseData = await store.submitReport();
      if (store.finished && store.success) {
        setLoading(false);
        Navigate("/reportConfirm", { state: { responseData } });
      } else if (store.finished && !store.success) {
        setLoading(false);
        store.setShowSubmissionFailedAlert(true);
      }
    }
  };

  // Categories for sections
  const encounterCategories = [
    {
      title: "PHOTOS_SECTION",
      section: <ImageSection store={store} />,
    },
    {
      title: "DATETIME_SECTION",
      section: <DateTimeSection store={store} />,
    },
    {
      title: "PLACE_SECTION",
      section: <PlaceSection store={store} />,
    },
    {
      title: "SPECIES",
      section: <ReportEncounterSpeciesSection store={store} />,
    },
    {
      title: "ADDITIONAL_COMMENTS_SECTION",
      section: <AdditionalCommentsSection store={store} />,
    },
    {
      title: "FOLLOWUP_SECTION",
      section: <FollowUpSection store={store} />,
    },
  ];

  const menu = encounterCategories.map((category, index) => ({
    id: index,
    title: category.title,
  }));

  const [selectedCategory, setSelectedCategory] = useState(0);
  const formRefs = useRef([]);
  const isScrollingByClick = useRef(false);
  const scrollTimeout = useRef(null);

  const handleClick = (id) => {
    clearTimeout(scrollTimeout.current);
    setSelectedCategory(id);
    isScrollingByClick.current = true;

    if (formRefs.current[id]) {
      formRefs.current[id].scrollIntoView({
        behavior: "smooth",
        block: "start",
      });
    }

    scrollTimeout.current = setTimeout(() => {
      isScrollingByClick.current = false;
    }, 1000);
  };

  const handleScroll = () => {
    if (isScrollingByClick.current) return;

    formRefs.current.forEach((ref, index) => {
      if (ref) {
        const rect = ref.getBoundingClientRect();
        if (rect.top >= 0 && rect.top < window.innerHeight / 2) {
          setSelectedCategory(index);
        }
      }
    });
  };

  const captchaRef = useRef(null);
  useEffect(() => {
    if (store.isHumanLocal) return;

    let isCaptchaRendered = false;
    const loadProCaptcha = async () => {
      if (isCaptchaRendered || !captchaRef.current) return;
      const { render } = await import(
        "https://js.prosopo.io/js/procaptcha.bundle.js"
      );
      if (procaptchaSiteKey) {
        render(captchaRef.current, {
          siteKey: procaptchaSiteKey,
          callback: onCaptchaVerified,
        });
        isCaptchaRendered = true;
      }
    };

    loadProCaptcha();

    return () => {
      if (captchaRef.current) {
        captchaRef.current.innerHTML = "";
      }
      isCaptchaRendered = false;
    };
  }, [procaptchaSiteKey]);

  const onCaptchaVerified = async (output) => {
    const payload = { procaptchaValue: output };
    try {
      const res = await fetch("/ReCAPTCHA", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      const data = await res.json();
      store.setIsHumanLocal(data.valid);
    } catch (error) {
      console.error("Error submitting captcha: ", error);
    }
  };
  return (
    <Container>
      <Modal
        show={store.showSubmissionFailedAlert}
        size="lg"
        onHide={() => {
          setLoading(false);
          store.setShowSubmissionFailedAlert(false)
        }}
        keyboard
        centered
        animation
      >
        <Modal.Header
          closeButton
          style={{
            border: "none",
            paddingBottom: "0px",
          }}
        ></Modal.Header>
        <div className="d-flex flex-row pb-4 ps-4">
          {missingField && <FormattedMessage id="MISSING_REQUIRED_FIELDS" />}
          {store.error && <div className="d-flex flex-column">
            {store.error.slice().map((error, index) => (
              <div key={index} className="d-flex flex-column">
                {error.code === "INVALID" && <p>
                  <FormattedMessage id="BEERROR_INVALID" />{error.fieldName} </p>}
                {error.code === "REQUIRED" && <p>
                  <FormattedMessage id="BEERROR_REQUIRED" />{error.fieldName} </p>}
                {!error.code && <p>
                  <FormattedMessage id="BEERROR_UNKNOWN" />{error.fieldName} </p>}
              </div>
            ))}
          </div>}
          {!missingField && !store.error && <FormattedMessage id="SUBMISSION_FAILED" />}
        </div>
      </Modal>
      <Row>
        <h3 className="pt-4">
          <FormattedMessage id="REPORT_AN_ENCOUNTER" />
        </h3>
        <p>
          <FormattedMessage id="REPORT_PAGE_DESCRIPTION" />
        </p>
        {!isLoggedIn ? (
          <Alert variant="warning">
            <div className="d-flex flex-row ">
              <i
                className="bi bi-info-circle-fill"
                style={{
                  marginRight: "8px",
                  color: themeColor.statusColors.yellow800,
                }}
              ></i>
              {(isHuman || store.isHumanLocal) ?<FormattedMessage id="SIGNIN_CAPTCHACOMPLETE_REMINDER_BANNER"/> : <FormattedMessage id="SIGNIN_REMINDER_BANNER" /> }
            </div>
            <Row
              className="d-flex flex-row"
              style={{
                padding: "10px 10px 0 10px",
                marginTop: "10px",
              }}
            >
              <MainButton
                style={{ width: "100px", height: "40px", marginBottom: 0 }}
                link={`${process.env.PUBLIC_URL}/login?redirect=%2Freport`}
                noArrow={true}
                color="white"
                backgroundColor={themeColor.wildMeColors.cyan600}
                onClick={() => {
                  localStorage.setItem("species", store.speciesSection.value);
                  localStorage.setItem(
                    "followUpSection.submitter.name",
                    store.followUpSection.submitter.name,
                  );
                  store.followUpSection.submitter.email && localStorage.setItem(
                    "followUpSection.submitter.email",
                    store.followUpSection.submitter.email,
                  );
                  store.followUpSection.photographer.name && localStorage.setItem(
                    "followUpSection.photographer.name",
                    store.followUpSection.photographer.name,
                  );
                  store.followUpSection.photographer.email && localStorage.setItem(
                    "followUpSection.photographer.email",
                    store.followUpSection.photographer.email,
                  );
                  store.followUpSection.additionalEmails && localStorage.setItem(
                    "followUpSection.additionalEmails",
                    store.followUpSection.additionalEmails,
                  );
                  store.additionalCommentsSection.value && localStorage.setItem(
                    "additionalCommentsSection",
                    store.additionalCommentsSection.value,
                  );
                  store.imagePreview && localStorage.setItem(
                    "uploadedFiles",
                    JSON.stringify(store.imagePreview),
                  );
                  store.imageSectionSubmissionId && localStorage.setItem(
                    "submissionId",
                    store.imageSectionSubmissionId,
                  );
                  store.imageSectionFileNames && localStorage.setItem(
                    "fileNames",
                    JSON.stringify(store.imageSectionFileNames),
                  );
                  store.dateTimeSection.value && localStorage.setItem(
                    "datetime",
                    store.dateTimeSection.value?.toISOString(),
                  );
                  store.exifDateTime && localStorage.setItem("exifDateTime", store.exifDateTime);
                  store.placeSection.locationId && localStorage.setItem("locationID", store.placeSection.locationId);
                  store.lat && localStorage.setItem("lat", store.lat);
                  store.lon && localStorage.setItem("lon", store.lon);
                }}
              >
                <FormattedMessage id="LOGIN_SIGN_IN" />
              </MainButton>
              {store.isHumanLocal || isHuman ? null : (
                <div
                  id="procaptcha-container"
                  ref={captchaRef}
                  style={{ width: "300px", marginLeft: "30px" }}
                ></div>
              )}
            </Row>
          </Alert>
        ) : null}
      </Row>
      <Row>
        <Alert
          variant="light"
          className="d-inline-block p-2"
          style={{
            color: "#dc3545",
            width: "auto",
            border: "none",
          }}
        >
          <strong>
            <FormattedMessage id="REQUIRED_FIELDS_KEY" />
          </strong>
        </Alert>
      </Row>

      <Row>
        <Col className="col-lg-4 col-md-6 col-sm-12 col-12 ps-0">
          <div
            className="p-4 mb-4"
            style={{
              backgroundImage: `linear-gradient(rgba(0, 0, 0, 0.5), rgba(0, 0, 0, 0.5)),url(${process.env.PUBLIC_URL}/images/report_an_encounter.png)`,
              backgroundSize: "cover",
              backgroundPosition: "center",
              borderRadius: "25px",
              height: "470px",
              maxWidth: "350px",
              color: "white",
            }}
          >
            {menu.map((data) => (
              <div
                key={data.id}
                className="d-flex justify-content-between p-2 mt-2"
                style={{
                  fontWeight: "500",
                  cursor: "pointer",
                  borderRadius: "10px",
                  backgroundColor:
                    selectedCategory === data.id
                      ? "rgba(255,255,255,0.5)"
                      : "transparent",
                }}
                onClick={() => handleClick(data.id)}
              >
                <h5>
                  <FormattedMessage id={data.title} />
                </h5>
                <i
                  className="bi bi-chevron-right"
                  style={{ fontSize: "1rem", fontWeight: "500" }}
                ></i>
              </div>
            ))}

            <MainButton
              backgroundColor={themeColor.wildMeColors.cyan600}
              color={themeColor.defaultColors.white}
              shadowColor={themeColor.defaultColors.white}
              style={{
                width: "100%",
                fontSize: "1.25rem",
                margin: "20px 0 20px 0",
              }}
              onClick={handleSubmit}
            >
              <FormattedMessage id="SUBMIT_ENCOUNTER" />
              {loading && (
                <div
                  className="spinner-border spinner-border-sm ms-1"
                  role="status"
                >
                  <span className="visually-hidden">
                    <FormattedMessage id="LOADING" />
                  </span>
                </div>
              )}
            </MainButton>
          </div>
        </Col>

        <Col
          className="col-lg-8 col-md-6 col-sm-12 col-12 h-100 pe-4"
          style={{
            maxHeight: "470px",
            overflow: "auto",
          }}
          onScroll={handleScroll}
        >
          <Form>
            {encounterCategories.map((category, index) => (
              <div
                key={index}
                ref={(el) => (formRefs.current[index] = el)}
                style={{ paddingBottom: "20px" }}
              >
                {category.section}
              </div>
            ))}
          </Form>
        </Col>
      </Row>
    </Container>
  );
});

export default ReportEncounter;
