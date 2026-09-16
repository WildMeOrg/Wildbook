package org.ecocean.grid;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Vector;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.ecocean.Encounter;
import org.ecocean.SuperSpot;

import com.reijns.I3S.Pair;

/**
 * Writes an I3S spot-match result file (<code>lastFull[Right]I3SScan.xml</code>) for the live
 * synchronous scan. This revives the I3S output that was dropped when the asynchronous grid
 * scan path (ScanWorkItem / WriteOutScanTask.i3sWriteThis) fell out of use and the scan was
 * rewritten as the Groth-only {@link org.ecocean.servlet.GrothMatchServlet}.
 *
 * The XML shape matches what WriteOutScanTask.i3sWriteThis produced so that
 * <code>encounters/i3sScanEndApplet.jsp</code> can render it unchanged. Two differences,
 * deliberately, from the legacy writer:
 * <ul>
 *   <li>matched-spot coordinates are read from the exact {@link EncounterLite} objects that
 *       {@code i3sScan} indexed (the query lite + the catalog lites captured during the scan and
 *       passed in here) rather than by re-fetching from the DB or the mutable matchGraph, so
 *       {@link Pair#getM1()}/{@link Pair#getM2()} indices always line up with the spot order I3S
 *       actually compared even if the matchGraph entry is replaced mid-scan; and</li>
 *   <li>the 0.001 &lt; score &le; 2.0 validity filter is applied <em>before</em> the 100-match
 *       cap (the comparator sorts ascending and unscored candidates default to 0, so a
 *       cap-then-filter would let zeros consume the cap and yield an empty file).</li>
 * </ul>
 */
public final class I3SResultWriter {
    private I3SResultWriter() {}

    /**
     * @param matches         the scan results (each MatchObject carries its I3S score + point map)
     * @param queryLite       the query encounter as an EncounterLite (source of query spot coords)
     * @param catalogLites    encounterNumber -&gt; the exact catalog EncounterLite i3sScan indexed
     * @param num             the query encounter number
     * @param newEncDate      query encounter date
     * @param newEncSex       query encounter sex
     * @param newEncShark     query encounter assigned individual
     * @param newEncSize      query encounter size string
     * @param rightSide       true for a right-side scan
     * @param shepherdDataDir the Wildbook data directory
     * @return true if the file was written
     */
    public static boolean write(MatchObject[] matches, EncounterLite queryLite,
        Map<String, EncounterLite> catalogLites, String num,
        String newEncDate, String newEncSex, String newEncShark, String newEncSize,
        boolean rightSide, File shepherdDataDir) {
        if ((matches == null) || (queryLite == null) || (catalogLites == null)) return false;
        try {
            Arrays.sort(matches, new NewI3SMatchComparator());

            Document document = DocumentHelper.createDocument();
            Element root = document.addElement("matchSet");
            root.addAttribute("scanDate", (new java.util.Date()).toString());

            ArrayList<SuperSpot> querySpots = rightSide
                ? queryLite.getRightSpots() : queryLite.getSpots();
            if ((querySpots == null) || querySpots.isEmpty()) {
                // nothing to map query spots against; still write an (empty) file so the
                // results link renders a "no matches" page rather than 404ing.
                return writeDocument(document, num, rightSide, shepherdDataDir);
            }

            int emitted = 0;
            for (int i = 0; (i < matches.length) && (emitted < 100); i++) {
                MatchObject mo = matches[i];
                // Filter THEN cap: only valid I3S matches count toward the 100 limit.
                if (!((mo.getI3SMatchValue() > 0.001) && (mo.getI3SMatchValue() <= 2.0))) continue;
                Vector map = mo.getMap2();
                if ((map == null) || map.isEmpty()) continue;

                // Source catalog spots from the exact EncounterLite i3sScan indexed (captured
                // during the scan), not the live matchGraph which may have been replaced since.
                EncounterLite catalogLite = catalogLites.get(mo.getEncounterNumber());
                if (catalogLite == null) continue;
                ArrayList<SuperSpot> catalogSpots = rightSide
                    ? catalogLite.getRightSpots() : catalogLite.getSpots();
                if ((catalogSpots == null) || catalogSpots.isEmpty()) continue;

                try {
                    // Build the <match> subtree detached and attach it to root only after BOTH
                    // encounter spot loops succeed, so a mid-build failure (e.g. an unexpected
                    // index) can't leave a one-encounter <match> that breaks the viewer's
                    // encounters.get(1) assumption.
                    Element match = DocumentHelper.createElement("match");
                    String finalscore = Double.toString(mo.getI3SMatchValue());
                    if (finalscore.length() > 7) finalscore = finalscore.substring(0, 6);
                    match.addAttribute("finalscore", finalscore);
                    match.addAttribute("evaluation", mo.getEvaluation());

                    Element enc = match.addElement("encounter");
                    enc.addAttribute("number", mo.getEncounterNumber());
                    enc.addAttribute("date", mo.getDate());
                    enc.addAttribute("sex", (mo.getSex() != null) ? mo.getSex() : "unknown");
                    enc.addAttribute("assignedToShark", mo.getIndividualName());
                    enc.addAttribute("size", Double.toString(mo.getSize()));

                    int mapSize = map.size();
                    for (int f = 0; f < mapSize; f++) {
                        Pair tempPair = (Pair) map.get(f);
                        int M1 = tempPair.getM1();
                        Element spot = enc.addElement("spot");
                        spot.addAttribute("x",
                            Double.toString(catalogSpots.get(M1).getTheSpot().getCentroidX()));
                        spot.addAttribute("y",
                            Double.toString(catalogSpots.get(M1).getTheSpot().getCentroidY()));
                    }

                    Element enc2 = match.addElement("encounter");
                    enc2.addAttribute("number", num);
                    enc2.addAttribute("date", newEncDate);
                    enc2.addAttribute("sex", (newEncSex != null) ? newEncSex : "unknown");
                    enc2.addAttribute("assignedToShark", newEncShark);
                    enc2.addAttribute("size", newEncSize);
                    for (int g = 0; g < mapSize; g++) {
                        Pair tempPair = (Pair) map.get(g);
                        int M2 = tempPair.getM2();
                        Element spot = enc2.addElement("spot");
                        spot.addAttribute("x",
                            Double.toString(querySpots.get(M2).getTheSpot().getCentroidX()));
                        spot.addAttribute("y",
                            Double.toString(querySpots.get(M2).getTheSpot().getCentroidY()));
                    }
                    // both encounters fully populated — now attach to the document
                    root.add(match);
                    emitted++;
                } catch (Exception perMatch) {
                    // a single malformed match (e.g. an index that no longer fits because the
                    // catalog entry was re-spotted mid-scan) must not abort the whole file.
                    perMatch.printStackTrace();
                }
            }

            return writeDocument(document, num, rightSide, shepherdDataDir);
        } catch (Exception e) {
            System.out.println("I3SResultWriter: failed to write I3S results for " + num);
            e.printStackTrace();
            return false;
        }
    }

    private static boolean writeDocument(Document document, String num, boolean rightSide,
        File shepherdDataDir) throws java.io.IOException {
        String fileAddition = rightSide ? "Right" : "";
        File file = new File(Encounter.dir(shepherdDataDir, num)
            + "/lastFull" + fileAddition + "I3SScan.xml");
        FileWriter mywriter = new FileWriter(file);
        org.dom4j.io.XMLWriter writer = null;
        try {
            org.dom4j.io.OutputFormat format = org.dom4j.io.OutputFormat.createPrettyPrint();
            format.setLineSeparator(System.getProperty("line.separator"));
            writer = new org.dom4j.io.XMLWriter(mywriter, format);
            writer.write(document);
        } finally {
            // closing the XMLWriter closes the underlying FileWriter; if the XMLWriter never
            // got constructed, close the FileWriter directly so the handle can't leak.
            if (writer != null) {
                writer.close();
            } else {
                mywriter.close();
            }
        }
        return true;
    }
}
