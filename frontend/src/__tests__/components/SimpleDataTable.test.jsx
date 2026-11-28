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

  test("keeps the user's column sort when a new data array arrives", () => {
    const rows = () => [{ id: "b" }, { id: "c" }, { id: "a" }];
    const shownIds = () =>
      Array.from(document.querySelectorAll('[role="cell"]')).map(
        (cell) => cell.textContent,
      );
    const { rerender } = render(
      <SimpleDataTable columns={columns} data={rows()} perPage={10} />,
    );
    expect(shownIds()).toEqual(["b", "c", "a"]);

    // click the ID header once: ascending
    fireEvent.click(screen.getByText("ID"));
    expect(shownIds()).toEqual(["a", "b", "c"]);

    // a poll (or a search keystroke) hands over a fresh, unsorted array, and
    // the page rebuilds its columns, so every selector is a new function
    const freshColumns = () => [
      { name: "ID", selector: (row) => row.id, cell: (row) => row.id },
    ];
    rerender(
      <SimpleDataTable columns={freshColumns()} data={rows()} perPage={10} />,
    );
    expect(shownIds()).toEqual(["a", "b", "c"]);

    // the table still knows which column is sorted: the next click reverses
    fireEvent.click(screen.getByText("ID"));
    expect(shownIds()).toEqual(["c", "b", "a"]);
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

  test("a new search (resetPageKey change) shows the results from the first page", () => {
    // 60 rows / 10 per page => 6 pages
    const { rerender } = render(
      <SimpleDataTable columns={columns} data={makeRows(60)} perPage={10} resetPageKey="" />,
    );
    fireEvent.click(screen.getByText("4"));
    expect(screen.getByText("row-31")).toBeInTheDocument();

    // the search narrows the list to 45 rows: page 4 still exists, and without
    // the reset the first matches would be three pages back
    rerender(
      <SimpleDataTable columns={columns} data={makeRows(45)} perPage={10} resetPageKey="row" />,
    );
    expect(screen.getByText("row-1")).toBeInTheDocument();
    expect(screen.queryByText("row-31")).not.toBeInTheDocument();
    // the pager agrees: page 1 is the highlighted one, and Next goes to page 2
    expect(document.querySelector(".active-page").textContent).toBe("1");
    fireEvent.click(screen.getByText(">"));
    expect(screen.getByText("row-11")).toBeInTheDocument();
    fireEvent.click(screen.getByText("<"));

    // a poll with the same search keeps the page the user then moves to
    fireEvent.click(screen.getByText("2"));
    rerender(
      <SimpleDataTable columns={columns} data={makeRows(45)} perPage={10} resetPageKey="row" />,
    );
    expect(screen.getByText("row-11")).toBeInTheDocument();
    expect(screen.queryByText("row-1")).not.toBeInTheDocument();
  });

  test("shows the given empty message when there are no rows", () => {
    const { rerender } = render(
      <SimpleDataTable columns={columns} data={[]} perPage={10} />,
    );
    expect(screen.getByText(/no records to display yet/i)).toBeInTheDocument();
    rerender(
      <SimpleDataTable
        columns={columns}
        data={[]}
        perPage={10}
        emptyMessage={<span>Nothing matches</span>}
      />,
    );
    expect(screen.getByText("Nothing matches")).toBeInTheDocument();
    expect(screen.queryByText(/no records to display yet/i)).not.toBeInTheDocument();
  });

  test("a new search keeps the user's column sort", () => {
    const rows = (ids) => ids.map((id) => ({ id }));
    const shownIds = () =>
      Array.from(document.querySelectorAll('[role="cell"]')).map(
        (cell) => cell.textContent,
      );
    const { rerender } = render(
      <SimpleDataTable columns={columns} data={rows(["b", "d", "a", "c"])} perPage={2} resetPageKey="" />,
    );
    // sort descending, then move to page 2
    fireEvent.click(screen.getByText("ID"));
    fireEvent.click(screen.getByText("ID"));
    fireEvent.click(screen.getByText("2"));
    expect(shownIds()).toEqual(["b", "a"]);

    // a search narrows the rows: back to page 1, still sorted descending
    rerender(
      <SimpleDataTable columns={columns} data={rows(["b", "a", "c"])} perPage={2} resetPageKey="x" />,
    );
    expect(shownIds()).toEqual(["c", "b"]);
  });
});
