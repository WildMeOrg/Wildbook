package org.ecocean.servlet;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

import org.ecocean.Encounter;
import org.ecocean.SuperSpot;
import org.ecocean.grid.EncounterLite;
import org.ecocean.grid.GridManager;
import org.ecocean.grid.GridManagerFactory;
import org.ecocean.grid.I3SMatchObject;
import org.ecocean.grid.I3SResultWriter;
import org.ecocean.grid.MatchComparator;
import org.ecocean.grid.MatchObject;
import org.ecocean.grid.ScanTask;
import org.ecocean.grid.VertexPointMatch;
import org.ecocean.shepherd.core.Shepherd;

/**
 * Runs one Modified-Groth (+ I3S) scan in the background so the request that triggered it
 * ({@link GrothMatchServlet}) can return immediately and the user lands on the polling progress
 * page (scanEndApplet.jsp). This restores the async + progress-bar UX that regressed when the
 * scan was made a blocking, single-threaded servlet call.
 *
 * The fast in-memory matchGraph engine is reused verbatim; only the work-item *grid* (whose
 * remote processors are dead) is not. Progress is reported via {@link GridManager}'s in-memory
 * scan-progress map; completion is signalled by setting the {@link ScanTask} finished so the
 * polling JSP stops and renders the result XML.
 */
public final class GrothScanRunnable implements Runnable {
    private static final Logger log = Logger.getLogger(GrothScanRunnable.class.getName());

    private final String context;
    private final String taskID;
    private final long token;
    private final String encNumber;
    private final boolean rightScan;
    private final SuperSpot[] queryArray;
    private final EncounterLite queryLite;
    private final double epsilon, R, Sizelim, maxTriangleRotation, C;
    private final String encDate, encSex, encIndividualID, encSize, encLocation, encLocationID;
    private final File shepherdDataDir;

    public GrothScanRunnable(String context, String taskID, long token, String encNumber,
        boolean rightScan, SuperSpot[] queryArray, EncounterLite queryLite,
        double epsilon, double R, double Sizelim, double maxTriangleRotation, double C,
        String encDate, String encSex, String encIndividualID, String encSize,
        String encLocation, String encLocationID, File shepherdDataDir) {
        this.context = context;
        this.taskID = taskID;
        this.token = token;
        this.encNumber = encNumber;
        this.rightScan = rightScan;
        this.queryArray = queryArray;
        this.queryLite = queryLite;
        this.epsilon = epsilon;
        this.R = R;
        this.Sizelim = Sizelim;
        this.maxTriangleRotation = maxTriangleRotation;
        this.C = C;
        this.encDate = encDate;
        this.encSex = encSex;
        this.encIndividualID = encIndividualID;
        this.encSize = encSize;
        this.encLocation = encLocation;
        this.encLocationID = encLocationID;
        this.shepherdDataDir = shepherdDataDir;
    }

    @Override public void run() {
        GridManager gm = GridManagerFactory.getGridManager();
        long startTime = System.currentTimeMillis();

        try {
            // Remove stale result files first so a failed or partial run can never display
            // pre-existing (possibly pre-fix, cross-species) results. scanEndApplet.jsp shows
            // XML whenever the task is finished and the file exists; deleting up front means a
            // run that dies before writing falls through to the JSP's "results could not be
            // written" branch instead of showing stale matches.
            deleteStaleResultFiles();

            // Phase 1 (match engine): single-threaded, no DB transaction needed. The matchGraph
            // is a ConcurrentHashMap iterated read-only here. First snapshot the ELIGIBLE
            // candidates (WB-1791 same-species + correct-side spots, gating BOTH I3S and Groth)
            // so the progress denominator reflects actual comparisons — matching the legacy
            // work-item progress — instead of the whole graph (all species + both sides). The
            // eligibility check is cheap; snapshotting also gives a stable count + candidate
            // list even if the live graph is edited mid-scan.
            ConcurrentHashMap<String, EncounterLite> matchGraph = GridManager.getMatchGraph();
            List<Map.Entry<String, EncounterLite>> candidates =
                new ArrayList<Map.Entry<String, EncounterLite>>();
            for (Map.Entry<String, EncounterLite> entry : matchGraph.entrySet()) {
                if (GrothMatchServlet.isEligibleCandidate(queryLite, entry.getValue(), encNumber,
                        entry.getKey(), rightScan)) {
                    candidates.add(entry);
                }
            }
            int total = candidates.size();
            gm.setScanProgress(taskID, 0, total);

            List<MatchObject> grothResults = new ArrayList<MatchObject>();
            Map<String, EncounterLite> scannedI3SLites = new HashMap<String, EncounterLite>();
            int done = 0;
            for (Map.Entry<String, EncounterLite> entry : candidates) {
                EncounterLite el = entry.getValue();

                Vector i3sPoints = null;
                double i3sValue = 0;
                try {
                    ArrayList<SuperSpot> candSpots =
                        rightScan ? el.getRightSpots() : el.getSpots();
                    ArrayList<SuperSpot> querySideSpots =
                        rightScan ? queryLite.getRightSpots() : queryLite.getSpots();
                    if ((candSpots != null) && !candSpots.isEmpty() &&
                        (querySideSpots != null) && !querySideSpots.isEmpty()) {
                        I3SMatchObject i3sResult = el.i3sScan(queryLite, rightScan);
                        TreeMap i3sMap = i3sResult.getMap();
                        Vector pts = new Vector();
                        if (i3sMap != null) {
                            for (Object pair : i3sMap.values()) pts.add(pair);
                        }
                        i3sPoints = pts;
                        i3sValue = i3sResult.getI3SMatchValue();
                        scannedI3SLites.put(entry.getKey(), el);
                    }
                } catch (Exception i3sEx) {
                    i3sPoints = null;
                }

                MatchObject mo = el.getPointsForBestMatch(
                    queryArray, epsilon, R, Sizelim, maxTriangleRotation, C, true, rightScan);
                mo.encounterNumber = entry.getKey();
                if (i3sPoints != null) mo.setI3SValues(i3sPoints, i3sValue);
                grothResults.add(mo);

                done++;
                if ((done % 250) == 0) gm.setScanProgress(taskID, done, total);
            }
            gm.setScanProgress(taskID, total, total);

            MatchObject[] matchArray = grothResults.toArray(new MatchObject[0]);
            Arrays.sort(matchArray, new MatchComparator());
            log.info("Groth match for " + encNumber + " completed in " +
                (System.currentTimeMillis() - startTime) + "ms (" + total +
                " same-species candidates of " + matchGraph.size() + " in graph)");

            // Phase 2 (results): build the DOM with encounter details inside a short tx, close
            // the tx, then write files (no DB transaction held during file I/O).
            Document document = buildMatchDocument(matchArray);
            writeScanXml(document);
            I3SResultWriter.write(matchArray, queryLite, scannedI3SLites, encNumber, encDate,
                encSex, encIndividualID, encSize, rightScan, shepherdDataDir);
        } catch (Exception e) {
            log.severe("Async Groth scan failed for " + encNumber + " (task " + taskID + "): " +
                e.getMessage());
            e.printStackTrace();
        } finally {
            // Always mark the task finished so the polling JSP stops even on failure (it then
            // shows the "results could not be written" branch since the XML was deleted up
            // front). The owner check is defensive — with no stale-reclaim this run owns the
            // slot throughout — and endScan releases it atomically by token.
            if (gm.isScanOwner(taskID, token)) {
                markScanTaskFinished();
                gm.clearScanProgress(taskID);
            }
            gm.endScan(taskID, token);
        }
    }

    /** Build the match-result DOM, loading encounter details within one short transaction. */
    private Document buildMatchDocument(MatchObject[] matchArray) {
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("GrothScanRunnable.buildMatchDocument");
        myShepherd.beginDBTransaction();
        try {
            Document document = DocumentHelper.createDocument();
            Element root = document.addElement("matchSet");
            root.addAttribute("scanDate", (new java.util.Date()).toString());
            root.addAttribute("R", String.valueOf(R));
            root.addAttribute("epsilon", String.valueOf(epsilon));
            root.addAttribute("Sizelim", String.valueOf(Sizelim));
            root.addAttribute("maxTriangleRotation", String.valueOf(maxTriangleRotation));
            root.addAttribute("C", String.valueOf(C));

            int numMatches = matchArray.length;
            if (numMatches > 100) numMatches = 100;

            for (int i = 0; i < numMatches; i++) {
                try {
                    MatchObject mo = matchArray[i];
                    if ((mo.getMatchValue() > 0) &&
                        ((mo.getMatchValue() * mo.getAdjustedMatchValue()) > 2)) {
                        Element match = root.addElement("match");
                        match.addAttribute("points", String.valueOf(mo.getMatchValue()));
                        match.addAttribute("adjustedpoints",
                            String.valueOf(mo.getAdjustedMatchValue()));
                        match.addAttribute("pointBreakdown", mo.getPointBreakdown());

                        String finalscore = String.valueOf(
                            mo.getMatchValue() * mo.getAdjustedMatchValue());
                        if (finalscore.length() > 7) finalscore = finalscore.substring(0, 6);
                        match.addAttribute("finalscore", finalscore);

                        try {
                            match.addAttribute("logMStdDev", String.valueOf(mo.getLogMStdDev()));
                        } catch (NumberFormatException nfe) {
                            match.addAttribute("logMStdDev", "<0.01");
                        }
                        match.addAttribute("evaluation", mo.getEvaluation());

                        Encounter firstEnc = myShepherd.getEncounter(mo.getEncounterNumber());
                        if (firstEnc == null) continue;

                        Element enc1 = match.addElement("encounter");
                        enc1.addAttribute("number", firstEnc.getEncounterNumber());
                        enc1.addAttribute("date", firstEnc.getDate());
                        enc1.addAttribute("sex",
                            firstEnc.getSex() != null ? firstEnc.getSex() : "unknown");
                        enc1.addAttribute("assignedToShark",
                            ServletUtilities.handleNullString(firstEnc.getIndividualID()));
                        if (firstEnc.getSizeAsDouble() != null) {
                            enc1.addAttribute("size", firstEnc.getSize() + " meters");
                        }
                        enc1.addAttribute("location", firstEnc.getLocation());
                        enc1.addAttribute("locationID", firstEnc.getLocationID());

                        VertexPointMatch[] scores = mo.getScores();
                        try {
                            for (VertexPointMatch score : scores) {
                                Element spot = enc1.addElement("spot");
                                spot.addAttribute("x", String.valueOf(score.getOldX()));
                                spot.addAttribute("y", String.valueOf(score.getOldY()));
                            }
                        } catch (NullPointerException npe) {}

                        Element enc2 = match.addElement("encounter");
                        enc2.addAttribute("number", encNumber);
                        enc2.addAttribute("date", encDate);
                        enc2.addAttribute("sex", encSex);
                        enc2.addAttribute("assignedToShark", encIndividualID);
                        enc2.addAttribute("size", encSize);
                        enc2.addAttribute("location", encLocation);
                        enc2.addAttribute("locationID", encLocationID);

                        try {
                            for (VertexPointMatch score : scores) {
                                Element spot = enc2.addElement("spot");
                                spot.addAttribute("x", String.valueOf(score.getNewX()));
                                spot.addAttribute("y", String.valueOf(score.getNewY()));
                            }
                        } catch (NullPointerException npe) {}

                        List<String> keywords = myShepherd.getKeywordsInCommon(
                            mo.getEncounterNumber(), encNumber);
                        if (keywords != null && !keywords.isEmpty()) {
                            Element kws = match.addElement("keywords");
                            for (String kwName : keywords) {
                                Element keyword = kws.addElement("keyword");
                                keyword.addAttribute("name", kwName);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return document;
        } finally {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
        }
    }

    /** Write the Groth result XML to the encounter directory. */
    private void writeScanXml(Document document) throws java.io.IOException {
        String fileAddition = rightScan ? "Right" : "";
        String thisEncDirString = Encounter.dir(shepherdDataDir, encNumber);
        File thisEncounterDir = new File(thisEncDirString);
        if (!thisEncounterDir.exists()) thisEncounterDir.mkdirs();

        File file = new File(thisEncDirString + "/lastFull" + fileAddition + "Scan.xml");
        FileWriter mywriter = new FileWriter(file);
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setLineSeparator(System.getProperty("line.separator"));
        XMLWriter writer = new XMLWriter(mywriter, format);
        writer.write(document);
        writer.close();
    }

    /** Delete any prior Groth + I3S result files for this side so stale results never show. */
    private void deleteStaleResultFiles() {
        String fileAddition = rightScan ? "Right" : "";
        String dir = Encounter.dir(shepherdDataDir, encNumber);
        deleteIfExists(new File(dir + "/lastFull" + fileAddition + "Scan.xml"));
        deleteIfExists(new File(dir + "/lastFull" + fileAddition + "I3SScan.xml"));
    }

    private void deleteIfExists(File f) {
        try {
            if (f.exists() && !f.delete()) {
                log.warning("Could not delete stale scan file: " + f.getAbsolutePath());
            }
        } catch (Exception e) {
            log.warning("Error deleting stale scan file " + f.getAbsolutePath() + ": " +
                e.getMessage());
        }
    }

    /**
     * Mark the ScanTask finished so the polling JSP stops. This is a REQUIRED terminal step:
     * if it never persists, scanEndApplet.jsp polls forever. So we retry with a fresh Shepherd
     * and explicitly roll back a failed/uncommitted transaction (commitDBTransactionWithStatus
     * does not roll back on failure, and closeDBTransaction does not roll back an active tx).
     */
    private void markScanTaskFinished() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            Shepherd sh = new Shepherd(context);
            sh.setAction("GrothScanRunnable.finish");
            sh.beginDBTransaction();
            boolean ok = false;
            try {
                ScanTask st = sh.getScanTask(taskID);
                if (st != null) {
                    st.setFinished(true);
                    st.setEndTime(System.currentTimeMillis());
                }
                ok = sh.commitDBTransactionWithStatus();
            } catch (Exception e) {
                log.severe("Error finishing ScanTask " + taskID + " (attempt " + attempt + "): " +
                    e.getMessage());
                ok = false;
            } finally {
                if (!ok) {
                    try { sh.rollbackDBTransaction(); } catch (Exception ignore) {}
                }
                sh.closeDBTransaction();
            }
            if (ok) return;
        }
        log.severe("Failed to persist finished ScanTask " + taskID +
            " after retries; the progress page may keep polling.");
    }
}
