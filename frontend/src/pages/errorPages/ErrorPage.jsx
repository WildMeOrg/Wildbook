import React, { useContext, useEffect } from "react";
import FooterVisibilityContext from "../../FooterVisibilityContext";
import { useIntl } from "react-intl";
import BrutalismButton from "../../components/BrutalismButton";
import Svg_404 from "../../components/svg/Svg_404";
import Svg_400 from "../../components/svg/Svg_400";
import Svg_401 from "../../components/svg/Svg_401";
import Svg_403 from "../../components/svg/Svg_403";
import Svg_500 from "../../components/svg/Svg_500";
import { FormattedMessage } from "react-intl";

const errorComponents = {
  404: Svg_404,
  500: Svg_500,
  400: Svg_400,
  401: Svg_401,
  403: Svg_403,
};

export default function ErrorPage({
  errorCode,
  errorId,
  errorDesc,
  position,
  loginRequired,
}) {
  const intl = useIntl();
  const { setVisible } = useContext(FooterVisibilityContext);
  const error = intl.formatMessage({ id: errorId, defaultMessage: errorId });
  const error_desc = intl.formatMessage({
    id: errorDesc,
    defaultMessage: errorDesc,
  });
  const ErrorSvg = errorComponents[errorCode];

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
          className="d-flex w-100"
          style={{
            backdropFilter: "blur(4px)",
            WebkitBackdropFilter: "blur(4px)",
            borderRadius: "16px",
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
                fillOpacity="0.02"
              />
              <foreignObject x="54%" y="20%" width="400" height="50">
                <div
                  className="d-flex align-items-center w-100 h-100"
                  xmlns="http://www.w3.org/1999/xhtml"
                  style={{
                    color: "white",
                    fontSize: "30px",
                    fontWeight: "bold",
                  }}
                >
                  {error}
                </div>
              </foreignObject>

              <foreignObject x="54%" y="32%" width="400" height="100">
                <div
                  className="d-flex align-items-center w-100 h-100"
                  xmlns="http://www.w3.org/1999/xhtml"
                  style={{
                    color: "white",
                    fontSize: "18px",
                  }}
                >
                  {error_desc}
                </div>
              </foreignObject>

              <foreignObject width="200" height="50" x="54%" y="60%">
                <BrutalismButton
                  color="white"
                  borderColor="white"
                  link={loginRequired ? "/react/login" : "/react/home"}
                  style={{ fontSize: "16px" }}
                >
                  <FormattedMessage
                    id={loginRequired ? "LOGIN_LOGIN" : "ERROR_BACK_HOME"}
                  />
                </BrutalismButton>
              </foreignObject>
              <g transform="translate(50, 0)">
                <ErrorSvg />
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
                colorInterpolationFilters="sRGB"
              >
                <feFlood floodOpacity="0" result="BackgroundImageFix" />
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
                colorInterpolationFilters="sRGB"
              >
                <feFlood floodOpacity="0" result="BackgroundImageFix" />
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
                colorInterpolationFilters="sRGB"
              >
                <feFlood floodOpacity="0" result="BackgroundImageFix" />
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
              width={errorCode === "404" ? "20%" : "30%"}
              height={errorCode === "403" ? "80%" : "55%"}
              y={`${position[1]}%`}
              x={`${position[0]}%`}
            />
          </svg>
        </div>
      </div>
    </div>
  );
}
