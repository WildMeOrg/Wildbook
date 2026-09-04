import axios from "axios";
import { useState, useEffect } from "react";

export default function usePostHeaderQuickSearch(value) {
  const [searchResults, setSearchResults] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!value.trim()) {
      setSearchResults([]);
      setLoading(false);
      return;
    }

    const delayDebounce = setTimeout(() => {
      setLoading(true);
      const searchValue = value.trim();
      const searchValueLower = searchValue.toLowerCase();
      axios
        .post("/api/v3/search/individual?size=10", {
          // Rank by relevance (the boosted should-clauses below), then by name
          // for deterministic order within a tier. A `sort` URL param would
          // replace relevance ranking entirely, and `id` is a UUID for newer
          // individuals, so sorting on it returns an arbitrary subset of the
          // matches (issue #1541).
          // (multi-valued names sort by their lowest value; unmapped_type keeps
          // the query working on an index without the names mapping)
          sort: [
            { _score: { order: "desc" } },
            { names: { order: "asc", unmapped_type: "keyword" } },
          ],
          query: {
            bool: {
              minimum_should_match: 1,
              should: [
                // Exact match on ID - highest priority (case-insensitive via wildcard)
                // Note: id field is keyword without normalizer, so we use wildcard
                // with no wildcards for case-insensitive exact match
                {
                  wildcard: {
                    id: {
                      value: searchValue,
                      case_insensitive: true,
                      boost: 100,
                    },
                  },
                },
                // Prefix match on ID - high priority
                {
                  wildcard: {
                    id: {
                      value: `${searchValue}*`,
                      case_insensitive: true,
                      boost: 50,
                    },
                  },
                },
                // Exact match on names - high priority
                // names field has normalizer, so term query with lowercase works
                {
                  term: {
                    names: {
                      value: searchValueLower,
                      boost: 80,
                    },
                  },
                },
                // Prefix match on names - medium priority
                {
                  prefix: {
                    names: {
                      value: searchValueLower,
                      boost: 40,
                    },
                  },
                },
                // Wildcard fallback for partial matches - lower priority
                {
                  wildcard: {
                    id: {
                      value: `*${searchValue}*`,
                      case_insensitive: true,
                      boost: 10,
                    },
                  },
                },
                {
                  wildcard: {
                    names: {
                      value: `*${searchValue}*`,
                      case_insensitive: true,
                      boost: 5,
                    },
                  },
                },
                {
                  wildcard: {
                    encounterIds: {
                      value: `*${searchValue}*`,
                      case_insensitive: true,
                      boost: 1,
                    },
                  },
                },
              ],
            },
          },
        })
        .then((response) => {
          setSearchResults(response?.data?.hits || []);
          setLoading(false);
        })
        .catch((error) => {
          console.error("Error fetching search results:", error);
          setLoading(false);
        });
    }, 300);

    return () => clearTimeout(delayDebounce);
  }, [value]);

  return { searchResults, loading };
}
