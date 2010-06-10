package org.ecocean.servlet;
import javax.jdo.Extent;
import javax.jdo.Query;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Vector;
import org.ecocean.*;

import com.oreilly.servlet.multipart.*;
import java.lang.StringBuffer;


//handles operations to sharks. possible operations include, create new, add encounter, remove encounter from
public class MassSwapLocationCode extends HttpServlet {
	

	
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
		
		String newLocCode="", oldLocCode="";
		oldLocCode=request.getParameter("oldLocCode");
		newLocCode=request.getParameter("newLocCode");
		Extent encClass=myShepherd.getPM().getExtent(Encounter.class, true);
		Query query=myShepherd.getPM().newQuery(encClass);
		
		if ((oldLocCode!=null)&&(oldLocCode!=null)&&(!newLocCode.equals(""))&&(!newLocCode.equals(""))) {			
			myShepherd.beginDBTransaction();
			try{
				Iterator it=myShepherd.getAllEncounters(query);
				
				while(it.hasNext()) {
					Encounter tempEnc=(Encounter)it.next();
					if(tempEnc.getLocationCode().equals(oldLocCode)) {
						tempEnc.setLocationCode(newLocCode);
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
			if(!madeChanges){
				myShepherd.rollbackDBTransaction();
				myShepherd.closeDBTransaction();
			}						
			//success!!!!!!!!
			else if(!locked){
				myShepherd.commitDBTransaction();
				myShepherd.closeDBTransaction();
				out.println(ServletUtilities.getHeader());
				out.println(("<strong>Success!</strong> I have successfully changed the location code "+oldLocCode+" to "+newLocCode+" for "+count+" encounters."));
				out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/appadmin/admin.jsp\">Return to the Administration page.</a></p>\n");
				out.println(ServletUtilities.getFooter());
			}
			//failure due to exception
			else{
				out.println(ServletUtilities.getHeader());
				out.println("<strong>Failure!</strong> An encounter is currently being modified by another user. Please wait a few seconds before trying to remove this data file again.");
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
