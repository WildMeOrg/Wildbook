package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;
import org.ecocean.*;


//adds spots to a new encounter
public class UpdateEmailAddress extends HttpServlet{
	
	//open a shepherd
	Shepherd myShepherd;
	
	
	
	public void init(ServletConfig config) throws ServletException {
    	super.init(config);
    	
    	
    		myShepherd=new Shepherd();
    	
    
  }

	
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {

	
		doPost(request, response);
    
    
	}
		


	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		
		boolean madeChanges=false;
		boolean ok2proceed=true;
		int numChanges=0;
		String findEmail="";
		String replaceEmail="";
		if(request.getParameter("replaceEmail")!=null) {
			replaceEmail=request.getParameter("replaceEmail").trim();
		}
		else{
			ok2proceed=false;
		}
		if((request.getParameter("findEmail")!=null)&&(!request.getParameter("findEmail").equals(""))) {
			findEmail=request.getParameter("findEmail").trim();
			
		}

		
		try {
		

	    myShepherd.beginDBTransaction();
			Iterator it=myShepherd.getAllEncountersAndUnapproved();
			while(it.hasNext()) {
		
				Encounter tempEnc=(Encounter)it.next();
				if(tempEnc.getSubmitterEmail().indexOf(findEmail)!=-1) {
					String newSubmitterEmail=tempEnc.getSubmitterEmail().replaceAll(findEmail,replaceEmail);
					tempEnc.setSubmitterEmail(newSubmitterEmail);
					madeChanges=true;
					numChanges++;
				}
				if(tempEnc.getPhotographerEmail().indexOf(findEmail)!=-1) {
					String newPhotographerEmail=tempEnc.getPhotographerEmail().replaceAll(findEmail,replaceEmail);
					tempEnc.setPhotographerEmail(newPhotographerEmail);
					madeChanges=true;
					numChanges++;
				}
        if((tempEnc.getInformOthers()!=null)&&(tempEnc.getInformOthers().indexOf(findEmail)!=-1)) {
          String newPhotographerEmail=tempEnc.getInformOthers().replaceAll(findEmail,replaceEmail);
          tempEnc.setInformOthers(newPhotographerEmail);
          madeChanges=true;
          numChanges++;
        }

			}
			if(madeChanges) {myShepherd.commitDBTransaction();}
			else {
				myShepherd.rollbackDBTransaction();
			}
			out.println(ServletUtilities.getHeader());
			out.println("<strong>Success!</strong> I successfully replaced "+numChanges+" instance(s) of email address "+findEmail+" with "+replaceEmail+".");
			out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/appadmin/admin.jsp\">Return to Administration</a></p>\n");
			out.println(ServletUtilities.getFooter());

		}
		
		
		
		catch(Exception e) {
			//System.out.println("You really screwed this one up!");
			out.println(ServletUtilities.getHeader());
			out.println("<strong>Error:</strong> I encountered an exception trying to replace this email address. The exception is listed below.");
			out.println("<pre>"+e.getMessage()+"</pre>");
			out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/appadmin/admin.jsp\">Return to Administration</a></p>\n");
			out.println(ServletUtilities.getFooter());
			e.printStackTrace();
		}

	out.close();
	
}
}