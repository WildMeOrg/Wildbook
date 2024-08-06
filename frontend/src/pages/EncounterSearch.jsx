import React, { useEffect, useState } from "react";
import DataTable from "../components/DataTable";
import useFilterEncounters from "../models/encounters/useFilterEncounters";
import FilterPanel from "../components/FilterPanel";
import useEncounterSearchSchemas from "../models/encounters/useEncounterSearchSchemas";
import SideBar from "../components/filterFields/SideBar";
import { FormattedMessage } from "react-intl";
import { useSearchParams } from "react-router-dom";
import { useIntl } from "react-intl";

export default function EncounterSearch() {

  const [formFilters, setFormFilters] = useState([]);
  const [filterPanel, setFilterPanel] = useState(true);
  const [refresh, setRefresh] = useState(false);
  const intl = useIntl();

  const columns = [
    { name: "Individual ID", selector: "individualId" },
    // { name: "Encounter ID", selector: "id" },
    { name: "Sighting ID", selector: "occurrenceId" },
    { name: "Alternative ID", selector: "otherCatalogNumbers" },
    { name: "Created Date", selector: "date" },
    { name: "Location ID", selector: "locationId" },
    { name: "Species", selector: "taxonomy" },
    { name: "Submitter", selector: "submitters" },
    { name: "Date Submitted", selector: "dateSubmitted" },
    { name: "Number Annotations", selector: "numberAnnotations" },
  ];

  const schemas = useEncounterSearchSchemas();

  const [page, setPage] = useState(0);
  const [perPage, setPerPage] = useState(20);
  const [searchParams, setSearchParams] = useSearchParams();

  // const [searchParams] = useSearchParams();
  const [ paramsFormFilters, setParamsFormFilters ] = useState([]);

  const paramsObject = Object.fromEntries(searchParams.entries()) || {};


  useEffect(() => {
    if (searchParams.get("results")) {
      setFilterPanel(false);
    } else {
      setFilterPanel(true);
    }
  }, [searchParams]);

  if (paramsObject.username && paramsFormFilters.find(opt => opt.filterId === "assignedUsername") === undefined) {
    setFilterPanel(false);
    setParamsFormFilters((prevFilters) => {
      return [
        ...prevFilters,
        {
          clause: "filter",
          filterId: "assignedUsername",
          query: {           
                  "term": {
                    "assignedUsername": paramsObject.username
                  }                
          }
        }
      ];});
  }    
        
  if (paramsObject.state && paramsFormFilters.find(opt => opt.filterId === "state") === undefined) {
    setParamsFormFilters((prevFilters) => {
      return [
        ...prevFilters,
        {
          clause: "filter",
          filterId: "state",
          query: {           
                  "term": {
                    "state": paramsObject.state
                  }                
          }
        }
      ];
    });
  };

  useEffect(() => {
    setFormFilters(Array.from(
          new Map([...paramsFormFilters, ...formFilters].map(filter => [filter.filterId, filter])).values()
      ));      
  }, [paramsFormFilters]);
  
  const { data: encounterData, loading, } = useFilterEncounters({
    queries: formFilters,
    params: {
      sort: "date",
      size: perPage,
      from: page * perPage,
    },
  });

  const encounters = encounterData?.results || [];
  const totalEncounters = encounterData?.resultCount || 0;
  const searchQueryId = encounterData?.searchQueryId || "";
  const tabs = [
    "ENCOUNTER_PROJECT_MANAGEMENT:/encounters/projectManagement.jsp",
    "ENCOUNTER_MATCHING_IMAGES_VIDEOS:/encounters/thumbnailSearchResults.jsp",
    "ENCOUNTER_MAPPED_RESULTS:/encounters/mappedSearchResults.jsp",
    "ENCOUNTER_RESULTS_CALENDAR:/xcalendar/calendar.jsp",
    "ENCOUNTER_ANALYSIS:/encounters/searchResultsAnalysis.jsp",
    "ENCOUNTER_EXPORT:/encounters/exportSearchResults.jsp",
  ];  

  const handleSearch = () => {
    setSearchParams({ results: "true" });
    setFilterPanel(false);
  };

  return (

    <div className="encounter-search container-fluid"
      style={{
        backgroundImage: "url('/react/images/encounter_search_background.png')",
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
        setRefresh={setRefresh}
        handleSearch={handleSearch} 
      />
      <DataTable
        isLoading={loading}
        style={{
          display: !filterPanel ? "block" : "none",
        }}
        title={<FormattedMessage id="ENCOUNTER_SEARCH_RESULTS" defaultMessage={"Encounter Search Results"} />}
        columnNames={columns}
        tabs={tabs}
        searchText={intl.formatMessage({id:"SEARCH_ENCOUNTERS"})}
        tableData={encounters}
        totalItems={totalEncounters}
        page={page}
        perPage={perPage}
        onPageChange={setPage}
        onPerPageChange={setPerPage}
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
        setRefresh={setRefresh}
        searchQueryId={searchQueryId}
      />
     
    </div>
  );
}
