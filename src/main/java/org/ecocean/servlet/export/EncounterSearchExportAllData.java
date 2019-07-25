package org.ecocean.servlet.export;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.ecocean.*;
import org.ecocean.media.*;
import org.ecocean.genetics.*;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.tag.MetalTag;

import javax.jdo.*;

import java.lang.StringBuffer;
import java.text.SimpleDateFormat;

import jxl.write.*;
import jxl.Workbook;


public class EncounterSearchExportAllData extends HttpServlet{

  private static final int BYTES_DOWNLOAD = 1024;


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
    doPost(request, response);
  } 

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{

    //set the response
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);

    Vector rEncounters = new Vector();
    int numResults = 0;

    //set up the files
    String filename = "encounterSearchResults_export_" + request.getRemoteUser() + ".xls";

    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File individualsDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    if(!individualsDir.exists()) {
        individualsDir.mkdirs();
    }

    File excelFile = new File(individualsDir.getAbsolutePath()+"/"+ filename);

    myShepherd.beginDBTransaction();

    try {

      //set up the output stream
      FileOutputStream fos = new FileOutputStream(excelFile);
      OutputStreamWriter outp = new OutputStreamWriter(fos);

      try{

        EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(myShepherd, request, "dateInMilliseconds descending");
        rEncounters = queryResult.getResult();

        int numMatchingEncounters=rEncounters.size();

        //load the optional locales
        Properties props = new Properties();
        try {
          props=ShepherdProperties.getProperties("locationIDGPS.properties", "",context);
        } catch (Exception e) {
          System.out.println("     Could not load locales.properties EncounterSearchExportAllData.");
          e.printStackTrace();
        }

        //let's set up some cell formats
        //WritableCellFormat floatFormat = new WritableCellFormat(NumberFormats.FLOAT);
        //WritableCellFormat integerFormat = new WritableCellFormat(NumberFormats.INTEGER);
        WritableWorkbook workbook = Workbook.createWorkbook(excelFile);
        WritableSheet sheet = workbook.createSheet("Encounters", 0);
        Label label0 = new Label(0, 0, "individualID");
        sheet.addCell(label0);
        Label label1 = new Label(1, 0, "catalogNumber");
        sheet.addCell(label1);
        Label label2 = new Label(2, 0, "alternateid");
        sheet.addCell(label2);
        Label label3 = new Label(3, 0, "comments");
        sheet.addCell(label3);
        Label label4 = new Label(4, 0, "sex");
        sheet.addCell(label4);
        Label label5 = new Label(5, 0, "genus");
        sheet.addCell(label5);
        Label label6 = new Label(6, 0, "specificEpithet");
        sheet.addCell(label6);
        Label label7 = new Label(7, 0, "locationID");
        sheet.addCell(label7);
        Label label8 = new Label(8, 0, "decimalLatitude");
        sheet.addCell(label8);
        Label label9 = new Label(9, 0, "decimalLongitude");
        sheet.addCell(label9);
        Label label10 = new Label(10, 0, "verbatimLocality");
        sheet.addCell(label10);
        Label label11 = new Label(11, 0, "modified");
        sheet.addCell(label11);
        Label label12 = new Label(12, 0, "occurrenceID");
        sheet.addCell(label12);
        Label label13 = new Label(13, 0, "behavior");
        sheet.addCell(label13);
        Label label14 = new Label(14, 0, "eventID");
        sheet.addCell(label14);
        Label label15 = new Label(15, 0, "verbatimEventDate");
        sheet.addCell(label15);
        Label label16 = new Label(16, 0, "dwcDateAdded");
        sheet.addCell(label16);
        Label label17 = new Label(17, 0, "dynamicProperties");
        sheet.addCell(label17);
        Label label18 = new Label(18, 0, "livingStatus");
        sheet.addCell(label18);
        Label label19 = new Label(19, 0, "date");
        sheet.addCell(label19);
        Label label20 = new Label(20, 0, "guid");
        sheet.addCell(label20);
        Label label21 = new Label(21, 0, "patterningCode");
        sheet.addCell(label21);
        Label label22 = new Label(22, 0, "submitterOrganization");
        sheet.addCell(label22);
        Label label23 = new Label(23, 0, "submitterProject");
        sheet.addCell(label23);
        Label label24 = new Label(24, 0, "precaudallength");
        sheet.addCell(label24);
        Label label25 = new Label(25, 0, "length");
        sheet.addCell(label25);
        Label label26 = new Label(26, 0, "temperature");
        sheet.addCell(label26);
        Label label27 = new Label(27, 0, "underwater");
        sheet.addCell(label27);
        Label label28 = new Label(28, 0, "tissueSamples");
        sheet.addCell(label28);
        Label label29 = new Label(29, 0, "annotations");
        sheet.addCell(label29);
        Label label30 = new Label(30, 0, "metalTags");
        sheet.addCell(label30);
        Label label31 = new Label(31, 0, "acousticTag");
        sheet.addCell(label31);
        Label label32 = new Label(32, 0, "satelliteTag");
        sheet.addCell(label32);
        Label label33 = new Label(33, 0, "submitterName");
        sheet.addCell(label33);
        Label label34 = new Label(34, 0, "photographerName");
        sheet.addCell(label34);
        Label label35 = new Label(35, 0, "mediaAssetKeywords");
        sheet.addCell(label35);


     
        String value = "";
        int count = 0;
         for(int i=0;i<numMatchingEncounters;i++){
            count++;
            numResults++;

            Encounter enc = (Encounter) rEncounters.get(i);

            String encID = enc.getCatalogNumber();
            value = encID;
            if (value!=null && !value.equals("")) {
              Label encIDLabel = new Label(0, count, enc.getIndividualID());
              sheet.addCell(encIDLabel);
            }

            value = enc.getCatalogNumber();
            if (value!=null && !value.equals("")) {
              Label altID = new Label(1,count,value);
              sheet.addCell(altID);
            }

            value = enc.getAlternateID();
            if (value!=null && !value.equals("")) {
              Label altID = new Label(2,count,value);
              sheet.addCell(altID);
            }

            value = enc.getComments();
            if (value!=null && !value.equals("")) {
              Label comments = new Label(3, count, value);
              sheet.addCell(comments);
            }

            value = enc.getSex();
            if (value!=null && !value.equals("")) {
                Label sex = new Label(4, count, value);
                sheet.addCell(sex);  
            }

            value = enc.getGenus();
            if (value!=null && !value.equals("")) {
                Label genus = new Label(5, count, value);
                sheet.addCell(genus);  
            }

            value = enc.getSpecificEpithet();
            if (value!=null && !value.equals("")) {
                Label specEp = new Label(6, count, value);
                sheet.addCell(specEp);  
            }

            value = enc.getLocationID();
            if (value!=null && !value.equals("")) {
                Label locID = new Label(7, count, value);
                sheet.addCell(locID);  
            }

            value = enc.getDecimalLatitude();
            if (value!=null && !value.equals("")) {
                Label decLat = new Label(8, count, value);
                sheet.addCell(decLat);  
            }

            value = enc.getDecimalLongitude();
            if (value!=null && !value.equals("")) {
                Label decLon = new Label(9, count, value);
                sheet.addCell(decLon);  
            }

            value = enc.getVerbatimLocality();
            if (value!=null && !value.equals("")) {
                Label vLoc = new Label(10, count, value);
                sheet.addCell(vLoc);  
            }

            value = enc.getModified();
            if (value!=null && !value.equals("")) {
                Label mod = new Label(11, count, value);
                sheet.addCell(mod);  
            }

            value = enc.getOccurrenceID();
            if (value!=null && !value.equals("")) {
                Label occ = new Label(12, count, value);
                sheet.addCell(occ);  
            }

            value = enc.getBehavior();
            if (value!=null && !value.equals("")) {
                Label beh = new Label(13, count, value);
                sheet.addCell(beh);  
            }

            value = enc.getEventID();
            if (value!=null && !value.equals("")) {
                Label ev = new Label(14, count, value);
                sheet.addCell(ev);  
            }

            value = enc.getVerbatimEventDate();
            if (value!=null && !value.equals("")) {
                Label vDate = new Label(15, count, value);
                sheet.addCell(vDate);  
            }

            value = enc.getDwcDateAdded();
            if (value!=null && !value.equals("")) {
                Label added = new Label(16, count, value);
                sheet.addCell(added);  
            }

            value = enc.getDynamicProperties();
            if (value!=null && !value.equals("")) {
                Label dps = new Label(17, count, value);
                sheet.addCell(dps);  
            }

            value = enc.getLivingStatus();
            if (value!=null && !value.equals("")) {
                Label ls = new Label(18, count, value);
                sheet.addCell(ls);  
            }

            value = enc.getDate();
            if (value!=null && !value.equals("")) {
                Label dt = new Label(19, count, value);
                sheet.addCell(dt);  
            }

            value = enc.getDWCGlobalUniqueIdentifier();
            if (value!=null && !value.equals("")) {
                Label guid = new Label(20, count, value);
                sheet.addCell(guid);  
            }

            value = enc.getPatterningCode();
            if (value!=null && !value.equals("")) {
                Label guid = new Label(21, count, value);
                sheet.addCell(guid);  
            }

            value = enc.getSubmitterOrganization();
            if (value!=null && !value.equals("")) {
                Label so = new Label(22, count, value);
                sheet.addCell(so);  
            }

            value = enc.getSubmitterProject();
            if (value!=null && !value.equals("")) {
                Label sp = new Label(23, count, value);
                sheet.addCell(sp);  
            }

            Measurement pcLength = enc.getMeasurement("precaudallength");
            if (pcLength!=null) {
                Label lPCLength = new Label(24, count, Double.toString(pcLength.getValue()));
              sheet.addCell(lPCLength);
            }

            Measurement length = enc.getMeasurement("length");
            if (length!=null) {
              Label lLength = new Label(25, count, Double.toString(length.getValue()));
              sheet.addCell(lLength);
            }

            Measurement temperature = enc.getMeasurement("temperature");
            if (temperature!=null) {
              Label temp = new Label(26, count, Double.toString(temperature.getValue()));
              sheet.addCell(temp);
            }

            Measurement abovewater = enc.getMeasurement("underwater");
            if (abovewater!=null) {
              Label lAbovewater = new Label(27, count, Double.toString(abovewater.getValue()));
              sheet.addCell(lAbovewater);
            }

            if (enc.getTissueSamples()!=null&&enc.getTissueSamples().size()>0) {
                Label samples = new Label(28, count, String.valueOf(enc.getTissueSamples().size()));
                sheet.addCell(samples);
            }

            if (enc.getAnnotations()!=null) {
                Label anns = new Label(29, count, String.valueOf(enc.getAnnotations().size()));
                sheet.addCell(anns);
            }

            if (enc.getMetalTags()!=null) {
                String ids = "";
                for (MetalTag mt : enc.getMetalTags()) {
                    ids += (mt.getId()+"; ");
                }
                Label lIDs = new Label(30, count, ids);
                sheet.addCell(lIDs);
            }

            if (enc.getAcousticTag()!=null) {
                Label at = new Label(31, count, enc.getAcousticTag().getId());
                sheet.addCell(at);
            }

            if (enc.getSatelliteTag()!=null) {
                Label st = new Label(32, count, enc.getSatelliteTag().getId());
                sheet.addCell(st);
            }

            //submitter name
            if (enc.getSubmitterName()!=null) {
              Label sn = new Label(33, count, enc.getSubmitterName());
              sheet.addCell(sn);
            }

            //photographer name
            if (enc.getPhotographerName()!=null) {
              Label pn = new Label(34, count, enc.getPhotographerName());
              sheet.addCell(pn);
            }
            
            //keywords
            if (enc.getMedia()!=null) {
              String allKws = "";
              for (MediaAsset ma : enc.getMedia()) {
                if (ma.hasKeywords()) {
                  for (Keyword kw : ma.getKeywords()) { 
                    allKws += kw.getReadableName()+"; ";  
                  }
                  Label kws = new Label(35, count, allKws);
                  sheet.addCell(kws);
                }
              }
            }



         } //end for loop iterating encs

         workbook.write();
         workbook.close();


        outp.close();
        outp=null;

      }
      catch(Exception ioe){
        ioe.printStackTrace();
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        out.println(ServletUtilities.getHeader(request));
        out.println("<html><body><p><strong>Error encountered</strong> with file writing. Check the relevant log.</p>");
        out.println("<p>Please let the webmaster know you encountered an error at: EncounterSearchExportExcelFile servlet</p></body></html>");
        out.println(ServletUtilities.getFooter(context));
        out.close();
        outp.close();
        outp=null;
      }


    }
    catch(Exception e) {
      e.printStackTrace();
      response.setContentType("text/html");
      PrintWriter out = response.getWriter();
      out.println(ServletUtilities.getHeader(request));
      out.println("<html><body><p><strong>Error encountered</strong></p>");
        out.println("<p>Please let the webmaster know you encountered an error at: EncounterSearchExportExcelFile servlet</p></body></html>");
        out.println(ServletUtilities.getFooter(context));
        out.close();
    }

    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();

      //now write out the file
      response.setContentType("application/msexcel");
      response.setHeader("Content-Disposition","attachment;filename="+filename);
      ServletContext ctx = getServletContext();
      //InputStream is = ctx.getResourceAsStream("/encounters/"+filename);
     InputStream is=new FileInputStream(excelFile);

      int read=0;
      byte[] bytes = new byte[BYTES_DOWNLOAD];
      OutputStream os = response.getOutputStream();

      while((read = is.read(bytes))!= -1){
        os.write(bytes, 0, read);
      }
      os.flush();
      os.close();
    }

    private String milliToDate(long millis) {
        String date = "";
        Date dt = new Date(millis);
        SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy");
        date = df.format(dt);
        return date;
    }

  }
