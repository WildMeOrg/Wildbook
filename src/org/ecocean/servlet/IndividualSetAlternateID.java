package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import org.ecocean.*;


//Set alternateID for the individual
public class IndividualSetAlternateID extends HttpServlet {
	
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
		sharky=request.getParameter("individual");
		String alternateID="";
		myShepherd.beginDBTransaction();
		if (myShepherd.isMarkedIndividual(sharky)) {
			MarkedIndividual myShark=myShepherd.getMarkedIndividual(sharky);
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
				out.println("<strong>Success!</strong> I have successfully changed the alternate ID for individual "+sharky+" to "+alternateID+".</p>");
							
				out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+sharky+"\">Return to "+sharky+"</a></p>\n");
				out.println(ServletUtilities.getFooter());
				String message="The alternate ID for "+sharky+" was set to "+alternateID+".";									
			}
			else{
										
				out.println(ServletUtilities.getHeader());
				out.println("<strong>Failure!</strong> This individual is currently being modified by another user. Please wait a few seconds before trying to modify this individual again.");
										
				out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+sharky+"\">Return to "+sharky+"</a></p>\n");
				out.println(ServletUtilities.getFooter());
										
			}
			}
			else {
									myShepherd.rollbackDBTransaction();
								out.println(ServletUtilities.getHeader());
								out.println("<strong>Error:</strong> I was unable to set the individual alternate ID. I cannot find the individual that you intended it for in the database.");
								out.println(ServletUtilities.getFooter());
									
			}
			out.close();
			myShepherd.closeDBTransaction();
    	}
}
	
	
