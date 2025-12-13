import { observer } from "mobx-react-lite";
import React from "react";
import { Col, Form, Row } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { useIntl } from "react-intl";

export const FollowUpSection = observer(({ store }) => {
  const intl = useIntl();
  return (
    <div>
      <h5>
        <FormattedMessage id="FOLLOWUP_SECTION" />
      </h5>
      <p className="fs-6">
        <FormattedMessage id="CONTACT_INSTRUCTION" />
      </p>
      <Row>
        <h6>
          <FormattedMessage id="CONTACT_SUBMITTERS" />
        </h6>
        <Col>
          <Form.Group>
            <Form.Label>
              <FormattedMessage id="CONTACT_NAME" />
            </Form.Label>
            <Form.Control
              type="text"
              placeholder={intl.formatMessage({ id: "TYPE_HERE" })}
              onChange={(e) => {
                store.setSubmitterName(e.target.value);
              }}
              value={store.followUpSection?.submitter?.name}
            ></Form.Control>
          </Form.Group>
        </Col>
        <Col>
          <Form.Group>
            <Form.Label>
              <FormattedMessage id="CONTACT_EMAIL" />
            </Form.Label>
            <Form.Control
              type="text"
              placeholder={intl.formatMessage({ id: "TYPE_HERE" })}
              onChange={(e) => {
                store.setSubmitterEmail(e.target.value);
              }}
              value={store.followUpSection?.submitter?.email}
            ></Form.Control>
          </Form.Group>
        </Col>
      </Row>
      <br />
      <Row>
        <h6>
          <FormattedMessage id="CONTACT_PHOTOGRAPHERS" />
        </h6>
        <Col>
          <Form.Group>
            <Form.Label>
              <FormattedMessage id="CONTACT_NAME" />
            </Form.Label>
            <Form.Control
              type="text"
              placeholder={intl.formatMessage({ id: "TYPE_HERE" })}
              onChange={(e) => {
                store.setPhotographerName(e.target.value);
              }}
              value={store.followUpSection?.photographer?.name}
            ></Form.Control>
          </Form.Group>
        </Col>
        <Col>
          <Form.Group>
            <Form.Label>
              <FormattedMessage id="CONTACT_EMAIL" />
            </Form.Label>
            <Form.Control
              type="text"
              placeholder={intl.formatMessage({ id: "TYPE_HERE" })}
              onChange={(e) => {
                store.setPhotographerEmail(e.target.value);
              }}
              value={store.followUpSection?.photographer?.email}
            ></Form.Control>
          </Form.Group>
        </Col>
      </Row>
      <br />
      <h6>
        <FormattedMessage id="CONTACT_ADDITIONAL" />
      </h6>
      <p className="fs-6">
        <FormattedMessage id="CSL_EMAILS_INSTRUCTION" />
      </p>
      <Form.Group>
        <Form.Control
          as="textarea"
          rows={4}
          maxLength={5000}
          placeholder={intl.formatMessage({ id: "CSL_EMAILS_EXAMPLE" })}
          onChange={(e) => {
            store.setAdditionalEmails(e.target.value);
          }}
          value={store.followUpSection?.additionalEmails}
        />
      </Form.Group>
    </div>
  );
});
