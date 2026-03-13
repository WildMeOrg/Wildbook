import React, { useEffect, useMemo, useRef, useState } from "react";
import { Select, Button, Divider, Checkbox } from "antd";

const footerStyle = {
  padding: 8,
  display: "flex",
  justifyContent: "flex-end",
  alignItems: "center",
};

const optionRowStyle = {
  display: "flex",
  alignItems: "center",
  gap: 8,
};

export default function MultiSelectWithCheckbox({
  options,
  value,
  onChangeCommitted,
  placeholder = "Select...",
  style,
  disabled,
}) {
  const selectRef = useRef(null);
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState(value ?? []);

  useEffect(() => {
    setDraft(value ?? []);
  }, [value]);

  const draftSet = useMemo(() => new Set(draft ?? []), [draft]);

  const handleDone = () => {
    onChangeCommitted?.(draft);
    setOpen(false);
    selectRef.current?.blur?.();
  };

  return (
    <Select
      ref={selectRef}
      mode="multiple"
      value={draft}
      disabled={disabled}
      placeholder={placeholder}
      style={{ width: 320, ...style }}
      open={open}
      onDropdownVisibleChange={setOpen}
      onChange={(next) => setDraft(next)}
      maxTagCount={2}
      options={options}
      filterOption={(input, option) =>
        (option?.label ?? "").toLowerCase().includes(input.toLowerCase())
      }
      dropdownRender={(menu) => (
        <>
          {menu}
          <Divider style={{ margin: "8px 0" }} />
          <div style={footerStyle}>
            <Button type="primary" size="small" onClick={handleDone}>
              Done
            </Button>
          </div>
        </>
      )}
      optionRender={(option) => {
        const checked = draftSet.has(option.value);
        return (
          <div style={optionRowStyle}>
            <Checkbox checked={checked} />
            <span>{option.label}</span>
          </div>
        );
      }}
    />
  );
}
