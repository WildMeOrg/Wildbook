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


/**
 * Returns the results of an encounter search request in XML for display in a calendar.
 * @author jholmber
 *
 */
public class CalendarXMLServer extends HttpServlet {
  
  
  public void init(ServletConfig config) throws ServletException {
      super.init(config);
    }

  
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
      doPost(request, response);
  }
    

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
    
    //System.out.println("CalendarXMLServer2 received: "+request.getQueryString()); 
    //set up the output
    response.setContentType("text/xml");
    PrintWriter out = response.getWriter(); 
    out.println("<data>");
    
        
        
    //establish a shepherd to manage DB interactions
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd=new Shepherd(context);
    myShepherd.setAction("CalendarXMLServer.class");
    
    
    //int numResults=0;

    
    Vector rEncounters=new Vector();      

    myShepherd.beginDBTransaction();
    
    EncounterQueryResult queryResult=EncounterQueryProcessor.processQuery(myShepherd, request, "individualID descending");
    rEncounters = queryResult.getResult();
    //rEncounters = EncounterQueryProcessor.processQuery(myShepherd, request, "individualID descending");
    
    

    //create a vector to hold matches
    Vector matches=new Vector();

    
    try{

      Iterator allEncounters=rEncounters.iterator();

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
                if(tempEnc.getIndividualID()!=null){
                
                  String sex="-";
                  MarkedIndividual sharky=myShepherd.getMarkedIndividual(tempEnc.getIndividualID());
                  if((sharky.getSex()!=null)&&(!sharky.getSex().toLowerCase().equals("unknown"))) {
                    if(sharky.getSex().equals("male")){
                      sex="M";
                    }
                    else{
                      sex="F";
                    }
                  }

                  String individualID="-";
                  if(tempEnc.getIndividualID()!=null)individualID=tempEnc.getIndividualID();
                  
                  String outputXML="<event id=\""+tempEnc.getCatalogNumber()+"\">";
                  outputXML+="<start_date>"+tempEnc.getYear()+"-"+tempEnc.getMonth()+"-"+tempEnc.getDay()+" "+"01:00"+"</start_date>";
                  outputXML+="<end_date>"+tempEnc.getYear()+"-"+tempEnc.getMonth()+"-"+tempEnc.getDay()+" "+"01:00"+"</end_date>";
                  outputXML+="<text><![CDATA["+individualID+"("+sex+")]]></text>";
                  outputXML+="<details></details></event>";
                  out.println(outputXML);
                } 
                else{

                  
                  String sex="-";
                  if((tempEnc.getSex()!=null)&&(!tempEnc.getSex().toLowerCase().equals("unknown"))) {
                    if(tempEnc.getSex().equals("male")){
                      sex="M";
                    }
                    else{
                      sex="F";
                    }
                  }
                  
                  
                  String outputXML="<event id=\""+tempEnc.getCatalogNumber()+"\">";
                  outputXML+="<start_date>"+tempEnc.getYear()+"-"+tempEnc.getMonth()+"-"+tempEnc.getDay()+" "+"01:00"+"</start_date>";
                  outputXML+="<end_date>"+tempEnc.getYear()+"-"+tempEnc.getMonth()+"-"+tempEnc.getDay()+" "+"01:01"+"</end_date>";
                  outputXML+="<text><![CDATA[No ID ("+sex+")]]></text>";
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
    myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
      

        out.println("</data>");
        out.close();
  }//end doPost

} //end class
  
  
