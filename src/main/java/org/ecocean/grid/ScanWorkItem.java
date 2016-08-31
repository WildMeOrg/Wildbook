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


import com.reijns.I3S.*;
import weka.core.Instance;
import org.ecocean.Encounter;
import org.ecocean.Spot;
import org.ecocean.SuperSpot;
import java.util.*;
import com.fastdtw.dtw.*;
import org.ecocean.grid.msm.*;
import weka.core.Utils;


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
  //private I3SMatchObject i3sResult;
  private Double fastDTWResult;
  private int totalWorkItemsInTask;
  private int workItemsCompleteInTask;
  
  String algorithms="";
  
  public boolean reversed=false;
  
  //SWALE tunings - default are early results for Physeter macrocephalus
  double swalePenalty=-2;
  double swaleReward=25.0;
  double swaleEpsilon=0.0011419401589504922;


  /**
   * empty constructor required by JDO Enhancer. DO NOT USE.
   */
  public ScanWorkItem() {
  }

  //test comment

  public ScanWorkItem(Encounter newEnc, Encounter existingEnc, String uniqueNum, String taskID, Properties props, String algorithms) {
    this.newEncounter = new EncounterLite(newEnc);
    this.existingEncounter = new EncounterLite(existingEnc);
    
    
    //if available, set the dates as long
    if(newEnc.getDateInMilliseconds()!=null){newEncounter.setDateLong(newEnc.getDateInMilliseconds());}
    if(existingEnc.getDateInMilliseconds()!=null){existingEncounter.setDateLong(existingEnc.getDateInMilliseconds());}
    
    
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
    this.algorithms=algorithms;

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
    
    //tuned on October 8, 2015 using /TrainHolmbergIntersection
    //double allowedHolmbergIntersectionProportion = 0.18;


 


    System.out.println("Now comparing new Encounter "+newEncounter.getEncounterNumber()+" vs existing encounter"+existingEncounter.getEncounterNumber());
       
    
    
    //start DTW array creation
    
    //com.reijns.I3S.Point2D[] newEncRefSpots=newEncounter.getThreeRightFiducialPoints();
    //com.reijns.I3S.Point2D[] existingEncRefSpots=existingEncounter.getThreeRightFiducialPoints();
    
    //we need to create a 0 to 1 time series for each one using the right hand spot
    

    
    
    
    MatchObject result=new MatchObject();
    if(!reversed){
      result.encounterNumber=existingEncounter.getEncounterNumber();
      result.individualName=existingEncounter.getIndividualID();
    }
    else{
      result.encounterNumber=newEncounter.getEncounterNumber();
      result.individualName=newEncounter.getIndividualID();
    }


    //adjust for scale
    double[] matrix = new double[6];
    com.reijns.I3S.Point2D[] comapare2mePoints = new com.reijns.I3S.Point2D[0];
    com.reijns.I3S.Point2D[] lookForThisEncounterPoints = new com.reijns.I3S.Point2D[0];
    I3SMatchObject newDScore=EncounterLite.improvedI3SScan(existingEncounter, newEncounter);
    System.out.println("Finished I3S method and I think the score is: "+newDScore.getI3SMatchValue());  
    //newDScore.setEncounterNumber(getNewEncNumber());
      //newDScore.setIndividualID(id);
      double newScore=weka.core.Utils.missingValue();
      System.out.println("I think newScore before setting is: "+newScore);
      //if(newDScore.getI3SMatchValue()>=0){
       if(newDScore.getI3SMatchValue()>=0){ newScore=newDScore.getI3SMatchValue();}
        System.out.println("I REALLY think my I3S score is: "+newScore);
        
        //
        //if(newScore<0.0000001){newScore=2.0;}
        
        //create a Vector of Points
        Vector points = new Vector();
        
        /*
        //TBD_CRAP WE NEED
        TreeMap map = newDScore.getMap();
        
        
        //int treeSize=map.size();
        Iterator map_iter = map.values().iterator();
        while (map_iter.hasNext()) {
          points.add((Pair) map_iter.next());
        }
        */
  
        //add the I3S results to the matchObject sent back
        result.setI3SValues(points, newScore);
     // }
      System.out.println("     I3S score is: "+newScore);
    //}
    
   // if(algorithms.indexOf("FastDTW")>-1){
      TimeWarpInfo twi=EncounterLite.fastDTW(existingEncounter, newEncounter, 30);
      
      java.lang.Double distance = new java.lang.Double(-1);
      if(twi!=null){
        WarpPath wp=twi.getPath();
          String myPath=wp.toString();
        distance=new java.lang.Double(twi.getDistance());
      }   
      
      result.setFastDTWPath(distance.toString());
      
      //calculate FastDTW
      //Double fastDTWResult = new Double(FastDTW.compare(ts1, ts2, 10, Distances.EUCLIDEAN_DISTANCE).getDistance());
      
      //if(rightScan){
        result.setRightFastDTWResult(distance);
      //}
      //else{
        result.setLeftFastDTWResult(distance);
      //}
      
      System.out.println("     FastDTW result is: "+distance);
      
      
      
      //set proportion Value
      //for dorsals use CRC method
      if(EncounterLite.isDorsalFin(existingEncounter)&&EncounterLite.isDorsalFin(newEncounter)){
        result.setProportionValue(EncounterLite.getCascadiaDorsalProportionsScore(existingEncounter, newEncounter));
      }
      //for flukes use simple width-height proportions
      else{
        result.setProportionValue(EncounterLite.getFlukeProportion(existingEncounter, newEncounter));
      }
      //set MSM value
      Double msmValue=new Double(weka.core.Utils.missingValue());
      Double potentialMMSMValue=MSM.getMSMDistance(existingEncounter, newEncounter);
      if(potentialMMSMValue!=null){
        msmValue=potentialMMSMValue;
      }
      System.out.println("     MSM result is: "+msmValue.doubleValue());
      result.setMSMSValue(msmValue);
      

      Double swaleValue=new Double(weka.core.Utils.missingValue());
      
      Double potentialSwaleValue=EncounterLite.getSwaleMatchScore(existingEncounter, newEncounter, swalePenalty, swaleReward, swaleEpsilon);
      if(potentialSwaleValue!=null){
        swaleValue=potentialSwaleValue;
      }
      System.out.println("     Swale result is: "+swaleValue.doubleValue());
      result.setSwaleValue(swaleValue);
      
      Double eucValue=new Double(weka.core.Utils.missingValue());
      Double potentialEucValue=EncounterLite.getEuclideanDistanceScore(existingEncounter, newEncounter);
      if(potentialEucValue!=null){eucValue=potentialEucValue;}
      System.out.println("     Euc. result is: "+swaleValue.doubleValue());
      result.setEuclideanDistanceValue(eucValue);
      
      
      double date = weka.core.Utils.missingValue();
      if((newEncounter.getDateLong()!=null)&&(existingEncounter.getDateLong()!=null)){
        try{
          date=Math.abs((new Long(newEncounter.getDateLong()-existingEncounter.getDateLong())).doubleValue());
        }
        catch(Exception e){
          e.printStackTrace();
        }
      }
      
      System.out.println("Date diff is: "+date);

      
      Double numIntersections=EncounterLite.getHolmbergIntersectionScore(existingEncounter, newEncounter);
      
      System.out.println("Intersection score is: "+numIntersections);
      
      result.setIntersectionCount(numIntersections);
      result.setAnglesOfIntersections("");
      result.setDateDiff(date);
      
    //patterningCode
      
      double pattCodeDiff = weka.core.Utils.missingValue();
      if((existingEncounter.getPatterningCode()!=null)&&(newEncounter.getPatterningCode()!=null)){
        String enc1Val=existingEncounter.getPatterningCode().replaceAll("[^\\d.]", "");
        String enc2Val=newEncounter.getPatterningCode().replaceAll("[^\\d.]", "");
        
        //at this point, we should have just numbers in the String
        try{
          double enc1code=(new Double(enc1Val)).doubleValue();
          double enc2code=(new Double(enc2Val)).doubleValue();
          pattCodeDiff=Math.abs(enc1code-enc2code);
          //System.out.println("Found a patterning code difference of: "+pattCodeDiff);
        }
        catch(Exception diffe){
          System.out.println("Found a potentially non-numeric-able patterning code on Encounter "+existingEncounter.getEncounterNumber()+" of "+existingEncounter.getPatterningCode());
          System.out.println("Found a potentially non-numeric-able patterning code on Encounter "+newEncounter.getEncounterNumber()+" of "+newEncounter.getPatterningCode());
          
          diffe.printStackTrace();
        }
        
      }
      result.setPatterningCodeDiffValue(pattCodeDiff);
      
      
   
      System.out.println("......Done SWI and returning  MO...");
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
  
  public Double getFastDTWResult(){return fastDTWResult;}
  
  public void setReversed(boolean myVal){this.reversed=myVal;}
  
  public void setSwalePenalty(double value){swalePenalty=value;}
  public void setSwaleEpsilon(double value){swaleEpsilon=value;}
  public void setSwaleReward(double value){swaleReward=value;}
  
}
	