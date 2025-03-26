import { useState, useEffect } from "react";
import axios from "axios";
import { useQuery } from "react-query";

function refreshNoop() {
  console.warning(
    "refresh is not implemented for useGet - replace with react-query invalidation logic instead.",
  );
}

// to do: borrow some logic from error formatter in utils, then delete that function
function formatError(response) {
  try {
    return response?.error ? response.error.toJSON().message : null;
  } catch (_error) {
    return "Error could not be formatted as JSON";
  }
}

export default function useFetch({
  queryKey,
  url,
  data,
  params,
  method = "get",
  dataAccessor = (result) => result?.data?.data,
  onSuccess = Function.prototype,
  queryOptions = {},
  responseType = "json",
}) {
  const [displayedError, setDisplayedError] = useState(null);
  const [displayedLoading, setDisplayedLoading] = useState(
    !queryOptions.disabled, // enabled? or disabled?
  );

  const apiUrl = `/api/v3${url}`;
  const result = useQuery(
    queryKey,
    async () => {
      const response = await axios.request({
        url: apiUrl,
        method,
        data,
        params,
        responseType,
      });
      const status = response?.status;
      if (status === 200) onSuccess(response);
      return response;
    },
    {
      staleTime: Infinity,
      cacheTime: Infinity,
      refetchOnMount: "always",
      ...queryOptions,
    },
  );

  const error = formatError(result);
  const statusCodeFromError = result?.error?.response?.status;
  useEffect(() => {
    if (result?.status === "loading") {
      setDisplayedLoading(true);
    } else {
      if (displayedError !== error) setDisplayedError(error);
      setDisplayedLoading(false);
    }
  }, [error, result?.status, statusCodeFromError, displayedError]);

  return {
    ...result,
    statusCode: result?.data?.status || result?.error?.response?.status,
    data: dataAccessor(result),
    isLoading: displayedLoading,
    loading: displayedLoading,
    error: displayedError,
    clearError: () => {
      setDisplayedError(null);
    },
    refresh: refreshNoop,
  };
}
