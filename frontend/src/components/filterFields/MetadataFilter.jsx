
import React from "react";
import Description from "../Form/Description";
import { FormattedMessage } from "react-intl";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import FormGroupText from "../Form/FormGroupText";


export default function MetadataFilter({
  data,
  onChange,
}) {

  const encounterStatusOptions = data?.encounterState?.map((item) => {
    return {
      value: item,
      label: item
    };
  }
  ) || [];

  const organizationOptions = Object.entries(data?.organizations||{})?.map((item) => {
    return {
      value: item[0],
      label: item[1]
    };
  }
  ) || [];

  const projectOptions = data?.project?.map((item) => {
    return {
      value: item,
      label: item
    };
  }
  ) || [];

  const assignedUserOptions = data?.users?.map((item) => {
    return {
      value: item.id,
      label: item.username
    };
  }
  ) || [];  

  return (
    <div>
      <h3><FormattedMessage id="FILTER_METADATA" /></h3>
      <Description>
        <FormattedMessage id="FILTER_METADATA_DESC" />
      </Description>

      <FormGroupMultiSelect
        isMulti={true}
        label="FILTER_ENCOUNTERS_STATUS"
        options={encounterStatusOptions}
        onChange={onChange}
        term="terms"
        field="state"
      />
      <FormGroupText
        label="FILTER_SUBMITTER"
        onChange={onChange}
        field="submitters"
        term="match"
        filterId="submitters"
      />
      
      <FormGroupMultiSelect
        isMulti={true}
        label="FILTER_ORGANIZATION_ID"
        options={organizationOptions}
        onChange={onChange}
        term="terms"
        field="organizations"
        filterId="organizations"
      />
      <FormGroupMultiSelect
        isMulti={true}
        label="FILTER_PROJECT_NAME"
        options={projectOptions}
        onChange={onChange}
        term="terms"
        field="projects"
        filterId = "projects"
      />
      <FormGroupMultiSelect
        isMulti={true}
        label="FILTER_ASSIGNED_USER"
        options={assignedUserOptions}
        onChange={onChange}
        term="terms"
        field="assignedUsername"
        filterId="assignedUsername"
      />
    </div>

    
  );
}