import { get, partition } from "lodash-es";
import { useCallback } from "react";
import axios from "axios";
import { useFetchManual } from "../../hooks/useFetchManual";
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

export default function useFilterEncountersWithMediaAssets({
  queries,
  params = {},
}) {
  const boolQuery = buildQuery(queries);
  const compositeQuery = { query: { bool: boolQuery } };
  const { sortOrder, sort, size, from } = params;

  const fetchMediaAssets = useCallback(
    async (overrideParams = {}) => {
      const response = await axios.request({
        url: "/api/v3/search/encounter",
        method: "post",
        data: compositeQuery,
        params: {
          sort: overrideParams.sort || sort?.sortname || "date",
          sortOrder: overrideParams.sortOrder || sort?.sortorder || "desc",
          size: overrideParams.size || size || 20,
          from: overrideParams.from ?? from ?? 0,
        },
      });
      return response;
    },
    [compositeQuery, sort, sortOrder, size, from],
  );

  const hookResult = useFetchManual({
    method: "post",
    queryKey: getEncounterFilterQueryKey(queries, sort, sortOrder),
    url: "/search/encounter",
    data: compositeQuery,
    params: {
      sort: sort?.sortname || "date",
      sortOrder: sort?.sortorder || "desc",
      size: size || 20,
      from: from || 0,
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
  });

  return { ...hookResult, fetchMediaAssets };
}
