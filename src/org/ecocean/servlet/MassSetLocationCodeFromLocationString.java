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


public class MassSetLocationCodeFromLocationString extends HttpServlet {
	
	
	
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
		
		String locCode="", matchString="";
		matchString=request.getParameter("matchString").toLowerCase();
		locCode=request.getParameter("locCode");
		Extent encClass=myShepherd.getPM().getExtent(Encounter.class, true);
		Query query=myShepherd.getPM().newQuery(encClass);
		
		if ((locCode!=null)&&(matchString!=null)&&(!matchString.equals(""))&&(!locCode.equals(""))) {			
			myShepherd.beginDBTransaction();
			try{
				Iterator it=myShepherd.getAllEncounters(query);
				
				while(it.hasNext()) {
					Encounter tempEnc=(Encounter)it.next();
					if(tempEnc.getLocation().toLowerCase().indexOf(matchString)!=-1) {
						tempEnc.setLocationCode(locCode);
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
				//myShepherd.commitDBTransaction();
				//myShepherd.closeDBTransaction();
				out.println(ServletUtilities.getHeader());
				out.println(("<strong>Success!</strong> I have successfully changed the location code to "+locCode+" for "+count+" encounters based on the location string: "+matchString+"."));
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
			out.println("<strong>Error:</strong> I was unable to set the location code as requested due to missing parameter values.");
			out.println(ServletUtilities.getFooter());
		}
		out.close();
    }
	
}
	
	
