package org.ecocean.servlet.export;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.*;
import javax.servlet.http.*;

import java.util.Vector;
import java.util.Random;
import java.util.Properties;
import java.util.StringTokenizer;

import org.json.*;

import org.ecocean.*;

public class GetIndividualSearchGoogleMapsPoints extends HttpServlet {

  
  public void init(ServletConfig config) throws ServletException {
      super.init(config);
    }

  
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
      doPost(request, response);
  }
    


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
    
    //set the response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    String langCode="en";
    
    //let's load encounterSearch.properties
    
    Properties map_props = new Properties();
    map_props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individualMappedSearchResults.properties"));

    Properties haploprops = new Properties();
    //haploprops.load(getClass().getResourceAsStream("/bundles/haplotypeColorCodes.properties"));
  haploprops=ShepherdProperties.getProperties("haplotypeColorCodes.properties", "");

    Properties localeprops = new Properties();
   localeprops.load(getClass().getResourceAsStream("/bundles/locales.properties"));

    
    //get our Shepherd
    Shepherd myShepherd = new Shepherd();

  Random ran= new Random();

  //set up the aspect styles



    //set up the vector for matching indies
    Vector rIndividuals = new Vector();
    
    myShepherd.beginDBTransaction();

    //start the query and get the results
    String order = "";
    request.setAttribute("gpsOnly", "yes");
    MarkedIndividualQueryResult queryResult = IndividualQueryProcessor.processQuery(myShepherd, request, order);
    rIndividuals = queryResult.getResult();
    int numIndividuals=rIndividuals.size();

   
 
    try {

      //let's start
      JSONObject indieMappedPoints     = new JSONObject();
      JSONArray featureList = new JSONArray();
      indieMappedPoints.put("type", "FeatureCollection");
      //JSONArray  addresses = new JSONArray();
      //JSONObject address;
      indieMappedPoints.put("features", featureList);
      
      
      for(int i=0;i<numIndividuals;i++) {
        MarkedIndividual indie=(MarkedIndividual)rIndividuals.get(i);
        Vector rEncounters=indie.returnEncountersWithGPSData(true,true); 
        int numEncs=rEncounters.size();
        boolean showMovePath=false;
        for(int yh=0;yh<numEncs;yh++){
          Encounter enc=(Encounter)rEncounters.get(yh);
          Double thisEncLat=null;
          Double thisEncLong=null;
       
        //first check if the Encounter object has lat and long values
          if((enc.getLatitudeAsDouble()!=null)&&(enc.getLongitudeAsDouble()!=null)){
            thisEncLat=enc.getLatitudeAsDouble();
            thisEncLong=enc.getLongitudeAsDouble();
          }
          //let's see if locales.properties has a location we can use
          else{
                   try {
                        String lc = enc.getLocationCode();
                        if (localeprops.getProperty(lc) != null) {
                          String gps = localeprops.getProperty(lc);
                          StringTokenizer st = new StringTokenizer(gps, ",");
                          thisEncLat=(new Double(st.nextToken()))+ran.nextDouble()*0.02;
                          thisEncLong=(new Double(st.nextToken()))+ran.nextDouble()*0.02;;

                        }
                      } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println("     I hit an error getting locales in individualMappedSearchResults.jsp.");
                      }
          }
        //test example
          // {"geometry": {"type": "Point", "coordinates": [-94.149, 36.33]}
             JSONObject point = new JSONObject();
             point.put("type", "Point");
             
             // construct a JSONArray from a string; can also use an array or list
             JSONArray coord = new JSONArray("["+thisEncLong.toString()+","+thisEncLat.toString()+"]");
             point.put("coordinates", coord);
             point.put("catalogNumber",enc.getCatalogNumber());
             point.put("rootURL",CommonConfiguration.getURLLocation(request));
             point.put("individualID",enc.getIndividualID());
             point.put("dataDirectoryName",CommonConfiguration.getDataDirectoryName());
             point.put("date",enc.getDate());
             
             
             String baseColor="C0C0C0";
             String sexColor="C0C0C0";
             String haploColor="C0C0C0";
             
             //now check if we should show by sex
             if(indie.getSex().equals("male")){
                 sexColor="0000FF";
               }
               else if(indie.getSex().equals("female")){
                 sexColor="FF00FF";
               }
               

             if((indie.getHaplotype()!=null)&&(haploprops.getProperty(indie.getHaplotype())!=null)){
                 if(!haploprops.getProperty(indie.getHaplotype()).trim().equals("")){ haploColor = haploprops.getProperty(indie.getHaplotype());}
             }
             
             //end color
             point.put("color",baseColor);
             point.put("sexColor",sexColor);
             point.put("haplotypeColor",haploColor);
             
             JSONObject feature = new JSONObject();
             feature.put("type", "Feature");
             JSONObject props = new JSONObject();
             feature.put("properties", props);
             
             feature.put("geometry", point);
             featureList.put(feature);
             
          
        }
        
    
        
        
       
        
        } //end for
   

      myShepherd.commitDBTransaction();
      myShepherd.closeDBTransaction();

      
      response.setContentType("application/json");
      response.getWriter().write(indieMappedPoints.toString());
      

    }
    catch(Exception e) {
      out.println("<p><strong>Error encountered</strong></p>");
      out.println("<p>Please let the webmaster know you encountered an error at: IndividualSearchExportCapture servlet</p>");
      e.printStackTrace();
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }
    
    
  }

  
}
