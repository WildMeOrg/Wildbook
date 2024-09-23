import React, { useContext } from "react";
import MainButton from "./MainButton";
import ThemeColorContext from "../ThemeColorProvider";

export default function Card({
  icon = true,
  title = "",
  content = "",
  buttonText = "",
  link = "",
}) {
  const themeColor = useContext(ThemeColorContext);
  return (
    <div
      className="d-flex flex-column align-items-center justify-content-center p-4 m-4 "
      style={{
        minWidth: "15rem",
        maxWidth: "20rem",
        borderRadius: "25px",
        boxShadow: "0 4px 8px 0 rgba(0,0,0,0.2)",
      }}
    >
      {icon}
      <div className="d-flex flex-column align-items-center justify-content-center ">
        <h5>{title}</h5>
        <p>{content}</p>
        <div>
          {buttonText && (
            <MainButton
              link={link}
              color="white"
              backgroundColor={themeColor?.wildMeColors?.cyan700}
              borderColor="#007bff"
            >
              {buttonText}
            </MainButton>
          )}
        </div>
      </div>
    </div>
  );
}
