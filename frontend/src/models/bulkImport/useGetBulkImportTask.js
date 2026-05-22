import { client } from "../../api/client";
import { useQuery } from "react-query";

// Whitelist of statuses that mean "work is in flight — keep polling."
// Anything else (terminal, unknown, missing) stops polling. Chose a
// whitelist over a terminal-blacklist because the backend emits a
// somewhat open-ended set of statuses (e.g. ImportTask.java emits
// "complete", "sent", "skipped", "identification not started",
// "unknown"; BulkImport.java overlays "processing-pipeline" at the
// task level) and the safe failure mode is "stop polling on an
// unrecognized status" rather than "poll forever waiting for a
// status string we'll never recognize."
const IN_FLIGHT_STATUSES = new Set([
  // Task-level transient states (see BulkImport.java:892,
  // ImportTask.java)
  "processing-pipeline",
  // IA phase transient state — queued/running, not yet terminal
  // (ImportTask.java:689, :711, :726)
  "sent",
]);

const isInFlight = (status) => IN_FLIGHT_STATUSES.has(status);

export default function useGetBulkImportTask(taskId) {
  const fetchTask = async () => {
    const { data } = await client.get(`/bulk-import/${taskId}`);
    return data;
  };

  const { data, isLoading, error, refetch } = useQuery(
    ["bulkImportTask", taskId],
    fetchTask,
    {
      enabled: Boolean(taskId),
      refetchOnWindowFocus: false,
      retry: false,
      select: (d) => d ?? [],
      // Poll every 5s while ANY of the three observable phase
      // statuses (task.status, iaSummary.detectionStatus,
      // iaSummary.identificationStatus) is in flight. Stop polling
      // once none of them is in flight. The IA phases run after task
      // upload completes (BulkImportTask.jsx:498 gates the re-ID
      // button on both task.status and iaSummary.detectionStatus
      // being complete), so we cannot stop on task.status alone.
      refetchInterval: (response) => {
        // First load not complete yet — react-query passes `undefined`
        // before the first successful response. Poll so we pick up
        // the initial state.
        if (response === undefined) return 5000;
        // Response came back but no task — backend returned an empty
        // or malformed body (e.g. the task id is invalid or was
        // deleted). Stop polling; re-polling cannot make the task
        // re-materialize, and infinite polling on a 200-with-no-task
        // would hammer the API.
        const task = response?.task;
        if (!task) return false;
        if (isInFlight(task.status)) return 5000;
        if (isInFlight(task.iaSummary?.detectionStatus)) return 5000;
        if (isInFlight(task.iaSummary?.identificationStatus)) return 5000;
        return false;
      },
      // Pause polling when the browser tab is hidden. Polling resumes
      // on the next scheduled interval after the tab regains focus
      // (refetchOnWindowFocus stays false so we do not double-fetch
      // the instant the tab is restored).
      refetchIntervalInBackground: false,
    },
  );

  const task = data?.task || {};

  return { task, isLoading, error, refetch };
}
