package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.ecocean.*;

import javax.jdo.*;

import java.lang.StringBuffer;
import java.util.GregorianCalendar;



public class SideFeed extends HttpServlet{

  //Shepherd myShepherd;


  public void init(ServletConfig config) throws ServletException {
      super.init(config);
      //myShepherd=new Shepherd();
    }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
      doPost(request, response);
  }



  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{

    //set the response
    response.setContentType("text/html");
    
    String context="context0";
    context=ServletUtilities.getContext(request);

    //vector of dates for the study
    Vector dates=new Vector();
    boolean monthly=false;
    if(request.getParameter("monthly")!=null){monthly=true;}
    Shepherd myShepherd= new Shepherd(context);

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
    if(request.getParameter("comments")!=null){
      out.println("/*<br><br>Capture histories for side tagging analysis:<br><br><pre>*/");
    }
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
      //if(!thisName.equals("Unassigned")){
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

              if((((sb.indexOf("1")!=-1)&&(sb.indexOf("2")!=-1))||(sb.indexOf("4")!=-1))&&(sb.indexOf("3")==-1)){

                //we need to break this into two encounters then
                //one is for all left-sides
                //the other for all right sides

                //lefts
                String leftLink="";
                if(request.getParameter("comments")!=null){
                  leftLink="/* Spawned left-side only from http://www.whaleshark.org/individuals.jsp?number="+s.getName()+" */";
                }
                StringBuffer sbLeft=new StringBuffer(sb.toString());
                out.println(sbLeft.toString().replaceAll("2", "0").replaceAll("4", "1")+" 1;  "+leftLink+"<br />");
                numSharks++;

                //rights
                String rightLink="";
                if(request.getParameter("comments")!=null){
                  rightLink="/* Spawned right-side only from http://www.whaleshark.org/individuals.jsp?number="+s.getName()+" */";
                }
                StringBuffer sbRight=new StringBuffer(sb.toString());
                out.println(sbRight.toString().replaceAll("1", "0").replaceAll("4", "2")+" 1;  "+rightLink+"<br />");
                numSharks++;

              }
              else{
                String link="";
                if(request.getParameter("comments")!=null){
                  link="/* http://www.whaleshark.org/individuals.jsp?number="+s.getName()+" */";
                }
                out.println(sb.toString()+" 1;  "+link+"<br />");
                numSharks++;
              }


            }



     // } //end if
    } //end while


    //now process encounters with right-side encounters
    filter=filter.replaceAll("SELECT DISTINCT individualID FROM", "SELECT FROM");
    //out.println(filter+"<br />");

    Query query2=myShepherd.getPM().newQuery(filter);
    Iterator it3=myShepherd.getAllEncounters(query2, "individualID ascending");
    while(it3.hasNext()) {
      Encounter thisEnc = (Encounter)it3.next();
      String thisName=ServletUtilities.handleNullString(thisEnc.getIndividualID());
      StringBuffer sb=new StringBuffer();
      if((thisEnc.getIndividualID()!=null)&&(thisEnc.getNumRightSpots()>0)){

        //APRIL
        GregorianCalendar gcApril1a=new GregorianCalendar(year1, 4, 1);
        GregorianCalendar gcApril1b=new GregorianCalendar(year1, 4, 7);
        if((thisEnc.getDateInMilliseconds()>=gcApril1a.getTimeInMillis())&&(thisEnc.getDateInMilliseconds()<=gcApril1b.getTimeInMillis())){
          sb.append("2");
        }
        else{
          sb.append("0");
        }
        GregorianCalendar gcApril2a=new GregorianCalendar(year1, 4, 8);
        GregorianCalendar gcApril2b=new GregorianCalendar(year1, 4, 14);
        if((thisEnc.getDateInMilliseconds()>=gcApril2a.getTimeInMillis())&&(thisEnc.getDateInMilliseconds()<=gcApril2b.getTimeInMillis())){
          sb.append("2");
        }
        else{
          sb.append("0");
        }
        GregorianCalendar gcApril3a=new GregorianCalendar(year1, 4, 15);
        GregorianCalendar gcApril3b=new GregorianCalendar(year1, 4, 21);
        if((thisEnc.getDateInMilliseconds()>=gcApril3a.getTimeInMillis())&&(thisEnc.getDateInMilliseconds()<=gcApril3b.getTimeInMillis())){
          sb.append("2");
        }
        else{
          sb.append("0");
        }
        GregorianCalendar gcApril4a=new GregorianCalendar(year1, 4, 22);
        GregorianCalendar gcApril4b=new GregorianCalendar(year1, 4, 28);
        if((thisEnc.getDateInMilliseconds()>=gcApril4a.getTimeInMillis())&&(thisEnc.getDateInMilliseconds()<=gcApril4b.getTimeInMillis())){
          sb.append("2");
        }
        else{
          sb.append("0");
        }

        //May
        gcApril1a=new GregorianCalendar(year1, 5, 1);
        gcApril1b=new GregorianCalendar(year1, 5, 7);
        if((thisEnc.getDateInMilliseconds()>=gcApril1a.getTimeInMillis())&&(thisEnc.getDateInMilliseconds()<=gcApril1b.getTimeInMillis())){
          sb.append("2");
        }
        else{
          sb.append("0");
        }
        gcApril2a=new GregorianCalendar(year1, 5, 8);
        gcApril2b=new GregorianCalendar(year1, 5, 14);
        if((thisEnc.getDateInMilliseconds()>=gcApril2a.getTimeInMillis())&&(thisEnc.getDateInMilliseconds()<=gcApril2b.getTimeInMillis())){
          sb.append("2");
        }
        else{
          sb.append("0");
        }
        gcApril3a=new GregorianCalendar(year1, 5, 15);
        gcApril3b=new GregorianCalendar(year1, 5, 21);
        if((thisEnc.getDateInMilliseconds()>=gcApril3a.getTimeInMillis())&&(thisEnc.getDateInMilliseconds()<=gcApril3b.getTimeInMillis())){
          sb.append("2");
        }
        else{
          sb.append("0");
        }
        gcApril4a=new GregorianCalendar(year1, 5, 22);
        gcApril4b=new GregorianCalendar(year1, 5, 28);
        if((thisEnc.getDateInMilliseconds()>=gcApril4a.getTimeInMillis())&&(thisEnc.getDateInMilliseconds()<=gcApril4b.getTimeInMillis())){
          sb.append("2");
        }
        else{
          sb.append("0");
        }

        //June
        gcApril1a=new GregorianCalendar(year1, 6, 1);
        gcApril1b=new GregorianCalendar(year1, 6, 7);
        if((thisEnc.getDateInMilliseconds()>=gcApril1a.getTimeInMillis())&&(thisEnc.getDateInMilliseconds()<=gcApril1b.getTimeInMillis())){
          sb.append("2");
        }
        else{
          sb.append("0");
        }
        gcApril2a=new GregorianCalendar(year1, 6, 8);
        gcApril2b=new GregorianCalendar(year1, 6, 14);
        if((thisEnc.getDateInMilliseconds()>=gcApril2a.getTimeInMillis())&&(thisEnc.getDateInMilliseconds()<=gcApril2b.getTimeInMillis())){
          sb.append("2");
        }
        else{
          sb.append("0");
        }
        gcApril3a=new GregorianCalendar(year1, 6, 15);
        gcApril3b=new GregorianCalendar(year1, 6, 21);
        if((thisEnc.getDateInMilliseconds()>=gcApril3a.getTimeInMillis())&&(thisEnc.getDateInMilliseconds()<=gcApril3b.getTimeInMillis())){
          sb.append("2");
        }
        else{
          sb.append("0");
        }
        gcApril4a=new GregorianCalendar(year1, 6, 22);
        gcApril4b=new GregorianCalendar(year1, 6, 28);
        if((thisEnc.getDateInMilliseconds()>=gcApril4a.getTimeInMillis())&&(thisEnc.getDateInMilliseconds()<=gcApril4b.getTimeInMillis())){
          sb.append("2");
        }
        else{
          sb.append("0");
        }

        //July
        gcApril1a=new GregorianCalendar(year1, 7, 1);
        gcApril1b=new GregorianCalendar(year1, 7, 7);
        if((thisEnc.getDateInMilliseconds()>=gcApril1a.getTimeInMillis())&&(thisEnc.getDateInMilliseconds()<=gcApril1b.getTimeInMillis())){
          sb.append("2");
        }
        else{
          sb.append("0");
        }
        gcApril2a=new GregorianCalendar(year1, 7, 8);
        gcApril2b=new GregorianCalendar(year1, 7, 14);
        if((thisEnc.getDateInMilliseconds()>=gcApril2a.getTimeInMillis())&&(thisEnc.getDateInMilliseconds()<=gcApril2b.getTimeInMillis())){
          sb.append("2");
        }
        else{
          sb.append("0");
        }
        gcApril3a=new GregorianCalendar(year1, 7, 15);
        gcApril3b=new GregorianCalendar(year1, 7, 21);
        if((thisEnc.getDateInMilliseconds()>=gcApril3a.getTimeInMillis())&&(thisEnc.getDateInMilliseconds()<=gcApril3b.getTimeInMillis())){
          sb.append("2");
        }
        else{
          sb.append("0");
        }
        gcApril4a=new GregorianCalendar(year1, 7, 22);
        gcApril4b=new GregorianCalendar(year1, 7, 28);
        if((thisEnc.getDateInMilliseconds()>=gcApril4a.getTimeInMillis())&&(thisEnc.getDateInMilliseconds()<=gcApril4b.getTimeInMillis())){
          sb.append("2");
        }
        else{
          sb.append("0");
        }

        String link="";
        if(request.getParameter("comments")!=null){
          link="/* Right-side encounter http://www.whaleshark.org/encounters/encounter.jsp?number="+thisEnc.getEncounterNumber()+" */";
        }
        out.println(sb.toString()+" 1;  "+link+"<br />");
        numSharks++;




      }
    }


    if(request.getParameter("comments")!=null){
      out.println("/*");
      out.println("</pre><br><br>Number of sharks identified during the study period: "+numSharks);
      out.println("*/");
    }




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