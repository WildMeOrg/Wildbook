package org.ecocean.servlet;


import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import org.ecocean.*;


public class EncounterSetOccurrenceRemarks extends HttpServlet {
	
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

		//--------------------------------	
		//edit submitter comments

						if ((request.getParameter("fixComment")!=null)) {
							myShepherd.beginDBTransaction();
							Encounter changeMe=myShepherd.getEncounter(request.getParameter("number"));
							setDateLastModified(changeMe);
							String comment=request.getParameter("fixComment");
							String oldComment="None";
							
							
							
							try{
							
								oldComment=changeMe.getComments();
								changeMe.setOccurrenceRemarks(comment);
								changeMe.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>Changed submitted comments from:<br><i>"+oldComment+"</i><br>to:<br><i>"+comment+"</i></p>");
							}catch(Exception le){
								locked=true;
								le.printStackTrace();
								myShepherd.rollbackDBTransaction();
							}
							
							
							
							if(!locked){
								myShepherd.commitDBTransaction();
								out.println(ServletUtilities.getHeader());
								out.println("<strong>Success:</strong> Encounter submitted comments were updated from:<br><i>"+oldComment+"</i><br>to:<br><i>"+comment+"</i>");
								out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
								out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
		      					out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
		      					out.println(ServletUtilities.getFooter());
								String message="Encounter #"+request.getParameter("number")+" submitted comments have been updated from \""+oldComment+"\" to \""+comment+"\".";
								ServletUtilities.informInterestedParties(request.getParameter("number"), message);
							}else {
								out.println(ServletUtilities.getHeader());
								out.println("<strong>Failure:</strong> Encounter submitted comments were NOT updated because another user is currently modifying this reconrd. Please press the Back button in your browser and try to edit the comments again in a few seconds.");
								out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
								out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
		      					out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
		      					out.println(ServletUtilities.getFooter());
								
							}
						}		
							
						else {
							out.println(ServletUtilities.getHeader());
							out.println("<strong>Error:</strong> I don't have enough information to complete your request.");
							out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
							out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
		      				out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
		      				out.println(ServletUtilities.getFooter());	
								
							}
						
						
			out.close();
			myShepherd.closeDBTransaction();
    	}
}
	
	
