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
  const [subBg, setSubBg] = useState({});

  return items.map((subItem, subIndex) => {
    const key = subItem.href || `menu-item-${subIndex}`;
    const hasSub = Array.isArray(subItem.sub) && subItem.sub.length > 0;
    const subKey = `${key}__sub`;

    if (!hasSub) {
      return (
        <NavDropdown.Item
          key={key}
          href={subItem.href}
          style={dropdownItemStyle}
        >
          {subItem.name}
        </NavDropdown.Item>
      );
    }

    return (
      <NavDropdown
        key={subKey}
        className="header-dropdown"
        title={
          <a
            style={dropdownTitleLinkStyle}
            onClick={(e) => {
              e.stopPropagation();
              e.preventDefault();
              window.location.href = subItem.href;
            }}
            href={subItem.href}
          >
            {subItem.name}
            <span style={{ paddingLeft: "34px" }}>
              <RightIcon />
            </span>
          </a>
        }
        drop="end"
        style={{
          paddingLeft: 8,
          fontSize: "0.9rem",
          backgroundColor: subBg[subKey] || "transparent",
        }}
        onMouseEnter={() => {
          setSubBg((prev) => ({ ...prev, [subKey]: "#CCF0FF" }));
          setOpenSubKey(subKey);
        }}
        onMouseLeave={() => {
          setSubBg((prev) => ({ ...prev, [subKey]: "white" }));
          setOpenSubKey(null);
        }}
        show={openSubKey === subKey}
      >
        {subItem.sub.map((leaf, leafIndex) => {
          const leafKey = leaf.href || `${subKey}-leaf-${leafIndex}`;
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
