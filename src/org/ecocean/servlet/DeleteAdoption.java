package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;
import org.ecocean.*;

import javax.jdo.*;
import com.oreilly.servlet.multipart.*;
import java.lang.StringBuffer;

public class DeleteAdoption extends HttpServlet {
	

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

		String number=request.getParameter("number");
		

		myShepherd.beginDBTransaction();
		if ((myShepherd.isAdoption(number))) {
			
			try{
				Adoption ad=myShepherd.getAdoptionDeepCopy(number);

				String savedFilename=request.getParameter("number")+".dat";
				//File thisEncounterDir=new File(((new File(".")).getCanonicalPath()).replace('\\','/')+"/"+CommonConfiguration.getAdoptionDirectory()+File.separator+request.getParameter("number"));
				File thisEncounterDir=new File(getServletContext().getRealPath(("/adoptions/"+request.getParameter("number"))));
	
				File serializedBackup=new File(thisEncounterDir,savedFilename);
				FileOutputStream fout = new FileOutputStream(serializedBackup);
				ObjectOutputStream oos = new ObjectOutputStream(fout);
				oos.writeObject(ad);
				oos.close();
				
				Adoption ad2=myShepherd.getAdoption(number);

				myShepherd.throwAwayAdoption(ad2);
									

									
			}
			catch(Exception le){
					locked=true;
					le.printStackTrace();
					myShepherd.rollbackDBTransaction();
					myShepherd.closeDBTransaction();
			}
									
			if(!locked){
				myShepherd.commitDBTransaction();
				myShepherd.closeDBTransaction();
				out.println(ServletUtilities.getHeader());
				out.println("<strong>Success!</strong> I have successfully removed adoption "+number+". However, a saved copy an still be restored.");
							
				out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/"+CommonConfiguration.getAdoptionDirectory()+"/adoption.jsp\">Return to the Adoption Create/Edit page.</a></p>\n");
				out.println(ServletUtilities.getFooter());
			}
			else{
										
												out.println(ServletUtilities.getHeader());				
												out.println("<strong>Failure!</strong> I failed to delete this adoption. Check the logs for more details.");
										
												out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/"+CommonConfiguration.getAdoptionDirectory()+"/adoption.jsp\">Return to the Adoption Create/Edit page.</a></p>\n");
												out.println(ServletUtilities.getFooter());
										
									}

								}
								else {
									myShepherd.rollbackDBTransaction();
									myShepherd.closeDBTransaction();
								out.println(ServletUtilities.getHeader());
								out.println("<strong>Error:</strong> I was unable to remove your image file. I cannot find the encounter that you intended it for in the database.");
								out.println(ServletUtilities.getFooter());
									
									}
								out.close();
    						}

		
		
	

		}
	
	
