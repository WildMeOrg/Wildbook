import React, { useState } from "react";
import { NavDropdown } from "react-bootstrap";
import RightIcon from "../svg/RightIcon";

export default function HeaderDropdownItems({
  items,
  dropdownItemStyle = { color: "black", fontSize: "0.9rem" },
  dropdownTitleLinkStyle = {
    color: "black",
    fontSize: "0.9rem",
    textDecoration: "none",
  },
}) {
  const [openSubKey, setOpenSubKey] = useState(null);

  return items.map((subItem, subIndex) => {
    const baseKey = `${subItem.href || subItem.name || "menu-item"}-${subIndex}`;
    const hasSub = Array.isArray(subItem.sub) && subItem.sub.length > 0;
    const subKey = `${baseKey}__sub`;

    if (!hasSub) {
      return (
        <NavDropdown.Item
          key={baseKey}
          href={subItem.href}
          style={dropdownItemStyle}
        >
          {subItem.name}
        </NavDropdown.Item>
      );
    }

    const isOpen = openSubKey === subKey;

    return (
      <NavDropdown
        key={subKey}
        className="header-dropdown"
        drop="end"
        style={{
          paddingLeft: 8,
          fontSize: "0.9rem",
          backgroundColor: isOpen ? "#CCF0FF" : "transparent",
        }}
        onMouseEnter={() => {
          setOpenSubKey(subKey);
        }}
        onMouseLeave={() => {
          setOpenSubKey(null);
        }}
        show={isOpen}
        title={
          <div className="d-flex align-items-center justify-content-between">
            <a
              style={dropdownTitleLinkStyle}
              href={subItem.href}
              onClick={(e) => {
                e.stopPropagation();
              }}
            >
              {subItem.name}
            </a>

            <span
              role="button"
              tabIndex={0}
              aria-label="open submenu"
              onClick={(e) => {
                e.preventDefault();
                e.stopPropagation();
                setOpenSubKey((prev) => (prev === subKey ? null : subKey));
              }}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  e.stopPropagation();
                  setOpenSubKey((prev) => (prev === subKey ? null : subKey));
                }
              }}
              style={{
                paddingLeft: 12,
                cursor: "pointer",
                color: dropdownTitleLinkStyle?.color || "black",
              }}
            >
              <RightIcon />
            </span>
          </div>
        }
      >
        {subItem.sub.map((leaf, leafIndex) => {
          const leafKey = `${leaf.href || leaf.name || "leaf"}-${leafIndex}`;
          return (
            <NavDropdown.Item
              key={leafKey}
              href={leaf.href}
              style={dropdownItemStyle}
            >
              {leaf.name}
            </NavDropdown.Item>
          );
        })}
      </NavDropdown>
    );
  });
}
