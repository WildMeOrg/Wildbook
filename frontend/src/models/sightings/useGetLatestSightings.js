// import * as Sentry from '@sentry/react';
import queryKeys from "../../constants/queryKeys";
import useFetch from "../../hooks/useFetch";

export default function useGetLatestSightings() {
  //   function onSuccess(response) {
  //     if (!__DEV__) {
  //       Sentry.setUser({
  //         email: get(response, ['data', 'email']),
  //         id: get(response, ['data', 'guid']),
  //         username: get(response, ['data', 'full_name']),
  //       });
  //     }
  //   }

  return useFetch({
    queryKey: queryKeys.me,
    url: "/latestSightings",
    // onSuccess,
    queryOptions: { retry: false },
  });
}
