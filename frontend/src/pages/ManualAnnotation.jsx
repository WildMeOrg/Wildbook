import React from "react";
import Form from "react-bootstrap/Form";
import { FormattedMessage } from "react-intl";
import Container from "react-bootstrap/Container";
import MainButton from "../components/MainButton";

export default function ManualAnnotation() {
  return (
    <Container>
      <h4 className="mt-3 mb-3">
        <FormattedMessage id="ADD_ANNOTATIONS" />
      </h4>
      <Form className="d-flex flex-row">
        <Form.Group controlId="formBasicEmail" className="me-3">
          <Form.Label>Annotation Type</Form.Label>
          <Form.Control type="text" placeholder="Enter Annotation Type" />
        </Form.Group>
        <Form.Group controlId="formBasicEmail">
          <Form.Label>Annotation Value</Form.Label>
          <Form.Control type="text" placeholder="Enter Annotation Value" />
        </Form.Group>
      </Form>
      <div className="d-flex justify-content-center align-items-center">
        images
      </div>
      <MainButton
        noArrow={true}
        style={{ marginTop: "1em" }}
        backgroundColor="lightblue"
        borderColor="#303336"
      >
        <FormattedMessage id="SAVE_ANNOTATION" />
      </MainButton>
    </Container>
  );
}
