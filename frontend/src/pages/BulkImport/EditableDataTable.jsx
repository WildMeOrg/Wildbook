import React, { useState, useEffect, useRef, useMemo } from "react";
import {
  tableHeaderMapping,
  columnsUseSelectCell,
} from "./BulkImportConstants";
import { throttle } from "lodash";
import { FormattedMessage } from "react-intl";

import {
  useReactTable,
  getCoreRowModel,
  getPaginationRowModel,
  flexRender,
} from "@tanstack/react-table";
import { observer } from "mobx-react-lite";
import useGetSiteSettings from "../../models/useGetSiteSettings";
import SelectCell from "../../components/SelectCell";
import BulkImportApplyToAllRowsModal from "./BulkImportApplyToAllRowsModal";

const EditableCell = observer(
  ({ store, value, rowIndex, columnId, setColId, setColValue }) => {
    const selectOptions = useMemo(() => {
      return store.getOptionsForSelectCell(columnId);
    }, [columnId, store]);
    const selectValue = useMemo(() => {
      if (
        value === null ||
        value === undefined ||
        String(value).trim() === ""
      ) {
        return null;
      }
      return { value, label: String(value) };
    }, [value]);

    const [showDetail, setShowDetail] = useState(false);
    const handleBlur = (e) => {
      const newValue = e.target.value;
      store.updateCellValue(rowIndex, columnId, newValue);
      store.invalidateValidation();
      const { errors, warnings } = store.validateSpreadsheet();
      store.setValidationErrors(errors);
      store.setValidationWarnings(warnings);
      if (columnId === "Encounter.submitterID") {
        setColId(columnId);
        setColValue(newValue);
        store.setApplyToAllRowModalShow(true);
      }
    };

    const useSelectCell = columnsUseSelectCell.includes(columnId);

    const renderInput = () => {
      if (useSelectCell) {
        return (
          <SelectCell
            options={selectOptions}
            value={selectValue}
            setColId={setColId}
            setColValue={setColValue}
            store={store}
            columnId={columnId}
            rowIndex={rowIndex}
            error={!!store.validationErrors?.[rowIndex]?.[columnId]}
          />
        );
      } else {
        return (
          <input
            type="text"
            className={`form-control form-control-sm rounded ${store.validationErrors?.[rowIndex]?.[columnId] ? "is-invalid" : ""}`}
            value={store.spreadsheetData?.[rowIndex]?.[columnId] ?? ""}
            title={value}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                handleBlur(e);
              }
            }}
            onChange={(e) =>
              store.updateCellValue(rowIndex, columnId, e.target.value)
            }
            onBlur={handleBlur}
            style={{ minWidth: "100px", maxWidth: "250px" }}
          />
        );
      }
    };

    return (
      <div>
        {renderInput()}
        {!!store.validationErrors?.[rowIndex]?.[columnId] &&
          (() => {
            const rawError = store.validationErrors[rowIndex][columnId];
            const isMediaAsset = columnId === "Encounter.mediaAsset0";

            if (!isMediaAsset) {
              if (typeof rawError === "object" && rawError.id) {
                return (
                  <div
                    className="invalid-feedback"
                    style={{ whiteSpace: "normal" }}
                  >
                    <FormattedMessage
                      id={rawError.id}
                      defaultMessage={rawError.id}
                      values={rawError.values}
                    />
                  </div>
                );
              }
              return (
                <div
                  className="invalid-feedback"
                  style={{ whiteSpace: "normal" }}
                >
                  <FormattedMessage id={rawError} defaultMessage={rawError} />
                </div>
              );
            }
            const missingList = rawError
              .replace(/^MISSING/, "")
              .split(",")
              .map((name) => name.trim())
              .filter(Boolean);

            return (
              <div
                className="invalid-feedback"
                style={{ whiteSpace: "normal" }}
              >
                <div>
                  <FormattedMessage id="BULKIMPORT_ERROR_INVALID_MISSINGIMAGES" />{" "}
                  {missingList.length}
                  <button
                    type="button"
                    className="btn btn-link btn-sm p-0 ms-2"
                    onClick={() => setShowDetail(!showDetail)}
                  >
                    {showDetail ? (
                      <i className="bi bi-chevron-up"></i>
                    ) : (
                      <i className="bi bi-chevron-down"></i>
                    )}
                  </button>
                </div>
                {showDetail && (
                  <ul className="list-unstyled mb-0 ps-3 ">
                    {missingList.map((img, idx) => (
                      <li key={idx}>{img}</li>
                    ))}
                  </ul>
                )}
              </div>
            );
          })()}
        {!!store.validationWarnings?.[rowIndex]?.[columnId] && (
          <div
            className="text-warning small mt-1"
            style={{ whiteSpace: "normal" }}
          >
            {
              <FormattedMessage
                id={store.validationWarnings[rowIndex][columnId]}
              />
            }
          </div>
        )}
      </div>
    );
  },
);

export const DataTable = observer(({ store }) => {
  const data = store.spreadsheetData || [];
  const columnsDef = store.columnsDef || [];
  const { data: siteData } = useGetSiteSettings();
  const minimalFields = siteData?.bulkImportMinimalFields || {};
  const validLocationIDs = siteData?.locationData.locationID || [];
  const validSubmitterIDs = siteData?.users?.map((user) => user.username) || [];
  const validSpecies = siteData?.siteTaxonomies || [];
  const validCountryIDs = siteData?.country || [];
  const validStates = siteData?.encounterState || [];
  const synonymFields = siteData?.bulkImportFieldNameSynonyms || [];
  const validSex = siteData?.sex || ["male", "female"];
  const validLifeStages = siteData?.lifeStage || [];
  const validLivingStatus = siteData?.livingStatus || [];
  const validBehavior = siteData?.behavior || [];
  const LabeledKeywordAllowedKeys =
    Object.keys(siteData?.labeledKeywordAllowedValues || {}) || [];
  const LabeledKeywordAllowedPairs = siteData?.labeledKeywordAllowedValues;
  const [columnPinning, setColumnPinning] = useState({
    left: ["rowNumber", columnsDef[0] || ""],
    right: [],
  });

  const [colId, setColId] = useState("");
  const [colValue, setColValue] = useState("");

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

  store.setMinimalFields(minimalFields);
  store.setValidLocationIDs(
    extractAllValues(store.convertToTreeData(validLocationIDs)),
  );
  store.setLocationIDOptions(store.convertToTreeData(validLocationIDs));
  store.setValidSubmitterIDs(validSubmitterIDs);
  store.setValidSpecies(validSpecies.map((species) => species.scientificName));
  store.setValidCountryIDs(validCountryIDs);
  store.setValidStates(validStates);
  store.setSynonymFields(synonymFields);
  store.setValidSex(validSex);
  store.setValidLifeStages(validLifeStages);
  store.setValidLivingStatus(validLivingStatus);
  store.setValidBehavior(validBehavior);
  store.setLabeledKeywordAllowedKeys(LabeledKeywordAllowedKeys);
  store.setLabeledKeywordAllowedPairs(LabeledKeywordAllowedPairs);

  useEffect(() => {
    if (!siteData) return;
    const timeout = setTimeout(() => {
      store.invalidateValidation();
      const { errors, warnings } = store.validateSpreadsheet();
      store.setValidationErrors(errors);
      store.setValidationWarnings(warnings);
    }, 500);

    return () => clearTimeout(timeout);
  }, [siteData]);

  const validateMediaAssets = useRef(
    throttle(() => {
      const col = "Encounter.mediaAsset0";
      const { errors } = store.validateMediaAsset0ColumnOnly();

      const currentErrors = store.validationErrors || {};

      store.spreadsheetData.forEach((_, rowIndex) => {
        const prevError = currentErrors?.[rowIndex]?.[col];
        const nextError = errors?.[rowIndex]?.[col];

        if (prevError && !nextError) {
          store.mergeValidationError(rowIndex, col, "");
        }
      });

      Object.entries(errors).forEach(([rowIndexStr, rowErrors]) => {
        const rowIndex = Number(rowIndexStr);
        const newError = rowErrors?.[col];
        const currentError = currentErrors?.[rowIndex]?.[col];

        if (newError && newError !== currentError) {
          store.mergeValidationError(rowIndex, col, newError);
        }
      });
    }, 2000),
  ).current;

  useEffect(() => {
    validateMediaAssets();
  }, [store.uploadedImages.length]);

  const columns = useMemo(() => {
    const colDefs = columnsDef.map((col) => ({
      header: tableHeaderMapping[col] || col,
      accessorKey: col,
      cell: ({ row }) => {
        const rowIndex = row.index;
        const value = store.spreadsheetData?.[rowIndex]?.[col] ?? "";
        const error = store.validationErrors?.[rowIndex]?.[col] ?? "";

        return (
          <EditableCell
            store={store}
            value={value}
            error={error}
            rowIndex={rowIndex}
            columnId={col}
            setColId={setColId}
            setColValue={setColValue}
          />
        );
      },
    }));

    colDefs.unshift({
      id: "rowNumber",
      header: "#",
      cell: ({ row }) => <div className="text-center">{row.index + 1}</div>,
    });

    return colDefs;
  }, [columnsDef, store.spreadsheetData, store.validationErrors]);

  const rowNumberColumn = {
    id: "rowNumber",
    header: "#",
    cell: ({ row }) => <div className="text-center">{row.index + 1}</div>,
  };
  columns.unshift(rowNumberColumn);

  const table = useReactTable({
    data,
    columns,
    columnResizeMode: "onChange",
    columnResizeDirection: "ltr",
    state: { columnPinning },
    onColumnPinningChange: setColumnPinning,
    defaultColumn: {
      enableResizing: true,
      size: 150,
      minSize: 50,
      maxSize: 500,
    },
    getCoreRowModel: getCoreRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    initialState: {
      pagination: {
        pageIndex: 0,
        pageSize: 10,
      },
      columnPinning: columnPinning,
    },
  });

  const pageCount = table.getPageCount();
  const currentPage = table.getState().pagination.pageIndex;

  return (
    <div
      className="p-3 border rounded shadow-sm bg-white mt-4"
      style={{
        overflowY: "auto",
      }}
      id="editable-data-table"
    >
      <BulkImportApplyToAllRowsModal
        store={store}
        columnId={colId}
        newValue={colValue}
      />

      <div className="table-responsive">
        <table
          className="table table-bordered table-hover table-sm"
          style={{
            maxHeight: "500px",
            overflowY: "auto",
            tableLayout: "auto",
            width: "max-content",
          }}
        >
          <thead className="table-light">
            {table.getHeaderGroups().map((headerGroup) => (
              <tr key={headerGroup.id}>
                {headerGroup.headers.map((header) => {
                  return (
                    <th
                      key={header.id}
                      className="text-capitalize position-relative text-center align-middle"
                      style={{
                        width: header.getSize(),
                      }}
                    >
                      {flexRender(
                        header.column.columnDef.header,
                        header.getContext(),
                      )}

                      {header.column.getCanResize() && (
                        <div
                          onMouseDown={header.getResizeHandler()}
                          onTouchStart={header.getResizeHandler()}
                          className="resizer"
                          style={{
                            position: "absolute",
                            right: 0,
                            top: 0,
                            width: "6px",
                            height: "100%",
                            cursor: "col-resize",
                            userSelect: "none",
                            touchAction: "none",
                            zIndex: 1,
                          }}
                        />
                      )}
                    </th>
                  );
                })}
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
        <p className="text-muted small mb-4">
          <FormattedMessage
            id="REQUIRED_FIELD_NOTE"
            defaultMessage="* denotes required fields"
          />
          <br />
          <FormattedMessage
            id="APPLY_TO_ALL_ROWS_NOTE"
            defaultMessage="+ denotes change can apply to all rows for this column"
          />
        </p>
      </div>

      <nav className="d-flex justify-content-between align-items-center mt-3">
        <div>
          <FormattedMessage
            id="SHOWING_PAGE_INFO"
            defaultMessage="Showing page {current} of {total}"
            values={{
              current: <strong>{currentPage + 1}</strong>,
              total: <strong>{pageCount}</strong>,
            }}
          />
        </div>

        <ul
          className="pagination pagination-sm mb-0"
          style={{
            flexWrap: "wrap",
            maxWidth: "100%",
            overflow: "auto",
          }}
        >
          <li
            className={`page-item ${!table.getCanPreviousPage() ? "disabled" : ""}`}
          >
            <button className="page-link" onClick={() => table.previousPage()}>
              <FormattedMessage id="PREV" />
            </button>
          </li>

          {Array.from({ length: pageCount }).map((_, i) => (
            <li
              key={i}
              className={`page-item ${i === currentPage ? "active" : ""}`}
            >
              <button
                className={`page-link ${store.errorPages.has(i) ? "bg-danger text-white" : ""}`}
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
              <FormattedMessage id="NEXT" />
            </button>
          </li>
        </ul>
      </nav>
    </div>
  );
});

export default DataTable;
