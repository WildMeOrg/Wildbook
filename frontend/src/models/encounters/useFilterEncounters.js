import { get, partition } from "lodash-es";
import { getEncounterFilterQueryKey } from "../../constants/queryKeys";
import useFetch from "../../hooks/useFetch";

function buildQuery(queries) {
  const mustNotQueries = queries.filter((q) => q.clause === "must_not");
  const [nestedQueries, nonNestedQueries] = partition(
    queries,
    (q) => q.clause === "nested",
  );
  const [filterQueries, otherQueries] = partition(
    nonNestedQueries,
    (q) => q.clause === "filter",
  );
  const arrayQueries = otherQueries.filter((q) => q.clause === "array");

  const nestedQuery = nestedQueries.map((n) => ({
    nested: {
      path: n.path,
      query: n.query,
    },
  }));

  const combinedFilterQueries = [
    ...filterQueries.map((f) => f.query),
    ...arrayQueries.flatMap((a) => a.query),
  ];

  return {
    filter: combinedFilterQueries,
    must_not: mustNotQueries.map((f) => f.query),
    must: [...nestedQuery],
  };
}

export default function useFilterEncounters({ queries, params = {} }) {
  const boolQuery = buildQuery(queries);
  const compositeQuery = { query: { bool: boolQuery } };
  const { sortOrder, sort, size, from } = params;

  return useFetch({
    method: "post",
    queryKey: getEncounterFilterQueryKey(queries, size, from, sort, sortOrder),
    url: "/search/encounter",
    data: compositeQuery,
    params: {
      sort: sort?.sortname,
      sortOrder: sort?.sortorder,
      size: size || 20,
      from: from || 0,
      ...params,
    },
    dataAccessor: (result) => {
      const resultCount = parseInt(
        get(result, ["data", "headers", "x-wildbook-total-hits"], "0"),
        10,
      );
      const maxResultWindow = parseInt(
        get(result, ["data", "headers", "x-wildbook-max-result-window"], "0"),
        10,
      );

      return {
        resultCount,
        maxResultWindow,
        results: get(result, ["data", "data", "hits"], []),
        searchQueryId: get(
          result,
          ["data", "data", "searchQueryId"],
          "defaultSearchQueryId",
        ),
        success: get(result, ["data", "data", "success"], false),
      };
    },
    queryOptions: {
      // retry transport/server errors only - a 4xx (e.g. pagination window
      // exceeded) is deterministic and re-sending it just repeats the failure
      retry: (failureCount, err) => {
        const status = err?.response?.status;
        return (!status || status >= 500) && failureCount < 2;
      },
      enable: false,
    },
  });
}
