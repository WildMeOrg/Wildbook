/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean.servlet;


import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServlet;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Vector;
import java.util.Iterator;
import org.ecocean.*;
import javax.jdo.Query;
import javax.jdo.Extent;
import java.lang.NumberFormatException;
import java.util.StringTokenizer;


//returns the results of an image search request in XML
//test
public class CalendarXMLServer2 extends HttpServlet {
	
	
	public void init(ServletConfig config) throws ServletException {
    	super.init(config);
  	}

	
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
    	doPost(request, response);
	}
		

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
		
		System.out.println("CalendarXMLServer2 received: "+request.getQueryString());	
		//set up the output
		response.setContentType("text/xml");
		PrintWriter out = response.getWriter();	
      	out.println("<data>");
		
      	
      	
		//establish a shepherd to manage DB interactions
		Shepherd myShepherd=new Shepherd();
		
		//change
		Extent encClass=myShepherd.getPM().getExtent(Encounter.class, true);
		Query queryEnc=myShepherd.getPM().newQuery(encClass);
		
		//required filters for output XML
		String from="";
		String fromYear="";
		String fromMonth="";
		//String fromDay="";
		String to="";
		String toYear="";
		String toMonth="";
		//String toDay="";
		String locCode="NONE";
		int startYear=1800;
		int endYear=9999;
		int startMonth=1;
		int endMonth=12;
		
		

		
		//get filters from request string
		if(request.getParameter("from")!=null) {
			try{
				
				from=request.getParameter("from");
				StringTokenizer str=new StringTokenizer(from,"-");
				int count=str.countTokens();
				for(int i=0;i<count;i++){
					if(i==0){fromYear=(String)str.nextElement();}
					if(i==1){fromMonth=(String)str.nextElement();}
					//if(i==2){fromDay=(String)str.nextElement();}
				}
				
			}
			catch(NumberFormatException nfe) {}
		}
		if(request.getParameter("to")!=null) {
			try{
				
				to=request.getParameter("to");
				StringTokenizer str=new StringTokenizer(to,"-");
				int count=str.countTokens();
				for(int i=0;i<count;i++){
					if(i==0){toYear=(String)str.nextElement();}
					if(i==1){toMonth=(String)str.nextElement();}
					//if(i==2){toDay=(String)str.nextElement();}
				}
				
			}
			catch(NumberFormatException nfe) {}
		}
		if(!fromYear.equals("")){startYear=Integer.parseInt(fromYear);}
		if(!toYear.equals("")){endYear=Integer.parseInt(toYear);}
		if(!fromMonth.equals("")){startMonth=Integer.parseInt(fromMonth);}
		if(!toMonth.equals("")){endMonth=Integer.parseInt(toMonth);}
		
		String filter="this.year >= "+startYear+" && this.year <= "+endYear+ " && (this.month >= "+startMonth+" || this.month <= "+ endMonth+")";		
		
		if((request.getParameter("locCode")!=null)&&(!request.getParameter("locCode").equals("NONE"))) {
			try{
				locCode=request.getParameter("locCode");
				filter+=" && this.locationID.startsWith(\""+locCode+"\")";
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
		queryEnc.setFilter(filter);
		queryEnc.setOrdering("individualID descending");

		//create a vector to hold matches
		Vector matches=new Vector();
		

		myShepherd.beginDBTransaction();
		
		try{

			Iterator allEncounters=myShepherd.getAllEncounters(queryEnc);

			while(allEncounters.hasNext()) {
				Encounter tempE=(Encounter)allEncounters.next();
      			matches.add(tempE.getEncounterNumber());
			}

		//output the XML for matching encounters
      	if(matches.size()>0) {
      		
      		//open DB again to pull data
      		//myShepherd.beginDBTransaction();
      		
      		try{
      			
      			//now spit out that XML for each match!
      			//remember to set primary attribute!
      			for(int i=0;i<matches.size();i++) {
      				String thisEncounter=(String)matches.get(i);
      				Encounter tempEnc=myShepherd.getEncounter(thisEncounter);
      				if(tempEnc!=null){
      					if(!tempEnc.isAssignedToMarkedIndividual().equals("Unassigned")){
      					
							String sex="-";
							MarkedIndividual sharky=myShepherd.getMarkedIndividual(tempEnc.isAssignedToMarkedIndividual());
							if((!sharky.getSex().equals("Unknown"))&&(!sharky.getSex().equals("unknown"))) {
								if(sharky.getSex().equals("male")){
									sex="M";
								}
								else{
									sex="F";
								}
							}
							String size="-";
							if(tempEnc.getSizeAsDouble()!=null) {
								size=tempEnc.getSizeAsDouble().toString();
							}
   							String outputXML="<event id=\""+tempEnc.getCatalogNumber()+"\">";
   							outputXML+="<start_date>"+tempEnc.getYear()+"-"+tempEnc.getMonth()+"-"+tempEnc.getDay()+" "+"01:00"+"</start_date>";
   							outputXML+="<end_date>"+tempEnc.getYear()+"-"+tempEnc.getMonth()+"-"+tempEnc.getDay()+" "+"01:00"+"</end_date>";
   							outputXML+="<text><![CDATA["+tempEnc.getIndividualID()+"("+sex+"/"+size+")]]></text>";
   							outputXML+="<details></details></event>";
   							out.println(outputXML);
      				 } else{
      				 	String sex="-";
      				 	if((!tempEnc.getSex().equals("Unknown"))&&(!tempEnc.getSex().equals("unknown"))) {
							if(tempEnc.getSex().equals("male")){
									sex="M";
								}
								else{
									sex="F";
								}
						}
						String size="-";
						if(tempEnc.getSizeAsDouble()!=null) {
								size=tempEnc.getSizeAsDouble().toString();
						}
						String outputXML="<event id=\""+tempEnc.getCatalogNumber()+"\">";
							outputXML+="<start_date>"+tempEnc.getYear()+"-"+tempEnc.getMonth()+"-"+tempEnc.getDay()+" "+"01:00"+"</start_date>";
							outputXML+="<end_date>"+tempEnc.getYear()+"-"+tempEnc.getMonth()+"-"+tempEnc.getDay()+" "+"01:01"+"</end_date>";
							outputXML+="<text><![CDATA[No ID ("+sex+"/"+size+")]]></text>";
							outputXML+="<details></details></event>";
							out.println(outputXML);
      				}
      			}
      					
      					
      		}

      		}
      		catch(Exception e){
      				e.printStackTrace();
      		}

      			
      	} //end if-matches>0
      	
		} //end try
		catch(Exception cal_e) {cal_e.printStackTrace();}
		queryEnc.closeAll();
		queryEnc=null;
		myShepherd.rollbackDBTransaction();
  		myShepherd.closeDBTransaction();
  		

      	out.println("</data>");
      	out.close();
	}//end doPost

} //end class
	
	
