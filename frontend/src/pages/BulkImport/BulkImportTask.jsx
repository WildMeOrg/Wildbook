import React, { useState, useContext } from "react";
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
import { Modal } from "react-bootstrap";

const BulkImportTask = () => {
  const intl = useIntl();
  const theme = useContext(ThemeColorContext);
  const [showError, setShowError] = useState(false);

  const taskId = new URLSearchParams(window.location.search).get("id");
  const { task, isLoading, error, refetch } = useGetBulkImportTask(taskId);
  React.useEffect(() => {
    if (error?.message) {
      setShowError(true);
    }
  }, [error]);

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
      selector: (row) => row.encounterID,
      cell: (row) =>
        row.encounterID ? (
          <a
            href={`/encounters/encounter.jsp?number=${row.encounterID}`}
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
      selector: (row) => row.encounterDate,
    },
    {
      name: "User",
      selector: (row) => row.user,
    },
    {
      name: "Occurrence",
      selector: (row) => row.occurrence,
    },
    {
      name: "Individual ID",
      selector: (row) => row.individualID,
      cell: (row) =>
        row.individualID !== "-" ? (
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
      selector: (row) => row.imageCount,
    },
    {
      name: "Class",
      selector: (row) => row.class,
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
            {
              title: "Import",
              progress: task?.importPercent || 0,
              status: (() => {
                if (task?.importPercent === 1) {
                  return "Completed";
                } else if(task?.importPercent === 0) {
                  return "Not Started";
                } else {
                  return "In Progress";
                }
              })(),
            },
            {
              title: "Detection",
              progress: task?.iaSummary?.detectionPercent || 0,
              status: task?.iaSummary?.detectionStatus || "Not Started",
            },
            {
              title: "Identification",
              progress: task?.iaSummary?.identificationPercent || 0,
              status: task?.iaSummary?.identificationStatus || "Not Started",
            },
          ].map(({ title, progress, status }) => (
            <ProgressCard
              key={title}
              title={title}
              progress={progress}
              status={status}
            />
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
              "Images Uploaded: " + (task?.iaSummary?.numberMediaAssets || 0)
            }
            data={[
              {
                label: intl.formatMessage({
                  id: "HAS_ACM_ID",
                  defaultMessage: "Has acmID",
                }),
                value: task?.iaSummary?.numberMediaAssetACMIds || 0,
              },
              {
                label: intl.formatMessage({
                  id: "TOTAL_ANNOTATIONS",
                  defaultMessage: "Total Annotations",
                }),
                value: task?.iaSummary?.numberAnnotations || 0,
              },
              {
                label: intl.formatMessage({
                  id: "VALID_FOR_IMAGE_ANALYSIS",
                  defaultMessage: "Valid for Image Analysis",
                }),
                value: task?.iaSummary?.numberMediaAssetValidIA || 0,
              },
              {
                label: intl.formatMessage({
                  id: "TOTAL_MARKED_INDIVIDUALS",
                  defaultMessage: "Total Marked Individuals",
                }),
                value: task?.detectionSummary?.numberMediaAssets || 0,
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
      <Modal show={showError} onHide={() => setShowError(false)} centered>
        <Modal.Header closeButton>
          <Modal.Title>
            <FormattedMessage
              id="BULK_IMPORT_TASK_ERROR"
              defaultMessage="Error Loading Bulk Import Task"
            />
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <p className="text-danger">{error?.message || "Unknown error"}</p>
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={() => setShowError(false)}>
            <FormattedMessage id="CLOSE" defaultMessage="Close" />
          </Button>
          <Button
            variant="primary"
            onClick={() => {
              refetch();
              setShowError(false);
            }}
          >
            <FormattedMessage id="RETRY" defaultMessage="Retry" />
          </Button>
        </Modal.Footer>
      </Modal>
    </Container>
  );
};

export default BulkImportTask;
