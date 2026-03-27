import React, { useContext } from "react";
import ThemeColorContext from "../../../ThemeColorProvider";

export default function ViewSwitcher({ activeView, onViewChange }) {
  const theme = useContext(ThemeColorContext);

  const views = [
    { id: "table", icon: "bi-table" },
    { id: "gallery", icon: "bi-grid-3x3-gap" },
    { id: "map", icon: "bi-map" },
  ];

  return (
    <div className="d-flex gap-1">
      {views.map((view) => (
        <div
          key={view.id}
          onClick={() => onViewChange(view.id)}
          style={{
            width: "32px",
            height: "32px",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            borderRadius: "6px",
            cursor: "pointer",
            backgroundColor:
              activeView === view.id
                ? theme.primaryColors.primary50
                : theme.grayColors.gray100,
            color:
              activeView === view.id
                ? theme.primaryColors.primary500
                : theme.grayColors.gray600,
            transition: "all 0.2s ease",
          }}
        >
          <i className={`bi ${view.icon}`} />
        </div>
      ))}
    </div>
  );
}
