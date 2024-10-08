import React, { useContext, useState, useRef } from "react";
import { Container, Row, Col, Form, Alert } from "react-bootstrap";
import ThemeColorContext from "../ThemeColorProvider";
import MainButton from "../components/MainButton";
import AuthContext from "../AuthProvider";
import { FormattedMessage } from "react-intl";
import ImageSection from "../components/ImageSection";
import DateTimeSection from "../components/DateTimeSection";
import PlaceSection from "../components/PlaceSection";
import SpeciesSection from "../components/SpeciesSection";
import AdditionalCommentsSection from "../components/AdditionalCommentsSection";
import FollowUpSection from "../components/FollowUpSection";

export default function ReportEncounter() {
  const themeColor = useContext(ThemeColorContext);
  const [species, setSpecies] = useState("");
  const [isValidForm, setIsValidForm] = useState(true);
  const { isLoggedIn } = useContext(AuthContext);
  const encounterCategories = [
    {
      title: "PHOTOS_SECTION",
      section: <ImageSection />,
    },
    {
      title: "DATETIME_SECTION",
      section: <DateTimeSection />,
    },
    {
      title: "PLACE_SECTION",
      section: <PlaceSection />,
    },
    {
      title: "SPECIES",
      section: (
        <SpeciesSection
          species={species}
          setSpecies={setSpecies}
          isValidForm={isValidForm}
        />
      ),
    },

    {
      title: "ADDITIONAL_COMMENTS_SECTION",
      section: <AdditionalCommentsSection />,
    },
    {
      title: "FOLLOWUP_SECTION",
      section: <FollowUpSection />,
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

  const handleSubmit = () => {
    if (!species) {
      setIsValidForm(false);
    } else {
      setIsValidForm(true);
      alert("Form Submit!");
    }
  };

  return (
    <Container>
      <Row>
        <h3 className="pt-4">
          <FormattedMessage id="REPORT_AN_ENCOUNTER" />
        </h3>
        <p>
          <FormattedMessage id="REPORT_PAGE_DESCRIPTION" />
        </p>
        {!isLoggedIn ? (
          <Alert variant="warning" dismissible>
            <i
              className="bi bi-info-circle-fill"
              style={{ marginRight: "8px", color: "#7b6a00" }}
            ></i>
            You are not signed in. If you want this encounter associated with
            your account, be sure to{" "}
            <a
              href="/react/login?redirect=%2Freport"
              style={{ color: "#337ab7", textDecoration: "underline" }}
            >
              sign in!
            </a>
          </Alert>
        ) : null}
      </Row>
      <Row>
        <Alert
          variant="light"
          className="d-inline-block p-2"
          style={{
            // backgroundColor: "#fff5f5",
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
        <Col className="col-lg-5 col-md-6 col-sm-12 col-12 ps-0">
          <div
            style={{
              backgroundImage: `linear-gradient(rgba(0, 0, 0, 0.5), rgba(0, 0, 0, 0.5)),url(${process.env.PUBLIC_URL}/images/report_an_encounter.png)`,
              backgroundSize: "cover",
              backgroundPosition: "center",
              borderRadius: "25px",
              padding: "20px",
              height: "470px",
              width: "350px",
              color: "white",
              marginBottom: "20px",
            }}
          >
            {menu.map((data) => {
              return (
                <div
                  key={data.id}
                  className="d-flex justify-content-between"
                  style={{
                    padding: "10px",
                    marginTop: "10px",
                    fontSize: "20px",
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
                  <FormattedMessage id={data.title} />
                  <i
                    className="bi bi-chevron-right"
                    style={{ fontSize: "14px", fontWeight: "500" }}
                  ></i>
                </div>
              );
            })}

            <MainButton
              backgroundColor={themeColor.wildMeColors.cyan600}
              color={themeColor.defaultColors.white}
              shadowColor={themeColor.defaultColors.white}
              style={{
                width: "calc(100% - 20px)",
                fontSize: "20px",
                marginTop: "20px",
                marginBottom: "20px",
              }}
              onClick={handleSubmit}
            >
              Submit Encounter
            </MainButton>
          </div>
        </Col>

        <Col
          className="col-lg-7 col-md-6 col-sm-12 col-12 h-100"
          style={{
            maxHeight: "470px",
            overflow: "auto",
          }}
          onScroll={handleScroll}
        >
          <Form style={{ width: "80%" }}>
            {encounterCategories.map((category, index) => (
              <div
                key={index}
                ref={(el) => (formRefs.current[index] = el)}
                style={{ paddingBottom: "20px" }}
              >
                <h4>{/* <FormattedMessage id={index} /> */}</h4>
                {category.section}
              </div>
            ))}
          </Form>
        </Col>
      </Row>
    </Container>
  );
}
