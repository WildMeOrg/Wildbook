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


public class IndividualRemoveDataFile extends HttpServlet {

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
				
	
				    	
								fileName=request.getParameter("filename").replaceAll("%20"," ");
								individualName=request.getParameter("individual");
								myShepherd.beginDBTransaction();
								if ((request.getParameter("individual")!=null)&&(request.getParameter("filename")!=null)&&(myShepherd.isMarkedIndividual(individualName))) {
									MarkedIndividual sharky=myShepherd.getMarkedIndividual(individualName);
									
									
									int positionInList=0;
									try{
									
									
									
									sharky.removeDataFile(fileName);
									sharky.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>"+"Removed "+individualName+" data file: "+fileName+".</p>");
									
									}
									catch(Exception le){
										locked=true;
										myShepherd.rollbackDBTransaction();
									}
									
									if(!locked){
												myShepherd.commitDBTransaction();
												out.println(ServletUtilities.getHeader());
												out.println("<strong>Success!</strong> I have successfully removed the data file. When returning to the individual's page, please make sure to refresh your browser to see the changes. Changes may not be visible until you have done so.");
							
												out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+individualName+"\">Return to "+individualName+"</a></p>\n");
												out.println(ServletUtilities.getFooter());
												}
									else{
												
												out.println(ServletUtilities.getHeader());
												out.println("<strong>Failure!</strong> This record is currently being modified by another user. Please wait a few seconds before trying to remove this data file again.");
										
												out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+individualName+"\">Return to "+individualName+"</a></p>\n");
												out.println(ServletUtilities.getFooter());
										
									}
									}
								else {
									myShepherd.rollbackDBTransaction();
								out.println(ServletUtilities.getHeader());
								out.println("<strong>Error:</strong> I was unable to remove your data file. I cannot find the record that you intended it for in the database, or I wasn't sure what file you wanted to remove.");
								out.println(ServletUtilities.getFooter());
									
									}
								out.close();
								myShepherd.closeDBTransaction();
    						}

		}
	
	
