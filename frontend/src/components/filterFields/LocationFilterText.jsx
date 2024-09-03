import React from "react";
import useGetSiteSettings from "../../models/useGetSiteSettings";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import FormGroupText from "../Form/FormGroupText";

export default function LocationFilterText({ onChange }) {
  const { data } = useGetSiteSettings();
  const countries =
    data?.country?.map((data) => {
      return {
        value: data,
        label: data,
      };
    }) || [];

  return (
    <div className="mt-3">
      <FormGroupText
        label="FILTER_VERBATIM_LOCATION"
        onChange={onChange}
        term="match"
        field="verbatimLocality"
        filterId={"verbatimLocality"}
        filterKey="Verbatim Location"
      />

      <FormGroupMultiSelect
        isMulti={true}
        noDesc={true}
        label="FILTER_COUNTRY"
        options={countries}
        onChange={onChange}
        term="terms"
        field="country"
        filterKey="Country"
      />
    </div>
  );
}
