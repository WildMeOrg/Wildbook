
import React from "react";
import Description from "../Form/Description";
import { FormattedMessage } from "react-intl";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import FormGroupText from "../Form/FormGroupText";


export default function MetadataFilter({
  data,
  onChange,
  setFormFilters,
  formFilters
}) {
  const encounterStatusOptions = data?.encounterState?.map((item) => {
    return {
      value: item,
      label: item
    };
  }
  ) || [];

  const organizationOptions = Object.entries(data?.organizations || {})?.map((item) => {
    return {
      value: item[0],
      label: item[1]
    };
  }
  ) || [];

  const projectOptions = Object.entries(data?.projectsForUser || {})?.map((item) => {
    return {
      value: item[0],
      label: item[1]
    };
  }
  ) || [];

  const assignedUserOptions = (data?.users?.filter(item => item.username).map((item) => {
    return {
      value: item.username,
      label: item.username
    };
  })) || [];
  

  return (
    <div>
      <h3><FormattedMessage id="FILTER_METADATA" /></h3>
      <Description>
        <FormattedMessage id="FILTER_METADATA_DESC" />
      </Description>

      <FormGroupMultiSelect
        isMulti={true}
        noDesc={true}
        label="FILTER_ENCOUNTERS_STATUS"
        options={encounterStatusOptions}
        onChange={onChange}
        term="terms"
        field="state"
        setFormFilters={setFormFilters}
        formFilters={formFilters}
        filterKey={"Encounter Status"}
      />
      <FormGroupText
        label="FILTER_SUBMITTER"
        noDesc={true}
        onChange={onChange}
        field="submitters"
        term="match"
        filterId="submitters"
        filterKey={"Submitter, Photographer, or Email Address"}
      />

      <FormGroupMultiSelect
        isMulti={true}
        noDesc={true}
        label="FILTER_ORGANIZATION_ID"
        options={organizationOptions}
        onChange={onChange}
        term="terms"
        field="organizations"
        filterId="organizations"
        filterKey={"Organization"}
      />
      <FormGroupMultiSelect
        isMulti={true}
        noDesc={true}
        label="FILTER_PROJECT_NAME"
        options={projectOptions}
        onChange={onChange}
        term="terms"
        field="projects"
        filterId="projects"
        filterKey={"Project Name"}
      />
      <FormGroupMultiSelect
        isMulti={true}
        noDesc={true}
        label="FILTER_ASSIGNED_USER"
        options={assignedUserOptions}
        onChange={onChange}
        term="terms"
        field="assignedUsername"
        filterId="assignedUsername"
        // setFormFilters={setFormFilters}
        // formFilters = {formFilters}
        filterKey={"Assigned User"}
      />
    </div>


  );
}