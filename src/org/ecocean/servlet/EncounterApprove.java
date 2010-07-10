package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import org.ecocean.*;


//Set alternateID for this encounter/sighting
public class EncounterApprove extends HttpServlet {
	
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
		}*/


			if (!(request.getParameter("number")==null)) {
				myShepherd.beginDBTransaction();
				Encounter newenc=myShepherd.getEncounter(request.getParameter("number"));
				
				setDateLastModified(newenc);
				
					
					try{
					
						newenc.approve();
						newenc.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>"+"Approved this encounter for public display.");
					}catch(Exception le){
						locked=true;
						le.printStackTrace();
						myShepherd.rollbackDBTransaction();
						
					}
					
					
					
					if(!locked){
						myShepherd.commitDBTransaction();
						out.println(ServletUtilities.getHeader());
						out.println("<strong>Success:</strong> I have approved this encounter "+request.getParameter("number")+" for inclusion in the database.");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
						out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
						out.println("<p><a href=\"encounters/allEncountersUnapproved.jsp?start=1&end=10&sort=nosort\">View all unapproved encounters</a></font></p>");
						out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");			
						out.println(ServletUtilities.getFooter());
						String message="Encounter "+request.getParameter("number")+" was approved for inclusion in the visual database.";
						ServletUtilities.informInterestedParties(request.getParameter("number"), message);
					} 
					else {
						out.println(ServletUtilities.getHeader());
						out.println("<strong>Failure:</strong> I have NOT approved this encounter "+request.getParameter("number")+" for the visual database. This new encounter is currently being modified by another user.");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
						out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
							out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
									
						out.println(ServletUtilities.getFooter());		
					}
				}
				else {
						out.println(ServletUtilities.getHeader());
						out.println("<strong>Error:</strong> I don't know which new encounter you're trying to approve.");
						out.println(ServletUtilities.getFooter());	
				}

			out.close();
			myShepherd.closeDBTransaction();
    	}
}
	
	
