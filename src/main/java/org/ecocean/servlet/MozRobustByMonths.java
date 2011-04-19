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
		
		int startYear=(new Integer(request.getParameter("startYear"))).intValue();
		int startMonth=(new Integer(request.getParameter("startMonth"))).intValue();
		int endMonth=(new Integer(request.getParameter("endMonth"))).intValue();
		int endYear=(new Integer(request.getParameter("endYear"))).intValue();
		
		 try{
	      
		    //get our date values
		    int day1=(new Integer(request.getParameter("day1"))).intValue();
		    int day2=(new Integer(request.getParameter("day2"))).intValue();
		    int month1=(new Integer(request.getParameter("month1"))).intValue();
		    int month2=(new Integer(request.getParameter("month2"))).intValue();
		    int year1=(new Integer(request.getParameter("year1"))).intValue();
		    int year2=(new Integer(request.getParameter("year2"))).intValue();
		    
		    
		    GregorianCalendar gcMin=new GregorianCalendar(startYear, startMonth, 1);
		    GregorianCalendar gcMax=new GregorianCalendar(endYear, endMonth, 31);
		    
		    if(filter.equals("SELECT FROM org.ecocean.Encounter WHERE ")){
		      filter+="((dateInMilliseconds >= "+gcMin.getTimeInMillis()+") && (dateInMilliseconds <= "+gcMax.getTimeInMillis()+"))";
		    }
		    else{filter+="&& ((dateInMilliseconds >= "+gcMin.getTimeInMillis()+") && (dateInMilliseconds <= "+gcMax.getTimeInMillis()+"))";
		    }
		    
		    
		   
		    
		      } catch(NumberFormatException nfe) {
		    //do nothing, just skip on
		    nfe.printStackTrace();
		      }
		
		
		
		String locCode="4a";
		if(request.getParameter("locCode")!=null) {
			locCode=request.getParameter("locCode");
			String locIDFilter=" locationID == \""+locCode+"\"";
      if(filter.equals("SELECT FROM org.ecocean.Encounter WHERE ")){filter+=locIDFilter;}
      else{filter+=(" && "+locIDFilter);}
		}
		
		
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
		out.println("<br><br>Capture histories for Program Mark robust model:<br><br><pre>");
		filter=filter.replaceAll("SELECT FROM", "SELECT DISTINCT individualID FROM");
		Query query=myShepherd.getPM().newQuery(filter);
		Iterator it2=myShepherd.getAllMarkedIndividuals(query, "individualID ascending");
		int numSharks=0;
		int numMales=0;
		int numFemales=0;
		
		//check for seasons wrapping over years
		int wrapsYear=0;
		if(startMonth>endMonth) {wrapsYear=1;}
		

		
		while(it2.hasNext()) {
			MarkedIndividual s=myShepherd.getMarkedIndividual((String)it2.next());
			double length=0;
			
			
			
			//if((s.wasSightedInLocationCode(locCode))&&(s.wasSightedInPeriod(startYear,startMonth,endYear,endMonth))) {
				
				//calculate avgLength
				if(avgLength) {
					int yearCount=0;
					for(int r=startYear;r<=endYear;r++) {
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


					
				for(int f=startYear;f<=(endYear-wrapsYear);f++) {
						//boolean hasArrived=false;
						for (int m_month=startMonth;m_month<(endMonth+1+(wrapsYear*12));m_month++) {
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
	

						out.println(sb.toString()+" 1; /*"+s.getName()+"*/<br>");
						numSharks++;

					
				
				
			//} //end if
		} //end while
		out.println("</pre><br><br>Number of sharks identified during the study period: "+numSharks);
		if(segregate){
			out.println("<br><br>Number of males identified during the study period: "+numMales);
			out.println("<br><br>Number of females identified during the study period: "+numFemales);
		}
		
		out.println("<br><br>Length breakdown by month:<br>");
		//avgSecondaryLengths
		//numSecondaryLengths
		/*String lengthString="";
		for(int t=0;t<(endYear-startYear+1);t++){
			lengthString=(startYear+t)+":";
			for(int q=0;q<(endMonth-startMonth+1);q++){
				if(numSecondaryLengths[t][q]>0){
					lengthString+="&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"+(avgSecondaryLengths[t][q]/numSecondaryLengths[t][q])+" ("+numSecondaryLengths[t][q]+")";
				}
				else{lengthString+="&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;N/A";}
				
			}
			
			out.println((lengthString+"<br>"));
		}
		*/
		
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