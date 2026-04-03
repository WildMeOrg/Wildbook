package org.ecocean.servlet;

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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Asynchronous Modified Groth pattern matching servlet.
 * Creates a ScanTask and queues work items for the grid client
 * (WorkAppletHeadlessEpic) to process, then redirects the user
 * to the results page which polls for completion.
 *
 * Parameters:
 *   encounterNumber - the encounter to match (required)
 *   rightSide       - "true" for right-side spots, "false" (default) for left
 */
public class GrothMatchServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger(GrothMatchServlet.class.getName());

    // Per-taskID locks to prevent concurrent re-scan races
    private static final ConcurrentHashMap<String, Object> taskLocks = new ConcurrentHashMap<>();

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
        encNumber = encNumber.trim();

        boolean rightScan = "true".equals(request.getParameter("rightSide"));

        // Check that the matchGraph is ready before proceeding
        if (!GridManager.isMatchGraphReady()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                "The match graph is still loading. Please try again shortly.");
            return;
        }

        // Validate the encounter exists and has enough spots
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

            // Verify this encounter is in the matchGraph
            if (GridManager.getMatchGraphEncounterLiteEntry(encNumber) == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Encounter " + encNumber + " is not in the match graph. " +
                    "Spots may not have been loaded yet.");
                return;
            }
        } finally {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
        }

        // Build the task ID: "scanL" or "scanR" + encounter number
        String taskID = "scan" + (rightScan ? "R" : "L") + encNumber;

        // Synchronize on per-taskID lock to prevent concurrent re-scan races.
        // putIfAbsent is atomic; all threads for the same taskID synchronize on
        // the same lock object. The lock is kept in the map permanently (lightweight).
        Object lock = taskLocks.computeIfAbsent(taskID, k -> new Object());
        synchronized (lock) {
            // Clean up any existing work items for this task in GridManager
            GridManager gm = GridManagerFactory.getGridManager();
            gm.removeWorkItemsForTask(taskID);
            gm.removeCompletedWorkItemsForTask(taskID);

            // Create and persist a new ScanTask
            Properties props = new Properties();
            props.setProperty("epsilon", CommonConfiguration.getEpsilon(context));
            props.setProperty("R", CommonConfiguration.getR(context));
            props.setProperty("Sizelim", CommonConfiguration.getSizelim(context));
            props.setProperty("maxTriangleRotation",
                CommonConfiguration.getMaxTriangleRotation(context));
            props.setProperty("C", CommonConfiguration.getC(context));

            Shepherd taskShepherd = new Shepherd(context);
            taskShepherd.setAction("GrothMatchServlet.class");
            try {
                // Delete old ScanTask if it exists
                taskShepherd.beginDBTransaction();
                ScanTask oldTask = taskShepherd.getScanTask(taskID);
                if (oldTask != null) {
                    taskShepherd.getPM().deletePersistent(oldTask);
                    taskShepherd.commitDBTransaction();
                    log.info("Deleted old ScanTask: " + taskID);
                } else {
                    taskShepherd.rollbackDBTransaction();
                }

                // Create and store new ScanTask
                ScanTask scanTask = new ScanTask(taskShepherd, taskID, props, encNumber, true);
                scanTask.setSubmitter(request.getRemoteUser() != null ?
                    request.getRemoteUser() : "unknown");
                taskShepherd.storeNewScanTask(scanTask);
                log.info("Created new ScanTask: " + taskID);
            } catch (Exception e) {
                log.severe("Failed to create ScanTask: " + e.getMessage());
                e.printStackTrace();
                taskShepherd.rollbackDBTransaction();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to create scan task: " + e.getMessage());
                return;
            } finally {
                taskShepherd.closeDBTransaction();
            }

            // Start ScanWorkItemCreationThread to populate work items
            ScanWorkItemCreationThread thread = new ScanWorkItemCreationThread(
                taskID, rightScan, encNumber, true, context, null);
            thread.threadCreationObject.start();
            log.info("Started ScanWorkItemCreationThread for task: " + taskID);
        } // end synchronized

        // Redirect immediately to the results page
        String redirectUrl = "encounters/scanEndApplet.jsp?number=" + encNumber +
            "&taskID=" + taskID;
        if (rightScan) {
            redirectUrl += "&rightSide=true";
        }
        response.sendRedirect(redirectUrl);
    }
}
