// import * as Sentry from '@sentry/react';
import queryKeys from "../../../constants/queryKeys";
import useFetch from "../../../hooks/useFetch";

export default function useGetMe() {
  return useFetch({
    queryKey: queryKeys.me,
    url: "/user",
    // onSuccess,
    queryOptions: { retry: false },
  });
}
