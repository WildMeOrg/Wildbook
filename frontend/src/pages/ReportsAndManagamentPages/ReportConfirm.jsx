import React, { useContext } from "react";
import { FormattedMessage } from "react-intl";
import ThemeColorContext from "../../ThemeColorProvider";
import { Col, Row, Alert } from "react-bootstrap";
import MainButton from "../../components/MainButton";
import { useLocation } from "react-router-dom";

export const ReportConfirm = () => {
  const location = useLocation();
  const responseData = location.state?.responseData;
  const themeColor = useContext(ThemeColorContext);

  return (
    <div style={{ marginLeft: "-100px", width: "720px" }}>
      <h2 className="mt-3">
        <FormattedMessage id="SUBMISSION_SUCCESSFUL" />
      </h2>
      <p className="fs-5 lh-lg m-0">
        <FormattedMessage id="SUBMISSION_SUCCESS_ALERT" />
      </p>
      {responseData?.invalidFiles?.length > 0 && (
        <Alert variant="warning" dismissible>
          <p className="fs-5 fw-semibold">
            <FormattedMessage id="INVALID_FILES" />
          </p>
          <p className="fs-6 mb-1">
            <FormattedMessage
              id="INVALID_FILES_MESSAGE"
              values={{
                link: (
                  <a href="https://wildbook.docs.wildme.org/data/photography-guidelines.html">
                    <FormattedMessage id="IMAGE_REQUIREMENTS" />
                  </a>
                ),
              }}
            />
          </p>

          {responseData?.invalidFiles?.map((invalidFile, index) => (
            <p key={index} className="fs-6 mb-0">
              {invalidFile.filename}
            </p>
          ))}
        </Alert>
      )}
      <p className="fs-5 lh-lg fw-bold m-0">
        <FormattedMessage id="ENCOUNTER" /> {responseData?.id}
      </p>
      <p className="fs-6 lh-sm fw-normal m-0">
        <FormattedMessage id="SUBMISSION_SUCCESS_LOCATION_ID" />{" "}
        {responseData.locationId}
      </p>
      <p className="fs-6 lh-sm fw-normal m-0">
        <FormattedMessage id="SUBMISSION_SUCCESS_SUBMITTED_ON" />{" "}
        {responseData.submissionDate}
      </p>
      <Row className="mt-2">
        {responseData.assets.map((asset, index) => (
          <div
            key={asset.id}
            className="d-inline-block text-center mt-1 mb-1 pe-0"
            style={{ width: "150px" }}
          >
            <img
              className="img-fluid"
              style={{ width: "150px", height: "150px" }}
              // NOTE: Assuming we will get correct image url else use this 'asset.url.replace('localhost', window.location.hostname)'
              src={asset.url}
              alt={`img${index + 1}`}
            />
            <div
              className="text-truncate"
              style={{ width: "100%", maxWidth: "150px" }}
              title={asset.filename}
            >
              {asset.filename}
            </div>
          </div>
        ))}
      </Row>
      <p className="fs-6 lh-sm fw-normal m-0">
        <FormattedMessage id="CONTACT_MESSAGE" />
        <a href="https://community.wildme.org">community.wildme.org</a>
      </p>
      <Row className="mb-5" style={{ width: "60%" }}>
        <Col className="ps-0">
          <MainButton
            borderColor={themeColor.wildMeColors.cyan600}
            backgroundColor={themeColor.wildMeColors.white}
            color={themeColor.wildMeColors.cyan600}
            shadowColor={themeColor.wildMeColors.cyan600}
            noArrow={true}
            style={{
              width: "calc(100% - 20px)",
              fontSize: "1rem",
              marginTop: "20px",
              marginBottom: "20px",
              fontWeight: "normal",
            }}
            onClick={() =>
              window.open(
                `/encounters/encounter.jsp?number=${responseData?.id}`,
                "_blank",
              )
            }
          >
            <FormattedMessage id="VIEW_ENCOUNTER" />
          </MainButton>
        </Col>
        <Col className="ps-0">
          <MainButton
            borderColor={themeColor.wildMeColors.cyan600}
            backgroundColor={themeColor.defaultColors.white}
            color={themeColor.wildMeColors.cyan600}
            shadowColor={themeColor.wildMeColors.cyan600}
            noArrow={true}
            style={{
              width: "calc(100% - 20px)",
              fontSize: "16px",
              marginTop: "20px",
              marginBottom: "20px",
              fontWeight: "normal",
            }}
            onClick={() => (window.location.href = "/react/report")}
          >
            <FormattedMessage id="SUBMIT_NEXT_ENCOUNTER" />
          </MainButton>
        </Col>
      </Row>
    </div>
  );
};

export default ReportConfirm;
