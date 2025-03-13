const helperFunction = (
  searchParams,
  store,
  setFilterPanel,
  setTempFormFilters = () => {},
) => {
  const params = Object.fromEntries(searchParams.entries()) || {};
  if (Object.keys(params).length === 0) {
    return;
  }
  Object.entries(params).forEach(([key, _]) => {
    if (key === "username") {
      store.addFilter(
        "assignedUsername",
        "filter",
        {
          term: {
            assignedUsername: params.username,
          },
        },
        "Assigned User",
      );
    }
    if (key === "state") {
      store.addFilter(
        "state",
        "filter",
        {
          term: {
            status: params.state,
          },
        },
        "Encounter State",
      );
    }
    if (key === "searchQueryId") {
      store.formFilters = JSON.parse(sessionStorage.getItem("formData")) || [];
      setTempFormFilters([...store.formFilters]);
    }
  });
  setFilterPanel(false);
};

export { helperFunction };
