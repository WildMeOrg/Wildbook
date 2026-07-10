// OpenSearch refuses to fetch hits past index.max_result_window (from + size ceiling),
// so pagination must only offer pages inside that window even when the total hit count
// is larger. The backend reports the per-index ceiling via the
// X-Wildbook-Max-Result-Window response header; default to the OpenSearch default.
export const DEFAULT_MAX_RESULT_WINDOW = 10000;

export function browsableItemCount(totalItems, maxResultWindow) {
  const windowLimit =
    maxResultWindow > 0 ? maxResultWindow : DEFAULT_MAX_RESULT_WINDOW;
  return Math.min(totalItems || 0, windowLimit);
}

export function pageCount(totalItems, maxResultWindow, perPage) {
  if (!perPage || perPage < 1) return 0;
  return Math.ceil(browsableItemCount(totalItems, maxResultWindow) / perPage);
}
