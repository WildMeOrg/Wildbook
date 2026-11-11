package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ecocean.Annotation;
import org.ecocean.Embedding;
import org.ecocean.media.Feature;
import org.ecocean.media.FeatureType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Bulk-import boxes PR A (spec 4.4): a boxed asset counts as finished for the visibility gate
 * once every box on it that has an extraction task is extracted or errored. Boxes with no
 * extraction task (a WBIA-only species sharing the photo) and unmarked annotations are ignored.
 */
class ImportedBoxesSettleTest {
    private static Annotation box(String id, boolean marked) {
        JSONObject params = new JSONObject().put("x", 1).put("y", 1).put("width", 5).put("height", 5);
        if (marked) params.put(Annotation.FEATURE_PARAM_IMPORTED_BOX, new JSONObject().put("importTaskId", "imp"));
        Annotation a = new Annotation(null, new Feature(new FeatureType("org.ecocean.boundingBox"), params), "body");
        a.setId(id);
        return a;
    }

    private static Task importedTask(String status) {
        Task t = new Task();
        t.addParameter(Task.PARAM_IMPORTED_BOX, true);
        t.setStatus(status);
        return t;
    }

    private static Annotation withEmbedding(Annotation a) {
        new Embedding(a, "m", "1", new JSONArray().put(0.1).put(0.2));
        return a;
    }

    @Test void settledWhenEveryTrackedBoxHasAnEmbeddingOrATerminalTask() {
        Annotation a = box("A", true), b = withEmbedding(box("B", true)), c = box("C", true);
        Map<String, List<Task>> tasks = new HashMap<String, List<Task>>();
        tasks.put("A", Arrays.asList(importedTask("processing")));
        tasks.put("B", Arrays.asList(importedTask("processing")));
        tasks.put("C", Arrays.asList(importedTask("error")));
        assertTrue(ImportedBoxes.allSettled(Arrays.asList(a, b, c), "A", ann -> tasks.get(ann.getId())));
    }

    @Test void notSettledWhileATrackedBoxIsPending() {
        Annotation a = box("A", true), c = box("C", true);
        Map<String, List<Task>> tasks = new HashMap<String, List<Task>>();
        tasks.put("A", Arrays.asList(importedTask("processing")));
        tasks.put("C", Arrays.asList(importedTask("processing")));
        assertFalse(ImportedBoxes.allSettled(Arrays.asList(a, c), "A", ann -> tasks.get(ann.getId())));
    }

    @Test void boxesWithoutAnExtractionTaskDoNotBlockSettle() {
        Annotation a = box("A", true), d = box("D", true);
        Task plain = new Task();
        plain.setStatus("initiated");
        Map<String, List<Task>> tasks = new HashMap<String, List<Task>>();
        tasks.put("A", Arrays.asList(importedTask("processing")));
        tasks.put("D", Arrays.asList(plain));
        assertTrue(ImportedBoxes.allSettled(Arrays.asList(a, d), "A", ann -> tasks.get(ann.getId())));
    }

    @Test void unmarkedAnnotationsAreIgnored() {
        Annotation trivial = box("T", false);
        assertTrue(ImportedBoxes.allSettled(Arrays.asList(trivial), null, ann -> Collections.<Task>emptyList()));
        assertTrue(ImportedBoxes.allSettled(Collections.<Annotation>emptyList(), null, ann -> null));
        assertTrue(ImportedBoxes.allSettled(null, null, ann -> null));
    }

    @Test void aPendingBoxWithNullTasksIsNotTracked() {
        Annotation a = box("A", true), e = box("E", true);
        Map<String, List<Task>> tasks = new HashMap<String, List<Task>>();
        tasks.put("A", Arrays.asList(importedTask("processing")));
        assertTrue(ImportedBoxes.allSettled(Arrays.asList(a, e), "A", ann -> tasks.get(ann.getId())));
    }
}
