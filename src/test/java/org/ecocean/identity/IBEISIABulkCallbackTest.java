package org.ecocean.identity;

import static org.junit.jupiter.api.Assertions.*;

import org.ecocean.ia.Task;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class IBEISIABulkCallbackTest {

    @Test void nonBulkTaskHasNullImportTaskId() {
        Task task = new Task();
        assertNull(IBEISIA.getImportTaskId(task));
        assertFalse(IBEISIA.isBulkImportParentTask(task));
    }

    @Test void bulkTaskIsDetected() {
        Task task = new Task();
        JSONObject params = new JSONObject();
        params.put("importTaskId", "it-uuid-1");
        task.setParameters(params);
        assertEquals("it-uuid-1", IBEISIA.getImportTaskId(task));
        assertTrue(IBEISIA.isBulkImportParentTask(task));
    }

    @Test void emptyImportTaskIdIsNotBulk() {
        Task task = new Task();
        JSONObject params = new JSONObject();
        params.put("importTaskId", "");
        task.setParameters(params);
        assertNull(IBEISIA.getImportTaskId(task));
        assertFalse(IBEISIA.isBulkImportParentTask(task));
    }

    @Test void nullTaskReturnsNull() {
        assertNull(IBEISIA.getImportTaskId(null));
        assertFalse(IBEISIA.isBulkImportParentTask(null));
    }

    @Test void taskWithNoParametersIsNotBulk() {
        Task task = new Task();
        assertNull(IBEISIA.getImportTaskId(task));
        assertFalse(IBEISIA.isBulkImportParentTask(task));
    }
}
