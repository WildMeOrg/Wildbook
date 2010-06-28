package org.ecocean.servlet;

import javax.servlet.*;
import javax.servlet.http.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.ThreadPoolExecutor;

import org.ecocean.*;


public class IndividualAddEncounter extends HttpServlet {

	
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

		//add encounter to a MarkedIndividual

			if ((request.getParameter("number")!=null)&&(request.getParameter("individual")!=null)&&(request.getParameter("matchType")!=null)) {
				
				String altID="";
				myShepherd.beginDBTransaction();
				Encounter enc2add=myShepherd.getEncounter(request.getParameter("number"));
				setDateLastModified(enc2add);
				String tempName=enc2add.isAssignedToMarkedIndividual();
				if ((tempName.equals("Unassigned"))&&(myShepherd.isMarkedIndividual(request.getParameter("individual")))) {
					try {
						
						
						boolean sexMismatch=false;
						
						//myShepherd.beginDBTransaction();
						MarkedIndividual addToMe=myShepherd.getMarkedIndividual(request.getParameter("individual"));
						if((addToMe.getAlternateID()!=null)&&(!addToMe.getAlternateID().equals(""))){altID=" (Alternate ID: "+addToMe.getAlternateID()+")";}
						try{
							if(!addToMe.getEncounters().contains(enc2add)) {
								addToMe.addEncounter(enc2add);
							}
							enc2add.setMatchedBy(request.getParameter("matchType"));
							enc2add.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>"+"Added to "+request.getParameter("individual")+".</p>");
							addToMe.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>"+"Added encounter "+request.getParameter("number")+".</p>");
							if (!(addToMe.getSex().equals(enc2add.getSex()))) {
								if (addToMe.getSex().equals("Unknown")) {addToMe.setSex(enc2add.getSex());}
								else if(((addToMe.getSex().equals("Male"))&(enc2add.getSex().equals("Female")))||((addToMe.getSex().equals("Female"))&(enc2add.getSex().equals("Male")))) {
									sexMismatch=true;
									}
								}
						} catch(Exception le){
							System.out.println("Hit locked exception on action: "+action);
							le.printStackTrace();
							locked=true;
							myShepherd.rollbackDBTransaction();
							
						}
						
						
						
						
						if(!locked){
							
							myShepherd.commitDBTransaction(action);
							Vector e_images=new Vector();
							
							
							//let's get ready for emailing
							ThreadPoolExecutor es=MailThreadExecutorService.getExecutorService();
              
							
							
							myShepherd.beginDBTransaction();
						
							String emailUpdate="\nPreviously identified record: "+request.getParameter("individual");
							emailUpdate=emailUpdate+altID;
							emailUpdate=emailUpdate+"\nhttp://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("individual")+"\n\nNew encounter: "+request.getParameter("number")+"\nhttp://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\n";
							int numEncounters=addToMe.totalEncounters();
			
							//inform all encounter submitters for this Marked Individual about the modification to their animal
							
							if(request.getParameter("noemail")==null) {
							
								
								//notify the administrators
								NotificationMailer mailer=new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), CommonConfiguration.getAutoEmailAddress(), ("Encounter update sent to submitters: "+request.getParameter("number")), ServletUtilities.getText("add2MarkedIndividual.txt")+emailUpdate, e_images);
								
								
								StringBuffer allSubs=new StringBuffer();
								
								//notify other submitters
								for(int l=0;l<numEncounters;l++) {
									Encounter tempEnc=addToMe.getEncounter(l);
									
									
									if (!(tempEnc.getSubmitterEmail().equals(enc2add.getSubmitterEmail()))&&(allSubs.indexOf(tempEnc.getSubmitterEmail())==-1)) {
										
									  String submitter=tempEnc.getSubmitterEmail();
		                if(submitter.indexOf(",")!=-1){
		                  StringTokenizer str=new StringTokenizer(submitter, ",");
		                  while(str.hasMoreTokens()){
		                    String token=str.nextToken().trim();
		                    if(!token.equals("")){
		                      es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), token, ("Encounter update: "+request.getParameter("number")), ServletUtilities.getText("markedIndividualUpdate.txt")+emailUpdate, e_images));
		                      allSubs.append(token);
		                    }
		                  }       
		                }
		                else{
		                  es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), submitter, ("Encounter update: "+request.getParameter("number")), ServletUtilities.getText("markedIndividualUpdate.txt")+emailUpdate, e_images));
		                  allSubs.append(submitter);
		                }

									  
									}
									if ((tempEnc.getPhotographerEmail()!=null)&&(!tempEnc.getPhotographerEmail().equals(""))&&(!tempEnc.getPhotographerEmail().equals(enc2add.getPhotographerEmail()))&&(allSubs.indexOf(tempEnc.getPhotographerEmail())==-1)) {
								
                    String submitter=tempEnc.getPhotographerEmail();
                    if(submitter.indexOf(",")!=-1){
                      StringTokenizer str=new StringTokenizer(submitter, ",");
                      while(str.hasMoreTokens()){
                        String token=str.nextToken().trim();
                        if(!token.equals("")){
                          es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), token, ("Encounter update: "+request.getParameter("number")), ServletUtilities.getText("markedIndividualUpdate.txt")+emailUpdate, e_images));
                          allSubs.append(token);
                        }
                      }       
                    }
                    else{
                      es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), submitter, ("Encounter update: "+request.getParameter("number")), ServletUtilities.getText("markedIndividualUpdate.txt")+emailUpdate, e_images));
                      allSubs.append(submitter);
                    }
										
									}
									
									
									if ((tempEnc.getInformOthers()!=null)&&(!tempEnc.getInformOthers().equals(""))&&(allSubs.indexOf(tempEnc.getInformOthers())==-1)) {
										
                    String submitter=tempEnc.getInformOthers();
                    if(submitter.indexOf(",")!=-1){
                      StringTokenizer str=new StringTokenizer(submitter, ",");
                      while(str.hasMoreTokens()){
                        String token=str.nextToken().trim();
                        if(!token.equals("")){
                          es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), token, ("Encounter update: "+request.getParameter("number")), ServletUtilities.getText("markedIndividualUpdate.txt")+emailUpdate, e_images));
                          allSubs.append(token);
                        }
                      }       
                    }
                    else{
                      es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), submitter, ("Encounter update: "+request.getParameter("number")), ServletUtilities.getText("markedIndividualUpdate.txt")+emailUpdate, e_images));
                      allSubs.append(submitter);
                    }
									}
									
									

								}
								
								//notify adopters
								ArrayList adopters = myShepherd.getAdopterEmailsForMarkedIndividual(request.getParameter("individual"));
								for(int t=0;t<adopters.size();t++) {
									String adEmail=(String)adopters.get(t);
									if ((allSubs.indexOf(adEmail)==-1)) {
									  es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), adEmail, ("Sighting update: "+request.getParameter("individual")), ServletUtilities.getText("adopterUpdate.txt")+emailUpdate, e_images));
									  allSubs.append(adEmail); 
									}
								}
								
								String rssTitle=request.getParameter("individual")+" Resight";
								String rssLink="http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number");
								String rssDescription=request.getParameter("individual")+" was resighted on "+enc2add.getShortDate()+".";
								File rssFile=new File(getServletContext().getRealPath(("/rss.xml")));

								ServletUtilities.addRSSEntry(rssTitle, rssLink, rssDescription,rssFile);
								File atomFile=new File(getServletContext().getRealPath(("/atom.xml")));

								ServletUtilities.addATOMEntry(rssTitle, rssLink, rssDescription, atomFile);
							}
				


							myShepherd.rollbackDBTransaction();
							
							
						//print successful result notice	
						out.println(ServletUtilities.getHeader());
						out.println("<strong>Success:</strong> Encounter "+request.getParameter("number")+" was successfully added to "+request.getParameter("individual")+".");
						if (sexMismatch) {
							out.println("<p><strong>Warning! There is conflict between the designated sex of the new encounter and the designated sex in previous records. You should resolve this conflict for consistency.</strong></p>");
							}
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("individual")+"\">View individual "+request.getParameter("individual")+"</a></p>\n");
						out.println(ServletUtilities.getFooter());
						String message="Encounter #"+request.getParameter("number")+" was added to "+request.getParameter("individual")+".";
						
						if(request.getParameter("noemail")==null) {
							ServletUtilities.informInterestedParties(request.getParameter("number"), message);
							ServletUtilities.informInterestedIndividualParties(request.getParameter("individual"), message);
						}
					}
						
					//if lock exception thrown
					else {
							out.println(ServletUtilities.getHeader());
							out.println("<strong>Failure:</strong> Encounter #"+request.getParameter("number")+" was NOT added to "+request.getParameter("individual")+". Another user is currently modifying this record in the database. Please try to add the encounter again after a few seconds.");
							out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
							out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("individual")+"\">View "+request.getParameter("individual")+"</a></p>\n");
							out.println(ServletUtilities.getFooter());
							
					}
						
						

						
					}
					catch (Exception e) {
						
						out.println(ServletUtilities.getHeader());
						out.println("<strong>Error:</strong> No such record exists in the database.");
						out.println(ServletUtilities.getFooter());
						myShepherd.rollbackDBTransaction();
						e.printStackTrace();
						//myShepherd.closeDBTransaction();
						}
					}
				else {
					out.println(ServletUtilities.getHeader());
					out.println("<strong>Error:</strong> You can't add this encounter to a marked individual when it's already assigned to another one, or you may be trying to add this encounter to a nonexistent individual.");
					out.println(ServletUtilities.getFooter());
					myShepherd.rollbackDBTransaction();
					//myShepherd.closeDBTransaction();
					}
				
				
			
				}
			else {
				out.println(ServletUtilities.getHeader());
				out.println("<strong>Error:</strong> I didn't receive enough data to add this encounter to a marked individual.");
				out.println(ServletUtilities.getFooter());
				}
	
		

	
			out.close();
			myShepherd.closeDBTransaction();
		}
	
}