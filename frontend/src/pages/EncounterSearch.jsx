import React, { useEffect, useState } from "react";
import DataTable from "../components/DataTable";
import useFilterEncounters from "../models/encounters/useFilterEncounters";

export default function EncounterSearch() {
  const columnNames = ["age", "name", "location"];

  const [page, setPage] = useState(0);
  const [perPage, setPerPage] = useState(10);

  const formFilters =  {
    "query" : {
       "bool" : {
          "filter" : [
 
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
          ],
          "must_not" : []
       }
    }
 }

  const { data: encounterData, loading } = useFilterEncounters({
    queries: formFilters,
    params: {},
  });

  console.log("encounterData", encounterData);

  useEffect(() => {
    console.log("Page: ", page);
    console.log("PerPage: ", perPage);
  }, [page, perPage]);
  const data = [
    {
      age: 232,
      name: "John Doe",
      location: "New York",
    },
    {
      age: 25,
      name: "Jane Doe",
      location: "Los Angeles",
    },
    {
      age: 27,
      name: "John Smith",
      location: "Chicago",
    },
    {
      age: 232,
      name: "John Doe",
      location: "New York",
    },
    {
      age: 25,
      name: "Jane Doe",
      location: "Los Angeles",
    },
    {
      age: 27,
      name: "John Smith",
      location: "Chicago",
    },
    {
      age: 232,
      name: "John Doe",
      location: "New York",
    },
    {
      age: 25,
      name: "Jane Doe",
      location: "Los Angeles",
    },
    {
      age: 27,
      name: "John Smith",
      location: "Chicago",
    },
    {
      age: 232,
      name: "John Doe",
      location: "New York",
    },
  ];

  return (
    <div
      style={{
        padding: "100px",
      }}
    >
      <DataTable
        title="Encounters"
        columnNames={columnNames}
        tableData={data}
        totalItems={85}
        page={page}
        perPage={perPage}
        onPageChange={setPage}
        onPerPageChange={setPerPage}
        loading={false}
        onSelectedRowsChange={(selectedRows) => {
          console.log("Selected Rows: ", selectedRows);
        }}
      />
    </div>
  );
}
