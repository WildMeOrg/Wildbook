import React from "react";
import PropTypes from "prop-types";
import EditIcon from "./icons/EditIcon";
import { FormattedMessage } from "react-intl";

export default function CardWithEditButton({
  onClick,
  icon,
  title = "Card Title",
  content = "Card content goes here.",
}) {
  return (
    <div
      className="d-flex flex-column justify-content-between mt-3 position-relative mb-3"
      style={{
        padding: "10px",
        borderRadius: "10px",
        boxShadow: `0px 0px 10px rgba(0, 0, 0, 0.2)`,
        width: "100%",
        overflow: "hidden",
      }}
    >
      <div
        className="d-flex align-items-center w-100 mb-3"
        style={{ fontSize: "1rem", fontWeight: "bold" }}
      >
        {icon || <i className={`bi bi-${icon} me-2`}></i>}
        <span style={{ marginLeft: "10px" }}>
          {<FormattedMessage id={title} />}
        </span>
      </div>
      <div>{content}</div>
      <div className="d-flex justify-content-end align-items-center w-100"></div>
      <div
        style={{
          position: "absolute",
          top: "10px",
          right: "10px",
          cursor: "pointer",
        }}
        onClick={onClick}
      >
        <EditIcon />
      </div>
    </div>
  );
}

CardWithEditButton.propTypes = {
  onSave: PropTypes.func.isRequired,
  onCancel: PropTypes.func.isRequired,
  saveButtonText: PropTypes.string,
  cancelButtonText: PropTypes.string,
};
