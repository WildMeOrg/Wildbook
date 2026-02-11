import React from "react";
import Description from "../Form/Description";
import { FormattedMessage } from "react-intl";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import SubmitterFilter from "./SubmitterFilter";
import useGetAllBulkImportTasks from "../../models/bulkImport/useGetAllBulkImportTasks";
import ContainerWithSpinner from "../ContainerWithSpinner";

export default function MetadataFilter({ data, store }) {
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

  const { data: taskData } = useGetAllBulkImportTasks();
  const tasks = taskData?.sourceNames || [];
  const bulkImportTaskOptions = tasks.map((name) => {
    return {
      value: name,
      label: name,
    };
  });

  return (
    <div>
      <h4>
        <FormattedMessage id="FILTER_METADATA" />
      </h4>
      <Description>
        <FormattedMessage id="FILTER_METADATA_DESC" />
      </Description>
      <ContainerWithSpinner loading={store.siteSettingsLoading}>
        <FormGroupMultiSelect
          isMulti={true}
          noDesc={true}
          label="FILTER_ENCOUNTERS_STATE"
          options={encounterStatusOptions}
          term="terms"
          field="state"
          filterKey={"Encounter State"}
          store={store}
          loading={store.siteSettingsLoading}
        />
      </ContainerWithSpinner>

      <SubmitterFilter store={store} />
      <ContainerWithSpinner loading={store.siteSettingsLoading}>
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
          loading={store.siteSettingsLoading}
        />
      </ContainerWithSpinner>
      <ContainerWithSpinner loading={store.siteSettingsLoading}>
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
          loading={store.siteSettingsLoading}
        />
      </ContainerWithSpinner>
      <ContainerWithSpinner loading={store.siteSettingsLoading}>
        <FormGroupMultiSelect
          isMulti={true}
          noDesc={true}
          label="FILTER_BULK_IMPORT_FILE_NAMES"
          options={bulkImportTaskOptions}
          term="terms"
          field="importTaskSourceName"
          filterId="importTaskSourceName"
          filterKey={"Bulk Import Task"}
          store={store}
          loading={store.siteSettingsLoading}
        />
      </ContainerWithSpinner>
      <ContainerWithSpinner loading={store.siteSettingsLoading}>
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
          loading={store.siteSettingsLoading}
        />
      </ContainerWithSpinner>
    </div>
  );
}
