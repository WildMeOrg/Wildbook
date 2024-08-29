import React from "react";
import ThemeColorContext from "../ThemeColorProvider";
import { FormattedMessage } from "react-intl";
import BrutalismButton from "./BrutalismButton";
import Cookies from "js-cookie";

export default function AlertBanner({ showAlert, setShowAlert }) {
  const theme = React.useContext(ThemeColorContext);
  return (
    <div
      className="fade show d-flex justify-content-between align-items-center"
      role="alert"
      style={{
        margin: "0 !important",
        padding: 10,
        boxSizing: "border-box",
        minHeight: 60,
        backgroundColor: theme.primaryColors.primary100,
        border: "none",
        borderRadius: 0,
        zIndex: 1000,
      }}
    >
      <FormattedMessage id="BANNER_ALERT" />
      <BrutalismButton
        color={theme.primaryColors.primary500}
        borderColor={theme.primaryColors.primary500}
        style={{ padding: 10, margin: 0 }}
        onClick={() => {
          setShowAlert(false);
          Cookies.set("showAlert", false, { expires: 7 });
        }
        }
      >
        <FormattedMessage id="OK" />
      </BrutalismButton>
    </div>
  );
}
