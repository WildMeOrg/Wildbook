package org.ecocean.servlet;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import org.ecocean.*;



public class EncounterSetSubmitterPhotographerContactInfo extends HttpServlet {
	
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

		//reset photographer/submitter contact info
				if ((request.getParameter("number")!=null)&&(request.getParameter("contact")!=null)) {
					myShepherd.beginDBTransaction();
					Encounter changeMe=myShepherd.getEncounter(request.getParameter("number"));
					setDateLastModified(changeMe);
					String oldName="";
					String oldEmail="";
					String oldAddress="";
					String oldPhone="";
					String oldContact="";
					String newContact="";
					
					
					try{
					
						if (request.getParameter("contact").equals("submitter")) {
							oldName=changeMe.getSubmitterName();
							oldEmail=changeMe.getSubmitterEmail();
							oldAddress=changeMe.getSubmitterAddress();
							oldPhone=changeMe.getSubmitterPhone();
							oldContact=oldName+", "+oldEmail+", "+oldAddress+", "+oldPhone;
							changeMe.setSubmitterName(request.getParameter("name"));
							changeMe.setSubmitterEmail(request.getParameter("email"));
							changeMe.setSubmitterPhone(request.getParameter("phone"));
							changeMe.setSubmitterAddress(request.getParameter("address"));
							if(request.getParameter("name")!=null) {newContact+=request.getParameter("name")+", ";}
							if(request.getParameter("email")!=null) {newContact+=request.getParameter("email")+", ";}
							if(request.getParameter("address")!=null) {newContact+=request.getParameter("address")+", ";}
							if(request.getParameter("phone")!=null) {newContact+=request.getParameter("phone");}
							
							changeMe.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>Changed submitter contact info from<br>"+oldContact+"<br>to<br>"+newContact+".</p>");
						}
						else {
							oldName=changeMe.getPhotographerName();
							oldEmail=changeMe.getPhotographerEmail();
							oldAddress=changeMe.getPhotographerAddress();
							oldPhone=changeMe.getPhotographerPhone();
							oldContact=oldName+", "+oldEmail+", "+oldAddress+", "+oldPhone;
							changeMe.setPhotographerName(request.getParameter("name"));
							changeMe.setPhotographerEmail(request.getParameter("email"));
							changeMe.setPhotographerPhone(request.getParameter("phone"));
							changeMe.setPhotographerAddress(request.getParameter("address"));
							if(request.getParameter("name")!=null) {newContact+=request.getParameter("name")+", ";}
							if(request.getParameter("email")!=null) {newContact+=request.getParameter("email")+", ";}
							if(request.getParameter("address")!=null) {newContact+=request.getParameter("address")+", ";}
							if(request.getParameter("phone")!=null) {newContact+=request.getParameter("phone");}
							
							changeMe.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>Changed photographer contact info from<br>"+oldContact+"<br>to<br>"+newContact+".</p>");
						}

					}
					catch(Exception le){
						locked=true;
						le.printStackTrace();
						myShepherd.rollbackDBTransaction();
					}
					
					
					if(!locked) {
						myShepherd.commitDBTransaction();
						out.println(ServletUtilities.getHeader());
						out.println("<strong>Success:</strong> Encounter contact information has been updated from:<br><i>"+oldContact+"</i><br>to<br><i>"+newContact+"</i>.");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
						out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
      					out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
      					out.println(ServletUtilities.getFooter());
						String message="The photographer or submitter contact information for encounter #"+request.getParameter("number")+"has been updated from "+oldContact+" to "+newContact+".";
						ServletUtilities.informInterestedParties(request.getParameter("number"), message);
					}else {
						out.println(ServletUtilities.getHeader());
						out.println("<strong>Failure:</strong> Encounter contact information was NOT updated because another user is currently modifying the record for this encounter.");
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
	
	
