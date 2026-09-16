import React, { useState, useEffect } from "react";
import { Container, Alert, Spinner } from "react-bootstrap";
import { FormattedMessage } from "react-intl";

const HowToPhotograph = () => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  const pdfUrl = `${process.env.PUBLIC_URL || ""}/files/how-to-photograph.pdf`;

  useEffect(() => {
    fetch(pdfUrl, { method: "HEAD" })
      .then((response) => {
        if (response.ok) {
          setLoading(false);
        } else {
          setError(true);
          setLoading(false);
        }
      })
      .catch(() => {
        setError(true);
        setLoading(false);
      });
  }, [pdfUrl]);

  return (
    <Container className="py-4">
      <h3 className="mb-3">
        <FormattedMessage id="PHOTOGRAPH_TITLE" />
      </h3>
      <p className="text-muted mb-4">
        <FormattedMessage id="PHOTOGRAPH_DESC" />
      </p>

      {loading && (
        <div className="text-center py-5">
          <Spinner animation="border" role="status">
            <span className="visually-hidden">Loading...</span>
          </Spinner>
          <p className="mt-3">
            <FormattedMessage id="LOADING" />
          </p>
        </div>
      )}

      {!loading && error && (
        <Alert variant="warning">
          <Alert.Heading>
            <FormattedMessage id="FILE_NOT_FOUND" />
          </Alert.Heading>
        </Alert>
      )}

      {!loading && !error && (
        <iframe
          title="how-to-photograph-pdf"
          src={pdfUrl}
          style={{
            width: "100%",
            height: "75vh",
            border: "none",
          }}
        />
      )}
    </Container>
  );
};

export default HowToPhotograph;
