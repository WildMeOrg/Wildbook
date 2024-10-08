import React from "react";
import { Form, Alert } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import useGetSiteSettings from "../models/useGetSiteSettings";

export default function SpeciesSection({ species, setSpecies, isValidForm }) {
  const { data } = useGetSiteSettings();
  let speciesList =
    data?.siteTaxonomies?.map((item) => {
      return {
        value: item?.scientificName,
        label: item?.scientificName,
      };
    }) || [];
  speciesList = [...speciesList, { value: "Unknown", label: "Unknown" }];

  return (
    <div>
      <h4>
        <FormattedMessage id="SPECIES" />
        <span style={{ color: "red" }}>
          <FormattedMessage id="REQUIRED_ASTERISK" />
        </span>
      </h4>
      <p>
        <FormattedMessage id="SPECIES_REQUIRED_IA_WARNING" />
      </p>

      <Form.Group>
        <Form.Label>
          <FormattedMessage id="SPECIES" />
          <span style={{ color: "red" }}>
            <FormattedMessage id="REQUIRED_ASTERISK" />
          </span>
        </Form.Label>
        <div
          style={{
            position: "relative",
            display: "inline-block",
            width: "100%",
          }}
        >
          <Form.Control
            as="select"
            required="true"
            style={{ paddingRight: "30px" }}
            onChange={(e) => {
              setSpecies(e.target.value);
            }}
          >
            <option value="">
              <FormattedMessage id="SPECIES_INSTRUCTION" />
            </option>
            {speciesList.map((option, optionIndex) => (
              <option key={optionIndex} value={option.value}>
                {option.label}
              </option>
            ))}
          </Form.Control>

          <i
            className="bi bi bi-chevron-down"
            style={{
              position: "absolute",
              right: "10px",
              top: "50%",
              transform: "translateY(-50%)",
              pointerEvents: "none",
              fontSize: "1em",
              color: "#6c757d",
            }}
          ></i>
        </div>
        {!isValidForm && !species && (
          <Alert
            variant="danger"
            style={{
              marginTop: "10px",
            }}
          >
            <i
              className="bi bi-info-circle-fill"
              style={{ marginRight: "8px", color: "#560f14" }}
            ></i>
            <FormattedMessage id="EMPTY_REQUIRED_WARNING" />
          </Alert>
        )}
      </Form.Group>
    </div>
  );
}
