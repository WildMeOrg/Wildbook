import React, { useState, useEffect } from "react";

import {
  useReactTable,
  getCoreRowModel,
  getPaginationRowModel,
  flexRender,
} from "@tanstack/react-table";
import { observer } from "mobx-react-lite";
import useGetSiteSettings from "../../models/useGetSiteSettings";
import SelectCell from "./SelectCell";

const EditableCell = ({
  store,
  initialValue,
  rowIndex,
  columnId,
  externalError,
}) => {
  const [value, setValue] = useState(initialValue ?? "");
  const [error, setError] = useState(externalError ?? "");
  useEffect(() => {
    setError(externalError ?? "");
  }, [externalError]);

  const handleBlur = () => {
    store.spreadsheetData[rowIndex][columnId] = value;
    const errors = store.validateSpreadsheet();
    setError(errors[rowIndex]?.[columnId] || "");
  };

  const handleKeyDown = (e) => {
    if (e.key === "Enter") {
      e.target.blur();
    }
  };

  const useSelectCell = store.columnsUseSelectCell.includes(columnId);

  const renderInput = () => {
    if (useSelectCell) {
      return (
        <SelectCell
          options={
            store.getOptionsForSelectCell(columnId)
          }
          value={value ? { value, label: value } : null}
          onChange={(sel) => setValue(sel ? sel.value : "")}
          onBlur={handleBlur}
          error={error}
        />
      );
    } else {
      return (
        <input
          type="text"
          className={`form-control form-control-sm rounded ${error ? "is-invalid" : ""}`}
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onBlur={handleBlur}
          onKeyDown={handleKeyDown}
          style={{
            minWidth: "100px",
            maxWidth: "250px",
          }}
        />
      );
    }
  };

  return (
    <div>
      {renderInput()}
      {error && <div className="invalid-feedback">{error}</div>}
    </div>
  );
};

export const DataTable = observer(({ store }) => {
  const data = store.spreadsheetData || [];
  const [cellErrors, setCellErrors] = useState({});
  const columnsDef = store.columnsDef || [];
  const { data: siteData } = useGetSiteSettings();
  const validLocationIDs = siteData?.locationData.locationID || [];
  const validSubmitterIDs = siteData?.users?.map((user) => user.username) || [];
  const validSpecies = siteData?.siteTaxonomies || [];
  const validCountryIDs = siteData?.country || [];
  const validSex = siteData?.sex || ["male","female"];
  const validLifeStages = siteData?.lifeStage || [];
  const validLivingStatus = siteData?.livingStatus || [];
  const validBehavior = siteData?.behavior || [];

  console.log("validBehavior", validBehavior);
  console.log("store behavior", store.validBehavior);

  const extractAllValues = (treeData) => {
    const values = [];
    const traverse = (nodes) => {
      nodes.forEach((node) => {
        values.push(node.value);
        if (node.children && node.children.length > 0) {
          traverse(node.children);
        }
      });
    };
    traverse(treeData);
    return values;
  };

  store.setValidLocationIDs(
    extractAllValues(store.convertToTreeData(validLocationIDs)),
  );
  store.setValidSubmitterIDs(validSubmitterIDs);
  store.setValidSpecies(validSpecies.map((species) => species.scientificName));
  store.setValidCountryIDs(validCountryIDs);
  store.setValidSex(validSex);
  store.setValidLifeStages(validLifeStages);
  store.setValidLivingStatus(validLivingStatus);
  store.setValidBehavior(validBehavior);

  useEffect(() => {
    if (store.spreadsheetData.length > 0) {
      const errors = store.validateSpreadsheet();
      if (errors) {
        setCellErrors(errors);
      }
    }
  }, [store.spreadsheetData]);

  const columns = columnsDef.map((col) => ({
    header: store.tableHeaderMapping[col] || col,
    accessorKey: col,
    cell: ({ row }) => (
      <EditableCell
        store={store}
        initialValue={row.original[col]}
        rowIndex={row.index}
        columnId={col}
        externalError={cellErrors[row.index]?.[col] || ""}
      />
    ),
  }));

  const rowNumberColumn = {
    id: "rowNumber",
    header: "#",
    cell: ({ row }) => <div className="text-center">{row.index + 1}</div>,
  };
  columns.unshift(rowNumberColumn);

  const table = useReactTable({
    data,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    initialState: {
      pagination: {
        pageIndex: 0,
        pageSize: 20,
      },
    },
  });

  const pageCount = table.getPageCount();
  const currentPage = table.getState().pagination.pageIndex;

  return (
    <div className="p-3 border rounded shadow-sm bg-white mt-4"
      style={{
        maxHeight: "500px",
        overflowY: "auto",
      }}
    >
      <div className="table-responsive">
        <table className="table table-bordered table-hover table-sm">
          <thead className="table-light">
            {table.getHeaderGroups().map((headerGroup) => (
              <tr key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <th key={header.id} className="text-capitalize">
                    {flexRender(
                      header.column.columnDef.header,
                      header.getContext(),
                    )}
                  </th>
                ))}
              </tr>
            ))}
          </thead>
          <tbody>
            {table.getRowModel().rows.map((row) => (
              <tr key={row.id}>
                {row.getVisibleCells().map((cell) => (
                  <td key={cell.id}>
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <nav className="d-flex justify-content-between align-items-center mt-3">
        <div>
          Showing page <strong>{currentPage + 1}</strong> of{" "}
          <strong>{pageCount}</strong>
        </div>

        <ul className="pagination pagination-sm mb-0">
          <li
            className={`page-item ${!table.getCanPreviousPage() ? "disabled" : ""}`}
          >
            <button className="page-link" onClick={() => table.previousPage()}>
              Prev
            </button>
          </li>

          {Array.from({ length: pageCount }).map((_, i) => (
            <li
              key={i}
              className={`page-item ${i === currentPage ? "active" : ""}`}
            >
              <button
                className="page-link"
                onClick={() => table.setPageIndex(i)}
              >
                {i + 1}
              </button>
            </li>
          ))}

          <li
            className={`page-item ${!table.getCanNextPage() ? "disabled" : ""}`}
          >
            <button className="page-link" onClick={() => table.nextPage()}>
              Next
            </button>
          </li>
        </ul>
      </nav>
    </div>
  );
});

export default DataTable;
