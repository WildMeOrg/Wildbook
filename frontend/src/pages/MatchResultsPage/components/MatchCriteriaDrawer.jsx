import React from "react";
import { Offcanvas } from "react-bootstrap";
import { FormattedMessage } from "react-intl";

export default function MatchCriteriaDrawer({ show, onHide, filter }) {
  return (
    <Offcanvas
      show={show}
      onHide={onHide}
      placement="end"
      style={{
        borderTopLeftRadius: 14,
        borderBottomLeftRadius: 14,
      }}
    >
      <Offcanvas.Header closeButton>
        <Offcanvas.Title>
          <FormattedMessage id="MATCH_CRITERIA" />
        </Offcanvas.Title>
      </Offcanvas.Header>
      <Offcanvas.Body style={{ overflowY: "auto" }}>
        <div style={{ color: "#6c757d" }}>
          {
            <div className="mb-4">
              <FormattedMessage id="FILTER_SET_FOR_TASK" />
            </div>
          }
          {filter?.locationIds && filter?.locationIds.length > 0 && (
            <div>
              <FormattedMessage id="LOCATION_IDS" />:{" "}
              {filter?.locationIds?.join(", ")}
            </div>
          )}
          {filter?.owner && (
            <div>
              <FormattedMessage id="OWNER" />: {filter?.owner}
            </div>
          )}
          {!filter?.owner && !filter?.locationIds && (
            <div>
              <FormattedMessage id="NO_FILTER_SET_FOR_TASK" />
            </div>
          )}
        </div>
      </Offcanvas.Body>
    </Offcanvas>
  );
}
