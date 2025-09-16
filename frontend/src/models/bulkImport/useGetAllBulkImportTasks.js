import queryKeys from "../../constants/queryKeys";
import useFetch from "../../hooks/useFetch";

export default function useGetSiteSettings() {
  return useFetch({
    queryKey: queryKeys.bulkImportTasks,
    url: "/bulk-import/sourceNames",
    queryOptions: { retry: true },
  });
}
