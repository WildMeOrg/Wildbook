import React, { useContext } from "react";
import MainButton from "./MainButton";
import ThemeColorContext from "../ThemeColorProvider";

export default function Card({
  icon = true,
  title = "",
  content = "",
  buttonText = "",
  link = "",
  color = "black",
}) {
  const themeColor = useContext(ThemeColorContext);
  return (
    <div
      className="d-flex flex-column align-items-center bg-white p-3 m-3 fw-semibold"
      onClick={() => (window.location.href = link)}
      style={{
        height: "170px",
        width: "170px",
        borderRadius: "20px",
        boxShadow: "0 4px 8px 0 rgba(0,0,0,0.2)",
        color: color,
        cursor: "pointer",
      }}
    >
      {icon}
      {title && (
        <span
          className="text-center"
          style={{
            overflow: "hidden",
            textOverflow: "ellipsis",
            maxWidth: "100%",
          }}
          title={title}
        >
          {title}
        </span>
      )}
      {content && <p>{content}</p>}
      {buttonText && (
        <div>
          <MainButton
            link={link}
            color="white"
            backgroundColor={themeColor?.wildMeColors?.cyan700}
            borderColor="#007bff"
          >
            {buttonText}
          </MainButton>
        </div>
      )}
    </div>
  );
}
