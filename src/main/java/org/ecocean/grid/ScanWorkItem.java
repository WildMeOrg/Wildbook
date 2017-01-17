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

//test
//.test 2
//test 3


import com.reijns.I3S.Pair;
import org.ecocean.Encounter;
import org.ecocean.Spot;
import org.ecocean.SuperSpot;

import java.util.*;


/**
 * A class description...
 * More description
 *
 * @author Jason Holmberg
 * @version 1.6
 */
//public class scanWorkItem extends JPPFTask implements java.io.Serializable{
public class ScanWorkItem implements java.io.Serializable {
  static final long serialVersionUID = 1325165653077808498L;
  private EncounterLite newEncounter;
  private EncounterLite existingEncounter;
  private String uniqueNum, taskID;
  private long startTime = -1;
  private long createTime = -1;
  private boolean done;
  private Hashtable props = new Hashtable();
  private int nice = 0;
  public Double epsilon;
  public Double R;
  public Double Sizelim;
  public Double maxTriangleRotation;
  public Double C;
  private boolean secondRun;
  public boolean rightScan;
  private MatchObject result;
  private I3SMatchObject i3sResult;
  private int totalWorkItemsInTask;
  private int workItemsCompleteInTask;


  /**
   * empty constructor required by JDO Enhancer. DO NOT USE.
   */
  public ScanWorkItem() {
  }

  //test comment

  public ScanWorkItem(Encounter newEnc, Encounter existingEnc, String uniqueNum, String taskID, Properties props) {
    this.newEncounter = new EncounterLite(newEnc);
    this.existingEncounter = new EncounterLite(existingEnc);
    this.uniqueNum = uniqueNum;
    this.taskID = taskID;

    //algorithm parameter read-ins
    this.epsilon = new Double(props.getProperty("epsilon"));
    this.R = new Double(props.getProperty("R"));
    this.Sizelim = new Double(props.getProperty("Sizelim"));
    this.maxTriangleRotation = new Double(props.getProperty("maxTriangleRotation"));
    this.C = new Double(props.getProperty("C"));

    //boolean read-ins
    this.secondRun = true;
    String secondRunString = (String) props.get("secondRun");
    if (secondRunString.equals("false")) {
      secondRun = false;
    }
    this.rightScan = false;
    String rightScanString = (String) props.get("rightScan");
    if (rightScanString.equals("true")) {
      rightScan = true;
    }

    createTime = System.currentTimeMillis();

  }
  
  public ScanWorkItem(EncounterLite newEnc, EncounterLite existingEnc, String uniqueNum, String taskID, Properties props) {
    this.newEncounter = newEnc;
    this.existingEncounter = existingEnc;
    this.uniqueNum = uniqueNum;
    this.taskID = taskID;

    //algorithm parameter read-ins
    this.epsilon = new Double(props.getProperty("epsilon"));
    this.R = new Double(props.getProperty("R"));
    this.Sizelim = new Double(props.getProperty("Sizelim"));
    this.maxTriangleRotation = new Double(props.getProperty("maxTriangleRotation"));
    this.C = new Double(props.getProperty("C"));

    //boolean read-ins
    this.secondRun = true;
    String secondRunString = (String) props.get("secondRun");
    if (secondRunString.equals("false")) {
      secondRun = false;
    }
    this.rightScan = false;
    String rightScanString = (String) props.get("rightScan");
    if (rightScanString.equals("true")) {
      rightScan = true;
    }

    createTime = System.currentTimeMillis();

  }

  //public scanWorkItemResult getResult(){
  //return result;
  //}

  public String getNewEncNumber() {
    return newEncounter.getEncounterNumber();
  }

  public String getExistingEncNumber() {
    return existingEncounter.getEncounterNumber();
  }


  /**
   * Returns true if a node is currently working on this object. This state times out after 60 seconds.
   */
  public boolean isCheckedOut(long millisecondsToWait) {
    if (getStartTime() > -1) {
      long currentTime = Calendar.getInstance().getTimeInMillis();
      if ((currentTime - getStartTime()) > millisecondsToWait) {
        return false;
      } else {
        return true;
      }
    } else {
      return false;
    }
  }

  public void run() {
    setResult(execute());
  }


  /**
   * Executes the work to be done on the remote node.
   * Make sure to setDone() when execute has completed successfully.
   */
  public MatchObject execute() {


    //determine which spots to pass in
    SuperSpot[] newspotsTemp = new SuperSpot[0];
    SuperSpot[] oldspotsTemp = new SuperSpot[0];
    if (!rightScan) {
      newspotsTemp = (SuperSpot[]) newEncounter.getSpots().toArray(newspotsTemp);
      oldspotsTemp = (SuperSpot[]) existingEncounter.getSpots().toArray(oldspotsTemp);
    } else {
      newspotsTemp = (SuperSpot[]) newEncounter.getRightSpots().toArray(newspotsTemp);
      oldspotsTemp = (SuperSpot[]) existingEncounter.getRightSpots().toArray(oldspotsTemp);
    }

    //create a re-write of the new spots
    ArrayList<SuperSpot> newGrothSpots = new ArrayList<SuperSpot>();
    int spotLength = newspotsTemp.length;
    for (int t = 0; t < spotLength; t++) {
      newGrothSpots.add(new SuperSpot(new Spot(0, newspotsTemp[t].getTheSpot().getCentroidX(), newspotsTemp[t].getTheSpot().getCentroidY())));
    }

    //create a re-write of the old spots
    ArrayList<SuperSpot> existingGrothSpots = new ArrayList<SuperSpot>();
    int spotLength2 = oldspotsTemp.length;
    for (int t = 0; t < spotLength2; t++) {
      existingGrothSpots.add(new SuperSpot(new Spot(0, oldspotsTemp[t].getTheSpot().getCentroidX(), oldspotsTemp[t].getTheSpot().getCentroidY())));
    }


    MatchObject result = existingEncounter.getPointsForBestMatch(newspotsTemp, epsilon.doubleValue(), R.doubleValue(), Sizelim.doubleValue(), maxTriangleRotation.doubleValue(), C.doubleValue(), secondRun, rightScan);

    //I3S processing

    //reset the spot patterns after Groth processing
    if (!rightScan) {
      newEncounter.processLeftSpots(newGrothSpots);
      existingEncounter.processLeftSpots(existingGrothSpots);
    } else {
      newEncounter.processRightSpots(newGrothSpots);
      existingEncounter.processRightSpots(existingGrothSpots);
    }

    //adjust for scale
    double[] matrix = new double[6];
    com.reijns.I3S.Point2D[] comapare2mePoints = new com.reijns.I3S.Point2D[0];
    com.reijns.I3S.Point2D[] lookForThisEncounterPoints = new com.reijns.I3S.Point2D[0];
    //if(rightScan){
    //comapare2mePoints=existingEncounter.getThreeRightFiducialPoints();
    //lookForThisEncounterPoints=newEncounter.getThreeRightFiducialPoints();
    //}
    //else {
    //comapare2mePoints=existingEncounter.getThreeLeftFiducialPoints();
    //lookForThisEncounterPoints=newEncounter.getThreeLeftFiducialPoints();
    //}
    i3sResult = existingEncounter.i3sScan(newEncounter, rightScan);

    //create a Vector of Points
    Vector points = new Vector();
    TreeMap map = i3sResult.getMap();
    //int treeSize=map.size();
    Iterator map_iter = map.values().iterator();
    while (map_iter.hasNext()) {
      points.add((Pair) map_iter.next());
    }

    //add the I3S results to the matchObject sent back
    result.setI3SValues(points, i3sResult.getI3SMatchValue());


    done = true;
    return result;
  }

  /**
   * Returns the unique number for this workItem.
   */
  public String getUniqueNumber() {
    return uniqueNum;
  }


  public String getTaskIdentifier() {
    return taskID;
  }


  /**
   * Returns the startTime of the workItem. This value is used to determine timeouts.
   */
  public long getStartTime() {
    return startTime;
  }

  ;

  /**
   * Sets the startTime of this workItem. This value is used to determine timeouts.
   */
  public void setStartTime(long newStartTime) {
    this.startTime = newStartTime;
  }


  public boolean isDone() {
    return done;
  }

  public void setDone(boolean finished) {
    this.done = finished;
  }


  //returns the priority of this task
  public int getNice() {
    return nice;
  }

  //sets the nice value for this task
  public void setNice(int nice) {
    this.nice = nice;
  }

  public MatchObject getResult() {
    return result;
  }

  public I3SMatchObject getI3SResult() {
    return i3sResult;
  }

  public void setResult(MatchObject newResult) {
    newResult.setTaskID(this.taskID);
    newResult.setWorkItemUniqueNumber(this.uniqueNum);
    result = newResult;
  }

  public EncounterLite getNewEncounterLite() {
    return newEncounter;
  }

  public EncounterLite getExistingEncounterLite() {
    return existingEncounter;
  }

  public long getCreateTime() {
    return createTime;
  }

  public int getTotalWorkItemsInTask() {
    return totalWorkItemsInTask;
  }

  public void setTotalWorkItemsInTask(int num) {
    totalWorkItemsInTask = num;
  }

  public int getWorkItemsCompleteInTask() {
    return workItemsCompleteInTask;
  }

  public void setWorkItemsCompleteInTask(int num) {
    workItemsCompleteInTask = num;
  }

  public boolean isRightScan() {
    return rightScan;
  }

  public void setExistingEncounter(EncounterLite el) {
    this.existingEncounter = el;
  }

  public void setNewEncounter(EncounterLite el) {
    this.newEncounter = el;
  }
}
	