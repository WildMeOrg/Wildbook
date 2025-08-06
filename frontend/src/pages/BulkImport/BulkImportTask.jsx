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
    if (error?.message || task?.status === "failed") {
      setShowError(true);
    }
  }, [error, task?.status]);

  if (isLoading) {
    return (
      <div className="d-flex justify-content-center p-5">
        <Spinner animation="border" />
      </div>
    );
  }

  const deleteTask = async () => {
    if (!task?.id) return;

    const confirmed = window.confirm(
      intl.formatMessage({ id: "BULK_IMPORT_DELETE_TASK_CONFIRM" }),
    );
    if (!confirmed) return;

    try {
      const res = await fetch(`/api/v3/bulk-import/${task.id}`, {
        method: "DELETE",
      });

      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || `HTTP ${res.status}`);
      }

      alert(intl.formatMessage({ id: "BULK_IMPORT_TASK_DELETED" }));
      window.location.href = "/react/";
    } catch (err) {
      console.error("Failed to delete import task:", err);
      alert(
        intl.formatMessage(
          { id: "BULK_IMPORT_TASK_DELETE_ERROR" },
          { error: err.message || "" },
        ),
      );
    }
  };

  const tableData = task?.encounters?.map((item) => {
    const taskArray =
      task?.iaSummary?.statsAnnotations?.encounterTaskInfo?.[item.id] || [];
    const classArray =
      Array.isArray(taskArray) && taskArray?.length > 0 ? taskArray[0] : [];
    return {
      encounterID: item.id,
      encounterDate: item.date,
      user: item.submitter?.displayName || "-",
      occurrenceID: item.occurrenceId || "-",
      individualID: item.individualId || "-",
      imageCount: item.numberMediaAssets,
      class: classArray,
    };
  });

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
      cell: (row) => row.encounterDate,
      selector: (row) => row.encounterDate,
    },
    {
      name: "User",
      cell: (row) => row.user,
      selector: (row) => row.user,
    },
    {
      name: "Sighting",
      cell: (row) =>
        row.occurrenceID !== "-" ? (
          <a
            href={`/occurrence.jsp?number=${row.occurrenceID}`}
            target="_blank"
            rel="noreferrer"
          >
            {row.occurrenceID}
          </a>
        ) : (
          "-"
        ),
      selector: (row) => row.occurrenceID,
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
      cell: (row) => row.imageCount,
      selector: (row) => row.imageCount,
    },
    {
      name: "Class",
      selector: (row) => row.class,
      cell: (row) => {
        const arr = row.class;
        if (Array.isArray(arr) && arr.length === 3) {
          const link = `/iaResults.jsp?taskId=${arr[0]}`;
          return (
            <a href={link} target="_blank" rel="noreferrer">
              {arr[2]} {": "}
              {arr[1]}
            </a>
          );
        }
        return "-";
      },
    },
  ];

  const ExcelIcon = () => (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="30"
      height="30"
      viewBox="0 0 100 100"
      stroke="#979593"
      strokeMiterlimit="10"
      strokeWidth="2"
      fill="white"
    >
      <path
        stroke="#979593"
        d="M67.1716,7H27c-1.1046,0-2,0.8954-2,2v78 c0,1.1046,0.8954,2,2,2h58c1.1046,0,2-0.8954,2-2V26.8284c0-0.5304-0.2107-1.0391-0.5858-1.4142L68.5858,7.5858 C68.2107,7.2107,67.702,7,67.1716,7z"
      />
      <path
        fill="none"
        stroke="#979593"
        d="M67,7v18c0,1.1046,0.8954,2,2,2h18"
      />
      <path
        fill="#C8C6C4"
        d="M51 61H41v-2h10c.5523 0 1 .4477 1 1l0 0C52 60.5523 51.5523 61 51 61zM51 55H41v-2h10c.5523 0 1 .4477 1 1l0 0C52 54.5523 51.5523 55 51 55zM51 49H41v-2h10c.5523 0 1 .4477 1 1l0 0C52 48.5523 51.5523 49 51 49zM51 43H41v-2h10c.5523 0 1 .4477 1 1l0 0C52 42.5523 51.5523 43 51 43zM51 67H41v-2h10c.5523 0 1 .4477 1 1l0 0C52 66.5523 51.5523 67 51 67zM79 61H69c-.5523 0-1-.4477-1-1l0 0c0-.5523.4477-1 1-1h10c.5523 0 1 .4477 1 1l0 0C80 60.5523 79.5523 61 79 61zM79 67H69c-.5523 0-1-.4477-1-1l0 0c0-.5523.4477-1 1-1h10c.5523 0 1 .4477 1 1l0 0C80 66.5523 79.5523 67 79 67zM79 55H69c-.5523 0-1-.4477-1-1l0 0c0-.5523.4477-1 1-1h10c.5523 0 1 .4477 1 1l0 0C80 54.5523 79.5523 55 79 55zM79 49H69c-.5523 0-1-.4477-1-1l0 0c0-.5523.4477-1 1-1h10c.5523 0 1 .4477 1 1l0 0C80 48.5523 79.5523 49 79 49zM79 43H69c-.5523 0-1-.4477-1-1l0 0c0-.5523.4477-1 1-1h10c.5523 0 1 .4477 1 1l0 0C80 42.5523 79.5523 43 79 43zM65 61H55c-.5523 0-1-.4477-1-1l0 0c0-.5523.4477-1 1-1h10c.5523 0 1 .4477 1 1l0 0C66 60.5523 65.5523 61 65 61zM65 67H55c-.5523 0-1-.4477-1-1l0 0c0-.5523.4477-1 1-1h10c.5523 0 1 .4477 1 1l0 0C66 66.5523 65.5523 67 65 67zM65 55H55c-.5523 0-1-.4477-1-1l0 0c0-.5523.4477-1 1-1h10c.5523 0 1 .4477 1 1l0 0C66 54.5523 65.5523 55 65 55zM65 49H55c-.5523 0-1-.4477-1-1l0 0c0-.5523.4477-1 1-1h10c.5523 0 1 .4477 1 1l0 0C66 48.5523 65.5523 49 65 49zM65 43H55c-.5523 0-1-.4477-1-1l0 0c0-.5523.4477-1 1-1h10c.5523 0 1 .4477 1 1l0 0C66 42.5523 65.5523 43 65 43z"
      />
      <path
        fill="#107C41"
        d="M12,74h32c2.2091,0,4-1.7909,4-4V38c0-2.2091-1.7909-4-4-4H12c-2.2091,0-4,1.7909-4,4v32 C8,72.2091,9.7909,74,12,74z"
      />
      <path d="M16.9492,66l7.8848-12.0337L17.6123,42h5.8115l3.9424,7.6486c0.3623,0.7252,0.6113,1.2668,0.7471,1.6236 h0.0508c0.2617-0.58,0.5332-1.1436,0.8164-1.69L33.1943,42h5.335l-7.4082,11.9L38.7168,66H33.041l-4.5537-8.4017 c-0.1924-0.3116-0.374-0.6858-0.5439-1.1215H27.876c-0.0791,0.2684-0.2549,0.631-0.5264,1.0878L22.6592,66H16.9492z" />
    </svg>
  );

  return (
    <Container className="mt-3 d-flex flex-column gap-3" id="bulk-import-task">
      <div>
        <h2 className="mb-0">
          <FormattedMessage
            id="BULK_IMPORT_TASK"
            defaultMessage="Bulk Import Task"
          />
        </h2>
        <Breadcrumb className="small mb-2 mt-2">
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
              title: intl.formatMessage({
                id: "IMPORT",
              }),
              progress: task?.importPercent || 0,
              status: (() => {
                if (task?.importPercent === 1) {
                  return "complete";
                } else if (task?.importPercent) {
                  return "in_progress";
                } else {
                  return "not_started";
                }
              })(),
            },
            {
              title: intl.formatMessage({
                id: "DETECTION",
              }),
              progress: task?.iaSummary?.detectionPercent || 0,
              status: task?.iaSummary?.detectionStatus || "not_started",
            },
            {
              title: intl.formatMessage({
                id: "IDENTIFICATION",
              }),
              progress: task?.iaSummary?.identificationPercent || 0,
              status: task?.iaSummary?.identificationStatus || "not_started",
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
        <h5 className="fw-semibold mb-2">
          <FormattedMessage
            id="BULK_IMPORT_DATA_UPLOADED"
            defaultMessage="Data Uploaded"
          />
        </h5>

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
            title={intl.formatMessage(
              {
                id: "PHOTOS_UPLOADED_TITLE",
                defaultMessage: "Images uploaded: {count}",
              },
              { count: task?.iaSummary?.numberMediaAssets || 0 },
            )}
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
                value: task?.numberMarkedIndividuals || 0,
              },
            ]}
          />
          <InfoAccordion
            icon={<ExcelIcon />}
            title={intl.formatMessage(
              {
                id: "SPREADSHEET_UPLOADED_TITLE",
                defaultMessage: "Spreadsheet Uploaded: {fileName}",
              },
              { fileName: task?.sourceName || "N/A" },
            )}
            data={[
              {
                label: intl.formatMessage({
                  id: "FILE_UPLOADED_SUCCESSFULLY",
                  defaultMessage: "File Uploaded Successfully",
                }),
              },
            ]}
          />
        </div>

        <SimpleDataTable columns={columns} data={tableData} />
      </section>

      <Row className="g-2 mb-4">
        <Col xs="auto">
          <Button variant="outline-danger" onClick={deleteTask}>
            <FormattedMessage id="BULK_IMPORT_DELETE_TASK" />
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
          <p className="text-danger">
            {error?.message ||
              intl.formatMessage({
                id: "BULK_IMPORT_TASK_ERROR_DEFAULT",
                defaultMessage: "An error occurred while loading the task.",
              })}
          </p>
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
