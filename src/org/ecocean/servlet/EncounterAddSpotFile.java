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
import java.lang.SecurityException;

public class EncounterAddSpotFile extends HttpServlet {
	

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
		boolean removedJPEG=false, locked=false;
		String fileName="None", encounterNumber="None";
		String side="left";
			
	
				    	try {
				    		
      						MultipartParser mp = new MultipartParser(request, 10*1024*1024); // 2MB
      						Part part;
      						while ((part = mp.readNextPart()) != null) {
        						String name = part.getName();
        						if (part.isParam()) {
        							
        							
          							// it's a parameter part
          							ParamPart paramPart = (ParamPart) part;
          							String value = paramPart.getStringValue();
          				
          				
          							//determine which variable to assign the param to
          							if (name.equals("number")) {encounterNumber=value;}
          							if (name.equals("rightSide")) {
          								if(value.equals("true")) {
          									side="right";
          								}
          								}
        							
        						}
        							
        							
									if (part.isFile()) {
											FilePart filePart = (FilePart) part;
          									fileName = ServletUtilities.cleanFileName(filePart.getFileName());
          									if (fileName != null) {
            									File thisSharkDir=new File(getServletContext().getRealPath(("/"+CommonConfiguration.getImageDirectory()+"/"+encounterNumber)));

            									
            									//eliminate the previous JPG version of this file if it existed      										//eliminate the previous JPG if it existed
												try{
													
													String sideAddition="";
													if(side.equals("right")) {
														sideAddition="Right";
														fileName="RIGHT"+fileName;
													}
													else{
														fileName="LEFT"+fileName;
													}
													
													File jpegVersion=new File(thisSharkDir, ("extract"+sideAddition+encounterNumber+".jpg"));
													if(jpegVersion.exists()) {removedJPEG=jpegVersion.delete();}
													
												} catch(SecurityException thisE){thisE.printStackTrace();System.out.println("Error attempting to delete the old JPEG version of a submitted spot data image!!!!");removedJPEG=false;}
            									
            									long file_size = filePart.writeTo(
            										new File(thisSharkDir, fileName)
            										);
            
					
										}
									}
								}
								

								
								
								System.out.println(encounterNumber);
								System.out.println(fileName);
								myShepherd.beginDBTransaction();
								if (myShepherd.isEncounter(encounterNumber)) {
									Encounter add2shark=myShepherd.getEncounter(encounterNumber);
									try{
										if (side.equals("right")) {
											add2shark.setRightSpotImageFileName(fileName);
											add2shark.hasRightSpotImage=true;
										}
										else {
											add2shark.setSpotImageFileName(fileName);
											add2shark.hasSpotImage=true;
										}
					
										String user="Unknown User";
										if(request.getRemoteUser()!=null){
											user=request.getRemoteUser();
										}
										add2shark.addComments("<p><em>"+user+" on "+(new java.util.Date()).toString()+"</em><br>"+"Submitted new "+side+"-side spot data graphic.</p>");
									
									} catch(Exception le){
										locked=true;
										myShepherd.rollbackDBTransaction();
										le.printStackTrace();
									}
									
									
									
									
									if(!locked){
												myShepherd.commitDBTransaction();
												myShepherd.closeDBTransaction();
												String sideAddition="";
												if(side.equals("right")) {sideAddition="&rightSide=true";}
												out.println(ServletUtilities.getHeader());
												out.println("<strong>Step 2 Confirmed:</strong> I have successfully uploaded your "+side+"-side spot data image file.");
												out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+encounterNumber+"\">Return to encounter "+encounterNumber+"</a></p>\n");
												out.println(ServletUtilities.getFooter());
									} else{
												out.println(ServletUtilities.getHeader());
												out.println("<strong>Step 2 Failed:</strong> This encounter is currently locked and modified by another user. Please try to resubmit your spot data and add this image again in a few seconds.");
												
												out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+encounterNumber+"\">Return to encounter "+encounterNumber+"</a></p>\n");
												out.println(ServletUtilities.getFooter());	
									}
								}
								else {
									myShepherd.rollbackDBTransaction();
									myShepherd.closeDBTransaction();
									out.println(ServletUtilities.getHeader());
									out.println("<strong>Error:</strong> I was unable to upload your file. I cannot find the encounter that you intended it for in the database.");
									out.println(ServletUtilities.getFooter());
								}
							} catch (IOException lEx) {
      							lEx.printStackTrace();
      							out.println(ServletUtilities.getHeader());
								out.println("<strong>Error:</strong> I was unable to upload your file.");
								out.println(ServletUtilities.getFooter());
								myShepherd.rollbackDBTransaction();
								myShepherd.closeDBTransaction();
    							}
    							out.close();
    						}

		
		
	

		}
	
	
