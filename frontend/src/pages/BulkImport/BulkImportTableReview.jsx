import React from "react";
import { observer } from "mobx-react-lite";
import EditableDataTable from "./EditableDataTable";
import { useContext } from "react";
import ThemeContext from "../../ThemeColorProvider";
import { FormattedMessage } from "react-intl";
import MainButton from "../../components/MainButton";

export const BulkImportTableReview = observer(({ store }) => {
  const theme = useContext(ThemeContext);

  return (
    <div>
      <EditableDataTable store={store} />
      <div className="d-flex flex-row justify-content-between mt-4">
        <MainButton
          onClick={() => {
            store.setActiveStep(3);
          }}
          disabled ={store._uploadFinished || Object.keys(store.validateSpreadsheet()).length > 0}
          backgroundColor={theme.wildMeColors.cyan700}
          color={theme.defaultColors.white}
          noArrow={true}
          style={{ width: "auto", fontSize: "1rem", marginLeft: "auto" }}
        >
          <FormattedMessage id="START_BULK_IMPORT" />
        </MainButton>
      </div>
    </div>
  );
});
