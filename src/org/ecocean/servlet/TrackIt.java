package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;
import org.ecocean.*;
//import javax.jdo.*;
//import com.poet.jdo.*;
import com.oreilly.servlet.multipart.*;
import java.lang.StringBuffer;


public class TrackIt extends HttpServlet {
	

	
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
		
		String email="None", encounterNumber="None", shark="None";

								email=request.getParameter("email");
								encounterNumber=request.getParameter("number");
								myShepherd.beginDBTransaction();
								if ((request.getParameter("number")!=null)&&(myShepherd.isEncounter(request.getParameter("number")))&&(email!=null)&&(!email.equals(""))&&(email.indexOf("@")!=-1)) {
									Encounter enc=myShepherd.getEncounter(encounterNumber);
									
									
									//int positionInList=0;
									try{

										Vector interested=enc.getInterestedResearchers();
										interested.add(email);
									
									}
									catch(Exception le){
										locked=true;
										myShepherd.rollbackDBTransaction();
									}
									
									if(!locked){
												myShepherd.commitDBTransaction();
												out.println(ServletUtilities.getHeader());
												out.println("<strong>Success!</strong> I have successfully added the tracking of encounter#"+encounterNumber+" for e-mail address "+email+".");
							
												out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+encounterNumber+"\">Return to encounter "+encounterNumber+"</a></p>\n");
												out.println(ServletUtilities.getFooter());
												Vector e_images=new Vector();
												String message="This is a confirmation that e-mail tracking of data changes to encounter "+encounterNumber+" has now started. You should receive e-mail updates any time changes to this encounter are made.";
												NotificationMailer mailer=new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), email, ("Encounter data tracking started for encounter: "+encounterNumber), message, e_images);
												}
									else{
										
												out.println(ServletUtilities.getHeader());
												out.println("<strong>Failure!</strong> This encounter is currently being modified by another user, or the database is locked. Please wait a few seconds before trying to add this e-mail address for tracking again.");
												out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+encounterNumber+"\">Return to encounter "+encounterNumber+"</a></p>\n");
												out.println(ServletUtilities.getFooter());
												
										
										}
									}
									
								else if ((request.getParameter("individual")!=null)&&(myShepherd.isMarkedIndividual(request.getParameter("individual")))&&(email!=null)&&(!email.equals(""))&&(email.indexOf("@")!=-1)) {
									
									shark=request.getParameter("individual");
									MarkedIndividual sharkie=myShepherd.getMarkedIndividual(shark);
									
									//myShepherd.beginDBTransaction();
									//int positionInList=0;
									try{

										Vector interested=sharkie.getInterestedResearchers();
										interested.add(email);
									
									}
									catch(Exception le){
										locked=true;
										myShepherd.rollbackDBTransaction();
									}
									
									if(!locked){
												myShepherd.commitDBTransaction();
												out.println(ServletUtilities.getHeader());
												out.println("<strong>Success!</strong> I have successfully added the tracking of "+shark+" for e-mail address "+email+".");
							
												out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+shark+"\">Return to "+shark+"</a></p>\n");
												out.println(ServletUtilities.getFooter());
												Vector e_images=new Vector();
												String message="This is a confirmation that e-mail tracking of data changes to "+shark+" has now started. You should receive e-mail updates any time changes to this record are made.";
												NotificationMailer mailer=new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), email, ("Data tracking started for encounter: "+shark), message, e_images);
												}
									else{
										
												out.println(ServletUtilities.getHeader());
												out.println("<strong>Failure!</strong> This record is currently being modified by another user, or the database is locked. Please wait a few seconds before trying to add this e-mail address for tracking again.");
												out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+shark+"\">Return to "+shark+"</a></p>\n");
												out.println(ServletUtilities.getFooter());
												
										
										}
									
									
									
									}	
								else {
									myShepherd.rollbackDBTransaction();
								out.println(ServletUtilities.getHeader());
								out.println("<strong>Error:</strong> I was unable to add your e-mail address to the tracking list. I cannot find the record that you indicated in the database, or your e-mail address is invalid.");
								out.println(ServletUtilities.getFooter());
									
									}
								out.close();
								myShepherd.closeDBTransaction();
    						}

		
		
	

		}
	
	
