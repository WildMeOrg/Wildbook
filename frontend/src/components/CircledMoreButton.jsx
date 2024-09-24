import React from "react";
import Button from "react-bootstrap/Button";
import ThemeColorContext from "../ThemeColorProvider";

const CircleButton = ({ href, color = "#cfe2ff", loading }) => {
  const theme = React.useContext(ThemeColorContext);
  const circleButtonStyle = {
    width: "50px",
    height: "50px",
    borderRadius: "50%",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: color,
    border: "none",
    marginRight: "10px",
  };

  return loading ? (
    <div
      className="spinner-border "
      role="status"
      style={{ color: theme.primaryColors.primary500 }}
    ></div>
  ) : (
    <Button style={circleButtonStyle} href={href}>
      <svg width="25" height="25" viewBox="0 0 25 25" fill="none">
        <path
          d="M16.4229 13.3442H5.24786C4.96453 13.3442 4.72703 13.2484 4.53536 13.0567C4.3437 12.8651 4.24786 12.6276 4.24786 12.3442C4.24786 12.0609 4.3437 11.8234 4.53536 11.6317C4.72703 11.4401 4.96453 11.3442 5.24786 11.3442H16.4229L11.5229 6.44424C11.3229 6.24424 11.227 6.0109 11.2354 5.74424C11.2437 5.47757 11.3479 5.24424 11.5479 5.04424C11.7479 4.8609 11.9812 4.76507 12.2479 4.75674C12.5145 4.7484 12.7479 4.84424 12.9479 5.04424L19.5479 11.6442C19.6479 11.7442 19.7187 11.8526 19.7604 11.9692C19.802 12.0859 19.8229 12.2109 19.8229 12.3442C19.8229 12.4776 19.802 12.6026 19.7604 12.7192C19.7187 12.8359 19.6479 12.9442 19.5479 13.0442L12.9479 19.6442C12.7645 19.8276 12.5354 19.9192 12.2604 19.9192C11.9854 19.9192 11.7479 19.8276 11.5479 19.6442C11.3479 19.4442 11.2479 19.2067 11.2479 18.9317C11.2479 18.6567 11.3479 18.4192 11.5479 18.2192L16.4229 13.3442Z"
          fill="#1C1B1F"
        />
      </svg>
    </Button>
  );
};

export default CircleButton;
