import React from "react";
import Datetime from "react-datetime";
import moment from "moment";
import "react-datetime/css/react-datetime.css";

export default function DateInput({ value = "", onChange }) {
  const m = value ? moment.parseZone(value) : null;

  return (
    <Datetime
      value={m}
      dateFormat="YYYY-MM-DD"
      timeFormat="HH:mm"
      onChange={(dt) => {
        if (!moment.isMoment(dt) || !dt.isValid()) return onChange?.("");
        onChange?.(dt.clone().format("YYYY-MM-DDTHH:mm[Z]"));
      }}
      renderInput={(props, open) => (
        <input {...props} value={value || ""} onFocus={open} />
      )}
    />
  );
}
