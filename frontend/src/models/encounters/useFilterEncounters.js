

import { get, partition } from "lodash-es";
import useFetch from "../../hooks/useFetch";
import { getEncounterFilterQueryKey } from "../../constants/queryKeys";

function buildQuery(queries) {
  const [mustNotQueries, nonMustNotQueries] = partition(queries, q => q.clause === "must_not");
  const [nestedQueries, nonNestedQueries] = partition(queries, q => q.clause === "nested");
  const [filterQueries, otherQueries] = partition(nonNestedQueries, q => q.clause === "filter");
  const mustQueries = nonNestedQueries.filter(q => q.clause === "must");

  const nestedQuery = nestedQueries.map(n => ({
    nested: {
      path: n.path,
      query: n.query,
    }
  }));  

  return {
    filter: filterQueries.map(f => f.query),
    must_not: mustNotQueries.map(f => f.query),
    must: [ ...nestedQuery]
  };
}

export default function useFilterEncounters({ queries, params = {} }) {

  const boolQuery = buildQuery(queries);
  const compositeQuery = { query: { bool: boolQuery } };
  const {sort, size, from, ...restParams} = params;

  return useFetch({
    method: "post",
    queryKey: getEncounterFilterQueryKey(queries, params),
    url: "/search/encounter",
    data: compositeQuery,
    params: {
      sort: "date",
      size: size || 20,
      from: from || 0,
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
