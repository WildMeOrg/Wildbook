import React, { useMemo } from "react";
import Select from "react-select";
import { observer } from "mobx-react-lite";

const normalize = (str = "") =>
  str.replace(/[^\p{L}\p{N}]/gu, "").toLowerCase();

const filterOption = ({ label }, rawInput) => {
  return normalize(label).includes(normalize(rawInput));
};

const SelectCell = observer(({ store, value, columnId, rowIndex, error, setColId, setColValue }) => {
  const options = useMemo(() => {
    return store.getOptionsForSelectCell(columnId);
  }, [columnId, store]);

  return (
    <Select
      options={options}
      value={store.spreadsheetData?.[rowIndex]?.[columnId] || ""}
      onChange={(sel) => {
          const newValue = sel ? sel.value : "";
          store.updateCellValue(rowIndex, columnId, newValue);

          const { errors, warnings } = store.validateRow(rowIndex);
          const errorMsg = errors?.[columnId] || "";
          const warningMsg = warnings?.[columnId] || "";

          store.mergeValidationError(rowIndex, columnId, errorMsg);
          store.mergeValidationWarning(rowIndex, columnId, warningMsg);

          if (columnId === "Encounter.locationID") {
            setColId(columnId);
            setColValue(newValue);
            store.setApplyToAllRowModalShow(true);
          }
        }}
      isClearable
      isSearchable
      menuPortalTarget={document.body}
      menuPosition="fixed"
      filterOption={filterOption}
      placeholder="searching"
      className={
        error
          ? "react-select-container is-invalid"
          : "react-select-container"
      }
      classNamePrefix="react-select"
      styles={{
        control: (base) => ({
          ...base,
          borderColor: error ? "red" : base.borderColor,
          boxShadow: error ? "0 0 0 1px red" : base.boxShadow,
        }),
        container: (base) => ({
          ...base,
          minWidth: "150px",
          maxWidth: "250px",
        }),
        menu: (base) => ({
          ...base,
          zIndex: 9999,
        }),
        menuPortal: base => ({ ...base, zIndex: 1060 }),
      }}
    />
  );
});

export default SelectCell;
