package org.ecocean.servlet.export;

import java.io.IOException;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Query;
import javax.servlet.*;
import javax.servlet.http.*;

import java.util.Vector;
import java.util.Random;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;

import org.json.*;
import org.ecocean.*;
import org.ecocean.media.MediaAsset;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.security.HiddenEncReporter;
import org.ecocean.security.HiddenIndividualReporter;

import java.util.zip.*;
import java.io.OutputStream;

public class GetEncounterSearchGoogleMapsPoints extends HttpServlet {

  
  public void init(ServletConfig config) throws ServletException {
      super.init(config);
    }

  
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
      doPost(request, response);
  }
    


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
    
    //check for compression
    String encodings = request.getHeader("Accept-Encoding");
    boolean useCompression = ((encodings != null) && (encodings.indexOf("gzip") > -1));
    
    //set the response
    //response.setContentType("text/html");
    //PrintWriter out = response.getWriter();
    String langCode=ServletUtilities.getLanguageCode(request);
    
    String context="context0";
    context=ServletUtilities.getContext(request);
    
    //let's load encounterSearch.properties
    
    Properties map_props = new Properties();
    //map_props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individualMappedSearchResults.properties"));
    map_props=ShepherdProperties.getProperties("mappedSearchResults.properties", langCode);
    
    Properties haploprops = new Properties();
    //haploprops.load(getClass().getResourceAsStream("/bundles/haplotypeColorCodes.properties"));
  haploprops=ShepherdProperties.getProperties("haplotypeColorCodes.properties", "",context);

    Properties localeprops = new Properties();
   localeprops=ShepherdProperties.getProperties("locationIDGPS.properties", "", context);

   List<String> allSpecies=CommonConfiguration.getIndexedPropertyValues("genusSpecies",context);
   int numSpecies=allSpecies.size();
  
   List<String> allSpeciesColors=CommonConfiguration.getIndexedPropertyValues("genusSpeciesColor",context);
   int numSpeciesColors=allSpeciesColors.size();
   
   Hashtable<String, String> speciesTable=new Hashtable<String,String>();
   for(int i=0;i<numSpecies;i++){ 
     if(i<numSpeciesColors){
       speciesTable.put(allSpecies.get(i),allSpeciesColors.get(i));
     }
   }
    
    //get our Shepherd
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("GetEncounterSearchGoogleMapsPoints.class");
    PersistenceManager pm=myShepherd.getPM();
    PersistenceManagerFactory pmf = pm.getPersistenceManagerFactory();
    javax.jdo.FetchGroup grp = pmf.getFetchGroup(MarkedIndividual.class, "individualSearchResults");
    grp.addMember("individualID").addMember("sex").addMember("names").addMember("encounters");
    
    javax.jdo.FetchGroup grp2 = pmf.getFetchGroup(Encounter.class, "encSearchResults");
    grp2.addMember("tissueSamples").addMember("sex").addMember("individual").addMember("decimalLatitude").addMember("decimalLongitude").addMember("catalogNumber").addMember("year").addMember("hour").addMember("month").addMember("minutes").addMember("day");


    myShepherd.getPM().getFetchPlan().setGroup("encSearchResults");
    myShepherd.getPM().getFetchPlan().addGroup("individualSearchResults");

  Random ran= new Random();

  //set up the aspect styles


  
  
  try {

    
    myShepherd.beginDBTransaction();

    //start the query and get the results
    String order = "";
    
    //determine if we should use locationID to determine some generic mapping points
    boolean useLocales=false;
    if(request.getParameter("useLocales")!=null){
      useLocales=true;
    }
    else{request.setAttribute("gpsOnly", "yes");}
    
    StringBuffer prettyPrint=new StringBuffer("");
    Map<String,Object> paramMap = new HashMap<String, Object>();
    String filter= EncounterQueryProcessor.queryStringBuilder(request, prettyPrint, paramMap);
    Query q1=myShepherd.getPM().newQuery(filter);
    Collection c = (Collection) (q1.execute());
    Vector<Encounter> rEncounters=new Vector<Encounter>(c);
    q1.closeAll();
    
    
    // viewOnly=true arg means this hiddenData relates to viewing the summary results
    HiddenEncReporter hiddenData = new HiddenEncReporter(rEncounters, request, myShepherd);
    rEncounters = hiddenData.viewableResults(rEncounters, true, myShepherd);


    int numEncs=rEncounters.size();



      //let's start
      JSONObject indieMappedPoints     = new JSONObject();
      JSONArray featureList = new JSONArray();
      indieMappedPoints.put("type", "FeatureCollection");
      indieMappedPoints.put("features", featureList);

        
        for(int yh=0;yh<numEncs;yh++){
          Encounter enc=(Encounter)rEncounters.get(yh);

          Double thisEncLat=null;
          Double thisEncLong=null;
       
        //first check if the Encounter object has lat and long values
          if((enc.getDecimalLatitude()!=null)&&(enc.getDecimalLongitude()!=null)&&(enc.getDecimalLatitudeAsDouble()>=-90.0)&&(enc.getDecimalLatitudeAsDouble()<=90.0)&&(enc.getDecimalLongitudeAsDouble()<=180.0)&&(enc.getDecimalLongitudeAsDouble()>=-180.0)){
            thisEncLat=enc.getLatitudeAsDouble();
            thisEncLong=enc.getLongitudeAsDouble();
          }
          //let's see if locationIDGPS.properties has a location we can use

          else if(useLocales){
                   try {
                        String lc = enc.getLocationCode();
                        if (localeprops.getProperty(lc) != null) {
                          String gps = localeprops.getProperty(lc);
                          StringTokenizer st = new StringTokenizer(gps, ",");
                          thisEncLat=(new Double(st.nextToken()))+ran.nextDouble()*0.02;
                          thisEncLong=(new Double(st.nextToken()))+ran.nextDouble()*0.02;;

                        }
                   } 
                   catch (Exception e) {
                        e.printStackTrace();
                        System.out.println("     I hit an error getting locales in individualMappedSearchResults.jsp.");
                   }
         }

            
          //if we have GPS data, let's create the object, otherwise cycle through in the loop
          if(thisEncLat!=null && thisEncLong!=null) {
            
            //set up colors
            String baseColor="C0C0C0";
            String sexColor="C0C0C0";
            String haploColor="C0C0C0";
            String speciesColor="C0C0C0";
            
            //now check if we should show by sex
            if(enc.getSex()!=null){
              if(enc.getSex().equals("male")){
                sexColor="0000FF";
              }
              else if(enc.getSex().equals("female")){
                sexColor="FF00FF";
              }
            }
              
            //set the haplotype color
            if((enc.getHaplotype()!=null)&&(haploprops.getProperty(enc.getHaplotype())!=null)){
                if(!haploprops.getProperty(enc.getHaplotype()).trim().equals("")){ haploColor = haploprops.getProperty(enc.getHaplotype());}
            }
            //set the species color
            if(enc.getGenus()!=null){
              speciesColor=speciesTable.get(enc.getGenus()+" "+enc.getSpecificEpithet());
            }
            
            
            
               JSONObject point = new JSONObject();
               point.put("type", "Point");
               
               // construct a JSONArray from a string; can also use an array or list
               JSONArray coord = new JSONArray("["+thisEncLong.toString()+","+thisEncLat.toString()+"]");
               point.put("coordinates", coord);
               point.put("catalogNumber",enc.getCatalogNumber());
               point.put("encSubdir",enc.subdir());
               point.put("rootURL",CommonConfiguration.getURLLocation(request));
               if(enc.getIndividual()!=null)point.put("individualID",ServletUtilities.handleNullString(enc.getIndividual().getIndividualID()));
               if(enc.getIndividual()!=null)point.put("individualDisplayName",ServletUtilities.handleNullString(enc.getIndividual().getDisplayName()));
               point.put("dataDirectoryName",CommonConfiguration.getDataDirectoryName(context));
               point.put("date",enc.getDate());
               
               
               //end color
               point.put("color",baseColor);
               point.put("sexColor",sexColor);
               point.put("haplotypeColor",haploColor);
               point.put("speciesColor",speciesColor);
               
               JSONObject feature = new JSONObject();
               feature.put("type", "Feature");
               JSONObject props = new JSONObject();
               feature.put("properties", props);
               
               feature.put("geometry", point);
               featureList.put(feature);
            }
             
             
             
          
        }


      //new compressed way
      response.setContentType("application/json");
      tryCompress(response, indieMappedPoints.toString(), useCompression);
      response.setHeader("Content-Type", "application/json");
      response.setStatus(200);

      

    }
    catch(Exception e) {
      e.printStackTrace();

    }
  finally{
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
  }
    
    
  }
  
  void tryCompress(HttpServletResponse resp, String s, boolean useComp) throws IOException {
    if (!useComp || (s.length() < 3000)) {  //kinda guessing on size here, probably doesnt matter
    
      resp.getWriter().write(s);
    
    } else {
      resp.setHeader("Content-Encoding", "gzip");
      OutputStream o = resp.getOutputStream();
      GZIPOutputStream gz = new GZIPOutputStream(o);
      gz.write(s.getBytes());
      gz.flush();
      gz.close();
      o.close();
    }
    
  }

  
}
