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

//unenhanced comment

import org.ecocean.Shepherd;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Properties;
import java.util.Vector;

//import java.util.Iterator;
//import org.ecocean.encounter;

/**
 * A scanTask is...
 *
 * @author Jason Holmberg
 * @version 1.6
 *          more comments
 */
public class ScanTask implements Serializable {
  static final long serialVersionUID = -5003600247232167377L;
  private String uniqueNum = "";
  private boolean writeThis = true;
  private Vector workItems = new Vector();
  private Vector workResults = new Vector();
  private boolean isFinished = false;
  private String submitter = "";
  private boolean deleteOnFinish = true;
  private String user = "Unknown";
  private Vector nodeIdentifiers = new Vector();
  private Vector nodeLocations = new Vector();
  private Vector nodeTimes = new Vector();
  private long startTime = -1;
  private long endTime = -1;
  private int numSpots = 0;
  private int numCollisions = 0;
  private boolean started = false;
  private int numComparisons = Integer.MAX_VALUE;


  /**
   * empty constructor required by JDO Enhancer. DO NOT USE.
   */
  public ScanTask() {
  }

  /**
   * Use this constructor for new Groth scanTasks.
   * Note: Input variable props must contain values for the following keys:
   * <ul><li>epsilon</li>
   * <li>R</li>
   * <li>maxTriangleRotation</li>
   * <li>Sizelim</li>
   * <li>C</li>
   * <li>side</li>
   * <li>getWorkItemURL</li>
   * <li>sendResultsURL</li>
   * </ul>
   */
  public ScanTask(Shepherd myShepherd, String uniqueNum, Properties props, String encounter2scan, boolean writeThis) {

    this.isFinished = false;
    this.uniqueNum = uniqueNum;
    this.writeThis = writeThis;

    //startTime = System.currentTimeMillis();
  }

  /**
   * Returns whether all of the work of this Task has been completed by remote nodes.
   */
  public boolean hasFinished() {
    return isFinished;
  }

  public void setFinished(boolean finish) {
    isFinished = finish;
  }

  public void setEndTime(long end) {
    endTime = end;
  }

  //returns the number of spots in the encounter to look for
  //this is generally used grid load balancing
  public int getNumSpots() {
    return numSpots;
  }

  //public scanWorkItem[] getWorkItems() {
  //scanWorkItem[] swi_array=new scanWorkItem[1];
  //swi_array=((scanWorkItem[])workItems.toArray(swi_array));
  //return swi_array;
  //}

  /*public matchObject[] getResults() {
      matchObject[] swir_array=new matchObject[getNumWorkItems()];
      scanWorkItem[] swi_array=getWorkItems();
      int length=swi_array.length;
      for(int i=0;i<length;i++) {
          swir_array[i]=swi_array[i].getResult();
      }

      return swir_array;
  }*/

  public boolean getWriteThis() {
    return writeThis;
  }

  public void addWorkItem(ScanWorkItem swi) {
    workItems.add(swi);
    if (numSpots == 0) {
      EncounterLite el = swi.getNewEncounterLite();
      if (swi.isRightScan()) {
        numSpots = el.getRightSpots().size();
      } else {
        numSpots = el.getSpots().size();
      }
    }
    numComparisons++;
  }

  public void setWorkItems(Vector swis) {
    this.workItems = swis;
    numComparisons=swis.size();
    ScanWorkItem swi = (ScanWorkItem) swis.get(0);
    if (numSpots == 0) {
      EncounterLite el = swi.getNewEncounterLite();
      if (swi.isRightScan()) {
        numSpots = el.getRightSpots().size();
      } else {
        numSpots = el.getSpots().size();
      }
    }
  }

  public ScanWorkItem getWorkItem(String uniqueNum) {
    int size = workItems.size();
    for (int i = 0; i < size; i++) {
      ScanWorkItem wi = (ScanWorkItem) workItems.get(i);
      if (wi.getUniqueNumber().equals(uniqueNum)) {
        return wi;
      }
    }
    return null;
  }

  //public scanWorkItem getFirstWorkItem() {
  //scanWorkItem wi=(scanWorkItem)workItems.get(0);
  //return wi;
  //}

  /*public scanWorkItem getNextWorkItem(long timeoutMilliseconds){
      int i=0;
      //int unfinished=-1;
      int size=workItems.size();
      while(i<size) {
          scanWorkItem wi=(scanWorkItem)workItems.get(i);

          //check for conditions to grab this item
          if((!wi.isDone())&&(!wi.isCheckedOut(timeoutMilliseconds))) {

              //let's check this work item out and return it
              wi.setStartTime(System.currentTimeMillis());
              return wi;
          }
          i++;
      }
      return null;
  }*/

  //use this method for returning groupings of workItems for more efficient processing
  public Vector getNextWorkItemGroup(long timeoutMilliseconds, int groupSize) {
    int i = 0;
    int numAddedToGroup = 0;
    int size = workItems.size();
    Vector group = new Vector();
    while ((i < size) && (numAddedToGroup < groupSize)) {
      ScanWorkItem wi = (ScanWorkItem) workItems.get(i);

      //check for conditions to grab this item
      if ((!wi.isDone()) && (!wi.isCheckedOut(timeoutMilliseconds))) {


        long time = System.currentTimeMillis();
        //see if we should set the start time of the task
        if (!hasStarted()) {
          startTime = time;
          setStarted(true);
        }

        //let's check this work item out and return it
        wi.setStartTime(time);
        numAddedToGroup++;
        group.add(wi);
      }
      i++;
    }
    return group;
  }

  public void addResult(ScanWorkItemResult wir) {
    //boolean found=false;
    //check to make sure no duplicates exist
    String wiNum = wir.getUniqueNumberWorkItem();
    ScanWorkItem swi = getWorkItem(wiNum);
    if (!swi.isDone()) {
      swi.setResult(wir.getResult());
      swi.setDone(true);

      //we also need to set this task to finished if appropriate
      if (workItems.size() == getNumWorkItemsComplete()) {
        setFinished(true);
        long time = System.currentTimeMillis();
        endTime = time;
      }

    }
    //if this workItem is already done, record a collision for later use in optimization analysis
    else {
      numCollisions++;
    }
  }

  public void reportCollision() {
    numCollisions++;
  }

  ;

  public int getNumWorkItemsComplete() {
    int size = workItems.size();
    int numComplete = 0;
    for (int i = 0; i < size; i++) {
      ScanWorkItem wi = (ScanWorkItem) workItems.get(i);
      if (wi.isDone()) numComplete++;
    }
    return numComplete;
  }

  public double getPercentComplete() {
    return (getNumWorkItemsComplete() / workItems.size() * 100);
  }

  public int getNumWorkItems() {
    return workItems.size();
  }

  //public int getNumWorkItemResults() {
  //	return workResults.size();
  //}

  /**
   * Returns the unique number for this workItem.
   */
  public String getUniqueNumber() {
    return uniqueNum;
  }

  public String getSubmitter() {
    return submitter;
  }

  public void setSubmitter(String submitter) {
    this.submitter = submitter;
  }

  public void setNumComparisons(int num) {
    this.numComparisons = num;
  }

  public int getNumComparisons() {
    return numComparisons;
  }

  public boolean getDeleteOnFinish() {
    return deleteOnFinish;
  }

  public void setDeleteOnFinish(boolean deleteOnFinish) {
    this.deleteOnFinish = deleteOnFinish;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public void addNode(String nodeID, String nodeLocation) {
    if (!nodeIdentifiers.contains(nodeID)) {
      nodeIdentifiers.add(nodeID);
      nodeLocations.add(nodeLocation);
      nodeTimes.add(Calendar.getInstance().getTimeInMillis());
    } else {
      //set the last update time of the node
      int position = nodeIdentifiers.indexOf(nodeID);
      nodeTimes.set(position, Calendar.getInstance().getTimeInMillis());
    }
  }

  public int getNumContributingNodes() {
    return nodeIdentifiers.size();
  }

  public int getNumCollisions() {
    return numCollisions;
  }

  public Vector getNodeIdentifiers() {
    return nodeIdentifiers;
  }

  public Vector getNodeLocations() {
    return nodeLocations;
  }

  public Vector getNodeTimes() {
    return nodeTimes;
  }

  public long getProcessingTime() {
    long totalTime = endTime - startTime;
    if (totalTime < 0) {
      return 0;
    }
    return totalTime;
  }

  public long getStartTime() {
    return startTime;
  }

  public long getEndTime() {
    return endTime;
  }

  public boolean hasStarted() {
    return started;
  }

  public void setStarted(boolean value) {
    started = value;
  }


}