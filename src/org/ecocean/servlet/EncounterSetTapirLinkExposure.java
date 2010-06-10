package org.ecocean.servlet;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import org.ecocean.*;


public class EncounterSetTapirLinkExposure extends HttpServlet {

	
	public void init(ServletConfig config) throws ServletException {
    	super.init(config);
   }

	
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
		doPost(request, response);
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
		Shepherd myShepherd=new Shepherd();
		//set up for response
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		boolean locked=false, isOwner=true;
		boolean isAssigned=false;

		/**
		if(request.getParameter("number")!=null){
			myShepherd.beginDBTransaction();
			if(myShepherd.isEncounter(request.getParameter("number"))) {
				Encounter verifyMyOwner=myShepherd.getEncounter(request.getParameter("number"));
				String locCode=verifyMyOwner.getLocationCode();
				
				//check if the encounter is assigned
				if((verifyMyOwner.getSubmitterID()!=null)&&(request.getRemoteUser()!=null)&&(verifyMyOwner.getSubmitterID().equals(request.getRemoteUser()))){
					isAssigned=true;
				}
				
				//if the encounter is assigned to this user, they have permissions for it...or if they're a manager
				if((request.isUserInRole("admin"))||(isAssigned)){
					isOwner=true;
				}
				//if they have general location code permissions for the encounter's location code
				else if(request.isUserInRole(locCode)){isOwner=true;}
			}
			myShepherd.rollbackDBTransaction();	
		}
		*/
		
		String action=request.getParameter("action");
		//System.out.println("Action is: "+action);
		if(action!=null){
		
			if ((action.equals("tapirLinkExpose"))) {
				if (!(request.getParameter("number")==null)) {
					myShepherd.beginDBTransaction();
					Encounter newenc=myShepherd.getEncounter(request.getParameter("number"));
					

						try{
						
							if(newenc.getOKExposeViaTapirLink()){
								newenc.setOKExposeViaTapirLink(false);
							}
							else{newenc.setOKExposeViaTapirLink(true);}

							//newenc.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>"+"Approved this encounter for TapirLink exposure.");
						}catch(Exception le){
							System.out.println("Hit locked exception on action: "+action);
							locked=true;
							le.printStackTrace();
							myShepherd.rollbackDBTransaction();
							//myShepherd.closeDBTransaction();
						}
						
						
						
						if(!locked){
							myShepherd.commitDBTransaction(action);
							out.println(ServletUtilities.getHeader());
							out.println("<strong>Success:</strong> I have changed encounter "+request.getParameter("number")+" TapirLink exposure status.");
							out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a>.</p>\n");
							out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
      						out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
										
							out.println(ServletUtilities.getFooter());
						} 
						else {
							out.println(ServletUtilities.getHeader());
							out.println("<strong>Failure:</strong> I have NOT changed encounter "+request.getParameter("number")+" TapirLink status. This encounter is currently being modified by another user, or an unknown error occurred.");
							out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
							out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
      						out.println("<p><a href=\"allIndividuals.jsp\">View all individual</a></font></p>");	
							out.println(ServletUtilities.getFooter());	
							
							
						}
			
							}
					
						
					
					else {
							out.println(ServletUtilities.getHeader());
							out.println("<strong>Error:</strong> I don't know which new encounter you're trying to approve.");
							out.println(ServletUtilities.getFooter());	
						
					}
				
				}

		else{
			out.println(ServletUtilities.getHeader());
			out.println("<p>I didn't understand your command, or you are not authorized for this action.</p>");
			out.println("<p>Please try again or <a href=\"welcome.jsp\">login here</a>.");
			out.println(ServletUtilities.getFooter());
			}
		
	} 
	else {
			out.println(ServletUtilities.getHeader());
			out.println("<p>I did not receive enough data to process your command. No action was indicated to me.</p>");
			out.println("<p>Please try again or <a href=\"welcome.jsp\">login here</a>.");
			out.println(ServletUtilities.getFooter());
		}
	
			out.close();
			myShepherd.closeDBTransaction();
		}
	
}