import React, { useState, useEffect, useContext } from "react";
import DataTable from "react-data-table-component";
import ReactPaginate from "react-paginate";
import { Row, Col } from "react-bootstrap";
import ThemeColorContext from "../ThemeColorProvider";

const sortRows = (rows, sort) => {
  if (!sort) return rows;
  const { selector, direction } = sort;
  return [...rows].sort((rowA, rowB) => {
    let comparison = 0;

    if (selector(rowA) > selector(rowB)) {
      comparison = 1;
    } else if (selector(rowA) < selector(rowB)) {
      comparison = -1;
    }

    return direction === "desc" ? comparison * -1 : comparison;
  });
};

const SimpleDataTable = ({ columns = [], data = [], perPage = 10 }) => {
  const theme = useContext(ThemeColorContext);
  const [currentPage, setCurrentPage] = useState(0);
  const [pagedData, setPagedData] = useState([]);
  const [dataset, setDataset] = useState([]);
  // the user's column sort ({ selector, direction }), reapplied to each new
  // `data` snapshot so a poll or a search keystroke doesn't silently undo it
  // while the header still shows the sort arrow
  const [sort, setSort] = useState(null);

  const safePerPage = Math.max(1, perPage);
  const pageCount = Math.ceil(data.length / safePerPage);

  useEffect(() => {
    // Rebuild dataset from the incoming `data` prop so updates
    // (e.g. polling progress) are reflected, keeping any active user sort.
    //
    // Preserve the user's current page across refreshes instead of
    // resetting to the first page. A bulk-import task polls every few
    // seconds while detection/identification run (and for a short
    // window after), handing this component a fresh `data` array on
    // every poll. Resetting here knocked a reviewer back to page 1 on
    // every poll while they were reviewing matches (issue #1637).
    // Clamp to the last available page so a shrinking row count never
    // strands us on an out-of-range page; the second effect recomputes
    // the visible rows from the clamped page.
    const indexedData = data.map((row, index) => ({
      ...row,
      tableID: row.tableID ?? index + 1,
    }));
    setDataset(sortRows(indexedData, sort));
    const lastPage = Math.max(
      0,
      Math.ceil(indexedData.length / safePerPage) - 1,
    );
    setCurrentPage((prev) => Math.min(prev, lastPage));
  }, [data, safePerPage, sort]);

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
    setSort({ selector: column.selector, direction: sortDirection });
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
        noDataComponent={
          <div className="p-3 text-center text-muted">
            There are no records to display yet. Page will refresh.
          </div>
        }
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
