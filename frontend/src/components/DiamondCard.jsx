import React from "react";
import { FormattedMessage } from "react-intl";

export default function DiamondCard({ date, title, annotations }) {
  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        justifyContent: "center",
        alignItems: "center",
        marginRight: "30px",
        marginTop: "30px",
      }}
    >
      <svg
        width="300"
        height="294"
        viewBox="0 0 300 294"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <g filter="url(#filter0_d_10_128)">
          <rect
            x="14.6599"
            y="143.344"
            width="195.648"
            height="195.648"
            rx="13.9749"
            transform="rotate(-45 14.6599 143.344)"
            fill="white"
          />
        </g>
        <path
          d="M64.2313 79.113C66.96 81.8418 71.3843 81.8418 74.113 79.113C76.8418 76.3843 76.8418 71.96 74.113 69.2313C71.3843 66.5025 66.96 66.5025 64.2313 69.2313C61.5025 71.96 61.5025 76.3842 64.2313 79.113ZM64.2313 217.457C66.96 220.186 71.3843 220.186 74.113 217.457C76.8418 214.729 76.8418 210.304 74.113 207.576C71.3843 204.847 66.96 204.847 64.2313 207.576C61.5025 210.304 61.5025 214.729 64.2313 217.457ZM147.3 15.8081L265.88 134.389L267.733 132.536L149.152 13.9553L147.3 15.8081ZM265.88 152.3L147.3 270.88L149.152 272.733L267.733 154.152L265.88 152.3ZM70.0986 75.0986L129.389 15.8081L127.536 13.9553L68.2457 73.2457L70.0986 75.0986ZM129.389 270.88L70.0986 211.59L68.2457 213.443L127.536 272.733L129.389 270.88ZM147.3 270.88C142.354 275.826 134.335 275.826 129.389 270.88L127.536 272.733C133.505 278.702 143.183 278.702 149.152 272.733L147.3 270.88ZM265.88 134.389C270.826 139.335 270.826 147.354 265.88 152.3L267.733 154.152C273.702 148.183 273.702 138.505 267.733 132.536L265.88 134.389ZM149.152 13.9553C143.183 7.98614 133.505 7.98614 127.536 13.9553L129.389 15.8081C134.335 10.8622 142.354 10.8623 147.3 15.8081L149.152 13.9553Z"
          fill="#0D6EFD"
        />

        <text
          x="50%"
          y="50%"
          dominantBaseline="middle"
          textAnchor="middle"
          fill="black"
          fontWeight="bold"
        >
          {date}
        </text>
        <text
          x="50%"
          y="60%"
          dominantBaseline="middle"
          textAnchor="middle"
          fill="black"
          dy="-5"
          fontWeight="bold"
        >
          {title}
        </text>
        <text
          x="50%"
          y="68%"
          dominantBaseline="middle"
          textAnchor="middle"
          fill="black"
          dy="-5"
        >
          {`${annotations}${" "}`}
          <FormattedMessage id="ANNOTATIONS" />
        </text>
        {/* <text x="50%" y="76%" dominant-baseline="middle" text-anchor="middle" fill="black" dy="-5">
            {`${animals} animals`}
        </text> */}

        <rect
          x="120"
          y="65"
          width="52.4058"
          height="52.4058"
          rx="26.2029"
          fill="#E7F0FF"
        />
        <rect
          x="134.203"
          y="79.2029"
          width="24"
          height="24"
          fill="url(#pattern0)"
        />
        <defs>
          <filter
            id="filter0_d_10_128"
            x="6.4736"
            y="0.30741"
            width="293.061"
            height="293.061"
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
            <feOffset dy="3.49372" />
            <feGaussianBlur stdDeviation="6.98744" />
            <feComposite in2="hardAlpha" operator="out" />
            <feColorMatrix
              type="matrix"
              values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0.2 0"
            />
            <feBlend
              mode="normal"
              in2="BackgroundImageFix"
              result="effect1_dropShadow_2133_15192"
            />
            <feBlend
              mode="normal"
              in="SourceGraphic"
              in2="effect1_dropShadow_2133_15192"
              result="shape"
            />
          </filter>
          <pattern
            id="pattern0"
            patternContentUnits="objectBoundingBox"
            width="1"
            height="1"
          >
            <use href="#image0_2133_15192" transform="scale(0.0078125)" />
          </pattern>
          <image
            id="image0_2133_15192"
            width="128"
            height="128"
            href="data:image/png;base64,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"
          />
        </defs>
      </svg>
    </div>
  );
}
