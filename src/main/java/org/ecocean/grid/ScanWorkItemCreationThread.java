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
//import org.ecocean.Occurrence;
import org.ecocean.Shepherd;

import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;

import javax.jdo.Query;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Enumeration;


public class ScanWorkItemCreationThread implements Runnable, ISharkGridThread {

  public Thread threadCreationObject;
  public boolean rightSide = false;
  public boolean writeThis = true;
  public String taskID = "";
  public String encounterNumber = "";
  java.util.Properties props2 = new java.util.Properties();
  boolean finished = false;
  GridManager gm;
  String context="context0";
  String jdoql="SELECT FROM org.ecocean.Encounter";

  /**
   * Constructor to create a new thread object
   */
  public ScanWorkItemCreationThread(String taskID, boolean rightSide, String encounterNum, boolean writeThis, String context, String jdoql) {
    this.taskID = taskID;
    this.writeThis = writeThis;
    this.rightSide = rightSide;
    this.encounterNumber = encounterNum;
    gm = GridManagerFactory.getGridManager();
    threadCreationObject = new Thread(this, ("scanWorkItemCreation_" + taskID));
    this.context=context;
    
    if((jdoql!=null)&&(!jdoql.trim().equals(""))){
      this.jdoql=jdoql;
    }
    /*
    else if(rightSide){
      jdoql="SELECT FROM org.ecocean.Encounter WHERE numSpotsRight > 0";
    }
    */
 
    
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
    //Shepherd myShepherd = new Shepherd(context);
    //myShepherd.setAction("ScanWorkItemCreationThread.class");
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


    
    Vector<String> newSWIs = new Vector<String>();
    Vector<ScanWorkItem> addThese = new Vector<ScanWorkItem>();
    //System.out.println("Successfully created the scanTask shell!");
    //myShepherd.beginDBTransaction();
    //EncounterLite baseEnc=new EncounterLite(myShepherd.getEncounter(encounterNumber));
    EncounterLite baseEnc=gm.getMatchGraphEncounterLiteEntry(encounterNumber);
    //myShepherd.rollbackDBTransaction();
    //now, add the workItems
    //myShepherd.beginDBTransaction();
    Query query=null;
    try {
      //Iterator encounters = myShepherd.getAllEncountersNoQuery();
      
      //query=myShepherd.getPM().newQuery(jdoql);
      //Collection c = (Collection) (query.execute());
      //System.out.println("Num scans to do: "+c.size());
      //Iterator encounters = c.iterator();

      ConcurrentHashMap<String,EncounterLite> chm= gm.getMatchGraph();
      Enumeration<String> keys=chm.keys();
     
      int count = 0;

      while (keys.hasMoreElements()) {
        //System.out.println("     Iterating encounters to create scanWorkItems...");
        String kv=keys.nextElement();
        EncounterLite el=chm.get(kv);
        //Encounter enc = (Encounter) encounters.next();
        if (!kv.equals(encounterNumber)) {
          String wiIdentifier = taskID + "_" + (new Integer(count)).toString();
          if (rightSide && (el.getRightSpots() != null) && (el.getRightSpots().size() > 0)) {
            //add the workItem
            ScanWorkItem swi = new ScanWorkItem(baseEnc, el, wiIdentifier, taskID, props2);
            //String uniqueNum = swi.getUniqueNumber();

            gm.addWorkItem(swi);

            //System.out.println("Added a new right-side scan task!");
            count++;
          } 
          else if (!rightSide && (el.getSpots() != null) && (el.getSpots().size() > 0)) {
            //add the workItem
            ScanWorkItem swi = new ScanWorkItem(baseEnc, el, wiIdentifier, taskID, props2);

            //String uniqueNum = swi.getUniqueNumber();


            gm.addWorkItem(swi);
            //System.out.println("Added a new left-side scan task: " + count);
            count++;
          }
        }

      }


      //System.out.println("Trying to commit the add of the scanWorkItems after leaving loop");
      //myShepherd.rollbackDBTransaction();
      
      finished = true;
    } 
    catch (Exception e) {
      System.out.println("I failed while constructing the workItems for a new scanTask.");
      e.printStackTrace();
      //myShepherd.rollbackDBTransaction();
      
    }
    finally{
      if(query!=null){query.closeAll();}
      //myShepherd.closeDBTransaction();
    }

  }


}