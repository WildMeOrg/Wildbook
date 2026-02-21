package org.ecocean.servlet;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.SuperSpot;
import org.ecocean.grid.*;
import org.ecocean.grid.tetra.TetraIndexManager;
import org.ecocean.grid.tetra.TetraQueryEngine;
import org.ecocean.shepherd.core.Shepherd;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Hybrid TETRA+Groth pattern matching servlet.
 * Uses TETRA hash index as a fast pre-filter to find candidates,
 * then runs the full optimized Groth algorithm on each candidate
 * for accurate scoring. Writes results in the XML format expected
 * by scanEndApplet.jsp and redirects the user there.
 *
 * Parameters:
 *   encounterNumber - the encounter to match (required)
 *   rightSide       - "true" for right-side spots, "false" (default) for left
 */
public class HybridMatchServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger(HybridMatchServlet.class.getName());

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

        TetraIndexManager mgr = TetraIndexManager.getInstance();
        if (!mgr.isReady()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                "TETRA index is not yet ready. The server may still be starting up.");
            return;
        }

        // Read Groth parameters from config
        double epsilon, R, Sizelim, maxTriangleRotation, C;
        try {
            epsilon = Double.parseDouble(CommonConfiguration.getEpsilon(context));
            R = Double.parseDouble(CommonConfiguration.getR(context));
            Sizelim = Double.parseDouble(CommonConfiguration.getSizelim(context));
            maxTriangleRotation = Double.parseDouble(
                CommonConfiguration.getMaxTriangleRotation(context));
            C = Double.parseDouble(CommonConfiguration.getC(context));
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Invalid Groth parameters in configuration: " + e.getMessage());
            return;
        }

        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("HybridMatchServlet.class");
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

            long startTime = System.currentTimeMillis();

            // Step 1: TETRA pre-filter to get candidate encounter IDs
            TetraQueryEngine engine = mgr.getQueryEngine();
            List<MatchObject> tetraCandidates = engine.match(querySpots, rightScan, encNumber);

            long tetraTime = System.currentTimeMillis() - startTime;
            log.info("TETRA pre-filter for " + encNumber + ": " + tetraCandidates.size() +
                " candidates in " + tetraTime + "ms");

            // Step 2: Run full Groth on each TETRA candidate
            SuperSpot[] queryArray = querySpots.toArray(new SuperSpot[0]);
            List<MatchObject> grothResults = new ArrayList<>();

            for (MatchObject tetraMo : tetraCandidates) {
                String candidateEncId = tetraMo.getEncounterNumber();
                EncounterLite el = GridManager.getMatchGraphEncounterLiteEntry(candidateEncId);
                if (el == null) continue;

                MatchObject mo = el.getPointsForBestMatch(
                    queryArray, epsilon, R, Sizelim, maxTriangleRotation, C,
                    true, rightScan);

                // EncounterLite from matchGraph may not have encounterNumber set
                mo.encounterNumber = candidateEncId;
                grothResults.add(mo);
            }

            // Step 3: Sort by matchValue * adjustedMatchValue descending
            MatchObject[] matchArray = grothResults.toArray(new MatchObject[0]);
            Arrays.sort(matchArray, new MatchComparator());

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("Hybrid match for " + encNumber + " completed in " + totalTime +
                "ms (" + tetraTime + "ms TETRA + " + (totalTime - tetraTime) +
                "ms Groth on " + tetraCandidates.size() + " candidates)");

            // Step 4: Build XML document in WriteOutScanTask format
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
                        match.addAttribute("points",
                            String.valueOf(mo.getMatchValue()));
                        match.addAttribute("adjustedpoints",
                            String.valueOf(mo.getAdjustedMatchValue()));
                        match.addAttribute("pointBreakdown", mo.getPointBreakdown());

                        String finalscore = String.valueOf(
                            mo.getMatchValue() * mo.getAdjustedMatchValue());
                        if (finalscore.length() > 7) {
                            finalscore = finalscore.substring(0, 6);
                        }
                        match.addAttribute("finalscore", finalscore);

                        try {
                            match.addAttribute("logMStdDev",
                                String.valueOf(mo.getLogMStdDev()));
                        } catch (NumberFormatException nfe) {
                            match.addAttribute("logMStdDev", "<0.01");
                        }
                        match.addAttribute("evaluation", mo.getEvaluation());

                        // First encounter element: the catalog match
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
                            for (int k = 0; k < scores.length; k++) {
                                Element spot = enc1.addElement("spot");
                                spot.addAttribute("x", String.valueOf(scores[k].getOldX()));
                                spot.addAttribute("y", String.valueOf(scores[k].getOldY()));
                            }
                        } catch (NullPointerException npe) {}

                        // Second encounter element: the query encounter
                        Element enc2 = match.addElement("encounter");
                        enc2.addAttribute("number", encNumber);
                        enc2.addAttribute("date", enc.getDate());
                        enc2.addAttribute("sex",
                            enc.getSex() != null ? enc.getSex() : "unknown");
                        enc2.addAttribute("assignedToShark",
                            ServletUtilities.handleNullString(enc.getIndividualID()));
                        if (enc.getSizeAsDouble() != null) {
                            enc2.addAttribute("size", enc.getSize() + " meters");
                        } else {
                            enc2.addAttribute("size", "unknown");
                        }
                        enc2.addAttribute("location", enc.getLocation());
                        enc2.addAttribute("locationID", enc.getLocationID());

                        try {
                            for (int j = 0; j < scores.length; j++) {
                                Element spot = enc2.addElement("spot");
                                spot.addAttribute("x", String.valueOf(scores[j].getNewX()));
                                spot.addAttribute("y", String.valueOf(scores[j].getNewY()));
                            }
                        } catch (NullPointerException npe) {}

                        // Keywords in common
                        List<String> keywords = myShepherd.getKeywordsInCommon(
                            mo.getEncounterNumber(), encNumber);
                        if (keywords != null && keywords.size() > 0) {
                            Element kws = match.addElement("keywords");
                            for (int y = 0; y < keywords.size(); y++) {
                                Element keyword = kws.addElement("keyword");
                                keyword.addAttribute("name", keywords.get(y));
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // Step 5: Write XML to encounter directory
            String rootWebappPath = getServletContext().getRealPath("/");
            File webappsDir = new File(rootWebappPath).getParentFile();
            File shepherdDataDir = new File(webappsDir,
                CommonConfiguration.getDataDirectoryName(context));

            String fileAddition = rightScan ? "Right" : "";
            String thisEncDirString = Encounter.dir(shepherdDataDir, encNumber);
            File thisEncounterDir = new File(thisEncDirString);
            if (!thisEncounterDir.exists()) {
                thisEncounterDir.mkdirs();
            }

            File file = new File(thisEncDirString +
                "/lastFull" + fileAddition + "Scan.xml");
            log.info("Writing hybrid scan XML to: " + file.getAbsolutePath());

            FileWriter mywriter = new FileWriter(file);
            org.dom4j.io.OutputFormat format = org.dom4j.io.OutputFormat.createPrettyPrint();
            format.setLineSeparator(System.getProperty("line.separator"));
            org.dom4j.io.XMLWriter writer = new org.dom4j.io.XMLWriter(mywriter, format);
            writer.write(document);
            writer.close();

            // Step 6: Redirect to scanEndApplet.jsp
            String redirectUrl = "encounters/scanEndApplet.jsp?number=" + encNumber +
                "&writeThis=true";
            if (rightScan) {
                redirectUrl += "&rightSide=true";
            }
            response.sendRedirect(redirectUrl);

        } catch (Exception e) {
            log.severe("Hybrid match failed: " + e.getMessage());
            e.printStackTrace();
            myShepherd.rollbackDBTransaction();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Match failed: " + e.getMessage());
        } finally {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
        }
    }
}
