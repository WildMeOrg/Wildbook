package org.ecocean.servlet;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import org.ecocean.*;


public class IndividualAddComment extends HttpServlet {

	
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

		

				myShepherd.beginDBTransaction();
				if ((request.getParameter("individual")!=null)&&(request.getParameter("user")!=null)&&(request.getParameter("comments")!=null)&&(myShepherd.isMarkedIndividual(request.getParameter("individual")))) {
					
					MarkedIndividual commentMe=myShepherd.getMarkedIndividual(request.getParameter("individual"));
					if(ServletUtilities.isUserAuthorizedForIndividual(commentMe, request)){
					
						try{
							commentMe.addComments("<p><em>"+request.getParameter("user")+" on "+(new java.util.Date()).toString()+"</em><br>"+request.getParameter("comments")+"</p>");
						}
						catch(Exception le){
							locked=true;
							le.printStackTrace();
							myShepherd.rollbackDBTransaction();
						}
					
						if(!locked){
							myShepherd.commitDBTransaction();
							out.println(ServletUtilities.getHeader());
							out.println("<strong>Success:</strong> I have successfully added your comments.");
							out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("individual")+"\">Return to "+request.getParameter("individual")+"</a></p>\n");
							out.println(ServletUtilities.getFooter());
							String message="A new comment has been added to "+request.getParameter("individual")+". The new comment is: \n"+request.getParameter("comments");
							ServletUtilities.informInterestedIndividualParties(request.getParameter("individual"), message);
						}
						else {
							out.println(ServletUtilities.getHeader());
							out.println("<strong>Failure:</strong> I did NOT add your comments. Another user is currently modifying this record. Please try to add your comments again in a few seconds.");
							out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("shark")+"\">Return to individual "+request.getParameter("individual")+"</a></p>\n");
							out.println(ServletUtilities.getFooter());
						
						}

					}
					else{
						myShepherd.rollbackDBTransaction();
						out.println(ServletUtilities.getHeader());
						out.println("<strong>Failure:</strong> You are not authorized to modify this database record.");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("individual")+"\">Return to "+request.getParameter("individual")+"</a></p>\n");
						out.println(ServletUtilities.getFooter());
						
					}
				} 
				else {
								
					myShepherd.rollbackDBTransaction();
					out.println(ServletUtilities.getHeader());
					out.println("<strong>Failure:</strong> I do not have enough information to add your comments.");
					out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("individual")+"\">Return to "+request.getParameter("individual")+"</a></p>\n");
					out.println(ServletUtilities.getFooter());
				}
				
			out.close();
			myShepherd.closeDBTransaction();
		}
	
}