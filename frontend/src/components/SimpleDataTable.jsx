import React, { useState, useEffect, useContext } from "react";
import DataTable from "react-data-table-component";
import ReactPaginate from "react-paginate";
import { Row, Col } from "react-bootstrap";
import ThemeColorContext from "../ThemeColorProvider";

const SimpleDataTable = ({ columns = [], data = [], perPage = 10 }) => {
  const theme = useContext(ThemeColorContext);
  const [currentPage, setCurrentPage] = useState(0);
  const [pagedData, setPagedData] = useState([]);
  const [dataset, setDataset] = useState([]);

  const safePerPage = Math.max(1, perPage);
  const pageCount = Math.ceil(data.length / safePerPage);

  useEffect(() => {
    if (dataset.length === 0) {
      const indexedData = data.map((row, index) => ({
        ...row,
        tableID: row.tableID ?? index + 1,
      }));
      setDataset(indexedData);
      setCurrentPage(0);
      setPagedData([...indexedData].slice(0, safePerPage));
    } else {
      setCurrentPage(0);
      setPagedData([...dataset].slice(0, safePerPage));
    }
  }, [data, safePerPage]);

  useEffect(() => {
    const start = currentPage * safePerPage;
    const end = start + safePerPage;
    setPagedData([...dataset].slice(start, end));
  }, [dataset, currentPage, safePerPage]);

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

  const tableStyles = {
    rows: {
      style: {
        borderRadius: "2px",
        border: "none !important",
        backgroundColor: theme?.defaultColors.white,
      },
      stripedStyle: {
        border: "none !important",
        backgroundColor: theme?.grayColors.gray50,
      },
      highlightOnHoverStyle: {
        outlineStyle: "none",
        outlineWidth: "0px",
        outlineColor: "transparent",
        backgroundColor: theme?.primaryColors?.primary50,
        transition: "background-color 0.2s ease",
      },
    },
  };

  return (
    <div className="container mt-3">
      <DataTable
        columns={userColumns}
        data={pagedData}
        customStyles={tableStyles}
        sortServer={true}
        onSort={dataSortFunction}
        keyField="tableID"
        highlightOnHover
        fixedHeader
        fixedHeaderScrollHeight="85vh"
        striped
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
