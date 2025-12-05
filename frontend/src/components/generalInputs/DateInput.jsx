import React from "react";
import Datetime from "react-datetime";
import moment from "moment";
import "react-datetime/css/react-datetime.css";
import { FormattedMessage } from "react-intl";

export default function DateInput({ value = "", onChange }) {
  const m = value ? moment.parseZone(value) : null;

  return (
    <>
      <h6 className="mt-2 mb-2">
        <FormattedMessage id="ENCOUNTER_DATE" />
      </h6>
      <Datetime
        value={m}
        dateFormat="YYYY-MM-DD"
        timeFormat="HH:mm"
        closeOnSelect={true}
        onChange={(dt) => {
          const s = moment.isMoment(dt)
            ? dt.format("YYYY-MM-DD[T]HH:mm")
            : String(dt);
          onChange?.(s);
        }}
        renderInput={(props, open) => (
          <input
            {...props}
            placeholder="YYYY-MM-DD HH:mm"
            value={value || null}
            onFocus={open}
            onChange={(e) => onChange?.(e.target.value)}
          />
        )}
      />
    </>
  );
}
