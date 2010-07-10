package org.ecocean.servlet;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import org.ecocean.*;


public class EncounterAddComment extends HttpServlet {
	
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


			myShepherd.beginDBTransaction();
			if ((request.getParameter("number")!=null)&&(request.getParameter("user")!=null)&&(request.getParameter("comments")!=null)&&(myShepherd.isEncounter(request.getParameter("number")))) {
				
				Encounter commentMe=myShepherd.getEncounter(request.getParameter("number"));
				setDateLastModified(commentMe);
				try{
				
					commentMe.addComments("<p><em>"+request.getParameter("user")+" on "+(new java.util.Date()).toString()+"</em><br>"+request.getParameter("comments")+"</p>");
				}
				catch(Exception le){
					locked=true;
					le.printStackTrace();
					myShepherd.rollbackDBTransaction();
				}
				
				
				
				out.println(ServletUtilities.getHeader());
				if(!locked){
					myShepherd.commitDBTransaction();
					out.println("<strong>Success:</strong> I have successfully added your comments.");
					out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
					String message="A new comment has been added to encounter #"+request.getParameter("number")+". The new comment is: \n"+request.getParameter("comments");
					ServletUtilities.informInterestedParties(request.getParameter("number"), message);
				}else{
					out.println("<strong>Failure:</strong> I did NOT add your comments. Another user is currently modifying the entry for this encounter. Please try to add your comments again in a few seconds.");
					out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
				}
				out.println(ServletUtilities.getFooter());


			} else {
							out.println(ServletUtilities.getHeader());
							out.println("<strong>Error:</strong> I don't have enough information to add your comments.");
							out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n"); 
							out.println(ServletUtilities.getFooter());
					}	
			myShepherd.closeDBTransaction();
			
			

			out.close();
			myShepherd.closeDBTransaction();
    	}
}
	
	
