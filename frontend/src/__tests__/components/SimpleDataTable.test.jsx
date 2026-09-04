import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import SimpleDataTable from "../../components/SimpleDataTable";

// Regression coverage for issue #1637: while a bulk-import task polls,
// SimpleDataTable receives a fresh `data` array on every poll. It must
// keep the reviewer on their current page instead of resetting to the
// first page on each refresh.
describe("SimpleDataTable pagination preservation", () => {
  const columns = [
    {
      name: "ID",
      selector: (row) => row.id,
      cell: (row) => row.id,
    },
  ];

  const makeRows = (count) =>
    Array.from({ length: count }, (_, index) => ({ id: `row-${index + 1}` }));

  test("keeps the current page when a new data array arrives (poll)", () => {
    // 25 rows / 10 per page => 3 pages.
    const { rerender } = render(
      <SimpleDataTable columns={columns} data={makeRows(25)} perPage={10} />,
    );

    // Navigate to page 3 (rows 21-25).
    fireEvent.click(screen.getByText("3"));
    expect(screen.getByText("row-21")).toBeInTheDocument();
    expect(screen.queryByText("row-1")).not.toBeInTheDocument();

    // A poll hands us a brand-new array reference with identical content.
    rerender(
      <SimpleDataTable columns={columns} data={makeRows(25)} perPage={10} />,
    );

    // Still on page 3 — not reset to page 1.
    expect(screen.getByText("row-21")).toBeInTheDocument();
    expect(screen.queryByText("row-1")).not.toBeInTheDocument();
  });

  test("clamps to the last page when the row count shrinks", () => {
    const { rerender } = render(
      <SimpleDataTable columns={columns} data={makeRows(25)} perPage={10} />,
    );

    fireEvent.click(screen.getByText("3"));
    expect(screen.getByText("row-21")).toBeInTheDocument();

    // Data shrinks to a single page; page index 2 is now out of range.
    rerender(
      <SimpleDataTable columns={columns} data={makeRows(5)} perPage={10} />,
    );

    // Clamped to the only remaining page rather than rendering a blank page.
    expect(screen.getByText("row-1")).toBeInTheDocument();
    expect(screen.getByText("row-5")).toBeInTheDocument();
  });
});
