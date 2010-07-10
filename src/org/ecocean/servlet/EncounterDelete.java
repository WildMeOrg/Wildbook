package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.Iterator;
import java.util.Vector;

import org.ecocean.*;


public class EncounterDelete extends HttpServlet {
	
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
		
		/**
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
		}*/


			if (!(request.getParameter("number")==null)) {
				String message="Encounter #"+request.getParameter("number")+" was deleted from the database.";
				ServletUtilities.informInterestedParties(request.getParameter("number"), message);
				myShepherd.beginDBTransaction();
				Encounter enc2trash=myShepherd.getEncounter(request.getParameter("number"));
				setDateLastModified(enc2trash);
				
				//first unregister the images from the index
				
				Vector additionalImageNames=enc2trash.getAdditionalImageNames();
				int initNumberImages=additionalImageNames.size();
				for(int i=0;i<initNumberImages;i++){
							Iterator keywords=myShepherd.getAllKeywords();
							String fileName=(String)additionalImageNames.get(i);
							String toRemove=request.getParameter("number")+"/"+fileName;
							while(keywords.hasNext()) {
									Keyword word=(Keyword)keywords.next();
									if(word.isMemberOf(toRemove)){
										word.removeImageName(toRemove);
									}
								}
				}				
								
				if (enc2trash.isAssignedToMarkedIndividual().equals("Unassigned")) {
					
					try{
					
						Encounter backUpEnc=myShepherd.getEncounterDeepCopy(enc2trash.getEncounterNumber());
						
						String savedFilename=request.getParameter("number")+".dat";
						File thisEncounterDir=new File(getServletContext().getRealPath(("/encounters/"+request.getParameter("number"))));

						
						File serializedBackup=new File(thisEncounterDir,savedFilename);
						FileOutputStream fout = new FileOutputStream(serializedBackup);
  						ObjectOutputStream oos = new ObjectOutputStream(fout);
  						oos.writeObject(backUpEnc);
  						oos.close();

						//record who deleted this encounter
						enc2trash.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>"+"Deleted this encounter from the database.");
						myShepherd.commitDBTransaction();
						
						//now delete for good
						myShepherd.beginDBTransaction();
						myShepherd.throwAwayEncounter(enc2trash);
						
						
					}  catch(Exception edel) {
						locked=true;
						edel.printStackTrace();
						myShepherd.rollbackDBTransaction();
						
					}
					
					
					
					if(!locked){
						myShepherd.commitDBTransaction();
						out.println(ServletUtilities.getHeader());
						out.println("<strong>Success:</strong> I have removed encounter "+request.getParameter("number")+" from the database. If you have deleted this encounter in error, please contact the webmaster and reference encounter "+request.getParameter("number")+" to have it restored.");
						out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
						out.println("<p><a href=\"encounters/allEncountersUnapproved.jsp\">View all unapproved encounters</a></font></p>");
            
						out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
									
						out.println(ServletUtilities.getFooter());
						Vector e_images=new Vector();
						NotificationMailer mailer=new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), CommonConfiguration.getNewSubmissionEmail(), ("Removed encounter "+request.getParameter("number")), "Encounter "+request.getParameter("number")+" has been removed from the database by user "+request.getRemoteUser()+".", e_images);

					} else {
						out.println(ServletUtilities.getHeader());
						out.println("<strong>Failure:</strong> I have NOT removed encounter "+request.getParameter("number")+" from the database. This encounter is currently being modified by another user.");
						out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
  						out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
									
						out.println(ServletUtilities.getFooter());	
						
						
					}
					}
				else {
					myShepherd.commitDBTransaction();
					out.println(ServletUtilities.getHeader());
					out.println("Encounter# "+request.getParameter("number")+" is assigned to an individual and cannot be rejected until it has been removed from that individual.");
					out.println(ServletUtilities.getFooter());
					}
						}
				
					
				
				else {
						out.println(ServletUtilities.getHeader());
						out.println("<strong>Error:</strong> I don't know which encounter you're trying to remove.");
						out.println(ServletUtilities.getFooter());	
					
				}
			

			out.close();
			myShepherd.closeDBTransaction();
    	}
}
	
	
