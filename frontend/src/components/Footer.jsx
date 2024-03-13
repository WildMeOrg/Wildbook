import React from 'react';
import { Container, Row, Col } from 'react-bootstrap';

const Footer = () => {
  return (
    <footer className="footer" style={{
        maxWidth: '1440px',
        marginLeft: 'auto',
        marginRight: 'auto',
        zIndex: '100',
        width: '100%',
        backgroundColor: '#cfe2ff',
        paddingTop: '100px',
        paddingBottom: '50px',
        marginTop: '100px',
      }}>
      <Container>
        <Row className="justify-content-md-center">
          <Col xs lg="2">
            <p>Documentation</p>
            <p>Community Forum</p>
            <p>Terms of Use</p>
          </Col>
          <Col md="auto">
            <p>Bulk Report</p>
            <p>Donate</p>
          </Col>
          <Col xs lg="2">
            <p>Gmail</p>
            <p>Discord</p>
            <p>Github</p>
            <p>X (Twitter)</p>
          </Col>
        </Row>
        <Row className="justify-content-md-center">
          <Col md="auto">
            <p>© 2024 @ Conservation X Labs | All Rights Reserved</p>
          </Col>
        </Row>
      </Container>
    </footer>
  );
};

export default Footer;
