import { get, partition } from "lodash-es";

import useFetch from "../../hooks/useFetch";
import { getEncounterFilterQueryKey } from "../../constants/queryKeys";

export default function useFilterEncounters({ queries, params = {} }) {
  const [filters, mustNots] = partition(queries, (q) => q.clause === "filter");

  const filterQueries = filters.map((f) => f.query);
  const mustNotQueries = mustNots.map((f) => f.query);

  const compositeQuery = {
    bool: { filter: filterQueries, must_not: mustNotQueries || [] },
  };

  return useFetch({
        method: "post",
        queryKey: getEncounterFilterQueryKey(queries, params),
        url: "/search/encounter",
        data: queries,
        params: {
          sort: "date",
          //reverse: false,
          size:1,
          from: 3,       
          ...params,
        },
        dataAccessor: (result) => {
            console.log("result", result);
            const resultCountString = get(result, ["data", "headers", "x-wildbook-total-hits"]);
            return {
                resultCount: parseInt(resultCountString, 10) || 0,
                results: get(result, ["data", "data","hits"], []),
            };
            },
            queryOptions: {
            retry: 2,
            },
      })
  
  
}
