import React, { useContext } from "react";
import { Container, Row, Col } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import FooterLink from "./footer/FooterLink";
import ThemeColorContext from "../ThemeColorProvider";
import FooterVisibilityContext from "../FooterVisibilityContext";
import { useSiteSettings } from "../SiteSettingsContext";
import {
  footerLinks1,
  footerLinks2,
  footerLinks3,
} from "../constants/footerMenu";

const Footer = () => {
  const theme = useContext(ThemeColorContext);
  const { visible } = useContext(FooterVisibilityContext);
  const { data } = useSiteSettings();
  const version = data?.system?.wildbookVersion;
  return visible ? (
    <footer
      className="footer py-3 w-100"
      style={{
        position: "relative",
        bottom: 0,
        zIndex: 2,
        backgroundColor: theme.statusColors.blue100,
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
              {version ? (
                <a
                  href={`https://github.com/WildMeOrg/Wildbook/releases/tag/${version}`}
                  style={{ color: "inherit", textDecoration: "none" }}
                  target="_blank"
                  rel="noreferrer"
                >
                  {`V${version}`}
                  {" | "}
                </a>
              ) : null}
              <FormattedMessage
                id="FOOTER_COPYRIGHT"
                values={{
                  year: new Date().getFullYear(),
                }}
              />
            </p>
          </Col>
        </Row>
      </Container>
    </footer>
  ) : null;
};

export default Footer;
