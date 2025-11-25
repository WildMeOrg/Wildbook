const helperFunction = async (
  searchParams,
  store,
  setFilterPanel,
  setTempFormFilters = () => {},
) => {
  const params = Object.fromEntries(searchParams.entries()) || {};
  if (Object.keys(params).length === 0) {
    return;
  }

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
    }
    if (key === "searchQueryId") {
      store.formFilters = JSON.parse(sessionStorage.getItem("formData")) || [];
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
    }
  }
  setTempFormFilters([...store.formFilters]);
  setFilterPanel(false);
  return;
};

export { helperFunction };
