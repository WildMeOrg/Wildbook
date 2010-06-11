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


public class IndividualAddFile extends HttpServlet {
	
	static SuperSpot tempSuperSpot;
	
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
		
		String fileName="None"; 
		String individualName="None";
				
	
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
          							if (name.equals("individual")) {individualName=value;}
        							
        						}
        							
        							
									if (part.isFile()) {
											FilePart filePart = (FilePart) part;
          									fileName = ServletUtilities.cleanFileName(filePart.getFileName());
          									if (fileName != null) {
          									  
          									  
          									  File individualsDir=new File(getServletContext().getRealPath(("/"+CommonConfiguration.getMarkedIndividualDirectory())));
          									  if(!individualsDir.exists()){individualsDir.mkdir();}
          									  
          									  
            									File thisSharkDir=new File(getServletContext().getRealPath(("/"+CommonConfiguration.getMarkedIndividualDirectory()+"/"+individualName)));
            									
            									
            									if (!(thisSharkDir.exists())) {thisSharkDir.mkdir();};
            									long file_size = filePart.writeTo(
            									    new File(thisSharkDir, fileName)
            										);
            
										}
									}
								}

								myShepherd.beginDBTransaction();
								if (myShepherd.isMarkedIndividual(individualName)) {
									MarkedIndividual add2shark=myShepherd.getMarkedIndividual(individualName);
									
									
									try{
									
										add2shark.addDataFile(fileName);
										add2shark.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>"+"Submitted new file: "+fileName+".</p>");
									} catch(Exception le){
										locked=true;
										myShepherd.rollbackDBTransaction();
									}
									
									
									
									if(!locked){
												myShepherd.commitDBTransaction();
												out.println(ServletUtilities.getHeader());
												out.println("<strong>Success:</strong> I have successfully uploaded your data file.");
												out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+individualName+"\">Return to "+individualName+"</a></p>\n");
												out.println(ServletUtilities.getFooter());
												//String message="A new data file named "+fileName+" has been added to "+request.getParameter("individual")+".";
									} 
									else {
										
												out.println(ServletUtilities.getHeader());
												out.println("<strong>Failure:</strong> I failed to add your file. This record is currently being modified by another user. Please try to add the file again in a few seconds.");
												out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+individualName+"\">Return to "+individualName+"</a></p>\n");
												out.println(ServletUtilities.getFooter());
									}
									}
								else {
									myShepherd.rollbackDBTransaction();
								out.println(ServletUtilities.getHeader());
								out.println("<strong>Error:</strong> I was unable to upload your file. I cannot find the record that you intended it for in the database.");
								out.println(ServletUtilities.getFooter());
									
									}
							} catch (IOException lEx) {
      							lEx.printStackTrace();
      							out.println(ServletUtilities.getHeader());
								out.println("<strong>Error:</strong> I was unable to upload your file.");
								out.println(ServletUtilities.getFooter());
      							
      							
    							}
    							out.close();
    							myShepherd.closeDBTransaction();
    						}

		
		
	

		}
	
	
