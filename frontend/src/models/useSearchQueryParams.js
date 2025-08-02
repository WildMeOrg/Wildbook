import { useMemo } from "react";
import { useSearchParams } from "react-router-dom";

export function useSearchQueryParams() {
  const [searchParams] = useSearchParams();
  return useMemo(
    () => Object.fromEntries(searchParams.entries()) || {},
    [searchParams],
  );
}
