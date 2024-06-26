import React, { useEffect, useState } from "react";
import DataTable from "../components/DataTable";
import useFilterEncounters from "../models/encounters/useFilterEncounters";

export default function EncounterSearch() {
  const columnNames = ["age", "name", "location"];

  const [page, setPage] = useState(0);
  const [perPage, setPerPage] = useState(10);

  const { data: encounterData, loading } = useFilterEncounters({
    queries: formFilters,
    params: searchParams,
  });

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
