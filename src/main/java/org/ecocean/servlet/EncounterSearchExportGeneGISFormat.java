package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;
import org.ecocean.*;
import org.ecocean.genetics.*;
import javax.jdo.*;
//import com.poet.jdo.*;
import java.lang.StringBuffer;


//adds spots to a new encounter
public class EncounterSearchExportGeneGISFormat extends HttpServlet{
  


  
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
    
    Shepherd myShepherd = new Shepherd();
    

    
    Vector rEncounters = new Vector();

    myShepherd.beginDBTransaction();

    

    
    try {
      EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(myShepherd, request, "year descending, month descending, day descending");
      rEncounters = queryResult.getResult();
      
      int numMatchingEncounters=rEncounters.size();
      
      //build the CSV file header
      StringBuffer locusString=new StringBuffer("");
      int numLoci=2; //most covered species will be diploids
      try{
        numLoci=(new Integer(CommonConfiguration.getProperty("numLoci"))).intValue();
      }
      catch(Exception e){System.out.println("numPloids configuration value did not resolve to an integer.");e.printStackTrace();}
      
      for(int j=0;j<numLoci;j++){
        locusString.append(",Locus"+(j+1)+" A1,Locus"+(j+1)+" A2");
        
      }
      out.println("<html><body>");
      out.println("Individual ID,Other ID 1,Date,Time,Latitude,Longitude,Area,Sub Area,Sex,Haplotype"+locusString.toString());
      
      
      
      for(int i=0;i<numMatchingEncounters;i++){
        
        Encounter enc=(Encounter)rEncounters.get(i);
        String assembledString="";
        if(enc.getIndividualID()!=null){assembledString+=enc.getIndividualID();}
        if(enc.getAlternateID()!=null){assembledString+=","+enc.getAlternateID();}
        else{assembledString+=",";}
        
        String dateString=",";
        if(enc.getYear()>0){
          dateString+=enc.getYear();
          if(enc.getMonth()>0){
            dateString+=("-"+enc.getMonth());
            if(enc.getDay()>0){dateString+=("-"+enc.getDay());}
          }
        }
        assembledString+=dateString;
        
        String timeString=",";
        if(enc.getHour()>-1){timeString+=enc.getHour()+":"+enc.getMinutes();}
        assembledString+=timeString;
        
        
        
        if((enc.getDecimalLatitude()!=null)&&(enc.getDecimalLongitude()!=null)){
          assembledString+=","+enc.getDecimalLatitude();
          assembledString+=","+enc.getDecimalLongitude();
        }
        else{assembledString+=",,";}
        
        assembledString+=","+enc.getVerbatimLocality();
        assembledString+=","+enc.getLocationID();
        assembledString+=","+enc.getSex();
        
        //find and print the haplotype
        String haplotypeString=",";
        if(enc.getHaplotype()!=null){haplotypeString+=enc.getHaplotype();}
        
        //find and print the ms markers
        String msMarkerString="";
        List<TissueSample> samples=enc.getTissueSamples();
        int numSamples=samples.size();
        boolean foundMsMarkers=false;
        for(int k=0;k<numSamples;k++){
          if(!foundMsMarkers){
            TissueSample t=samples.get(k);
            List<GeneticAnalysis> analyses=t.getGeneticAnalyses();
            int aSize=analyses.size();
            for(int l=0;l<aSize;l++){
              GeneticAnalysis ga=analyses.get(l);
              if(ga.getAnalysisType().equals("MicrosatelliteMarkers")){
                foundMsMarkers=true;
                MicrosatelliteMarkersAnalysis ga2=(MicrosatelliteMarkersAnalysis)ga;
                List<Locus> loci=ga2.getLoci();
                int localLoci=loci.size();
                for(int m=0;m<localLoci;m++){
                  Locus locus=loci.get(m);
                  if(locus.getAllele0()!=null){msMarkerString+=","+locus.getAllele0();}
                  else{msMarkerString+=",";}
                  if(locus.getAllele1()!=null){msMarkerString+=","+locus.getAllele1();}
                  else{msMarkerString+=",";}
                }
              
              }
            }
          }
        }
        
        out.println("<p>"+assembledString+haplotypeString+msMarkerString+"</p>");
        
      }
      
      out.println("</body></html>");

      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();


    }
    catch(Exception e) {
      out.println("<p><strong>Error encountered</strong></p>");
      out.println("<p>Please let the webmaster know you encountered an error at: EncounterSearchExportGeneGISFormat servlet</p>");

      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();


    }
    

    out.close();
  }

  
  }