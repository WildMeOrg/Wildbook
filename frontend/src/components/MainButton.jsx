import React, { useState } from "react";
import { Button } from "react-bootstrap";

export default function MainButton({
  link,
  onClick,
  disabled,
  color = "#000000",
  backgroundColor = "rgba(255, 255, 255, 0.01)",
  borderColor,
  shadowColor = "#000000",
  type = "button",
  className = "",
  children,
  style,
  noArrow,
  ...rest
}) {
  const [isHovered, setIsHovered] = useState(false);
  const boxShadowStyle = !isHovered
    ? `1px 2px 0px ${shadowColor}`
    : `4px 4px 0px ${shadowColor}`;

  return (
    <Button
      variant="primary"
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      onClick={onClick}
      disabled={disabled}
      type={type}
      className={`d-flex justify-content-center align-items-center ${className}`}
      style={{
        backgroundColor: backgroundColor,
        border: borderColor
          ? `2px solid ${borderColor}`
          : `2px solid ${backgroundColor}`,
        color: color,
        margin: "8px",
        borderRadius: "4.8px",
        fontWeight: "bold",
        whiteSpace: "nowrap",
        boxShadow: boxShadowStyle,
        ...style,
      }}
      {...rest}
    >
      <a
        href={link}
        className="d-flex align-items-center justify-content-center text-decoration-none w-100 h-100"
        style={{ color: "inherit" }}
      >
        {children}
        <span className="ms-2"></span>
        {!noArrow && <i className="bi bi-arrow-right"></i>}
      </a>
    </Button>
  );
}
