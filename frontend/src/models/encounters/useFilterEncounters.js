// import { get, partition } from "lodash-es";
// import useFetch from "../../hooks/useFetch";
// import { getEncounterFilterQueryKey } from "../../constants/queryKeys";

// export default function useFilterEncounters({ queries, params = {} }) {
//   console.log("Queries:", queries);
//   const [nestedQueries, nonNestedQueries] = partition(queries, q => q.clause === "nested");
//   const [filterQueries, mustNotQueries] = partition(nonNestedQueries, q => q.clause === "filter");

//   const nestedQuery = nestedQueries.map(n => ({
//     nested: {
//       path: n.path,
//       query: {
//         bool: {
//           filter: n.query.bool.filter.map(f => ({ match: f }))
//         }
//       }
//     }
//   }));

//   console.log("Nested Query:", nestedQuery);

//   const boolQuery = {
//     filter: filterQueries.map(f => ({ match: f.query })),
//     must_not: mustNotQueries.map(f => f.query)
//   };

//   console.log

//   if (nestedQuery.length > 0) {
//     boolQuery.filter.push(...nestedQuery);
//   }

//   const compositeQuery = {
//     query: {
//       bool: boolQuery
//     }
//   };

//   return useFetch({
//     method: "post",
//     queryKey: getEncounterFilterQueryKey(queries, params),
//     url: "/search/encounter",
//     data: compositeQuery,
//     params: {
//       sort: "date",
//       size: 1,
//       from: 3,
//       ...params,
//     },
//     dataAccessor: (result) => {
//       const resultCountString = get(result, ["data", "headers", "x-wildbook-total-hits"], "0");
//       return {
//         resultCount: parseInt(resultCountString, 10),
//         results: get(result, ["data", "data", "hits"], []),
//       };
//     },
//     queryOptions: {
//       retry: 2,
//     },
//   });
// }

import { get, partition } from "lodash-es";
import useFetch from "../../hooks/useFetch";
import { getEncounterFilterQueryKey } from "../../constants/queryKeys";

export default function useFilterEncounters({ queries, params = {} }) {
  const [nestedQueries, nonNestedQueries] = partition(queries, q => q.clause === "nested");
  const [filterQueries, mustNotQueries] = partition(nonNestedQueries, q => q.clause === "filter");

  const nestedQuery = nestedQueries.map(n => ({
    nested: {
      path: n.path,
      query: {
        bool: {
          filter: n.query.bool.filter.map(f => ({ match: f }))
        }
      }
    }
  }));

  const boolQuery = {
    filter: filterQueries.map(f => ({ match: f.query })),
    must_not: mustNotQueries.map(f => f.query),
    must: nestedQuery  
  };

  const compositeQuery = {
    query: {
      bool: boolQuery
    }
  };

  return useFetch({
    method: "post",
    queryKey: getEncounterFilterQueryKey(queries, params),
    url: "/search/encounter",
    data: compositeQuery,
    params: {
      sort: "date",
      size: 1,
      from: 3,
      ...params,
    },
    dataAccessor: (result) => {
      const resultCountString = get(result, ["data", "headers", "x-wildbook-total-hits"], "0");
      return {
        resultCount: parseInt(resultCountString, 10),
        results: get(result, ["data", "data", "hits"], []),
      };
    },
    queryOptions: {
      retry: 2,
    },
  });
}
