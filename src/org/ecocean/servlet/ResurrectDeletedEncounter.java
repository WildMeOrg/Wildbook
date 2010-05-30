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


public class ResurrectDeletedEncounter extends HttpServlet {
	

	
	
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


		encounterNumber=request.getParameter("number");
		
		myShepherd.beginDBTransaction();

		if ((request.getParameter("number")!=null)&&(!myShepherd.isEncounter(encounterNumber))) {
				myShepherd.rollbackDBTransaction();
				//ok, let's get the encounter object back from the .dat file
				String datFilename=request.getParameter("number")+".dat";
				//File thisEncounterDat=new File(((new File(".")).getCanonicalPath()).replace('\\','/')+"/"+CommonConfiguration.getImageDirectory()+File.separator+request.getParameter("number")+File.separator+datFilename);			
				File thisEncounterDat=new File(getServletContext().getRealPath(("/"+CommonConfiguration.getImageDirectory()+"/"+request.getParameter("number")+"/"+datFilename)));

				
				if(thisEncounterDat.exists()) {
					
					try{
						FileInputStream f_in = new FileInputStream(thisEncounterDat);
						ObjectInputStream obj_in=new ObjectInputStream(f_in);
						
						Encounter restoreMe=(Encounter)obj_in.readObject();
						restoreMe.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>"+"Restored this encounter after accidental deletion.");
						String newnum=myShepherd.storeNewEncounter(restoreMe, (request.getParameter("number")));
						//thisEncounterDat.delete();
									
					}

					catch(Exception eres){
										locked=true;
										myShepherd.rollbackDBTransaction();
										myShepherd.closeDBTransaction();
										eres.printStackTrace();
					}
									
				
				if(!locked){
							
					out.println(ServletUtilities.getHeader());
					out.println("<strong>Success!</strong> I have successfully restored encounter "+request.getParameter("number")+" from accidental deletion.</p>");
							
					out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+encounterNumber+"\">Return to encounter "+encounterNumber+"</a></p>\n");
					out.println(ServletUtilities.getFooter());
					//String message="The matched by type for encounter "+encounterNumber+" was changed from "+prevMatchedBy+" to "+matchedBy+".";
					//informInterestedParties(encounterNumber, message);
				}
				else{
										
					out.println(ServletUtilities.getHeader());
					out.println("<strong>Failure!</strong> This encounter cannot be restored due to an unknown error. Please contact the webmaster.");
										
					//out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+encounterNumber+"\">Return to encounter "+encounterNumber+"</a></p>\n");
					out.println(ServletUtilities.getFooter());
										
					}
					
				} else {
					out.println(ServletUtilities.getHeader());
					out.println("<strong>Failure!</strong> I could not find the DAT file to restore this encounter from.");
										
					//out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+encounterNumber+"\">Return to encounter "+encounterNumber+"</a></p>\n");
					out.println(ServletUtilities.getFooter());
					
				}
					
				}
			else {
									
								out.println(ServletUtilities.getHeader());
								out.println("<strong>Error:</strong> I was unable to resurrect the encounter because I did not know which encounter you were referring to, or this encounter still exists in the database!");
								//out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("shark")+"\">Return to shark "+request.getParameter("shark")+"</a></p>\n");
								out.println(ServletUtilities.getFooter());
									
									}
								out.close();
    						}

		
		
	

		}
	
	
