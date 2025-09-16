import React from "react";
import MainButton from "../../components/MainButton";
import ThemeColorContext from "../../ThemeColorProvider";
import { FormattedMessage } from "react-intl";

export default function BulkImportSeeInstructionsButton({ store }) {
  const theme = React.useContext(ThemeColorContext);
  const handleClick = () => {
    store.setShowInstructions(true);
  };

  return (
    <div>
      <MainButton
        id="bulk-import-see-instructions-button"
        className="mt-3"
        color={theme?.wildMeColors?.cyan500}
        borderColor={theme?.wildMeColors?.cyan500}
        shadowColor={theme?.wildMeColors?.cyan500}
        noArrow={true}
        onClick={handleClick}
        style={{
          height: "40px",
          fontSize: "14px",
          padding: "0 5px",
        }}
      >
        <FormattedMessage id="SEE_INSTRUCTIONS" />
      </MainButton>
    </div>
  );
}
