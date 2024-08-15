import React, { useEffect, useState } from "react";
import DataTable from "../components/DataTable";
import useFilterEncounters from "../models/encounters/useFilterEncounters";
import FilterPanel from "../components/FilterPanel";
import useEncounterSearchSchemas from "../models/encounters/useEncounterSearchSchemas";
import SideBar from "../components/filterFields/SideBar";
import { FormattedMessage } from "react-intl";
import { useSearchParams } from "react-router-dom";
import { useIntl } from "react-intl";
import axios from "axios";
import { get } from "lodash";

const columns = [
  { name: "ID", selector: "individualDisplayName" },
  // { name: "Encounter ID", selector: "id" },
  { name: "SIGHTING_ID", selector: "occurrenceId" },
  { name: "ALTERNATIVE_ID", selector: "otherCatalogNumbers" },
  { name: "CREATED_DATE", selector: "date" },
  { name: "LOCATION_ID", selector: "locationId" },
  { name: "SPECIES", selector: "taxonomy" },
  { name: "SUBMITTER", selector: "assignedUsername" },
  { name: "DATE_SUBMITTED", selector: "dateSubmitted" },
  { name: "NUMBER_ANNOTATIONS", selector: "numberAnnotations" },
];


export default function EncounterSearch() {

  const intl = useIntl();
  const schemas = useEncounterSearchSchemas();
  const [page, setPage] = useState(0);
  const [perPage, setPerPage] = useState(20);
  const [searchParams, setSearchParams] = useSearchParams();
  const [paramsFormFilters, setParamsFormFilters] = useState([]);
  const paramsObject = Object.fromEntries(searchParams.entries()) || {};
  const [formFilters, setFormFilters] = useState([]);
  // const [resultPage, setResultPage] = useState(false);
  // const [refresh, setRefresh] = useState(false);  
  const regularQuery = searchParams.get("regularQuery");

  const [queryID, setQueryID] = useState(regularQuery ? null : searchParams.get("searchQueryId"));
  console.log("queryID", queryID);
  const [searchData, setSearchData] = useState([]);
  const [filterPanel, setFilterPanel] = useState(queryID ? false : true);
  const [totalItems, setTotalItems] = useState(0);
  const [searchIdResultPage, setSearchIdResultPage] = useState(0);
  const [searchIdResultPerPage, setSearchIdResultPerPage1] = useState(20);
  const [sort, setSort] = useState({ sortname: "date", sortorder: "desc" });

  const { sortname, sortorder } = sort;

  const [encounterSortName, setEncounterSortName] = useState("date");
  const [encounterSortOrder, setEncounterSortOrder] = useState("desc");
  const [searchIdSortName, setSearchIdSortName] = useState("date");
  const [searchIdSortOrder, setSearchIdSortOrder] = useState("desc");

  useEffect(() => {
    if (!queryID) {
      setEncounterSortName(sortname);
      setEncounterSortOrder(sortorder);
    } else {
      setSearchIdSortName(sortname);
      setSearchIdSortOrder(sortorder);
    }
  }, [queryID, sortname, sortorder]);

  const tabs = [
    `ENCOUNTER_PROJECT_MANAGEMENT:/encounters/projectManagement.jsp`,
    `ENCOUNTER_MATCHING_IMAGES_VIDEOS:/encounters/thumbnailSearchResults.jsp`,
    `ENCOUNTER_MAPPED_RESULTS:/encounters/mappedSearchResults.jsp`,
    `ENCOUNTER_RESULTS_CALENDAR:/xcalendar/calendar.jsp`,
    `ENCOUNTER_ANALYSIS:/encounters/searchResultsAnalysis.jsp`,
    `ENCOUNTER_EXPORT:/encounters/exportSearchResults.jsp`,
  ];

  useEffect(() => {
    setFormFilters(Array.from(
      new Map([...paramsFormFilters, ...formFilters].map(filter => [filter.filterId, filter])).values()
    ));
  }, [paramsFormFilters]);

  useEffect(() => {
    if (regularQuery) {
      setQueryID("");
    } else {
      setQueryID(searchParams.get("searchQueryId"));
    }
  }, [searchParams]);

  const { data: encounterData, loading, } = useFilterEncounters({
    queries: formFilters,
    params: {
      sort: encounterSortName,
      sortOrder: encounterSortOrder,
      size: perPage,
      from: page * perPage,
    },
  });

  const encounters = queryID ? searchData || [] : encounterData?.results || [];

  const sortedEncounters = encounters.sort((a, b) => {
    if (!a[sortname] || !b[sortname]) return 0;

    const valueA = a[sortname];
    const valueB = b[sortname];

    if (sortorder === 'asc') {
      return valueA > valueB ? 1 : valueA < valueB ? -1 : 0;
    } else if (sortorder === 'desc') {
      return valueA < valueB ? 1 : valueA > valueB ? -1 : 0;
    } else {
      return 0; // Default to no sorting if sortorder is invalid
    }
  });

  const totalEncounters = encounterData?.resultCount || 0;
  const searchQueryId = encounterData?.searchQueryId || "";


  const updatedTabs = tabs.map(tab => {
    const [name, url] = tab.split(":");
    const updatedUrl = queryID ? `${url}?searchQueryId=${queryID}` : searchQueryId ? `${url}?searchQueryId=${searchQueryId}&regularQuery=true` : url;
    return `${name}:${updatedUrl}`;
  });

  useEffect(() => {
    if (queryID) {
      axios.get(`/api/v3/search/${queryID}?from=${searchIdResultPage * searchIdResultPerPage}&size=${searchIdResultPerPage}&sort=${searchIdSortName}&sortOrder=${searchIdSortOrder}`)
        .then(response => {
          setSearchData(response?.data?.hits || []);
          setTotalItems(parseInt(get(response, ["headers", "x-wildbook-total-hits"], "0"), 10));
          setFilterPanel(false);
          // setResultPage(true); 
        })
        .catch(error => {
          console.error("Error fetching search data:", error);
        });
    }
  }, [queryID, searchIdResultPage, searchIdResultPerPage, searchIdSortName, searchIdSortOrder]);

  useEffect(() => {
    const handlePopState = () => {
      setFilterPanel(prev => !prev);
    };
    window.addEventListener('popstate', handlePopState);
    return () => {
      window.removeEventListener('popstate', handlePopState);
    };
  }, []);

  const handleSearch = () => {
    setSearchParams(PrevSearchParams => {
      const newSearchParams = new URLSearchParams(PrevSearchParams);
      newSearchParams.set("results", "true");
      return newSearchParams;
    }
    );
  };

  if (paramsObject.username && paramsFormFilters.find(opt => opt.filterId === "assignedUsername") === undefined) {
    setFilterPanel(false);
    setParamsFormFilters((prevFilters) => {
      return [
        ...prevFilters,
        {
          clause: "filter",
          filterId: "assignedUsername",
          filterKey: "Assigned User",
          query: {
            "term": {
              "assignedUsername": paramsObject.username
            }
          }
        }
      ];
    });
  }

  if (paramsObject.state && paramsFormFilters.find(opt => opt.filterId === "state") === undefined) {
    setParamsFormFilters((prevFilters) => {
      return [
        ...prevFilters,
        {
          clause: "filter",
          filterId: "state",
          filterKey: "Encounter Status",
          query: {
            "term": {
              "state": paramsObject.state
            }
          }
        }
      ];
    });
  };

  return (

    <div className="encounter-search container-fluid"
      style={{
        backgroundImage: "linear-gradient(rgba(0, 0, 0, 0.5), rgba(0, 0, 0, 0.5)), url('/react/images/encounter_search_background.png')",
        backgroundSize: "cover",
        minHeight: "800px",
        width: "100%",
        padding: "20px",
        backgroundAttachment: "fixed",
      }}
    >
      <FilterPanel
        style={{
          display: filterPanel ? "block" : "none",
        }}
        formFilters={formFilters}
        setFormFilters={(input) => {
          setFormFilters(input)
        }}
        setFilterPanel={setFilterPanel}
        updateFilters={Function.prototype}
        schemas={schemas}
        // setRefresh={setRefresh}
        handleSearch={handleSearch}
        setQueryID={setQueryID}
        setSearchParams={setSearchParams}
      />
      <DataTable
        isLoading={loading}
        style={{
          display: !filterPanel ? "block" : "none",
        }}
        title={<FormattedMessage id="ENCOUNTER_SEARCH_RESULTS" defaultMessage={"Encounter Search Results"} />}
        columnNames={columns}
        tabs={updatedTabs}
        searchText={intl.formatMessage({ id: "SEARCH" })}
        tableData={sortedEncounters}
        totalItems={queryID ? totalItems : totalEncounters}
        page={queryID ? searchIdResultPage : page}
        perPage={queryID ? searchIdResultPerPage : perPage}
        onPageChange={queryID ? setSearchIdResultPage : setPage}
        onPerPageChange={queryID ? setSearchIdResultPerPage1 : setPerPage}
        setSort={setSort}

        loading={false}
        onRowClicked={(row) => {
          const url = `/encounters/encounter.jsp?number=${row.id}`;
          window.location.href = url;
        }}
        onSelectedRowsChange={(selectedRows) => {
          console.log("Selected Rows: ", selectedRows);
        }}
      />
      <SideBar
        formFilters={formFilters}
        setFilterPanel={setFilterPanel}
        setFormFilters={setFormFilters}
        // setRefresh={setRefresh}
        searchQueryId={searchQueryId}
        queryID={queryID}
      />
    </div>
  );
}
