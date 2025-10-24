
import React, { useEffect, useState } from "react";
import DataTable from "../../components/DataTable";
import useFilterEncounters from "../../models/encounters/useFilterEncounters";
import useFilterEncountersAll from "../../models/encounters/useFilterEncountersAll";
import FilterPanel from "../../components/FilterPanel";
import useEncounterSearchSchemas from "../../models/encounters/useEncounterSearchSchemas";
import SideBar from "../../components/filterFields/SideBar";
import { FormattedMessage } from "react-intl";
import { useSearchParams } from "react-router-dom";
import { useIntl } from "react-intl";
import axios from "axios";
import { get } from "lodash-es";
import ThemeColorContext from "../../ThemeColorProvider";
import { encounterSearchColumns } from "../../constants/searchPageColumns";
import { encounterSearchPagetabs } from "../../constants/searchPageTabs";
import { globalEncounterFormStore as store } from "./stores/EncounterFormStore";
import { helperFunction } from "./getAllSearchParamsAndParse";
import ExportModal from "./components/ExportModal";
import { observer } from "mobx-react-lite";

const EncounterSearch = observer(() => {
  const columns = encounterSearchColumns;
  const tabs = encounterSearchPagetabs;
  const intl = useIntl();
  const schemas = useEncounterSearchSchemas();
  const theme = React.useContext(ThemeColorContext);
  const [totalItems, setTotalItems] = useState(0);
  const [searchIdResultPage, setSearchIdResultPage] = useState(0);
  const [searchIdResultPerPage, setSearchIdResultPerPage1] = useState(20);
  const [sort, setSort] = useState({ sortname: "date", sortorder: "desc" });
  const { sortname, sortorder } = sort;

  const [page, setPage] = useState(0);
  const [perPage, setPerPage] = useState(20);
  const [searchParams, setSearchParams] = useSearchParams();
  const regularQuery = searchParams.get("regularQuery");
  const [queryID, setQueryID] = useState(
    regularQuery ? null : searchParams.get("searchQueryId"),
  );

  const [searchData, setSearchData] = useState([]);
  const [filterPanel, setFilterPanel] = useState(queryID ? false : true);
  const [encounterSortName, setEncounterSortName] = useState("date");
  const [encounterSortOrder, setEncounterSortOrder] = useState("desc");
  const [searchIdSortName, setSearchIdSortName] = useState("date");
  const [searchIdSortOrder, setSearchIdSortOrder] = useState("desc");
  const [tempFormFilters, setTempFormFilters] = useState([]);
  const [exportModalOpen, setExportModalOpen] = useState(false);

  useEffect(() => {
    helperFunction(searchParams, store, setFilterPanel, setTempFormFilters, encounterData);
  }, [searchParams]);

  useEffect(() => {
    if (!queryID) {
      setEncounterSortName(sortname);
      setEncounterSortOrder(sortorder);
    } else {
      setSearchIdSortName(sortname);
      setSearchIdSortOrder(sortorder);
    }
  }, [queryID, sortname, sortorder]);

  const {
    data: encounterData,
    loading,
    refetch,
  } = useFilterEncounters({
    queries: store.formFilters,
    params: {
      sort: encounterSortName,
      sortOrder: encounterSortOrder,
      size: perPage,
      from: page * perPage,
    },
  });

  const { refetch: refetchAll } = useFilterEncountersAll({
    queries: store.formFilters,
    params: { sort: encounterSortName, sortOrder: encounterSortOrder },
  });

  const encounters = queryID ? searchData || [] : encounterData?.results || [];

  const sortedEncounters = React.useMemo(() => {
    const list = (encounters || []).slice();
    if (!sortname || !sortorder) return list;
    return list.sort((a, b) => {
      const va = a?.[sortname],
        vb = b?.[sortname];
      if (va == null || vb == null) return 0;
      if (sortorder === "asc") return va > vb ? 1 : va < vb ? -1 : 0;
      if (sortorder === "desc") return va < vb ? 1 : va > vb ? -1 : 0;
      return 0;
    });
  }, [encounters, sortname, sortorder]);

  const totalEncounters = encounterData?.resultCount || 0;
  const searchQueryId = encounterData?.searchQueryId || "";

  const updatedTabs = tabs.map((tab) => {
    const [name, url] = tab.split(":");
    const updatedUrl = queryID
      ? `${url}?searchQueryId=${queryID}`
      : searchQueryId
        ? `${url}?searchQueryId=${searchQueryId}&regularQuery=true`
        : url;
    return `${name}:${updatedUrl}`;
  });

  useEffect(() => {
    if (queryID) {
      axios
        .get(
          `/api/v3/search/${queryID}?from=${searchIdResultPage * searchIdResultPerPage}&size=${searchIdResultPerPage}&sort=${searchIdSortName}&sortOrder=${searchIdSortOrder}`,
        )
        .then((response) => {
          setSearchData(response?.data?.hits || []);
          setTotalItems(
            parseInt(
              get(response, ["headers", "x-wildbook-total-hits"], "0"),
              10,
            ),
          );
          setFilterPanel(false);
        })
        .catch((error) => {
          console.error("Error fetching search data:", error);
        });
    }
  }, [
    queryID,
    searchIdResultPage,
    searchIdResultPerPage,
    searchIdSortName,
    searchIdSortOrder,
  ]);

  useEffect(() => {
    const handlePopState = () => {
      setFilterPanel((prev) => !prev);
    };
    window.addEventListener("popstate", handlePopState);
    return () => {
      window.removeEventListener("popstate", handlePopState);
    };
  }, []);

  const handleSearch = () => {
    setSearchParams((PrevSearchParams) => {
      const newSearchParams = new URLSearchParams(PrevSearchParams);
      newSearchParams.set("results", "true");
      return newSearchParams;
    });
  };

  return (
    <div
      className="encounter-search container-fluid"
      style={{
        backgroundImage: `linear-gradient(rgba(0, 0, 0, 0.5), rgba(0, 0, 0, 0.5)), url('${process.env.PUBLIC_URL}/images/encounter_search_background.png')`,
        backgroundSize: "cover",
        minHeight: "700px",
        width: "100%",
        padding: "20px",
        backgroundAttachment: "fixed",
      }}
    >
      <FilterPanel
        style={{
          display: filterPanel ? "block" : "none",
        }}
        setFilterPanel={setFilterPanel}
        schemas={schemas}
        handleSearch={handleSearch}
        setQueryID={setQueryID}
        refetch={refetch}
        setTempFormFilters={setTempFormFilters}
        store={store}
      />
      <DataTable
        store={store}
        refetchAll={refetchAll}
        isLoading={loading}
        style={{
          display: !filterPanel ? "block" : "none",
        }}
        title={
          <FormattedMessage
            id="ENCOUNTER_SEARCH_RESULTS"
            defaultMessage={"Encounter Search Results"}
          />
        }
        columnNames={columns}
        tabs={updatedTabs}
        searchText={intl.formatMessage({ id: "SEARCH" })}
        tableData={sortedEncounters}
        totalItems={queryID ? totalItems : totalEncounters}
        page={queryID ? searchIdResultPage : page}
        perPage={queryID ? searchIdResultPerPage : perPage}
        onPageChange={queryID ? setSearchIdResultPage : setPage}
        onPerPageChange={queryID ? setSearchIdResultPerPage1 : setPerPage}
        setSort={setSort}
        loading={false}
        setExportModalOpen={setExportModalOpen}
        extraStyles={[
          {
            when: (row) => row.access === "none",
            style: {
              backgroundColor: theme?.statusColors?.yellow100 || "#fff3cd",
              "&:hover": {
                backgroundColor: theme?.primaryColors?.primary300 || "#e0f7fa",
              },
            },
          },
        ]}
        onRowClicked={(row) => {
          const url = `/react/encounter?number=${row.id}`;
          window.open(url, "_blank");
        }}
        onSelectedRowsChange={(selectedRows) => {
          store.setSelectedRows(selectedRows?.selectedRows || []);
        }}
      />
      <SideBar
        setFilterPanel={setFilterPanel}
        searchQueryId={searchQueryId}
        queryID={false}
        store={store}
        tempFormFilters={tempFormFilters}
      />
      <ExportModal
        open={exportModalOpen}
        setOpen={setExportModalOpen}
        searchQueryId={searchQueryId}
      />
    </div>
  );
});

export default EncounterSearch;
