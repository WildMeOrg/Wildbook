import React from "react";
import { Form, Alert } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import useGetSiteSettings from "../models/useGetSiteSettings";
import { observer } from "mobx-react-lite";

export const ReportEncounterSpeciesSection = observer(({ reportEncounterStore }) => {
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
            *
          </span>
        </h4>
        <p>
          <FormattedMessage id="SPECIES_REQUIRED_IA_WARNING" />
        </p>
  
        <Form.Group>
          <Form.Label>
            <FormattedMessage id="SPECIES" />
            <span style={{ color: "red" }}>
              *
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
                reportEncounterStore.speciesSection.value = e.target.value;
                reportEncounterStore.speciesSection.error = e.target.value ? false : true;
              }}
            >
              <option value="">
                <FormattedMessage id="SPECIES_INSTRUCTION" />
              </option>
              <option value="option1">
                <FormattedMessage id="SPECIES_INSTRUCTION222" />
              </option>
              <option value="option2">
                <FormattedMessage id="SPECIES_INSTRUCTION333" />
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
          {reportEncounterStore.speciesSection.error && (
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
  });