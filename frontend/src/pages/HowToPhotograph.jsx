import React, { useState, useEffect } from "react";
import { Container, Alert, Spinner } from "react-bootstrap";

const HowToPhotograph = () => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  const pdfUrl = `${process.env.PUBLIC_URL || ""}/files/how-to-photograph.pdf`;

  useEffect(() => {
    // Check if file exists
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

  if (loading) {
    return (
      <Container className="py-4">
        <div className="text-center py-5">
          <Spinner animation="border" role="status">
            <span className="visually-hidden">Loading...</span>
          </Spinner>
          <p className="mt-3">Loading document...</p>
        </div>
      </Container>
    );
  }

  if (error) {
    return (
      <Container className="py-4">
        <Alert variant="warning">
          <Alert.Heading>Unable to Load Document</Alert.Heading>
          <p>Sorry, the photography guide file could not be found.</p>
        </Alert>
      </Container>
    );
  }

  return (
    <Container className="py-4">
      <iframe
        title="how-to-photograph-pdf"
        src={pdfUrl}
        style={{
          width: "100%",
          height: "75vh",
          border: "none",
        }}
      />
    </Container>
  );
};

export default HowToPhotograph;
