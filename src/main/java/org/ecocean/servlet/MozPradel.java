package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.ecocean.*;

import javax.jdo.*;

//import com.poet.jdo.*;
import java.lang.StringBuffer;


//adds spots to a new encounter
public class MozPradel extends HttpServlet{
	


	
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
		
		//before any DB transactions, check permissions
		String locCode="4a1";


		
		myShepherd.beginDBTransaction();
		Extent sharkClass=myShepherd.getPM().getExtent(MarkedIndividual.class, true);
		Query query=myShepherd.getPM().newQuery(sharkClass);

		
		try {
			int startYear=(new Integer(request.getParameter("startYear"))).intValue();
			int startMonth=(new Integer(request.getParameter("startMonth"))).intValue();
			int endMonth=(new Integer(request.getParameter("endMonth"))).intValue();
			int endYear=(new Integer(request.getParameter("endYear"))).intValue();
			int numMales=0, numFemales=0;
			
			//check for seasons wrapping over years
			int wrapsYear=0;
			if(startMonth>endMonth) {wrapsYear=1;}
			
			//low period checks
			int numAlsoSightedInLowPeriod=0;
			
			//boolean segregate=false;
			boolean avgLength=false;
			//boolean bonnerSchwarzOutput=false;
			//boolean leftTagsOnly=false;
			

	    	

			//now, let's print out our capture histories
			out.println(ServletUtilities.getHeader(request));
			out.println("<br><br>Capture histories for live recaptures modeling: "+startYear+"-"+endYear+", months "+startMonth+"-"+endMonth+"<br><br><pre>");
		
			Iterator it2=myShepherd.getAllMarkedIndividuals(query);
			int numSharks=0;
			while(it2.hasNext()) {
				MarkedIndividual s=(MarkedIndividual)it2.next();
				//out.println("Name: "+s.getName());
				StringBuffer lengthSB=new StringBuffer();
				double length=0;
				
				//calculate average length if selected
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

				
				
				if((s.wasSightedInLocationCode(locCode))&&(s.wasSightedInPeriod(startYear,startMonth,endYear,endMonth))) {
					boolean wasReleased=false;
					StringBuffer sb=new StringBuffer();
					
					//out.println(s.getName());
					
					//lets print out each shark's capture history
					for(int f=startYear;f<=(endYear-wrapsYear);f++) {
						boolean sharkWasSeen=false;
						

							//out.println("Entering the right place on year "+f+" with: "+startYear+","+startMonth+","+endYear+","+endMonth);
							sharkWasSeen=s.wasSightedInPeriod(f,startMonth,1,(f+wrapsYear),endMonth, 31, locCode);
					
						if(sharkWasSeen){
							
						  //out.println(s.getName());
							
						
							//let's see if it was also sighted in a low period
							/*
						  boolean wasSightedOutsidePeriod=false;
							if(startMonth>endMonth) {
								for(int g=(startYear+1);g<=(endYear-wrapsYear);g++) {
									if(!wasSightedOutsidePeriod){
											wasSightedOutsidePeriod = ((s.wasSightedInPeriod(g,(12-endMonth+1),1,(g+wrapsYear),(startMonth-1), 31, locCode)));
									}
								}
							}
							if(wasSightedOutsidePeriod){numAlsoSightedInLowPeriod++;}
							*/
							
							
							//out.println("And I found a sighting!");
							sb.append("1");
						//	if(bonnerSchwarzOutput) {sb.append(" ");}
							wasReleased=true;
						}
						else{
							sb.append("0");
							//if(bonnerSchwarzOutput) {sb.append(" ");}
						}
					}
					if(wasReleased) {

						
						
						//average length as a covariate format
						// if(avgLength) {
							
						//	out.println(sb.toString()+" 1 "+length+"; /*"+s.getName()+"*/<br>");
						//}
						

						
						//plain old recaptures case
						//else{
							out.println(sb.toString()+" 1; /*"+s.getName()+"*/<br>");
						//}
						numSharks++;
					}
				
				} //end if
			} //end while
			out.println("</pre><br><br>/*Number of sharks identified during the study period: "+numSharks);
			out.println("</pre><br><br>Number also sighted outside the study period: "+numAlsoSightedInLowPeriod+"*/");
			
			query.closeAll();
			myShepherd.rollbackDBTransaction();
			myShepherd.closeDBTransaction();
			query=null;
			out.println(ServletUtilities.getFooter(context));

		}
		catch(Exception e) {
			out.println("<p><strong>Error encountered</strong></p>");
			out.println("<p>Please let the webmaster know you encountered an error at: ningalooPradel servlet</p>");
			query.closeAll();
			myShepherd.rollbackDBTransaction();
			myShepherd.closeDBTransaction();
			query=null;
			out.println(ServletUtilities.getFooter(context));

		}
		

		out.close();
	}

	
	}