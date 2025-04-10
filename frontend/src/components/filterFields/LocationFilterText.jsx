import React from "react";
import useGetSiteSettings from "../../models/useGetSiteSettings";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import FormGroupText from "../Form/FormGroupText";

export default function LocationFilterText({ store }) {
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
        term="match"
        field="verbatimLocality"
        filterId={"verbatimLocality"}
        filterKey="Verbatim Location"
        store={store} 
      />

      <FormGroupMultiSelect
        isMulti={true}
        noDesc={true}
        label="FILTER_COUNTRY"
        options={countries}
        term="terms"
        field="country"
        filterKey="Country"
        store={store}
      />
    </div>
  );
}
