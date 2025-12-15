import React, { useState, useEffect, useContext } from "react";
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
  const [dataset, setDataset] = useState([]);

  const pageCount = Math.ceil(data.length / perPage);

  useEffect(() => {
    if (dataset.length === 0) {
      const indexedData = data.map((row, index) => ({
        ...row,
        tableID: row.tableID ?? index + 1,
      }));
      setDataset(indexedData);
      setCurrentPage(0);
    }
    setPagedData([...dataset].slice(0, perPage));
  }, [data]);

  useEffect(() => {
    const start = currentPage * perPage;
    const end = start + perPage;
    setPagedData([...dataset].slice(start, end));
  }, [dataset, currentPage]);

  const userColumns = columns.map((col) => ({
    id: col.selector,
    name: col.name,
    selector: col.selector,
    sortable: col.sortable ?? true,
    cell: col.cell || ((row) => row[col.selector] || "-"),
  }));

  const dataSortFunction = (column, sortDirection) => {
    let sortedData = [...dataset].sort((rowA, rowB) => {
      let comparison = 0;

      if (column.selector(rowA) > column.selector(rowB)) {
        comparison = 1;
      } else if (column.selector(rowA) < column.selector(rowB)) {
        comparison = -1;
      }

      return sortDirection === "desc" ? comparison * -1 : comparison;
    });
    setDataset(sortedData);
  };

  const conditionalRowStyles = [
    {
      when: (row) => (dataset.indexOf(row) % perPage) % 2 === 0,
      style: {
        backgroundColor: "#ffffff",
        "&:hover": {
          backgroundColor: theme?.primaryColors?.primary50,
        },
      },
    },
    {
      when: (row) => (dataset.indexOf(row) % perPage) % 2 !== 0,
      style: {
        backgroundColor: "#f2f2f2",
        "&:hover": {
          backgroundColor: theme?.primaryColors?.primary50,
        },
      },
    },
  ];

  return (
    <div className="container mt-3">
      <DataTable
        columns={userColumns}
        data={pagedData}
        customStyles={customStyles}
        conditionalRowStyles={conditionalRowStyles}
        onSort={dataSortFunction}
        keyField="tableID"
        highlightOnHover
        fixedHeader
        fixedHeaderScrollHeight="85vh"
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
