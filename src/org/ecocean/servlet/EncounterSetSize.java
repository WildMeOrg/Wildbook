package org.ecocean.servlet;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import org.ecocean.*;


public class EncounterSetSize extends HttpServlet {

	
	public void init(ServletConfig config) throws ServletException {
    	super.init(config);
   }

	private void setDateLastModified(Encounter enc){

		String strOutputDateTime = ServletUtilities.getDate();
		enc.setDWCDateLastModified(strOutputDateTime);
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
		System.out.println("Action is: "+action);
		if(action!=null){
		


			if (action.equals("setEncounterSize")) {
				if ((request.getParameter("number")!=null)&&(request.getParameter("lengthField")!=null)&&(request.getParameter("lengthUnits")!=null)&&(request.getParameter("guessList")!=null)) {
					myShepherd.beginDBTransaction();
					Encounter changeMe=myShepherd.getEncounter(request.getParameter("number"));
					setDateLastModified(changeMe);
					
					double oldSize=0;
					String oldUnits="";
					String oldGuess="";
					boolean okNumberFormat=true;
					try{
					
						oldSize=changeMe.getSize();
						oldUnits=changeMe.getMeasureUnits();
						oldGuess=changeMe.getSizeGuess();
						changeMe.setMeasureUnits(request.getParameter("lengthUnits"));
						changeMe.setSizeGuess(request.getParameter("guessList"));
						
						//check for appropriate number format for reported size

						Double inputSize=new Double(request.getParameter("lengthField"));
						changeMe.setSize(inputSize.doubleValue());
						
						changeMe.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>Changed encounter size from "+oldSize+" "+oldUnits+"("+oldGuess+")"+" to "+request.getParameter("lengthField")+" "+request.getParameter("lengthUnits")+"("+request.getParameter("guessList")+").</p>");
					}
					catch(NumberFormatException nfe){
						System.out.println("User tried to enter improper number format when editing encounter length.");
						okNumberFormat=false;
						nfe.printStackTrace();
						myShepherd.rollbackDBTransaction();
					}
					catch(Exception le){
						System.out.println("Hit locked exception on action: "+action);
						locked=true;
						le.printStackTrace();
						myShepherd.rollbackDBTransaction();
					}

					
					
					if(!locked&&okNumberFormat){
						myShepherd.commitDBTransaction(action);
						out.println(ServletUtilities.getHeader());
						out.println("<strong>Success:</strong> Encounter size has been updated from "+oldSize+" "+oldUnits+"("+oldGuess+")"+" to "+request.getParameter("lengthField")+" "+request.getParameter("lengthUnits")+"("+request.getParameter("guessList")+").");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
						out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
      					out.println("<p><a href=\"allIndividuals.jsp\">View all sharks</a></font></p>");
      					out.println(ServletUtilities.getFooter());
						String message="Encounter #"+request.getParameter("number")+" size has been updated from "+oldSize+" "+oldUnits+"("+oldGuess+")"+" to "+request.getParameter("lengthField")+" "+request.getParameter("lengthUnits")+"("+request.getParameter("guessList")+").";
						ServletUtilities.informInterestedParties(request.getParameter("number"), message);
					} 
					else if(!okNumberFormat) {
						out.println(ServletUtilities.getHeader());
						out.println("<strong>Failure:</strong> Encounter size was NOT updated because I did not understand the value that you entered. The value must be zero or greater. A value of zero indicates an unknown length.");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
						out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
      					out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
      					//out.println("<p><a href=\"encounters/allEncounters.jsp?rejects=true\">View all rejected encounters</a></font></p>");
						out.println(ServletUtilities.getFooter());
					}
					else {
						out.println(ServletUtilities.getHeader());
						out.println("<strong>Failure:</strong> Encounter size was NOT updated because another user is currently modifying the record for this encounter.");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
						out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
      					out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
      					//out.println("<p><a href=\"encounters/allEncounters.jsp?rejects=true\">View all rejected encounters</a></font></p>");
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