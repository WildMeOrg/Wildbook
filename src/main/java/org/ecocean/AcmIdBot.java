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
 * fails. It first checks bulk ImportTasks for appropriate images that may be missing an acmId, and then it checks Encounters submitted within the
 * past 24 hours.
 *
 */
public class AcmIdBot {
    static String context = "context0";

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
                        IBEISIA.sendMediaAssetsNew(fixMe, context);
                        numAcmIdFixesSent++;
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

    // background workers
    public static boolean startServices(String context) {
        startCollector(context);
        return true;
    }

    // basically our "listener" daemon; but is more pull (poll?) than push so to speak.
    private static void startCollector(final String context) { // throws IOException {
        long interval = 15; // number minutes between runs
        long initialDelay = 1; // number minutes before first execution occurs

        System.out.println("+ AcmIdBot.startCollector(" + context + ") starting.");
        final ScheduledExecutorService schedExec = Executors.newScheduledThreadPool(1);
        final ScheduledFuture schedFuture = schedExec.scheduleWithFixedDelay(new Runnable() {
            // DO WORK HERE
            public void run() {
                if (new java.io.File("/tmp/WB_AcmIdBot_SHUTDOWN").exists()) {
                    System.out.println("INFO: AcmIdBot.startCollection(" + context +
                    ") shutting down due to file signal");
                    schedExec.shutdown();
                    return;
                }
                fixAcmIds(context);
            }
        }, initialDelay, // initial delay
            interval, // period delay *after* execution finishes
            TimeUnit.MINUTES); // unit of delays above

        System.out.println("Let's get AcmIdBot's time running.");
        try {
            schedExec.awaitTermination(5000, TimeUnit.MILLISECONDS);
        } catch (java.lang.InterruptedException ex) {
            System.out.println("WARNING: AcmIdBot.startCollector(" + context + ") interrupted: " +
                ex.toString());
        }
        System.out.println("+ AcmIdBot.startCollector(" + context + ") backgrounded");
    }

    // mostly for ContextDestroyed in StartupWildbook..... i think?
    public static void cleanup() {
        System.out.println(
            "================ = = = = = = ===================== AcmIdBot.cleanup() finished.");
    }

    public static void fixAcmIds(String context) {
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("AcmIdBot.java");
        myShepherd.beginDBTransaction();

        Query query2 = null;
        Query query3 = null;
        try {
            System.out.println(
                "Looking for complete import tasks with media assets with missing acmIds");

            // number of fixes to consider before finishing and letting a new round of work restart the effort
            int maxFixes = 500;
            String filter2 =
                "select from org.ecocean.media.Feature where itask.status == 'complete' && itask.encounters.contains(enc) && enc.annotations.contains(annot) && annot.features.contains(this) && asset.acmId == null VARIABLES org.ecocean.Encounter enc;org.ecocean.servlet.importer.ImportTask itask;org.ecocean.Annotation annot";
            query2 = myShepherd.getPM().newQuery(filter2);
            query2.setOrdering("revision desc");
            Collection c2 = (Collection)(query2.execute());
            List<Feature> feats = new ArrayList<Feature>(c2);
            query2.closeAll();
            query2 = null;  // Mark as closed

            fixFeats(feats, myShepherd, "ACM ID ImportTask fixing summary", maxFixes);

            // check recent Encounter submissions in last 24 hours for missing acmIds
            long currentTimeInMillis = System.currentTimeMillis();
            long twenyFourHoursAgo = currentTimeInMillis - (1000 * 60 * 60 * 24);
            System.out.println(
                "Looking for recent Encounters (24 hours) with media assets with missing acmIds");
            // dwcDateAddedLong >=
            String filter3 =
                "select from org.ecocean.media.Feature where enc45.dwcDateAddedLong >= " +
                twenyFourHoursAgo +
                " && enc45.annotations.contains(annot) && annot.features.contains(this) && asset.acmId == null VARIABLES org.ecocean.Encounter enc45;org.ecocean.Annotation annot";
            query3 = myShepherd.getPM().newQuery(filter3);
            query3.setOrdering("revision desc");
            Collection c3 = (Collection)(query3.execute());
            List<Feature> feats2 = new ArrayList<Feature>(c3);
            query3.closeAll();
            query3 = null;  // Mark as closed

            fixFeats(feats2, myShepherd, "Recent Encounter ACM ID Fixing Summary", maxFixes);
        } catch (Exception f) {
            System.out.println("Exception in AcmIdBot!");
            f.printStackTrace();
        } finally {
            if (query2 != null) query2.closeAll();
            if (query3 != null) query3.closeAll();
            myShepherd.rollbackAndClose();
        }
    }
}
