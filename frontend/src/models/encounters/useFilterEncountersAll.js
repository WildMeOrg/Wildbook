import { get, partition } from "lodash-es";
import useFetchManual from "../../hooks/useFetchManual";
import { getEncounterFilterQueryKey } from "../../constants/queryKeys";

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

export default function useFilterEncountersAll({ queries, params = {} }) {
  const boolQuery = buildQuery(queries);
  const compositeQuery = { query: { bool: boolQuery } };
  const { sortOrder, sort } = params;

  return useFetchManual({
    method: "post",
    queryKey: getEncounterFilterQueryKey(queries, sort, sortOrder),
    url: "/search/encounter",
    data: compositeQuery,
    params: {
      sort: sort?.sortname,
      sortOrder: sort?.sortorder,
      size: 10000, // Default size to 10000
      from: 0, // Default from to 0
    },
    dataAccessor: (result) => {
      const resultCount = parseInt(
        get(result, ["data", "headers", "x-wildbook-total-hits"], "0"),
        10,
      );

      return {
        resultCount,
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
      retry: 2,
      enable: false,
    },
  });
}
