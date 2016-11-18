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
public class MozRobustByMonths extends HttpServlet{
	
	//Shepherd myShepherd;

	
	public void init(ServletConfig config) throws ServletException {
    	super.init(config);
    	//myShepherd=new Shepherd();
  	}

	
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
    	doPost(request, response);
	}
		


	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
		
	  
	  String context="context0";
    context=ServletUtilities.getContext(request);
    
    
		//set the response
		response.setContentType("text/html");
		
		Shepherd myShepherd=new Shepherd(context);
		
		//vector of dates for the study
		//Vector dates=new Vector();
		boolean monthly=false;
		if(request.getParameter("monthly")!=null){monthly=true;}

		PrintWriter out = response.getWriter();
		try {
		
      myShepherd.beginDBTransaction();
      //Extent sharkClass=myShepherd.getPM().getExtent(MarkedIndividual.class, true);
      //Query query=myShepherd.getPM().newQuery(sharkClass);
      
      String filter="SELECT FROM org.ecocean.Encounter WHERE ";
		
		//int startYear=(new Integer(request.getParameter("startYear"))).intValue();
		//int startMonth=(new Integer(request.getParameter("startMonth"))).intValue();
		//int endMonth=(new Integer(request.getParameter("endMonth"))).intValue();
		//int endYear=(new Integer(request.getParameter("endYear"))).intValue();
		
      
      int day1=1;
      int day2=31;
      int month1=10;
      int month2=2;
      int year1=2005;
      int year2=2011;
 
		 try{
	      
		    //get our date values
		    day1=(new Integer(request.getParameter("day1"))).intValue();
		    day2=(new Integer(request.getParameter("day2"))).intValue();
		    month1=(new Integer(request.getParameter("month1"))).intValue();
		    month2=(new Integer(request.getParameter("month2"))).intValue();
		    year1=(new Integer(request.getParameter("year1"))).intValue();
		    year2=(new Integer(request.getParameter("year2"))).intValue();
		    
		    
		    GregorianCalendar gcMin=new GregorianCalendar(year1, month1, 1);
		    GregorianCalendar gcMax=new GregorianCalendar(year2, month2, 31);
		    
		    filter+="((dateInMilliseconds >= "+gcMin.getTimeInMillis()+") && (dateInMilliseconds <= "+gcMax.getTimeInMillis()+"))";
	
		    //filter+="&& (name != 'Unassigned')";
		    
		   
		    
		      } catch(NumberFormatException nfe) {
		    //do nothing, just skip on
		    nfe.printStackTrace();
		      }
		
		
		
		String locCode="4a";
		if(request.getParameter("locCode")!=null) {
			locCode=request.getParameter("locCode");
			
		}
		String locIDFilter=" locationID == \""+locCode+"\"";
    filter+=(" && "+locIDFilter);
		
		
		boolean segregate=false;
		if(request.getParameter("segregate")!=null) {
			segregate=true;
		}
		
		boolean avgLength=false;
		if(request.getParameter("avgLength")!=null) {
			avgLength=true;
		}
		
		boolean multistrata=false;
		if(request.getParameter("multistrata")!=null) {
			multistrata=true;
		}
		

    
    
		//now, let's print out our capture histories
		out.println("/*<br><br>Capture histories for Program Mark robust model:<br><br><pre>*/");
		filter=filter.replaceAll("SELECT FROM", "SELECT DISTINCT individualID FROM");
		Query query=myShepherd.getPM().newQuery(filter);
		Iterator it2=myShepherd.getAllMarkedIndividuals(query, "individualID ascending");
		int numSharks=0;
		int numMales=0;
		int numFemales=0;
		
		//check for seasons wrapping over years
		int wrapsYear=0;
		if(month1>month2) {wrapsYear=1;}
		

		
		while(it2.hasNext()) {
		  String thisName=(String)it2.next();
		  //if(!thisName.equals("Unassigned")){
		    MarkedIndividual s=myShepherd.getMarkedIndividual(thisName);
		    double length=0;
			
			
			
		    //if((s.wasSightedInLocationCode(locCode))&&(s.wasSightedInPeriod(startYear,startMonth,endYear,endMonth))) {
				
				//calculate avgLength
				if(avgLength) {
					int yearCount=0;
					for(int r=year1;r<=year2;r++) {
						if(s.averageLengthInYear(r)>0.1){
							yearCount++;
							length+=s.averageLengthInYear(r);
						}
					}
					if(yearCount>0) {
						length=length/yearCount;
					}
				}
				
				
				//boolean wasReleased=false;
				StringBuffer sb=new StringBuffer();
				//lets print out each shark's capture history
				boolean sharkWasSeen=false;


					
				for(int f=year1;f<=(year2-wrapsYear);f++) {
						//boolean hasArrived=false;
						for (int m_month=month1;m_month<(month2+1+(wrapsYear*12));m_month++) {
							boolean sharkWasSeen1=false;
							boolean sharkWasSeen2=false;
							//boolean multistrata1=false;
							//boolean multistrata2=false;
							int innerMonth=m_month;
							int innerYear=f;
							if(innerMonth>12){
								innerMonth=innerMonth-12;
								innerYear=innerYear+1;
							}
							int numberEncounters=s.totalEncounters();
							Vector encounters=s.getEncounters();
							for(int c=0;c<numberEncounters;c++) {
								Encounter temp=(Encounter)encounters.get(c);
								int year=temp.getYear();							
								int month=temp.getMonth();
								int day=temp.getDay();
								if((year==innerYear)&&(month==innerMonth)&&(temp.getLocationCode().startsWith(locCode))){
									
									if(!monthly){
										if((day>=1)&&(day<=15)) {
											sharkWasSeen1=true;
										}
										if(day>15){
											sharkWasSeen2=true;
										}
									}
									else{
										sharkWasSeen1=true;
									}
									
								}

							}
							
							
							if(!monthly){
								if(sharkWasSeen1) {
									sb.append("1");
									sharkWasSeen=true;
								}
								else{sb.append("0");}
								if(sharkWasSeen2) {
					
									sb.append("1");
						
									sharkWasSeen=true;
								}
								else{sb.append("0");}
							}
							else{
								if(sharkWasSeen1) {
									sb.append("1");
									sharkWasSeen=true;
								}
								else{sb.append("0");}
								
								
							}
							
							
						}
					
					}
	
				    if(sb.indexOf("1")!=-1){
				      out.println(sb.toString()+" 1; /*"+s.getName()+"*/<br>");
				      numSharks++;
				    }
					
				
				
			//} //end if
		} //end while
		out.println("/*");
		out.println("</pre><br><br>Number of sharks identified during the study period: "+numSharks);
		if(segregate){
			out.println("<br><br>Number of males identified during the study period: "+numMales);
			out.println("<br><br>Number of females identified during the study period: "+numFemales);
		}
		
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