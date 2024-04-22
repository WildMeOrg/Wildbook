import React from 'react';
import { Container, Row, Col } from 'react-bootstrap';
import { FormattedMessage } from 'react-intl';
import FooterLink from './footer/FooterLink';

const Footer = () => {
  return (
    <footer className="footer" style={{
        maxWidth: '1440px',
        marginLeft: 'auto',
        marginRight: 'auto',
        zIndex: '100',
        width: '100%',
        backgroundColor: '#cfe2ff',
        paddingTop: '20px',
        paddingBottom: '20px',
        fontSize: '1rem',
      }}>
      <Container>
        <Row className="justify-content-md-center">
          
          <Col xs lg="2">
            <FooterLink 
              href="https://www.wildme.org/donate.html" 
              text={<FormattedMessage id='FOOTER_DONATE'/>}
            />
            <FooterLink 
              href="https://docs.wildme.org/product-docs/en/wildbook/getting-started-with-wildbook" 
              text={<FormattedMessage id='FOOTER_DOCUMENTATION'/>}
            />
            <FooterLink 
              href="/terms-of-use.jsp" 
              text={<FormattedMessage id='FOOTER_TERM_OF_USE'/>}
            />

          </Col>
          
          <Col xs lg="2">
            <FooterLink 
              href="https://community.wildme.org" 
              text={<FormattedMessage id='FOOTER_COMMUNITY_FORUM'/>}
            />
              <FooterLink 
              href="https://github.com/WildMeOrg" 
              text={<FormattedMessage id='FOOTER_GITHUB'/>}
            />
          </Col>

          <Col xs lg="2">
            <FooterLink 
              href="https://www.instagram.com/conservationxlabs" 
              text={<FormattedMessage id='FOOTER_INSTAGRAM'/>}
            />
            <FooterLink 
              href="https://www.facebook.com/ConservationXLabs" 
              text={<FormattedMessage id='FOOTER_FACEBOOK'/>}
            />
            <FooterLink 
              href="https://twitter.com/conservationx" 
              text={<FormattedMessage id='FOOTER_TWITTER'/>}
            />
            <FooterLink 
              href="https://www.linkedin.com/company/conservationxlabs/" 
              text={<FormattedMessage id='FOOTER_LINKEDIN'/>}
            />

          </Col>
        </Row>
        <Row className="justify-content-md-center" style={{
          marginTop: '20px'
        }}>
          <Col md="auto">
            <p>
              <FormattedMessage id='FOOTER_COPYRIGHT'/>
            </p>
          </Col>
        </Row>
      </Container>
    </footer>
  );
};

export default Footer;
