package org.ecocean.servlet.export;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.ecocean.*;
import org.ecocean.servlet.ServletUtilities;

import java.lang.StringBuffer;


//adds spots to a new encounter
public class IndividualSearchExportCapture extends HttpServlet{
  


  
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
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("IndividualSearchExportCapture.class");
    Vector<MarkedIndividual> rIndividuals = new Vector<MarkedIndividual>();
    myShepherd.beginDBTransaction();
    String order = "";
    
    String locCode=request.getParameter("locationCodeField");

    MarkedIndividualQueryResult result = IndividualQueryProcessor.processQuery(myShepherd, request, order);
    rIndividuals = result.getResult();
    int numIndividuals=rIndividuals.size();
    int numSharks=0;
    out.println("<pre>");
    out.println("title='Example CAPTURE Export of Marked Individuals from the "+CommonConfiguration.getHTMLTitle(context)+"'");
 
 
    try {
      int startYear=(new Integer(request.getParameter("year1"))).intValue();
      int startMonth=(new Integer(request.getParameter("month1"))).intValue();
      int endMonth=(new Integer(request.getParameter("month2"))).intValue();
      int endYear=(new Integer(request.getParameter("year2"))).intValue();

      
      
      //check for seasons wrapping over years
      int wrapsYear=0;
      if(startMonth>endMonth) {wrapsYear=1;}

      int numYearsCovered=endYear-startYear-wrapsYear+1;
      out.println("task read captures x matrix occasions="+numYearsCovered);

      //now, let's print out our capture histories

      //out.println("<br><br>Capture histories for live recaptures modeling: "+startYear+"-"+endYear+", months "+startMonth+"-"+endMonth+"<br><br><pre>");
    
      
      int maxLengthID=0;
      for(int p=0;p<numIndividuals;p++) {
        MarkedIndividual s=rIndividuals.get(p);
        if(s.getIndividualID().length()>maxLengthID){maxLengthID=s.getIndividualID().length();}
      }
      
      out.println("format='(a"+maxLengthID+","+numYearsCovered+"f1.0)'");
      out.println("read input data");
      
      
      for(int i=0;i<numIndividuals;i++) {
        MarkedIndividual s=rIndividuals.get(i);

        boolean wasSightedInRequestedLocation=false;
        if((request.getParameter("locationCodeField")!=null)&&(!request.getParameter("locationCodeField").trim().equals(""))){
          wasSightedInRequestedLocation=s.wasSightedInLocationCode(locCode);
        }
        else{
          wasSightedInRequestedLocation=true;
        }
        
        if((wasSightedInRequestedLocation)&&(s.wasSightedInPeriod(startYear,startMonth,endYear,endMonth))) {
          boolean wasReleased=false;
          StringBuffer sb=new StringBuffer();

          //lets print out each shark's capture history
          for(int f=startYear;f<=(endYear-wrapsYear);f++) {
            boolean sharkWasSeen=false;
            

              
              if((request.getParameter("locationCodeField")!=null)&&(!request.getParameter("locationCodeField").trim().equals(""))){
                sharkWasSeen=s.wasSightedInPeriod(f,startMonth,1,(f+wrapsYear),endMonth, 31, locCode);
              }
              else{
                sharkWasSeen=s.wasSightedInPeriod(f,startMonth,1,(f+wrapsYear),endMonth, 31);
              }
              
          
            if(sharkWasSeen){
              

              sb.append("1");
              wasReleased=true;
            }
            else{
              sb.append("0");
              
            }
          }
          if(wasReleased) {

              
              String adjustedID=s.getIndividualID();
              while(adjustedID.length()<maxLengthID){adjustedID+="X";}
            
              out.println(adjustedID+sb.toString());
       
            numSharks++;
          }
        
        } //end if
      } //end while

      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();


    }
    catch(Exception e) {
      out.println("<p><strong>Error encountered</strong></p>");
      out.println("<p>Please let the webmaster know you encountered an error at: IndividualSearchExportCapture servlet</p>");
      e.printStackTrace();
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }
    
    out.println("task closure test<br/>task model selection");
    //out.println("task population estimate ALL");
    //out.println("task population estimate NULL JACKKNIFE REMOVAL ZIPPEN MT-CH MH-CH MTH-CH");
    out.println("task population estimate ALL");
    out.close();
  }

  
  }