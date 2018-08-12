package org.ecocean.servlet.export;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.ecocean.*;
import org.ecocean.genetics.*;
import org.ecocean.servlet.ServletUtilities;

import javax.jdo.*;

import java.lang.StringBuffer;

import org.dom4j.Document;
import org.dom4j.DocumentHelper; 
import org.dom4j.Element;


//adds spots to a new encounter
public class EncounterSearchExportKML extends HttpServlet{
  
  private static final int BYTES_DOWNLOAD = 1024;

  
  public void init(ServletConfig config) throws ServletException {
      super.init(config);
    }

  
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
      doPost(request, response);
  }
    


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{

    String context="context0";
    context=ServletUtilities.getContext(request);
    
    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    if(!encountersDir.exists()){encountersDir.mkdirs();}
    
    //determine if placemarks should be decorated
    boolean bareBonesPlacemarks=false;
    if(request.getParameter("barebones")!=null){bareBonesPlacemarks=true;}
    
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("EncounterSearchExportKML.class");
    Vector rEncounters = new Vector();
    
    //set up the files
    String gisFilename = "exportKML_" + request.getRemoteUser() + ".kml";
    File gisFile = new File(encountersDir.getAbsolutePath()+"/" + gisFilename);

    //setup the KML output file
    //String kmlFilename = "KMLExport_" + request.getRemoteUser() + ".kml";
    Document document = DocumentHelper.createDocument();
    Element root = document.addElement("kml");
    root.addAttribute("xmlns", "http://www.opengis.net/kml/2.2");
    root.addAttribute("xmlns:gx", "http://www.google.com/kml/ext/2.2");
    Element docElement = root.addElement("Document");

    boolean addTimeStamp = false;
    if (request.getParameter("addTimeStamp") != null) {
      addTimeStamp = true;
    }
    
    
    myShepherd.beginDBTransaction();
      
      try{
      
      if(request.getParameter("encounterSearchUse")!=null){
        Collection c=(Collection)myShepherd.getPM().newQuery("SELECT FROM org.ecocean.Encounter WHERE decimalLatitude != null && decimalLongitude != null").execute();
        rEncounters=new Vector(c);
      }
      else{
        EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(myShepherd, request, "year descending, month descending, day descending");
        rEncounters = queryResult.getResult();
      }

			Vector blocked = Encounter.blocked(rEncounters, request);
			if (blocked.size() > 0) {
				response.setContentType("text/html");
				PrintWriter out = response.getWriter();
				out.println(ServletUtilities.getHeader(request));  
				out.println("<html><body><p><strong>Access denied.</strong></p>");
				out.println(ServletUtilities.getFooter(context));
				out.close();
				return;
			}
      
        int numMatchingEncounters=rEncounters.size();
      

        for(int i=0;i<numMatchingEncounters;i++){
        
          Encounter enc=(Encounter)rEncounters.get(i);
        //populate KML file ====================================================

          if ((enc.getDecimalLongitude()!=null) && (enc.getDecimalLatitude() != null)) {
            //System.out.println("adding KML point...");
            Element placeMark = docElement.addElement("Placemark");
            Element name = placeMark.addElement("name");
            String nameText = "";

            //add the name
            if (enc.getIndividualID()==null) {
              nameText = "Encounter " + enc.getEncounterNumber();
            } else {
              nameText = enc.getIndividualID() + ": Encounter " + enc.getEncounterNumber();
            }
            name.setText(nameText);

            //add the visibility element
            Element viz = placeMark.addElement("visibility");
            viz.setText("1");

            //add the descriptive HTML
            if(!bareBonesPlacemarks){
              Element description = placeMark.addElement("description");

              String descHTML = "<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?noscript=true&number=" + enc.getEncounterNumber() + "\">Direct Link</a></p>";
              descHTML += "<p> <strong>Date:</strong> " + enc.getDate() + "</p>";
              if(enc.getLocation()!=null){
                descHTML += "<p> <strong>Location:</strong><br>" + enc.getLocation() + "</p>";
              }
              //trying to find problematic sizes...
              try{
                if (enc.getSizeAsDouble() != null) {
                  descHTML += "<p> <strong>Size:</strong> " + enc.getSize() + " meters</p>";
                }
              }
              catch(Exception npe){npe.printStackTrace();System.out.println("NPE on size for encounter: "+enc.getCatalogNumber());}
            
              if(enc.getSex()!=null){
                descHTML += "<p> <strong>Sex:</strong> " + enc.getSex() + "</p>";
              }
              if ((enc.getComments()!=null)&&(!enc.getComments().equals(""))) {
                descHTML += "<p> <strong>Comments:</strong> " + enc.getComments() + "</p>";
              }

              descHTML += "<strong>Images</strong><br />";
              List<SinglePhotoVideo> imgs = enc.getImages();
              int imgsNum = imgs.size();
              for (int imgNum = 0; imgNum < imgsNum; imgNum++) {
                descHTML += ("<br />" + "<a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?noscript=true&number=" + enc.getEncounterNumber() + "\"><img src=\"http://" + request.getServerName() +"/"+CommonConfiguration.getDataDirectoryName(context) + "/encounters/" + enc.getEncounterNumber() + "/" + imgs.get(imgNum).getDataCollectionEventID() + ".jpg\" /></a>");
              }

              description.addCDATA(descHTML);
            }

            if (addTimeStamp && !bareBonesPlacemarks) {
              //add the timestamp
              String stampString = "";
              if (enc.getYear() != -1) {
                stampString += enc.getYear();
                if (enc.getMonth() != -1) {
                  String tsMonth = Integer.toString(enc.getMonth());
                  if (tsMonth.length() == 1) {
                    tsMonth = "0" + tsMonth;
                  }
                  stampString += ("-" + tsMonth);
                  if (enc.getDay() != -1) {
                    String tsDay = Integer.toString(enc.getDay());
                    if (tsDay.length() == 1) {
                      tsDay = "0" + tsDay;
                    }
                    stampString += ("-" + tsDay);
                  }
                }
              }

              if (!stampString.equals("")) {
                Element timeStamp = placeMark.addElement("TimeStamp");
                timeStamp.addNamespace("gx", "http://www.google.com/kml/ext/2.2");
                Element when = timeStamp.addElement("when");
                when.setText(stampString);
              }
            }

            //add the actual lat-long points
            Element point = placeMark.addElement("Point");
            Element coords = point.addElement("coordinates");
            String coordsString = enc.getDWCDecimalLongitude() + "," + enc.getDWCDecimalLatitude();
            if (enc.getMaximumElevationInMeters() != null) {
              coordsString += "," + enc.getMaximumElevationInMeters();
            }
            else if (enc.getMaximumDepthInMeters() != null) {
              coordsString += ",-" + enc.getMaximumDepthInMeters();
            }
            else {
              coordsString += ",0";
            }
            coords.setText(coordsString);


          }
        }
        //end KML ==============================================================


        //write out KML 
        //File kmlFile = new File(getServletContext().getRealPath(("/encounters/" + kmlFilename)));
        FileWriter kmlWriter = new FileWriter(gisFile);
        org.dom4j.io.OutputFormat format = org.dom4j.io.OutputFormat.createPrettyPrint();
        format.setLineSeparator(System.getProperty("line.separator"));
        org.dom4j.io.XMLWriter writer = new org.dom4j.io.XMLWriter(kmlWriter, format);
        writer.write(document);
        writer.close();
        
        //now write out the file
        response.setContentType("application/vnd.google-earth.kml+xml");
        response.setHeader("Content-Disposition","attachment;filename="+gisFilename);
        //ServletContext ctx = getServletContext();
        //InputStream is = ctx.getResourceAsStream("/encounters/"+gisFilename);
       InputStream is=new FileInputStream(gisFile);
        
        
        int read=0;
        byte[] bytes = new byte[BYTES_DOWNLOAD];
        OutputStream os = response.getOutputStream();
       
        while((read = is.read(bytes))!= -1){
          os.write(bytes, 0, read);
        }
        os.flush();
        os.close(); 

        
      }
      catch(Exception ioe){
        ioe.printStackTrace();
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        out.println(ServletUtilities.getHeader(request));
        out.println("<html><body><p><strong>Error encountered</strong> with file writing. Check the relevant log.</p>");
        out.println("<p>Please let the webmaster know you encountered an error at: EncounterSearchExportKML servlet</p></body></html>");
        out.println(ServletUtilities.getFooter(context));
      }
      


    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();


  
      
      
      
    }
  
  
  }
