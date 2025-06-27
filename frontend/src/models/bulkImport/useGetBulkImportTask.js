import { client } from "../../api/client";
import { useQuery } from "react-query";

export default function useGetBulkImportTask(taskId) {
  const fetchTask = async () => {
    const { data } = await client.get(`/bulk-import/${taskId}`);
    return data;
  };

  const {
    data,
    isLoading,
    error,
    refetch,
  } = useQuery(["bulkImportTask", taskId], fetchTask, {
    enabled: Boolean(taskId),
    refetchOnWindowFocus: false,
    retry: false,
    select: (d) => d ?? [],
  });

  const task = data?.task || {};

  return { task, isLoading, error, refetch };
}
