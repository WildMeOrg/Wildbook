import React, { useState, useEffect, useMemo } from "react";
import DataTable from "react-data-table-component";
import ReactPaginate from "react-paginate";
import { InputGroup, Form, Button, Container, Row, Col } from "react-bootstrap";
import "bootstrap/dist/css/bootstrap.min.css";
import "../css/dataTable.css";
import { FormattedMessage } from "react-intl";
import ThemeColorContext from "../ThemeColorProvider";
import { useIntl } from "react-intl";

const customStyles = {
  rows: {
    style: {
      border: "none !important",
      borderRadius: "5px",
    },
  },
};


const conditionalRowStyles = (theme) => [
  {
    when: (row) => row.tableID % 2 === 0,
    style: {
      backgroundColor: "#ffffff", // Light gray color
      "&:hover": {
        backgroundColor: theme?.primaryColors?.primary300 || "#e0f7fa",
      },
    },
  },
  {
    when: (row) => row.tableID % 2 !== 0,
    style: {
      backgroundColor: "#f2f2f2", // White color
      "&:hover": {
        backgroundColor: theme?.primaryColors?.primary300 || "#e0f7fa",
      },
    },
  },

];


const MyDataTable = ({
  title = "",
  columnNames = [],
  totalItems = 0,
  tableData = [],
  searchText = "",
  page,
  perPage,
  onPageChange,
  onPerPageChange,
  setSort,
  style = {},
  tabs = [],
  isLoading = false,
  onSelectedRowsChange = () => { },
  onRowClicked = () => { },
}) => {
  const [data, setData] = useState([]);
  const [filterText, setFilterText] = useState("");
  const [goToPage, setGoToPage] = useState("");
  const perPageOptions = [10, 20, 30, 40, 50];
  const intl = useIntl();

  const wrappedColumns = useMemo(
    () =>
      columnNames.map((col) => {
        const sortFunction = (rowA, rowB) => {
          const a = rowA[col.selector] || '';
          const b = rowB[col.selector] || '';

          const isANumber = !isNaN(a);
          const isBNumber = !isNaN(b);

          if (isANumber && isBNumber) {
            return Number(a) - Number(b);
          } else if (!isANumber && !isBNumber) {
            return a.localeCompare(b);
          } else if (isANumber) {
            return -1;
          } else {
            return 1;
          }
        };
        if (col.selector === 'occurrenceId') {
          return {
            id: col.selector,
            name: <FormattedMessage id={col.name} />,
            cell: (row) => <a
              target="_blank"
              style={{ color: 'inherit'}}
              href={`/occurrence.jsp?number=${row[col.selector]}`}>{row[col.selector] || "-"}</a>,
            selector: (row) => row[col.selector] || "-",
            sortable: true,
            sortFunction: sortFunction,
          };
        } else if (col.selector === 'individualDisplayName') {
          return {
            id: col.selector,
            name: <FormattedMessage id={col.name} />,
            cell: (row) => <a
              target="_blank"
              style={{ color: 'inherit'}}
              href={row[col.selector] ? `/individuals.jsp?id=${row["individualId"]}` : null}>{row[col.selector] || "unassigned"}</a>,
            selector: (row) => row[col.selector] || "-",
            sortable: true,
            sortFunction: sortFunction,
          };
        } else if (col.selector === 'numberAnnotations') {
          return {
            id: col.selector,
            name: <FormattedMessage id={col.name} />,

            selector: (row) => row[col.selector] || 0,
            sortable: true,
            sortFunction: sortFunction,
          };
        } else if (col.selector === 'date' || col.selector === 'dateSubmitted') {
          return {
            id: col.selector,
            name: <FormattedMessage id={col.name} />,
            selector: (row) => {
              const dateStr = row[col.selector];
              if (dateStr) {
                const date = new Date(dateStr);
                console.log(date.toISOString().split('T')[0]);
                return date.toISOString().split('T')[0];
              }
              return "-";
            },
            sortable: true,
            sortFunction: sortFunction,
          };
        }

        else {
          return ({
            id: col.selector,
            name: <FormattedMessage id={col.name} />,
            selector: (row) => row[col.selector] || "-", // Accessor function for the column data
            sortable: true, // Make the column sortable
            sortFunction: sortFunction,
            conditionalCellStyles: [
              {
                when: () => true,
                style: {
                  whiteSpace: 'wrap',
                  wordWrap: 'break-word',
                  wordBreak: 'break-all',
                },
              },
            ],
          });

        }

      }),
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

  const handleSort = (column, sortDirection, sortFunction) => {

    const columnName = column?.id === "locationId" ? "locationName" : column?.id;

    setSort({ sortname: columnName, sortorder: sortDirection });
  };

  const filteredData = data.filter((item) =>
    Object.values(item).some(
      (value) =>
        value &&
        value.toString().toLowerCase().includes(filterText.toLowerCase()),
    ),
  );

  const theme = React.useContext(ThemeColorContext);

  return (
    <div className="container" style={{
      ...style,
    }}>
      <h2 className="mt-3" style={{ color: "white" }}>{title}</h2>
      <div className="d-flex flex-row justify-content-between">
        <div className="tabs">
          <Button
            key={"result"}
            variant="outline-tertiary"
            className="me-1"
            style={{
              backgroundColor: "rgba(255,255,255,0.8)",
              color: theme.primaryColors.primary700,
              fontWeight: "bold",
              fontSize: "1em",
              paddingLeft: "5px", paddingRight: "5px"
            }}

          >    <FormattedMessage id="RESULTS_TABLE" defaultMessage={"Results Table"} />
          </Button>
          {tabs.map((tab, index) => {
            return (
              <Button
                key={index}
                variant="outline-tertiary"
                className="me-1 mt-1"
                style={{ backgroundColor: "rgba(255,255,255,0.3)", paddingLeft: "5px", paddingRight: "5px" }}

              >
                <a
                  key={index}
                  href={tab.split(":")[1]}
                  style={{ color: "white", textDecoration: "none", fontWeight: "bold" }}
                >
                  {<FormattedMessage id={tab.split(":")[0]} defaultMessage={tab.split(":")[0]} />}
                </a>
              </Button>
            );
          })}
        </div>
        <InputGroup className="mb-3 d-flex search-bar"
          style={{
            minWidth: "120px",
            height: "30px",
            whiteSpace: "nowrap",
            // maxWidth: "20%",
          }}>
          <Form.Control
            type="text"
            className="custom-placeholder"
            style={{
              backgroundColor: "transparent",
              color: "white",
              border: '1px solid white',
              borderRight: 'none',
              flex: "1 1 auto",
              borderRadius: "50px 0 0 50px",
            }}
            placeholder={searchText || intl.formatMessage({ id: "SEARCH" })}
            value={filterText}
            onChange={handleFilterChange}
          />
          {
            filterText.length == 0 ? <Button
              style={{
                backgroundColor: "transparent",
                color: 'white',
                border: '1px solid white',
                borderLeft: 'none',
                borderRadius: "0 50px 50px 0",
              }}
            >
              <i class="bi bi-search"></i>
            </Button> : <Button
              style={{
                backgroundColor: "transparent",
                color: 'white',
                border: '1px solid white',
                borderLeft: 'none',
                borderRadius: "0 50px 50px 0",
              }}
              onClick={clearFilterResult}
            >
              <i class="bi bi-x-lg"></i>
            </Button>
          }
        </InputGroup>
      </div>
      <div
        style={{
          borderRadius: '5px',
          overflow: 'hidden',
        }}
      >
        <DataTable
          // title={title}
          columns={wrappedColumns}
          data={filteredData}
          customStyles={customStyles}
          conditionalRowStyles={conditionalRowStyles(theme)}
          // selectableRows
          onSelectedRowsChange={onSelectedRowsChange}
          pointerOnHover
          highlightOnHover
          onRowClicked={onRowClicked}
          selectableRowsHighlight
          progressPending={isLoading}
          onSort={handleSort}
        />
      </div>
      {
        filteredData.length == 0 && !isLoading ? <div className="d-flex justify-content-center align-items-center" style={{ color: "white" }}>
          <FormattedMessage id="NO_RESULTS_FOUND" defaultMessage={"No results found"} />
        </div> : <Row className="mt-3 d-flex justify-content-center align-items-center">
          <Col
            xs={12}
            className="d-flex justify-content-center align-items-center flex-nowrap"
          >
            <div className="me-3" style={{ color: "white" }}>
              <span><FormattedMessage id="TOTAL_ITEMS" defaultMessage={"Total Items"} />: {totalItems}</span>
            </div>
            <InputGroup className="me-3" style={{ width: "150px" }}>
              <InputGroup.Text><FormattedMessage id="PER_PAGE" defaultMessage={"Per page"} /></InputGroup.Text>
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
            <InputGroup className="ms-3" style={{ width: "180px", whiteSpace: "nowrap" }}>
              <InputGroup.Text><FormattedMessage id="GO_TO" defaultMessage={"Go to"} /></InputGroup.Text>
              <Form.Control
                type="text"
                value={goToPage}
                onChange={handleGoToPageChange}
              />
              <Button className="go-button" onClick={handleGoToPageSubmit}>
                <FormattedMessage id="GO" defaultMessage={"Go"} />
              </Button>
            </InputGroup>
          </Col>
        </Row>
      }

    </div>
  );
};

export default MyDataTable;
