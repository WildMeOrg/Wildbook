package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;

import org.ecocean.*;
import javax.jdo.*;

import java.lang.StringBuffer;
import java.util.GregorianCalendar;


//robust design
public class SideFeed extends HttpServlet{
  
  Shepherd myShepherd;

  
  public void init(ServletConfig config) throws ServletException {
      super.init(config);
      myShepherd=new Shepherd();
    }

  
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
      doPost(request, response);
  }
    


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
    
    //set the response
    response.setContentType("text/html");
    
    //vector of dates for the study
    Vector dates=new Vector();
    boolean monthly=false;
    if(request.getParameter("monthly")!=null){monthly=true;}

    PrintWriter out = response.getWriter();
    try {
    
      myShepherd.beginDBTransaction();
      Extent sharkClass=myShepherd.getPM().getExtent(MarkedIndividual.class, true);
      //Query query=myShepherd.getPM().newQuery(sharkClass);
      
      String filter="SELECT FROM org.ecocean.Encounter WHERE ";
    
    //int startYear=(new Integer(request.getParameter("startYear"))).intValue();
    //int startMonth=(new Integer(request.getParameter("startMonth"))).intValue();
    //int endMonth=(new Integer(request.getParameter("endMonth"))).intValue();
    //int endYear=(new Integer(request.getParameter("endYear"))).intValue();
    
      
      //int day1=1;
      //int day2=31;
      //int month1=10;
      //int month2=2;
      int year1=2005;
      //int year2=2011;
 
     try{
        
        //get our date values
        //day1=(new Integer(request.getParameter("day1"))).intValue();
        //day2=(new Integer(request.getParameter("day2"))).intValue();
        //month1=(new Integer(request.getParameter("month1"))).intValue();
        //month2=(new Integer(request.getParameter("month2"))).intValue();
        year1=(new Integer(request.getParameter("year1"))).intValue();
        //year2=(new Integer(request.getParameter("year2"))).intValue();
        
        
        GregorianCalendar gcMin=new GregorianCalendar(year1, 4, 1);
        GregorianCalendar gcMax=new GregorianCalendar(year1, 7, 31);
        
        filter+="((dateInMilliseconds >= "+gcMin.getTimeInMillis()+") && (dateInMilliseconds <= "+gcMax.getTimeInMillis()+"))";
  
        //filter+="&& (name != 'Unassigned')";
        
       
        
          } catch(NumberFormatException nfe) {
        //do nothing, just skip on
        nfe.printStackTrace();
          }
    
    
    
    String locCode="1a1";
    if(request.getParameter("locCode")!=null) {
      locCode=request.getParameter("locCode");
      
    }
    String locIDFilter=" locationID == \""+locCode+"\"";
    filter+=(" && "+locIDFilter);
    
    

    

    
    
    //now, let's print out our capture histories
    out.println("/*<br><br>Capture histories for side tagging analysis:<br><br><pre>*/");
    filter=filter.replaceAll("SELECT FROM", "SELECT DISTINCT individualID FROM");
    Query query=myShepherd.getPM().newQuery(filter);
    Iterator it2=myShepherd.getAllMarkedIndividuals(query, "individualID ascending");
    int numSharks=0;
    int numMales=0;
    int numFemales=0;
    
    //check for seasons wrapping over years
    int wrapsYear=0;
    //if(month1>month2) {wrapsYear=1;}
    

    
    while(it2.hasNext()) {
      String thisName=(String)it2.next();
      if(!thisName.equals("Unassigned")){
        MarkedIndividual s=myShepherd.getMarkedIndividual(thisName);
        double length=0;
      

        
        
       StringBuffer sb=new StringBuffer();

       //APRIL
       sb.append(s.sidesSightedInPeriod(year1, 4, 1, year1, 4, 7, locCode));
       sb.append(s.sidesSightedInPeriod(year1, 4, 8, year1, 4, 14, locCode));
       sb.append(s.sidesSightedInPeriod(year1, 4, 15, year1, 4, 21, locCode));
       sb.append(s.sidesSightedInPeriod(year1, 4, 22, year1, 4, 28, locCode));
       
       //May
       sb.append(s.sidesSightedInPeriod(year1, 5, 1, year1, 5, 7, locCode));
       sb.append(s.sidesSightedInPeriod(year1, 5, 8, year1, 5, 14, locCode));
       sb.append(s.sidesSightedInPeriod(year1, 5, 15, year1, 5, 21, locCode));
       sb.append(s.sidesSightedInPeriod(year1, 5, 22, year1, 5, 28, locCode));
       
       //JUNE
       sb.append(s.sidesSightedInPeriod(year1, 6, 1, year1, 6, 7, locCode));
       sb.append(s.sidesSightedInPeriod(year1, 6, 8, year1, 6, 14, locCode));
       sb.append(s.sidesSightedInPeriod(year1, 6, 15, year1, 6, 21, locCode));
       sb.append(s.sidesSightedInPeriod(year1, 6, 22, year1, 6, 28, locCode));
       
       //JULY
       sb.append(s.sidesSightedInPeriod(year1, 7, 1, year1, 7, 7, locCode));
       sb.append(s.sidesSightedInPeriod(year1, 7, 8, year1, 7, 14, locCode));
       sb.append(s.sidesSightedInPeriod(year1, 7, 15, year1, 7, 21, locCode));
       sb.append(s.sidesSightedInPeriod(year1, 7, 22, year1, 7, 28, locCode));
       
      
        

            if((sb.indexOf("1")!=-1)||(sb.indexOf("2")!=-1)||(sb.indexOf("3")!=-1)){
              out.println(sb.toString()+" 1; /*"+s.getName()+"*/<br>");
              numSharks++;
            }
          
        
        
      } //end if
    } //end while
    out.println("/*");
    out.println("</pre><br><br>Number of sharks identified during the study period: "+numSharks);

    
    out.println("*/");

    
    query.closeAll();
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();


  }
  catch(Exception e) {
    e.printStackTrace();
    out.println("You really screwed this one up!");
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
    e.printStackTrace();
    
  }
  out.close();
}

}