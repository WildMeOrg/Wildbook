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
import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;

import java.util.Vector;


public class TuningTaskCreationThread implements Runnable, ISharkGridThread {

  public Thread threadCreationObject;
  public boolean rightSide = false;
  public boolean writeThis = true;
  public String taskID = "";
  java.util.Properties props2 = new java.util.Properties();
  boolean finished = false;
  GridManager gm;
  int maxNumWorkItems = 99999999;
  int numAdded = 0;
  private String context="context0";

  /**
   * Constructor to create a new thread object
   */
  public TuningTaskCreationThread(String taskID, boolean writeThis, int maxNumWorkItems,String context) {
    this.taskID = taskID;
    this.writeThis = writeThis;
    gm = GridManagerFactory.getGridManager();
    threadCreationObject = new Thread(this, ("scanWorkItemCreation_" + taskID));
    this.maxNumWorkItems = maxNumWorkItems;
    this.context=context;
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
    myShepherd.setAction("TuningTaskCreationThread");
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
    props2.setProperty("epsilon", "0.01");
    props2.setProperty("R", "8");
    props2.setProperty("Sizelim", "0.85");
    props2.setProperty("maxTriangleRotation", "10");
    props2.setProperty("C", "0.99");
    props2.setProperty("secondRun", secondRun);
    props2.setProperty("rightScan", rightScan);

    myShepherd.beginDBTransaction();
    Vector<String> newSWIs = new Vector<String>();
    Vector<ScanWorkItem> addThese = new Vector<ScanWorkItem>();
    System.out.println("Successfully created the scanTask shell!");
    //now, add the workItems
    myShepherd.beginDBTransaction();
    try {

      int count = 0;

      Vector leftSharks = myShepherd.getPossibleTrainingIndividuals();
      int numLeftSharks = leftSharks.size();
      Vector rightSharks = myShepherd.getRightPossibleTrainingIndividuals();
      int numRightSharks = rightSharks.size();


      for (int i = 0; i < numLeftSharks; i++) {
        //System.out.println("Iterating encounters to create scanWorkItems...");
        MarkedIndividual s = (MarkedIndividual) leftSharks.get(i);
        //String encounterNumber="";
        Vector encs = s.getTrainableEncounters();
        int numTrainable = encs.size();
        for (int j = 0; j < (numTrainable - 1); j++) {
          Encounter enc = (Encounter) encs.get(j);
          for (int k = (j + 1); k < numTrainable; k++) {
            Encounter enc2 = (Encounter) encs.get(k);
            String wiIdentifier = taskID + "_" + (new Integer(count)).toString();
            //add the workItem
            ScanWorkItem swi = new ScanWorkItem(enc, enc2, wiIdentifier, taskID, props2);
            //String uniqueNum=swi.getUniqueNumber();

            //change
            //newSWIs.add(uniqueNum);

            if (numAdded < maxNumWorkItems) {
              gm.addWorkItem(swi);
              numAdded++;
            }


            //change
            //addThese.add(swi);
            //System.out.println("Added a new left-side training task!");


            count++;
          }

        }
        //if((count % 10 == 0)||(k==(numTrainable-1))){
        //System.out.println("Trying to commit the add of the scanWorkItems");

        //change
        //myShepherd.getPM().makePersistentAll(addThese);

        myShepherd.commitDBTransaction();
        addThese = new Vector();
        myShepherd.beginDBTransaction();
        //}
      }

      //reset the counter
      numAdded = 0;

      for (int i = 0; i < numRightSharks; i++) {
        //System.out.println("Iterating encounters to create scanWorkItems...");
        MarkedIndividual s = (MarkedIndividual) rightSharks.get(i);
        //String encounterNumber="";
        Vector encs = s.getRightTrainableEncounters();
        int numTrainable = encs.size();
        for (int j = 0; j < numTrainable; j++) {
          Encounter enc = (Encounter) encs.get(j);
          for (int k = (j + 1); k < numTrainable; k++) {
            Encounter enc2 = (Encounter) encs.get(k);
            String wiIdentifier = taskID + "_" + (new Integer(count)).toString();
            //add the workItem
            ScanWorkItem swi = new ScanWorkItem(enc, enc2, wiIdentifier, taskID, props2);
            swi.rightScan = true;

            if (numAdded < maxNumWorkItems) {
              gm.addWorkItem(swi);
              numAdded++;
            }


            //String uniqueNum=swi.getUniqueNumber();

            //change
            //newSWIs.add(uniqueNum);
            //addThese.add(swi);

            System.out.println("Added a new right-side training task!");
            count++;
          }

        }
        //if((count % 10 == 0)||(k==(numTrainable-1))){
        System.out.println("Trying to commit the add of the scanWorkItems");

        //change
        //myShepherd.getPM().makePersistentAll(addThese);
        myShepherd.commitDBTransaction();
        addThese = new Vector();
        myShepherd.beginDBTransaction();
        //}
      }


      //myShepherd.getPM().makePersistentAll(addThese);
      System.out.println("Trying to commit the add of the Tuning Task scanWorkItems after leaving loop");
      myShepherd.commitDBTransaction();
      myShepherd.closeDBTransaction();
      finished = true;
    } catch (Exception e) {
      System.out.println("I failed while constructing the workItems for a new scanTask.");
      e.printStackTrace();
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }

  }


}