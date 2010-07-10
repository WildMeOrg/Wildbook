package org.ecocean.servlet;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import org.ecocean.*;
import java.util.Vector;

//Set alternateID for this encounter/sighting
public class EncounterSetAsUnidentifiable extends HttpServlet {
	
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
		

			if (request.getParameter("number")!=null) {
				myShepherd.beginDBTransaction();
				Encounter enc2reject=myShepherd.getEncounter(request.getParameter("number"));
				setDateLastModified(enc2reject);
				boolean isOK=enc2reject.isAssignedToMarkedIndividual().equals("Unassigned");
				myShepherd.rollbackDBTransaction();
				if (isOK) {
					
					myShepherd.beginDBTransaction();
					try{
					
						enc2reject.reject();
						enc2reject.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>Set this encounter as unidentifiable in the database.</p>");
						enc2reject.approved=false;
						}catch(Exception le){
							locked=true;
							le.printStackTrace();
							myShepherd.rollbackDBTransaction();
						}
					
					
					
					if(!locked){
						String submitterEmail=enc2reject.getSubmitterEmail();
						myShepherd.commitDBTransaction();
						out.println(ServletUtilities.getHeader());
						out.println("<strong>Success:</strong> I have set encounter "+request.getParameter("number")+" as unidentifiable in the database.");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">View unidentifiable encounter #"+request.getParameter("number")+"</a></p>\n");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/allEncounters.jsp\">View all encounters</a></font></p>");
  						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/allIndividuals.jsp\">View all individuals</a></font></p>");				
						out.println(ServletUtilities.getFooter());
						String message="Encounter #"+request.getParameter("number")+" was set as unidentifiable in the database.";
						ServletUtilities.informInterestedParties(request.getParameter("number"), message);
						
						String emailUpdate="\nEncounter: "+request.getParameter("number")+"\nhttp://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\n";
						Vector e_images=new Vector();
						NotificationMailer mailer=new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), submitterEmail, ("Encounter update: "+request.getParameter("number")), ServletUtilities.getText("dataOnlyUpdate.txt")+emailUpdate, e_images);
						
					} else {
						out.println(ServletUtilities.getHeader());
						out.println("<strong>Failure:</strong> I have NOT modified encounter "+request.getParameter("number")+" in the database because another user is currently modifying its entry. Please try this operation again in a few seconds.");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">View unidentifiable encounter #"+request.getParameter("number")+"</a></p>\n");
						out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
  						out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");				
						out.println(ServletUtilities.getFooter());
						
					}
					
					}
			
				else {
					out.println(ServletUtilities.getHeader());
					out.println("Encounter# "+request.getParameter("number")+" is assigned to an individual and cannot be set as unidentifiable until it has been removed from that individual.");
					out.println(ServletUtilities.getFooter());
					}
			}
				
			else {
						out.println(ServletUtilities.getHeader());
						out.println("<strong>Error:</strong> I do not know which encounter you are trying to remove.");
						out.println(ServletUtilities.getFooter());	
					
				}	
		
			out.close();
			myShepherd.closeDBTransaction();
    	}
}
	
	
