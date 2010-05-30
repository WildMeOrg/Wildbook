package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;
import org.ecocean.*;
//import javax.jdo.*;
//import com.poet.jdo.*;
//import com.oreilly.servlet.multipart.*;
import java.lang.StringBuffer;


//handles operations to sharks. possible operations include, create new, add encounter, remove encounter from
public class IndividualSetNickName extends HttpServlet {
	
	
	
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
		
		String sharky="None", nickname="", namer="";
				
	
				    
								sharky=request.getParameter("individual");
								nickname=request.getParameter("nickname");
								namer=request.getParameter("namer");
								myShepherd.beginDBTransaction();
								if ((myShepherd.isMarkedIndividual(sharky))&&(request.getParameter("nickname")!=null)&&(request.getParameter("namer")!=null)) {
									MarkedIndividual myShark=myShepherd.getMarkedIndividual(sharky);
									try{


										myShark.setNickName(nickname);
										myShark.setNickNamer(namer);
									
									
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
												out.println("<strong>Success!</strong> I have successfully changed the nickname for "+sharky+" to "+nickname+".</p>");
							
												out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+sharky+"\">Return to "+sharky+"</a></p>\n");
												out.println(ServletUtilities.getFooter());
												String message="The nickname for "+sharky+" was set as "+nickname+".";
												
									}
									else{
										
										out.println(ServletUtilities.getHeader());
										out.println("<strong>Failure!</strong> This record is currently being modified by another user. Please wait a few seconds before trying to nickname this individual again.");
										out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+sharky+"\">Return to "+sharky+"</a></p>\n");
										out.println(ServletUtilities.getFooter());
										
									}
								}
								else {
									myShepherd.rollbackDBTransaction();
									out.println(ServletUtilities.getHeader());
									out.println("<strong>Error:</strong> I was unable to set the nickname. I cannot find the shark that you intended it for in the database.");
									out.println(ServletUtilities.getFooter());
								}
								out.close();
								myShepherd.closeDBTransaction();
    						}

		
		
	

		}
	
	
