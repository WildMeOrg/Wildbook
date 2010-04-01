package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;
import org.ecocean.*;


public class DontTrack extends HttpServlet {
	

	
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
		
		myShepherd.beginDBTransaction();
		if ((request.getParameter("number")!=null)&&(myShepherd.isEncounter(request.getParameter("number")))&&(request.getParameter("email")!=null)) {
						email=request.getParameter("email");
						encounterNumber=request.getParameter("number");
						
						
						
						Encounter enc=myShepherd.getEncounter(encounterNumber);
									//int positionInList=0;
									try{
									
									
									Vector interested=enc.getInterestedResearchers();
									//int initNumberImages=interested.size();
									//remove copyrighted images to allow them to be reset
									
									for(int i=0;i<interested.size();i++){
										String thisEmail=(String)interested.get(i);
										if(thisEmail.equals(email)){
											interested.remove(i);
											i--;
											}
										}
									//enc.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>"+"Removed encounter image graphic: "+fileName+".</p>");
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
												out.println("<strong>Success!</strong> I have successfully stopped the tracking of encounter#"+encounterNumber+" for e-mail address "+email+".");
							
												out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+encounterNumber+"\">Go to encounter "+encounterNumber+"</a></p>\n");
												out.println(ServletUtilities.getFooter());
												Vector e_images=new Vector();
												String message="This is a confirmation that e-mail tracking of data changes to encounter "+encounterNumber+" has now been stopped.";
												NotificationMailer mailer=new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), email, ("Encounter data tracking stopped for encounter: "+encounterNumber), message, e_images);
												}
									else{
										
												out.println(ServletUtilities.getHeader());
												out.println("<strong>Failure!</strong> This encounter is currently being modified by another user, or the database is locked. Please wait a few seconds before trying to remove this e-mail address from tracking again.");
												out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+encounterNumber+"\">Return to encounter "+encounterNumber+"</a></p>\n");
												out.println(ServletUtilities.getFooter());
										
									}
									}
									
								//stop tracking a MarkedIndividual	
								else if ((request.getParameter("individual")!=null)&&(myShepherd.isMarkedIndividual(request.getParameter("individual")))&&(request.getParameter("email")!=null)) {
									
									email=request.getParameter("email");
									shark=request.getParameter("individual");
									MarkedIndividual sharkie=myShepherd.getMarkedIndividual(shark);
						
									//myShepherd.beginDBTransaction();
									
									try{
									
									
									Vector interested=sharkie.getInterestedResearchers();
									//int initNumberImages=interested.size();
									//remove copyrighted images to allow them to be reset
									
									for(int i=0;i<interested.size();i++){
										String thisEmail=(String)interested.get(i);
										if(thisEmail.equals(email)){
											interested.remove(i);
											i--;
											}
										}
									//enc.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>"+"Removed encounter image graphic: "+fileName+".</p>");
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
												out.println("<strong>Success!</strong> I have successfully stopped the tracking of "+shark+" for e-mail address "+email+".");
							
												out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+shark+"\">Go to "+shark+"</a></p>\n");
												out.println(ServletUtilities.getFooter());
												Vector e_images=new Vector();
												String message="This is a confirmation that e-mail tracking of data changes to "+shark+" has now been stopped.";
												NotificationMailer mailer=new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), email, ("Data tracking stopped for: "+shark), message, e_images);
												}
									else{
										
												out.println(ServletUtilities.getHeader());
												out.println("<strong>Failure!</strong> This record is currently being modified by another user, or the database is locked. Please wait a few seconds before trying to remove this e-mail address from tracking again.");
												out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+shark+"\">Return to "+shark+"</a></p>\n");
												out.println(ServletUtilities.getFooter());
										
									}
									
									
									}	
									
									
								else {
								myShepherd.rollbackDBTransaction();	
								myShepherd.closeDBTransaction();
								out.println(ServletUtilities.getHeader());
								out.println("<strong>Error:</strong> I was unable to remove your e-mail address from the tracking list. I cannot find the encounter or marked individual that you indicated in the database, or you did not provide a valid e-mail address.");
								out.println(ServletUtilities.getFooter());
									
									}
								out.close();
    						}

		
		
	

		}
	
	
