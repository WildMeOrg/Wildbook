
const helperFunction = (searchParams, store, setFilterPanel) => {

    const params = Object.fromEntries(searchParams.entries()) || {};
    if(params.length === 0) {
        return;
    }
    Object.entries(params).map(([key, _]) => {
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
        }        
    });
    setFilterPanel(false);
};

export { helperFunction };