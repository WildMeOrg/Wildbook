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
      axios
        .post("/api/v3/search/individual?size=10", {
          query: {
            bool: {
              should: [
                {
                  wildcard: {
                    names: {
                      value: `*${value}*`,
                      case_insensitive: true,
                    },
                  },
                },
                {
                  wildcard: {
                    id: {
                      value: `*${value}*`,
                      case_insensitive: true,
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
