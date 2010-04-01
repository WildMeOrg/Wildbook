package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;
import org.ecocean.*;
import javax.jdo.*;
//import com.poet.jdo.*;
import com.oreilly.servlet.multipart.*;
import java.lang.StringBuffer;


public class EncounterSetMatchedBy extends HttpServlet {
	

	public void init(ServletConfig config) throws ServletException {
    	super.init(config);

    
  }

	
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {

		// Here we forward to the appropriate page using the request dispatcher

		//getServletContext().getRequestDispatcher("/Noget.html").forward(req, res);
		doPost(request, response);
	}
		
	
	
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
		
		//initialize shepherd
		Shepherd myShepherd=new Shepherd();
		
		//set up for response
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		boolean locked=false;
		
		//setup variables
		String encounterNumber="None";
		String matchedBy="Unknown", prevMatchedBy="";
		
		myShepherd.beginDBTransaction();
	
				    
								
								encounterNumber=request.getParameter("number");
								if(request.getParameter("matchedBy")!=null) matchedBy=request.getParameter("matchedBy");
								if ((myShepherd.isEncounter(encounterNumber))&&(request.getParameter("number")!=null)) {
									Encounter sharky=myShepherd.getEncounter(encounterNumber);
									
									
									
									try{
									

										if(sharky.getMatchedBy()!=null){prevMatchedBy=sharky.getMatchedBy();}

											sharky.setMatchedBy(matchedBy);
											sharky.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>"+"Changed matched by type from "+prevMatchedBy+" to "+matchedBy+".</p>");
									
										}
									catch(Exception le){
										locked=true;
										myShepherd.rollbackDBTransaction();
										
									}
									
									if(!locked){
												myShepherd.commitDBTransaction();
												//myShepherd.closeDBTransaction();
												out.println(ServletUtilities.getHeader());
												out.println("<strong>Success!</strong> I have successfully changed the matched by type for encounter "+encounterNumber+" from "+prevMatchedBy+" to "+matchedBy+".</p>");
							
												out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+encounterNumber+"\">Return to encounter "+encounterNumber+"</a></p>\n");
												out.println(ServletUtilities.getFooter());
												String message="The matched by type for encounter "+encounterNumber+" was changed from "+prevMatchedBy+" to "+matchedBy+".";
												ServletUtilities.informInterestedParties(encounterNumber, message);
												}
									else{
										
												out.println(ServletUtilities.getHeader());
												out.println("<strong>Failure!</strong> This encounter is currently being modified by another user. Please wait a few seconds before trying to remove this data file again.");
										
												out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+encounterNumber+"\">Return to encounter "+encounterNumber+"</a></p>\n");
												out.println(ServletUtilities.getFooter());
										
									}
									}
								else {
									
								out.println(ServletUtilities.getHeader());
								out.println("<strong>Error:</strong> I was unable to set the matched by type. I cannot find the marked individual that you intended it for in the database, or I wasn't sure what file you wanted to remove.");
								out.println(ServletUtilities.getFooter());
									
									}
								out.close();
								
								myShepherd.closeDBTransaction();
    						}
	
		

		
		
	

		}
	
	
