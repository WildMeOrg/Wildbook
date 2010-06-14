package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import org.ecocean.*;


//Set alternateID for this encounter/sighting
public class EncounterSetAlternateID extends HttpServlet {
	
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
		boolean locked=false;
		
		String sharky="None";
				
	
				    
		sharky=request.getParameter("encounter");
		String alternateID="";
		myShepherd.beginDBTransaction();
		if ((myShepherd.isEncounter(sharky))&&(request.getParameter("alternateid")!=null)) {
			Encounter myShark=myShepherd.getEncounter(sharky);
			alternateID=request.getParameter("alternateid");										
			try{
				myShark.setAlternateID(alternateID);
				myShark.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>"+"Set alternate ID: "+alternateID+".");
        
			}
			catch(Exception le){
				locked=true;
				myShepherd.rollbackDBTransaction();
				myShepherd.closeDBTransaction();
			}
									
			if(!locked){
				myShepherd.commitDBTransaction();
				myShepherd.closeDBTransaction();
				out.println(ServletUtilities.getHeader());
				out.println("<strong>Success!</strong> I have successfully changed the alternate ID for encounter "+sharky+" to "+alternateID+".</p>");
							
				out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+sharky+"\">Return to encounter "+sharky+"</a></p>\n");
				out.println(ServletUtilities.getFooter());
				String message="The alternate ID for encounter "+sharky+" was set to "+alternateID+".";									
			}
			else{
										
				out.println(ServletUtilities.getHeader());
				out.println("<strong>Failure!</strong> This encounter is currently being modified by another user. Please wait a few seconds before trying to modify this encounter again.");
										
				out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+sharky+"\">Return to encounter "+sharky+"</a></p>\n");
				out.println(ServletUtilities.getFooter());
										
			}
									}
								else {
									myShepherd.rollbackDBTransaction();
								out.println(ServletUtilities.getHeader());
								out.println("<strong>Error:</strong> I was unable to set the alternate ID. I cannot find the encounter that you intended it for in the database.");
								out.println(ServletUtilities.getFooter());
									
									}
								out.close();
								myShepherd.closeDBTransaction();
    						}

		
		
	

		}
	
	
