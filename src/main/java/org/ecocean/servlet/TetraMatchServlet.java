package org.ecocean.servlet;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.SuperSpot;
import org.ecocean.grid.MatchObject;
import org.ecocean.grid.VertexPointMatch;
import org.ecocean.grid.tetra.TetraIndexManager;
import org.ecocean.grid.tetra.TetraQueryEngine;
import org.ecocean.shepherd.core.Shepherd;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Synchronous TETRA pattern matching servlet.
 * Returns ranked match results as JSON in a single request-response cycle,
 * replacing the queue-based scan pipeline.
 *
 * Parameters:
 *   encounterNumber - the encounter to match
 *   rightSide       - "true" for right-side spots, "false" (default) for left
 *
 * Response: JSON object with "matches" array and "meta" object.
 */
public class TetraMatchServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger(TetraMatchServlet.class.getName());

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
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        String encNumber = request.getParameter("encounterNumber");
        if (encNumber == null || encNumber.trim().isEmpty()) {
            sendError(out, response, 400, "Missing required parameter: encounterNumber");
            return;
        }

        boolean rightScan = "true".equals(request.getParameter("rightSide"));

        TetraIndexManager mgr = TetraIndexManager.getInstance();
        if (!mgr.isReady()) {
            sendError(out, response, 503,
                "TETRA index is not yet ready. The server may still be starting up.");
            return;
        }

        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("TetraMatchServlet.class");
        myShepherd.beginDBTransaction();

        try {
            Encounter enc = myShepherd.getEncounter(encNumber);
            if (enc == null) {
                sendError(out, response, 404, "Encounter not found: " + encNumber);
                return;
            }

            ArrayList<SuperSpot> spots = rightScan ?
                enc.getRightSpots() : enc.getSpots();

            if (spots == null || spots.size() < 4) {
                sendError(out, response, 400,
                    "Need at least 4 spots for matching. Found: " +
                    (spots == null ? 0 : spots.size()));
                return;
            }

            long startTime = System.currentTimeMillis();

            TetraQueryEngine engine = mgr.getQueryEngine();
            List<MatchObject> results = engine.match(spots, rightScan, encNumber);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("TETRA match for " + encNumber + " completed in " + elapsed +
                "ms with " + results.size() + " results");

            // Build JSON response
            JSONObject json = new JSONObject();

            JSONObject meta = new JSONObject();
            meta.put("queryEncounter", encNumber);
            meta.put("side", rightScan ? "right" : "left");
            meta.put("querySpots", spots.size());
            meta.put("matchTimeMs", elapsed);
            meta.put("totalResults", results.size());
            meta.put("leftIndexSize", mgr.getLeftIndex().getNumIndexedEncounters());
            meta.put("rightIndexSize", mgr.getRightIndex().getNumIndexedEncounters());
            json.put("meta", meta);

            JSONArray matchesArray = new JSONArray();
            int rank = 1;
            for (MatchObject mo : results) {
                JSONObject match = new JSONObject();
                match.put("rank", rank++);
                match.put("encounterNumber", mo.getEncounterNumber());
                match.put("individualName", mo.getIndividualName());
                match.put("matchValue", mo.getMatchValue());
                match.put("adjustedMatchValue", mo.getAdjustedMatchValue());
                match.put("score", mo.getMatchValue() * mo.getAdjustedMatchValue());
                match.put("evaluation", mo.getEvaluation());
                match.put("numMatchedPatterns", mo.numTriangles);
                match.put("date", mo.getDate());

                // Matched spot pairs
                VertexPointMatch[] scores = mo.getScores();
                JSONArray spotPairs = new JSONArray();
                for (VertexPointMatch vpm : scores) {
                    JSONObject pair = new JSONObject();
                    pair.put("queryX", vpm.getNewX());
                    pair.put("queryY", vpm.getNewY());
                    pair.put("catalogX", vpm.getOldX());
                    pair.put("catalogY", vpm.getOldY());
                    pair.put("votes", vpm.getPoints());
                    spotPairs.put(pair);
                }
                match.put("matchedSpots", spotPairs);
                matchesArray.put(match);
            }
            json.put("matches", matchesArray);

            out.print(json.toString(2));

        } catch (Exception e) {
            log.severe("TETRA match failed: " + e.getMessage());
            e.printStackTrace();
            sendError(out, response, 500, "Match failed: " + e.getMessage());
        } finally {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
        }
    }

    private void sendError(PrintWriter out, HttpServletResponse response,
                           int statusCode, String message) {
        response.setStatus(statusCode);
        JSONObject error = new JSONObject();
        error.put("error", message);
        out.print(error.toString());
    }
}
