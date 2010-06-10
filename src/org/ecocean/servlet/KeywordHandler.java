package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;
import org.ecocean.*;
//import javax.jdo.*;
//import com.poet.jdo.*;
import java.lang.StringBuffer;
import java.lang.NullPointerException;



//handles operations to sharks. possible operations include, create new, add encounter, remove encounter from
public class KeywordHandler extends HttpServlet {


	
	public void init(ServletConfig config) throws ServletException {
    	super.init(config);
  	}
		

		
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
		doPost(request, response);
		}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
		Shepherd myShepherd=new Shepherd();
		//set up for response
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();

		//System.out.println(request.getQueryString());
		String action=request.getParameter("action");
		System.out.println("Action is: "+action);
		//System.out.println(request.getCharacterEncoding());
		if(action!=null){
			
			if ((action.equals("addNewWord"))&&(request.getParameter("keyword")!=null)&&(request.getParameter("readableName")!=null)) {
				String indexname=request.getParameter("keyword");
				String readableName=request.getParameter("readableName");
				Keyword newword=new Keyword(indexname, readableName);
				String newkw=myShepherd.storeNewKeyword(newword, indexname);
				
				//confirm success
				out.println(ServletUtilities.getHeader());
				out.println("<strong>Success:</strong> The new image indexing keyword <em>"+readableName+"</em> has been added.");
				//out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("shark")+"\">Return to shark <strong>"+request.getParameter("shark")+"</strong></a></p>\n");
				out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/appadmin/kwAdmin.jsp\">Return to keyword administration page.</a></p>\n");
				out.println("<p><a href=\"../encounters/allEncounters.jsp\">View all encounters</a></font></p>");
				out.println(ServletUtilities.getFooter());
				
			}
			
			else if ((action.equals("removeWord"))&&(request.getParameter("keyword")!=null)) {
				myShepherd.beginDBTransaction();
				Keyword word=myShepherd.getKeyword(request.getParameter("keyword"));
				String desc=word.getReadableName();
				myShepherd.throwAwayKeyword(word);
				myShepherd.commitDBTransaction();
			
				//confirm success
				out.println(ServletUtilities.getHeader());
				out.println("<strong>Success:</strong> The image indexing keyword <i>"+desc+"</i> has been removed.");
				//out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("shark")+"\">Return to shark <strong>"+request.getParameter("shark")+"</strong></a></p>\n");
				out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/appadmin/kwAdmin.jsp\">Return to keyword administration page.</a></p>\n");
				out.println("<p><a href=\"../encounters/allEncounters.jsp\">View all encounters</a></font></p>");
				out.println(ServletUtilities.getFooter());
			}
			
			else if ((action.equals("addPhoto"))&&(request.getParameter("photoName")!=null)&&(request.getParameter("keyword")!=null)&&(request.getParameter("number")!=null)) {
				boolean locked=false;
				String readableName="";
				myShepherd.beginDBTransaction();
				try{
					Keyword word=myShepherd.getKeyword(request.getParameter("keyword"));
					word.addImageName(request.getParameter("number")+"/"+request.getParameter("photoName"));
					readableName=word.getReadableName();
				}
				catch(Exception le){
								locked=true;
								myShepherd.rollbackDBTransaction();
								le.printStackTrace();
				}
				if(!locked) {
				
					myShepherd.commitDBTransaction();
				
					//confirm success
					out.println(ServletUtilities.getHeader());
					out.println("<strong>Success:</strong> The image name "+request.getParameter("photoName")+" has been added to indexing keyword <i>"+readableName+"</i>.");
					//out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("shark")+"\">Return to shark <strong>"+request.getParameter("shark")+"</strong></a></p>\n");
					out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
					out.println("<p><a href=\"../encounters/allEncounters.jsp\">View all encounters</a></font></p>");
					out.println(ServletUtilities.getFooter());
				}
				else {
							out.println(ServletUtilities.getHeader());
							out.println("<strong>Failure:</strong> I have NOT added this keyword to the photo. This keyword is currently being modified by another user.");
							out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
							out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
      						out.println("<p><a href=\"allIndividuals.jsp\">View all sharks</a></font></p>");
										
							out.println(ServletUtilities.getFooter());
				}
			}
			
			//edit the text of a keyword
			else if ((action.equals("rename"))&&(request.getParameter("keyword")!=null)&&(request.getParameter("newName")!=null)) {
				myShepherd.beginDBTransaction();
				Keyword word=myShepherd.getKeyword(request.getParameter("keyword"));
				String oldName=word.getReadableName();
				word.setReadableName(request.getParameter("newName"));
				
				myShepherd.commitDBTransaction();
				
				//confirm success
				out.println(ServletUtilities.getHeader());
				out.println("<strong>Success:</strong> The keyword <i>"+oldName+"</i> has been changed to <i>"+request.getParameter("newName")+"</i>.");
				out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/appadmin/kwAdmin.jsp\">Return to keyword administration.</a></font></p>");
				out.println(ServletUtilities.getFooter());
			}
			
			else if ((action.equals("removePhoto"))&&(request.getParameter("photoName")!=null)&&(request.getParameter("keyword")!=null)&&(request.getParameter("number")!=null)) {
				myShepherd.beginDBTransaction();
				Keyword word=myShepherd.getKeyword(request.getParameter("keyword"));
				word.removeImageName(request.getParameter("number")+"/"+request.getParameter("photoName"));
				String readableName=word.getReadableName();
				myShepherd.commitDBTransaction();
				
				//confirm success
				out.println(ServletUtilities.getHeader());
				out.println("<strong>Success:</strong> The image name "+request.getParameter("photoName")+" has been removed from indexing keyword <i>"+readableName+"</i>.");
				//out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("shark")+"\">Return to shark <strong>"+request.getParameter("shark")+"</strong></a></p>\n");
				out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
				out.println("<p><a href=\"../encounters/allEncounters.jsp\">View all encounters</a></font></p>");
				out.println(ServletUtilities.getFooter());
			}
			
			
			else {
				
					out.println(ServletUtilities.getHeader());
					out.println("<strong>Error:</strong> I don't have enough information to complete your request.");
					//out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("shark")+"\">Return to shark <strong>"+request.getParameter("shark")+"</strong></a></p>\n");
					out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
      				out.println("<p><a href=\"allIndividuals.jsp\">View all sharks</a></font></p>");
      				//out.println("<p><a href=\"encounters/allEncounters.jsp?rejects=true\">View all rejected encounters</a></font></p>");
					out.println(ServletUtilities.getFooter());
			}
			
			
		}
		else {
			out.println(ServletUtilities.getHeader());
			out.println("<p>I did not receive enough data to process your command. No action was indicated to me.</p>");
			out.println("<p>Please try again or <a href=\"welcome.jsp\">login here</a>.");
			out.println(ServletUtilities.getFooter());
			//npe2.printStackTrace();
		}
			myShepherd.closeDBTransaction();
			myShepherd=null;
			out.flush();
			out.close();
		}
	
}