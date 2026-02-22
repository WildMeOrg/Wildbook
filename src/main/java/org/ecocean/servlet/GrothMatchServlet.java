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
import java.util.List;
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

            long startTime = System.currentTimeMillis();

            // Run Groth against all encounters in the matchGraph
            SuperSpot[] queryArray = querySpots.toArray(new SuperSpot[0]);
            ConcurrentHashMap<String, EncounterLite> matchGraph = GridManager.getMatchGraph();
            List<MatchObject> grothResults = new ArrayList<>();

            for (ConcurrentHashMap.Entry<String, EncounterLite> entry : matchGraph.entrySet()) {
                if (entry.getKey().equals(encNumber)) continue;
                EncounterLite el = entry.getValue();

                MatchObject mo = el.getPointsForBestMatch(
                    queryArray, epsilon, R, Sizelim, maxTriangleRotation, C,
                    true, rightScan);
                mo.encounterNumber = entry.getKey();
                grothResults.add(mo);
            }

            // Sort by matchValue * adjustedMatchValue descending
            MatchObject[] matchArray = grothResults.toArray(new MatchObject[0]);
            Arrays.sort(matchArray, new MatchComparator());

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("Groth match for " + encNumber + " completed in " + totalTime +
                "ms (" + matchGraph.size() + " catalog encounters)");

            // Build XML document in WriteOutScanTask format
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

            // Write XML to encounter directory
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
            log.info("Writing Groth scan XML to: " + file.getAbsolutePath());

            FileWriter mywriter = new FileWriter(file);
            org.dom4j.io.OutputFormat format = org.dom4j.io.OutputFormat.createPrettyPrint();
            format.setLineSeparator(System.getProperty("line.separator"));
            org.dom4j.io.XMLWriter writer = new org.dom4j.io.XMLWriter(mywriter, format);
            writer.write(document);
            writer.close();

            // Redirect to scanEndApplet.jsp
            String redirectUrl = "encounters/scanEndApplet.jsp?number=" + encNumber +
                "&writeThis=true";
            if (rightScan) {
                redirectUrl += "&rightSide=true";
            }
            response.sendRedirect(redirectUrl);

        } catch (Exception e) {
            log.severe("Groth match failed: " + e.getMessage());
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
