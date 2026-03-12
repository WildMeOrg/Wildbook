import React from "react";

const EmptyMatchPlaceholder = ({ sectionId }) => (
  <div
    data-testid={`match-prospect-right-placeholder-${sectionId}`}
    style={{
      width: "100%",
      height: "100%",
      minHeight: "320px",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      backgroundColor: "#f8f9fa",
    }}
  >
    <svg
      width="70"
      height="63"
      viewBox="0 0 70 63"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
    >
      <path
        d="M46.6696 15.5563C38.0781 6.96482 24.1485 6.96482 15.5569 15.5563L0.00058341 31.1127L15.5569 46.669C24.1485 55.2606 38.0781 55.2606 46.6696 46.669C55.2612 38.0775 55.2612 24.1479 46.6696 15.5563Z"
        fill="#798086"
      />
      <g filter="url(#filter0_d_348_12301)">
        <path
          fillRule="evenodd"
          clipRule="evenodd"
          d="M22.5569 15.4436C31.1484 6.85211 45.0786 6.85213 53.6702 15.4436L69.2258 31.0003L53.6702 46.5569C45.0786 55.1484 31.1484 55.1484 22.5569 46.5569C13.9654 37.9654 13.9654 24.0352 22.5569 15.4436Z"
          fill="#798086"
        />
      </g>
      <path
        d="M30.1172 30.8882C30.1172 26.4699 33.6989 22.8882 38.1172 22.8882C42.5355 22.8882 46.1172 26.4699 46.1172 30.8882C46.117 35.3063 42.5353 38.8882 38.1172 38.8882C33.6991 38.8881 30.1174 35.3063 30.1172 30.8882Z"
        fill="#E5E6E7"
      />
      <defs>
        <filter
          id="filter0_d_348_12301"
          x="14.1133"
          y="9"
          width="55.1133"
          height="44.0005"
          filterUnits="userSpaceOnUse"
          colorInterpolationFilters="sRGB"
        >
          <feFlood floodOpacity="0" result="BackgroundImageFix" />
          <feColorMatrix
            in="SourceAlpha"
            type="matrix"
            values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 127 0"
            result="hardAlpha"
          />
          <feOffset dx="-2" />
          <feComposite in2="hardAlpha" operator="out" />
          <feColorMatrix
            type="matrix"
            values="0 0 0 0 1 0 0 0 0 1 0 0 0 0 1 0 0 0 1 0"
          />
          <feBlend
            mode="normal"
            in2="BackgroundImageFix"
            result="effect1_dropShadow_348_12301"
          />
          <feBlend
            mode="normal"
            in="SourceGraphic"
            in2="effect1_dropShadow_348_12301"
            result="shape"
          />
        </filter>
      </defs>
    </svg>
  </div>
);

export default EmptyMatchPlaceholder;
