package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.ecocean.*;

import javax.jdo.*;

import java.lang.StringBuffer;


//adds spots to a new encounter
public class NingalooMultistate extends HttpServlet{
	
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
		PrintWriter out = response.getWriter();
		
		String context="context0";
    context=ServletUtilities.getContext(request);
    
    Shepherd myShepherd = new Shepherd(context);
		
		//before any DB transactions, check permissions
		String locCode="1a";
		if(request.getParameter("locCode")!=null) {
			locCode=request.getParameter("locCode");
		}
		if((request.isUserInRole("manager"))||(request.isUserInRole("admin"))||(request.isUserInRole(locCode))) {
		
		
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
			if(startMonth<endMonth) {wrapsYear=1;}
			
			boolean segregate=false;
			boolean avgLength=false;
			boolean leftTagsOnly=false;
			
			if(request.getParameter("option")!=null) {
				if(request.getParameter("option").equals("avgLength")) {
					avgLength=true;
				}
				else if(request.getParameter("option").equals("segregate")) {
					segregate=true;
				}
			}
			if(request.getParameter("leftTagsOnly")!=null) {
				leftTagsOnly=true;
			}
	    	

			//now, let's print out our capture histories
			out.println(ServletUtilities.getHeader(request));
			out.println("<br><br>Capture histories for live recaptures modeling: "+startYear+"-"+endYear+", months "+startMonth+"-"+endMonth+"<br><br><pre>");
		
			Iterator it2=myShepherd.getAllMarkedIndividuals(query);
			int numSharks=0;
			while(it2.hasNext()) {
				MarkedIndividual s=(MarkedIndividual)it2.next();
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
				if((s.wasSightedInLocationCode("1a"))&&(s.wasSightedInPeriod(startYear,startMonth,endYear,endMonth))) {
					boolean wasReleased=false;
					StringBuffer sb=new StringBuffer();

					//lets print out each shark's capture history
					for(int f=startYear;f<(endYear+1);f++) {
						boolean sharkWasSeen=false;
						
						if(leftTagsOnly){
							sharkWasSeen=((s.wasSightedInPeriodLeftOnly(f,startMonth,(f+wrapsYear),endMonth))&&(s.wasSightedInYearLeftTagsOnly(f, locCode)));
						}
						else {
							sharkWasSeen=((s.wasSightedInPeriod(f,startMonth,(f+wrapsYear),endMonth))&&(s.wasSightedInYear(f, locCode)));
						}
						if(sharkWasSeen){
							
							if((s.wasSightedInYear(f, "1a1"))&&(s.wasSightedInYear(f, "1a2"))){
								sb.append("3");
							}
							else if(s.wasSightedInYear(f, "1a1")){
								sb.append("1");
							}
							else if(s.wasSightedInYear(f, "1a2")){
								sb.append("2");
							}
							else{
								sb.append("0");
							}
							
							
							
							wasReleased=true;
							
						}
						else{
							sb.append("0");
							
						}
					}
					if(wasReleased) {
				
						//sexually segregated groups
						if(segregate) {
							if(s.getSex().equals("male")){
								out.println(sb.toString()+" 100; /*"+s.getName()+"*/<br>");
								numMales++;
							}
							else if(s.getSex().equals("female")){
								out.println(sb.toString()+" 010; /*"+s.getName()+"*/<br>");
								numFemales++;
							}
							else {
								out.println(sb.toString()+" 001; /*"+s.getName()+"*/<br>");
							}
						}
						
						
						//average length as a covariate format
						else if(avgLength) {
							
							out.println(sb.toString()+" 1 "+length+"; /*"+s.getName()+"*/<br>");
						}
						
						
						//plain old recaptures case
						else{
							out.println(sb.toString()+" 1; /*"+s.getName()+"*/<br>");
						}
						numSharks++;
					}
				
				} //end if
			} //end while
			out.println("</pre><br><br>Number of sharks identified during the study period: "+numSharks);
			query.closeAll();
			myShepherd.rollbackDBTransaction();
			myShepherd.closeDBTransaction();
			query=null;
			out.println(ServletUtilities.getFooter(context));

		}
		catch(Exception e) {
			e.printStackTrace();
			out.println("<p><strong>Error encountered</strong></p>");
			out.println("<p>Please let the webmaster know you encountered an error at: ningalooPradel servlet</p>");
			query.closeAll();
			myShepherd.rollbackDBTransaction();
			myShepherd.closeDBTransaction();
			query=null;
			out.println(ServletUtilities.getFooter(context));

		}
		
		} //end if has right role
		else {
			out.println(ServletUtilities.getHeader(request));
			out.println("<p><strong>Permission denied</strong></p>");
			out.println("<p>You do not have permissions to access this capture history.</p>");
			out.println(ServletUtilities.getFooter(context));
		}
		out.close();
	}
	
	
		
	
	}