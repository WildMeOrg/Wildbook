
const helperFunction = (searchParams, store, setFilterPanel) => {

    const params = Object.fromEntries(searchParams.entries()) || {};

    if (!("searchQueryId" in params)) {
        return;
    }

    Object.entries(params).map(([key, value]) => {
        if (key === "searchQueryId") {
            console.log("3333333", sessionStorage.getItem("formData"));
            store.formFilters = JSON.parse(sessionStorage.getItem("formData")) || [];
        }

        if (key === "username") {
            console.log("username", value);
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
            console.log("state", value);
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



        // if (key === "organization") {
        //     console.log("organization", value);
        //     store.addFilter(
        //         "organizations",
        //         "filter", 
        //         {
        //             term: {
        //               organizations: params.organization,
        //             },
        //           },
        //         "Organization",
        //     );            
        // }
        // if (key === "project") {
        //     console.log("project", value);
        //     store.addFilter(
        //         "projects",
        //         "filter", 
        //         {
        //             term: {
        //               projects: params.project,
        //             },
        //           },
        //         "Project",
        //     );            
        // }
    });
    setFilterPanel(false);
};

export { helperFunction };