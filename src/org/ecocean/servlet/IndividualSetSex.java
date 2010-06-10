package org.ecocean.servlet;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import org.ecocean.*;


public class IndividualSetSex extends HttpServlet {

	
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


		
		String action=request.getParameter("action");

					if ((request.getParameter("individual")!=null)&&(request.getParameter("selectSex")!=null)) {
						
						myShepherd.beginDBTransaction();
						MarkedIndividual changeMe=myShepherd.getMarkedIndividual(request.getParameter("individual"));
						String oldSex="Unknown";
						try{
						
							if (changeMe.getSex()!=null) {oldSex=changeMe.getSex();}
							changeMe.setSex(request.getParameter("selectSex"));
							changeMe.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>Changed sex from "+oldSex+" to "+request.getParameter("selectSex")+".</p>");
						}catch(Exception le){
							//System.out.println("Hit locked exception on action: "+action);
							locked=true;
							le.printStackTrace();
							myShepherd.rollbackDBTransaction();
						}
						
						
						
						if(!locked){
							myShepherd.commitDBTransaction(action);
							out.println(ServletUtilities.getHeader());
							out.println("<strong>Success:</strong> Sex has been updated from "+oldSex+" to "+request.getParameter("selectSex")+".");
							out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("individual")+"\">Return to <strong>"+request.getParameter("individual")+"</strong></a></p>\n");
							//out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
							out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
	      					out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
	      					//out.println("<p><a href=\"encounters/allEncounters.jsp?rejects=true\">View all rejected encounters</a></font></p>");
							out.println(ServletUtilities.getFooter());
							String message="The sex for "+request.getParameter("individual")+" has been updated from "+oldSex+" to "+request.getParameter("selectSex")+".";
							ServletUtilities.informInterestedIndividualParties(request.getParameter("individual"), message);
							}
						else{
							
							out.println(ServletUtilities.getHeader());
							out.println("<strong>Failure:</strong> Sex was NOT updated. This record is currently being modified by another user. Please try this operation again in a few seconds.");
							out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("individual")+"\">Return to <strong>"+request.getParameter("individual")+"</strong></a></p>\n");
							//out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
							out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
	      					out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
	      					//out.println("<p><a href=\"encounters/allEncounters.jsp?rejects=true\">View all rejected encounters</a></font></p>");
							out.println(ServletUtilities.getFooter());
							
							}
						
					}		
						
					else {
						out.println(ServletUtilities.getHeader());
						out.println("<strong>Error:</strong> I don't have enough information to complete your request.");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("individual")+"\">Return to <strong>"+request.getParameter("individual")+"</strong></a></p>\n");
						out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
	      				out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
	      				//out.println("<p><a href=\"encounters/allEncounters.jsp?rejects=true\">View all rejected encounters</a></font></p>");
						out.println(ServletUtilities.getFooter());	
							
						}
					
					
		

	
			out.close();
			myShepherd.closeDBTransaction();
		}
	
}