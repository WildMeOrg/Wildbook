package org.ecocean.servlet;


import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import org.ecocean.*;


//Set alternateID for this encounter/sighting
public class EncounterSetGPS extends HttpServlet {
	
	public void init(ServletConfig config) throws ServletException {
    	super.init(config);
    }

	
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
		doPost(request, response);
	}
	
	
	private void setDateLastModified(Encounter enc){
		String strOutputDateTime = ServletUtilities.getDate();
		enc.setDWCDateLastModified(strOutputDateTime);
	}
		

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
		Shepherd myShepherd=new Shepherd();
		//set up for response
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();
		boolean locked=false;
		boolean isOwner=true;
		
		/**
		if(request.getParameter("number")!=null){
			myShepherd.beginDBTransaction();
			if(myShepherd.isEncounter(request.getParameter("number"))) {
				Encounter verifyMyOwner=myShepherd.getEncounter(request.getParameter("number"));
				String locCode=verifyMyOwner.getLocationCode();
				
				//check if the encounter is assigned
				if((verifyMyOwner.getSubmitterID()!=null)&&(request.getRemoteUser()!=null)&&(verifyMyOwner.getSubmitterID().equals(request.getRemoteUser()))){
					isOwner=true;
				}
				
				//if the encounter is assigned to this user, they have permissions for it...or if they're a manager
				else if((request.isUserInRole("admin"))){
					isOwner=true;
				}
				//if they have general location code permissions for the encounter's location code
				else if(request.isUserInRole(locCode)){isOwner=true;}
			}
			myShepherd.rollbackDBTransaction();	
		}
		*/


		//reset GPS coordinates

				//System.out.println("Trying to resetGPS...");
				if ((request.getParameter("number")!=null)&&(request.getParameter("lat")!=null)&&(request.getParameter("longitude")!=null)&&(request.getParameter("gpsLongitudeMinutes")!=null)&&(request.getParameter("gpsLatitudeMinutes")!=null)&&(request.getParameter("longDirection")!=null)&&(request.getParameter("latDirection")!=null)&&(request.getParameter("gpsLongitudeSeconds")!=null)&&(request.getParameter("gpsLatitudeSeconds")!=null)) {
					myShepherd.beginDBTransaction();
					Encounter changeMe=myShepherd.getEncounter(request.getParameter("number"));
					setDateLastModified(changeMe);
					String longitude=request.getParameter("longitude");
					String lat=request.getParameter("lat");
					String gpsLongitudeMinutes=request.getParameter("gpsLongitudeMinutes");
					String gpsLatitudeMinutes=request.getParameter("gpsLatitudeMinutes");
					String gpsLongitudeSeconds=request.getParameter("gpsLongitudeSeconds");
					String gpsLatitudeSeconds=request.getParameter("gpsLatitudeSeconds");
					String latDirection=request.getParameter("latDirection");
					String longDirection=request.getParameter("longDirection");
					
					String oldGPS=changeMe.getGPSLatitude()+" "+changeMe.getGPSLongitude();
					if(oldGPS.equals(" ")){
						oldGPS="NO VALUE";
					}
					String newGPS=lat+"&deg; "+gpsLatitudeMinutes+"\' "+gpsLatitudeSeconds+"\" "+latDirection+" "+longitude+"&deg; "+gpsLongitudeMinutes+"\' "+gpsLongitudeSeconds+"\" "+longDirection;
					
					try{
					
						if (!(lat.equals(""))) {
							changeMe.setGPSLatitude(lat+"&deg; "+gpsLatitudeMinutes+"\' "+gpsLatitudeSeconds+"\" "+latDirection);
						
							
								try {
									double degrees=(new Double(lat)).doubleValue();
										double minutes=0;
										try{
											minutes=((new Double(gpsLatitudeMinutes)).doubleValue())/60;
										}
										catch(NumberFormatException nfe){}
											
										double seconds = 0;
										try{
											seconds=((new Double(gpsLatitudeSeconds)).doubleValue())/3600;
										}
										catch(NumberFormatException nfe){}
										double position=degrees+minutes+seconds;
										if(latDirection.toLowerCase().equals("south")) {
											position=position*-1;
										}
										changeMe.setDWCDecimalLatitude(position);

									
								}
								catch(Exception e) {
									System.out.println("EncounterSetGPS: problem setting decimal latitude!");
									e.printStackTrace();
								}
							
							
						}
						if (!(longitude.equals(""))) {
							changeMe.setGPSLongitude(longitude+"&deg; "+gpsLongitudeMinutes+"\' "+gpsLongitudeSeconds+"\" "+longDirection);
						
							try {
								double degrees=(new Double(longitude)).doubleValue();
									double minutes=0;
									try{
										minutes=((new Double(gpsLongitudeMinutes)).doubleValue())/60;
									}
									catch(NumberFormatException nfe){}
									double seconds=0;
									try{
										seconds = ((new Double(gpsLongitudeSeconds)).doubleValue())/3600;
									}
									catch(NumberFormatException nfe){}
										double position=degrees+minutes+seconds;
									if(longDirection.toLowerCase().equals("west")) {
										position=position*-1;
									}
									changeMe.setDWCDecimalLongitude(position);

								
							}
							catch(Exception e) {
								System.out.println("EncounterSetGPS: problem setting decimal longitude!");
								e.printStackTrace();
							}
						}
						
						//if one is not set, set all to null
						if((longitude.equals(""))||(lat.equals(""))){
							changeMe.setGPSLongitude("");
							changeMe.setGPSLatitude("");
							changeMe.setDecimalLatitude("");
							changeMe.setDecimalLongitude("");

							changeMe.setDWCDecimalLatitude(-9999.0);
							changeMe.setDWCDecimalLongitude(-9999.0);
							newGPS="NO VALUE";
							
						}
						changeMe.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>Changed encounter GPS coordinates from "+oldGPS+" to "+lat+"&deg; "+gpsLatitudeMinutes+"\' "+gpsLatitudeSeconds+"\" "+latDirection+" "+longitude+"&deg; "+gpsLongitudeMinutes+"\' "+gpsLongitudeSeconds+"\" "+longDirection+".</p>");
					
					
					
					}catch(Exception le){
						locked=true;
						le.printStackTrace();
						myShepherd.rollbackDBTransaction();
					}
					
					if(!locked){
					
						myShepherd.commitDBTransaction();
						out.println(ServletUtilities.getHeader());
						out.println("<strong>Success:</strong> The encounter's recorded GPS location has been updated from "+oldGPS+" to "+newGPS+".");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter <strong>"+request.getParameter("number")+"</strong></a></p>\n");
						out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
      					out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
      					out.println(ServletUtilities.getFooter());
						String message="The recorded GPS location for encounter #"+request.getParameter("number")+" has been updated from "+oldGPS+" to "+newGPS+".";
						ServletUtilities.informInterestedParties(request.getParameter("number"), message);
						}
					else{
						
						out.println(ServletUtilities.getHeader());
						out.println("<strong>Failure:</strong> Encounter GPS location was NOT updated. This encounter is currently being modified by another user. Please try this operation again in a few seconds. If this condition persists, contact the webmaster.");
						out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter <strong>"+request.getParameter("number")+"</strong></a></p>\n");
						out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
      					out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
      					out.println(ServletUtilities.getFooter());
						
						}
					
				}		
					
				else {
					out.println(ServletUtilities.getHeader());
					out.println("<strong>Error:</strong> I don't have enough information to complete your request.");
					out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter <strong>"+request.getParameter("number")+"</strong></a></p>\n");
					out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
      				out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
      				out.println(ServletUtilities.getFooter());	
						
					}
				
				
//end GPS reset
			out.close();
			myShepherd.closeDBTransaction();
    	}
}
	
	
