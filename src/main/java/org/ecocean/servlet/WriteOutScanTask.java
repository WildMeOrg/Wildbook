package org.ecocean.servlet;

import com.reijns.I3S.Pair;
import jxl.write.WritableWorkbook;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.grid.*;
import org.ecocean.Shepherd;
import org.ecocean.SuperSpot;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

public class WriteOutScanTask extends HttpServlet {
    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        // set up a shepherd for DB transactions

        String context = "context0";

        context = ServletUtilities.getContext(request);

        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("WriteOutScanTask.class");
        PrintWriter out = null;
        GridManager gm = GridManagerFactory.getGridManager();

        // if ((!request.getParameter("number").equals("TuningTask")) && (!request.getParameter("number").equals("FalseMatchTask"))) {
        double cutoff = 2;
        String statusText = "success";
        System.out.println("writeOutScanTask: I am starting up.");

        myShepherd.beginDBTransaction();
        try {
            ScanTask st2 = myShepherd.getScanTask(request.getParameter("number"));
            st2.setFinished(true);
            long time = System.currentTimeMillis();
            st2.setEndTime(time);

            // let's check the checked-in value

            System.out.println("Now setting this scanTask as finished!");
            String taskID = st2.getUniqueNumber();

            // change
            String encNumber = request.getParameter("number").substring(5);
            String newEncDate = "";
            String newEncShark = "";
            String newEncSize = "";
            Encounter newEnc = myShepherd.getEncounter(encNumber);
            newEncDate = newEnc.getDate();
            if (newEnc.getIndividualID() != null) {
                newEncShark = newEnc.getIndividualID();
            }
            if (newEnc.getSizeAsDouble() != null) { newEncSize = newEnc.getSize() + " meters"; }
            MatchObject[] res = new MatchObject[0];

            res = gm.getMatchObjectsForTask(taskID).toArray(res);

            boolean righty = false;
            if (taskID.startsWith("scanR")) {
                righty = true;
            }
            boolean successfulWrite = writeResult(res, encNumber, CommonConfiguration.getR(context),
                CommonConfiguration.getEpsilon(context), CommonConfiguration.getSizelim(context),
                CommonConfiguration.getMaxTriangleRotation(context),
                CommonConfiguration.getC(context), newEncDate, newEncShark, newEncSize, righty,
                cutoff, myShepherd, context, "", null);
            boolean successfulI3SWrite = i3sWriteThis(myShepherd, res, encNumber, newEncDate,
                newEncShark, newEncSize, righty, 2.5, context);
            boolean successfulLocationIDWrite = false;
            // let's write out the results for the same locationID
            if ((st2.getLocationIDFilters() != null) && (st2.getLocationIDFilters().size() > 0)) {
                ArrayList<MatchObject> resLocationID = gm.getMatchObjectsForTask(taskID);
                ArrayList<String> locs = st2.getLocationIDFilters();
                ArrayList<MatchObject> filtered = new ArrayList<MatchObject>();
                MatchObject[] resLoc2 = new MatchObject[0];
                resLoc2 = resLocationID.toArray(resLoc2);
                Arrays.sort(resLoc2, new MatchComparator());

                int resSize = resLoc2.length;
                for (int f = 0; f < resSize; f++) {
                    if (filtered.size() < 50) {
                        String moEncNum = resLoc2[f].getEncounterNumber();
                        Encounter localEnc = myShepherd.getEncounter(moEncNum);
                        if (localEnc != null && localEnc.getLocationID() != null &&
                            locs.contains(localEnc.getLocationID())) {
                            filtered.add(resLoc2[f]);
                            // System.out.println("        Found a "+localEnc.getLocationID()+" encounter! ("+filtered.size()+")");
                        }
                    }
                }
                if (filtered.size() > 0) {
                    MatchObject[] resLoc = new MatchObject[filtered.size()];
                    resLoc = filtered.toArray(resLoc);
                    Arrays.sort(resLoc, new MatchComparator());
                    // System.out.println("resLoc="+resLoc.length+"; filtered="+filtered.size());
                    successfulLocationIDWrite = writeResult(resLoc, encNumber,
                        CommonConfiguration.getR(context), CommonConfiguration.getEpsilon(context),
                        CommonConfiguration.getSizelim(context),
                        CommonConfiguration.getMaxTriangleRotation(context),
                        CommonConfiguration.getC(context), newEncDate, newEncShark, newEncSize,
                        righty, cutoff, myShepherd, context, "LocationID", locs);
                }
            }
            // write out the boosted results
            // if(request.getParameter("boost")!=null){
            // Properties props = new Properties();
            // props = generateBoostedResults(res, encNumber, myShepherd);
            // successfulWrite=writeBoostedResult(encNumber, res, encNumber, newEncDate, newEncShark, newEncSize, righty, cutoff, myShepherd, props);
            // }

            myShepherd.commitDBTransaction();

            // let's cleanup after a successful commit
            ThreadPoolExecutor es = SharkGridThreadExecutorService.getExecutorService();
            es.execute(new ScanTaskCleanupThread(request.getParameter("number")));

            // let's go see the written results
            // String sideAddition = "";
            // if (request.getParameter("number").indexOf("scanR") != -1) {
            // sideAddition = "&rightSide=true";
            // }
            // String resultsURL = ("http://" + CommonConfiguration.getURLLocation(request) + "/encounters/scanEndApplet.jsp?number=" +
            // request.getParameter("number").substring(5) + "&writeThis=true" + sideAddition);
            // response.sendRedirect(resultsURL);

            statusText = "success";
        } catch (Exception e) {
            myShepherd.rollbackDBTransaction();
            System.out.println("scanResultsServlet registered the following error...");
            e.printStackTrace();
            statusText = "failure";
        } finally {
            myShepherd.closeDBTransaction();
            response.setContentType("text/plain");
            out = response.getWriter();
            out.println(statusText);
            out.close();
        }
    }

    public boolean writeResult(MatchObject[] swirs, String num, String R, String epsilon,
        String Sizelim, String maxTriangleRotation, String C, String newEncDate, String newEncShark,
        String newEncSize, boolean rightSide, double cutoff, Shepherd myShepherd, String context,
        String fileCustomizationString, List<String> locationIDs) {
        try {
            // System.out.println("Prepping to write XML file for encounter "+num);

            // now setup the XML write for the encounter
            int resultsSize = swirs.length;
            MatchObject[] matches = swirs;

            Arrays.sort(matches, new MatchComparator());
            StringBuffer resultsXML = new StringBuffer();
            Document document = DocumentHelper.createDocument();
            Element root = document.addElement("matchSet");
            root.addAttribute("scanDate", (new java.util.Date()).toString());
            root.addAttribute("R", R);
            root.addAttribute("epsilon", epsilon);
            root.addAttribute("Sizelim", Sizelim);
            root.addAttribute("maxTriangleRotation", maxTriangleRotation);
            root.addAttribute("C", C);
            if (locationIDs != null && locationIDs.size() > 0)
                root.addAttribute("locationID", locationIDs.toString());
            int numMatches = matches.length;
            // hard limit this to 100 matches...no human really goes beyond this...
            if (numMatches > 100) numMatches = 100;
            for (int i = 0; i < numMatches; i++) {
                try {
                    MatchObject mo = matches[i];
                    if ((mo.getMatchValue() > 0) &&
                        ((mo.getMatchValue() * mo.getAdjustedMatchValue()) > 2)) {
                        Element match = root.addElement("match");
                        match.addAttribute("points", (new Double(mo.getMatchValue())).toString());
                        match.addAttribute("adjustedpoints",
                            (new Double(mo.getAdjustedMatchValue())).toString());
                        match.addAttribute("pointBreakdown", mo.getPointBreakdown());
                        String finalscore = (new Double(mo.getMatchValue() *
                            mo.getAdjustedMatchValue())).toString();
                        if (finalscore.length() > 7) {
                            finalscore = finalscore.substring(0, 6);
                        }
                        match.addAttribute("finalscore", finalscore);

                        // check if logM is very small...
                        try {
                            match.addAttribute("logMStdDev",
                                (new Double(mo.getLogMStdDev())).toString());
                        } catch (java.lang.NumberFormatException nfe) {
                            match.addAttribute("logMStdDev", "<0.01");
                        }
                        match.addAttribute("evaluation", mo.getEvaluation());

                        Encounter firstEnc = myShepherd.getEncounter(mo.getEncounterNumber());
                        Element enc = match.addElement("encounter");
                        enc.addAttribute("number", firstEnc.getEncounterNumber());
                        enc.addAttribute("date", firstEnc.getDate());
                        if (firstEnc.getSex() != null) {
                            enc.addAttribute("sex", firstEnc.getSex());
                        } else { enc.addAttribute("sex", "unknown"); }
                        enc.addAttribute("assignedToShark",
                            ServletUtilities.handleNullString(firstEnc.getIndividualID()));
                        if (firstEnc.getSizeAsDouble() != null) {
                            enc.addAttribute("size", (firstEnc.getSize() + " meters"));
                        }
                        enc.addAttribute("location", firstEnc.getLocation());
                        enc.addAttribute("locationID", firstEnc.getLocationID());
                        VertexPointMatch[] firstScores = mo.getScores();
                        try {
                            for (int k = 0; k < firstScores.length; k++) {
                                Element spot = enc.addElement("spot");
                                spot.addAttribute("x",
                                    (new Double(firstScores[k].getOldX())).toString());
                                spot.addAttribute("y",
                                    (new Double(firstScores[k].getOldY())).toString());
                            }
                        } catch (NullPointerException npe) {}
                        Element enc2 = match.addElement("encounter");
                        Encounter secondEnc = myShepherd.getEncounter(num);
                        enc2.addAttribute("number", num);
                        enc2.addAttribute("date", secondEnc.getDate());
                        // enc2.addAttribute("sex", secondEnc.getSex());
                        if (secondEnc.getSex() != null) {
                            enc2.addAttribute("sex", secondEnc.getSex());
                        } else { enc2.addAttribute("sex", "unknown"); }
                        enc2.addAttribute("assignedToShark",
                            ServletUtilities.handleNullString(secondEnc.getIndividualID()));
                        if (secondEnc.getSizeAsDouble() != null) {
                            enc2.addAttribute("size", (secondEnc.getSize() + " meters"));
                        } else { enc2.addAttribute("size", "unknown"); }
                        enc2.addAttribute("location", secondEnc.getLocation());
                        enc2.addAttribute("locationID", secondEnc.getLocationID());
                        try {
                            for (int j = 0; j < firstScores.length; j++) {
                                Element spot = enc2.addElement("spot");
                                spot.addAttribute("x",
                                    (new Double(firstScores[j].getNewX())).toString());
                                spot.addAttribute("y",
                                    (new Double(firstScores[j].getNewY())).toString());
                            }
                        } catch (NullPointerException npe) {}
                        // let's find the keywords in common
                        List<String> keywords = myShepherd.getKeywordsInCommon(
                            mo.getEncounterNumber(), num);
                        int keywordsSize = keywords.size();
                        if (keywordsSize > 0) {
                            Element kws = match.addElement("keywords");
                            for (int y = 0; y < keywordsSize; y++) {
                                Element keyword = kws.addElement("keyword");
                                keyword.addAttribute("name", ((String)keywords.get(y)));
                            }
                        }
                    } // end if
                } catch (Exception finale) { finale.printStackTrace(); }
            } // end for

            // prep for writing out the XML

            // in case this is a right-side scan, change file name to save to
            String fileAddition = "";
            if (rightSide) {
                fileAddition = "Right";
            }
            // setup data dir
            String rootWebappPath = getServletContext().getRealPath("/");
            File webappsDir = new File(rootWebappPath).getParentFile();
            File shepherdDataDir = new File(webappsDir,
                CommonConfiguration.getDataDirectoryName(context));
            // if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
            File encountersDir = new File(shepherdDataDir.getAbsolutePath() + "/encounters");
            // if(!encountersDir.exists()){encountersDir.mkdirs();}
            String thisEncDirString = Encounter.dir(shepherdDataDir, num);
            File thisEncounterDir = new File(thisEncDirString);
            if (!thisEncounterDir.exists()) {
                thisEncounterDir.mkdirs();
                System.out.println("I am making the encDir: " + thisEncDirString);
            }
            // File file=new File((new
            // File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFull"+fileAddition+"Scan.xml");
            File file = new File(Encounter.dir(shepherdDataDir,
                num) + "/lastFull" + fileAddition + fileCustomizationString + "Scan.xml");

            System.out.println("Writing scanTask XML file to: " + file.getAbsolutePath());

            FileWriter mywriter = new FileWriter(file);
            org.dom4j.io.OutputFormat format = org.dom4j.io.OutputFormat.createPrettyPrint();
            format.setLineSeparator(System.getProperty("line.separator"));
            org.dom4j.io.XMLWriter writer = new org.dom4j.io.XMLWriter(mywriter, format);
            writer.write(document);
            writer.close();
            System.out.println("Successful write.");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    } // end writeResult method

    public boolean i3sWriteThis(Shepherd myShepherd, MatchObject[] matches, String num,
        String newEncDate, String newEncShark, String newEncSize, boolean rightSide, double cutoff,
        String context) {
        try {
            System.out.println(
                "scanWorkItemResultsHandler: Prepping to write I3S XML file for encounter " + num);

            // now setup the XML write for the encounter
            // int resultsSize=results.size();

            Arrays.sort(matches, new NewI3SMatchComparator());
            StringBuffer resultsXML = new StringBuffer();
            Document document = DocumentHelper.createDocument();
            Element root = document.addElement("matchSet");
            root.addAttribute("scanDate", (new java.util.Date()).toString());
            // System.out.println("Total num matches for I3S printing: "+matches.length);

            int numMatches = matches.length;
            // hard limit this to 100 matches...no human really goes beyond this...
            if (numMatches > 100) numMatches = 100;
            for (int i = 0; i < numMatches; i++) {
                try {
                    // System.out.println();
                    MatchObject mo = matches[i];
                    // System.out.println("I3S match value: "+mo.getI3SMatchValue());
                    if ((mo.getI3SMatchValue() > 0.001) && (mo.getI3SMatchValue() <= 2.0)) {
                        Element match = root.addElement("match");
                        String finalscore = (new Double(mo.getI3SMatchValue())).toString();
                        if (finalscore.length() > 7) {
                            finalscore = finalscore.substring(0, 6);
                        }
                        match.addAttribute("finalscore", finalscore);
                        match.addAttribute("evaluation", mo.getEvaluation());

                        Element enc = match.addElement("encounter");
                        enc.addAttribute("number", mo.getEncounterNumber());
                        enc.addAttribute("date", mo.getDate());
                        if (mo.getSex() != null) { enc.addAttribute("sex", mo.getSex()); } else {
                            enc.addAttribute("sex", "unknown");
                        }
                        enc.addAttribute("assignedToShark", mo.getIndividualName());
                        enc.addAttribute("size", (new Double(mo.getSize())).toString());

                        // get the Map
                        Vector map = mo.getMap2();
                        int mapSize = map.size();
                        Encounter e1 = myShepherd.getEncounter(mo.getEncounterNumber());
                        for (int f = 0; f < mapSize; f++) {
                            Pair tempPair = (com.reijns.I3S.Pair)map.get(f);
                            int M1 = tempPair.getM1();
                            ArrayList<SuperSpot> spts = new ArrayList<SuperSpot>();
                            if (rightSide) {
                                spts = e1.getRightSpots();
                            } else {
                                spts = e1.getSpots();
                            }
                            // System.out.println("scanWorkItemResultsHandler: I3S spots: "+spts.size()+" vs mapSize: "+mapSize);
                            Element spot = enc.addElement("spot");
                            spot.addAttribute("x",
                                (new Double(spts.get(M1).getTheSpot().getCentroidX())).toString());
                            spot.addAttribute("y",
                                (new Double(spts.get(M1).getTheSpot().getCentroidY())).toString());
                        }
                        Element enc2 = match.addElement("encounter");
                        enc2.addAttribute("number", num);
                        enc2.addAttribute("date", newEncDate);
                        enc2.addAttribute("sex", mo.getNewSex());
                        enc2.addAttribute("assignedToShark", newEncShark);
                        enc2.addAttribute("size", newEncSize);

                        // reset the Iterator
                        Encounter e2 = myShepherd.getEncounter(num);
                        for (int g = 0; g < mapSize; g++) {
                            Pair tempPair = (com.reijns.I3S.Pair)map.get(g);
                            int M2 = tempPair.getM2();
                            ArrayList<SuperSpot> spts = new ArrayList<SuperSpot>();
                            if (rightSide) {
                                spts = e2.getRightSpots();
                            } else {
                                spts = e2.getSpots();
                            }
                            Element spot = enc2.addElement("spot");
                            // System.out.println("scanWorkItemResultsHandler: I3S next spots: "+spts.size()+" vs mapSize: "+mapSize);
                            spot.addAttribute("x",
                                (new Double(spts.get(M2).getTheSpot().getCentroidX())).toString());
                            spot.addAttribute("y",
                                (new Double(spts.get(M2).getTheSpot().getCentroidY())).toString());
                        }
                    }
                } catch (NullPointerException npe) {
                    npe.printStackTrace();
                }
            }
            // prep for writing out the XML

            // in case this is a right-side scan, change file name to save to
            String fileAddition = "";
            if (rightSide) {
                fileAddition = "Right";
            }
            // setup data dir
            String rootWebappPath = getServletContext().getRealPath("/");
            File webappsDir = new File(rootWebappPath).getParentFile();
            File shepherdDataDir = new File(webappsDir,
                CommonConfiguration.getDataDirectoryName(context));
            // if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
            File encountersDir = new File(shepherdDataDir.getAbsolutePath() + "/encounters");
            // if(!encountersDir.exists()){encountersDir.mkdirs();}

            // File file=new File((new
            // File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFull"+fileAddition+"I3SScan.xml");
            File file = new File(Encounter.dir(shepherdDataDir,
                num) + "/lastFull" + fileAddition + "I3SScan.xml");
            FileWriter mywriter = new FileWriter(file);
            org.dom4j.io.OutputFormat format = org.dom4j.io.OutputFormat.createPrettyPrint();
            format.setLineSeparator(System.getProperty("line.separator"));
            org.dom4j.io.XMLWriter writer = new org.dom4j.io.XMLWriter(mywriter, format);
            writer.write(document);
            writer.close();
            System.out.println("writeOutScanTask: Successful I3S write.");
            return true;
        } catch (Exception e) {
            System.out.println("writeOutScanTask: Failed to write out I3S results!");
            e.printStackTrace();
            return false;
        }
    }

    public void finalize(WritableWorkbook workbook) {
        try {
            workbook.write();
        } catch (Exception e) {
            System.out.println("Unknown error writing output Excel file for TuningTask...");
            e.printStackTrace();
        }
    }

    public ScanWorkItem getWI4MO(ScanWorkItemResult swir, ArrayList<ScanWorkItem> list) {
        // System.out.println("I'm looking for: "+swir.getUniqueNumberWorkItem());

        ScanWorkItem swi = new ScanWorkItem();

        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getUniqueNumber().equals(swir.getUniqueNumberWorkItem())) {
                return list.get(i);
            }
        }
        return swi;
    }

    public void writeBoostFiles(StringBuffer train, StringBuffer test, String number) {
        try {
            // write out the training file .train
            // File file1=new File((new
            // File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"appadmin"+File.separator+"boostedResults_"+number+".train");
            File file1 = new File(getServletContext().getRealPath(("/appadmin/" +
                "boostedResults_" + number + ".train")));

            // use buffering
            Writer output = new BufferedWriter(new FileWriter(file1));
            try {
                // FileWriter always assumes default encoding is OK!
                output.write(train.toString());
            } finally {
                output.close();
            }
            File file2 = new File(getServletContext().getRealPath(("/appadmin/" +
                "boostedResults_" + number + ".test")));

            // use buffering
            Writer output2 = new BufferedWriter(new FileWriter(file2));
            try {
                // FileWriter always assumes default encoding is OK!
                output2.write(test.toString());
            } finally {
                output2.close();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.out.println("Failed to write out the training and test files for JBoost...");
        }
    }
}
