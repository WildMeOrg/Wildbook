// OpenSearch refuses to fetch hits past index.max_result_window (a from + size
// ceiling), so pagination must only offer pages whose whole range stays inside that
// window even when the total hit count is larger. The backend reports the per-index
// ceiling via the X-Wildbook-Max-Result-Window response header; default to the
// OpenSearch default.
export const DEFAULT_MAX_RESULT_WINDOW = 10000;

function effectiveWindow(maxResultWindow) {
  return maxResultWindow > 0 ? maxResultWindow : DEFAULT_MAX_RESULT_WINDOW;
}

export function pageCount(totalItems, maxResultWindow, perPage) {
  if (!perPage || perPage < 1) return 0;
  const total = totalItems || 0;
  const windowLimit = effectiveWindow(maxResultWindow);

  // inside the window a partial last page is fine (from + size stays <= window)
  if (total <= windowLimit) return Math.ceil(total / perPage);
  // capped: only FULL pages fit - a ceil'd last page would straddle the window
  // (e.g. window 10000 at 30/page: from=9990&size=30 = 10020 is refused)
  return Math.floor(windowLimit / perPage);
}

export function browsableItemCount(totalItems, maxResultWindow, perPage) {
  const total = totalItems || 0;
  const windowLimit = effectiveWindow(maxResultWindow);

  if (total <= windowLimit) return total;
  const pages = pageCount(total, maxResultWindow, perPage);

  return pages > 0 ? pages * perPage : Math.min(total, windowLimit);
}
