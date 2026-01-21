import React from "react";
import { Offcanvas } from "react-bootstrap";

export default function MatchCriteriaDrawer({ show, onHide, filter }) {
  return (
    <Offcanvas
      show={show}
      onHide={onHide}
      placement="end"
      style={{
        borderTopLeftRadius: 14,
        borderBottomLeftRadius: 14,
        overflow: "hidden",
      }}
    >
      <Offcanvas.Header closeButton>
        <Offcanvas.Title>Match Criteria</Offcanvas.Title>
      </Offcanvas.Header>
      <Offcanvas.Body>
        <div style={{ color: "#6c757d" }}>
          {filter?.locationIds && filter?.locationIds.length > 0 && (
            <div>Location IDs: {filter?.locationIds?.join(", ")}</div>
          )}
          {filter?.owner && <div>Owner: filter?.owner</div>}
        </div>
      </Offcanvas.Body>
    </Offcanvas>
  );
}
