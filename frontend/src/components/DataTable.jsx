import React, { useState, useEffect, useMemo } from "react";
import DataTable from "react-data-table-component";
import ReactPaginate from "react-paginate";
import { InputGroup, Form, Button, Container, Row, Col } from "react-bootstrap";
import "bootstrap/dist/css/bootstrap.min.css";
import "../css/dataTable.css";

const customStyles = {
  rows: {
    style: {
      border: "none !important",
      borderRadius: "5px",
    },
  },
};

const conditionalRowStyles = [
  {
    when: (row) => row.tableID % 2 === 0,
    style: {
      backgroundColor: "#ffffff", // Light gray color
    },
  },
  {
    when: (row) => row.tableID % 2 !== 0,
    style: {
      backgroundColor: "#f2f2f2", // White color
    },
  },
];

const MyDataTable = ({
  title = "",
  columnNames = [],
  totalItems = 0,
  tableData = [],
  page,
  perPage,
  onPageChange,
  onPerPageChange,
  onSelectedRowsChange = () => {},
}) => {
  const [data, setData] = useState([]);
  const [filterText, setFilterText] = useState("");
  const [goToPage, setGoToPage] = useState("");
  const perPageOptions = [10, 20, 30, 40, 45];

  const wrappedColumns = useMemo(
    () =>
      columnNames.map((col) => {
        return ({        
        name: col.name.charAt(0).toUpperCase() + col.name.slice(1),
        selector: (row) => row[col.selector], // Accessor function for the column data
        sortable: true, // Make the column sortable
      })}),
    [columnNames],
  );

  useEffect(() => {
    setData(tableData.map((row, index) => ({ tableID: index, ...row })));
  }, [tableData]);

  const handlePageChange = ({ selected }) => {
    onPageChange(selected);
  };

  const handleGoToPageChange = (event) => {
    setGoToPage(event.target.value);
  };

  const handleGoToPageSubmit = () => {
    const pageNumber = Number(goToPage) - 1;
    if (
      !isNaN(pageNumber) &&
      pageNumber >= 0 &&
      pageNumber < Math.ceil(totalItems / perPage)
    ) {
      onPageChange(pageNumber);
    }
  };

  const handlePerPageChange = (event) => {
    const newPerPage = Number(event.target.value);
    if (!isNaN(newPerPage) && newPerPage > 0) {
      onPerPageChange(newPerPage);
    }
  };

  const handleFilterChange = (event) => {
    setFilterText(event.target.value);
  };

  const clearFilterResult = () => {
    setFilterText("");
  };

  const filteredData = data.filter((item) =>
    Object.values(item).some(
      (value) =>
        value &&
        value.toString().toLowerCase().includes(filterText.toLowerCase()),
    ),
  );

  return (
    <div className="w-100">
      <h2 className="mt-3">{title}</h2>
      <InputGroup className="mb-3" style={{ width: "300px" }}>
        <Form.Control
          type="text"
          placeholder="Filter by Text"
          value={filterText}
          onChange={handleFilterChange}
        />
        <Button className="go-button">Filter</Button>
        <Button variant="outline-secondary" onClick={clearFilterResult}>
          Clear
        </Button>
      </InputGroup>
      <DataTable
        // title={title}
        columns={wrappedColumns}
        data={filteredData}
        customStyles={customStyles}
        conditionalRowStyles={conditionalRowStyles}
        selectableRows
        onSelectedRowsChange={onSelectedRowsChange}
        selectableRowsHighlight
      />
      <Row className="mt-3 d-flex justify-content-center align-items-center">
        <Col
          xs={12}
          className="d-flex justify-content-center align-items-center flex-nowrap"
        >
          <div className="me-3" style={{color:"white"}}>
            <span>Total Items: {totalItems}</span>
          </div>
          <InputGroup className="me-3" style={{ width: "150px" }}>
            <InputGroup.Text>Per page</InputGroup.Text>
            <Form.Control
              as="select"
              value={perPage}
              onChange={handlePerPageChange}
            >
              {perPageOptions.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </Form.Control>
          </InputGroup>
          <ReactPaginate
            previousLabel={"<"}
            nextLabel={">"}
            breakLabel={"..."}
            breakClassName={"page-item"}
            breakLinkClassName={"page-link"}
            pageCount={Math.ceil(totalItems / perPage)}
            marginPagesDisplayed={2}
            pageRangeDisplayed={2}
            onPageChange={handlePageChange}
            containerClassName={"pagination"}
            pageClassName={"page-item"}
            pageLinkClassName={"page-link"}
            previousClassName={"page-item"}
            previousLinkClassName={"page-link"}
            nextClassName={"page-item"}
            nextLinkClassName={"page-link"}
            activeClassName={"active-page"}
            forcePage={page}
          />
          <InputGroup className="ms-3" style={{ width: "150px" }}>
            <InputGroup.Text>Go to</InputGroup.Text>
            <Form.Control
              type="text"
              value={goToPage}
              onChange={handleGoToPageChange}
            />
            <Button className="go-button" onClick={handleGoToPageSubmit}>
              Go
            </Button>
          </InputGroup>
        </Col>
      </Row>
    </div>
  );
};

export default MyDataTable;
