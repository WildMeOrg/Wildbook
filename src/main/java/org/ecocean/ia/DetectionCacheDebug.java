package org.ecocean.ia;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Query;
import javax.jdo.datastore.DataStoreCache;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.media.Feature;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdPMF;

/*
 * Diagnostic helper for the "annotations don't appear after detection" bug.
 *
 * Logs the JDO state of objects touched by detection at three vantage points
 * (in-memory same-shepherd, fresh-shepherd-with-L2, fresh-shepherd-after-L2-evict)
 * plus a direct SQL probe of the FEATURE table, then performs a scoped L2 evict
 * for the affected objects.
 *
 * All log lines are prefixed [DETECT-DIAG] for easy grep.
 */
public class DetectionCacheDebug {
    private static final String TAG = "[DETECT-DIAG]";

    public static void logEncounterGraph(String label, Shepherd shepherd,
        Collection<String> encIds) {
        if (shepherd == null) {
            System.out.println(TAG + " " + label + " shepherd is null, skipping");
            return;
        }
        if (encIds == null || encIds.isEmpty()) {
            System.out.println(TAG + " " + label + " no encounter IDs to log");
            return;
        }
        System.out.println(TAG + " ==== BEGIN " + label + " (thread=" +
            Thread.currentThread().getName() + ") ====");
        for (String encId : encIds) {
            try {
                Encounter enc = shepherd.getEncounter(encId);
                if (enc == null) {
                    System.out.println(TAG + " " + label + " enc=" + encId + " NOT FOUND");
                    continue;
                }
                List<Annotation> anns = enc.getAnnotations();
                List<MediaAsset> media = enc.getMedia();
                System.out.println(TAG + " " + label + " enc=" + encId +
                    " anns.size=" + (anns == null ? -1 : anns.size()) +
                    " annIds=" + collectAnnIds(anns) +
                    " media.size=" + (media == null ? -1 : media.size()) +
                    " mediaIds=" + collectMaIds(media));
                if (media != null) {
                    for (MediaAsset ma : media) {
                        if (ma == null) continue;
                        List<Feature> feats = ma.getFeatures();
                        List<Annotation> maAnns = ma.getAnnotations();
                        System.out.println(TAG + " " + label + "   ma=" + ma.getIdInt() +
                            " feats.size=" + (feats == null ? -1 : feats.size()) +
                            " featIds=" + collectFeatIds(feats) +
                            " ma.getAnnotations.size=" + (maAnns == null ? -1 : maAnns.size()) +
                            " ma.getAnnotations.ids=" + collectAnnIds(maAnns) +
                            " detectionStatus=" + ma.getDetectionStatus());
                        if (feats != null) {
                            for (Feature ft : feats) {
                                if (ft == null) continue;
                                Annotation backAnn = ft.getAnnotation();
                                MediaAsset backMa = ft.getMediaAsset();
                                System.out.println(TAG + " " + label + "     feat=" + ft.getId() +
                                    " feat.getAnnotation=" +
                                    (backAnn == null ? "NULL" : backAnn.getId()) +
                                    " feat.getMediaAsset=" +
                                    (backMa == null ? "NULL" : Integer.toString(backMa.getIdInt())));
                            }
                        }
                    }
                }
                if (anns != null) {
                    for (Annotation ann : anns) {
                        if (ann == null) continue;
                        List<Feature> feats = ann.getFeatures();
                        System.out.println(TAG + " " + label + "   ann=" + ann.getId() +
                            " ann.getFeatures.size=" + (feats == null ? -1 : feats.size()) +
                            " ann.getFeatures.ids=" + collectFeatIds(feats));
                    }
                }
            } catch (Exception ex) {
                System.out.println(TAG + " " + label + " enc=" + encId + " EXCEPTION " + ex);
                ex.printStackTrace();
            }
        }
        System.out.println(TAG + " ==== END " + label + " ====");
    }

    /*
     * Direct SQL probe of FEATURE table — DB ground truth, bypassing JDO/L2 entirely.
     * Logs ANNOTATION_ID and ASSET_ID for each feature ID provided.
     */
    public static void sqlProbeFeatures(String context, Collection<String> featureIds) {
        if (featureIds == null || featureIds.isEmpty()) {
            System.out.println(TAG + " sqlProbeFeatures: no feature IDs");
            return;
        }
        System.out.println(TAG + " ==== BEGIN sqlProbeFeatures (DB ground truth) ====");
        Shepherd shepherd = new Shepherd(context);
        shepherd.setAction("DetectionCacheDebug.sqlProbeFeatures");
        shepherd.beginDBTransaction();
        try {
            for (String fid : featureIds) {
                if (fid == null) continue;
                String sql =
                    "SELECT \"ID\", \"ANNOTATION_ID\", \"ASSET_ID\" FROM \"FEATURE\" WHERE \"ID\" = ?";
                Query q = shepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
                try {
                    @SuppressWarnings("unchecked")
                    List<Object[]> rows = (List<Object[]>)q.executeWithArray(fid);
                    if (rows == null || rows.isEmpty()) {
                        System.out.println(TAG + " sql feat=" + fid + " NOT FOUND IN DB");
                    } else {
                        for (Object[] row : rows) {
                            System.out.println(TAG + " sql feat=" + row[0] +
                                " ANNOTATION_ID=" + row[1] +
                                " ASSET_ID=" + row[2]);
                        }
                    }
                } finally {
                    q.closeAll();
                }
            }
        } catch (Exception ex) {
            System.out.println(TAG + " sqlProbeFeatures EXCEPTION " + ex);
            ex.printStackTrace();
        } finally {
            shepherd.rollbackAndClose();
        }
        System.out.println(TAG + " ==== END sqlProbeFeatures ====");
    }

    /*
     * Scoped L2 cache eviction for the touched objects. Uses pm.newObjectIdInstance
     * to construct OIDs from class+PK and evicts each.
     */
    public static void scopedEvict(String context,
        Collection<String> encIds, Collection<Integer> maIds,
        Collection<String> annIds, Collection<String> featureIds) {
        PersistenceManagerFactory pmf = ShepherdPMF.getPMF(context);
        if (pmf == null) {
            System.out.println(TAG + " scopedEvict: pmf is null");
            return;
        }
        DataStoreCache cache = pmf.getDataStoreCache();
        PersistenceManager pm = pmf.getPersistenceManager();
        int evicted = 0;
        try {
            evicted += evictAll(pm, cache, Encounter.class, encIds);
            evicted += evictAll(pm, cache, MediaAsset.class, maIds);
            evicted += evictAll(pm, cache, Annotation.class, annIds);
            evicted += evictAll(pm, cache, Feature.class, featureIds);
        } finally {
            pm.close();
        }
        System.out.println(TAG + " scopedEvict evicted " + evicted + " OIDs from L2");
    }

    private static int evictAll(PersistenceManager pm, DataStoreCache cache, Class<?> cls,
        Collection<?> ids) {
        if (ids == null) return 0;
        int n = 0;
        for (Object id : ids) {
            if (id == null) continue;
            try {
                Object oid = pm.newObjectIdInstance(cls, id);
                cache.evict(oid);
                n++;
            } catch (Exception ex) {
                System.out.println(TAG + " evict failed cls=" + cls.getSimpleName() +
                    " id=" + id + " ex=" + ex);
            }
        }
        return n;
    }

    /*
     * Full post-commit diagnostic: log fresh-shepherd state, SQL probe, scoped evict,
     * then re-log fresh-shepherd state. Run from IBEISIA.processCallback after commit.
     */
    public static void runPostCommitDiagnostics(String context,
        Collection<String> encIds, Collection<Integer> maIds,
        Collection<String> annIds, Collection<String> featureIds) {
        System.out.println(TAG + " >>>>>>>>>> postCommit diagnostics begin" +
            " encs=" + encIds + " mas=" + maIds + " anns=" + annIds + " feats=" + featureIds);

        // 1. Log state from fresh shepherd (may hit L2 cache)
        Shepherd s1 = new Shepherd(context);
        s1.setAction("DetectionCacheDebug.postCommit_freshShepherd_L2active");
        s1.beginDBTransaction();
        try {
            logEncounterGraph("post-commit fresh-shepherd L2-ACTIVE", s1, encIds);
        } finally {
            s1.rollbackAndClose();
        }

        // 2. SQL probe the FEATURE table directly (bypass JDO entirely)
        sqlProbeFeatures(context, featureIds);

        // 3. Scoped L2 eviction for the touched OIDs
        scopedEvict(context, encIds, maIds, annIds, featureIds);

        // 4. Re-log from a brand new shepherd (post-evict — L2 is empty for these OIDs)
        Shepherd s2 = new Shepherd(context);
        s2.setAction("DetectionCacheDebug.postCommit_freshShepherd_L2evicted");
        s2.beginDBTransaction();
        try {
            logEncounterGraph("post-commit fresh-shepherd L2-EVICTED", s2, encIds);
        } finally {
            s2.rollbackAndClose();
        }

        System.out.println(TAG + " <<<<<<<<<< postCommit diagnostics end");
    }

    private static String collectAnnIds(Collection<Annotation> anns) {
        if (anns == null) return "null";
        List<String> ids = new ArrayList<String>();
        for (Annotation a : anns) ids.add(a == null ? "null" : a.getId());
        return ids.toString();
    }

    private static String collectMaIds(Collection<MediaAsset> mas) {
        if (mas == null) return "null";
        List<String> ids = new ArrayList<String>();
        for (MediaAsset m : mas) ids.add(m == null ? "null" : Integer.toString(m.getIdInt()));
        return ids.toString();
    }

    private static String collectFeatIds(Collection<Feature> feats) {
        if (feats == null) return "null";
        List<String> ids = new ArrayList<String>();
        for (Feature f : feats) ids.add(f == null ? "null" : f.getId());
        return ids.toString();
    }
}
