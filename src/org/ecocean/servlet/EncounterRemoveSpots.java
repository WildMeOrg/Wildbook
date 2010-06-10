package org.ecocean.servlet;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import org.ecocean.*;


public class EncounterRemoveSpots extends HttpServlet {
	
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
		
		/*
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
				String side="left";
				myShepherd.beginDBTransaction();
				Encounter despotMe=myShepherd.getEncounter(request.getParameter("number"));
				boolean assigned=false;
				
				try{
					if(despotMe.isAssignedToMarkedIndividual().equals("Unassigned")){
				
						if((request.getParameter("rightSide")!=null)&&(request.getParameter("rightSide").equals("true"))) {
							despotMe.removeRightSpots();
							despotMe.hasRightSpotImage=false;
							despotMe.rightSpotImageFileName="";
							side="right";
							despotMe.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>Removed "+side+"-side spot data.</p>");
							despotMe.setNumRightSpots(0);
						}
						else if((request.getParameter("rightSide")!=null)&&(request.getParameter("rightSide").equals("false"))) {
				
							despotMe.removeSpots();
							despotMe.hasSpotImage=false;
							despotMe.spotImageFileName="";
							despotMe.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>Removed "+side+"-side spot data.</p>");
							despotMe.setNumLeftSpots(0);
						}
					}
					else{
						locked=true;
						myShepherd.rollbackDBTransaction();
						assigned=true;
					}
				
				}catch(Exception le){
					locked=true;
								le.printStackTrace();
								myShepherd.rollbackDBTransaction();
							}
				
				
				out.println(ServletUtilities.getHeader());
				if(!locked){
					myShepherd.commitDBTransaction();
					out.println("<strong>Success:</strong> I have removed spot data for encounter "+request.getParameter("number")+".");
					out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
					String message="The spot-matching data for encounter "+request.getParameter("number")+" was removed.";
					ServletUtilities.informInterestedParties(request.getParameter("number"), message);
				} else{
					
					if(assigned) {
						out.println("<strong>Failure:</strong> I was NOT able to remove the spot data because the encounter has been assigned to a marked individual. Please try to remove the spot data again after removing the encounter from the individual if appropriate.");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
					}
					else{
					
						out.println("<strong>Failure:</strong> I was NOT able to remove the spot data because another user is currently modifying this encounter, or you did not specify a side to remove spot data from. Please try to remove the spot data again in a few seconds.");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
					}
				}
				
				out.println(ServletUtilities.getFooter());
			} 
			else {
							out.println(ServletUtilities.getHeader());
							out.println("<strong>Error:</strong> I don't have enough information to complete your request.");
							out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n"); 
							out.println(ServletUtilities.getFooter());
					}	

			out.close();
			myShepherd.closeDBTransaction();
    	}
}
	
	
