import React, { useContext } from "react";
import ThemeColorContext from "../../ThemeColorProvider";

export default function EncounterIcon({ height = 24, width = 24 }) {
  const themeColor = useContext(ThemeColorContext);
  return (
    <div>
      <svg
        width={width}
        height={height}
        viewBox="0 0 44 44"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <g filter="url(#filter0_d_773_5993)">
          <rect
            x="4"
            y="2"
            width="36"
            height="36"
            rx="18"
            fill={themeColor.primaryColors.primary500}
          />
          <path
            d="M27 20C27 17.24 24.76 15 22 15C19.24 15 17 17.24 17 20C17 22.76 19.24 25 22 25C24.76 25 27 22.76 27 20ZM22 23C20.35 23 19 21.65 19 20C19 18.35 20.35 17 22 17C23.65 17 25 18.35 25 20C25 21.65 23.65 23 22 23ZM15 23H13V27C13 28.1 13.9 29 15 29H19V27H15V23ZM15 13H19V11H15C13.9 11 13 11.9 13 13V17H15V13ZM29 11H25V13H29V17H31V13C31 11.9 30.1 11 29 11ZM29 27H25V29H29C30.1 29 31 28.1 31 27V23H29V27Z"
            fill="white"
          />
        </g>
        <defs>
          <filter
            id="filter0_d_773_5993"
            x="0"
            y="0"
            width="44"
            height="44"
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
            <feOffset dy="2" />
            <feGaussianBlur stdDeviation="2" />
            <feColorMatrix
              type="matrix"
              values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0.075 0"
            />
            <feBlend
              mode="normal"
              in2="BackgroundImageFix"
              result="effect1_dropShadow_773_5993"
            />
            <feBlend
              mode="normal"
              in="SourceGraphic"
              in2="effect1_dropShadow_773_5993"
              result="shape"
            />
          </filter>
        </defs>
      </svg>
    </div>
  );
}
