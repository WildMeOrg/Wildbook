import { get, partition } from "lodash-es";

import useFetch from "../../hooks/useFetch";
import { getEncounterFilterQueryKey } from "../../constants/queryKeys";

export default function useFilterEncounters({ queries, params = {} }) {
  const [filters, mustNots] = partition(queries, (q) => q.clause === "filter");

  const filterQueries = filters.map((f) => f.query);
  const mustNotQueries = mustNots.map((f) => f.query);

  const compositeQuery = {
    bool: { filter: filterQueries, must_not: mustNotQueries },
  };
  return useFetch({
    method: "post",
    queryKey: getEncounterFilterQueryKey(queries, params),
    url: "/sightings/search",
    data: compositeQuery,
    params: {
      limit: 20,
      offset: 0,
      sort: "created",
      reverse: false,
      ...params,
    },
    dataAccessor: (result) => {
      const resultCountString = get(result, [
        "data",
        "headers",
        "x-total-count",
      ]);
      return {
        resultCount: parseInt(resultCountString, 10),
        results: get(result, ["data", "data"]),
      };
    },
    queryOptions: {
      retry: 2,
    },
  });
}
