import React from "react";
import { FormGroup, FormLabel, FormControl } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { useIntl } from "react-intl";
import { observer } from "mobx-react-lite";

const SubmitterFilter = observer(({ store }) => {
  const intl = useIntl();
  const currentValue =
    store.formFilters
      .find((f) => f.filterId === "submitters")
      ?.query.bool.should[0].wildcard.photographers.replace(/\*/g, "") || "";
  const handleInputChange = (e) => {
    if (e.target.value.trim() === "") {
      store.removeFilter("submitters");
      return;
    }
    store.addFilter(
      "submitters",
      "filter",
      {
        bool: {
          should: [
            {
              wildcard: {
                photographers: `*${e.target.value}*`,
              },
            },
            {
              wildcard: {
                submitters: `*${e.target.value}*`,
              },
            },
          ],
        },
      },
      "Submitter, Photographer, or Email Address",
    );
  };

  return (
    <FormGroup className="mt-2">
      <FormLabel>
        <FormattedMessage id={"FILTER_SUBMITTER"} defaultMessage="" />
      </FormLabel>
      <FormControl
        type="text"
        placeholder={intl.formatMessage({ id: "TYPE_HERE" })}
        value={currentValue}
        onChange={handleInputChange}
      />
    </FormGroup>
  );
});

export default SubmitterFilter;
