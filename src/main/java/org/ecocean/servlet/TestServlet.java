package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.ecocean.*;

import javax.jdo.*;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;


//adds spots to a new encounter
public class TestServlet extends HttpServlet{
	
	//open a shepherd
	//shepherd myShepherd;
	
	public void init(ServletConfig config) throws ServletException {
    	super.init(config);
    	
    	
    		//myShepherd=new shepherd();
    	
    
  }

	
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {

	// Here we forward to the appropriate page using the request dispatcher

    //getServletContext().getRequestDispatcher("/Noget.html").forward(req, res);
    
    doPost(request, response);
    
    
		}
		
	/*public boolean isInList(Sheet sheet, String encNum){

		int rows=sheet.getRows();
		for(int i=1;i<rows;i++) {
			Cell a1 = sheet.getCell(0,i); 
			if(a1.getContents().equals(encNum)) {
				return true;
			}
		}
		
		
		return false;
		
	}*/

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
		
	  String context="context0";
    context=ServletUtilities.getContext(request);
	  
	  response.setContentType("text/html");
		boolean madeChanges=false;
		PrintWriter out = response.getWriter();
		Shepherd myShepherd=new Shepherd(context);
		myShepherd.beginDBTransaction();
		Extent encClass=myShepherd.getPM().getExtent(Encounter.class, true);
		Query query=myShepherd.getPM().newQuery(encClass);
		try {
		
			
			//DateTime dt=new DateTime();
			//DateTimeFormatter fmt = ISODateTimeFormat.date();
			//String strOutputDateTime = fmt.print(dt);
			
			
			
			Iterator it=myShepherd.getAllEncounters(query);
			int count=0;
			while(it.hasNext()) {
				
				Encounter enc=(Encounter)it.next();
				
			
				madeChanges=true;
				enc.setLivingStatus("alive");
				
				
			
		}
			
			
			
		
		if(madeChanges) {
			myShepherd.commitDBTransaction();
			query.closeAll();
			myShepherd.closeDBTransaction();
			myShepherd=null;
			query=null;
			
			}
		else {
			query.closeAll();
			myShepherd.rollbackDBTransaction();
			myShepherd.closeDBTransaction();
			query=null;
		}
		
		//workbook.close(); 
	
	//out.println("This test servlet has executed and fixed encounter data!");
	//out.println("The number of Ningaloo photos in the library is: "+numPhotos+"<br>");		
	//out.println("The number of left-side patterns in the library is: "+numLeftSidePatterns+"<br>");		
	//out.println("The number of right-side patterns in the library is: "+numRightSidePatterns+"<br>");
	//out.println("The number of encounters with left- and right-side patterns in the library is: "+numCombined+"<br>");
	//out.println("The number of Unassigned encounters with left-side pattern data is: "+numUnassignedLeft+"<br>");
	//out.println("The number of Unassigned encounters with right-side pattern data is: "+numUnassignedRight+"<br>");
	//out.println("The number of test names was: "+jason+"<br>");
	//out.println("The number of contributors to the library is: "+numnames+"<br>");
	//out.println(contributorNames);
	
	
	
	//out.println("The number of corrections made is: "+corrections);
	}
	catch(Exception e) {
		System.out.println("You really screwed this one up!");
		e.printStackTrace();
		query.closeAll();
		query=null;
		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();

		
	}
	out.close();
}
}