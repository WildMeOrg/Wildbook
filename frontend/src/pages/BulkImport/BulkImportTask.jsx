import React from "react";
import {
  Container,
  Row,
  Col,
  Breadcrumb,
  Button,
  Spinner,
} from "react-bootstrap";
import { FormattedMessage, useIntl } from "react-intl";
import { FaImage } from "react-icons/fa";
import { MdTableChart } from "react-icons/md";
import useGetBulkImportTask from "../../models/bulkImport/useGetBulkImportTask";
import { ProgressCard } from "../../components/ProgressCard";
import ThemeColorContext from "../../ThemeColorProvider";
import InfoAccordion from "../../components/InfoAccordion";
import SimpleDataTable from "../../components/SimpleDataTable";

export const BulkImportTask = () => {
  const intl = useIntl();
  const theme = React.useContext(ThemeColorContext);

  const taskId = new URLSearchParams(window.location.search).get("id");
  const { task, isLoading } = useGetBulkImportTask(taskId);

  if (isLoading) {
    return (
      <div className="d-flex justify-content-center p-5">
        <Spinner animation="border" />
      </div>
    );
  }

  const deleteTask = () => {
    console.warn("Delete task", task?.id);
  };

  const tableData = task?.encounters?.map((item) => ({
    encounterID: item.id,
    encounterDate: item.date,
    user: item.submitter?.displayName || "-",
    occurrence: "-",
    individualID: "-",
    imageCount: item.numberMediaAssets,
    class: "-",
  }));

  const columns = [
    {
      name: "Encounter ID",
      selector: "encounterID",
      cell: (row) =>
        row.encounterID ? (
          <a
            href={`/encounters/encounter.jsp?number==${row.encounterID}`}
            target="_blank"
            rel="noreferrer"
          >
            {row.encounterID}
          </a>
        ) : (
          "-"
        ),
    },
    {
      name: "Encounter Date",
      selector: "encounterDate",
    },
    {
      name: "User",
      selector: "user",
    },
    {
      name: "Occurrence",
      selector: "occurrence",
    },
    {
      name: "Individual ID",
      selector: "individualID",
      cell: (row) =>
        row.individualID ? (
          <a
            href={`/individuals.jsp?id=${row.individualID}`}
            target="_blank"
            rel="noreferrer"
          >
            {row.individualID}
          </a>
        ) : (
          "-"
        ),
    },
    {
      name: "Image Count",
      selector: "imageCount",
    },
    {
      name: "Class",
      selector: "class",
    },
  ];

  return (
    <Container className="mt-3 d-flex flex-column gap-3">
      <div>
        <h2 className="mb-0">
          <FormattedMessage
            id="BULK_IMPORT_TASK"
            defaultMessage="Bulk Import Task"
          />
        </h2>
        <Breadcrumb className="small mb-2">
          <Breadcrumb.Item href="#/bulk-import">Bulk Import</Breadcrumb.Item>
          <Breadcrumb.Item active>
            {intl.formatMessage(
              {
                id: "BULK_IMPORT_TASK_BREADCRUMB",
                defaultMessage: "Import Task: {id}",
              },
              { id: task?.id },
            )}
          </Breadcrumb.Item>
        </Breadcrumb>
      </div>

      <Row className="g-2">
        <div className="d-flex flex-row gap-3">
          {[
            { title: "Import", progress: task?.imageUploadProgress || 100 },
            {
              title: "Detection",
              progress: task?.spreadsheetUploadProgress || 10,
            },
            {
              title: "Identification",
              progress: task?.dataProcessingProgress || 0,
            },
          ].map(({ title, progress }) => (
            <ProgressCard key={title} title={title} progress={progress} />
          ))}
        </div>
      </Row>

      <section>
        <h5 className="fw-semibold mb-2">Data Uploaded</h5>

        <div
          style={{
            marginTop: "2rem",
            marginBottom: "4rem",
            display: "flex",
            flexDirection: "row",
            gap: "1rem",
          }}
        >
          <InfoAccordion
            icon={<FaImage size={20} color={theme.primaryColors.primary500} />}
            title={
              "Images Uploaded: " + (task?.iaSummary?.numberMediaAssets || 999)
            }
            data={[
              {
                label: "Has acmID",
                value: task?.iaSummary?.numberMediaAssetACMIds || 999,
              },
              {
                label: "Total Annotations",
                value: task?.iaSummary?.numberAnnotations || 999,
              },
              {
                label: "Valid for Image Analysis",
                value: task?.iaSummary?.numberMediaAssetValidIA || 999,
              },
              {
                label: "Total Marked Individuals",
                value: task?.detectionSummary?.numberMediaAssets || 999,
              },
            ]}
          />
          <InfoAccordion
            icon={
              <MdTableChart size={20} color={theme.primaryColors.primary500} />
            }
            title={task?.sourceName || "Excel Sheets Uploaded"}
            data={[{ label: "File Uploaded Successfully" }]}
          />
        </div>

        <SimpleDataTable columns={columns} data={tableData} />
      </section>

      <Row className="g-2 mb-4">
        <Col xs="auto">
          <Button variant="outline-danger" onClick={deleteTask}>
            Delete Import Task
          </Button>
        </Col>
      </Row>
    </Container>
  );
};
