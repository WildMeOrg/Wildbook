import React, { useContext, useEffect } from "react";
import FooterVisibilityContext from "../../FooterVisibilityContext";
import { useIntl } from "react-intl";
import BrutalismButton from "../../components/BrutalismButton";

export default function NotFound({
  errorCode = "500",
  errorId = "NOT_FOUND",
  errorDesc = "NOT_FOUND_DESC",
  position = [20, 35],
}) {
  const intl = useIntl();
  const { setVisible } = useContext(FooterVisibilityContext);
  const error = intl.formatMessage({ id: errorId, defaultMessage: errorId });
  const error_desc = intl.formatMessage({
    id: errorDesc,
    defaultMessage: errorDesc,
  });

  useEffect(() => {
    setVisible(false);
    return () => setVisible(true);
  }, [setVisible]);

  return (
    <div
      className="d-flex justify-content-center w-100"
      style={{
        zIndex: 200,
        height: "100vh",
        backgroundImage: `url("/react/images/${errorCode}_background.png")`,
        backgroundSize: "cover",
        backgroundPosition: "center",
        backgroundRepeat: "no-repeat",
      }}
    >
      <div
        className="d-flex justify-content-center align-items-center"
        style={{
          zIndex: 201,
          width: "78%",
        }}
      >
        <div
          style={{
            backdropFilter: "blur(4px)",
            WebkitBackdropFilter: "blur(4px)",
            borderRadius: "16px",
            width: "100%",
          }}
        >
          <svg
            width="100%"
            height="100%"
            viewBox="0 0 1074 365"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <g filter="url(#filter0_bi_2134_656)">
              <rect
                width="1074"
                height="364"
                rx="16"
                fill="#445D10"
                fill-opacity="0.02"
              />
              <text
                x="60%"
                y="33%"
                dominant-baseline="middle"
                text-anchor="middle"
                fill="white"
                font-size="26"
                font-weight="bold"
              >
                {error}
              </text>
              <text
                x="60%"
                y="50%"
                dominant-baseline="middle"
                text-anchor="middle"
                fill="white"
                font-weight="bold"
              >
                {error_desc}
              </text>
              <foreignObject width="200" height="50" x="55%" y="75%">
                <BrutalismButton
                  color="white"
                  borderColor="white"
                  link="/react/home"
                >
                  Back Home
                </BrutalismButton>
              </foreignObject>

              <g clip-path="url(#clip1_2134_656)">
                <g filter="url(#filter2_i_2134_656)">
                  <path
                    d="M195.612 218.802V254.471H51.8379L49.0942 226.21L129.35 97.1152H165.842L126.194 162.006L93.269 218.802H195.612ZM175.445 97.1152V296.862H129.213V97.1152H175.445ZM355.026 178.743V215.235C355.026 229.594 353.334 242.078 349.95 252.688C346.657 263.297 341.901 272.077 335.682 279.028C329.554 285.979 322.238 291.146 313.732 294.53C305.226 297.914 295.852 299.606 285.608 299.606C277.377 299.606 269.694 298.554 262.56 296.451C255.427 294.256 248.979 290.963 243.217 286.573C237.546 282.092 232.653 276.513 228.538 269.836C224.422 263.16 221.266 255.294 219.071 246.24C216.876 237.094 215.779 226.759 215.779 215.235V178.743C215.779 164.292 217.425 151.808 220.718 141.29C224.102 130.681 228.903 121.901 235.123 114.95C241.342 107.999 248.704 102.831 257.21 99.4474C265.716 96.0634 275.09 94.3714 285.334 94.3714C293.565 94.3714 301.202 95.4689 308.244 97.6639C315.378 99.7675 321.826 103.06 327.588 107.542C333.35 111.932 338.243 117.465 342.267 124.141C346.383 130.818 349.538 138.729 351.733 147.875C353.928 156.93 355.026 167.219 355.026 178.743ZM308.793 220.86V172.844C308.793 166.441 308.427 160.862 307.696 156.106C307.055 151.351 306.049 147.326 304.677 144.034C303.397 140.741 301.796 138.089 299.876 136.077C297.955 133.973 295.76 132.464 293.291 131.55C290.913 130.544 288.26 130.041 285.334 130.041C281.675 130.041 278.383 130.818 275.456 132.373C272.621 133.836 270.197 136.214 268.185 139.507C266.173 142.799 264.618 147.189 263.521 152.677C262.515 158.164 262.012 164.887 262.012 172.844V220.86C262.012 227.353 262.332 233.024 262.972 237.871C263.704 242.627 264.71 246.697 265.99 250.081C267.362 253.374 269.008 256.072 270.929 258.175C272.85 260.187 275.045 261.651 277.514 262.565C279.983 263.48 282.681 263.937 285.608 263.937C289.175 263.937 292.376 263.205 295.211 261.742C298.047 260.279 300.47 257.901 302.482 254.608C304.586 251.224 306.141 246.788 307.147 241.301C308.244 235.722 308.793 228.908 308.793 220.86ZM521.573 218.802V254.471H377.799L375.055 226.21L455.311 97.1152H491.803L452.156 162.006L419.23 218.802H521.573ZM501.407 97.1152V296.862H455.174V97.1152H501.407Z"
                    fill="#C1C3B9"
                  />
                </g>
              </g>
            </g>
            <defs>
              <filter
                id="filter0_bi_2134_656"
                x="-8"
                y="-8"
                width="1090"
                height="380.5"
                filterUnits="userSpaceOnUse"
                color-interpolation-filters="sRGB"
              >
                <feFlood flood-opacity="0" result="BackgroundImageFix" />
                <feGaussianBlur in="BackgroundImageFix" stdDeviation="4" />
                <feComposite
                  in2="SourceAlpha"
                  operator="in"
                  result="effect1_backgroundBlur_2134_656"
                />
                <feBlend
                  mode="normal"
                  in="SourceGraphic"
                  in2="effect1_backgroundBlur_2134_656"
                  result="shape"
                />
                <feColorMatrix
                  in="SourceAlpha"
                  type="matrix"
                  values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 127 0"
                  result="hardAlpha"
                />
                <feOffset dx="-4.5" dy="4.5" />
                <feGaussianBlur stdDeviation="5" />
                <feComposite
                  in2="hardAlpha"
                  operator="arithmetic"
                  k2="-1"
                  k3="1"
                />
                <feColorMatrix
                  type="matrix"
                  values="0 0 0 0 0.933333 0 0 0 0 0.952941 0 0 0 0 0.709804 0 0 0 1 0"
                />
                <feBlend
                  mode="normal"
                  in2="shape"
                  result="effect2_innerShadow_2134_656"
                />
              </filter>
              <filter
                id="filter1_d_2134_656"
                x="575"
                y="245"
                width="133"
                height="48"
                filterUnits="userSpaceOnUse"
                color-interpolation-filters="sRGB"
              >
                <feFlood flood-opacity="0" result="BackgroundImageFix" />
                <feColorMatrix
                  in="SourceAlpha"
                  type="matrix"
                  values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 127 0"
                  result="hardAlpha"
                />
                <feOffset dx="4" dy="4" />
                <feComposite in2="hardAlpha" operator="out" />
                <feColorMatrix
                  type="matrix"
                  values="0 0 0 0 1 0 0 0 0 1 0 0 0 0 1 0 0 0 1 0"
                />
                <feBlend
                  mode="normal"
                  in2="BackgroundImageFix"
                  result="effect1_dropShadow_2134_656"
                />
                <feBlend
                  mode="normal"
                  in="SourceGraphic"
                  in2="effect1_dropShadow_2134_656"
                  result="shape"
                />
              </filter>
              <filter
                id="filter2_i_2134_656"
                x="44.5987"
                y="94.3714"
                width="476.975"
                height="209.73"
                filterUnits="userSpaceOnUse"
                color-interpolation-filters="sRGB"
              >
                <feFlood flood-opacity="0" result="BackgroundImageFix" />
                <feBlend
                  mode="normal"
                  in="SourceGraphic"
                  in2="BackgroundImageFix"
                  result="shape"
                />
                <feColorMatrix
                  in="SourceAlpha"
                  type="matrix"
                  values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 127 0"
                  result="hardAlpha"
                />
                <feOffset dx="-4.5" dy="4.49541" />
                <feGaussianBlur stdDeviation="6.24771" />
                <feComposite
                  in2="hardAlpha"
                  operator="arithmetic"
                  k2="-1"
                  k3="1"
                />
                <feColorMatrix
                  type="matrix"
                  values="0 0 0 0 0.93238 0 0 0 0 0.951667 0 0 0 0 0.710578 0 0 0 1 0"
                />
                <feBlend
                  mode="normal"
                  in2="shape"
                  result="effect1_innerShadow_2134_656"
                />
              </filter>
              <clipPath id="clip0_2134_656">
                <rect
                  width="103"
                  height="30"
                  fill="white"
                  transform="translate(588 252)"
                />
              </clipPath>
              <clipPath id="clip1_2134_656">
                <rect
                  width="489"
                  height="341"
                  fill="white"
                  transform="translate(41 23.5)"
                />
              </clipPath>
            </defs>
            <image
              href={`/react/images/${errorCode}_animal.png`}
              alt="notFound-hedgehog"
              width="30%"
              height="55%"
              y={`${position[1]}%`}
              x={`${position[0]}%`}
            />
          </svg>
        </div>
      </div>
    </div>
  );
}
