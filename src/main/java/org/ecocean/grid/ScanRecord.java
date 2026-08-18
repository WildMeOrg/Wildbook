package org.ecocean.grid;

import java.io.Serializable;

/**
 * One immutable row per completed Modified Groth (+ I3S) scan run, written by
 * GrothScanRunnable at the end of each run so MetricsBot can report spot-pattern
 * matching activity in /metrics (wildbook_tasks_modifiedGroth, wildbook_tasks_i3s,
 * and the wildbook_identification_tasks_completed_last24 aggregate).
 *
 * ScanTask rows cannot serve this purpose: their id is scan{L|R}{encounterNumber},
 * one reused row per encounter side, reset on every rerun — so they count distinct
 * encounter sides ever scanned, not scan runs. ScanRecord is row-per-run.
 *
 * This is best-effort telemetry: the result XML files on disk and this row cannot
 * be written atomically, so a JVM crash between the two can under- or over-count a
 * single run. Failure to persist a record is logged and dropped — it must never
 * block scan completion.
 */
public class ScanRecord implements Serializable {
    static final long serialVersionUID = 1L;

    private String id;
    private String taskID;
    private String encounterNumber;
    private boolean rightSide;
    private long startTime = -1;
    private long endTime = -1;
    private boolean grothSuccess = false;
    private boolean i3sSuccess = false;

    /**
     * empty constructor required by the JDO enhancer. DO NOT USE.
     */
    public ScanRecord() {}

    /**
     * @param id           pre-generated UUID for this run — generated ONCE per run so
     *                     retried inserts stay idempotent (the id is the primary key)
     * @param taskID       the (reused) ScanTask id, e.g. scanL12345
     * @param grothSuccess the Groth result XML was written successfully
     * @param i3sSuccess   the I3S result XML was written successfully
     */
    public ScanRecord(String id, String taskID, String encounterNumber, boolean rightSide,
        long startTime, long endTime, boolean grothSuccess, boolean i3sSuccess) {
        this.id = id;
        this.taskID = taskID;
        this.encounterNumber = encounterNumber;
        this.rightSide = rightSide;
        this.startTime = startTime;
        this.endTime = endTime;
        this.grothSuccess = grothSuccess;
        this.i3sSuccess = i3sSuccess;
    }

    public String getId() {
        return id;
    }

    public String getTaskID() {
        return taskID;
    }

    public String getEncounterNumber() {
        return encounterNumber;
    }

    public boolean isRightSide() {
        return rightSide;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public boolean isGrothSuccess() {
        return grothSuccess;
    }

    public boolean isI3sSuccess() {
        return i3sSuccess;
    }
}
