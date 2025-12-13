import React from "react";
import { FormattedMessage } from "react-intl";
import Description from "../Form/Description";
import { FormGroup, FormControl } from "react-bootstrap";
import { observer } from "mobx-react-lite";

const IndividualDateFilter = observer(({ store }) => {
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
            value={
              store.formFilters
                .find((filter) => filter.filterId === "individualTimeOfBirth")
                ?.query?.range?.individualTimeOfBirth?.gte.split("T")[0] || ""
            }
            onChange={(e) => {
              if (e.target.value) {
                store.addFilter(
                  "individualTimeOfBirth",
                  "filter",
                  {
                    range: {
                      individualTimeOfBirth: {
                        gte: `${e.target.value}T00:00:00.000Z`,
                        lte: `${e.target.value}T23:59:59.000Z`,
                      },
                    },
                  },
                  "Birth Date",
                );
              } else {
                store.removeFilter("individualTimeOfBirth");
              }
            }}
          />
        </FormGroup>
        <FormGroup className="w-100" style={{ marginRight: "10px" }}>
          <p>
            <FormattedMessage id="FILTER_DEATH" defaultMessage="Death" />
          </p>
          <FormControl
            type="date"
            value={
              store.formFilters
                .find((filter) => filter.filterId === "individualTimeOfDeath")
                ?.query?.range?.individualTimeOfDeath?.gte.split("T")[0] || ""
            }
            onChange={(e) => {
              if (e.target.value) {
                store.addFilter(
                  "individualTimeOfDeath",
                  "filter",
                  {
                    range: {
                      individualTimeOfDeath: {
                        gte: `${e.target.value}T00:00:00.000Z`,
                        lte: `${e.target.value}T23:59:59.000Z`,
                      },
                    },
                  },
                  "Death Date",
                );
              } else {
                store.removeFilter("individualTimeOfDeath");
              }
            }}
          />
        </FormGroup>
      </>
    </div>
  );
});

export default IndividualDateFilter;
