import React from "react";
import { Accordion, Card, useAccordionButton } from "react-bootstrap";
import ThemeColorContext from "../ThemeColorProvider";

function ContextAwareToggle({ children, eventKey }) {
  const decoratedOnClick = useAccordionButton(eventKey);
  return (
    <div
      onClick={decoratedOnClick}
      style={{
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        padding: "0.75rem 1.25rem",
        cursor: "pointer",
        userSelect: "none",
      }}
    >
      {children}
      <span
        className="bi bi-chevron-down"
        style={{ transition: "transform .2s", marginLeft: "1rem" }}
        data-bs-target={`#collapse-${eventKey}`}
      />
    </div>
  );
}

export default function InfoAccordion({
  icon,
  title,
  data = [],
  expanded = false,
}) {
  const theme = React.useContext(ThemeColorContext);
  const defaultActiveKey = expanded ? "0" : "null";
  return (
    <Accordion defaultActiveKey={defaultActiveKey} style={{ maxWidth: 500 }}>
      <Card style={{ backgroundColor: theme.primaryColors.primary50 }}>
        <Card.Header style={{ backgroundColor: theme.primaryColors.primary50 }}>
          <ContextAwareToggle eventKey="0">
            {icon}
            <span
              style={{
                marginLeft: 8,
                wordBreak: "break-word",
                whiteSpace: "normal",
                display: "inline-block",
                maxWidth: "100%",
              }}
            >
              {title}
            </span>
          </ContextAwareToggle>
        </Card.Header>
        <Accordion.Collapse eventKey="0">
          <Card.Body>
            <ul style={{ paddingLeft: 20, margin: 0, listStyleType: "none" }}>
              {data.map((item, index) => (
                <li key={index} style={{ marginBottom: "0.5rem" }}>
                  {Object.prototype.hasOwnProperty.call(item, "value")
                    ? `${item.label} : ${item.value}`
                    : item.label}
                </li>
              ))}
            </ul>
          </Card.Body>
        </Accordion.Collapse>
      </Card>
    </Accordion>
  );
}
