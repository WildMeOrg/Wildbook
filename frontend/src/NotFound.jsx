import React from "react";

export default function NotFound() {
  return (
    <div
      style={{
        position: "relative",
        zIndex: "200",
        paddingTop: "100px",
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        height: "80vh",
      }}
    >
      <img
        src="/react/images/404.png"
        alt="notFound-forest"
        width="100%"
        className="vh-100"
        style={{
          objectFit: "cover",
          position: "absolute",
          top: 0,
          left: 0,
        }}
      />
      <img
        src="/react/images/Hedgehog.png"
        alt="notFound-hedgehog"
        objectFit="cover"
        style={{
          position: "absolute",
          top: 500,
          left: 400,
        }}
      />

      <h1>404 - Not Found</h1>
    </div>
  );
}
