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

import java.util.TreeMap;
import weka.core.Utils;
//import com.reijns.I3S.*;

/**
 * matchObject is a temporary object used by the <code>shepherd</code> class to store unordered match values
 *
 * @author jholmber
 */
public class I3SMatchObject implements java.io.Serializable {
  static final long serialVersionUID = 9122107217335010239L;
  public String individualName = "N/A", date="";
  public double matchValue=-1, size=-1;
  public String encounterNumber = "N/A";
  private TreeMap map=new TreeMap();;
  public String newSex = "Unknown", catalogSex = "Unknown";


  public I3SMatchObject() {
  }

  public I3SMatchObject(double score, TreeMap map) {
    this.matchValue = score;
    this.individualName = individualName;
    this.map = map;
    this.encounterNumber = encounterNumber;
    //this.catalogSex = catalogSex;
    //this.date = date;
    this.size = size;
    //this.mahalSum=mahalSum;
    //this.fastDTWResult=fastDTWDistance;
    //this.geroMatchDistance=geroMatchDistance;
  }

  public TreeMap getMap() {
    return map;
  }

  public double getI3SMatchValue() {
    return matchValue;
  }

  /**
   * DONT NEED THESE - I think...
   * public double getRightmostSharkSpot() {
   * double greatest=0;
   * for (int i=0;i<scores.length;i++) {
   * <p/>
   * if (scores[i].oldX>greatest) {greatest=scores[i].oldX;}
   * <p/>
   * }
   * return greatest;
   * }
   * <p/>
   * public double getHighestSharkSpot() {
   * double greatest=0;
   * for (int i=0;i<scores.length;i++) {
   * <p/>
   * if (scores[i].oldY>greatest) {greatest=scores[i].oldY;}
   * }
   * return greatest;
   * }
   * <p/>
   * public double getRightmostEncounterSpot() {
   * double greatest=0;
   * for (int i=0;i<scores.length;i++) {
   * if (scores[i].newX>greatest) {greatest=scores[i].newX;}
   * }
   * return greatest;
   * }
   * <p/>
   * public double getHighestEncounterSpot() {
   * double greatest=0;
   * for (int i=0;i<scores.length;i++) {
   * if (scores[i].newY>greatest) {greatest=scores[i].newY;}
   * }
   * return greatest;
   * }
   * **********
   */

  public String getEvaluation() {
    double product = matchValue;
    if (product < 1) {
      return "High";
    } else if (product < 2) {
      return "Moderate";
    } else {
      return "Low";
    }
  }

  public String getIndividualName() {
    return individualName;
  }
  

  public double getMatchValue(){
    
    //don't return the default failure value...screws up boosting
    if(matchValue==1000000000){return weka.core.Utils.missingValue();}
    
    return matchValue;
  }
  
  public void setEncounterNumber(String num){this.encounterNumber=num;}
  public void setIndividualID(String id){this.individualName=id;}
  
}