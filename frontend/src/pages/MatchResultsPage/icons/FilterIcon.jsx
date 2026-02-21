import React from "react";

export default function FilterIcon({
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
        width="19"
        height="19"
        viewBox="0 0 19 19"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <path
          d="M3.4552 2.33333H15.1219L9.27687 9.68333L3.4552 2.33333ZM0.246871 1.87833C2.60354 4.9 6.9552 10.5 6.9552 10.5V17.5C6.9552 18.1417 7.4802 18.6667 8.12187 18.6667H10.4552C11.0969 18.6667 11.6219 18.1417 11.6219 17.5V10.5C11.6219 10.5 15.9619 4.9 18.3185 1.87833C18.9135 1.10833 18.3652 0 17.3969 0H1.16854C0.200205 0 -0.348129 1.10833 0.246871 1.87833Z"
          fill="#00ACCE"
        />
      </svg>
    </div>
  );
}
