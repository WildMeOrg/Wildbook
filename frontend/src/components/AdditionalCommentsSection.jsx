import React from "react";
import { Form } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { observer } from "mobx-react-lite";
import { useIntl } from "react-intl";

export const AdditionalCommentsSection = observer(
  ({ reportEncounterStore }) => {
    const intl = useIntl();
    return (
      <div>
        <h5>
          <FormattedMessage id="ADDITIONAL_COMMENTS_SECTION" />
        </h5>
        <Form.Group>
          <Form.Control
            as="textarea"
            rows={4}
            maxLength={5000}
            placeholder={intl.formatMessage({ id: "TYPE_HERE" })}
            onChange={(e) => {
              reportEncounterStore.setCommentsSectionValue(e.target.value);
            }}
            value={reportEncounterStore.additionalCommentsSection?.value}
          />
        </Form.Group>
      </div>
    );
  },
);
