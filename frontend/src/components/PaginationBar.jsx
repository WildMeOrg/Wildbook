import React, { useMemo, useState } from "react";
import ReactPaginate from "react-paginate";
import { FormattedMessage } from "react-intl";

export default function PaginationBar({
  totalItems,
  page,                 
  pageSize,             
  onPageChange,         
  onPageSizeChange,     
  className = "",
  pageSizeOptions = [10, 20, 30, 50],
  pageRangeDisplayed = 2,
  marginPagesDisplayed = 1,
}) {
  const totalPages = useMemo(
    () => Math.max(1, Math.ceil((totalItems || 0) / (pageSize || 10))),
    [totalItems, pageSize]
  );

  const [gotoInput, setGotoInput] = useState("");

  return (
    <div
      className={`d-flex align-items-center justify-content-between flex-wrap gap-2 ${className}`}
    >
      <div className="fw-semibold">
        <FormattedMessage
          id="pagination.total"
          defaultMessage="Total {n} items"
          values={{ n: totalItems }}
        />
      </div>

      <ReactPaginate
        pageCount={totalPages}
        forcePage={(page || 1) - 1}               
        onPageChange={(e) => onPageChange?.(e.selected + 1)}
        previousLabel="‹"
        nextLabel="›"
        breakLabel="…"
        marginPagesDisplayed={marginPagesDisplayed}
        pageRangeDisplayed={pageRangeDisplayed}
        containerClassName="pagination mb-0"
        pageClassName="page-item"
        pageLinkClassName="page-link"
        previousClassName="page-item"
        previousLinkClassName="page-link"
        nextClassName="page-item"
        nextLinkClassName="page-link"
        breakClassName="page-item"
        breakLinkClassName="page-link"
        activeClassName="active"
      />

      <div className="d-flex align-items-center gap-2">
        <select
          className="form-select form-select-sm bg-dark text-light"
          value={pageSize}
          onChange={(e) => onPageSizeChange?.(Number(e.target.value))}
          style={{ width: 120 }}
          aria-label="Page size"
        >
          {pageSizeOptions.map((n) => (
            <option key={n} value={n}>
              {n} / page
            </option>
          ))}
        </select>

        <div className="d-flex align-items-center gap-2">
          <span className="text-secondary">Go to</span>
          <input
            type="number"
            min={1}
            max={totalPages}
            value={gotoInput}
            onChange={(e) => setGotoInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                const v = Math.max(
                  1,
                  Math.min(totalPages, Number(gotoInput) || 1)
                );
                onPageChange?.(v);
                setGotoInput("");
              }
            }}
            className="form-control form-control-sm bg-dark text-light"
            style={{ width: 80 }}
            aria-label="Go to page"
          />
        </div>
      </div>
    </div>
  );
}
