import React, { useEffect, useState } from "react";
import { Alert } from "react-bootstrap";
import DataTable from "./components/DataTable";

export default function About() {
  const columnNames = ["age", "name", "location"];

  const [page, setPage] = useState(0);
  const [perPage, setPerPage] = useState(10);
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
      <h1>About</h1>
      <Alert>This is a simple page about wildbook.</Alert>
      <DataTable
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
