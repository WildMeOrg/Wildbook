package org.ecocean.servlet;
import javax.jdo.Extent;
import javax.jdo.Query;
import javax.servlet.*;
import javax.servlet.http.*;
//import java.io.FileReader;
//import java.io.BufferedReader;
//import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
//import java.util.Vector;
import org.ecocean.*;

//import com.oreilly.servlet.multipart.*;
//import java.lang.StringBuffer;


public class MassSetInformOthers extends HttpServlet {
	
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
		boolean madeChanges=false;
		int count=0;
		
		String informEmail="", matchString="";
		matchString=request.getParameter("matchString").toLowerCase();
		informEmail=request.getParameter("informEmail");
		Extent encClass=myShepherd.getPM().getExtent(Encounter.class, true);
		Query query=myShepherd.getPM().newQuery(encClass);
		
		if ((informEmail!=null)&&(matchString!=null)&&(!matchString.equals(""))&&(!informEmail.equals(""))) {			
			myShepherd.beginDBTransaction();
			try{
				Iterator it=myShepherd.getAllEncounters(query);
				
				while(it.hasNext()) {
					Encounter tempEnc=(Encounter)it.next();
					String previousInform="";
					if(tempEnc.getInformOthers()!=null){previousInform=tempEnc.getInformOthers();}
					
					if(tempEnc.getSubmitterName().toLowerCase().indexOf(matchString)!=-1) {
						tempEnc.setInformOthers(informEmail+","+previousInform);
						madeChanges=true;
						count++;
					}
					else if(tempEnc.getSubmitterEmail().toLowerCase().indexOf(matchString)!=-1) {
						tempEnc.setInformOthers(informEmail+","+previousInform);
						madeChanges=true;
						count++;
					}
					else if(tempEnc.getPhotographerEmail().toLowerCase().indexOf(matchString)!=-1) {
						tempEnc.setInformOthers(informEmail+","+previousInform);
						madeChanges=true;
						count++;
					}
					else if(tempEnc.getPhotographerName().toLowerCase().indexOf(matchString)!=-1) {
						tempEnc.setInformOthers(informEmail+","+previousInform);
						madeChanges=true;
						count++;
					}
					
					
					
				} //end while
			}
			catch(Exception le){
				locked=true;
				myShepherd.rollbackDBTransaction();
				myShepherd.closeDBTransaction();
			}
			if(!madeChanges&&!locked){
				myShepherd.rollbackDBTransaction();
				myShepherd.closeDBTransaction();
			}
			else if(!locked){
				myShepherd.commitDBTransaction();
				myShepherd.closeDBTransaction();
			}
			//success!!!!!!!!
			if(!locked){

				out.println(ServletUtilities.getHeader());
				out.println(("<strong>Success!</strong> I have successfully set the Inform Others field for "+count+" encounters based on the submitter/photographer string: "+matchString+"."));
				out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/appadmin/admin.jsp\">Return to the Administration page.</a></p>\n");
				out.println(ServletUtilities.getFooter());
			}
			//failure due to exception
			else{
				out.println(ServletUtilities.getHeader());
				out.println("<strong>Failure!</strong> An encounter is currently being modified by another user. Please wait a few seconds before trying to execute this operation again.");
				out.println(ServletUtilities.getFooter());							
			}
		}
		else {
			out.println(ServletUtilities.getHeader());
			out.println("<strong>Error:</strong> I was unable to set others to inform as requested due to missing parameter values.");
			out.println(ServletUtilities.getFooter());
		}
		out.close();
    }
	
}
	
	
