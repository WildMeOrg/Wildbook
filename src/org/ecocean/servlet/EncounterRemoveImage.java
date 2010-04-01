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


public class EncounterRemoveImage extends HttpServlet {

	
	
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
		//boolean assigned=false;
		String fileName="None", encounterNumber="None";
		int positionInList=-1;		
	
								//fileName=request.getParameter("filename").replaceAll("%20"," ");
								encounterNumber=request.getParameter("number");
								try{
									positionInList=(new Integer(request.getParameter("position"))).intValue();
									positionInList--;
								}
								catch (Exception e){
									
									System.out.println("encounterRemoveImage: "+request.getParameter("number")+" "+request.getParameter("position"));
								}
								myShepherd.beginDBTransaction();
								if ((myShepherd.isEncounter(encounterNumber))&&(positionInList>-1)) {
									Encounter enc=myShepherd.getEncounter(encounterNumber);
									if(enc.isAssignedToMarkedIndividual().equals("Unassigned")) {
									
									//positionInList=0;
									try{
									
									
										Vector additionalImageNames=enc.getAdditionalImageNames();
										fileName=(String)additionalImageNames.get(positionInList);
										int initNumberImages=additionalImageNames.size();
										//remove copyrighted images to allow them to be reset
									
										//for(int i=0;i<initNumberImages;i++){
										//	String thisImageName=(String)additionalImageNames.get(i);
										//	if((thisImageName.equals(fileName))&&(positionInList==0)){positionInList=i;}
										//}
										//positionInList++;
										for(int j=positionInList;j<(initNumberImages+1);j++){
											//remove copyrighted images
											try{
										
												//File cpyrght=new File(((new File(".")).getCanonicalPath()).replace('\\','/')+"/webapps/ROOT/encounters/"+encounterNumber+"/"+j+".jpg");
												File cpyrght=new File(getServletContext().getRealPath(("/encounters/"+encounterNumber+"/"+j+".jpg")));

												
												boolean successfulDelete=false;
												if(cpyrght.exists()){successfulDelete=cpyrght.delete();}
												if(!successfulDelete){System.out.println("Unsuccessful attempt to delete file: "+encounterNumber+"/"+fileName);}
											} catch(Exception e) {e.printStackTrace();}
										
										}
									
									
										enc.removeAdditionalImageName(fileName);
										enc.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>"+"Removed encounter image graphic: "+fileName+".</p>");
										Iterator keywords=myShepherd.getAllKeywords();
										String toRemove=encounterNumber+"/"+fileName;
										while(keywords.hasNext()) {
											Keyword word=(Keyword)keywords.next();
										
											if(word.isMemberOf(toRemove)){
											
												word.removeImageName(toRemove);
											}
										}
									
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
												out.println("<strong>Success!</strong> I have successfully removed the encounter image file. When returning to the encounter page, please make sure to refresh your browser to see the changes. Image changes will not be visible until you have done so.");
							
												out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+encounterNumber+"\">Return to encounter "+encounterNumber+"</a></p>\n");
												out.println(ServletUtilities.getFooter());
												String message="An image file named "+fileName+" has been removed from encounter#"+encounterNumber+".";
												ServletUtilities.informInterestedParties(encounterNumber, message);
												}
									else{
										
												out.println(ServletUtilities.getHeader());
												out.println("<strong>Failure!</strong> This encounter is currently being modified by another user. Please wait a few seconds before trying to remove this image again.");
										
												out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+encounterNumber+"\">Return to encounter "+encounterNumber+"</a></p>\n");
												out.println(ServletUtilities.getFooter());
										
									}
									}
									else {
										myShepherd.rollbackDBTransaction();
										myShepherd.closeDBTransaction();
										out.println(ServletUtilities.getHeader());
										out.println("<strong>Error:</strong> I was unable to remove your image file. For data protection, you must first remove the encounter from the marked individual it is assigned to.");
										out.println(ServletUtilities.getFooter());
									}
								}
								else {
									myShepherd.rollbackDBTransaction();
									myShepherd.closeDBTransaction();
								out.println(ServletUtilities.getHeader());
								out.println("<strong>Error:</strong> I was unable to remove your image file. I cannot find the encounter that you intended it for in the database.");
								out.println(ServletUtilities.getFooter());
									
									}
								out.close();
    						}

		
		
	

		}
	
	
