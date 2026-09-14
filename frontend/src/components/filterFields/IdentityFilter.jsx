import React from "react";
import { FormattedMessage } from "react-intl";
import Form from "react-bootstrap/Form";
import Description from "../Form/Description";
import FormGroupText from "../Form/FormGroupText";
import FormControl from "react-bootstrap/FormControl";
import { useIntl } from "react-intl";
import { observer } from "mobx-react-lite";

const IdentityFilter = observer(({ store }) => {
  const includeEncounters = <FormattedMessage id="FILTER_NO_INDIVIDUAL_ID" />;
  const intl = useIntl();

  return (
    <div>
      <h4>
        <FormattedMessage id="FILTER_IDENTITY" />
      </h4>
      <Description>
        <FormattedMessage id="FILTER_IDENTITY_DESC" />
      </Description>
      <Form className="d-flex flex-row aligh-items-center">
        <FormattedMessage id="FILTER_SIGHTED_AT_LEAST" />
        <FormControl
          type="number"
          style={{
            width: "100px",
            marginLeft: "10px",
            marginRight: "10px",
          }}
          placeholder={intl.formatMessage({ id: "TYPE_NUMBER" })}
          value={
            store.formFilters.find(
              (filter) => filter.filterId === "individualNumberEncounters",
            )?.query?.range?.individualNumberEncounters?.gte || ""
          }
          onChange={(e) => {
            if (e.target.value) {
              store.addFilter(
                "individualNumberEncounters",
                "filter",
                {
                  range: {
                    individualNumberEncounters: { gte: e.target.value },
                  },
                },
                "Number of Encounters",
              );
            } else {
              store.removeFilter("individualNumberEncounters");
            }
          }}
        />
        <FormattedMessage id="FILTER_TIMES" />
      </Form>

      <Form>
        <Form.Check
          label={includeEncounters}
          type="checkbox"
          id="custom-checkbox"
          checked={
            store.formFilters.find(
              (filter) =>
                filter.filterId === "individualId" &&
                filter.clause === "must_not",
            )?.query?.exists?.field === "individualId"
          }
          onChange={(e) => {
            if (e.target.checked) {
              store.addFilter(
                "individualId",
                "must_not",
                {
                  exists: {
                    field: "individualId",
                  },
                },
                "Include only encounters with no assigned Individual ID",
              );
            } else {
              store.removeFilter("individualId");
            }
          }}
        />
      </Form>
      <FormGroupText
        label="FILTER_NUMBER_REPORTED_MARKED_INDIVIDUALS"
        noDesc={true}
        field={"occurrenceIndividualCount"}
        term={"match"}
        filterId={"occurrenceIndividualCount"}
        filterKey={"Number of Reported Marked Individuals"}
        store={store}
      />
      <FormGroupText
        label="FILTER_ALTERNATIVE_ID"
        noDesc={true}
        field={"otherCatalogNumbers"}
        term={"term"}
        filterId={"otherCatalogNumbers"}
        filterKey={"Alternative ID"}
        store={store}
      />
      <FormGroupText
        label="FILTER_INDIVIDUAL_NAME"
        field={"individualNames"}
        term={"match"}
        filterId={"individualNames"}
        filterKey={"Individual Names"}
        store={store}
      />
    </div>
  );
});

export default IdentityFilter;
