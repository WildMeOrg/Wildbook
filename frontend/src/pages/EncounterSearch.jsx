import React, { useEffect, useState } from "react";
import DataTable from "../components/DataTable";
import useFilterEncounters from "../models/encounters/useFilterEncounters";
import FilterPanel from "../components/FilterPanel";
import useEncounterSearchSchemas from "../models/encounters/useEncounterSearchSchemas";

export default function EncounterSearch() {

  const [formFilters, setFormFilters] = useState([]);
  const [filterPanel, setFilterPanel] = useState(true);
  const [sliderIn, setSliderIn] = useState(false);

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
  const [perPage, setPerPage] = useState(10);

  const formFilters1 = {
    "query": {
      "bool": {
        "filter": [

          //   {
          //     "match" : {
          //        "taxonomy" : "Giraffa tippelskirchi"
          //     }
          //  },

          //  {
          //     "match" : {
          //        "individualId" : "924911ec-3c40-4678-ae29-1b4e38523f17"
          //     }
          //  },

          // {
          //      "match" : {
          //      "numberAnnotations" : "1"
          //      }
          // },

          // {
          //      "match" : {
          //      "id" : "a29b7e4b-e19b-4973-99e9-39a9ce12559e"
          //      }
          // },

          // {
          //     "range" : {
          //        "numberAnnotations" : {
          //           "gte" : 1,
          //           "lte" : 5
          //        }
          //     }
          //  },

          //  {
          //     "range": {
          //        "date": {
          //           "gte": "2022-01-01T00:00:00Z",
          //           "lte": "2024-12-31T23:59:59Z"
          //        }
          //     }
          //  },
          // {
          //   geo_distance: {
          //     distance: "500km",
          //     locationGeoPoint: {
          //       lat: 11,
          //       lon: 22
          //     }
          //   }
          // }
        ],
        "must_not": []
      }
    }
  }

  const { data: encounterData, loading } = useFilterEncounters({
    queries: formFilters,
    params: {
      sort: "date",
      size: perPage,
      from: page * perPage,
    },
  });

  // console.log("encounterData", encounterData);
  const encounters = encounterData?.results || [];
  const totalEncounters = encounterData?.resultCount || 0;

  useEffect(() => {
    console.log("Page: ", page);
    console.log("PerPage: ", perPage);
  }, [page, perPage]);

  return (

    <div className="encounter-search"
      style={{
        backgroundImage: "url('/react/images/encounter_search_background.png')",
        backgroundSize: "cover",
        height: "800px",
        width: "100%",
      }}
    >
      {filterPanel ? <FilterPanel
        formFilters={formFilters}
        setFormFilters={setFormFilters}
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
    </div>
  );
}
