package org.ecocean;

import java.lang.Runnable;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.Collection;
import java.util.List;
import javax.jdo.Query;
import org.ecocean.identity.IBEISIA;
import org.ecocean.media.Feature;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;

/*
 * Wildbook requires shared UUIDs (a.k.a. acmID) between MediaAsset objects in the Wildbook database and images in WBIA. AcmIDs are a prerequisite for
 * detection and therefore can be a blocker in the IA pipeline if for any reason WBIA times out or is otherwise unavailable to provide an acmId to
 * Wildbook when new data is submitted. This bot provides some automated backend healing to get images registered if for any reason acmId registration
 * fails. It first checks bulk ImportTasks for appropriate images that may be missing an acmId (fast path for fresh imports), and then runs one
 * page of a continuous reconciliation sweep: every MediaAsset backing a matchAgainst annotation is eventually probed against WBIA
 * (/api/image/rowid/uuid/) and re-registered if WBIA does not know its acmId.
 *
 */
public class AcmIdBot {
    private static void fixFeats(List<Feature> feats, Shepherd myShepherd, String summaryMessage,
                                 int maxFixes) {
        if (feats != null && feats.size() > 0) {
            int numRecommended = feats.size();
            int numValidIAFixes = 0;
            int numAcmIdFixesSent = 0;
            int numAcmIdFixesSuccessful = 0;
            int numInvalidForIA = 0;
            for (Feature feat : feats) {
                MediaAsset asset = feat.getMediaAsset();
                myShepherd.setAction("AcmIDBot_" + summaryMessage + "_asset_" + asset.getId());
                try {
                    // is this an appropriate image type for acm ID registration?
                    if (asset != null && asset.isValidImageForIA() == null) {
                        asset.validateSourceImage();
                        myShepherd.updateDBTransaction();
                        numValidIAFixes++;
                        if (!asset.isValidImageForIA()) numInvalidForIA++;
                    }
                    // if appropriate let's send it
                    if (asset != null && asset.isValidImageForIA()) {
                        // let's check for child media assets - lack of these could impact acmID registration
                        if (!asset.hasFamily(myShepherd)) asset.updateStandardChildren();
                        ArrayList<MediaAsset> fixMe = new ArrayList<MediaAsset>();
                        fixMe.add(asset);
                        IBEISIA.sendMediaAssetsNew(fixMe, myShepherd.getContext());
                        numAcmIdFixesSent++;
                        // commit now: rectifyMediaAssetIds set acmId on this asset, and
                        // fixAcmIds() ends with rollbackAndClose() which would discard it
                        myShepherd.updateDBTransaction();
                        if (asset.getAcmId() != null) {
                            numAcmIdFixesSuccessful++;
                            // allow the bot to determine how many fixes it wants the logic to consider before exiting
                            // helps keep the bots attention back on newer data
                            if (numAcmIdFixesSuccessful >= maxFixes) break;
                        }
                    }
                } catch (Exception ec) {
                    System.out.println("Exception in AcmIdBot.fixFeats");
                    ec.printStackTrace();
                    // as of now we don't know of a commonality that would suggest a fix
                    if (ec.toString().contains("HTTP error code : 500")) {
                        asset.setIsValidImageForIA(false);
                        myShepherd.updateDBTransaction();
                        numValidIAFixes--;
                        numInvalidForIA++;
                    }
                }
            }
            System.out.println(summaryMessage);
            System.out.println("...candidate fixes: " + numRecommended);
            System.out.println("......num valid for IA checks performed: " + numValidIAFixes);
            System.out.println(".........num ultimately invalid for IA: " + numInvalidForIA);
            System.out.println("......num media assets sent for ACM ID fixing: " +
                numAcmIdFixesSent);
            System.out.println("......num media assets successfully updated with Acm ID: " +
                numAcmIdFixesSuccessful);
        }
    }

    // ------- reconciliation sweep (spec: 2026-07-01-acmidbot-reconciliation-sweep-design.md) -------

    static final int SWEEP_PAGE_SIZE = 10000; // distinct assets examined per 15-minute run
    static final int PAGE_FAIL_LIMIT = 3; // failed runs on one page before skipping it
    // in-memory sweep state; a restart just restarts the sweep (probes are cheap)
    static int sweepCursor = 0; // highest asset id processed
    static int sweepFailCount = 0; // consecutive failures at the current cursor

    // one page of sweep candidates, reduced to primitives so no JDO object
    // outlives the read transaction
    static class SweepPage {
        final List<Integer> nullAcmAssetIds = new ArrayList<Integer>();
        final java.util.LinkedHashMap<String, Integer> acmIdToAssetId =
            new java.util.LinkedHashMap<String, Integer>();
        boolean rawExhausted = false;
        int lastAssetId = -1; // -1 = empty page
    }

    // walk assets (ascending id order, may contain duplicates from multiple
    // Features per asset) collecting up to pageSize distinct assets.
    // rawExhausted is true only when the input ran out — never inferred from
    // a post-dedup count (spec §1/§2).
    static SweepPage collectSweepPage(java.util.Iterator<MediaAsset> assetsInIdOrder,
        int pageSize) {
        SweepPage page = new SweepPage();
        java.util.Set<Integer> seen = new java.util.HashSet<Integer>();

        page.rawExhausted = true;
        while (assetsInIdOrder.hasNext()) {
            MediaAsset asset = assetsInIdOrder.next();
            if (asset == null) continue;
            // NOTE: MediaAsset.getId() returns String; getIdInt() is the int accessor
            if (seen.contains(asset.getIdInt())) continue;
            if (seen.size() >= pageSize) {
                page.rawExhausted = false;
                return page;
            }
            seen.add(asset.getIdInt());
            page.lastAssetId = asset.getIdInt();
            // known-invalid assets are unhealable: counted as processed, never probed
            if (asset.isValidImageForIA() != null && !asset.isValidImageForIA()) continue;
            // null or malformed acmId: route straight to the heal path rather than
            // the probe, since a non-UUID value would make WBIA reject the whole
            // probe chunk and could strand the rest of the page (see Fix B)
            if (asset.getAcmId() == null || !Util.isUUID(asset.getAcmId())) {
                page.nullAcmAssetIds.add(asset.getIdInt());
            } else {
                Integer displaced = page.acmIdToAssetId.put(asset.getAcmId(), asset.getIdInt());
                if (displaced != null) {
                    System.out.println("WARNING: AcmIdBot sweep found duplicate acmId " +
                        asset.getAcmId() + " on assets " + displaced + " and " +
                        asset.getIdInt() + "; only the latter will be probed/healed this sweep");
                }
            }
        }
        return page;
    }

    // cursor policy (spec §2): maxFixes clamp wins (resume mid-page next run),
    // else wrap to 0 on true exhaustion, else advance past the page
    static int nextCursorAfterSuccess(SweepPage page, boolean maxFixesHit, int resumeAssetId) {
        if (maxFixesHit) return resumeAssetId;
        if (page.rawExhausted) return 0;
        return page.lastAssetId;
    }

    // poisoned-page guard (spec §5): after PAGE_FAIL_LIMIT consecutive failures
    // on the same page, skip it so one bad page cannot stall the sweep forever
    static boolean shouldSkipPoisonedPage(int consecutiveFailures) {
        return consecutiveFailures >= PAGE_FAIL_LIMIT;
    }

    // did a sendMediaAssetsNew() round-trip actually register this acmId with
    // WBIA? add_images_json returns the registered image UUIDs; require ours
    // among them before counting (and committing) a heal — otherwise a locally
    // pre-assigned acmId could be persisted although WBIA never registered it
    static boolean sendConfirmedAcmId(org.json.JSONObject sendRtn, String expectedAcmId) {
        if ((sendRtn == null) || (expectedAcmId == null)) return false;
        org.json.JSONArray batches = sendRtn.optJSONArray("batchResults");
        if (batches == null) return false;
        for (int b = 0; b < batches.length(); b++) {
            org.json.JSONObject rtn = batches.optJSONObject(b);
            if (rtn == null) continue; // "EMPTY BATCH" marker string
            org.json.JSONArray resp = rtn.optJSONArray("response");
            if (resp == null) continue;
            for (int i = 0; i < resp.length(); i++) {
                org.json.JSONObject fancy = resp.optJSONObject(i);
                if ((fancy != null) &&
                    expectedAcmId.equals(fancy.optString("__UUID__", null))) return true;
            }
        }
        return false;
    }

    // background workers
    private static ScheduledExecutorService schedExec = null;

    public static synchronized boolean startServices(String context) {
        if ((schedExec != null) && !schedExec.isShutdown()) {
            System.out.println("WARNING: AcmIdBot.startServices(" + context +
                ") called but collector already running; ignoring");
            return false;
        }
        startCollector(context);
        return true;
    }

    // basically our "listener" daemon; but is more pull (poll?) than push so to speak.
    private static void startCollector(final String context) { // throws IOException {
        long interval = 15; // number minutes between runs
        long initialDelay = 1; // number minutes before first execution occurs

        System.out.println("+ AcmIdBot.startCollector(" + context + ") starting.");
        schedExec = Executors.newScheduledThreadPool(1);
        final ScheduledExecutorService execRef = schedExec;
        final ScheduledFuture schedFuture = execRef.scheduleWithFixedDelay(new Runnable() {
            // DO WORK HERE
            public void run() {
                if (new java.io.File("/tmp/WB_AcmIdBot_SHUTDOWN").exists()) {
                    System.out.println("INFO: AcmIdBot.startCollection(" + context +
                    ") shutting down due to file signal");
                    execRef.shutdown();
                    return;
                }
                fixAcmIds(context);
            }
        }, initialDelay, // initial delay
            interval, // period delay *after* execution finishes
            TimeUnit.MINUTES); // unit of delays above

        System.out.println("Let's get AcmIdBot's time running.");
        try {
            execRef.awaitTermination(5000, TimeUnit.MILLISECONDS);
        } catch (java.lang.InterruptedException ex) {
            System.out.println("WARNING: AcmIdBot.startCollector(" + context + ") interrupted: " +
                ex.toString());
        }
        System.out.println("+ AcmIdBot.startCollector(" + context + ") backgrounded");
    }

    // called from StartupWildbook contextDestroyed
    public static synchronized void cleanup() {
        if (schedExec != null) {
            schedExec.shutdown();
            schedExec = null;
        }
        System.out.println(
            "================ = = = = = = ===================== AcmIdBot.cleanup() finished.");
    }

    // fixAcmIds is public and mutates static sweep state; guard against a
    // concurrent manual invocation racing the scheduled run
    private static final java.util.concurrent.atomic.AtomicBoolean botRunning =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    public static void fixAcmIds(String context) {
        if (!botRunning.compareAndSet(false, true)) {
            System.out.println(
                "WARNING: AcmIdBot.fixAcmIds() already running; skipping this invocation");
            return;
        }
        try {
            Shepherd myShepherd = new Shepherd(context);

            myShepherd.setAction("AcmIdBot.java");
            myShepherd.beginDBTransaction();

            // number of fixes to consider before finishing and letting a new round of work restart the effort
            int maxFixes = 500;
            Query query2 = null;
            try {
                System.out.println(
                    "Looking for complete import tasks with media assets with missing acmIds");

                String filter2 =
                    "select from org.ecocean.media.Feature where itask.status == 'complete' && itask.encounters.contains(enc) && enc.annotations.contains(annot) && annot.features.contains(this) && asset.acmId == null VARIABLES org.ecocean.Encounter enc;org.ecocean.servlet.importer.ImportTask itask;org.ecocean.Annotation annot";
                query2 = myShepherd.getPM().newQuery(filter2);
                query2.setOrdering("revision desc");
                Collection c2 = (Collection)(query2.execute());
                List<Feature> feats = new ArrayList<Feature>(c2);
                query2.closeAll();
                query2 = null;  // Mark as closed

                fixFeats(feats, myShepherd, "ACM ID ImportTask fixing summary", maxFixes);
            } catch (Exception f) {
                System.out.println("Exception in AcmIdBot!");
                f.printStackTrace();
            } finally {
                if (query2 != null) query2.closeAll();
                myShepherd.rollbackAndClose();
            }

            // full reconciliation sweep of the matchable set (replaces the old
            // 24-hour Encounter query); manages its own transactions
            sweepMatchableAssets(context, maxFixes);
        } finally {
            botRunning.set(false);
        }
    }

    /**
     * One 15-minute bite of the reconciliation sweep (spec
     * docs/superpowers/specs/2026-07-01-acmidbot-reconciliation-sweep-design.md).
     * Phase-separated: (1) read one page of distinct matchable assets inside a
     * short transaction, reduced to primitives; (2) probe WBIA for unknown
     * acmIds over HTTP with no transaction open; (3) heal missing assets in a
     * fresh transaction with explicit commits so rectified acmIds survive
     * rollbackAndClose.
     */
    static void sweepMatchableAssets(String context, int maxFixes) {
        // ---- read phase ----
        SweepPage page = null;
        Shepherd readShepherd = new Shepherd(context);

        readShepherd.setAction("AcmIdBot.sweepRead");
        readShepherd.beginDBTransaction();
        Query query = null;
        try {
            System.out.println("AcmIdBot sweep: reading matchable assets from cursor " +
                sweepCursor);
            String filter =
                "select from org.ecocean.media.Feature where annot.matchAgainst == true && annot.features.contains(this) && asset.id > "
                + sweepCursor + " VARIABLES org.ecocean.Annotation annot";
            query = readShepherd.getPM().newQuery(filter);
            query.setOrdering("asset.id ascending");
            // stream the result instead of buffering ~1M Feature rows client-side
            // (pgjdbc default fetchSize=0 buffers the whole ResultSet); setRange
            // would break rawExhausted semantics, a cursor fetch does not
            query.getFetchPlan().setFetchSize(1000);
            Collection c = (Collection)(query.execute());
            final java.util.Iterator featIter = c.iterator();
            java.util.Iterator<MediaAsset> assetIter = new java.util.Iterator<MediaAsset>() {
                public boolean hasNext() {
                    return featIter.hasNext();
                }
                public MediaAsset next() {
                    return ((Feature)featIter.next()).getMediaAsset();
                }
            };
            page = collectSweepPage(assetIter, SWEEP_PAGE_SIZE);
        } catch (Exception ex) {
            System.out.println("Exception in AcmIdBot.sweepMatchableAssets read phase!");
            ex.printStackTrace();
        } finally {
            if (query != null) query.closeAll();
            readShepherd.rollbackAndClose();
        }
        if (page == null) {
            // read phase failed: this counts toward the same poisoned-page guard as
            // probe failures, since a corrupt row at this cursor could otherwise
            // stall the sweep forever. There is no page.lastAssetId to resume from
            // here, so a skip is a blind advance rather than a targeted one.
            sweepFailCount++;
            System.out.println("WARNING: AcmIdBot sweep read phase failed (attempt " +
                sweepFailCount + " of " + PAGE_FAIL_LIMIT + " at cursor " + sweepCursor +
                ")");
            if (shouldSkipPoisonedPage(sweepFailCount)) {
                System.out.println(
                    "WARNING: AcmIdBot sweep SKIPPING page after repeated read failures; " +
                    "blind-advancing cursor " + sweepCursor + " -> " +
                    (sweepCursor + SWEEP_PAGE_SIZE) +
                    " (no page ids available; wrap-around will resweep the skipped range)");
                sweepCursor += SWEEP_PAGE_SIZE;
                sweepFailCount = 0;
            }
            return; // cursor otherwise unchanged: same page retried next run
        }

        // ---- probe phase (no transaction open) ----
        List<String> missingAcmIds = null;
        try {
            missingAcmIds = org.ecocean.ia.plugin.WildbookIAM.iaMissingImageIds(
                new ArrayList<String>(page.acmIdToAssetId.keySet()), context);
        } catch (java.io.IOException ex) {
            sweepFailCount++;
            System.out.println("WARNING: AcmIdBot sweep probe failed (attempt " +
                sweepFailCount + " of " + PAGE_FAIL_LIMIT + " at cursor " + sweepCursor +
                "): " + ex.toString());
            if (shouldSkipPoisonedPage(sweepFailCount)) {
                System.out.println(
                    "WARNING: AcmIdBot sweep SKIPPING page after repeated failures; cursor " +
                    sweepCursor + " -> " + page.lastAssetId);
                if (page.lastAssetId >= 0) sweepCursor = page.lastAssetId;
                sweepFailCount = 0;
            }
            return; // cursor otherwise unchanged: same page retried next run
        }

        // ---- heal phase (own transaction, explicit commits) ----
        List<Integer> candidateIds = new ArrayList<Integer>(page.nullAcmAssetIds);
        for (String acmId : missingAcmIds) {
            Integer assetId = page.acmIdToAssetId.get(acmId);
            if (assetId != null) candidateIds.add(assetId);
        }
        java.util.Collections.sort(candidateIds); // ascending id = cursor order
        int healedCount = 0;
        int sentCount = 0;
        boolean maxFixesHit = false;
        int resumeAssetId = page.lastAssetId;
        if (candidateIds.size() > 0) {
            Shepherd healShepherd = new Shepherd(context);
            healShepherd.setAction("AcmIdBot.sweepHeal");
            healShepherd.beginDBTransaction();
            try {
                for (Integer assetId : candidateIds) {
                    healShepherd.setAction("AcmIdBot.sweepHeal_asset_" + assetId);
                    // hoisted so the catch block can revert a pre-assigned acmId that
                    // WBIA never acknowledged (send threw before confirmation)
                    MediaAsset asset = null;
                    String priorAcmId = null;
                    boolean priorCaptured = false;
                    try {
                        asset = org.ecocean.media.MediaAssetFactory.load(
                            assetId.intValue(), healShepherd);
                        if (asset == null) continue;
                        if (asset.isValidImageForIA() == null) {
                            asset.validateSourceImage();
                            healShepherd.updateDBTransaction();
                        }
                        if (!asset.isValidImageForIAForced()) continue;
                        if (!asset.hasFamily(healShepherd)) asset.updateStandardChildren();
                        // legacy null or malformed acmIds adopt the asset UUID before sending
                        priorAcmId = asset.getAcmId();
                        priorCaptured = true;
                        if ((priorAcmId == null) || !Util.isUUID(priorAcmId))
                            asset.setAcmId(asset.getUUID());
                        ArrayList<MediaAsset> fixMe = new ArrayList<MediaAsset>();
                        fixMe.add(asset);
                        // checkFirst=false: the probe already established absence
                        org.json.JSONObject sendRtn = IBEISIA.sendMediaAssetsNew(fixMe,
                            context, false);
                        sentCount++;
                        // NOTE: compared against the POST-rectify acmId on purpose. If WBIA
                        // already knew this image under a different UUID, rectifyMediaAssetIds
                        // (inside the send) adopted WBIA's authoritative UUID — that
                        // convergence IS the heal, and committing it is correct. A strict
                        // compare against the pre-send UUID would revert and re-probe forever.
                        if (sendConfirmedAcmId(sendRtn, asset.getAcmId())) {
                            // commit so the confirmed acmId survives rollbackAndClose
                            healShepherd.updateDBTransaction();
                            healedCount++;
                            if (healedCount >= maxFixes) {
                                maxFixesHit = true;
                                resumeAssetId = assetId.intValue();
                                break;
                            }
                        } else {
                            System.out.println(
                                "WARNING: AcmIdBot sweep could not confirm WBIA registration for asset "
                                + assetId + "; not counting as healed");
                            // don't persist an acmId WBIA never acknowledged
                            asset.setAcmId(priorAcmId);
                        }
                    } catch (Exception ec) {
                        // revert BEFORE any commit below (or a later asset's commit)
                        // can persist an acmId WBIA never acknowledged; probed-missing
                        // assets revert to their original non-null DB acmId
                        if (priorCaptured && asset != null) asset.setAcmId(priorAcmId);
                        System.out.println("Exception in AcmIdBot sweep heal for asset " +
                            assetId);
                        ec.printStackTrace();
                        // mirror fixFeats: a 500 from WBIA marks the image invalid for IA
                        if (ec.toString().contains("HTTP error code : 500")) {
                            try {
                                if (asset != null) {
                                    asset.setIsValidImageForIA(false);
                                    healShepherd.updateDBTransaction();
                                }
                            } catch (Exception inner) {
                                inner.printStackTrace();
                            }
                        }
                    }
                }
            } finally {
                healShepherd.rollbackAndClose();
            }
        }
        sweepCursor = nextCursorAfterSuccess(page, maxFixesHit, resumeAssetId);
        sweepFailCount = 0;
        System.out.println("AcmIdBot Reconciliation Sweep Summary");
        System.out.println("...page: " + (page.acmIdToAssetId.size() +
            page.nullAcmAssetIds.size()) + " candidates (" + page.acmIdToAssetId.size() +
            " probed, " + page.nullAcmAssetIds.size() + " null acmId)");
        System.out.println("......missing from WBIA: " + missingAcmIds.size());
        System.out.println("......heals sent: " + sentCount + ", heals successful: " +
            healedCount + (maxFixesHit ? " (maxFixes cap hit)" : ""));
        System.out.println("......cursor now " + sweepCursor +
            (page.rawExhausted && !maxFixesHit ? " (sweep complete, wrapped)" : ""));
    }
}
