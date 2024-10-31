import React, { useContext } from "react";
import { Form, Alert } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { observer } from "mobx-react-lite";
import Datetime from "react-datetime";
import "react-datetime/css/react-datetime.css";
import moment from "moment";
import ThemeContext from "../../ThemeColorProvider";

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
            onBlur: (e) => {
              const inputDate = e.target.value;
              if (inputDate === "") {
                store.setDateTimeSectionError(false);
                return;
              }
              const isValidFormat = moment(
                new Date(inputDate),
                moment.ISO_8601,
                true,
              ).isValid();
              if (!isValidFormat) {
                store.setDateTimeSectionError(true);
              }
            },
          }}
          input={true}
          dateFormat={"YYYY-MM-DD"}
          closeOnSelect={true}
          value={store.dateTimeSection.value}
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
                color: theme.statusColors.red800 }}
            ></i>
            <FormattedMessage id="INVALID_DATETIME_WARNING" />
          </Alert>
        )}
        <div className="position-relative d-inline-block w-100 mt-4">
          <Form.Label>
            <FormattedMessage id="DATETIME_EXIF" />
          </Form.Label>
          <Form.Control
            as="select"
            onChange={(e) => {
              const inputDate = e.target.value;
              store.setDateTimeSectionValue(new Date(inputDate));
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
