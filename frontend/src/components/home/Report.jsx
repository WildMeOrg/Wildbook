import React, { useContext } from "react";
import BrutalismButton from "../BrutalismButton";
import { FormattedMessage } from "react-intl";
import ThemeColorContext from "../../ThemeColorProvider";

export default function Report() {
  const theme = useContext(ThemeColorContext);
  return (
    <div
      id="report"
      className="d-flex flex-row justify-content-around align-items-center mt-5 w-100"
      style={{
        height: "500px",
        backgroundColor: theme.statusColors.blue100,
      }}
    >
      <div
        id="report-image"
        className="d-flex align-items-center justify-content-end w-50 me-5"
      >
        <svg
          width="338"
          height="326"
          viewBox="0 0 538 526"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
        >
          <path
            d="M361.294 100.774C448.672 151.222 449.982 430.013 369.842 473.866C289.702 517.719 35.7239 358.94 33.9135 279.918C32.103 200.895 273.916 50.3266 361.294 100.774Z"
            fill="#D9D9D9"
          />
          <image
            alt="report"
            href={`${process.env.PUBLIC_URL}/images/submit.png`}
            x="0"
            y="0"
            width="538px"
            height="526px"
          />
        </svg>
      </div>

      <div
        id="report-text"
        className="d-flex flex-column align-items-start justify-content-start pe-3 w-50 ps-5"
      >
        <h1 style={{ fontSize: "48px" }}>
          <FormattedMessage id="SUBMIT_NEW_DATA" />
        </h1>

        <div className="d-flex flex-row justify-content-around align-items-center mt-2">
          <BrutalismButton link={"/submit.jsp"}>
            <FormattedMessage id="REPORT_AN_ENCOUNTER" />
          </BrutalismButton>
          <BrutalismButton link={"/import/instructions.jsp"}>
            <FormattedMessage id="BULK_REPORT" />
          </BrutalismButton>
        </div>
      </div>
    </div>
  );
}
