package org.ecocean.servlet.export;

import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.ecocean.*;
import org.ecocean.genetics.*;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.security.HiddenEncReporter;

import javax.jdo.*;

import java.lang.StringBuffer;

import jxl.write.*;
import jxl.Workbook;

public class EncounterSearchExportExcelSimple extends HttpServlet {

    private static final int BYTES_DOWNLOAD = 1024;

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        String context = "context0";
        context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("EncounterSearchExportExcelSimple.class");

        
        String filename = "encounterSearchResults_export_" + request.getRemoteUser() + ".xls";

        String rootWebappPath = getServletContext().getRealPath("/");
        File webappsDir = new File(rootWebappPath).getParentFile();
        File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
        if (!shepherdDataDir.exists()) {
            shepherdDataDir.mkdirs();
        }
        File encountersDir = new File(shepherdDataDir.getAbsolutePath() + "/encounters");
        if (!encountersDir.exists()) {
            encountersDir.mkdirs();
        }
        
        File excelFile = new File(encountersDir.getAbsolutePath() + "/" + filename);
        
        
        try {
            FileOutputStream fos = new FileOutputStream(excelFile);
            OutputStreamWriter outp = new OutputStreamWriter(fos);
            try {
                myShepherd.beginDBTransaction();
                Vector rEncounters = new Vector();
                EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(myShepherd, request,
                "year descending, month descending, day descending");
                rEncounters = queryResult.getResult();

                HiddenEncReporter hiddenData = new HiddenEncReporter(rEncounters, request, myShepherd);

                int numMatchingEncounters = rEncounters.size();

                // load the optional locales
                Properties props = new Properties();
                try {
                    props = ShepherdProperties.getProperties("locationIDGPS.properties", "", context);

                } catch (Exception e) {
                    System.out.println("     Could not load locales.properties EncounterSearchExportExcelFile.");
                    e.printStackTrace();
                }

                WritableWorkbook workbook = Workbook.createWorkbook(excelFile);
                WritableSheet sheet = workbook.createSheet("Search Results", 0);


                // headers only, columns processed in loop below
                final String[] headerLabels = {
                    "Date Last Modified",
                    "Institution Code",
                    "Collection Code",
                    "Catalog Number",
                    "Record URL",
                    "Scientific Name",
                    "Basis of record",
                    "Citation",
                    "Kingdom",
                    "Phylum",
                    "Class",
                    "Order",
                    "Family",
                    "Genus",
                    "species",
                    "Year Collected",
                    "Month Collected",
                    "Day Collected",
                    "Time of Day",
                    "Locality",
                    "Longitude",
                    "Latitude",
                    "Sex",
                    "Notes",
                    "Length (m)",
                    "Marked Individual",
                    "Location ID",
                    "Submitter Email Address",
                    "Date Encounter Submitted",
                    "Has Left Spot Image",
                    "Has Right Spot Image"                    
                };

                for (int i=0;i<headerLabels.length;i++) {
                    Label label = new Label(i, 0, headerLabels[i]);
                    sheet.addCell(label);
                }

                // Excel export =========================================================
                int count = 0;

                for (int i = 0; i < numMatchingEncounters; i++) {
                    Encounter enc = (Encounter) rEncounters.get(i);
                    if (hiddenData.contains(enc))
                        continue;
                    count++;

                    int colCount = 0;

                    Label labelModified = new Label(colCount, count, enc.getDWCDateLastModified());
                    sheet.addCell(labelModified);
                    colCount++;

                    Label labelIntCode = new Label(colCount, count, CommonConfiguration.getProperty("institutionCode", context));
                    sheet.addCell(labelIntCode);
                    colCount++;

                    Label labelCatalogCode = new Label(colCount, count, CommonConfiguration.getProperty("catalogCode", context));
                    sheet.addCell(labelCatalogCode);
                    colCount++;

                    Label labelEncId = new Label(colCount, count, enc.getEncounterNumber());
                    sheet.addCell(labelEncId);
                    colCount++;

                    Label lNumberx4 = new Label(colCount, count, ("http://" + CommonConfiguration.getURLLocation(request)
                            + "/encounters/encounter.jsp?number=" + enc.getEncounterNumber()));
                    sheet.addCell(lNumberx4);
                    colCount++;

                    if ((enc.getGenus() != null) && (enc.getSpecificEpithet() != null)) {
                        Label lNumberx5 = new Label(colCount, count, (enc.getGenus() + " " + enc.getSpecificEpithet()));
                        sheet.addCell(lNumberx5);
                    } else if (CommonConfiguration.getProperty("genusSpecies0", context) != null) {
                        Label lNumberx5 = new Label(colCount, count,
                                (CommonConfiguration.getProperty("genusSpecies0", context)));
                        sheet.addCell(lNumberx5);
                    }
                    colCount++;
                    
                    Label labelP = new Label(colCount, count, "P");
                    sheet.addCell(labelP);
                    colCount++;

                    Label lNumberx7 = new Label(colCount, count, CommonConfiguration.getProperty("citation", context));
                    sheet.addCell(lNumberx7);
                    colCount++;

                    Label lNumberx8 = new Label(colCount, count, CommonConfiguration.getProperty("kingdom", context));
                    sheet.addCell(lNumberx8);
                    colCount++;

                    Label lNumberx9 = new Label(colCount, count, CommonConfiguration.getProperty("phylum", context));
                    sheet.addCell(lNumberx9);
                    colCount++;

                    Label lNumberx10 = new Label(colCount, count, CommonConfiguration.getProperty("class", context));
                    sheet.addCell(lNumberx10);
                    colCount++;

                    Label lNumberx11 = new Label(colCount, count, CommonConfiguration.getProperty("order", context));
                    sheet.addCell(lNumberx11);
                    colCount++;

                    Label lNumberx12 = new Label(colCount, count, CommonConfiguration.getProperty("family", context));
                    sheet.addCell(lNumberx12);
                    colCount++;

                    Label labelGenus = null;
                    Label labelSpecies = null;
                    if ((enc.getGenus() != null) && (enc.getSpecificEpithet() != null)) {
                        labelGenus = new Label(colCount, count, enc.getGenus());
                        sheet.addCell(labelGenus);

                        labelSpecies = new Label(colCount+1, count, enc.getSpecificEpithet());
                        sheet.addCell(labelSpecies);

                    } else if (CommonConfiguration.getProperty("genusSpecies0", context) != null) {
                        StringTokenizer str = new StringTokenizer(
                                CommonConfiguration.getProperty("genusSpecies0", context), " ");
                        if (str.countTokens() > 1) {
                            labelGenus = new Label(colCount, count, str.nextToken());
                            sheet.addCell(labelGenus);

                            labelSpecies = new Label(colCount+1, count, str.nextToken());
                            sheet.addCell(labelSpecies);
                        }
                    }
                    colCount+=2;

                    if (enc.getYear() > 0) {
                        Label label = new Label(colCount, count, Integer.toString(enc.getYear()));
                        sheet.addCell(label);
                    }
                    colCount++;

                    if (enc.getMonth() > 0) {
                        Label label = new Label(colCount, count, Integer.toString(enc.getMonth()));
                        sheet.addCell(label);
                    }
                    colCount++;

                    if (enc.getDay() > 0) {
                        Label label = new Label(colCount, count, Integer.toString(enc.getDay()));
                        sheet.addCell(label);
                    }
                    colCount++;

                    if (enc.getHour() > -1) {
                        Label label = new Label(colCount, count, (enc.getHour() + ":" + enc.getMinutes()));
                        sheet.addCell(label);
                    }
                    colCount++;

                    Label label = new Label(colCount, count, enc.getLocation());
                    sheet.addCell(label);
                    colCount++;

                    if (enc.getDWCDecimalLongitude() != null && enc.getDWCDecimalLatitude() != null) {
                        Label labelLon = new Label(colCount, count, enc.getDWCDecimalLongitude());
                        sheet.addCell(labelLon);
                        Label labelLat  = new Label(colCount, count, enc.getDWCDecimalLatitude());
                        sheet.addCell(labelLat);

                    } else if (enc.getLocationCode() != null && !enc.getLocationCode().equals("")) {
                        try {
                            String lc = enc.getLocationCode();
                            if (props.getProperty(lc) != null) {
                                String gps = props.getProperty(lc);
                                StringTokenizer st = new StringTokenizer(gps, ",");
                                Label lNumberx24 = new Label(colCount, count, st.nextToken());
                                sheet.addCell(lNumberx24);
                                Label lNumberx25 = new Label(colCount+1, count, st.nextToken());
                                sheet.addCell(lNumberx25);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.out.println("     I hit an error getting locales in searchResults.jsp.");
                        }
                    }
                    colCount+=2;

                    if ((enc.getSex() != null) && (!enc.getSex().equals("unknown"))) {
                        Label lSex = new Label(colCount, count, enc.getSex());
                        sheet.addCell(lSex);
                    }
                    colCount++;
                    
                    if (enc.getComments() != null) {
                        Label lNumberx27 = new Label(colCount, count,
                                enc.getComments().replace("<br>", ". ").replaceAll("\n", "").replaceAll("\r", ""));
                        sheet.addCell(lNumberx27);
                    }
                    colCount++;
                    
                    if (enc.getSizeAsDouble() != null) {
                        Label lNumberx28 = new Label(colCount, count, enc.getSizeAsDouble().toString());
                        sheet.addCell(lNumberx28);
                    }
                    colCount++;

                    if (enc.getIndividual() != null) {
                        Label lNumberx29 = new Label(colCount, count, enc.getIndividual().getDisplayName());
                        sheet.addCell(lNumberx29);
                    }
                    colCount++;

                    if (enc.getLocationCode() != null) {
                        Label lNumberx30 = new Label(colCount, count, enc.getLocationCode());
                        sheet.addCell(lNumberx30);
                    }
                    colCount++;

                    if (enc.getSubmitterEmail() != null) {
                        Label lNumberx31 = new Label(colCount, count, enc.getSubmitterEmail());
                        sheet.addCell(lNumberx31);
                    }
                    colCount++;
                    
                    if (enc.getDWCDateAdded() != null) {
                        Label lNumberx32 = new Label(colCount, count, enc.getDWCDateAdded());
                        sheet.addCell(lNumberx32);
                    }
                    colCount++;


                    if (enc.hasAnnotations()) {
                        String labelBool = "false";
                        if (enc.hasLeftSpotImage()) labelBool = "true";
                        Label lNumberx33 = new Label(colCount, count, labelBool);
                        sheet.addCell(lNumberx33);
                    }
                    colCount++;

                    if (enc.hasAnnotations()) {
                        String labelBool = "false";
                        if (enc.hasRightSpotImage()) labelBool = "true";
                        Label lNumberx34 = new Label(colCount, count, labelBool);
                        sheet.addCell(lNumberx34);
                    }
                    colCount++;


                } // end for loop iterating encounters

                hiddenData.writeHiddenDataReport(workbook);

                workbook.write();
                workbook.close();

                outp.close();
                outp = null;

            } catch (Exception ioe) {
                ioe.printStackTrace();
                response.setContentType("text/html");
                PrintWriter out = response.getWriter();
                out.println(ServletUtilities.getHeader(request));
                out.println(
                        "<html><body><p><strong>Error encountered</strong> with file writing. Check the relevant log.</p>");
                out.println(
                        "<p>Please let the webmaster know you encountered an error at: EncounterSearchExportExcelSimple servlet</p></body></html>");
                out.println(ServletUtilities.getFooter(context));
                out.close();
                outp.close();
                outp = null;
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.setContentType("text/html");
            PrintWriter out = response.getWriter();
            out.println(ServletUtilities.getHeader(request));
            out.println("<html><body><p><strong>Error encountered</strong></p>");
            out.println(
                    "<p>Please let the webmaster know you encountered an error at: EncounterSearchExportExcelSimple servlet</p></body></html>");
            out.println(ServletUtilities.getFooter(context));
            out.close();
        }
        
        OutputStream os = response.getOutputStream();
        InputStream is = new FileInputStream(excelFile);
        try {
            myShepherd.rollbackDBTransaction();
            response.setContentType("application/msexcel");
            response.setHeader("Content-Disposition", "attachment;filename=" + filename);
            //ServletContext ctx = getServletContext();
            // InputStream is = ctx.getResourceAsStream("/encounters/"+filename);
    
            int read = 0;
            byte[] bytes = new byte[BYTES_DOWNLOAD];
    
            while ((read = is.read(bytes)) != -1) {
                os.write(bytes, 0, read);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            is.close();
            os.flush();
            os.close();
            myShepherd.closeDBTransaction();
        }

    }

}
