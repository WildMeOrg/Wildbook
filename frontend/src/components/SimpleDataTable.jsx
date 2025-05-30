import React, { useState, useEffect, useMemo, useContext } from "react";
import DataTable from "react-data-table-component";
import ReactPaginate from "react-paginate";
import { Row, Col } from "react-bootstrap";
import ThemeColorContext from "../ThemeColorProvider";

const customStyles = {
  rows: {
    style: {
      border: "none !important",
      borderRadius: "5px",
    },
  },
};

const SimpleDataTable = ({ columns = [], data = [], perPage = 10 }) => {
  const theme = useContext(ThemeColorContext);
  const [currentPage, setCurrentPage] = useState(0);
  const [pagedData, setPagedData] = useState([]);

  const pageCount = Math.ceil(data.length / perPage);

  useEffect(() => {
    const start = currentPage * perPage;
    const end = start + perPage;
    setPagedData(data.slice(start, end));
  }, [data, currentPage, perPage]);

const wrappedColumns = useMemo(() =>
  columns.map(col => ({
    id: col.selector,
    name: col.name,
    selector: col.selector, 
    sortable: col.sortable ?? true,
    cell: col.cell || (row => row[col.selector] || "-"),
  })), [columns]);


  const conditionalRowStyles = [
    {
      when: row => row.tableID % 2 === 0,
      style: {
        backgroundColor: "#ffffff",
        "&:hover": {
          backgroundColor: theme?.primaryColors?.primary300 || "#e0f7fa",
        },
      },
    },
    {
      when: row => row.tableID % 2 !== 0,
      style: {
        backgroundColor: "#f2f2f2",
        "&:hover": {
          backgroundColor: theme?.primaryColors?.primary300 || "#e0f7fa",
        },
      },
    },
  ];

  const dataWithIndex = pagedData.map((row, index) => ({
    ...row,
    tableID: currentPage * perPage + index,
  }));

  return (
    <div className="container mt-3">
      <DataTable
        columns={wrappedColumns}
        data={dataWithIndex}
        customStyles={customStyles}
        conditionalRowStyles={conditionalRowStyles}
        highlightOnHover
        pointerOnHover
      />
      <Row className="mt-3 d-flex justify-content-center">
        <Col xs="auto">
          <ReactPaginate
            previousLabel={"<"}
            nextLabel={">"}
            pageCount={pageCount}
            onPageChange={({ selected }) => setCurrentPage(selected)}
            containerClassName={"pagination"}
            pageClassName={"page-item"}
            pageLinkClassName={"page-link"}
            previousClassName={"page-item"}
            previousLinkClassName={"page-link"}
            nextClassName={"page-item"}
            nextLinkClassName={"page-link"}
            activeClassName={"active-page"}
            forcePage={currentPage}
          />
        </Col>
      </Row>
    </div>
  );
};

export default SimpleDataTable;
