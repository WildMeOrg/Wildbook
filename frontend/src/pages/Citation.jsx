import React from "react";
import { FormattedMessage } from "react-intl";
import { Container, Row, Col } from "react-bootstrap";

export default function Citation() {
  return (
    <Container>
      <Row className="my-5">
        <Col>
          <h1>
            <FormattedMessage id="CITATION_TITLE" />
          </h1>
        </Col>
      </Row>
      <Row className="mb-3">
        <Col>
          <p>
            <FormattedMessage id="CITATION_INTRODUCTION" />
          </p>
        </Col>
      </Row>
      <Row className="mb-3">
        <Col>
          <p>
            <FormattedMessage id="CITATION_AGREEMENT" />
          </p>
          <ol>
            <li>
              <FormattedMessage id="CITATION_AGREEMENT_ITEM_1" />
            </li>
            <li>
              <FormattedMessage id="CITATION_AGREEMENT_ITEM_2" />
            </li>
            <ul>
              <li>
                <FormattedMessage id="CITATION_AGREEMENT_ITEM_2_SUBITEM_1" />
              </li>
              <li>
                <strong>
                  <FormattedMessage id="CITATION_AGREEMENT_ITEM_2_SUBITEM_2" />
                </strong>
              </li>
              <ul>
                <li>
                  <FormattedMessage id="CITATION_CITATION_DETAILS_1" />
                </li>
              </ul>
              <li>
                <strong>
                  <FormattedMessage id="CITATION_AGREEMENT_ITEM_2_SUBITEM_3" />
                </strong>
              </li>
              <ul>
                <li>
                  <FormattedMessage id="CITATION_CITATION_DETAILS_2" />
                </li>
              </ul>
            </ul>
            <li>
              <FormattedMessage id="CITATION_FORWARD" />
            </li>
            <li>
              <FormattedMessage id="CITATION_DISCLAIMER" />
            </li>
          </ol>
        </Col>
      </Row>
    </Container>
  );
}
