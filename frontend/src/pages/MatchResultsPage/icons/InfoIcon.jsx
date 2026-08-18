import React from "react";

export default function InfoIcon({
  onClick = () => {},
  style = {},
  className = "",
}) {
  return (
    <div
      style={{
        cursor: "pointer",
        ...style,
      }}
      className={className}
      onClick={onClick}
      aria-label="Zoom In"
      role="button"
      tabIndex={0}
      onKeyDown={() => {}}
    >
      <svg
        width="30"
        height="30"
        viewBox="0 0 30 30"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <path
          d="M15 0C6.72 0 0 6.72 0 15C0 23.28 6.72 30 15 30C23.28 30 30 23.28 30 15C30 6.72 23.28 0 15 0ZM15 22.5C14.175 22.5 13.5 21.825 13.5 21V15C13.5 14.175 14.175 13.5 15 13.5C15.825 13.5 16.5 14.175 16.5 15V21C16.5 21.825 15.825 22.5 15 22.5ZM16.5 10.5H13.5V7.5H16.5V10.5Z"
          fill="#00ACCE"
        />
      </svg>
    </div>
  );
}
