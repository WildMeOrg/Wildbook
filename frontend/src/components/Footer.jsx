import React, { useContext } from "react";
import { Container, Row, Col } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import FooterLink from "./footer/FooterLink";
import ThemeColorContext from "../ThemeColorProvider";
import FooterVisibilityContext from "../FooterVisibilityContext";
import {
  footerLinks1,
  footerLinks2,
  footerLinks3,
} from "../constants/footerMenu";

const Footer = () => {
  const theme = useContext(ThemeColorContext);
  const { visible } = useContext(FooterVisibilityContext);

  return visible ? (
    <footer
      className="footer py-3 w-100"
      style={{
        position: "relative",
        bottom: 0,
        zIndex: 2,
        backgroundColor: theme.statusColors.blue100,
        // maxWidth: "1440px",
      }}
    >
      <Container>
        <Row className="justify-content-md-center text-center">
          <Col lg={2}>
            {footerLinks1.map((link, index) => (
              <Col key={index} xs lg="2">
                <FooterLink
                  href={link.href}
                  text={<FormattedMessage id={link.id} />}
                />
              </Col>
            ))}
          </Col>

          <Col lg={2}>
            {footerLinks2.map((link, index) => (
              <Col key={index} xs lg="2" className="text-nowrap">
                <FooterLink
                  href={link.href}
                  text={<FormattedMessage id={link.id} />}
                />
              </Col>
            ))}
          </Col>

          <Col lg={2}>
            {footerLinks3.map((link, index) => (
              <Col key={index} xs lg="2" className="text-nowrap">
                <FooterLink
                  href={link.href}
                  text={<FormattedMessage id={link.id} />}
                />
              </Col>
            ))}
          </Col>
        </Row>
        <Row className="justify-content-md-center py-3">
          <Col md="auto">
            <p>
              <FormattedMessage id="FOOTER_COPYRIGHT" />
            </p>
          </Col>
        </Row>
      </Container>
    </footer>
  ) : null;
};

export default Footer;
