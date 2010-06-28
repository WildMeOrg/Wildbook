package org.ecocean.servlet;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.Vector;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.StringTokenizer;
import org.ecocean.*;


public class IndividualCreate extends HttpServlet {
	
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
		

		//create a new MarkedIndividual from an encounter
	
			if ((request.getParameter("individual")!=null)&&(request.getParameter("number")!=null)&&(!request.getParameter("individual").equals(""))&&(!request.getParameter("individual").equals(" "))) {
				myShepherd.beginDBTransaction();
				Encounter enc2make=myShepherd.getEncounter(request.getParameter("number"));
				setDateLastModified(enc2make);
				
				String belongsTo=enc2make.isAssignedToMarkedIndividual();
				String submitter=enc2make.getSubmitterEmail();
				String photographer=enc2make.getPhotographerEmail();
				String informers=enc2make.getInformOthers();
				
				if (!(myShepherd.isMarkedIndividual(request.getParameter("individual")))) {
				
				
					if ((belongsTo.equals("Unassigned"))&&(request.getParameter("individual")!=null)) {
						try{
							MarkedIndividual newShark=new MarkedIndividual(request.getParameter("individual"), enc2make);
							enc2make.assignToMarkedIndividual(request.getParameter("individual"));
							enc2make.setMatchedBy("Unmatched first encounter");
							newShark.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>"+"Created "+request.getParameter("individual")+".</p>");
							newShark.setDateTimeCreated(ServletUtilities.getDate());
							myShepherd.addMarkedIndividual(newShark);
							enc2make.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>"+"Added to newly marked individual "+request.getParameter("individual")+".</p>");
						}
						catch(Exception le) {
							locked=true;
							le.printStackTrace();
							myShepherd.rollbackDBTransaction();
							
						}
						
						if(!locked){
							myShepherd.commitDBTransaction();
							if(request.getParameter("noemail")==null) {
								//send the e-mail
								Vector e_images=new Vector();
								String emailUpdate="\nNewly marked: "+request.getParameter("individual")+"\nhttp://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("individual")+"\n\nEncounter: "+request.getParameter("number")+"\nhttp://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\n";
								ThreadPoolExecutor es=MailThreadExecutorService.getExecutorService();
								
								//notify the admins
					      es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), CommonConfiguration.getNewSubmissionEmail(), ("Encounter update: "+request.getParameter("number")), ServletUtilities.getText("createdMarkedIndividual.txt")+emailUpdate, e_images));
								
					      if(submitter.indexOf(",")!=-1){
					        StringTokenizer str=new StringTokenizer(submitter, ",");
					        while(str.hasMoreTokens()){
					          String token=str.nextToken().trim();
					          if(!token.equals("")){
					            es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), token, ("Encounter update: "+request.getParameter("number")), ServletUtilities.getText("createdMarkedIndividual.txt")+emailUpdate, e_images));
			              }
					        }       
					      }
								else{
								  es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), submitter, ("Encounter update: "+request.getParameter("number")), ServletUtilities.getText("createdMarkedIndividual.txt")+emailUpdate, e_images));
								}
								
                if(photographer.indexOf(",")!=-1){
                  StringTokenizer str=new StringTokenizer(photographer, ",");
                  while(str.hasMoreTokens()){
                    String token=str.nextToken().trim();
                    if(!token.equals("")){
                      es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), token, ("Encounter update: "+request.getParameter("number")), ServletUtilities.getText("createdMarkedIndividual.txt")+emailUpdate, e_images));
                    }
                  }       
                }
                else{
                  es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), photographer, ("Encounter update: "+request.getParameter("number")), ServletUtilities.getText("createdMarkedIndividual.txt")+emailUpdate, e_images));
                }
                
                
                
                
								if ((informers!=null)&&(!informers.equals(""))) {
									
									if(informers.indexOf(",")!=-1){
	                  StringTokenizer str=new StringTokenizer(informers, ",");
	                  while(str.hasMoreTokens()){
	                    String token=str.nextToken().trim();
	                    if(!token.equals("")){
	                      es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), token, ("Encounter update: "+request.getParameter("number")), ServletUtilities.getText("createdMarkedIndividual.txt")+emailUpdate, e_images));
	                    }
	                  }       
	                }
	                else{
	                  es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), informers, ("Encounter update: "+request.getParameter("number")), ServletUtilities.getText("createdMarkedIndividual.txt")+emailUpdate, e_images));
	                }
									
									
									
								}
								
								
								
								String rssTitle="New marked individual: "+request.getParameter("individual");
								String rssLink="http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("individual");
								String rssDescription=request.getParameter("individual")+" has been added.";
								File rssFile=new File(getServletContext().getRealPath(("/rss.xml")));

								ServletUtilities.addRSSEntry(rssTitle, rssLink, rssDescription, rssFile);
								File atomFile=new File(getServletContext().getRealPath(("/atom.xml")));

								ServletUtilities.addATOMEntry(rssTitle, rssLink, rssDescription,atomFile);
								
							}
							//set up the directory for this individual
							File thisSharkDir=new File(getServletContext().getRealPath(("/"+CommonConfiguration.getMarkedIndividualDirectory()+"/"+request.getParameter("individual"))));

							
							if (!(thisSharkDir.exists())) {thisSharkDir.mkdir();};

							//output success statement
							out.println(ServletUtilities.getHeader());
							out.println("<strong>Success:</strong> Encounter #"+request.getParameter("number")+" was successfully used to create <strong>"+request.getParameter("individual")+"</strong>.");
							out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
							out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("individual")+"\">View <strong>"+request.getParameter("individual")+"</strong></a></p>\n");
							out.println(ServletUtilities.getFooter());
							String message="Encounter #"+request.getParameter("number")+" was identified as a new individual. The new individual has been named "+request.getParameter("individual")+".";
							if(request.getParameter("noemail")==null) {
								ServletUtilities.informInterestedParties(request.getParameter("number"), message);
							}
					} 
					else {
						out.println("<strong>Failure:</strong> Encounter #"+request.getParameter("number")+" was NOT used to create a new individual. This encounter is currently being modified by another user. Please go back and try to create the new individual again in a few seconds.");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("individual")+"\">View <strong>"+request.getParameter("individual")+"</strong></a></p>\n");
						out.println(ServletUtilities.getFooter());
						
					}
					
					
				}
				else {
							
						myShepherd.rollbackDBTransaction();
					
				}
					
				}
				else if ((myShepherd.isMarkedIndividual(request.getParameter("individual")))) {
					myShepherd.rollbackDBTransaction();
					out.println(ServletUtilities.getHeader());
					out.println("<strong>Error:</strong> A marked individual by this name already exists in the database. Select a different name and try again.");
					out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
					out.println(ServletUtilities.getFooter());
					
				}
				else {
					myShepherd.rollbackDBTransaction();
					out.println(ServletUtilities.getHeader());
					out.println("<strong>Error:</strong> You cannot make a new marked individual from this encounter because it is already assigned to another marked individual. Remove it from its previous individual if you want to re-assign it elsewhere.");
					out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
					out.println(ServletUtilities.getFooter());
				}
				
				
				

				
				}
				

			else {
				out.println(ServletUtilities.getHeader());
				out.println("<strong>Error:</strong> I didn't receive enough data to create a marked individual from this encounter.");
				out.println(ServletUtilities.getFooter());
				}	
						
			
	
			out.close();
			myShepherd.closeDBTransaction();
    	}
}
	
	
