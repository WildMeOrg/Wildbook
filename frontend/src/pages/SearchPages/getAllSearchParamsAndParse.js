const helperFunction = async (searchParams, store, setFilterPanel) => {
  const params = Object.fromEntries(searchParams.entries()) || {};
  if (Object.keys(params).length === 0) {
    return;
  }

  let didAddFilter = false;

  for (const [key, _] of Object.entries(params)) {
    if (key === "username") {
      store.addFilter(
        "assignedUsername",
        "filter",
        {
          terms: {
            assignedUsername: [params.username],
          },
        },
        "Assigned User",
      );
      didAddFilter = true;
    }
    if (key === "state") {
      store.addFilter(
        "state",
        "filter",
        {
          terms: {
            state: [params.state],
          },
        },
        "Encounter State",
      );
      didAddFilter = true;
    }
    if (key === "searchQueryId") {
      store.getFiltersFromStorage();
    }
    if (key === "individualIDExact") {
      store.addFilter(
        "individualId",
        "filter",
        {
          terms: {
            individualId: [params.individualIDExact],
          },
        },
        "Individual ID",
      );
      didAddFilter = true;
    }
  }
  if (didAddFilter) {
    store.applyFilters();
    setFilterPanel(false);
  }

  return;
};

export { helperFunction };
