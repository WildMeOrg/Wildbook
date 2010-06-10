package org.ecocean.servlet;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import org.ecocean.*;


public class EncounterResetDate extends HttpServlet {
	
	public void init(ServletConfig config) throws ServletException {
    	super.init(config);
    }

	
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
		doPost(request, response);
	}
	
	
	private void setDateLastModified(Encounter enc){
		String strOutputDateTime = ServletUtilities.getDate();
		enc.setDWCDateLastModified(strOutputDateTime);
	}
		

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
		Shepherd myShepherd=new Shepherd();
		//set up for response
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		boolean locked=false;

		boolean isOwner=true;
		
		/**
		if(request.getParameter("number")!=null){
			myShepherd.beginDBTransaction();
			if(myShepherd.isEncounter(request.getParameter("number"))) {
				Encounter verifyMyOwner=myShepherd.getEncounter(request.getParameter("number"));
				String locCode=verifyMyOwner.getLocationCode();
				
				//check if the encounter is assigned
				if((verifyMyOwner.getSubmitterID()!=null)&&(request.getRemoteUser()!=null)&&(verifyMyOwner.getSubmitterID().equals(request.getRemoteUser()))){
					isOwner=true;
				}
				
				//if the encounter is assigned to this user, they have permissions for it...or if they're a manager
				else if((request.isUserInRole("admin"))){
					isOwner=true;
				}
				//if they have general location code permissions for the encounter's location code
				else if(request.isUserInRole(locCode)){isOwner=true;}
			}
			myShepherd.rollbackDBTransaction();	
		}
*/
			if ((request.getParameter("number")!=null)&&(request.getParameter("day")!=null)&&(request.getParameter("month")!=null)&&(request.getParameter("year")!=null)&&(request.getParameter("hour")!=null)&&(request.getParameter("minutes")!=null)) {
				myShepherd.beginDBTransaction();
				Encounter fixMe=myShepherd.getEncounter(request.getParameter("number"));
				setDateLastModified(fixMe);
				String oldDate="";
				String newDate="";
				

				try{
				
				oldDate=fixMe.getDate();
				fixMe.setDay(Integer.parseInt(request.getParameter("day")));
				fixMe.setMonth(Integer.parseInt(request.getParameter("month")));
				fixMe.setYear(Integer.parseInt(request.getParameter("year")));
				fixMe.setHour(Integer.parseInt(request.getParameter("hour")));
				fixMe.setMinutes(request.getParameter("minutes"));
				newDate=fixMe.getDate();
				fixMe.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>Changed encounter date from "+oldDate+" to "+newDate+".</p>");
				
				}catch(Exception le){
					locked=true;
								le.printStackTrace();
								myShepherd.rollbackDBTransaction();
							}
				
				
				out.println(ServletUtilities.getHeader());
				if(!locked){
				
					myShepherd.commitDBTransaction();
					out.println("<strong>Success:</strong> I have changed the encounter date from "+oldDate+" to "+newDate+".");
					out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
					String message="The date of encounter #"+request.getParameter("number")+" was changed from "+oldDate+" to "+newDate+".";
					ServletUtilities.informInterestedParties(request.getParameter("number"), message);
				} else {
										
					out.println("<strong>Failure:</strong> I have NOT changed the encounter date because another user is currently modifying this encounter. Please try this operation again in a few seconds.");
					out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
					
					
					
				}
				out.println(ServletUtilities.getFooter());

			} else {
							out.println(ServletUtilities.getHeader());
							out.println("<strong>Error:</strong> I don't have enough information to complete your request.");
							out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n"); 
							out.println(ServletUtilities.getFooter());
					}	
			

			out.close();
			myShepherd.closeDBTransaction();
    	}
}
	
	
