import React, { useContext } from "react";
import { Form, Alert } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { observer } from "mobx-react-lite";
import Datetime from "react-datetime";
import "react-datetime/css/react-datetime.css";
import ThemeContext from "../../ThemeColorProvider";
import { parseReportDateTime, toReportDateTimeValue } from "./reportDateTime";

export const DateTimeSection = observer(({ store }) => {
  const theme = useContext(ThemeContext);

  return (
    <div>
      <h5>
        <FormattedMessage id="DATETIME_SECTION" />
        {store.dateTimeSection.required && <span>*</span>}
      </h5>
      <p className="fs-6">
        <FormattedMessage id="DATE_INSTRUCTION" />
      </p>
      <Form.Group>
        <Form.Label>
          <FormattedMessage id="DATETIME_SECTION" />
          {store.dateTimeSection.required && <span>*</span>}
        </Form.Label>
        <Datetime
          inputProps={{
            placeholder: "YYYY-MM-DD",
            // react-datetime only displays values it can parse with its own
            // format; a partial date (e.g. "2024-03") restored after login
            // would otherwise render as an empty field.
            ...(typeof store.dateTimeSection.value === "string" && {
              value: store.dateTimeSection.value,
            }),
            // Update the store before react-datetime re-renders so a
            // store-controlled value never lags the input (caret position).
            onChange: (e) => store.setDateTimeSectionValue(e.target.value),
            onBlur: (e) => {
              const inputDate = e.target.value;
              if (inputDate === "") {
                store.setDateTimeSectionError(false);
                return;
              }
              const isValidFormat = parseReportDateTime(inputDate).isValid();
              if (!isValidFormat) {
                store.setDateTimeSectionError(true);
              }
            },
          }}
          input={true}
          dateFormat={"YYYY-MM-DD"}
          closeOnSelect={true}
          value={store.dateTimeSection.value}
          timeFormat="HH:mm"
          onChange={(e) => {
            store.setDateTimeSectionValue(e);
            store.setDateTimeSectionError(e ? false : true);
          }}
        />
        {store.dateTimeSection.error && (
          <Alert
            variant="danger"
            style={{
              marginTop: "10px",
            }}
          >
            <i
              className="bi bi-info-circle-fill"
              style={{
                marginRight: "8px",
                color: theme.statusColors.red800,
              }}
            ></i>
            <FormattedMessage id="INVALID_DATETIME_WARNING" />
          </Alert>
        )}
        <div className="position-relative d-inline-block w-100 mt-4">
          <Form.Label htmlFor="exifDateTime">
            <FormattedMessage id="DATETIME_EXIF" />
          </Form.Label>
          <Form.Control
            as="select"
            id="exifDateTime"
            onChange={(e) => {
              const inputDate = e.target.value;
              store.setDateTimeSectionValue(toReportDateTimeValue(inputDate));
            }}
          >
            <option value="">
              <FormattedMessage id="SELECT_DATETIME" />
            </option>
            {store.exifDateTime.map((option, optionIndex) => (
              <option key={optionIndex} value={option}>
                {option}
              </option>
            ))}
          </Form.Control>
        </div>
      </Form.Group>
    </div>
  );
});
