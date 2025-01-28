import React, { useState, useEffect } from "react";
import { FormattedMessage } from "react-intl";
import Description from "../Form/Description";
import { FormLabel, FormGroup, FormControl } from "react-bootstrap";

export default function IndividualDateFilter({ onChange }) {
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [birthDate, setBirthDate] = useState("");
  const [deathDate, setDeathDate] = useState("");

  useEffect(() => {
    updateQuery1("startDate", startDate);
  }, [startDate]);
  useEffect(() => {
    updateQuery1("endDate", endDate);
  }, [endDate]);

  const updateQuery1 = () => {
    if (startDate || endDate) {
      const query = {
        range: {
          datematched: {},
        },
      };

      if (startDate) {
        query.range.datematched.gte = startDate + "T00:00:00Z";
      }

      if (endDate) {
        query.range.datematched.lte = endDate + "T23:59:59Z";
      }
      onChange({
        filterId: "datematched",
        filterKey: "Date Matched",
        clause: "filter",
        query: query,
      });
    } else {
      onChange(null, "date");
    }
  };

  return (
    <div>
      <h4>
        <FormattedMessage id="FILTER_DATE" />
      </h4>
      <Description>
        <FormattedMessage id="FILTER_DATE_DESC" />
      </Description>
      <>
        <FormGroup className="w-100" style={{ marginRight: "10px" }}>
          <p>
            <FormattedMessage id="FILTER_BIRTH" defaultMessage="Birth" />
          </p>
          <FormControl
            type="date"
            value={birthDate}
            onChange={(e) => {
              setBirthDate(e.target.value);
              onChange({
                filterId: "individualTimeofBirth",
                filterKey: "Birth Date",
                clause: "filter",
                query: {
                  match: {
                    individualTimeofBirth: e.target.value,
                  },
                },
              });
            }}
          />
        </FormGroup>
        <FormGroup className="w-100" style={{ marginRight: "10px" }}>
          <p>
            <FormattedMessage id="FILTER_DEATH" defaultMessage="Death" />
          </p>
          <FormControl
            type="date"
            value={deathDate}
            onChange={(e) => {
              setDeathDate(e.target.value);
              onChange({
                filterId: "individualTimeofDeath",
                filterKey: "Death Date",
                clause: "filter",
                query: {
                  match: {
                    individualTimeofDeath: e.target.value,
                  },
                },
              });
            }}
          />
        </FormGroup>
        {/* <FormLabel>
          <FormattedMessage id="FILTER_DATE_MATCHED" />
        </FormLabel>
        <div className="d-flex flex-row w-100 mb-2">
          <FormGroup className="w-50" style={{ marginRight: "10px" }}>
            <p>
              <FormattedMessage id="FILTER_FROM" defaultMessage="From" />
            </p>
            <FormControl
              type="date"
              value={startDate}
              onChange={(e) => {
                setStartDate(e.target.value);
                updateQuery1();
              }}
            />
          </FormGroup>
          <FormGroup className="w-50">
            <p>
              <FormattedMessage id="FILTER_TO" defaultMessage="To" />
            </p>
            <FormControl
              type="date"
              value={endDate}
              onChange={(e) => {
                setEndDate(e.target.value);
                updateQuery1();
              }}
            />
          </FormGroup>
        </div> */}
      </>
    </div>
  );
}
