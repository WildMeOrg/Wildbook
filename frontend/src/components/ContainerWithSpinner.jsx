import React from "react";
import SmallSpinner from "./SmallSpinner";

const ContainerWithSpinner = ({ loading = false, children }) => {
  return (
    <div style={{ position: "relative" }}>
      <div style={{ paddingRight: loading ? 32 : 0 }}>{children}</div>
      {loading ? (
        <div
          style={{
            position: "absolute",
            right: 8,
            bottom: 8,
          }}
        >
          <SmallSpinner />
        </div>
      ) : null}
    </div>
  );
};

export default ContainerWithSpinner;
