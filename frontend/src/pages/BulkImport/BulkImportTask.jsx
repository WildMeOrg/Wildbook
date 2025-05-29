import React, { useEffect, useState, useMemo } from "react";
import {
  Container,
  Row,
  Col,
  Breadcrumb,
  Card,
  Table,
  Pagination,
  Button,
  Spinner,
} from "react-bootstrap";
import { FormattedMessage, useIntl } from "react-intl";
import { FaImage } from "react-icons/fa";
import { MdTableChart } from "react-icons/md";
import useGetBulkImportTask from "../../models/bulkImport/useGetBulkImportTask";
import { ProgressCard } from "../../components/ProgressCard";
import ThemeColorContext from "../../ThemeColorProvider";

const FilesHeader = ({ imageCount, spreadsheetName, theme }) => (
  <Row className="g-3 align-items-center mb-3">
    <Col xs="auto" className="d-flex align-items-center">
      <FaImage
        size={20}
        color={theme.primaryColors.primary500}
      />
      <span>{imageCount} images uploaded</span>
    </Col>
    <Col xs="auto" className="d-flex align-items-center">
      <MdTableChart size={20} color={theme.primaryColors.primary500} />
      <span>{spreadsheetName}</span>
    </Col>
  </Row>
);

export const BulkImportTask = () => {
  const intl = useIntl();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
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

  const pagedRows = task?.rows?.slice((page - 1) * pageSize, page * pageSize);

  return (
    <Container className="mt-3 d-flex flex-column gap-3">
      <div>
        <h2 className="mb-0">
          <FormattedMessage id="BULK_IMPORT_TASK" defaultMessage="Bulk Import Task" />
        </h2>
        <Breadcrumb className="small mb-2">
          <Breadcrumb.Item href="#/bulk-import">Bulk Import</Breadcrumb.Item>
          <Breadcrumb.Item active>
            {intl.formatMessage(
              { id: "BULK_IMPORT_TASK_BREADCRUMB", defaultMessage: "Import Task: {id}" },
              { id: task?.id }
            )}
          </Breadcrumb.Item>
        </Breadcrumb>
      </div>

      <Row className="g-2">

        <div className="d-flex flex-row gap-3">
          {[
            { title: "Images Uploaded", progress: task?.imageUploadProgress || 100 },
            { title: "Spreadsheet Uploaded", progress: task?.spreadsheetUploadProgress || 10 },
            { title: "Data Processed", progress: task?.dataProcessingProgress || 0 },
          ].map(({ title, progress }) => (
            <ProgressCard key={title} title={title} progress={progress} />
          ))}
        </div>

      </Row>

      <section>
        <h5 className="fw-semibold mb-2">Data Uploaded</h5>
        <FilesHeader imageCount={task.imagecount || 0} spreadsheetName={task.spreadsheetFileName || "dummy.xlsx"} theme={theme} />

        <Table bordered hover size="sm">
          <thead className="table-light align-middle">
            <tr>
              {task?.columns?.map((c) => (
                <th key={c}>{c}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {pagedRows?.map((r) => (
              <tr key={r.id} style={{ verticalAlign: "middle" }}>
                {task?.columns?.map((c) => {
                  return c === "Encounter" ? (
                    <td key={c}>
                      <a href={`/encounters/encounter.jsp?number=${r[c]}`} className="text-decoration-none">
                        {r[c] || "-"}
                      </a>
                    </td>
                  ) : (
                    <td key={c}>{r[c] || "-"}</td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </Table>

        {/* <div className="d-flex justify-content-between align-items-center mb-3">
          <span className="small text-muted">Total {task?.rows?.length} items</span>
          <Pagination size="sm" className="mb-0">
            <Pagination.First disabled={page === 1} onClick={() => setPage(1)} />
            <Pagination.Prev disabled={page === 1} onClick={() => setPage(page - 1)} />
            <Pagination.Item active>{page}</Pagination.Item>
            <Pagination.Next disabled={page === totalPages} onClick={() => setPage(page + 1)} />
            <Pagination.Last disabled={page === totalPages} onClick={() => setPage(totalPages)} />
          </Pagination>
        </div> */}
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
