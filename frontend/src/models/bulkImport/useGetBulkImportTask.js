import { client } from "../../api/client";
import { useQuery } from "@tanstack/react-query";

export default function useGetBulkImportTask(taskId) {
  const fetchTask = async () => {
    const response = await client.get(`/bulk-import/task/${taskId}`);
    return response.data;
  };

  const {
    data: task,
    isLoading,
    error,
    refresh,
  } = useQuery(["bulkImportTask", taskId], fetchTask, {
    enabled: !!taskId,
    refetchOnWindowFocus: false,
    retry: false,
  });

  return { task, isLoading, error, refresh };
}
