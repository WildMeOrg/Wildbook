import React, { useState, useEffect, useMemo } from "react";
import DataTable from "react-data-table-component";
import ReactPaginate from "react-paginate";
import { InputGroup, Form, Button, Row, Col } from "react-bootstrap";
import "bootstrap/dist/css/bootstrap.min.css";
import "../css/dataTable.css";
import { FormattedMessage } from "react-intl";
import ThemeColorContext from "../ThemeColorProvider";
import { useIntl } from "react-intl";
import Calendar from "../pages/SearchPages/searchResultTabs/CalendarView";
import { observer } from "mobx-react-lite";
import GalleryView from "../pages/SearchPages/searchResultTabs/GalleryView";
import Select from "react-select";
import MainButton from "./MainButton";
import useGetSiteSettings from "../models/useGetSiteSettings";

const customStyles = {
  rows: {
    style: {
      border: "none !important",
      borderRadius: "5px",
      fontSize: "1rem",
    },
  },
};

const MyDataTable = observer(
  ({
    store,
    searchQueryId,
    refetchMediaAssets = () => {},
    pg = () => {},
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
    isLoading = false,
    extraStyles = [],
    onSelectedRowsChange = () => {},
    onRowClicked = () => {},
    setExportModalOpen = () => {},
  }) => {
    const [data, setData] = useState([]);
    const [filterText, setFilterText] = useState("");
    const [goToPage, setGoToPage] = useState("");
    const perPageOptions = [10, 20, 30, 40, 50];
    const intl = useIntl();

    const conditionalRowStyles = (theme) =>
      [
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
      ].concat(extraStyles);

    const wrappedColumns = useMemo(
      () =>
        columnNames.map((col) => {
          const sortFunction = (rowA, rowB) => {
            const a = rowA[col.selector] || "";
            const b = rowB[col.selector] || "";

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
          if (col.selector === "occurrenceId") {
            return {
              id: col.selector,
              name: <FormattedMessage id={col.name} />,
              cell: (row) => (
                <a
                  target="_blank"
                  style={{ color: "inherit" }}
                  href={
                    row[col.selector]
                      ? `/occurrence.jsp?number=${row[col.selector]}`
                      : null
                  }
                  rel="noreferrer"
                >
                  {row[col.selector] || "-"}
                </a>
              ),
              selector: (row) => row[col.selector] || "-",
              sortable: true,
              sortFunction: sortFunction,
            };
          } else if (col.selector === "individualDisplayName") {
            return {
              id: col.selector,
              name: <FormattedMessage id={col.name} />,
              cell: (row) => (
                <a
                  target="_blank"
                  style={{ color: "inherit" }}
                  href={
                    row[col.selector]
                      ? `/individuals.jsp?id=${row["individualId"]}`
                      : null
                  }
                  rel="noreferrer"
                >
                  {row[col.selector] || "unassigned"}
                </a>
              ),
              selector: (row) => row[col.selector] || "-",
              sortable: true,
              sortFunction: sortFunction,
            };
          } else if (col.selector === "numberAnnotations") {
            return {
              id: col.selector,
              name: <FormattedMessage id={col.name} />,

              selector: (row) => row[col.selector] || 0,
              sortable: true,
              sortFunction: sortFunction,
            };
          } else if (
            col.selector === "date" ||
            col.selector === "dateSubmitted"
          ) {
            return {
              id: col.selector,
              name: <FormattedMessage id={col.name} />,
              selector: (row) => {
                const dateStr = row[col.selector];
                if (dateStr) {
                  const date = new Date(dateStr);
                  return date.toISOString().split("T")[0];
                }
                return "-";
              },
              sortable: true,
              sortFunction: sortFunction,
            };
          } else {
            return {
              id: col.selector,
              name: <FormattedMessage id={col.name} />,
              selector: (row) => row[col.selector] || "-",
              sortable: true,
              sortFunction: sortFunction,
              conditionalCellStyles: [
                {
                  when: () => true,
                  style: {
                    whiteSpace: "wrap",
                    wordWrap: "break-word",
                    wordBreak: "break-all",
                  },
                },
              ],
            };
          }
        }),
      [columnNames],
    );

    const params = new URLSearchParams(window.location.search);
    const individualIDExact = params.get("individualIDExact");
    const calendar = params.get("calendar");
    useEffect(() => {
      if (individualIDExact) {
        const timer = setTimeout(() => {
          store.setActiveStep(1);
        }, 1000);
        return () => clearTimeout(timer);
      }
    }, [individualIDExact]);
    useEffect(() => {
      if (calendar) {
        const timer = setTimeout(() => {
          store.setActiveStep(4);
        }, 1000);
        return () => clearTimeout(timer);
      }
    }, [calendar]);

    useEffect(() => {
      if (
        store.projectBannerStatusCode === 0 ||
        store.projectBannerStatusCode === 1
      ) {
        const target = store.selectedRows.length > 0 ? 1 : 0;
        if (store.projectBannerStatusCode !== target) {
          store.setprojectBannerStatusCode(target);
        }
      }
    }, [store.selectedRows.length, store.projectBannerStatusCode]);

    const { data: siteSettingsData, loading: siteSettingsLoading } =
      useGetSiteSettings();
    useEffect(() => {
      if (siteSettingsData) {
        store.setSiteSettingsData(siteSettingsData);
        store.setSiteSettingsLoading(siteSettingsLoading);
      }
    }, [siteSettingsData, siteSettingsLoading]);

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

    const projectOptions = Object.entries(
      store?.siteSettingsData?.projectsForUser ?? {},
    ).map(([value, label]) => ({ value, label }));

    const handleSort = (column, sortDirection) => {
      const columnName =
        column?.id === "locationId" ? "locationName" : column?.id;

      setSort({ sortname: columnName, sortorder: sortDirection });
    };

    const filteredData = React.useMemo(() => {
      if (!filterText) return data;
      const needle = filterText.toLowerCase();
      return data.filter((item) =>
        Object.values(item).some(
          (v) => v != null && String(v).toLowerCase().includes(needle),
        ),
      );
    }, [data, filterText]);

    const theme = React.useContext(ThemeColorContext);

    const activeStyle = {
      fontWeight: "bold",
      fontSize: "1em",
      paddingLeft: "5px",
      paddingRight: "5px",
      backgroundColor: "rgba(255, 255, 255, 0.8)",
      color: theme.primaryColors.primary700,
    };

    const inactiveStyle = {
      fontWeight: "bold",
      fontSize: "1em",
      paddingLeft: "5px",
      paddingRight: "5px",
      backgroundColor: "rgba(255, 255, 255, 0.3)",
      color: "white",
    };

    return (
      <div
        className="container mt-3 mb-5"
        style={{
          ...style,
        }}
      >
        <h2 className="mt-3" style={{ color: "white" }}>
          {title}
        </h2>
        <div className="d-flex flex-row justify-content-between">
          <div className="tabs">
            <Button
              key={"result"}
              variant="outline-tertiary"
              className="me-1"
              onClick={() => {
                store.setActiveStep(0);
              }}
              style={{
                ...(store.activeStep === 0 ? activeStyle : inactiveStyle),
              }}
            >
              <FormattedMessage id="RESULTS_TABLE" />
            </Button>
            <Button
              key={"gallery"}
              variant="outline-tertiary"
              className="me-1"
              onClick={async () => {
                store.setActiveStep(1);
              }}
              style={{
                ...(store.activeStep === 1 ? activeStyle : inactiveStyle),
              }}
            >
              <FormattedMessage id="GALLERY_VIEW" />
            </Button>
            <Button
              key={"map"}
              variant="outline-tertiary"
              className="me-1"
              onClick={() => {
                const url = `/encounters/mappedSearchResults.jsp?searchQueryId=${searchQueryId}&regularQuery=true`;
                window.open(url, "_blank");
              }}
              style={{
                ...(store.activeStep === 2 ? activeStyle : inactiveStyle),
              }}
            >
              <FormattedMessage id="MAP_VIEW" defaultMessage={"Map View"} />
            </Button>
            <Button
              key={"chart"}
              variant="outline-tertiary"
              className="me-1"
              onClick={() => {
                const url = `/encounters/searchResultsAnalysis.jsp?searchQueryId=${searchQueryId}&regularQuery=true`;
                window.open(url, "_blank");
              }}
              style={{
                ...(store.activeStep === 3 ? activeStyle : inactiveStyle),
              }}
            >
              <FormattedMessage id="CHART_VIEW" />
            </Button>
            <Button
              key={"calendar"}
              variant="outline-tertiary"
              className="me-1"
              onClick={() => {
                store.setActiveStep(4);
              }}
              style={{
                ...(store.activeStep === 4 ? activeStyle : inactiveStyle),
              }}
            >
              <FormattedMessage id="CALENDAR_VIEW" />
            </Button>
          </div>
          <InputGroup
            className="mb-3 d-flex search-bar"
            style={{
              minWidth: "220px",
              height: "30px",
              whiteSpace: "nowrap",
            }}
          >
            <Form.Control
              type="text"
              className="custom-placeholder"
              style={{
                backgroundColor: "transparent",
                color: "white",
                border: "1px solid white",
                borderRight: "none",
                flex: "1 1 auto",
                borderRadius: "50px 0 0 50px",
              }}
              placeholder={searchText || intl.formatMessage({ id: "SEARCH" })}
              value={filterText}
              onChange={handleFilterChange}
            />
            {filterText.length === 0 ? (
              <Button
                style={{
                  backgroundColor: "transparent",
                  color: "white",
                  border: "1px solid white",
                  borderLeft: "none",
                  borderRadius: "0 50px 50px 0",
                }}
              >
                <i className="bi bi-search"></i>
              </Button>
            ) : (
              <Button
                style={{
                  backgroundColor: "transparent",
                  color: "white",
                  border: "1px solid white",
                  borderLeft: "none",
                  borderRadius: "0 50px 50px 0",
                }}
                onClick={clearFilterResult}
              >
                <i className="bi bi-x-lg"></i>
              </Button>
            )}
            <button
              className="btn btn-outline-secondary ms-2 me-2"
              style={{
                backgroundColor: "transparent",
                color: "white",
                border: "1px solid white",
                borderRadius: "5px",
              }}
              onClick={() => {
                setExportModalOpen(true);
              }}
            >
              <FormattedMessage id="EXPORT" />
            </button>
          </InputGroup>
          <br />
        </div>
        {store.activeStep === 0 && (
          <div className="d-flex flex-row align-items-center">
            {store.projectBannerStatusCode === 0 && (
              <div
                className="d-flex flex-row align-items-center gap-2"
                style={{ color: "white", height: "50px" }}
              >
                <FormattedMessage id="ADD_ENCOUNTER_TO_PROJECT_DESC" />
              </div>
            )}
            {store.projectBannerStatusCode === 1 && (
              <div
                className="d-flex flex-row align-items-center gap-2"
                style={{ color: "white", height: "50px" }}
              >
                <div>
                  <FormattedMessage id="ADD_TO_PROJECT" />
                </div>
                <div
                  style={{
                    minWidth: "350px",
                    flex: "1 1 auto",
                  }}
                >
                  <Select
                    isMulti={true}
                    options={projectOptions}
                    className="basic-multi-select"
                    classNamePrefix="select"
                    menuPlacement="auto"
                    menuPortalTarget={document.body}
                    styles={{
                      menuPortal: (base) => ({ ...base, zIndex: 9999 }),
                    }}
                    value={projectOptions.filter((option) =>
                      store.selectedProjects.includes(option.value),
                    )}
                    getOptionLabel={(option) => option.label}
                    placeholder={intl.formatMessage({ id: "SELECT_PROJECTS" })}
                    onChange={(selected) =>
                      store.setSelectedProjects(
                        (selected || []).map((opt) => opt.value),
                      )
                    }
                    closeMenuOnSelect={false}
                  />
                </div>
                <MainButton
                  color="white"
                  noArrow
                  backgroundColor={theme?.wildMeColors?.cyan700}
                  borderColor="#007bff"
                  disabled={
                    !store.selectedProjects ||
                    store.selectedProjects.length === 0
                  }
                  onClick={() => {
                    store.addEncountersToProject();
                  }}
                >
                  <FormattedMessage id="ADD" />
                </MainButton>
              </div>
            )}
            {store.projectBannerStatusCode === 2 && (
              <div
                className="d-flex align-items-center"
                style={{
                  backgroundColor: theme?.primaryColors?.primary100,
                  borderRadius: "5px",
                  padding: "5px",
                  color: theme?.wildMeColors?.green700,
                  height: "50px",
                }}
              >
                <i className="bi bi-info-circle"></i>
                <FormattedMessage id="ADDING_TO_PROJECT" />
                <i
                  className="bi bi-arrow-repeat ms-2"
                  style={{
                    fontSize: "1.5em",
                    color: theme?.wildMeColors?.cyan700,
                  }}
                ></i>
              </div>
            )}
            {store.projectBannerStatusCode === 3 && (
              <div
                className="d-flex align-items-center"
                style={{
                  backgroundColor: theme?.primaryColors?.primary100,
                  borderRadius: "5px",
                  padding: "5px",
                  color: theme?.wildMeColors?.green700,
                  height: "50px",
                }}
              >
                <i className="bi bi-info-circle"></i>
                <FormattedMessage id="ADDED_TO_PROJECT" />
                <i
                  className="bi bi-check-circle ms-2"
                  style={{
                    fontSize: "1.5em",
                    color: theme?.wildMeColors?.green700,
                  }}
                ></i>
              </div>
            )}
            {store.projectBannerStatusCode === 4 && (
              <div
                className="d-flex align-items-center"
                style={{
                  backgroundColor: theme?.statusColors?.red100,
                  borderRadius: "5px",
                  padding: "5px",
                  color: theme?.wildMeColors?.green700,
                  height: "50px",
                }}
              >
                <i className="bi bi-info-circle"></i>
                <FormattedMessage id="FAILED_TO_ADD_TO_PROJECT" />
                <i
                  className="bi bi-x-circle ms-2"
                  style={{
                    fontSize: "1.5em",
                    color: theme?.wildMeColors?.red700,
                  }}
                ></i>
              </div>
            )}
          </div>
        )}
        <div
          className="w-100"
          style={{
            display: store.activeStep === 0 ? "block" : "none",
          }}
        >
          <div
            style={{
              borderRadius: "5px",
              overflow: "hidden",
            }}
          >
            <DataTable
              columns={wrappedColumns}
              data={filteredData}
              customStyles={customStyles}
              conditionalRowStyles={conditionalRowStyles(theme)}
              onSelectedRowsChange={onSelectedRowsChange}
              pointerOnHover
              highlightOnHover
              onRowClicked={onRowClicked}
              selectableRowsHighlight
              progressPending={isLoading}
              onSort={handleSort}
              selectableRows
              clearSelectedRows={store.clearSelectedRows}
            />
          </div>
          {filteredData.length === 0 && !isLoading ? (
            <div
              className="d-flex justify-content-center align-items-center"
              style={{ color: "white" }}
            >
              <FormattedMessage
                id="NO_RESULTS_FOUND"
                defaultMessage={"No results found"}
              />
            </div>
          ) : (
            <Row className="mt-3 d-flex justify-content-center align-items-center">
              <Col
                xs={12}
                className="d-flex justify-content-center align-items-center flex-nowrap"
              >
                <div className="me-3" style={{ color: "white" }}>
                  <span>
                    <FormattedMessage
                      id="TOTAL_ITEMS"
                      defaultMessage={"Total Items"}
                    />
                    : {totalItems}
                  </span>
                </div>
                <InputGroup className="me-3" style={{ width: "150px" }}>
                  <InputGroup.Text>
                    <FormattedMessage
                      id="PER_PAGE"
                      defaultMessage={"Per page"}
                    />
                  </InputGroup.Text>
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
                <InputGroup
                  className="ms-3"
                  style={{ width: "180px", whiteSpace: "nowrap" }}
                >
                  <InputGroup.Text>
                    <FormattedMessage id="GO_TO" defaultMessage={"Go to"} />
                  </InputGroup.Text>
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
          )}
        </div>
        {store.activeStep === 1 && (
          <div
            className="w-100"
            style={{
              display: "block",
            }}
          >
            <GalleryView
              store={store}
              refetchMediaAssets={refetchMediaAssets}
              pg={pg}
            />
          </div>
        )}
        {store.activeStep === 4 && (
          <div className="w-100">
            <Calendar store={store} />
          </div>
        )}
      </div>
    );
  },
);

export default MyDataTable;
