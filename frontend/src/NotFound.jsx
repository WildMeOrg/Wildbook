import React from "react";

export default function NotFound() {
  return (
    <div
      className="position-relative d-flex justify-content-center align-items-center"
      style={{ zIndex: 200, paddingTop: "100px", height: "80vh" }}
    >
      <img
        src="/react/images/404.png"
        alt="notFound-forest"
        className="position-absolute top-0 start-0 w-100 vh-100"
        style={{ objectFit: "cover" }}
      />
      <img
        src="/react/images/Hedgehog.png"
        alt="notFound-hedgehog"
        className="position-absolute"
        style={{ objectFit: "cover", top: "500px", left: "400px" }}
      />
      <h1>404 - Not Found</h1>
    </div>
  );
}
