package org.ecocean.ia;

import java.util.List;
import java.util.function.Function;

import org.ecocean.Annotation;
import org.ecocean.identity.IBEISIA;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;

/**
 * Bulk-import boxes PR A (spec 4.4): helpers shared by {@link IA}'s extraction intake and
 * {@link MlServiceProcessor}'s extraction lifecycle.
 *
 * <p>A boxed asset is imported with its detectionStatus unset, so the unchanged
 * {@link MatchVisibilityGate} treats it as unfinished. It is settled to {@code complete} here,
 * once every box on it that has an extraction task is extracted or errored; the gate then lets
 * every sibling box's match proceed. Boxes with no extraction task (a WBIA-only species sharing
 * the photo) and unmarked annotations never block settling.</p>
 */
public final class ImportedBoxes {
    private ImportedBoxes() {}

    /**
     * Pure predicate. {@code doneAnnId} is the box whose extraction just finished in the calling
     * transaction (its embedding may not be visible through the annotation yet); {@code tasksFor}
     * answers the tasks that hold a given annotation.
     */
    public static boolean allSettled(List<Annotation> annsOnAsset, String doneAnnId,
        Function<Annotation, List<Task>> tasksFor) {
        if (annsOnAsset == null) return true;
        for (Annotation ann : annsOnAsset) {
            if ((ann == null) || !ann.isImportedBox()) continue;
            List<Task> tasks = tasksFor.apply(ann);
            boolean tracked = false;
            boolean terminal = false;
            if (tasks != null) {
                for (Task t : tasks) {
                    if (!Task.isImportedBoxTask(t)) continue;
                    tracked = true;
                    if (t.statusInEndState()) terminal = true;
                }
            }
            if (!tracked) continue;
            if (terminal) continue;
            if ((doneAnnId != null) && doneAnnId.equals(ann.getId())) continue;
            if (ann.numberEmbeddings() > 0) continue;
            return false;
        }
        return true;
    }

    /** Marks the asset detection-complete once {@link #allSettled}; idempotent; null-safe. */
    public static void settle(Shepherd shep, MediaAsset ma, String doneAnnId) {
        if (ma == null) return;
        if (IBEISIA.STATUS_COMPLETE.equals(ma.getDetectionStatus())) return;
        // Task.getTasksFor queries with ignoreCache=true, and DataNucleus then refreshes the result
        // objects from the datastore: an unflushed status write on a sibling's task (the error
        // mark made a moment ago in this transaction) would be overwritten. Flush first; a flush
        // failure propagates, because querying after it would re-expose that refresh.
        shep.getPM().flush();
        if (!allSettled(ma.getAnnotations(), doneAnnId, ann -> Task.getTasksFor(ann, shep))) return;
        ma.setDetectionStatus(IBEISIA.STATUS_COMPLETE);
        System.out.println("INFO: ImportedBoxes.settle() asset " + ma.getId() +
            " complete: every imported box on it is extracted or errored");
    }

    /** Terminal error on an imported-box child, then settle the asset it holds (if any). */
    public static void failChild(Shepherd shep, Task child, String code, String message) {
        if (child == null) return;
        IA.markTaskError(child, code, message);
        List<MediaAsset> held = child.getObjectMediaAssets();
        if ((held != null) && !held.isEmpty()) settle(shep, held.get(0), null);
    }
}
