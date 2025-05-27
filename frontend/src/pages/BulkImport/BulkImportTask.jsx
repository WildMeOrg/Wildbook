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
import {
  BsCheckCircleFill,
  BsImage,
  BsFileEarmarkExcel,
  BsShieldExclamation,
} from "react-icons/bs";
import { observer } from "mobx-react-lite";

const StepCard = ({ icon, title, subtitle, variant = "success" }) => (
  <Card
    className="text-center d-flex align-items-center justify-content-center p-2"
    style={{ width: 110, minHeight: 90 }}
  >
    <div className="mb-1" style={{ fontSize: 24, color: variant === "success" ? "#16a34a" : "#0d6efd" }}>
      {icon}
    </div>
    <div style={{ fontSize: 12, lineHeight: 1.1 }}>
      {title}
      <br />
      <span className="text-muted">{subtitle}</span>
    </div>
  </Card>
);

const FilesHeader = ({ imageCount, spreadsheetName }) => (
  <Row className="g-3 align-items-center mb-3">
    <Col xs="auto" className="d-flex align-items-center">
      <BsImage className="me-1" />
      <span>{imageCount} images uploaded</span>
    </Col>
    <Col xs="auto" className="d-flex align-items-center">
      <BsFileEarmarkExcel className="me-1" />
      <span>{spreadsheetName}</span>
    </Col>
  </Row>
);

export const BulkImportTask = observer(({
  taskId,
  onDeleteTask,
}) => {
  const intl = useIntl();
  const [loading, setLoading] = useState(true);
  const [task, setTask] = useState(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  useEffect(() => {
    (async () => {
      await new Promise((r) => setTimeout(r, 600));
      setTask(dummyTaskData);
      setLoading(false);
    })();
  }, []);

  const totalPages = useMemo(
    () => (task ? Math.ceil(task.rows.length / pageSize) : 1),
    [task, pageSize]
  );

  if (loading) {
    return (
      <div className="d-flex justify-content-center p-5">
        <Spinner animation="border" />
      </div>
    );
  }

  const deleteTask = () => {
    console.warn("Delete task", task.id);
  };

  const pagedRows = task.rows.slice((page - 1) * pageSize, page * pageSize);

  return (
    <Container fluid className="mt-3 d-flex flex-column gap-3">
      {/* Title & breadcrumb */}
      <div>
        <h2 className="mb-0">
          <FormattedMessage id="BULK_IMPORT_TASK" defaultMessage="Bulk Import Task" />
        </h2>
        <Breadcrumb className="small mb-2">
          <Breadcrumb.Item href="#/bulk-import">Bulk Import</Breadcrumb.Item>
          <Breadcrumb.Item active>
            {intl.formatMessage(
              { id: "BULK_IMPORT_TASK_BREADCRUMB", defaultMessage: "Import Task: {id}" },
              { id: task.id }
            )}
          </Breadcrumb.Item>
        </Breadcrumb>
      </div>

      {/* Step indicator */}
      <Row className="g-2">
        {task.steps.map((s) => (
          <Col key={s.name} xs="auto">
            <StepCard
              icon={s.status === "completed" ? <BsCheckCircleFill /> : <BsShieldExclamation />}
              title={s.title}
              subtitle={s.subtitle}
              variant={s.status === "completed" ? "success" : "primary"}
            />
          </Col>
        ))}
      </Row>

      {/* Data uploaded */}
      <section>
        <h5 className="fw-semibold mb-2">Data Uploaded</h5>
        <FilesHeader imageCount={task.imageCount} spreadsheetName={task.spreadsheetName} />

        <Table bordered hover size="sm">
          <thead className="table-light align-middle">
            <tr>
              {task.columns.map((c) => (
                <th key={c}>{c}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {pagedRows.map((r) => (
              <tr key={r.id} style={{ verticalAlign: "middle" }}>
                {task.columns.map((c) => {
                  console.log("Column:", c, "Row:", r[c]);
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

        {/* pagination */}
        <div className="d-flex justify-content-between align-items-center mb-3">
          <span className="small text-muted">Total {task.rows.length} items</span>
          <Pagination size="sm" className="mb-0">
            <Pagination.First disabled={page === 1} onClick={() => setPage(1)} />
            <Pagination.Prev disabled={page === 1} onClick={() => setPage(page - 1)} />
            <Pagination.Item active>{page}</Pagination.Item>
            <Pagination.Next disabled={page === totalPages} onClick={() => setPage(page + 1)} />
            <Pagination.Last disabled={page === totalPages} onClick={() => setPage(totalPages)} />
          </Pagination>
        </div>
      </section>

      {/* action buttons */}
      <Row className="g-2 mb-4">

        <Col xs="auto">
          <Button variant="outline-danger" onClick={deleteTask}>
            Delete Import Task
          </Button>
        </Col>
      </Row>
    </Container>
  );
});

const dummyTaskData = {
  id: "2b7e1b6e-acd5-4cxx-9xys-dfes-1re5sd23",
  imageCount: 123,
  spreadsheetName: "spreadsheet-name.xlsx",
  steps: [
    { name: "import", title: "Import", subtitle: "Completed", status: "completed" },
    { name: "validation", title: "Image Validation", subtitle: "Completed", status: "completed" },
    { name: "detection", title: "Detection", subtitle: "Completed", status: "completed" },
    { name: "identification", title: "Identification", subtitle: "Queued", status: "queued" },
  ],
  columns: [
    "Encounter",
    "Encounter Date",
    "User",
    "Occurrence",
    "Individual",
    "Images",
    "Class",
  ],
  rows: new Array(85).fill(null).map((_, i) => ({
    id: `enc-${i}`,
    Encounter: `123abc${i}`,
    "Encounter Date": "2024-03-08",
    User: "Erin Keene",
    Occurrence: "-",
    Individual: "-",
    Images: "2 images",
    Class: "-",
  })),
  locationOptions: [
    { value: "Cape Washington", label: "Cape Washington" },
    { value: "Los Angeles", label: "Los Angeles" },
    { value: "New York", label: "New York" },
  ],
};
