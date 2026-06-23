package org.ecocean.servlet;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.SuperSpot;
import org.ecocean.grid.*;
import org.ecocean.shepherd.core.Shepherd;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Synchronous Modified Groth pattern matching servlet.
 * Runs the optimized Groth algorithm against the full matchGraph catalog
 * and writes results in the XML format expected by scanEndApplet.jsp.
 *
 * Parameters:
 *   encounterNumber - the encounter to match (required)
 *   rightSide       - "true" for right-side spots, "false" (default) for left
 */
public class GrothMatchServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger(GrothMatchServlet.class.getName());

    /** Build the redirect URL to the polling progress/results page for a scan. */
    private static String scanProgressUrl(String encNumber, String taskID, boolean rightScan) {
        return "encounters/scanEndApplet.jsp?number=" + encNumber + "&taskID=" + taskID +
            (rightScan ? "&rightSide=true" : "");
    }

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        String encNumber = request.getParameter("encounterNumber");
        if (encNumber == null || encNumber.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                "Missing required parameter: encounterNumber");
            return;
        }

        boolean rightScan = "true".equals(request.getParameter("rightSide"));

        // Check that the matchGraph is ready before proceeding
        if (!GridManager.isMatchGraphReady()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                "The match graph is still loading. Please try again shortly.");
            return;
        }

        // Groth parameter locals — resolved after enc is loaded (see Phase 1/2 boundary)
        double epsilon, R, Sizelim, maxTriangleRotation, C;

        // Phase 1: Short DB transaction to load query encounter spots
        SuperSpot[] queryArray;
        // Query encounter as an EncounterLite — needed by the I3S matcher (i3sScan),
        // which is run alongside Groth below to revive I3S results. Built while the
        // encounter is still managed; EncounterLite copies the spot data so it stays
        // usable after the transaction closes.
        EncounterLite queryLite;
        String encDate, encSex, encIndividualID, encLocation, encLocationID, encSize;
        String queryGenus = null, querySpecificEpithet = null;
        {
            Shepherd myShepherd = new Shepherd(context);
            myShepherd.setAction("GrothMatchServlet.class");
            myShepherd.beginDBTransaction();
            try {
                Encounter enc = myShepherd.getEncounter(encNumber);
                if (enc == null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND,
                        "Encounter not found: " + encNumber);
                    return;
                }

                ArrayList<SuperSpot> querySpots = rightScan ?
                    enc.getRightSpots() : enc.getSpots();

                if (querySpots == null || querySpots.size() < 4) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "Need at least 4 spots for matching. Found: " +
                        (querySpots == null ? 0 : querySpots.size()));
                    return;
                }

                queryArray = querySpots.toArray(new SuperSpot[0]);
                queryLite = new EncounterLite(enc);
                encDate = enc.getDate();
                encSex = enc.getSex() != null ? enc.getSex() : "unknown";
                encIndividualID = ServletUtilities.handleNullString(enc.getIndividualID());
                encSize = enc.getSizeAsDouble() != null ? enc.getSize() + " meters" : "unknown";
                encLocation = enc.getLocation();
                encLocationID = enc.getLocationID();
                // Query species for species-aware Groth param resolution (below).
                queryGenus = enc.getGenus();
                querySpecificEpithet = enc.getSpecificEpithet();
            } finally {
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
            }
        }

        // Resolve Groth parameters by query encounter's species
        org.ecocean.grid.GrothParams gp =
            CommonConfiguration.getGrothParams(queryGenus, querySpecificEpithet, context);
        epsilon = gp.getEpsilon();
        R = gp.getR();
        Sizelim = gp.getSizelim();
        maxTriangleRotation = gp.getMaxTriangleRotation();
        C = gp.getC();

        // Phase 2 + 3 run ASYNCHRONOUSLY. A single-threaded scan over the in-memory
        // matchGraph can take minutes over a large same-species catalog; running it inline
        // hung the browser on /GrothMatch and frequently never wrote results. Instead we
        // create a ScanTask, hand the work to a background thread (GrothScanRunnable), and
        // redirect immediately to the polling progress page (scanEndApplet.jsp).
        String taskID = "scan" + (rightScan ? "R" : "L") + encNumber;
        GridManager gm = GridManagerFactory.getGridManager();

        // Active-run guard: if a scan for this taskID is already running, do NOT start a second
        // (it would corrupt the shared ScanTask lifecycle and race on result files) — just show
        // its progress page. tryStartScan returns a non-zero ownership token; 0 means active.
        final long scanToken = gm.tryStartScan(taskID);
        if (scanToken == 0L) {
            response.sendRedirect(scanProgressUrl(encNumber, taskID, rightScan));
            return;
        }

        // Once the background runnable is submitted it owns all cleanup (finish/clear/endScan);
        // until then, every exit path here must release the slot we just claimed.
        boolean submitted = false;
        try {
            String rootWebappPath = getServletContext().getRealPath("/");
            File webappsDir = new File(rootWebappPath).getParentFile();
            File shepherdDataDir = new File(webappsDir,
                CommonConfiguration.getDataDirectoryName(context));

            // Create-or-reset the ScanTask in one short transaction so the first poll sees an
            // unfinished, freshly-initialized task. commitDBTransactionWithStatus surfaces
            // commit failures so we never redirect to a page that polls a task that was never
            // durably stored; on failure we roll back explicitly (close() does not).
            boolean committed = false;
            Shepherd taskShepherd = new Shepherd(context);
            taskShepherd.setAction("GrothMatchServlet.createTask");
            taskShepherd.beginDBTransaction();
            try {
                ScanTask st = taskShepherd.getScanTask(taskID);
                if (st == null) {
                    st = new ScanTask(taskShepherd, taskID, new java.util.Properties(),
                        encNumber, true);
                    taskShepherd.getPM().makePersistent(st);
                }
                // Initialize/reset state identically for new and reused tasks so no stale
                // finished/started/startTime/endTime/filters leak into this run.
                st.setFinished(false);
                st.setStarted(true);
                st.setStartTime(System.currentTimeMillis());
                st.setEndTime(-1);
                st.setLocationIDFilters(new ArrayList<String>());
                committed = taskShepherd.commitDBTransactionWithStatus();
            } catch (Exception ce) {
                log.severe("Failed to create/reset ScanTask " + taskID + ": " + ce.getMessage());
                committed = false;
            } finally {
                if (!committed) {
                    try { taskShepherd.rollbackDBTransaction(); } catch (Exception ignore) {}
                }
                taskShepherd.closeDBTransaction();
            }

            if (!committed) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not create scan task. Please try again.");
                return;
            }

            gm.setScanProgress(taskID, 0, GridManager.getMatchGraph().size());

            java.util.concurrent.ThreadPoolExecutor es =
                SharkGridThreadExecutorService.getExecutorService();
            if (es == null) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Scan executor unavailable; please try again shortly.");
                return;
            }

            try {
                es.execute(new GrothScanRunnable(context, taskID, scanToken, encNumber, rightScan,
                    queryArray, queryLite, epsilon, R, Sizelim, maxTriangleRotation, C,
                    encDate, encSex, encIndividualID, encSize, encLocation, encLocationID,
                    shepherdDataDir));
                submitted = true;
            } catch (java.util.concurrent.RejectedExecutionException ree) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Too many scans in progress; please try again shortly.");
                return;
            }

            response.sendRedirect(scanProgressUrl(encNumber, taskID, rightScan));
        } catch (Exception e) {
            log.severe("Failed to start Groth scan for " + encNumber + ": " + e.getMessage());
            e.printStackTrace();
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to start scan: " + e.getMessage());
            }
        } finally {
            // If we never handed the work to the background thread, release the slot + progress
            // we claimed. After submission the runnable owns these (token-guarded), so a failing
            // sendRedirect here must NOT release them out from under a running scan.
            if (!submitted) {
                gm.clearScanProgress(taskID);
                gm.endScan(taskID, scanToken);
            }
        }
    }

    /**
     * Decide whether a catalog candidate is eligible to be matched against the query.
     *
     * Centralizes all candidate-eligibility rules so they live in one tested place:
     *   - the candidate is not the query encounter itself,
     *   - the candidate has spots on the side being scanned (#1608), and
     *   - WB-1791: the candidate is the same species as the query
     *     (delegated to the null-safe {@link EncounterLite#doesSpeciesMatch(EncounterLite)},
     *     mirroring the legacy async ScanWorkItemCreationThread behavior that the
     *     synchronous Groth rewrite dropped).
     *
     * @param queryLite          query-side EncounterLite (carries genus/specificEpithet)
     * @param candidate          catalog EncounterLite under consideration
     * @param queryEncNumber     the query encounter number
     * @param candidateEncNumber the candidate encounter number
     * @param rightScan          true for a right-side scan, false for left
     * @return true if the candidate should be matched, false to skip it
     */
    static boolean isEligibleCandidate(EncounterLite queryLite, EncounterLite candidate,
        String queryEncNumber, String candidateEncNumber, boolean rightScan) {
        if (queryLite == null || candidate == null) return false;
        // skip self
        if (candidateEncNumber != null && candidateEncNumber.equals(queryEncNumber)) return false;
        // skip candidates with no spots on the scanned side
        List<?> candidateSpots = rightScan ? candidate.getRightSpots() : candidate.getSpots();
        if (candidateSpots == null || candidateSpots.isEmpty()) return false;
        // WB-1791: same-species only (null-safe; false if either side lacks taxonomy)
        if (!queryLite.doesSpeciesMatch(candidate)) return false;
        return true;
    }
}
