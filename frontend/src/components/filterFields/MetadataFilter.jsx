import React from "react";
import Description from "../Form/Description";
import { FormattedMessage } from "react-intl";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import FormGroupText from "../Form/FormGroupText";

export default function MetadataFilter({
  data,
  store
}) {
  const encounterStatusOptions =
    data?.encounterState?.map((item) => {
      return {
        value: item,
        label: item,
      };
    }) || [];

  const organizationOptions =
    Object.entries(data?.organizations || {})?.map((item) => {
      return {
        value: item[0],
        label: item[1],
      };
    }) || [];

  const projectOptions =
    Object.entries(data?.projectsForUser || {})?.map((item) => {
      return {
        value: item[0],
        label: item[1],
      };
    }) || [];

  const assignedUserOptions =
    data?.users
      ?.filter((item) => item.username)
      .map((item) => {
        return {
          value: item.username,
          label: item.username,
        };
      }) || [];

  return (
    <div>
      <h4>
        <FormattedMessage id="FILTER_METADATA" />
      </h4>
      <Description>
        <FormattedMessage id="FILTER_METADATA_DESC" />
      </Description>

      <FormGroupMultiSelect
        isMulti={true}
        noDesc={true}
        label="FILTER_ENCOUNTERS_STATE"
        options={encounterStatusOptions}
        term="terms"
        field="state"
        filterKey={"Encounter State"}
        store={store}
      />
      <FormGroupText
        label="FILTER_SUBMITTER"
        noDesc={true}
        field="submitters"
        term="match"
        filterId="submitters"
        filterKey={"Submitter, Photographer, or Email Address"}
        store={store}
      />

      <FormGroupMultiSelect
        isMulti={true}
        noDesc={true}
        label="FILTER_ORGANIZATION_ID"
        options={organizationOptions}
        term="terms"
        field="organizations"
        filterId="organizations"
        filterKey={"Organization"}
        store={store}
      />
      <FormGroupMultiSelect
        isMulti={true}
        noDesc={true}
        label="FILTER_PROJECT_NAME"
        options={projectOptions}
        term="terms"
        field="projects"
        filterId="projects"
        filterKey={"Project Name"}
        store={store}
      />
      <FormGroupMultiSelect
        isMulti={true}
        noDesc={true}
        label="FILTER_ASSIGNED_USER"
        options={assignedUserOptions}
        term="terms"
        field="assignedUsername"
        filterId="assignedUsername"
        filterKey={"Assigned User"}
        store={store}
      />
    </div>
  );
}
