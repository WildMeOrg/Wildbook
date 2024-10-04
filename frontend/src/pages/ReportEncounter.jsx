import React, { useContext, useState, useRef } from "react";
import { Container, Row, Col, Form, Alert } from "react-bootstrap";
import ThemeColorContext from "../ThemeColorProvider";
import MainButton from "../components/MainButton";
import AuthContext from "../AuthProvider";

export default function ReportEncounter() {
  const themeColor = useContext(ThemeColorContext);
  const { isLoggedIn } = useContext(AuthContext);
  const encounterCategories = [
    "photos",
    "date and time",
    "place",
    "species",
    "additional comments",
    "followup information",
  ];
  const menu = encounterCategories.map((category, index) => ({
    id: index,
    title: category,
  }));

  const [selectedCategory, setSelectedCategory] = useState(0);
  const formRefs = useRef([]);
  const isScrollingByClick = useRef(false);
  const scrollTimeout = useRef(null);

  // Scroll into view when category is selected by click
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

  // Function to update the selected category when scrolling, but only if it's not triggered by a click
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

  return (
    <Container>
      <Row>
        <h3 className="pt-4">Report an Encounter</h3>
        <p>
          Please use the online form below to record the details of your
          encounter. Be as accurate and specific as possible.
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
            backgroundColor: "#fff5f5",
            color: "#dc3545",
            width: "auto",
          }}
        >
          <strong>* required fields</strong>
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
            {menu.map((data) => (
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
                {data.title}
                <i
                  className="bi bi-chevron-right"
                  style={{ fontSize: "14px", fontWeight: "500" }}
                ></i>
              </div>
            ))}

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
          <Form>
            {encounterCategories.map((category, index) => (
              <div
                key={index}
                ref={(el) => (formRefs.current[index] = el)}
                style={{ paddingBottom: "20px" }}
              >
                <h4>{category}</h4>
                <Form.Group>
                  <Form.Label>Details for {category}</Form.Label>
                  <Form.Control
                    type="text"
                    placeholder={`Enter ${category} details`}
                  />
                  <Form.Label>Details for {category}</Form.Label>
                  <Form.Control
                    type="text"
                    placeholder={`Enter ${category} details`}
                  />
                  <Form.Label>Details for {category}</Form.Label>
                  <Form.Control
                    type="text"
                    placeholder={`Enter ${category} details`}
                  />
                </Form.Group>
              </div>
            ))}
          </Form>
        </Col>
      </Row>
    </Container>
  );
}
