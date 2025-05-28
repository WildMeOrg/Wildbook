import { client } from "../../api/client";
import { useQuery } from "react-query";

export default function useGetBulkImportTask(taskId) {
  const fetchTask = async () => {
    const { data } = await client.get(`/bulk-import/task/${taskId}`);
    return data;
  };

  const {
    data: task = [],
    isLoading,
    error,
    refetch,
  } = useQuery(["bulkImportTask", taskId], fetchTask, {
    enabled: Boolean(taskId),
    refetchOnWindowFocus: false,
    retry: false,
    select: (d) => d ?? [],
  });

  return { task, isLoading, error, refetch };
}
