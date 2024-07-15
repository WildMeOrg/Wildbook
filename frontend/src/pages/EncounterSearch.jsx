import React, { useEffect, useState } from "react";
import DataTable from "../components/DataTable";
import useFilterEncounters from "../models/encounters/useFilterEncounters";
import FilterPanel from "../components/FilterPanel";
import useEncounterSearchSchemas from "../models/encounters/useEncounterSearchSchemas";
import SideBar from "../components/filterFields/SideBar";

export default function EncounterSearch() {

  const [formFilters, setFormFilters] = useState([]);
  const [filterPanel, setFilterPanel] = useState(true);
  const [sliderIn, setSliderIn] = useState(false);

  console.log("formFilters", formFilters);

  const columns = [
    { name: "Encounter ID", selector: "id" },
    { name: "Created Date", selector: "date" },
    { name: "Individual ID", selector: "individualId" },
    { name: "Location ID", selector: "locationId" },
    { name: "Number Annotations", selector: "numberAnnotations" },
    { name: "Species", selector: "taxonomy" },
  ];

  const schemas = useEncounterSearchSchemas();

  const [page, setPage] = useState(0);
  const [perPage, setPerPage] = useState(20);

  const { data: encounterData, loading } = useFilterEncounters({
    queries: formFilters,
    params: {
      sort: "date",
      size: perPage,
      from: page * perPage,
    },
  });

  const encounters = encounterData?.results || [];
  const totalEncounters = encounterData?.resultCount || 0;

  return (

    <div className="encounter-search container-fluid"
      style={{
        backgroundImage: "url('/react/images/encounter_search_background.png')",
        backgroundSize: "cover",
        height: "800px",
        width: "100%",
        overflow: "auto",
        padding: "20px",
      }}
    >
      {filterPanel ? <FilterPanel
        formFilters={formFilters}
        setFormFilters={setFormFilters}
        setFilterPanel={setFilterPanel}
        updateFilters={Function.prototype}
        schemas={schemas}
      />
        : <DataTable
          title="Encounters"
          columnNames={columns}
          tableData={encounters}
          totalItems={totalEncounters}
          page={page}
          perPage={perPage}
          onPageChange={setPage}
          onPerPageChange={setPerPage}
          loading={false}
          onSelectedRowsChange={(selectedRows) => {
            console.log("Selected Rows: ", selectedRows);
          }}
        />}
      <SideBar
        formFilters={formFilters}
        setFilterPanel={setFilterPanel}
        setFormFilters={setFormFilters}
      />
    </div>
  );
}
