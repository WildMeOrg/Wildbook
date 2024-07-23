

import { get, partition } from "lodash-es";
import useFetch from "../../hooks/useFetch";
import { getEncounterFilterQueryKey } from "../../constants/queryKeys";

function buildQuery(queries) {
  const [nestedQueries, nonNestedQueries] = partition(queries, q => q.clause === "nested");
  const [filterQueries, mustNotQueries] = partition(nonNestedQueries, q => q.clause === "filter");
  const mustQueries = nonNestedQueries.filter(q => q.clause === "must");

  const nestedQuery = nestedQueries.map(n => ({
    nested: {
      path: n.path,
      query: n.query,
    }
  }));  

  return {
    filter: filterQueries.map(f => f.query),
    // must_not: mustNotQueries.map(f => f.query),
    must: [ ...nestedQuery]
  };
}

export default function useFilterEncounters({ queries, params = {} }) {
  // console.log("Queries:", queries);

  const boolQuery = buildQuery(queries);
  const compositeQuery = { query: { bool: boolQuery } };

  return useFetch({
    method: "post",
    queryKey: getEncounterFilterQueryKey(queries, params),
    url: "/search/encounter",
    data: compositeQuery,
    params: {
      sort: "date",
      // size: 1,
      // from: 3,
      ...params,
    },
    dataAccessor: (result) => {
      const resultCount = parseInt(get(result, ["data", "headers", "x-wildbook-total-hits"], "0"), 10);
      return {
        resultCount,
        results: get(result, ["data", "data", "hits"], []),
      };
    },
    queryOptions: {
      retry: 2,
    },
  });
}
