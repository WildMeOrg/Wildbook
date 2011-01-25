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

//temp comment

import java.util.ArrayList;
import java.util.Vector;


/**
 * matchObject is a temporary object used by the <code>shepherd</code> class to store unordered match values
 * tests
 */
public class MatchObject implements java.io.Serializable {
  static final long serialVersionUID = 9122107217335010239L;
  public String individualName = "N/A", date;
  public double matchValue, size, adjustedMatchValue = 0;
  public double[] logMbreakdown;
  public int numTriangles;
  public ArrayList scores = new ArrayList();
  public String encounterNumber = "N/A", pointBreakdown = "N/A";
  public String newSex = "Unknown", catalogSex = "Unknown";
  public String wiUniqueNum = "";
  public String taskID = "";

  //add I3S match values
  public Vector Points = new Vector();
  public double i3sMatchValue;

  //used for JDO enhance
  public MatchObject() {
  }

  public MatchObject(String individualName, double matchValue, double adjustedMatchValue, int numTriangles, ArrayList scores, String encounterNumber, String pointBreakdown, double[] logMbreakdown, String catalogSex, String date, double size) {
    this.matchValue = matchValue;
    this.adjustedMatchValue = adjustedMatchValue;
    this.individualName = individualName;
    this.numTriangles = numTriangles;
    this.scores = scores;
    this.encounterNumber = encounterNumber;
    this.pointBreakdown = pointBreakdown;
    this.catalogSex = catalogSex;
    this.logMbreakdown = logMbreakdown;
    this.date = date;
    this.size = size;

  }

  public MatchObject(String individualName, double matchValue, int numTriangles, String encounterNumber) {
    this.matchValue = matchValue;
    this.individualName = individualName;
    this.numTriangles = numTriangles;
    this.encounterNumber = encounterNumber;
    //this.scores=scores;
  }

  public String getLogMBreakdown() {
    if (logMbreakdown == null) {
      return "&nbsp;";
    } else {
      StringBuffer logMs = new StringBuffer();
      for (int iter = 0; iter < logMbreakdown.length; iter++) {
        logMs.append((new Double(logMbreakdown[iter])).toString());
        logMs.append("| ");
      }
      return logMs.toString();
    }


  }

  public String getLogMStdDev() {
    if (logMbreakdown == null) {
      return "&nbsp;";
    } else {
      StringBuffer logMStdDev = new StringBuffer();
      double meanLogM = 0;
      double stdDeviationLogM = 0;
      int logMSize = logMbreakdown.length;
      for (int iter4 = 0; iter4 < logMSize; iter4++) {
        meanLogM += logMbreakdown[iter4];
      }
      meanLogM = meanLogM / logMSize;
      for (int iter5 = 0; iter5 < logMSize; iter5++) {
        stdDeviationLogM += Math.pow((logMbreakdown[iter5] - meanLogM), 2);
      }
      //System.out.println("Almost standard deviation is: "+stdDeviationLogM);
      //System.out.println("LogM list size minus one is: "+(logM.size()-1));
      //System.out.println("The real std dev. should be: "+Math.pow((stdDeviationLogM/(logM.size()-1)), 0.5));
      try {
        stdDeviationLogM = Math.pow((stdDeviationLogM / (logMbreakdown.length - 1)), 0.5);
      } catch (ArithmeticException ae) {
        stdDeviationLogM = 0;
      }
      if (stdDeviationLogM > 0.01) {
        return ((new Double(stdDeviationLogM)).toString().substring(0, 7));
      } else {
        return "<0.01";
      }

    }


  }


  public double getRightmostSharkSpot() {
    double greatest = 0;
    for (int i = 0; i < scores.size(); i++) {

      if (((VertexPointMatch) scores.get(i)).oldX > greatest) {
        greatest = ((VertexPointMatch) scores.get(i)).oldX;
      }

    }
    return greatest;
  }

  public double getHighestSharkSpot() {
    double greatest = 0;
    for (int i = 0; i < scores.size(); i++) {

      if (((VertexPointMatch) scores.get(i)).oldY > greatest) {
        greatest = ((VertexPointMatch) scores.get(i)).oldY;
      }

    }
    return greatest;
  }

  public double getRightmostEncounterSpot() {
    double greatest = 0;
    for (int i = 0; i < scores.size(); i++) {

      if (((VertexPointMatch) scores.get(i)).newX > greatest) {
        greatest = ((VertexPointMatch) scores.get(i)).newX;
      }

    }
    return greatest;

  }

  public VertexPointMatch[] getScores() {
    VertexPointMatch[] scrs = new VertexPointMatch[scores.size()];
    for (int i = 0; i < scores.size(); i++) {
      scrs[i] = (VertexPointMatch) scores.get(i);
    }
    return scrs;
  }


  public double getHighestEncounterSpot() {
    double greatest = 0;
    for (int i = 0; i < scores.size(); i++) {

      if (((VertexPointMatch) scores.get(i)).newY > greatest) {
        greatest = ((VertexPointMatch) scores.get(i)).newY;
      }

    }
    return greatest;
  }

  public String getEvaluation() {
    double product = matchValue * adjustedMatchValue;
    if (product >= 115) {
      return "High";
    } else if (product >= 40) {
      return "Moderate";
    } else {
      return "Low";
    }
  }

  public double getAdjustedMatchValue() {
    return adjustedMatchValue;
  }

  public double getMatchValue() {
    return matchValue;
  }

  public String getDate() {
    return date;
  }

  public String getPointBreakdown() {
    return pointBreakdown;
  }

  public String getSex() {
    return catalogSex;
  }

  public String getNewSex() {
    return newSex;
  }

  public String getEncounterNumber() {
    return encounterNumber;
  }

  public double getSize() {
    return size;
  }

  public String getIndividualName() {
    return individualName;
  }

  public Vector getMap2() {
    return Points;
  }

  public double getI3SMatchValue() {
    return i3sMatchValue;
  }

  public void setI3SValues(Vector map2, double i3sMatchValue) {
    this.Points = map2;
    this.i3sMatchValue = i3sMatchValue;
  }

  public void setWorkItemUniqueNumber(String num) {
    this.wiUniqueNum = num;
  }

  public String getWorkItemUniqueNumber() {
    return this.wiUniqueNum;
  }

  public void setTaskID(String num) {
    this.taskID = num;
  }

  public String getTaskID() {
    return this.taskID;
  }

}