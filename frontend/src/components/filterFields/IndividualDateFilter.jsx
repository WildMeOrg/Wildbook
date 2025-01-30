import React, { useState } from "react";
import { FormattedMessage } from "react-intl";
import Description from "../Form/Description";
import { FormGroup, FormControl } from "react-bootstrap";

export default function IndividualDateFilter({ onChange }) {
  const [birthDate, setBirthDate] = useState("");
  const [deathDate, setDeathDate] = useState("");

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
                filterId: "individualTimeOfBirth",
                filterKey: "Birth Date",
                clause: "filter",
                query: {
                  range: {
                    individualTimeOfBirth: {
                      gte: `${e.target.value}T00:00:00.000Z`,
                      lte: `${e.target.value}T23:59:59.000Z`,
                    },
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
                filterId: "individualTimeOfDeath",
                filterKey: "Death Date",
                clause: "filter",
                query: {
                  range: {
                    individualTimeOfDeath: {
                      gte: `${e.target.value}T00:00:00.000Z`,
                      lte: `${e.target.value}T23:59:59.000Z`,
                    },
                  },
                },
              });
            }}
          />
        </FormGroup>
      </>
    </div>
  );
}
