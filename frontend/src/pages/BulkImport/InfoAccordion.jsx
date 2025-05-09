
import React from 'react';
import { Accordion, Card, useAccordionButton } from 'react-bootstrap';

function ContextAwareToggle({ children, eventKey }) {
  const decoratedOnClick = useAccordionButton(eventKey);
  return (
    <div
      onClick={decoratedOnClick}
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: '0.75rem 1.25rem',
        background: '#f7f7f7',
        cursor: 'pointer',
        userSelect: 'none',
      }}
    >
      {children}
      <span className="bi bi-caret-right"  
            style={{ transition: 'transform .2s' }}
            data-bs-target={`#collapse-${eventKey}`}
      />
    </div>
  );
}

export default function InfoAccordion({icon, title, data=[]}) {
  return (
    <Accordion defaultActiveKey="0" style={{ maxWidth: 400,  }}>
      <Card>
        <Card.Header>
          <ContextAwareToggle eventKey="0">
            {icon} {title}
          </ContextAwareToggle>
        </Card.Header>
        <Accordion.Collapse eventKey="0">
          <Card.Body>
            <ul style={{ paddingLeft: 20, margin: 0, listStyleType: 'none' }}>
              {data.map((item, index) => (
                <li key={index} style={{ marginBottom: '0.5rem' }}>
                  {item.label}: {item.value}
                </li>
              ))}
            </ul>
          </Card.Body>
        </Accordion.Collapse>
      </Card>
    </Accordion>
  );
}
