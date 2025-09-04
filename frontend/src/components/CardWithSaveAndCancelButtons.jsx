import React from "react";
import PropTypes from "prop-types";

import MainButton from "./MainButton";
import ThemeColorContext from "../ThemeColorProvider";

export default function CardWithSaveAndCancelButtons({
    onSave,
    onCancel,
    saveButtonText = "Save",
    cancelButtonText = "Cancel",
    icon,
    title = "Card Title",
    content = "Card content goes here.",
    ...props
}) {
    const theme = React.useContext(ThemeColorContext);
    return (
        <div className="d-flex flex-column justify-content-between mt-3"
            style={{
                padding: "10px",
                borderRadius: "10px",
                boxShadow: `0px 0px 10px rgba(0, 0, 0, 0.2)`,
                width: "100%"
            }}>
            <div className="d-flex align-items-center w-100 mb-3" style={{ fontSize: "1rem", fontWeight: "bold" }}>
                {icon || <i className={`bi bi-${icon} me-2`}></i>}
                <span style={{ marginLeft: "10px" }}>{title}</span>
            </div>
            <div>{content}</div>

            <div className="d-flex justify-content-between align-items-center w-100 flex-wrap mt-3">
                <MainButton
                    onClick={onSave}
                    noArrow={true}
                    backgroundColor={theme.primaryColors.primary700}
                    color="white"
                >
                    {saveButtonText}
                </MainButton>
                <MainButton
                    onClick={onCancel}
                    noArrow={true}
                    variant="secondary"
                    borderColor={theme.primaryColors.primary700}
                    color={theme.primaryColors.primary700}
                    shadowColor={theme.primaryColors.primary700}
                >
                    {cancelButtonText}
                </MainButton>
            </div>
        </div>
    );
}

CardWithSaveAndCancelButtons.propTypes = {
    onSave: PropTypes.func.isRequired,
    onCancel: PropTypes.func.isRequired,
    saveButtonText: PropTypes.string,
    cancelButtonText: PropTypes.string,
};