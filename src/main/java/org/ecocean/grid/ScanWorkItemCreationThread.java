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

package org.ecocean.grid;

import org.ecocean.Encounter;
import org.ecocean.CommonConfiguration;
import org.ecocean.Shepherd;
import org.ecocean.Util;
import org.ecocean.identity.IBEISIA;
import org.ecocean.servlet.ServletUtilities;

import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;
import java.util.ArrayList;

import javax.jdo.Query;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.net.URISyntaxException;


public class ScanWorkItemCreationThread implements Runnable, ISharkGridThread {

  public Thread threadCreationObject;
  public boolean rightSide = false;
  public boolean writeThis = true;
  public String taskID = "";
  public String encounterNumber = "";
  java.util.Properties props2 = new java.util.Properties();
  boolean finished = false;
  GridManager gm;
    ServletContext sctx;
    String baseUrl = "http://unknown-url.example.com";
  String context="context0";
  String jdoql="SELECT FROM org.ecocean.Encounter";
  String algorithms="";
  String genus="";
  String species="";

  /**
   * Constructor to create a new thread object
   */
  public ScanWorkItemCreationThread(String taskID, boolean rightSide, String encounterNum, boolean writeThis, String context, String jdoql, String genus, String species, ServletContext sctx, HttpServletRequest request) {
    this.taskID = taskID;
    this.writeThis = writeThis;
    this.rightSide = rightSide;
    this.encounterNumber = encounterNum;
    this.sctx = sctx;
    gm = GridManagerFactory.getGridManager();
    threadCreationObject = new Thread(this, ("scanWorkItemCreation_" + taskID));
    this.context=context;
    try {
        baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
    } catch (URISyntaxException ex) {
        System.out.println("ScanWorkItemCreationThread() failed to obtain baseUrl: " + ex.toString());
    }
System.out.println("baseUrl --> " + baseUrl);
    
    if((jdoql!=null)&&(!jdoql.trim().equals(""))){
      this.jdoql=jdoql;
    }
    if(CommonConfiguration.getProperty("algorithms", context)!=null){
      algorithms=CommonConfiguration.getProperty("algorithms", context);
    }
    
    
    if(genus!=null){this.genus=genus;}
    if(species!=null){this.species=species;}
  }


  /**
   * main method of the shepherd thread
   */
  public void run() {
    createThem();
  }

  public boolean isFinished() {
    return finished;
  }


  public void createThem() {
    Shepherd myShepherd = new Shepherd(context);
    GridManager gm = GridManagerFactory.getGridManager();

    String secondRun = "true";
    String rightScan = "false";
    boolean writeThis = true;
    if (writeThis) {
      writeThis = false;
    }
    if (rightSide) {
      rightScan = "true";
    }
    props2.setProperty("rightScan", rightScan);
    
    

    
    


    //Modified Groth algorithm parameters
    //pulled from the gridManager
    props2.setProperty("epsilon", gm.getGrothEpsilon());
    props2.setProperty("R", gm.getGrothR());
    props2.setProperty("Sizelim", gm.getGrothSizelim());
    props2.setProperty("maxTriangleRotation", gm.getGrothMaxTriangleRotation());
    props2.setProperty("C", gm.getGrothC());
    props2.setProperty("secondRun", gm.getGrothSecondRun());


    myShepherd.beginDBTransaction();
    
    Encounter originalEnc=myShepherd.getEncounter(encounterNumber);
    Vector<String> newSWIs = new Vector<String>();
    Vector<ScanWorkItem> addThese = new Vector<ScanWorkItem>();
    System.out.println("Successfully created the scanTask shell!");
    //now, add the workItems
    myShepherd.beginDBTransaction();
    //Query query=null;
    Collection c=null;
    try {
      
/*  NOTE: our new way to find suitable encounters is below -- TODO this needs to account for empty genus & species!!!
      if(genus.equals("")){
        query=myShepherd.getPM().newQuery(jdoql);
        c = (Collection) (query.execute());
      }
      else{
        //c= myShepherd.getAllEncountersForSpeciesWithSpots(genus, species);
        String keywordQueryString="SELECT FROM org.ecocean.Encounter WHERE spots != null && genus == '"+genus+"' && specificEpithet == '"+species+"'";
        query=myShepherd.getPM().newQuery(keywordQueryString);
        c = (Collection) (query.execute());
      }
      //System.out.println("Num scans to do: "+c.size());
      Iterator encounters = c.iterator();
*/
      
        ArrayList<Encounter> encounters = Encounter.getEncountersForMatching(Util.taxonomyString(genus, species), myShepherd);

        //we kick of IBEIS first, so it has (plenty of!) time to finish
        ArrayList<Encounter> qencs = new ArrayList<Encounter>();
        qencs.add(myShepherd.getEncounter(encounterNumber));
        ArrayList<Encounter> tencs = new ArrayList<Encounter>();  //all the other encounters
        for (Encounter enc : encounters) {
            if (!enc.getEncounterNumber().equals(encounterNumber)) tencs.add(enc);
        }
//System.out.println("qencs = " + qencs);
//System.out.println("tencs = " + tencs);
        IBEISIA.beginIdentify(qencs, tencs, myShepherd, Util.taxonomyString(genus, species), taskID, baseUrl, context);

      
    //iterate thru again now for the other matching algorithms
      int count = 0;
    for (Encounter enc : encounters) {
        System.out.println("     Iterating encounters to create scanWorkItems [" + enc.getEncounterNumber() + "  " + count + "/" + encounters.size() + "] ...");
/*
if (count > 20) {
    count++;
    continue;
}
*/
        
        //TBD- ok, for now we're going to hardcode the check for species here
        
        if (!enc.getEncounterNumber().equals(encounterNumber)) {
          //if((enc.getSpots()!=null)&&(enc.getSpots().size()>0)&&(enc.getRightSpots()!=null)&&(enc.getRightSpots().size()>0)){
          
            /*  
            String encGenusSpecies="unknown";
              String originalEncGenusSpecies="unknown2";
              if((originalEnc.getGenus()!=null)&&(enc.getGenus()!=null)){
                if((originalEnc.getSpecificEpithet()!=null)&&(enc.getSpecificEpithet()!=null)){
                  encGenusSpecies=enc.getGenus()+enc.getSpecificEpithet();
                  originalEncGenusSpecies=originalEnc.getGenus()+originalEnc.getSpecificEpithet();
                }
              }
              */
              //if(encGenusSpecies.equals(originalEncGenusSpecies)){
                
                
                //tunings for the SWALE algorithm - default is Physetermacrocephalus
                double swalePenalty=-2;
                double swaleReward=25.0;
                double swaleEpsilon=0.0011419401589504922;
                //Swale value for Tursiopstruncatus
                //if(encGenusSpecies.equals("Tursiopstruncatus")){
                  swalePenalty=0;
                  swaleReward=25.0;
                  swaleEpsilon=0.003977041051268339;
                //}
                
              
                String wiIdentifier = taskID + "_" + (new Integer(count)).toString();
                //if (rightSide && (enc.getRightSpots() != null) && (enc.getRightSpots().size() > 0)) {
                  //add the workItem
                  ScanWorkItem swi = new ScanWorkItem(myShepherd.getEncounter(encounterNumber), enc, wiIdentifier, taskID, props2, algorithms);
                  swi.setSwaleEpsilon(swaleEpsilon);
                  swi.setSwalePenalty(swalePenalty);
                  swi.setSwaleReward(swaleReward);
      
                  gm.addWorkItem(swi);
                  count++;
                  
                  /*
                  //scan the reverse as well
                  System.out.println("     I am creating an inverse ScanWorkItem!");
                  ScanWorkItem swi2 = new ScanWorkItem(enc,myShepherd.getEncounter(encounterNumber), (wiIdentifier+"Revere"), taskID, props2, algorithms);
                  swi2.setReversed(true);
                  swi2.setSwaleEpsilon(swaleEpsilon);
                  swi2.setSwalePenalty(swalePenalty);
                  swi2.setSwaleReward(swaleReward);
                  gm.addWorkItem(swi2);
      
                  //System.out.println("Added a new right-side scan task!");
                  count++;
                  */
                
              //} 
          //}     
        }

      }
      
      ScanTask st=myShepherd.getScanTask(taskID);
      st.setNumComparisons(count);


/*
        String rootDir = sctx.getRealPath("/");
        String baseDir = ServletUtilities.dataDir(context, rootDir);
*/

      //System.out.println("Trying to commit the add of the scanWorkItems after leaving loop");
      myShepherd.commitDBTransaction();
      myShepherd.closeDBTransaction();
      finished = true;
    } 
    catch (Exception e) {
      System.out.println("I failed while constructing the workItems for a new scanTask.");
      e.printStackTrace();
      
    }
    finally{
      //if(query!=null){query.closeAll();}
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }

  }


}
