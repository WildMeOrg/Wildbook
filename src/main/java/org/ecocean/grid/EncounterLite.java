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

//another unenhanced comment


import com.fastdtw.timeseries.TimeSeries;
import com.fastdtw.timeseries.TimeSeriesBase;
import com.fastdtw.timeseries.TimeSeriesBase.Builder;
import com.fastdtw.dtw.*;
import com.fastdtw.util.Distances;
import com.reijns.I3S.Affine;
import com.reijns.I3S.Compare;
import com.reijns.I3S.FingerPrint;
import com.reijns.I3S.Point2D;


import org.ecocean.Encounter;
import org.ecocean.Spot;
import org.ecocean.SuperSpot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.lang.Double;
import java.awt.geom.*;

import weka.core.Utils;

import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import java.util.Collections;


//import timeseries packages
import org.ecocean.timeseries.core.*;
import org.ecocean.timeseries.core.distance.*;


//a class...
//more description..
//more comments
//more comments
public class EncounterLite implements java.io.Serializable {

  static final long serialVersionUID = 2458490675847424012L;

  //new spot arrays
  private double[] spotsX;
  private double[] spotsY;
  private double[] rightSpotsX;
  private double[] rightSpotsY;
  private double[] leftReferenceSpotsX;
  private double[] leftReferenceSpotsY;
  private double[] rightReferenceSpotsX;
  private double[] rightReferenceSpotsY;


  private double size;
  private String sex = "Unknown";
  private String encounterNumber = "";
  private String belongsToMarkedIndividual="";
  String date = "";
  public String dynamicProperties;
  private Long dateLong=null;
  private String genusSpecies="";
  private String patterningCode;
  
  public EncounterLite() {
  }
  
  public EncounterLite(Encounter enc){
    //130 degrees was tested to be optimum angle for dolphin dorsals scanning by 5 degree increments from 60 to 180
    this(enc, 130);
  }

  public EncounterLite(Encounter enc, double dorsalRotationInDegree) {
    if(enc.getDate()!=null){
      this.date = enc.getDate();
    }
    
    if(enc.getDateInMilliseconds()!=null){
      this.dateLong=enc.getDateInMilliseconds();
    }
    
    if((enc.getGenus()!=null)&&(enc.getSpecificEpithet()!=null)){
      this.genusSpecies=enc.getGenus()+enc.getSpecificEpithet();
    }
    
    this.encounterNumber = enc.getEncounterNumber();
    if(enc.getIndividualID()!=null){
      this.belongsToMarkedIndividual = enc.getIndividualID();
    }
    if(enc.getSex()!=null){
      this.sex = enc.getSex();
    }
    if(enc.getDynamicProperties()!=null){this.dynamicProperties=enc.getDynamicProperties();}

    if(enc.getPatterningCode()!=null){this.patterningCode=enc.getPatterningCode();}
    //get spots

  if (isDorsalFin(enc)) {
    
    
    
    processDorsalSpots(enc, dorsalRotationInDegree);
    
    //System.out.println("Finished processed dorsal spots!");
    //System.out.println(".....Left spots: "+this.getSpots().size());
   

  } else {
    if (enc.getSpots() != null) {

      processLeftSpots(enc.getSpots());
      //System.out.println("EncounterLite sees "+enc.getSpots().size()+" left-side spots.");

      if (enc.getLeftReferenceSpots() != null) {

        processLeftReferenceSpots(enc.getLeftReferenceSpots());
        //System.out.println("EncounterLite sees "+enc.getLeftReferenceSpots().size()+" left-side REFERENCE spots.");

      }


    }

    //get right spots
    if (enc.getRightSpots() != null) {

      processRightSpots(enc.getRightSpots());
      //System.out.println("EncounterLite sees "+enc.getRightSpots().size()+" right-side spots.");

      if (enc.getRightReferenceSpots() != null) {

        processRightReferenceSpots(enc.getRightReferenceSpots());
        //System.out.println("EncounterLite sees "+enc.getRightReferenceSpots().size()+" right-side REFERENCE spots.");

      }


    }
  } //non-dorsal spots


  }

  public String getDate() {
    return date;
  }

  public ArrayList getSpots() {
    if (spotsX != null) {
      int length = spotsX.length;
      ArrayList leftArray = new ArrayList();
      int q = 0;
      while (q < length) {
        double a = spotsX[q];
        double b = spotsY[q];
        leftArray.add(new SuperSpot(a, b));
        q++;
      }
      return leftArray;
    } else {
      return null;
    }
  }

  public ArrayList getRightSpots() {
    if (rightSpotsX != null) {
      int length = rightSpotsX.length;
      ArrayList rightArray = new ArrayList();
      for (int q = 0; q < length; q++) {
        rightArray.add(new SuperSpot(rightSpotsX[q], rightSpotsY[q]));
      }
      return rightArray;
    } else {
      return null;
    }
  }


  public SuperSpot[] getLeftReferenceSpots() {
    if (leftReferenceSpotsX != null) {
      int length = leftReferenceSpotsX.length;
      SuperSpot[] leftArray = new SuperSpot[length];
      int q = 0;
      while (q < length) {
        double a = leftReferenceSpotsX[q];
        double b = leftReferenceSpotsY[q];
        leftArray[q] = new SuperSpot(a, b);
        q++;
      }
      return leftArray;
    } else {
      return null;
    }
  }

  public SuperSpot[] getRightReferenceSpots() {
    if (rightReferenceSpotsX != null) {
      int length = rightReferenceSpotsX.length;
      SuperSpot[] rightArray = new SuperSpot[length];
      for (int q = 0; q < length; q++) {
        rightArray[q] = new SuperSpot(rightReferenceSpotsX[q], rightReferenceSpotsY[q]);
      }
      return rightArray;
    } else {
      return null;
    }
  }

  public String getEncounterNumber() {
    return encounterNumber;
  }

  public MatchObject getPointsForBestMatch(SuperSpot[] newspotsTemp, double epsilon, double R, double Sizelim, double maxTriangleRotation, double C, boolean secondRun, boolean rightScan, SuperSpot[] newRefSpots) {
    System.out.println("\nNow comparing against encounter " + encounterNumber + " of " + belongsToMarkedIndividual + "...");
    try {

      SuperSpot[] spots = new SuperSpot[0];

      //check to see if this is a right side scan. if false, this is a left-side scan.
      if (rightScan) {
        //this is a rightside pattern scan
        spots = (SuperSpot[]) getRightSpots().toArray(spots);
      } else {
        spots = (SuperSpot[]) getSpots().toArray(spots);
      }

      SuperSpot[] newspots;
      //set up variables needed to normalize spots and make sure that list A is always the smallest of the two lists
      boolean swappedSpots = false;

      newspots = new SuperSpot[newspotsTemp.length];
      for (int nsIter = 0; nsIter < newspotsTemp.length; nsIter++) {
        newspots[nsIter] = newspotsTemp[nsIter];
      }

      double xMaxCatalog = 0;
      double yMaxCatalog = 0;
      double xMaxNew = 0;
      double yMaxNew = 0;
      for (int iterN1 = 0; iterN1 < newspots.length; iterN1++) {
        if (newspots[iterN1].getTheSpot().getCentroidX() > xMaxNew) {
          xMaxNew = newspots[iterN1].getTheSpot().getCentroidX();
        }
      }
      for (int iterN2 = 0; iterN2 < newspots.length; iterN2++) {
        if (newspots[iterN2].getTheSpot().getCentroidY() > yMaxNew) {
          yMaxNew = newspots[iterN2].getTheSpot().getCentroidY();
        }
      }
      for (int iterN3 = 0; iterN3 < spots.length; iterN3++) {
        if (spots[iterN3].getTheSpot().getCentroidX() > xMaxCatalog) {
          xMaxCatalog = spots[iterN3].getTheSpot().getCentroidX();
        }
      }

      //correction
      for (int iterN4 = 0; iterN4 < spots.length; iterN4++) {
        if (spots[iterN4].getTheSpot().getCentroidY() > yMaxCatalog) {
          yMaxCatalog = spots[iterN4].getTheSpot().getCentroidY();
        }
      }
      double normFactorCatalog = 0;
      if (xMaxCatalog > yMaxCatalog) {
        normFactorCatalog = xMaxCatalog;
      } else {
        normFactorCatalog = yMaxCatalog;
      }
      //System.out.println("normFactorCatalog is: "+normFactorCatalog);
      double normFactorNew = 0;
      if (xMaxNew > yMaxNew) {
        normFactorNew = xMaxNew;
      } else {
        normFactorNew = yMaxNew;
      }
      for (int iterj = 0; iterj < newspots.length; iterj++) {
        Spot replaceMe = newspots[iterj].getTheSpot();
        newspots[iterj] = new SuperSpot(new Spot(replaceMe.getArea(), (replaceMe.getCentroidX() / normFactorNew), (replaceMe.getCentroidY() / normFactorNew)));
      }
      //now iterate through catalog spots and normalize each
      for (int iterj2 = 0; iterj2 < spots.length; iterj2++) {
        Spot replaceMe = spots[iterj2].getTheSpot();
        spots[iterj2] = new SuperSpot(new Spot(replaceMe.getArea(), (replaceMe.getCentroidX() / normFactorCatalog), (replaceMe.getCentroidY() / normFactorCatalog)));
      }

      //stopped formatting here


      //start triangle creation in Groth method
      double newSpan, baseSpan, newClosePairDist, baseClosePairDist;
      double bestScore = 0, adjustedScore = 0;
      int orient;
      double allowedRotationDiff = Math.toRadians(maxTriangleRotation);


      //construct all triangles for the newEncounter
      newSpan = -1;
      newClosePairDist = 9999;
      int numSpots = newspots.length;
      //System.out.println("     I expect "+(numSpots * (numSpots - 1) * 1 / 6)+" triangles.");
      ArrayList newTriangles = new ArrayList(numSpots * (numSpots - 1) * (numSpots - 2) / 6);
      int newSpotArrayL = newspots.length - 2;
      for (int i = 0; i < newSpotArrayL; i++) {

        for (int j = i + 1; j < (newspots.length - 1); j++) {
          int newArrayL = newspots.length;
          //for (int k = j + 1; k < newArrayL; k++) {
           
          
          SpotTriangle tempTriangle = new SpotTriangle(newspots[i].getTheSpot(), newspots[j].getTheSpot(), newRefSpots[1].getTheSpot(), epsilon);
          
          orient = 0;
            if (tempTriangle.clockwise) orient = 1;


            if (tempTriangle.D13 > newSpan) {


              newSpan = tempTriangle.D13;
            }
            if (tempTriangle.D12 < newClosePairDist) {
              newClosePairDist = tempTriangle.D12;
            }
            //System.out.println("      Triangle R is "+tempTriangle.R+" and C is "+tempTriangle.C);
            
            if ((tempTriangle.R <= R) && (tempTriangle.C <= C)) {
              newTriangles.add(tempTriangle);

            }
          //}
        }
      }
      //System.out.println("     I found "+newTriangles.size()+" new encounter triangles.\n Filtering for Sizelim...");
      for (int i = 0; i < newTriangles.size(); i++) {
        SpotTriangle tempTriangle = (SpotTriangle) newTriangles.get(i);

        //old sizelim computation
        //System.out.println("      Evaluating a triangle with Sizelim of: "+(tempTriangle.D13 / newSpan));
        if (tempTriangle.D13 / newSpan >= Sizelim) {

          //System.out.println("Removing large triangle: "+tempTriangle.D13+" "+newSpan+" "+tempTriangle.D13/newSpan);
          newTriangles.remove(i);
          i--;

        }
      }
      
      System.out.println("     After Sizelim filering there are now "+newTriangles.size()+" new encounter triangles.");
      
      
      
      if (newClosePairDist < (3 * epsilon)) {
        System.out.println("WARNING!!!! Spots in the new encounter are too close together to support this high of an epsilon value!!!");
      }

      //construct all triangles for the base Encounter to be compared to
      baseSpan = -1;
      baseClosePairDist = 9999;
      SuperSpot[] baseSpots = spots;
      int spotAL = baseSpots.length;
      //System.out.println("      I expect "+(spotAL*(spotAL-1)*(spotAL-2)/6)+" triangles.");
      ArrayList baseTriangles = new ArrayList(spotAL * (spotAL - 1) * (spotAL - 2) / 6);
      int spotArrayL = baseSpots.length - 2;
      int ensureNumIterations = 0;
      for (int i = 0; i < spotArrayL; i++) {

        for (int j = i + 1; j < (baseSpots.length - 1); j++) {

          for (int k = j + 1; k < baseSpots.length; k++) {
            SpotTriangle tempTriangle = new SpotTriangle(baseSpots[i].getTheSpot(), baseSpots[j].getTheSpot(), baseSpots[k].getTheSpot(), epsilon);
            orient = 0;
            if (tempTriangle.clockwise) orient = 1;
            //System.out.println("New "+i+" "+j+" "+k+" "+tempTriangle.C+" "+tempTriangle.tC2+" "+tempTriangle.R+" "+tempTriangle.tR2+" "+tempTriangle.D13+" "+orient);
            //System.out.println(i+" "+j+" "+k+" "+tempTriangle.Dxs+" "+tempTriangle.Dys+" "+tempTriangle.Dxl+" "+tempTriangle.Dyl+" "+orient);
            if (tempTriangle.D13 > baseSpan) {
              baseSpan = tempTriangle.D13;
            }
            if (tempTriangle.D12 < baseClosePairDist) {
              baseClosePairDist = tempTriangle.D12;
            }
            if ((tempTriangle.R <= R) && (tempTriangle.C <= C)) {
              baseTriangles.add(tempTriangle);
            }
          }
        }
      }
      //System.out.println("     I found "+baseTriangles.size()+" base encounter triangles.\n Filtering for Sizelim...");
      for (int i = 0; i < baseTriangles.size(); i++) {
        SpotTriangle tempTriangle = (SpotTriangle) baseTriangles.get(i);

        //old way
        if (tempTriangle.D13 / baseSpan >= Sizelim) {

          //new way
          //	if (tempTriangle.D13/baseSpan < Sizelim) {
          //System.out.println("Removing large triangle: "+tempTriangle.D13+" "+baseSpan+" "+tempTriangle.D13/baseSpan);
          baseTriangles.remove(i);
          i--;
        } else {
          //System.out.println("Keeping: "+i+" "+tempTriangle.D13+" "+baseSpan+" "+tempTriangle.D13/baseSpan);
        }
      }
      //System.out.println("     Now using "+baseTriangles.size()+" base encounter triangles.");
      //System.out.println("newSpan "+newSpan+" baseSpan "+baseSpan);
      //System.out.println("     baseClosePairDist is "+baseClosePairDist);
      if (baseClosePairDist < (3 * epsilon)) {
        System.out.println("WARNING!!!! Spots in the catalog encounter are too close together to support this high of an epsilon value!!!");
      }

      //System.out.println("   I have constructed all of the triangles!");

      //now swap the traingles if newTriangles>baseTriangles

      SpotTriangle[] tArray = new SpotTriangle[0];
      SpotTriangle[] baseArray = new SpotTriangle[0];

      if (newTriangles.size() > baseTriangles.size()) {
        swappedSpots = true;
        baseArray = (SpotTriangle[]) (newTriangles.toArray(baseArray));
        tArray = (SpotTriangle[]) (baseTriangles.toArray(tArray));
      } else {
        tArray = (SpotTriangle[]) (newTriangles.toArray(tArray));
        baseArray = (SpotTriangle[]) (baseTriangles.toArray(baseArray));
      }

      //now begin processing the triangles

      Arrays.sort(tArray, new RComparator());
      Arrays.sort(baseArray, new RComparator());

      //VmatchesA are the matched triangles of the new encounter whose spots were passed into this method
      ArrayList VmatchesA = new ArrayList(5000);

      //VmatchesB are the matched triangles of this encounter
      ArrayList VmatchesB = new ArrayList(5000);
      ArrayList bestSums = new ArrayList(5000);
      double holdingMatch = 0;

      boolean matched;
      int arrayL = tArray.length;
      int baseArrayL = baseArray.length;
      // below, 'A' refers to tArray which is the array of the new encounter triangles, 'B' to baseArray which is the array of this database encounter's triangles
      double RA, RB, CA, CB;
      double tRA2, tRB2, tCA2, tCB2;
      double RotA, rotdiff, bestrot;
      double sqrttR2sum, Rdiff2, Cdiff2, sumdiffs, bestsum, besttol;
      int bestiter2 = 0;
      for (int iter1 = 0; iter1 < arrayL; iter1++) {
        matched = false;
        bestsum = 99999;
        RA = tArray[iter1].R;
        tRA2 = tArray[iter1].tR2;
        CA = tArray[iter1].C;
        tCA2 = tArray[iter1].tC2;
        RotA = tArray[iter1].getMyVertexOneRotationInRadians();
        for (int iter2 = 0; iter2 < baseArrayL; iter2++) {
          RB = baseArray[iter2].R;
          tRB2 = baseArray[iter2].tR2;
          sqrttR2sum = Math.sqrt(tRA2 + tRB2);
          //System.out.println(iter1+" "+iter2+" RB "+RB+" RA-sqrttR2sum "+(RA-sqrttR2sum)+" RA+sqrttR2sum "+(RA+sqrttR2sum));
          if ((RB > (RA - sqrttR2sum)) && (RB < (RA + sqrttR2sum))) {
            //System.out.println("Testing...");
            CB = baseArray[iter2].C;
            tCB2 = baseArray[iter2].tC2;
            Rdiff2 = (RA - RB) * (RA - RB) / (tRA2 + tRB2);
            Cdiff2 = (CA - CB) * (CA - CB) / (tCA2 + tCB2);
            rotdiff = Math.abs(RotA - baseArray[iter2].getMyVertexOneRotationInRadians()) / allowedRotationDiff;
            if ((Rdiff2 < 1.0) && (Cdiff2 < 1.0) && (rotdiff < 1.0)) {
              sumdiffs = Rdiff2 + Cdiff2 + (rotdiff * rotdiff);
              //System.out.println("Match: "+iter1+" "+iter2+" RA "+RA+" RB "+RB+" CA "+CA+" CB "+CB+" CWA "+tArray[iter1].clockwise+" CWB "+baseArray[iter2].clockwise+" "+sumdiffs);
              //System.out.println("PerA "+tArray[iter1].logPerimeter+" PerB "+baseArray[iter2].logPerimeter);


              //added the requirement here that matched trianlges be of the same orientation - jah 1/19/04
              if (sumdiffs < bestsum) {

                //check to make sure that the triangles are not extreme rotations of each other

                //System.out.println("angle of rotation diff is: "+Math.toDegrees(tArray[iter1].getMyVertexOneRotationInRadians()-baseArray[iter2].getMyVertexOneRotationInRadians()));
                matched = true;
                bestiter2 = iter2;
                bestsum = sumdiffs;
                //VmatchesA.add(tArray[iter1]);
                //VmatchesB.add(baseArray[iter2]);
                //	}
              }
            }
          }
        }
        if (matched) {
          //System.out.println("Best iter2:"+bestiter2);
          //System.out.println("Match: "+bestsum+" "+tArray[iter1].R+" "+baseArray[bestiter2].R+" "+tArray[iter1].C+" "+baseArray[bestiter2].C+" "+tArray[iter1].D13/newSpan+" "+baseArray[bestiter2].D13/baseSpan+" "+tArray[iter1].clockwise+" "+baseArray[bestiter2].clockwise+" "+iter1+" "+bestiter2);
          VmatchesA.add(tArray[iter1]);
          VmatchesB.add(baseArray[bestiter2]);
          bestSums.add(new Double(bestsum));
        }
      }
      System.out.println("     I am now about to start filtering with "+VmatchesA.size()+" triangles!");
      //now begin filtering
      ArrayList logM = new ArrayList(VmatchesA.size());
      int nPLUS = 0;
      int nMINUS = 0;


      for (int iter3 = 0; iter3 < VmatchesA.size(); iter3++) {
        logM.add(new Double((((SpotTriangle) VmatchesA.get(iter3)).logPerimeter) - ((SpotTriangle) VmatchesB.get(iter3)).logPerimeter));
        //System.out.println("M value of: "+(new Double((((spotTriangle)VmatchesA.elementAt(iter3)).logPerimeter)-((spotTriangle)VmatchesB.elementAt(iter3)).logPerimeter)).doubleValue());
        if (((SpotTriangle) VmatchesA.get(iter3)).clockwise == ((SpotTriangle) VmatchesB.get(iter3)).clockwise) {
          nPLUS++;
        } else {
          nMINUS++;
        }
      }
      int mT = Math.abs(nPLUS - nMINUS);
      int mF = nPLUS + nMINUS - mT;
      double multiple = 0;
      boolean stillIterate = true;
      int numIterations = 0;
      System.out.println("   Going into the logM filter with "+VmatchesA.size()+" matching triangles. Before filtering, N+="+nPLUS+" N-="+nMINUS);

      double oldStdDeviationLogM = 10000;
      while (stillIterate && (numIterations < 20) && (VmatchesA.size() > 0)) {
        //System.out.println("          iterating filter with "+VmatchesA.size()+" triangles!");
        numIterations++;
        boolean haveMadeChange = false;

        //let's find some logM info
        double meanLogM = 0;
        double stdDeviationLogM = 0;

        //method to compute logM using only the standard deviations of same sense triangles
        int logMSize = logM.size();
        for (int iter4 = 0; iter4 < logMSize; iter4++) {
          boolean Aorientation = ((SpotTriangle) VmatchesA.get(iter4)).clockwise;
          boolean Borientation = ((SpotTriangle) VmatchesB.get(iter4)).clockwise;
          if (Aorientation == Borientation) {
            //logMSize++;
            meanLogM += ((Double) logM.get(iter4)).doubleValue();
          }
        }
        meanLogM = meanLogM / nPLUS;
        System.out.println("Found a mean of: "+meanLogM);


        //weighted method
        //int logMSize=logM.size();
        //int sumLogMDivBestSum=0;
        //int OneOverBestSum=0;

        //for(int iter4=0; iter4<logMSize; iter4++){
        //	sumLogMDivBestSum+=(((Double)logM.get(iter4)).doubleValue())/(((Double)bestSums.get(iter4)).doubleValue());
        //	OneOverBestSum+=1/(((Double)bestSums.get(iter4)).doubleValue());
        //	}
        //meanLogM=sumLogMDivBestSum/OneOverBestSum;


        for (int iter5 = 0; iter5 < logMSize; iter5++) {
          boolean Aorientation = ((SpotTriangle) VmatchesA.get(iter5)).clockwise;
          boolean Borientation = ((SpotTriangle) VmatchesB.get(iter5)).clockwise;
          if (Aorientation == Borientation) {
            stdDeviationLogM += Math.pow((((Double) logM.get(iter5)).doubleValue() - meanLogM), 2);
          }
        }
        System.out.println("Almost standard deviation is: "+stdDeviationLogM);
        System.out.println("LogM list size minus one is: "+(logM.size()-1));
        System.out.println("The real std dev. should be: "+Math.pow((stdDeviationLogM/(logM.size()-1)), 0.5));


        if (nPLUS > 1) {
          stdDeviationLogM = Math.pow((stdDeviationLogM / (nPLUS - 1)), 0.5);
        } else {
          stdDeviationLogM = 0.0;
        }

        //System.out.println("Found a std. dev. of: "+stdDeviationLogM);


        //now let's define a filter based on logM
        int greaterThanMeanLogM = 0;
        int lessThanMeanLogM = 0;
        for (int iterCount = 0; iterCount < logM.size(); iterCount++) {
          if ((((Double) logM.get(iterCount)).doubleValue()) > meanLogM) {
            greaterThanMeanLogM++;
          } else {
            lessThanMeanLogM++;
          }
        }
        boolean leftSideHeavy = false;
        boolean rightSideHeavy = false;
        boolean balanced = false;
        if (Math.pow((lessThanMeanLogM - greaterThanMeanLogM), 2) > (lessThanMeanLogM + greaterThanMeanLogM)) {
          if (lessThanMeanLogM > greaterThanMeanLogM) {
            leftSideHeavy = true;
          } else {
            rightSideHeavy = true;
          }
        } else {
          balanced = true;
        }

        //Groth's way
        //if(mF>mT) {multiple=1;}
        //else if((0.1*mT)>mF) {multiple=3;}
        //else{multiple=2;}

        // softer logM filter:
        //if(mF>2.0*mT) {multiple=1;}
        //else if((0.5*mT)>mF) {multiple=3;}
        //else{multiple=2;}


        // softer still:
        if (nMINUS > nPLUS) {
          multiple = 1;
        } else if ((0.5 * mT) > mF) {
          multiple = 3;
        } else {
          multiple = 2;
        }


        //now discard nonmatches
        int logMremovals = 0;
        int leftsideRemovals = 0, rightsideRemovals = 0;
        for (int iter6 = 0; iter6 < logM.size(); iter6++) {

          if (Math.abs(((Double) logM.get(iter6)).doubleValue() - meanLogM) > (multiple * stdDeviationLogM)) {
            if (leftSideHeavy && (((Double) logM.get(iter6)).doubleValue() < meanLogM)) {
              leftsideRemovals++;
            } else if (rightSideHeavy && (((Double) logM.get(iter6)).doubleValue() > meanLogM)) {
              rightsideRemovals++;
            } else if (leftSideHeavy && (((Double) logM.get(iter6)).doubleValue() > meanLogM)) {
              rightsideRemovals++;
            } else if (rightSideHeavy && (((Double) logM.get(iter6)).doubleValue() < meanLogM)) {
              leftsideRemovals++;
            }
            logM.remove(iter6);
            VmatchesA.remove(iter6);
            VmatchesB.remove(iter6);
            bestSums.remove(iter6);
            haveMadeChange = true;
            iter6--;
            logMremovals++;
          }
        }
        //System.out.print("     left heavy? "+leftSideHeavy+"   ");
        //System.out.print("     right heavy? "+rightSideHeavy+"   ");
        //System.out.println("     Balanced? "+balanced+"   ");
        //	System.out.println("     Removed "+logMremovals+" triangles on logM filter pass "+numIterations+" with a filter/multiple value of "+multiple);
        //System.out.println("          leftsideRemovals="+leftsideRemovals+"     rightsideRemovals="+rightsideRemovals);
        //System.out.println("          N+ is "+nPLUS+" and N- is "+nMINUS);

        if (!haveMadeChange) {
          stillIterate = false;
        }
        nPLUS = 0;
        nMINUS = 0;
        int iterLimit = VmatchesA.size();
        for (int iter7 = 0; iter7 < iterLimit; iter7++) {
          if (((SpotTriangle) VmatchesA.get(iter7)).clockwise == ((SpotTriangle) VmatchesB.get(iter7)).clockwise) {
            nPLUS++;
          } else {
            nMINUS++;
          }
        }
        mT = Math.abs(nPLUS - (nMINUS));
        mF = nPLUS + nMINUS - mT;

        oldStdDeviationLogM = stdDeviationLogM;
        //System.out.println("          Going into the next round with mT, mF: "+mT+","+mF);
      }


      for (int iter8 = 0; iter8 < VmatchesA.size(); iter8++) {
        if (((SpotTriangle) VmatchesA.get(iter8)).clockwise != ((SpotTriangle) VmatchesB.get(iter8)).clockwise) {
          logM.remove(iter8);
          VmatchesA.remove(iter8);
          VmatchesB.remove(iter8);
          bestSums.remove(iter8);
          iter8--;
        }

      }

      System.out.println("Going into Groth scoring with "+VmatchesA.size()+" matching triangles.");
      if (VmatchesA.size() == 0) {
        return (new MatchObject(belongsToMarkedIndividual, 0, 0, encounterNumber));
      }
      MatchedPoints mp = new MatchedPoints();
      int vMatchL = VmatchesA.size();
      for (int iter10 = 0; iter10 < vMatchL; iter10++) {
        for (int iter11 = 0; iter11 < 3; iter11++) {
          Spot spotA = ((SpotTriangle) VmatchesA.get(iter10)).getVertex(iter11 + 1);
          Spot spotB = ((SpotTriangle) VmatchesB.get(iter10)).getVertex(iter11 + 1);
          //for(int iter12=iter11+1; iter12<VmatchesA.size(); iter12++) {
          int tempPlace = mp.hasMatchedPair(spotA, spotB);
          if (tempPlace != -1) {
            ((VertexPointMatch) mp.get(tempPlace)).points++;

          } else {
            mp.add(new VertexPointMatch(spotA, spotB, 1));
          }
          //	}
        }

      }
      VertexPointMatch[] scores = new VertexPointMatch[0];
      scores = (VertexPointMatch[]) (mp.toArray(scores));
      Arrays.sort(scores, new ScoreComparator());
      //System.out.println("scores.length is: "+scores.length);
      if (scores[0].points == 1) {
        System.out.println("Exiting because I could not match a single triangle point more than once.");

        return (new MatchObject(belongsToMarkedIndividual, 0, 0, encounterNumber));
      }
      ArrayList secondRunSpots = new ArrayList();
      //ArrayList secondRunSpotsB=new ArrayList();
      int scoresSize = scores.length;
      secondRunSpots.add(scores[0]);
      int iter20 = 1;
      boolean keepOnCounting = true;
      boolean hasNotBeenSeenYet = true;

      /*old way
   ArrayList countedSpots=new ArrayList();
   countedSpots.add(new spot(0, scores[0].newX, scores[0].newY));
   while(keepOnCounting&&(iter20<scoresSize)){



       for(int iter30=0;iter30<countedSpots.size();iter30++){
           spot tempSpot=(spot)countedSpots.get(iter30);
           if((scores[iter20].newX==tempSpot.getCentroidX())&&(scores[iter20].newY==tempSpot.getCentroidY())){hasNotBeenSeenYet=false;}
       }

       if((scores[iter20].points>(scores[(iter20-1)].points/2))&&(scores[iter20].points>1)&&(hasNotBeenSeenYet)){
           secondRunSpots.add(scores[iter20]);
           countedSpots.add(new spot(0, scores[iter20].newX, scores[iter20].newY));
           }
       else{keepOnCounting=false;}
       iter20++;
      }*/

      //Zaven's correction
      ArrayList countedSpotsA = new ArrayList();
      ArrayList countedSpotsB = new ArrayList();
      countedSpotsA.add(new Spot(0, scores[0].newX, scores[0].newY));
      countedSpotsB.add(new Spot(0, scores[0].oldX, scores[0].oldY));
      while (keepOnCounting && (iter20 < scoresSize)) {

        for (int iter30 = 0; iter30 < countedSpotsA.size(); iter30++) {
          Spot tempSpot = (Spot) countedSpotsA.get(iter30);
          if ((scores[iter20].newX == tempSpot.getCentroidX()) && (scores[iter20].newY == tempSpot.getCentroidY())) {
            hasNotBeenSeenYet = false;
          }
          tempSpot = (Spot) countedSpotsB.get(iter30);
          if ((scores[iter20].oldX == tempSpot.getCentroidX()) && (scores[iter20].oldY == tempSpot.getCentroidY())) {
            hasNotBeenSeenYet = false;
          }
        }

        if ((scores[iter20].points > (scores[(iter20 - 1)].points / 2)) && (scores[iter20].points > 1) && (hasNotBeenSeenYet)) {
          secondRunSpots.add(scores[iter20]);
          countedSpotsA.add(new Spot(0, scores[iter20].newX, scores[iter20].newY));
          countedSpotsB.add(new Spot(0, scores[iter20].oldX, scores[iter20].oldY));
        } else {
          keepOnCounting = false;
        }
        iter20++;
      }


      VertexPointMatch[] scoredSpots = new VertexPointMatch[0];
      scoredSpots = (VertexPointMatch[]) (secondRunSpots.toArray(scoredSpots));

      System.out.print("     Scoring going into second pass: ");
      for (int iter40 = 0; iter40 < scoredSpots.length; iter40++) {
        //System.out.print(scoredSpots[iter40].points+"+");
      }
      //System.out.println("...");

      ArrayList secondRunSpotsA = new ArrayList();
      ArrayList secondRunSpotsB = new ArrayList();
      for (int iter25 = 0; iter25 < scoredSpots.length; iter25++) {
        boolean matchListA = false;
        for (int iter26 = 0; iter26 < secondRunSpotsA.size(); iter26++) {
          if ((scoredSpots[iter25].newX == (((SuperSpot) secondRunSpotsA.get(iter26)).getTheSpot().getCentroidX())) && (scoredSpots[iter25].newY == (((SuperSpot) secondRunSpotsA.get(iter26)).getTheSpot().getCentroidY()))) {
            matchListA = true;
          }
        }
        if (!matchListA) {
          secondRunSpotsA.add(new SuperSpot(new Spot(0, scoredSpots[iter25].newX, scoredSpots[iter25].newY)));
        }
        boolean matchListB = false;
        for (int iter26 = 0; iter26 < secondRunSpotsB.size(); iter26++) {
          if ((scoredSpots[iter25].oldX == (((SuperSpot) secondRunSpotsB.get(iter26)).getTheSpot().getCentroidX())) && (scoredSpots[iter25].oldY == (((SuperSpot) secondRunSpotsB.get(iter26)).getTheSpot().getCentroidY()))) {
            matchListB = true;
          }
        }
        if (!matchListB) {
          secondRunSpotsB.add(new SuperSpot(new Spot(0, scoredSpots[iter25].oldX, scoredSpots[iter25].oldY)));
        }
      }
      SuperSpot[] secondNewSpots = new SuperSpot[0];
      secondNewSpots = (SuperSpot[]) (secondRunSpotsA.toArray(secondNewSpots));
      SuperSpot[] secondBaseSpots = new SuperSpot[0];
      secondBaseSpots = (SuperSpot[]) (secondRunSpotsB.toArray(secondBaseSpots));

      System.out.println("secondNewSpots is :"+secondNewSpots.length);
      System.out.println("secondBaseSpots is :"+secondBaseSpots.length);

      //now run Groth's algorithm again if there are enough spots. if not, exit as this is not a match.
      VertexPointMatch[] secondPassSpots = scoredSpots;
      if ((secondNewSpots.length > 3) && (secondBaseSpots.length > 3)) {

        //run recursion on these spots now
        secondPassSpots = secondGrothPass(secondNewSpots, secondBaseSpots, epsilon, R, Sizelim, maxTriangleRotation, C);
        if (secondPassSpots.length < 3) {
          System.out.println("Exiting after the second pass because the returned number of spots was less than three. "+scoredSpots.length+"-"+(scoredSpots.length-secondPassSpots.length)+"="+secondPassSpots.length);
          return (new MatchObject(belongsToMarkedIndividual, 0, 0, encounterNumber));
        }
        //System.out.println("     The second pass cut out "+(scoredSpots.length-secondPassSpots.length)+" spots.");
      } else {
        System.out.println("Exiting processing because there were less than three spots going into the second filter pass. This is not a match.");
        return (new MatchObject(belongsToMarkedIndividual, 0, 0, encounterNumber));
      }
      // end second run


      //let's create and pass along an array of the logM values of the matched and scored triangles.
      int logMSize = logM.size();
      double[] logMbreakdown = new double[logMSize];
      for (int logMIter = 0; logMIter < logMSize; logMIter++) {
        logMbreakdown[logMIter] = ((Double) (logM.get(logMIter))).doubleValue();
      }

      String pointBreakdown = "";
      int iterLimit = secondPassSpots[0].points;
      int iter14 = 0;
      boolean ok2iterate = true;
      int scoresLength = secondPassSpots.length;
      while ((iter14 < scoresLength) && ok2iterate) {
        if (iter14 == 0) {

          bestScore += secondPassSpots[iter14].points;
          pointBreakdown += secondPassSpots[iter14].points + " + ";
          //System.out.print(secondPassSpots[iter14].points+"+");
          //}
          iter14++;
        }
        //modification...remove the drop in half limit that Groth recommends in his paper b/c point scores are so low.
        //else if(scores[iter14].points>=((scores[(iter14-1)].points)/2)) {
        else {
          bestScore += secondPassSpots[iter14].points;
          pointBreakdown += secondPassSpots[iter14].points + " + ";
          //System.out.print(secondPassSpots[iter14].points+"+");
          iter14++;
        }

      }
      adjustedScore = bestScore / (arrayL * 3);


      //System.out.println("\nTotal score is: "+bestScore);
      //System.out.println("\nAdjusted score is: "+adjustedScore);


      if (!swappedSpots) {

        for (int diters = 0; diters < secondPassSpots.length; diters++) {
          //System.out.println("was: "+secondPassSpots[diters].newX);
          secondPassSpots[diters].newX = secondPassSpots[diters].newX * normFactorNew;
          //System.out.println("changed to: "+secondPassSpots[diters].newX);
          secondPassSpots[diters].newY = secondPassSpots[diters].newY * normFactorNew;
          //System.out.println("changed to: "+secondPassSpots[diters].oldX);
          secondPassSpots[diters].oldX = secondPassSpots[diters].oldX * normFactorCatalog;
          //	System.out.println("changed to: "+secondPassSpots[diters].oldX);
          secondPassSpots[diters].oldY = secondPassSpots[diters].oldY * normFactorCatalog;
        }

      } else {
        for (int diters = 0; diters < secondPassSpots.length; diters++) {
          //System.out.println("was: "+secondPassSpots[diters].newX);
          secondPassSpots[diters].newX = secondPassSpots[diters].newX * normFactorCatalog;
          //System.out.println("changed to: "+secondPassSpots[diters].newX);
          secondPassSpots[diters].newY = secondPassSpots[diters].newY * normFactorCatalog;
          //System.out.println("changed to: "+secondPassSpots[diters].oldX);
          secondPassSpots[diters].oldX = secondPassSpots[diters].oldX * normFactorNew;
          //	System.out.println("changed to: "+secondPassSpots[diters].oldX);
          secondPassSpots[diters].oldY = secondPassSpots[diters].oldY * normFactorNew;
        }

      }


      //if newspots and spots were swapped at the beginning of this method to decrease processing time, we need to correct this for eventual spot mapping
      if (swappedSpots) {
        VertexPointMatch[] fixedSpots = new VertexPointMatch[secondPassSpots.length];
        for (int iter70 = 0; iter70 < secondPassSpots.length; iter70++) {
          fixedSpots[iter70] = new VertexPointMatch(secondPassSpots[iter70].oldX, secondPassSpots[iter70].oldY, secondPassSpots[iter70].newX, secondPassSpots[iter70].newY, secondPassSpots[iter70].points);
        }
        secondPassSpots = fixedSpots;
      }

      ArrayList secondPassSpotsAL = new ArrayList();
      for (int y = 0; y < secondPassSpots.length; y++) {
        secondPassSpotsAL.add(secondPassSpots[y]);
      }


      //send these matched results back!!!
      return (new MatchObject(belongsToMarkedIndividual, bestScore, adjustedScore, VmatchesA.size(), secondPassSpotsAL, encounterNumber, pointBreakdown, logMbreakdown, sex, getDate(), size));


    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("0 points awarded due to exception.");
      return (new MatchObject(belongsToMarkedIndividual, 0, 0, encounterNumber));
    }
  }

  private static VertexPointMatch[] secondGrothPass(SuperSpot[] secondNewSpots, SuperSpot[] secondBaseSpots, double epsilon, double R, double Sizelim, double maxTriangleRotation, double C) {
    VertexPointMatch[] scores = new VertexPointMatch[0];
    try {
      SuperSpot[] newspots = secondNewSpots;
      SuperSpot[] localspots = secondBaseSpots;

      //start triangle creation in Groth method
      double newSpan, baseSpan, newClosePairDist, baseClosePairDist;
      double bestScore = 0;
      int orient;
      double allowedRotationDiff = Math.toRadians(maxTriangleRotation);


      //construct all triangles for the newEncounter
      newSpan = -1;
      newClosePairDist = 9999;
      int numSpots = newspots.length;
      //System.out.println("     I expect "+(numSpots*(numSpots-1)*(numSpots-2)/6)+" triangles.");
      ArrayList newTriangles = new ArrayList(numSpots * (numSpots - 1) * (numSpots - 2) / 6);
      int newSpotArrayL = newspots.length - 2;
      for (int i = 0; i < newSpotArrayL; i++) {
        for (int j = i + 1; j < (newspots.length - 1); j++) {
          int newArrayL = newspots.length;
          for (int k = j + 1; k < newArrayL; k++) {
            SpotTriangle tempTriangle = new SpotTriangle(newspots[i].getTheSpot(), newspots[j].getTheSpot(), newspots[k].getTheSpot(), epsilon);
            orient = 0;
            if (tempTriangle.clockwise) orient = 1;


            if (tempTriangle.D13 > newSpan) {


              newSpan = tempTriangle.D13;
            }
            if (tempTriangle.D12 < newClosePairDist) {
              newClosePairDist = tempTriangle.D12;
            }
            if ((tempTriangle.R <= R) && (tempTriangle.C <= C)) {
              newTriangles.add(tempTriangle);
            }
          }
        }
      }
      //System.out.println("     I found "+newTriangles.size()+" new encounter triangles.\n Filtering for Sizelim...");
      for (int i = 0; i < newTriangles.size(); i++) {
        SpotTriangle tempTriangle = (SpotTriangle) newTriangles.get(i);

        //old working sizelim filter
        if (tempTriangle.D13 / newSpan >= Sizelim) {

          //new sizelim second pass
          //if (tempTriangle.D13/newSpan < Sizelim) {

          //System.out.println("Removing large triangle: "+tempTriangle.D13+" "+newSpan+" "+tempTriangle.D13/newSpan);
          newTriangles.remove(i);
          i--;

        }
      }
      //System.out.println("     Now using "+newTriangles.size()+" new encounter triangles.");
      //System.out.println("     newClosePairDist is "+newClosePairDist);
      if (newClosePairDist < (3 * epsilon)) {
        System.out.println("WARNING!!!! Spots in the new encounter are too close together to support this high of an epsilon value!!!");
      }
      //construct all triangles for the base Encounter to be compared to
      baseSpan = -1;
      baseClosePairDist = 9999;
      SuperSpot[] baseSpots = localspots;
      int spotAL = baseSpots.length;
      //System.out.println("      I expect "+(spotAL*(spotAL-1)*(spotAL-2)/6)+" triangles.");
      ArrayList baseTriangles = new ArrayList(spotAL * (spotAL - 1) * (spotAL - 2) / 6);
      int spotArrayL = baseSpots.length - 2;
      int ensureNumIterations = 0;
      for (int i = 0; i < spotArrayL; i++) {

        for (int j = i + 1; j < (baseSpots.length - 1); j++) {

          for (int k = j + 1; k < baseSpots.length; k++) {
            SpotTriangle tempTriangle = new SpotTriangle(baseSpots[i].getTheSpot(), baseSpots[j].getTheSpot(), baseSpots[k].getTheSpot(), epsilon);
            orient = 0;
            if (tempTriangle.clockwise) orient = 1;
            //System.out.println("New "+i+" "+j+" "+k+" "+tempTriangle.C+" "+tempTriangle.tC2+" "+tempTriangle.R+" "+tempTriangle.tR2+" "+tempTriangle.D13+" "+orient);
            //System.out.println(i+" "+j+" "+k+" "+tempTriangle.Dxs+" "+tempTriangle.Dys+" "+tempTriangle.Dxl+" "+tempTriangle.Dyl+" "+orient);
            if (tempTriangle.D13 > baseSpan) {
              baseSpan = tempTriangle.D13;
            }
            if (tempTriangle.D12 < baseClosePairDist) {
              baseClosePairDist = tempTriangle.D12;
            }
            if ((tempTriangle.R <= R) && (tempTriangle.C <= C)) {
              baseTriangles.add(tempTriangle);
            }
            //ensureNumIterations++;
          }
        }
      }
      //System.out.println("     I found "+baseTriangles.size()+" base encounter triangles.\n Filtering for Sizelim...");
      for (int i = 0; i < baseTriangles.size(); i++) {
        SpotTriangle tempTriangle = (SpotTriangle) baseTriangles.get(i);


        //old way
        /*if (tempTriangle.D13/baseSpan >= Sizelim) {

        //new way
        //if (tempTriangle.D13/baseSpan < Sizelim) {

            //System.out.println("Removing large triangle: "+tempTriangle.D13+" "+baseSpan+" "+tempTriangle.D13/baseSpan);
            baseTriangles.remove(i);
            i--;
        } else {
            //System.out.println("Keeping: "+i+" "+tempTriangle.D13+" "+baseSpan+" "+tempTriangle.D13/baseSpan);
        }*/
      }
      //System.out.println("     Now using "+baseTriangles.size()+" base encounter triangles.");
      //System.out.println("newSpan "+newSpan+" baseSpan "+baseSpan);
      //System.out.println("     baseClosePairDist is "+baseClosePairDist);
      if (baseClosePairDist < (3 * epsilon)) {
        System.out.println("WARNING!!!! Spots in the catalog encounter are too close together to support this high of an epsilon value!!!");
      }

      //System.out.println("   I have constructed all of the triangles!");
      SpotTriangle[] tArray = new SpotTriangle[0];
      tArray = (SpotTriangle[]) (newTriangles.toArray(tArray));
      SpotTriangle[] baseArray = new SpotTriangle[0];
      baseArray = (SpotTriangle[]) (baseTriangles.toArray(baseArray));
      //now begin processing the triangles

      //System.out.println("     I found "+tArray.length+" new encounter triangles.");
      //System.out.println("     I found "+baseArray.length+" base encounter triangles.");

      Arrays.sort(tArray, new RComparator());
      Arrays.sort(baseArray, new RComparator());

      //VmatchesA are the matched triangles of the new encounter whose spots were passed into this method
      ArrayList VmatchesA = new ArrayList(5000);

      //VmatchesB are the matched triangles of this encounter
      ArrayList VmatchesB = new ArrayList(5000);
      ArrayList bestSums = new ArrayList(5000);
      double holdingMatch = 0;

      boolean matched;
      int arrayL = tArray.length;
      int baseArrayL = baseArray.length;
      // below, 'A' refers to tArray which is the array of the new encounter triangles, 'B' to baseArray which is the array of this database encounter's triangles
      double RA, RB, CA, CB;
      double tRA2, tRB2, tCA2, tCB2;
      double RotA, rotdiff, bestrot;
      double sqrttR2sum, Rdiff2, Cdiff2, sumdiffs, bestsum, besttol;
      int bestiter2 = 0;
      for (int iter1 = 0; iter1 < arrayL; iter1++) {
        matched = false;
        bestsum = 99999;
        RA = tArray[iter1].R;
        tRA2 = tArray[iter1].tR2;
        CA = tArray[iter1].C;
        tCA2 = tArray[iter1].tC2;
        RotA = tArray[iter1].getMyVertexOneRotationInRadians();
        for (int iter2 = 0; iter2 < baseArrayL; iter2++) {
          RB = baseArray[iter2].R;
          tRB2 = baseArray[iter2].tR2;
          sqrttR2sum = Math.sqrt(tRA2 + tRB2);
          //System.out.println(iter1+" "+iter2+" RB "+RB+" RA-sqrttR2sum "+(RA-sqrttR2sum)+" RA+sqrttR2sum "+(RA+sqrttR2sum));
          if ((RB > (RA - sqrttR2sum)) && (RB < (RA + sqrttR2sum))) {
            //System.out.println("Testing...");
            CB = baseArray[iter2].C;
            tCB2 = baseArray[iter2].tC2;
            Rdiff2 = (RA - RB) * (RA - RB) / (tRA2 + tRB2);
            Cdiff2 = (CA - CB) * (CA - CB) / (tCA2 + tCB2);
            rotdiff = Math.abs(RotA - baseArray[iter2].getMyVertexOneRotationInRadians()) / allowedRotationDiff;
            if ((Rdiff2 < 1.0) && (Cdiff2 < 1.0) && (rotdiff < 1.0)) {
              sumdiffs = Rdiff2 + Cdiff2 + (rotdiff * rotdiff);
              //System.out.println("Match: "+iter1+" "+iter2+" RA "+RA+" RB "+RB+" CA "+CA+" CB "+CB+" CWA "+tArray[iter1].clockwise+" CWB "+baseArray[iter2].clockwise+" "+sumdiffs);
              //System.out.println("PerA "+tArray[iter1].logPerimeter+" PerB "+baseArray[iter2].logPerimeter);


              //added the requirement here that matched trianlges be of the same orientation - jah 1/19/04
              if (sumdiffs < bestsum) {
                //if ((sumdiffs<bestsum)&&(tArray[iter1].clockwise==baseArray[iter2].clockwise)) {
                //if (tArray[iter1].clockwise==baseArray[iter2].clockwise) {

                //check to make sure that the triangles are not extreme rotations of each other
                //	if(Math.abs((tArray[iter1].getMyVertexOneRotationInRadians()-baseArray[iter2].getMyVertexOneRotationInRadians()))<allowedRotationDiff) {

                //System.out.println("angle of rotation diff is: "+Math.toDegrees(tArray[iter1].getMyVertexOneRotationInRadians()-baseArray[iter2].getMyVertexOneRotationInRadians()));
                matched = true;
                bestiter2 = iter2;
                bestsum = sumdiffs;

                //	}
              }
            }
          }
        }
        if (matched) {
          //System.out.println("Best iter2:"+bestiter2);
          //System.out.println("Match: "+bestsum+" "+tArray[iter1].R+" "+baseArray[bestiter2].R+" "+tArray[iter1].C+" "+baseArray[bestiter2].C+" "+tArray[iter1].D13/newSpan+" "+baseArray[bestiter2].D13/baseSpan+" "+tArray[iter1].clockwise+" "+baseArray[bestiter2].clockwise+" "+iter1+" "+bestiter2);
          VmatchesA.add(tArray[iter1]);
          VmatchesB.add(baseArray[bestiter2]);
          bestSums.add(new Double(bestsum));
        }
      }
      //System.out.println("I am now about to start filtering with "+VmatchesA.size()+" triangles!");
      //now begin filtering
      ArrayList logM = new ArrayList(VmatchesA.size());
      int nPLUS = 0;
      int nMINUS = 0;


      for (int iter3 = 0; iter3 < VmatchesA.size(); iter3++) {
        logM.add(new Double((((SpotTriangle) VmatchesA.get(iter3)).logPerimeter) - ((SpotTriangle) VmatchesB.get(iter3)).logPerimeter));
        //System.out.println("M value of: "+(new Double((((spotTriangle)VmatchesA.elementAt(iter3)).logPerimeter)-((spotTriangle)VmatchesB.elementAt(iter3)).logPerimeter)).doubleValue());
        if (((SpotTriangle) VmatchesA.get(iter3)).clockwise == ((SpotTriangle) VmatchesB.get(iter3)).clockwise) {
          nPLUS++;
        } else {
          nMINUS++;
        }
      }
      int mT = Math.abs(nPLUS - nMINUS);
      int mF = nPLUS + nMINUS - mT;
      double multiple = 0;
      boolean stillIterate = true;
      int numIterations = 0;
      //System.out.println("   Going into the logM filter with "+VmatchesA.size()+" matching triangles. Before filtering, N+="+nPLUS+" N-="+nMINUS);
      //while(stillIterate&&(numIterations<9)&&(VmatchesA.size()>0)) {
      double oldStdDeviationLogM = 10000;
      while (stillIterate && (numIterations < 20) && (VmatchesA.size() > 0)) {
        //System.out.println("          iterating filter with "+VmatchesA.size()+" triangles!");
        numIterations++;
        boolean haveMadeChange = false;

        //let's find some logM info
        double meanLogM = 0;
        double stdDeviationLogM = 0;

        //method to compute logM using only the standard deviations of same sense triangles
        int logMSize = logM.size();
        for (int iter4 = 0; iter4 < logMSize; iter4++) {
          boolean Aorientation = ((SpotTriangle) VmatchesA.get(iter4)).clockwise;
          boolean Borientation = ((SpotTriangle) VmatchesB.get(iter4)).clockwise;
          if (Aorientation == Borientation) {
            //logMSize++;
            meanLogM += ((Double) logM.get(iter4)).doubleValue();
          }
        }
        meanLogM = meanLogM / nPLUS;
        //System.out.println("Found a mean of: "+meanLogM);


        for (int iter5 = 0; iter5 < logMSize; iter5++) {
          boolean Aorientation = ((SpotTriangle) VmatchesA.get(iter5)).clockwise;
          boolean Borientation = ((SpotTriangle) VmatchesB.get(iter5)).clockwise;
          if (Aorientation == Borientation) {
            stdDeviationLogM += Math.pow((((Double) logM.get(iter5)).doubleValue() - meanLogM), 2);
          }
        }

        /*try {
         stdDeviationLogM=Math.pow((stdDeviationLogM/(logM.size()-1)), 0.5);
         }
     catch (ArithmeticException ae) {stdDeviationLogM=0;}*/
        if (nPLUS > 1) {
          stdDeviationLogM = Math.pow((stdDeviationLogM / (nPLUS - 1)), 0.5);
        } else {
          stdDeviationLogM = 0.0;
        }


        //System.out.println("Found a std. dev. of: "+stdDeviationLogM);


        //now let's define a filter based on logM
        int greaterThanMeanLogM = 0;
        int lessThanMeanLogM = 0;
        for (int iterCount = 0; iterCount < logM.size(); iterCount++) {
          if ((((Double) logM.get(iterCount)).doubleValue()) > meanLogM) {
            greaterThanMeanLogM++;
          } else {
            lessThanMeanLogM++;
          }
        }
        boolean leftSideHeavy = false;
        boolean rightSideHeavy = false;
        boolean balanced = false;
        if (Math.pow((lessThanMeanLogM - greaterThanMeanLogM), 2) > (lessThanMeanLogM + greaterThanMeanLogM)) {
          if (lessThanMeanLogM > greaterThanMeanLogM) {
            leftSideHeavy = true;
          } else {
            rightSideHeavy = true;
          }
        } else {
          balanced = true;
        }

        //Groth's way
        //if(mF>mT) {multiple=1;}
        //else if((0.1*mT)>mF) {multiple=3;}
        //else{multiple=2;}

        // softer logM filter:
        //if(mF>2.*mT) {multiple=1;}
        //else if((0.5*mT)>mF) {multiple=3;}
        //else{multiple=2;}

        // softer still:
        if (nMINUS > nPLUS) {
          multiple = 1;
        } else if ((0.5 * mT) > mF) {
          multiple = 3;
        } else {
          multiple = 2;
        }


        //now discard nonmatches
        int logMremovals = 0;
        int leftsideRemovals = 0, rightsideRemovals = 0;
        for (int iter6 = 0; iter6 < logM.size(); iter6++) {

          if (Math.abs(((Double) logM.get(iter6)).doubleValue() - meanLogM) > (multiple * stdDeviationLogM)) {
            if (leftSideHeavy && (((Double) logM.get(iter6)).doubleValue() < meanLogM)) {
              leftsideRemovals++;
            } else if (rightSideHeavy && (((Double) logM.get(iter6)).doubleValue() > meanLogM)) {
              rightsideRemovals++;
            } else if (leftSideHeavy && (((Double) logM.get(iter6)).doubleValue() > meanLogM)) {
              rightsideRemovals++;
            } else if (rightSideHeavy && (((Double) logM.get(iter6)).doubleValue() < meanLogM)) {
              leftsideRemovals++;
            }
            logM.remove(iter6);
            VmatchesA.remove(iter6);
            VmatchesB.remove(iter6);
            bestSums.remove(iter6);
            haveMadeChange = true;
            iter6--;
            logMremovals++;
          }
        }
        //System.out.print("     left heavy? "+leftSideHeavy+"   ");
        //System.out.print("     right heavy? "+rightSideHeavy+"   ");
        //System.out.println("     Balanced? "+balanced+"   ");
        //System.out.println("     Removed "+logMremovals+" triangles on logM filter pass "+numIterations+" with a filter/multiple value of "+multiple);
        //System.out.println("          leftsideRemovals="+leftsideRemovals+"     rightsideRemovals="+rightsideRemovals);
        //System.out.println("          N+ is "+nPLUS+" and N- is "+nMINUS);

        if (!haveMadeChange) {
          stillIterate = false;
        }
        nPLUS = 0;
        nMINUS = 0;
        int iterLimit = VmatchesA.size();
        for (int iter7 = 0; iter7 < iterLimit; iter7++) {
          if (((SpotTriangle) VmatchesA.get(iter7)).clockwise == ((SpotTriangle) VmatchesB.get(iter7)).clockwise) {
            nPLUS++;
          } else {
            nMINUS++;
          }
        }
        mT = Math.abs(nPLUS - (nMINUS));
        mF = nPLUS + nMINUS - mT;

        oldStdDeviationLogM = stdDeviationLogM;
        //System.out.println("          Going into the next round with mT, mF: "+mT+","+mF);
      }


      for (int iter8 = 0; iter8 < VmatchesA.size(); iter8++) {
        if (((SpotTriangle) VmatchesA.get(iter8)).clockwise != ((SpotTriangle) VmatchesB.get(iter8)).clockwise) {
          logM.remove(iter8);
          VmatchesA.remove(iter8);
          VmatchesB.remove(iter8);
          bestSums.remove(iter8);
          iter8--;
        }

      }


      //System.out.println("Going into scoring with "+VmatchesA.size()+" matching triangles.");
      if (VmatchesA.size() == 0) {
        return scores;
      }
      MatchedPoints mp = new MatchedPoints();
      int vMatchL = VmatchesA.size();
      for (int iter10 = 0; iter10 < vMatchL; iter10++) {
        for (int iter11 = 0; iter11 < 3; iter11++) {
          Spot spotA = ((SpotTriangle) VmatchesA.get(iter10)).getVertex(iter11 + 1);
          Spot spotB = ((SpotTriangle) VmatchesB.get(iter10)).getVertex(iter11 + 1);
          //for(int iter12=iter11+1; iter12<VmatchesA.size(); iter12++) {
          int tempPlace = mp.hasMatchedPair(spotA, spotB);
          if (tempPlace != -1) {
            ((VertexPointMatch) mp.get(tempPlace)).points++;

          } else {
            mp.add(new VertexPointMatch(spotA, spotB, 1));
          }
          //	}
        }

      }

      scores = (VertexPointMatch[]) (mp.toArray(scores));
      Arrays.sort(scores, new ScoreComparator());
      ArrayList secondRunSpots = new ArrayList();
      int scoresSize = scores.length;
      secondRunSpots.add(scores[0]);
      int iter20 = 1;
      boolean keepOnCounting = true;
      boolean hasNotBeenSeenYet = true;

      //old way
      /*ArrayList countedSpots=new ArrayList();
   countedSpots.add(new spot(0, scores[0].newX, scores[0].newY));
   while(keepOnCounting&&(iter20<scoresSize)){



       for(int iter30=0;iter30<countedSpots.size();iter30++){
           spot tempSpot=(spot)countedSpots.get(iter30);
           if((scores[iter20].newX==tempSpot.getCentroidX())&&(scores[iter20].newY==tempSpot.getCentroidY())){hasNotBeenSeenYet=false;}
       }

       if((scores[iter20].points>(scores[(iter20-1)].points/2))&&(scores[iter20].points>1)&&(hasNotBeenSeenYet)){
           secondRunSpots.add(scores[iter20]);
           countedSpots.add(new spot(0, scores[iter20].newX, scores[iter20].newY));
           }
       else{keepOnCounting=false;}
       iter20++;
      }*/

      //Zaven's correction
      ArrayList countedSpotsA = new ArrayList();
      ArrayList countedSpotsB = new ArrayList();
      countedSpotsA.add(new Spot(0, scores[0].newX, scores[0].newY));
      countedSpotsB.add(new Spot(0, scores[0].oldX, scores[0].oldY));
      while (keepOnCounting && (iter20 < scoresSize)) {

        for (int iter30 = 0; iter30 < countedSpotsA.size(); iter30++) {
          Spot tempSpot = (Spot) countedSpotsA.get(iter30);
          if ((scores[iter20].newX == tempSpot.getCentroidX()) && (scores[iter20].newY == tempSpot.getCentroidY())) {
            hasNotBeenSeenYet = false;
          }
          tempSpot = (Spot) countedSpotsB.get(iter30);
          if ((scores[iter20].oldX == tempSpot.getCentroidX()) && (scores[iter20].oldY == tempSpot.getCentroidY())) {
            hasNotBeenSeenYet = false;
          }
        }

        if ((scores[iter20].points > (scores[(iter20 - 1)].points / 2)) && (scores[iter20].points > 1) && (hasNotBeenSeenYet)) {
          secondRunSpots.add(scores[iter20]);
          countedSpotsA.add(new Spot(0, scores[iter20].newX, scores[iter20].newY));
          countedSpotsB.add(new Spot(0, scores[iter20].oldX, scores[iter20].oldY));
        } else {
          keepOnCounting = false;
        }
        iter20++;
      }

      VertexPointMatch[] scoredSpots = new VertexPointMatch[0];
      scoredSpots = (VertexPointMatch[]) (secondRunSpots.toArray(scoredSpots));
      return scoredSpots;
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("0 points awarded due to exception in secondGrothPass.");
      return scores;
    }
  }

  /**
   * OLD-DEPRECATED_SCREWY AFFINE - This method allows us to use the I3S match algorithm as well.
   */
  /*
  public I3SMatchObject i3sScan(EncounterLite newEnc, boolean scanRight) {

    //superSpot objects are my equivalent in my DB of Point2D
    
    System.out.println("     Starting I3S scan...");
    
    //these spots are for the unknown encounter
    SuperSpot[] newspotsTemp = new SuperSpot[0];

    //set up the arrays to hold the three reference points for the new and existing encounter
    Point2D[] thisEncControlSpots = new Point2D[3];
    Point2D[] newEncControlSpots = new Point2D[3];


    //populate the reference points...depending on whether right
    //or left-side patterning is to be used.
    if (scanRight) {
      newspotsTemp = (SuperSpot[]) newEnc.getRightSpots().toArray(newspotsTemp);
      newEncControlSpots = newEnc.getThreeRightFiducialPoints();
      thisEncControlSpots = this.getThreeRightFiducialPoints();
    } else {
      newspotsTemp = (SuperSpot[]) newEnc.getSpots().toArray(newspotsTemp);
      newEncControlSpots = newEnc.getThreeLeftFiducialPoints();
      thisEncControlSpots = this.getThreeLeftFiducialPoints();
    }


    //convert the new encounter's spots into a Point2D array
    //previously I determined which side's spots to grab and populated
    //newspotsTemp
    int newSpotsLength = newspotsTemp.length;
    Point2D[] newEncounterSpots = new Point2D[newSpotsLength];
    for (int i = 0; i < newSpotsLength; i++) {
      newEncounterSpots[i] = new Point2D(newspotsTemp[i].getTheSpot().getCentroidX(), newspotsTemp[i].getTheSpot().getCentroidY());
    }

    //convert this existing encounter's spots into a Point2D array
    SuperSpot[] thisSpots = new SuperSpot[0];
    if (scanRight) {
      thisSpots = (SuperSpot[]) getRightSpots().toArray(thisSpots);
    } else {
      thisSpots = (SuperSpot[]) getSpots().toArray(thisSpots);
    }
    int thisSpotsLength = thisSpots.length;
    Point2D[] thisEncounterSpots = new Point2D[thisSpotsLength];
    for (int j = 0; j < thisSpotsLength; j++) {
      thisEncounterSpots[j] = new Point2D(thisSpots[j].getTheSpot().getCentroidX(), thisSpots[j].getTheSpot().getCentroidY());
    }


    //let's create the new fingerprint
    Point2D[] newOrigEncounterSpots = new Point2D[newSpotsLength];
    //System.arraycopy(newEncounterSpots, 0, newOrigEncounterSpots, 0, newSpotsLength);

    // clearly, newEncounterSpots and newOrigEncounterSpots must be of
    // the same length, and newOrigEncounterSpots must already have the data
    for (int z = 0; z < newOrigEncounterSpots.length; z++) {
      newOrigEncounterSpots[z] = new Point2D(newEncounterSpots[z].getX(), newEncounterSpots[z].getY());
    }
    FingerPrint newPrint = new FingerPrint(newOrigEncounterSpots, newEncounterSpots, newEncControlSpots);
    //System.out.println("I have constructed the new fingerprint!");


    //let's create the existing encounter fingerprint
    Point2D[] origThisEncounterSpots = new Point2D[thisSpotsLength];
    //System.arraycopy(thisEncounterSpots, 0, origThisEncounterSpots, 0, thisSpotsLength);
    for (int e = 0; e < thisEncounterSpots.length; e++) {
      origThisEncounterSpots[e] = new Point2D(thisEncounterSpots[e].getX(), thisEncounterSpots[e].getY());
    }

    FingerPrint thisPrint = new FingerPrint(origThisEncounterSpots, thisEncounterSpots, thisEncControlSpots);
    //System.out.println("I have constructed the existing fingerprint!");
    
    //start DTW array creation
    int sizeNewPrint=newPrint.fpp.length;
    int sizeThisPrint=thisPrint.fpp.length;
    
    //Point2D[] thisEncControlSpots = new Point2D[3];
    //Point2D[] newEncControlSpots = new Point2D[3];
    
    
    //Line2D.Double newLeftLine=new Line2D.Double(newEncControlSpots[0].getX(),newEncControlSpots[0].getY(),newEncControlSpots[1].getX(),newEncControlSpots[1].getY());
    //Line2D.Double newRightLine=new Line2D.Double(newEncControlSpots[1].getX(),newEncControlSpots[1].getY(),newEncControlSpots[2].getX(),newEncControlSpots[2].getY());
    

    Builder b1 = TimeSeriesBase.builder();
    //double newHighestControlSpot=newEncControlSpots[0].getY();
    //if(newEncControlSpots[2].getY()>newHighestControlSpot){newHighestControlSpot=newEncControlSpots[2].getY();}
    
    //add the tip
    if(scanRight){
      b1.add(newEncControlSpots[1].getX(),newEncControlSpots[1].getY());  
    }
    else{
      b1.add(newEncControlSpots[0].getX(),newEncControlSpots[0].getY());  
    }
    
    for (int t = 0; t < sizeNewPrint; t++) {
      double myX=newPrint.fpp[t].getX();
      double myY=newPrint.fpp[t].getY();
      System.out.println(" builder: "+myX+","+myY);
      b1.add(myX,myY);     
    }
    //add the notch
    if(scanRight){
      b1.add(newEncControlSpots[2].getX(),newEncControlSpots[2].getY());  
    }
    else{
      b1.add(newEncControlSpots[1].getX(),newEncControlSpots[1].getY());  
    }
    
    TimeSeries ts1=b1.build();
    
    //Line2D.Double thisLeftLine=new Line2D.Double(thisEncControlSpots[0].getX(),thisEncControlSpots[0].getY(),thisEncControlSpots[1].getX(),thisEncControlSpots[1].getY());
    //Line2D.Double thisRightLine=new Line2D.Double(thisEncControlSpots[1].getX(),thisEncControlSpots[1].getY(),thisEncControlSpots[2].getX(),thisEncControlSpots[2].getY());
    
    
    Builder b2 = TimeSeriesBase.builder();
    //double thisHighestControlSpot=thisEncControlSpots[0].getY();
    //if(thisEncControlSpots[2].getY()>thisHighestControlSpot){thisHighestControlSpot=thisEncControlSpots[2].getY();}
    
    if(scanRight){
      b2.add(thisEncControlSpots[1].getX(),thisEncControlSpots[1].getY());  
    }
    else{
      b2.add(thisEncControlSpots[0].getX(),thisEncControlSpots[0].getY());  
    }
    for (int t = 0; t < sizeThisPrint; t++) {
      double myX=thisPrint.fpp[t].getX();
      
      double myY=thisPrint.fpp[t].getY();
      
      
      //run an above/below the line tes
      System.out.println(" builder: "+myX+","+myY);
      b2.add(myX,myY); 
    }
    
    //add end control point
    if(scanRight){
      b2.add(thisEncControlSpots[2].getX(),thisEncControlSpots[2].getY());  
    }
    else{
      b2.add(thisEncControlSpots[1].getX(),thisEncControlSpots[1].getY());  
    }
    
    TimeSeries ts2=b2.build();
    
    TimeWarpInfo twi=FastDTW.compare(ts1, ts2, 30, Distances.EUCLIDEAN_DISTANCE);
    WarpPath wp=twi.getPath();
    String myPath=wp.toString();
    Double distance = new Double(twi.getDistance());
    System.out.println("    !!!!I found a FastDTW score of: "+distance);
    //end DTW array creation
    
    
    //insert Shane's algorithm
    double geroMatchValue=0;
    
    try{
      //System.out.println("     About to start TraceCompare!");
      Fluke newFluke=new Fluke(newEnc);
      //System.out.println("     newFluke created!");
      Fluke thisFluke=new Fluke(this);
      //System.out.println("     thisFluke created!");
      TraceCompare tc = new TraceCompare();
      //System.out.println("     TraceComapre done!");
      ArrayList<Fluke> flukes=new ArrayList<Fluke>();
      flukes.add(thisFluke);
      TreeSet<Fluke> matches = tc.processCatalog(flukes,newFluke);
      
      if(matches.size()>0){
        geroMatchValue=matches.first().getMatchValue();
      }
    }
    catch(Exception fe){fe.printStackTrace();}
    
    
    System.out.println("    !!!!I found a Gero score of: "+geroMatchValue);
    

    //affine transform for scale adjustment
    doAffine(newPrint);
    doAffine(thisPrint);
    
    //let's try some fun intersection analysis
    int newPrintSize=newPrint.fpp.length;
    int thisPrintSize=thisPrint.fpp.length;
    int numIntersections=0;
    StringBuffer anglesOfIntersection=new StringBuffer("");
    for(int i=0;i<(newPrintSize-1);i++){
      //for(int j=i+1;j<newPrintSize;j++){
      int j=i+1;
        
        java.awt.geom.Point2D.Double newStart=(new  java.awt.geom.Point2D.Double(newPrint.getFpp(i).getX(),newPrint.getFpp(i).getY()));
        java.awt.geom.Point2D.Double newEnd=(new  java.awt.geom.Point2D.Double(newPrint.getFpp(j).getX(),newPrint.getFpp(j).getY()) ) ;
        java.awt.geom.Line2D.Double newLine=new java.awt.geom.Line2D.Double(newStart,newEnd  );
      
        //now compare to thisPattern
        for(int m=0;m<(thisPrintSize-1);m++){
 
              int n=m+1;
              
              java.awt.geom.Point2D.Double thisStart=(new  java.awt.geom.Point2D.Double(thisPrint.getFpp(m).getX(),thisPrint.getFpp(m).getY()));
              java.awt.geom.Point2D.Double thisEnd=(new  java.awt.geom.Point2D.Double(thisPrint.getFpp(n).getX(),thisPrint.getFpp(n).getY()) );   
              java.awt.geom.Line2D.Double thisLine=new java.awt.geom.Line2D.Double(thisStart,thisEnd);
              
              if(((newStart.getX()<=thisEnd.getX()))){
                if(newLine.intersectsLine(thisLine)){
                  numIntersections++;
                  String intersectionAngle=Double.toString(angleBetween2Lines(newLine, thisLine));
                  anglesOfIntersection.append(intersectionAngle+",");
                 }
                //else{System.out.println("["+newStart.getX()+","+newStart.getY()+","+newEnd.getX()+","+newEnd.getY()+"]"+" does not intersect with "+"["+thisStart.getX()+","+thisStart.getY()+","+thisEnd.getX()+","+thisEnd.getY()+"]");}
                
                //short circuit to end if the comparison line is past the new line
                if(newEnd.getX()<thisStart.getX()){
                  m=thisPrintSize;
                }
              }
            
           
            
            
            
          
        }
        
      
      //}
      
      
    }
    System.out.println("     Num intersections is: "+numIntersections);
    System.out.println("     Intersection angles: "+anglesOfIntersection.toString());
    
    

    Compare wsCompare = new Compare(thisPrint);
    FingerPrint[] fpBest = new FingerPrint[1];

    TreeMap hm = new TreeMap();

    boolean successfulCompare = wsCompare.find(newPrint, fpBest, 1, true, hm);
    //boolean successfulCompare=wsCompare.compareTwo(newPrint, thisPrint, hm,true);


    if (successfulCompare) {
      System.out.println("Successful I3S compare!");
    } else {
      System.out.println("Error in compare!");
    }


    //now return an I3S match object
    I3SMatchObject i3smo=new I3SMatchObject(belongsToMarkedIndividual, fpBest[0].getScore(), encounterNumber, sex, getDate(), size, hm, 0, distance, geroMatchValue);
    i3smo.setFastDTWPath(wp.toString());
    i3smo.setIntersectionCount(numIntersections);
    i3smo.setAnglesOfIntersections(anglesOfIntersection.toString());
    return i3smo;
  }
  */

  public void doAffine(FingerPrint fp) {
    double[] matrix = new double[6];
    Affine.calcAffine(fp.control[0].getX(), fp.control[0].getY(), fp.control[1].getX(), fp.control[1].getY(), fp.control[2].getX(), fp.control[2].getY(), 100, 100, 900, 100, 500, 700, matrix);

    for (int i = 0; i < fp.orig.length; i++) {
      fp.fpp[i].x = matrix[0] * fp.orig[i].getX() + matrix[1] * fp.orig[i].getY() + matrix[2];
      fp.fpp[i].y = matrix[3] * fp.orig[i].getX() + matrix[4] * fp.orig[i].getY() + matrix[5];
    }

    //System.out.println("Enter encounterLite.doAffine");

  }

  public com.reijns.I3S.Point2D[] getThreeLeftFiducialPoints() {
    com.reijns.I3S.Point2D[] Rray = new com.reijns.I3S.Point2D[3];
    if ((getLeftReferenceSpots() != null) && (getLeftReferenceSpots().length == 3)) {

      SuperSpot[] refsLeft = getLeftReferenceSpots();

      Rray[0] = new com.reijns.I3S.Point2D(refsLeft[0].getTheSpot().getCentroidX(), refsLeft[0].getTheSpot().getCentroidY());
      Rray[1] = new com.reijns.I3S.Point2D(refsLeft[1].getTheSpot().getCentroidX(), refsLeft[1].getTheSpot().getCentroidY());
      Rray[2] = new com.reijns.I3S.Point2D(refsLeft[2].getTheSpot().getCentroidX(), refsLeft[2].getTheSpot().getCentroidY());
      //System.out.println("	I found three left reference points! The first is:"+refsLeft[0].getTheSpot().getCentroidX()+","+refsLeft[0].getTheSpot().getCentroidY()+" and "+refsLeft[1].getTheSpot().getCentroidX()+","+refsLeft[1].getTheSpot().getCentroidY()+" and "+refsLeft[2].getTheSpot().getCentroidX()+","+refsLeft[2].getTheSpot().getCentroidY());

    } else {
      com.reijns.I3S.Point2D topLeft = new com.reijns.I3S.Point2D(getLeftmostSpot(), getHighestSpot());
      com.reijns.I3S.Point2D bottomLeft = new com.reijns.I3S.Point2D(getLeftmostSpot(), getLowestSpot());
      com.reijns.I3S.Point2D bottomRight = new com.reijns.I3S.Point2D(getRightmostSpot(), getLowestSpot());
      Rray[0] = topLeft;
      Rray[1] = bottomLeft;
      Rray[2] = bottomRight;
      //System.out.println("	I made up three left reference points!");
    }

    return Rray;
  }

  public com.reijns.I3S.Point2D[] getThreeRightFiducialPoints() {
    com.reijns.I3S.Point2D[] Rray = new com.reijns.I3S.Point2D[3];
    if ((getRightReferenceSpots() != null) && (getRightReferenceSpots().length == 3)) {
      SuperSpot[] refsRight = getRightReferenceSpots();
      Rray[0] = new com.reijns.I3S.Point2D(refsRight[0].getTheSpot().getCentroidX(), refsRight[0].getTheSpot().getCentroidY());
      Rray[1] = new com.reijns.I3S.Point2D(refsRight[1].getTheSpot().getCentroidX(), refsRight[1].getTheSpot().getCentroidY());
      Rray[2] = new com.reijns.I3S.Point2D(refsRight[2].getTheSpot().getCentroidX(), refsRight[2].getTheSpot().getCentroidY());

    } else {

      com.reijns.I3S.Point2D topRight = new com.reijns.I3S.Point2D(getRightmostRightSpot(), getHighestRightSpot());
      com.reijns.I3S.Point2D bottomRight = new com.reijns.I3S.Point2D(getRightmostRightSpot(), getLowestRightSpot());
      com.reijns.I3S.Point2D bottomLeft = new com.reijns.I3S.Point2D(getLeftmostRightSpot(), getLowestRightSpot());

      Rray[0] = topRight;
      Rray[1] = bottomRight;
      Rray[2] = bottomLeft;
    }
    return Rray;
  }

  public double getRightmostSpot() {
    SuperSpot[] spots = new SuperSpot[0];
    spots = (SuperSpot[]) getSpots().toArray(spots);
    double rightest = 0;
    for (int iter = 0; iter < spots.length; iter++) {
      if (spots[iter].getCentroidX() > rightest) {
        rightest = spots[iter].getCentroidX();
      }
    }
    return rightest;
  }

  public double getLeftmostSpot() {
    SuperSpot[] spots = new SuperSpot[0];
    spots = (SuperSpot[]) getSpots().toArray(spots);
    double leftest = getRightmostSpot();
    for (int iter = 0; iter < spots.length; iter++) {
      if (spots[iter].getCentroidX() < leftest) {
        leftest = spots[iter].getCentroidX();
      }
    }
    return leftest;
  }

  public double getHighestSpot() {
    SuperSpot[] spots = new SuperSpot[0];
    spots = (SuperSpot[]) getSpots().toArray(spots);
    double highest = getLowestSpot();
    for (int iter = 0; iter < spots.length; iter++) {
      if (spots[iter].getCentroidY() < highest) {
        highest = spots[iter].getCentroidY();
      }
    }
    return highest;
  }

  public double getLowestSpot() {
    SuperSpot[] spots = new SuperSpot[0];
    spots = (SuperSpot[]) getSpots().toArray(spots);
    double lowest = 0;
    for (int iter = 0; iter < spots.length; iter++) {
      if (spots[iter].getCentroidY() > lowest) {
        lowest = spots[iter].getCentroidY();
      }
    }
    return lowest;
  }

  public double getRightmostRightSpot() {
    SuperSpot[] spots = new SuperSpot[0];
    spots = (SuperSpot[]) getRightSpots().toArray(spots);
    double rightest = 0;
    for (int iter = 0; iter < spots.length; iter++) {
      if (spots[iter].getCentroidX() > rightest) {
        rightest = spots[iter].getCentroidX();
      }
    }
    return rightest;
  }


  public double getLeftmostRightSpot() {
    SuperSpot[] spots = new SuperSpot[0];
    spots = (SuperSpot[]) getRightSpots().toArray(spots);
    double leftest = getRightmostRightSpot();
    for (int iter = 0; iter < spots.length; iter++) {
      if (spots[iter].getCentroidX() < leftest) {
        leftest = spots[iter].getCentroidX();
      }
    }
    return leftest;
  }

  public double getHighestRightSpot() {
    SuperSpot[] spots = new SuperSpot[0];
    spots = (SuperSpot[]) getRightSpots().toArray(spots);
    double highest = getLowestRightSpot();
    for (int iter = 0; iter < spots.length; iter++) {
      if (spots[iter].getCentroidY() < highest) {
        highest = spots[iter].getCentroidY();
      }
    }
    return highest;
  }

  public double getLowestRightSpot() {
    SuperSpot[] spots = new SuperSpot[0];
    spots = (SuperSpot[]) getRightSpots().toArray(spots);
    double lowest = 0;
    for (int iter = 0; iter < spots.length; iter++) {
      if (spots[iter].getCentroidY() > lowest) {
        lowest = spots[iter].getCentroidY();
      }
    }
    return lowest;
  }


  public void processLeftSpots(ArrayList<org.ecocean.SuperSpot> initSpots) {
    int length = initSpots.size();
    spotsX = new double[length];
    spotsY = new double[length];
    for (int q = 0; q < length; q++) {
      spotsX[q] = initSpots.get(q).getCentroidX();
      spotsY[q] = initSpots.get(q).getCentroidY();
    }
  }

  public void processLeftReferenceSpots(ArrayList<org.ecocean.SuperSpot> initSpots) {
    int length = initSpots.size();
    leftReferenceSpotsX = new double[length];
    leftReferenceSpotsY = new double[length];
    for (int q = 0; q < length; q++) {
      leftReferenceSpotsX[q] = initSpots.get(q).getCentroidX();
      leftReferenceSpotsY[q] = initSpots.get(q).getCentroidY();
      //System.out.println("     Left reference spot "+q+": "+initSpots.get(q).getCentroidX()+","+initSpots.get(q).getCentroidY());
    }
  }

  public void processRightSpots(ArrayList<org.ecocean.SuperSpot> initSpots) {
    int length = initSpots.size();
    rightSpotsX = new double[length];
    rightSpotsY = new double[length];
    for (int q = 0; q < length; q++) {
      rightSpotsX[q] = initSpots.get(q).getCentroidX();
      rightSpotsY[q] = initSpots.get(q).getCentroidY();
    }
  }

  public void processRightReferenceSpots(ArrayList<org.ecocean.SuperSpot> initSpots) {
    int length = initSpots.size();
    rightReferenceSpotsX = new double[length];
    rightReferenceSpotsY = new double[length];
    for (int q = 0; q < length; q++) {
      rightReferenceSpotsX[q] = initSpots.get(q).getCentroidX();
      rightReferenceSpotsY[q] = initSpots.get(q).getCentroidY();
    }
  }

private double amplifyY(double origValue, double s){
  double yValue=origValue;
  
  //amplify the Y, which has already been normalized 0 to 1
  yValue=yValue/(1-yValue);
  
  if(s<0){yValue=yValue*-1;}
  
  return yValue;
  
}

  public String getDynamicPropertyValue(String name) {
    if (dynamicProperties != null) {
      name = name.replaceAll("%20", " ");
      //let's create a TreeMap of the properties
      TreeMap<String, String> tm = new TreeMap<String, String>();
      StringTokenizer st = new StringTokenizer(dynamicProperties, ";");
      while (st.hasMoreTokens()) {
        String token = st.nextToken();
        int equalPlace = token.indexOf("=");
        tm.put(token.substring(0, equalPlace), token.substring(equalPlace + 1));
      }
      if (tm.containsKey(name)) {
        return tm.get(name);
      }
    }
    return null;
  }

  public static double angleBetween2Lines(java.awt.geom.Line2D.Double line1, java.awt.geom.Line2D.Double line2){
      double angle1 = Math.atan2(line1.getY1() - line1.getY2(),
                                 line1.getX1() - line1.getX2());
      double angle2 = Math.atan2(line2.getY1() - line2.getY2(),
                                 line2.getX1() - line2.getX2());
      return Math.abs(angle1-angle2);
  }

  
  public static AffineTransform deriveAffineTransform(
      double oldX1, double oldY1,
      double oldX2, double oldY2,
      double oldX3, double oldY3,
      double newX1, double newY1,
      double newX2, double newY2,
      double newX3, double newY3) {

  double[][] oldData = { {oldX1, oldX2, oldX3}, {oldY1, oldY2, oldY3}, {1, 1, 1} };
  RealMatrix oldMatrix = MatrixUtils.createRealMatrix(oldData);

  double[][] newData = { {newX1, newX2, newX3}, {newY1, newY2, newY3} };
  RealMatrix newMatrix = MatrixUtils.createRealMatrix(newData);

  RealMatrix inverseOld = new LUDecomposition(oldMatrix).getSolver().getInverse();
  RealMatrix transformationMatrix = newMatrix.multiply(inverseOld);

  double m00 = transformationMatrix.getEntry(0, 0);
  double m01 = transformationMatrix.getEntry(0, 1);
  double m02 = transformationMatrix.getEntry(0, 2);
  double m10 = transformationMatrix.getEntry(1, 0);
  double m11 = transformationMatrix.getEntry(1, 1);
  double m12 = transformationMatrix.getEntry(1, 2);

  return new AffineTransform(m00, m10, m01, m11, m02, m12);       
}
  
  public static AffineTransform deriveAffineTransformIgnoreNotch(
      double oldX1, double oldY1,
      double oldX2, double oldY2,
      double oldX3, double oldY3,
      double newX1, double newY1,
      double newX2, double newY2,
      double newX3, double newY3) {
    
    
          //we can't trust the notch point, too subject to skew
          //so we're going to ignore the notch points: oldX2,oldY2,newX2,newY2
          //instead we need to calculate new values assuming a right triangle with the tip points
          /*
          java.awt.geom.Point2D.Double oldMidpoint=new java.awt.geom.Point2D.Double(((oldX1+oldX3)/2),((oldY1+oldY3)/2));
          java.awt.geom.Point2D.Double oldRightFlukePoint=new java.awt.geom.Point2D.Double(oldX3,oldY3);
          double oldHalfWidth=oldRightFlukePoint.distance(oldMidpoint);
          double oldHypotenuse=oldHalfWidth/Math.cos(Math.toRadians(45));
          double oldDepth=Math.sqrt(oldHypotenuse*oldHypotenuse-oldHalfWidth*oldHalfWidth);
          double oldDx=oldX1-oldX3;
          double oldSlope=(oldDx)/(oldY1-oldY3);
          double oldNotchCalculatedX = Math.sqrt(Math.abs(oldDepth*oldDepth - oldDx*oldDx)) / oldSlope + oldMidpoint.getX();
          double oldNotchCalculatedY = oldSlope * (oldNotchCalculatedX - oldMidpoint.getX()) - oldMidpoint.getY();
          */
          
          //TRY PROPORTIONS
          double width1=oldX3-oldX1;
          double width2=newX3-newX1;

          //heights are from control spot 1 to intersection with line formed by control spots 0 and 2
          java.awt.geom.Point2D.Double notchControlPoint1=new java.awt.geom.Point2D.Double(oldX2,oldY2);
          java.awt.geom.Line2D.Double widthLine1=new java.awt.geom.Line2D.Double(new java.awt.geom.Point2D.Double(oldX1,oldY1),new java.awt.geom.Point2D.Double(oldX3,oldY3));
          double height1=widthLine1.ptLineDist(new java.awt.geom.Point2D.Double(oldX2,oldY2));

          //heights are from control spot 1 to intersection with line formed by control spots 0 and 2
          java.awt.geom.Point2D.Double notchControlPoint2=new java.awt.geom.Point2D.Double(newX2,newY2);
          java.awt.geom.Line2D.Double widthLine2=new java.awt.geom.Line2D.Double(new java.awt.geom.Point2D.Double(newX1,newY1),new java.awt.geom.Point2D.Double(newX3,newY3));
          double height2=widthLine2.ptLineDist(new java.awt.geom.Point2D.Double(newX2,newY2));
          
          
          /*
          double finalNum=(height1/width1)/(height2/width2);
          
          //based on the sign of finalNum, let's adjust our height 1
          if(finalNum>1){
            
            //oldEncounter height needs to be reduced
            double newHeight=height2*width1/(width2*height1*finalNum);
            
          }
          else if(finalNum<1>){
            
            //old encounter height needs to decreased by %
            
          }
          */
          
          //Jon's method
          java.awt.geom.Point2D.Double thirdIsoscelesPoint=deriveThirdIsoscelesPoint(oldX1, oldX2, oldY1, oldY2);

          
          
          //TRY PROPORTIONS
          
          //original
          //double[][] oldData = { {oldX1, oldX2, oldX3}, {oldY1, oldY2, oldY3}, {1, 1, 1} };
          //calculate notch as third point in an isosceles triangle
          double[][] oldData = { {oldX1, thirdIsoscelesPoint.getX(), oldX3}, {oldY1, thirdIsoscelesPoint.getY(), oldY3}, {1, 1, 1} };
          //just stretch notch point down via proportions
          
          RealMatrix oldMatrix = MatrixUtils.createRealMatrix(oldData);
        
          /*
          java.awt.geom.Point2D.Double newMidpoint=new java.awt.geom.Point2D.Double(((newX1+newX3)/2),((newY1+newY3)/2));
          
          java.awt.geom.Point2D.Double newRightFlukePoint=new java.awt.geom.Point2D.Double(newX3,newY3);
          double newHalfWidth=oldRightFlukePoint.distance(newMidpoint);
          double newHypotenuse=newHalfWidth/Math.cos(Math.toRadians(45));
          double newDepth=Math.sqrt(newHypotenuse*newHypotenuse-newHalfWidth*newHalfWidth);
          double newDx=newX1-newX3;
          double newSlope=(newDx)/(newY1-newY3);
          double newNotchCalculatedX = Math.sqrt(Math.abs(newDepth*newDepth - newDx*newDx)) / newSlope + newMidpoint.getX();
          double newNotchCalculatedY = newSlope * (newNotchCalculatedX - newMidpoint.getX()) - newMidpoint.getY();
          */
          
        //Jon's method
          java.awt.geom.Point2D.Double thirdIsoscelesPoint2=deriveThirdIsoscelesPoint(newX1, newX2, newY1, newY2);

          
          //original
          //double[][] newData = { {newX1, newX2, newX3}, {newY1, newY2, newY3} };
          //calculate notch as third point in an isosceles triangles
          double[][] newData = { {newX1, thirdIsoscelesPoint2.getX(), newX3}, {newY1, thirdIsoscelesPoint2.getY(), newY3} };
          //just stretch notch point down
          
          RealMatrix newMatrix = MatrixUtils.createRealMatrix(newData);
        
          RealMatrix inverseOld = new LUDecomposition(oldMatrix).getSolver().getInverse();
          RealMatrix transformationMatrix = newMatrix.multiply(inverseOld);
        
          double m00 = transformationMatrix.getEntry(0, 0);
          double m01 = transformationMatrix.getEntry(0, 1);
          double m02 = transformationMatrix.getEntry(0, 2);
          double m10 = transformationMatrix.getEntry(1, 0);
          double m11 = transformationMatrix.getEntry(1, 1);
          double m12 = transformationMatrix.getEntry(1, 2);
        
          return new AffineTransform(m00, m10, m01, m11, m02, m12);       
}
  
  public static AffineTransform calculateTransform(java.awt.geom.Point2D.Double[] src, java.awt.geom.Point2D.Double[] dst) {
    Array2DRowRealMatrix x = new Array2DRowRealMatrix(new double[][] {
    { src[0].getX(), src[1].getX(), src[2].getX() }, { src[0].getY(), src[1].getY(), src[2].getY() },
    { 1, 1, 1 } });
    Array2DRowRealMatrix y = new Array2DRowRealMatrix(new double[][] {
    { dst[0].getX(), dst[1].getX(), dst[2].getX() }, { dst[0].getY(), dst[1].getY(), dst[2].getY() },
    { 0, 0, 0 } });
    
    double[][] data = y.multiply(new LUDecomposition(x).getSolver().getInverse()).getData();
    
    return new AffineTransform(
        new double[] { data[0][0], data[1][0], data[0][1], data[1][1], data[0][2], data[1][2] }
     );
    }
  
  
  
  //start new I3S
  public static I3SMatchObject improvedI3SScan(EncounterLite newEnc, EncounterLite oldEnc) {

    try{
    //superSpot objects are my equivalent in my DB of Point2D
    
    System.out.println("     Starting I3S scan...");
    
    //these spots are for the unknown encounter
    //SuperSpot[] newspotsTemp = new SuperSpot[0];
    //SuperSpot[] oldspotsTemp = new SuperSpot[0];

    //populate the reference points...depending on whether right
    //or left-side patterning is to be used.
   
    ArrayList<SuperSpot> spots2=newEnc.getSpots();
    if(newEnc.getRightSpots()!=null){
      spots2.addAll(newEnc.getRightSpots());
    }
    System.out.println("     I3S trying to sort first spot array of size: "+spots2.size());
    Collections.sort(spots2, new XComparator());
    //newspotsTemp=(SuperSpot[])spots2.toArray();
    
    ArrayList<SuperSpot> spots=oldEnc.getSpots();
    if(oldEnc.getRightSpots()!=null){
      spots.addAll(oldEnc.getRightSpots());
    }
    System.out.println("     I3S trying to sort second spot array of size: "+spots.size());
    
    Collections.sort(spots, new XComparator());
    //oldspotsTemp=(SuperSpot[])spots.toArray();
    
    com.reijns.I3S.Point2D[] newEncControlSpots=new com.reijns.I3S.Point2D[3];
    
    /*
    
    for(int i=0;i<spots2.size();i++){
      SuperSpot mySpot=spots2.get(i);
      
      //get the rightmost spot
      if(mySpot.getCentroidX()>newEncControlSpots[2].getX()){newEncControlSpots[2]=new com.reijns.I3S.Point2D(mySpot.getCentroidX(),mySpot.getCentroidY());}
      
      //get the bottommost spot
      if(mySpot.getCentroidY()>newEncControlSpots[1].getY()){newEncControlSpots[1]=new com.reijns.I3S.Point2D(mySpot.getCentroidX(),mySpot.getCentroidY());}
      
      //get the leftmost spot
      if(mySpot.getCentroidX()<newEncControlSpots[0].getX()){newEncControlSpots[0]=new com.reijns.I3S.Point2D(mySpot.getCentroidX(),mySpot.getCentroidY());} 
    } 
    */
    
    //return to using refSpots
    newEncControlSpots[0]=new com.reijns.I3S.Point2D(newEnc.getLeftReferenceSpots()[0].getCentroidX(),newEnc.getLeftReferenceSpots()[0].getCentroidY());
    newEncControlSpots[1]=new com.reijns.I3S.Point2D(newEnc.getLeftReferenceSpots()[1].getCentroidX(),newEnc.getLeftReferenceSpots()[1].getCentroidY());
    newEncControlSpots[2]=new com.reijns.I3S.Point2D(newEnc.getLeftReferenceSpots()[2].getCentroidX(),newEnc.getLeftReferenceSpots()[2].getCentroidY());
    
    com.reijns.I3S.Point2D[] theEncControlSpots=new com.reijns.I3S.Point2D[3];
    
    /*
    for(int i=0;i<spots.size();i++){
      SuperSpot mySpot=spots.get(i);
      
      //get the rightmost spot
      if(mySpot.getCentroidX()>theEncControlSpots[2].getX()){theEncControlSpots[2]=new com.reijns.I3S.Point2D(mySpot.getCentroidX(),mySpot.getCentroidY());}
      
      //get the bottommost spot
      if(mySpot.getCentroidY()>theEncControlSpots[1].getY()){theEncControlSpots[1]=new com.reijns.I3S.Point2D(mySpot.getCentroidX(),mySpot.getCentroidY());}
      
      //get the leftmost spot
      if(mySpot.getCentroidX()<theEncControlSpots[0].getX()){theEncControlSpots[0]=new com.reijns.I3S.Point2D(mySpot.getCentroidX(),mySpot.getCentroidY());} 
    } */
    
    //return to using persisted refSpots
    //return to using refSpots
    theEncControlSpots[0]=new com.reijns.I3S.Point2D(oldEnc.getLeftReferenceSpots()[0].getCentroidX(),oldEnc.getLeftReferenceSpots()[0].getCentroidY());
    theEncControlSpots[1]=new com.reijns.I3S.Point2D(oldEnc.getLeftReferenceSpots()[1].getCentroidX(),oldEnc.getLeftReferenceSpots()[1].getCentroidY());
    theEncControlSpots[2]=new com.reijns.I3S.Point2D(oldEnc.getLeftReferenceSpots()[2].getCentroidX(),oldEnc.getLeftReferenceSpots()[2].getCentroidY());
    
  
    //convert the new encounter's spots into a Point2D array
    //previously I determined which side's spots to grab and populated
    int newSpotsLength = spots2.size();
    Point2D[] newEncounterSpots = new Point2D[newSpotsLength];
    Point2D[] newOrigEncounterSpots = new Point2D[newSpotsLength];
    
    //Do our affine here
    AffineTransform at=deriveAffineTransformIgnoreNotch(
        newEncControlSpots[0].getX(),
        newEncControlSpots[0].getY(),
        newEncControlSpots[1].getX(),
        newEncControlSpots[1].getY(),
        newEncControlSpots[2].getX(),
        newEncControlSpots[2].getY(),
        theEncControlSpots[0].getX(),
        theEncControlSpots[0].getY(),
        theEncControlSpots[1].getX(),
        theEncControlSpots[1].getY(),
        theEncControlSpots[2].getX(),
        theEncControlSpots[2].getY()
   );   
    
    // clearly, newEncounterSpots and newOrigEncounterSpots must be of
    // the same length, and newOrigEncounterSpots must already have the data
    for (int z = 0; z < newOrigEncounterSpots.length; z++) {
      
      java.awt.geom.Point2D.Double originalPoint=new java.awt.geom.Point2D.Double(spots2.get(z).getCentroidX(),spots2.get(z).getCentroidY());
      java.awt.geom.Point2D.Double transformedPoint=new java.awt.geom.Point2D.Double();
      at.transform(originalPoint, transformedPoint);
      
      newOrigEncounterSpots[z] = new Point2D(originalPoint.getX(), originalPoint.getY());
      newEncounterSpots[z] = new Point2D(transformedPoint.getX(), transformedPoint.getY());
      
    }
    FingerPrint newPrint = new FingerPrint(newOrigEncounterSpots, newEncounterSpots, newEncControlSpots);
    //System.out.println("I have constructed the new fingerprint!");


    //let's create the existing encounter fingerprint
    int spotsSize=spots.size();
    Point2D[] origThisEncounterSpots = new Point2D[spotsSize];
    Point2D[] theEncounterSpots = new Point2D[spotsSize];
    //System.arraycopy(thisEncounterSpots, 0, origThisEncounterSpots, 0, thisSpotsLength);
    for (int e = 0; e < spotsSize; e++) {
           origThisEncounterSpots[e] = new Point2D(spots.get(e).getCentroidX(), spots.get(e).getCentroidY());
           theEncounterSpots[e] = new Point2D(spots.get(e).getCentroidX(), spots.get(e).getCentroidY());
    }

    FingerPrint thisPrint = new FingerPrint(origThisEncounterSpots, theEncounterSpots, theEncControlSpots);
    
    Compare wsCompare = new Compare(thisPrint);
    FingerPrint[] fpBest = new FingerPrint[1];

    TreeMap hm = new TreeMap();

    boolean successfulCompare = wsCompare.find(newPrint, fpBest, 1, true, hm);
    //boolean successfulCompare=wsCompare.compareTwo(newPrint, thisPrint, hm,true);

    //fpBest[0].getScore();
    System.out.println("About to report out an I3S score of: "+fpBest[0].getScore());
    
  //now return an I3S match object
    I3SMatchObject i3smo=new I3SMatchObject(fpBest[0].getScore(),hm);
    return i3smo;
    
    }
    catch(Exception e){
      e.printStackTrace();
      //punt with a high score of 2
      I3SMatchObject i3smo=new I3SMatchObject(-1.0,null);
      return i3smo;
    }
  }
  //end new I3S with affine
  
  
  public static TimeWarpInfo fastDTW(EncounterLite theEnc,EncounterLite theEnc2, int radius){
    try{
      ArrayList<SuperSpot> spots=new ArrayList<SuperSpot>();
      if(theEnc.getSpots()!=null){spots.addAll(theEnc.getSpots());}
      if(theEnc.getRightSpots()!=null){spots.addAll(theEnc.getRightSpots());}
        //newEncControlSpots = theEnc.getThreeLeftFiducialPoints();
        
        
        java.awt.geom.Point2D.Double[] theEncControlSpots=new java.awt.geom.Point2D.Double[3];
        theEncControlSpots[0]=new java.awt.geom.Point2D.Double(9999999,0);
        theEncControlSpots[1]=new java.awt.geom.Point2D.Double(0,-999999);
        theEncControlSpots[2]=new java.awt.geom.Point2D.Double(-99999,0);
        Builder theBuilder = TimeSeriesBase.builder();
        for(int i=0;i<spots.size();i++){
          SuperSpot mySpot=spots.get(i);
          
          //get the rightmost spot
          if(mySpot.getCentroidX()>theEncControlSpots[2].getX()){theEncControlSpots[2]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
          
          //get the bottommost spot
          if(mySpot.getCentroidY()>theEncControlSpots[1].getY()){theEncControlSpots[1]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
          
          //get the leftmost spot
          if(mySpot.getCentroidX()<theEncControlSpots[0].getX()){theEncControlSpots[0]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
          
          //let's do our FastDTW stuff too
          
          theBuilder.add(spots.get(i).getCentroidX(),spots.get(i).getCentroidY());  
            
          
        } 
        
        TimeSeries theTimeSeries=theBuilder.build();
        
        
        //let's create theEnc fingerprint
        SuperSpot[] newspotsTemp = new SuperSpot[0];
        newspotsTemp = (SuperSpot[]) spots.toArray(newspotsTemp);
        int newSpotsLength = newspotsTemp.length;
        java.awt.geom.Point2D.Double[] newEncounterSpots = new java.awt.geom.Point2D.Double[newSpotsLength];
        for (int i = 0; i < newSpotsLength; i++) {
          newEncounterSpots[i] = new java.awt.geom.Point2D.Double(newspotsTemp[i].getTheSpot().getCentroidX(), newspotsTemp[i].getTheSpot().getCentroidY());
        }
        java.awt.geom.Point2D.Double[] newOrigEncounterSpots = new java.awt.geom.Point2D.Double[spots.size()];
         for (int z = 0; z < newOrigEncounterSpots.length; z++) {
          newOrigEncounterSpots[z] = new java.awt.geom.Point2D.Double(spots.get(z).getCentroidX(), spots.get(z).getCentroidY());
        }
  
  
      
      //EncounterLite enc=new EncounterLite(theEnc);
      // = new Point2D[3];
      //SuperSpot[] newspotsTemp = new SuperSpot[0];
      //newspotsTemp = (SuperSpot[]) enc.getRightSpots().toArray(newspotsTemp);
      ArrayList<SuperSpot> spots2=new ArrayList<SuperSpot>();
      if(theEnc2.getSpots()!=null){spots2.addAll(theEnc2.getSpots());}
      if(theEnc2.getRightSpots()!=null){spots2.addAll(theEnc2.getRightSpots());}
        //newEncControlSpots = theEnc2.getThreeLeftFiducialPoints();
        
        java.awt.geom.Point2D.Double[] newEncControlSpots=new java.awt.geom.Point2D.Double[3];
        newEncControlSpots[0]=new java.awt.geom.Point2D.Double(9999999,0);
        newEncControlSpots[1]=new java.awt.geom.Point2D.Double(0,-999999);
        newEncControlSpots[2]=new java.awt.geom.Point2D.Double(-99999,0);
        Builder newBuilder = TimeSeriesBase.builder();
        
        for(int i=0;i<spots2.size();i++){
          SuperSpot mySpot=spots2.get(i);
          
          //get the rightmost spot
          if(mySpot.getCentroidX()>newEncControlSpots[2].getX()){newEncControlSpots[2]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
          
          //get the bottommost spot
          if(mySpot.getCentroidY()>newEncControlSpots[1].getY()){newEncControlSpots[1]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
          
          //get the leftmost spot
          if(mySpot.getCentroidX()<newEncControlSpots[0].getX()){newEncControlSpots[0]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
          
          newBuilder.add(spots2.get(i).getCentroidX(),spots2.get(i).getCentroidY());  
          
        } 
        
        TimeSeries newTimeSeries=newBuilder.build();
        
        /*
        AffineTransform at=EncounterLite.deriveAffineTransform(
            newEncControlSpots[0].getX(),
            newEncControlSpots[0].getY(),
            newEncControlSpots[1].getX(),
            newEncControlSpots[1].getY(),
            newEncControlSpots[2].getX(),
            newEncControlSpots[2].getY(),
            theEncControlSpots[0].getX(),
            theEncControlSpots[0].getY(),
            theEncControlSpots[1].getX(),
            theEncControlSpots[1].getY(),
            theEncControlSpots[2].getX(),
            theEncControlSpots[2].getY()
       ); 
       */    
       
        return FastDTW.compare(theTimeSeries, newTimeSeries, radius, Distances.EUCLIDEAN_DISTANCE);
        
    }
    catch(Exception e){
      e.printStackTrace();
      return null;
    }
  }
    
    public static Double getHolmbergIntersectionScore_OLDER(EncounterLite theEnc,EncounterLite theEnc2){
      
      try{
        ArrayList<SuperSpot> spots=new ArrayList<SuperSpot>();
        if(theEnc.getSpots()!=null){spots.addAll(theEnc.getSpots());}
        if(theEnc.getRightSpots()!=null){spots.addAll(theEnc.getRightSpots());}
          //newEncControlSpots = theEnc.getThreeLeftFiducialPoints();
          
        
        //sort the Array - lowest x to highest X coordinate
        Collections.sort(spots, new XComparator());
        
          
          java.awt.geom.Point2D.Double[] theEncControlSpots=new java.awt.geom.Point2D.Double[3];
          //Builder theBuilder = TimeSeriesBase.builder();
          
          /*
          for(int i=0;i<spots.size();i++){
            SuperSpot mySpot=spots.get(i);
            
            //get the rightmost spot
            if(mySpot.getCentroidX()>theEncControlSpots[2].getX()){theEncControlSpots[2]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
            
            //get the bottommost spot
            if(mySpot.getCentroidY()>theEncControlSpots[1].getY()){theEncControlSpots[1]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
            
            //get the leftmost spot
            if(mySpot.getCentroidX()<theEncControlSpots[0].getX()){theEncControlSpots[0]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
            
            //let's do our FastDTW stuff too
            
            //theBuilder.add(spots.get(i).getCentroidX(),spots.get(i).getCentroidY());  
              
            
          }
          */ 
          

    
        
        //EncounterLite enc=new EncounterLite(theEnc);
        // = new Point2D[3];
        //SuperSpot[] newspotsTemp = new SuperSpot[0];
        //newspotsTemp = (SuperSpot[]) enc.getRightSpots().toArray(newspotsTemp);
        ArrayList<SuperSpot> spots2=new ArrayList<SuperSpot>();
        if(theEnc2.getSpots()!=null){spots2.addAll(theEnc2.getSpots());}
        if(theEnc2.getRightSpots()!=null){spots2.addAll(theEnc2.getRightSpots());}
          //newEncControlSpots = theEnc2.getThreeLeftFiducialPoints();
          
          java.awt.geom.Point2D.Double[] newEncControlSpots=new java.awt.geom.Point2D.Double[3];
         //Builder newBuilder = TimeSeriesBase.builder();
          
          /*
          for(int i=0;i<spots2.size();i++){
            SuperSpot mySpot=spots2.get(i);
            
            //get the rightmost spot
            if(mySpot.getCentroidX()>newEncControlSpots[2].getX()){newEncControlSpots[2]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
            
            //get the bottommost spot
            if(mySpot.getCentroidY()>newEncControlSpots[1].getY()){newEncControlSpots[1]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
            
            //get the leftmost spot
            if(mySpot.getCentroidX()<newEncControlSpots[0].getX()){newEncControlSpots[0]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
            
            //newBuilder.add(spots2.get(i).getCentroidX(),spots2.get(i).getCentroidY());  
            
          } 
          */
          
          
          /*
          for(int i=0;i<spots.size();i++){
            SuperSpot mySpot=spots.get(i);
            
            //get the rightmost spot
            if(mySpot.getCentroidX()>theEncControlSpots[2].getX()){theEncControlSpots[2]=new com.reijns.I3S.Point2D(mySpot.getCentroidX(),mySpot.getCentroidY());}
            
            //get the bottommost spot
            if(mySpot.getCentroidY()>theEncControlSpots[1].getY()){theEncControlSpots[1]=new com.reijns.I3S.Point2D(mySpot.getCentroidX(),mySpot.getCentroidY());}
            
            //get the leftmost spot
            if(mySpot.getCentroidX()<theEncControlSpots[0].getX()){theEncControlSpots[0]=new com.reijns.I3S.Point2D(mySpot.getCentroidX(),mySpot.getCentroidY());} 
          } */
          
          
          newEncControlSpots[0]=new java.awt.geom.Point2D.Double(theEnc2.getLeftReferenceSpots()[0].getCentroidX(),theEnc2.getLeftReferenceSpots()[0].getCentroidY());
          newEncControlSpots[1]=new java.awt.geom.Point2D.Double(theEnc2.getLeftReferenceSpots()[1].getCentroidX(),theEnc2.getLeftReferenceSpots()[1].getCentroidY());
          newEncControlSpots[2]=new java.awt.geom.Point2D.Double(theEnc2.getLeftReferenceSpots()[2].getCentroidX(),theEnc2.getLeftReferenceSpots()[2].getCentroidY());
          
          
          //return to using persisted refSpots
          //return to using refSpots
          theEncControlSpots[0]=new java.awt.geom.Point2D.Double(theEnc.getLeftReferenceSpots()[0].getCentroidX(),theEnc.getLeftReferenceSpots()[0].getCentroidY());
          theEncControlSpots[1]=new java.awt.geom.Point2D.Double(theEnc.getLeftReferenceSpots()[1].getCentroidX(),theEnc.getLeftReferenceSpots()[1].getCentroidY());
          theEncControlSpots[2]=new java.awt.geom.Point2D.Double(theEnc.getLeftReferenceSpots()[2].getCentroidX(),theEnc.getLeftReferenceSpots()[2].getCentroidY());
          
          Collections.sort(spots2, new XComparator());
          //for(int i=0;i<spots2.size();i++){System.out.println(spots2.get(i).getCentroidX());}
          
          
          AffineTransform at=EncounterLite.deriveAffineTransformIgnoreNotch(
              newEncControlSpots[0].getX(),
              newEncControlSpots[0].getY(),
              newEncControlSpots[1].getX(),
              newEncControlSpots[1].getY(),
              newEncControlSpots[2].getX(),
              newEncControlSpots[2].getY(),
              theEncControlSpots[0].getX(),
              theEncControlSpots[0].getY(),
              theEncControlSpots[1].getX(),
              theEncControlSpots[1].getY(),
              theEncControlSpots[2].getX(),
              theEncControlSpots[2].getY()
         );   
          
          AffineTransform atInverse=at.createInverse();
          
          //in advance of any intersection
          //create a list of Poin2DDouble Pair proportional distances from the notch
          ArrayList<Double> intersectionsProportionalDistances=new ArrayList<Double>();
          
          //let's try some fun intersection analysis
          int newPrintSize=spots2.size();
          int thisPrintSize=spots.size();
          
          //calculate smallest array size and then -1 for max number of potential lines to match
          int maxIntersectingLines=newPrintSize-1;
          if(thisPrintSize<newPrintSize){maxIntersectingLines=thisPrintSize-1;}
          
          double numIntersections=0;
          StringBuffer anglesOfIntersection=new StringBuffer("");
          for(int i=0;i<(newPrintSize-1);i++){
            //for(int j=i+1;j<newPrintSize;j++){
            int j=i+1;
            
            java.awt.geom.Point2D.Double originalStartPoint=new java.awt.geom.Point2D.Double(spots2.get(i).getCentroidX(),spots2.get(i).getCentroidY());
            java.awt.geom.Point2D.Double transformedStartPoint=new java.awt.geom.Point2D.Double();
            at.transform(originalStartPoint, transformedStartPoint);
           
            java.awt.geom.Point2D.Double originalEndPoint=new java.awt.geom.Point2D.Double(spots2.get(j).getCentroidX(),spots2.get(j).getCentroidY());
            java.awt.geom.Point2D.Double transformedEndPoint=new java.awt.geom.Point2D.Double();
            at.transform(originalEndPoint, transformedEndPoint);
              
              java.awt.geom.Point2D.Double newStart=(new  java.awt.geom.Point2D.Double(transformedStartPoint.getX(),transformedStartPoint.getY()));
              java.awt.geom.Point2D.Double newEnd=(new  java.awt.geom.Point2D.Double(transformedEndPoint.getX(),transformedEndPoint.getY()) ) ;
              java.awt.geom.Line2D.Double newLine=new java.awt.geom.Line2D.Double(newStart,newEnd  );
            
              //now compare to thisPattern
              for(int m=0;m<(thisPrintSize-1);m++){
       
                    int n=m+1;
                    
                    java.awt.geom.Point2D.Double thisStart=(new  java.awt.geom.Point2D.Double(spots.get(m).getCentroidX(),spots.get(m).getCentroidY()));
                    java.awt.geom.Point2D.Double thisEnd=(new  java.awt.geom.Point2D.Double(spots.get(n).getCentroidX(),spots.get(n).getCentroidY()) );   
                    java.awt.geom.Line2D.Double thisLine=new java.awt.geom.Line2D.Double(thisStart,thisEnd);
                    
                    //if((thisEnd.getX()>=newStart.getX()) && (thisStart.getX()<=newEnd.getX())){
                      if(newLine.intersectsLine(thisLine)){
                        numIntersections++;
                        String intersectionAngle=java.lang.Double.toString(EncounterLite.angleBetween2Lines(newLine, thisLine));
                        anglesOfIntersection.append(intersectionAngle+",");
                        
                        //calculate proportional distance to test if intersection was valid in original space
                        //untranslate new points since they were mapped into this points
                        java.awt.geom.Point2D.Double intersectionPoint=getIntersectionPoint(newLine,thisLine);
                        if(intersectionPoint!=null){
                          
                          double theDistanceToLine=Math.abs(theEncControlSpots[0].distance(intersectionPoint));
                          java.awt.geom.Line2D.Double theWidthLine=new java.awt.geom.Line2D.Double(theEncControlSpots[0],theEncControlSpots[2]);
                          double theHeight=theWidthLine.ptLineDist(theEncControlSpots[1]);
                          double theProportion = theDistanceToLine/theHeight;
                          
                          //now the newLine detangle
                          java.awt.geom.Point2D.Double transformedIntersectionPoint=new java.awt.geom.Point2D.Double();
                          atInverse.transform(intersectionPoint,  transformedIntersectionPoint);
                          double newDistanceToLine=Math.abs(newEncControlSpots[0].distance(transformedIntersectionPoint));
                          java.awt.geom.Line2D.Double newWidthLine=new java.awt.geom.Line2D.Double(newEncControlSpots[0],newEncControlSpots[2]);
                          double newHeight=newWidthLine.ptLineDist(newEncControlSpots[1]);
                          
                          double newProportion = newDistanceToLine/newHeight;
                          
                          double proportionalDistance=Math.abs(1-newProportion/theProportion);
                          
                          
                          
                          //if this proprtional distance is too warped, don't count it
                          //if(proportionalDistance>allowedIntersectionWarpProportion){numIntersections--;}
                          
                        }
                        
                      }
                      //else{System.out.println("["+newStart.getX()+","+newStart.getY()+","+newEnd.getX()+","+newEnd.getY()+"]"+" does not intersect with "+"["+thisStart.getX()+","+thisStart.getY()+","+thisEnd.getX()+","+thisEnd.getY()+"]");}
                      
                      //short circuit to end if the comparison line is past the new line
                      //if(newEnd.getX()<thisStart.getX()){
                       // m=thisPrintSize;
                     // }
                   // }
              }
              
            
            //}
            
            
          }
          return (numIntersections/maxIntersectingLines);
          //return (numIntersections);
      
    }
     catch(Exception e){
       e.printStackTrace();
       return 0.0;
     }
    
    
  }
    
  /*  
    public static Double geroMatch(EncounterLite existingEncounter,EncounterLite newEnc) {

      
      
      //insert Shane's algorithm
      Double geroMatchValue=new Double(0);
      
      try{
        //System.out.println("     About to start TraceCompare!");
        Fluke newFluke=new Fluke(newEnc);
        //System.out.println("     newFluke created!");
        Fluke thisFluke=new Fluke(existingEncounter);
        //System.out.println("     thisFluke created!");
        TraceCompare tc = new TraceCompare();
        //System.out.println("     TraceComapre done!");
        ArrayList<Fluke> flukes=new ArrayList<Fluke>();
        flukes.add(thisFluke);
        TreeSet<Fluke> matches = tc.processCatalog(flukes,newFluke);
        
        if(matches.size()>0){
          geroMatchValue=matches.first().getMatchValue();
        }
        System.out.println("    !!!!I found a Gero score of: "+geroMatchValue);
        
        return geroMatchValue;
      }
      catch(Exception fe){
        fe.printStackTrace(); 
        return null;
      }
      
      
      
}
    */
  
    public static MatchObject getModifiedGroth4Flukes(EncounterLite existingEnc, EncounterLite newEnc, double epsilon, double R, double Sizelim, double maxTriangleRotation, double C, boolean secondRun) {
      System.out.println("\nNow comparing against encounter " + existingEnc.getEncounterNumber() + " of " + existingEnc.getIndividualID()+ "...");
      try {

        SuperSpot[] spots = new SuperSpot[0];

        //check to see if this is a right side scan. if false, this is a left-side scan.
        ArrayList<SuperSpot> newSpots=new ArrayList<SuperSpot>();
        if(existingEnc.getSpots()!=null){newSpots.addAll(existingEnc.getSpots());}
        if(existingEnc.getRightSpots()!=null){newSpots.addAll(existingEnc.getRightSpots());}
        

        SuperSpot[] newspots=new SuperSpot[0];
        //set up variables needed to normalize spots and make sure that list A is always the smallest of the two lists
        boolean swappedSpots = false;

        newspots = newSpots.toArray(newspots);
        

        double xMaxCatalog = 0;
        double yMaxCatalog = 0;
        double xMaxNew = 0;
        double yMaxNew = 0;
        for (int iterN1 = 0; iterN1 < newspots.length; iterN1++) {
          if (newspots[iterN1].getTheSpot().getCentroidX() > xMaxNew) {
            xMaxNew = newspots[iterN1].getTheSpot().getCentroidX();
          }
        }
        for (int iterN2 = 0; iterN2 < newspots.length; iterN2++) {
          if (newspots[iterN2].getTheSpot().getCentroidY() > yMaxNew) {
            yMaxNew = newspots[iterN2].getTheSpot().getCentroidY();
          }
        }
        for (int iterN3 = 0; iterN3 < spots.length; iterN3++) {
          if (spots[iterN3].getTheSpot().getCentroidX() > xMaxCatalog) {
            xMaxCatalog = spots[iterN3].getTheSpot().getCentroidX();
          }
        }

        //correction
        for (int iterN4 = 0; iterN4 < spots.length; iterN4++) {
          if (spots[iterN4].getTheSpot().getCentroidY() > yMaxCatalog) {
            yMaxCatalog = spots[iterN4].getTheSpot().getCentroidY();
          }
        }
        double normFactorCatalog = 0;
        if (xMaxCatalog > yMaxCatalog) {
          normFactorCatalog = xMaxCatalog;
        } else {
          normFactorCatalog = yMaxCatalog;
        }
        //System.out.println("normFactorCatalog is: "+normFactorCatalog);
        double normFactorNew = 0;
        if (xMaxNew > yMaxNew) {
          normFactorNew = xMaxNew;
        } else {
          normFactorNew = yMaxNew;
        }
        for (int iterj = 0; iterj < newspots.length; iterj++) {
          Spot replaceMe = newspots[iterj].getTheSpot();
          newspots[iterj] = new SuperSpot(new Spot(replaceMe.getArea(), (replaceMe.getCentroidX() / normFactorNew), (replaceMe.getCentroidY() / normFactorNew)));
        }
        //now iterate through catalog spots and normalize each
        for (int iterj2 = 0; iterj2 < spots.length; iterj2++) {
          Spot replaceMe = spots[iterj2].getTheSpot();
          spots[iterj2] = new SuperSpot(new Spot(replaceMe.getArea(), (replaceMe.getCentroidX() / normFactorCatalog), (replaceMe.getCentroidY() / normFactorCatalog)));
        }

        //stopped formatting here


        //start triangle creation in Groth method
        double newSpan, baseSpan, newClosePairDist, baseClosePairDist;
        double bestScore = 0, adjustedScore = 0;
        int orient;
        double allowedRotationDiff = Math.toRadians(maxTriangleRotation);

        
        
        //start detect new reference spots
        
        java.awt.geom.Point2D.Double[] newEncControlSpots=new java.awt.geom.Point2D.Double[3];
        newEncControlSpots[0]=new java.awt.geom.Point2D.Double(9999999,0);
        newEncControlSpots[1]=new java.awt.geom.Point2D.Double(0,-999999);
        newEncControlSpots[2]=new java.awt.geom.Point2D.Double(-99999,0);
         for(int i=0;i<newspots.length;i++){
          SuperSpot mySpot=newspots[i];
          
          //get the rightmost spot
          if(mySpot.getCentroidX()>newEncControlSpots[2].getX()){newEncControlSpots[2]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
          
          //get the bottommost spot
          if(mySpot.getCentroidY()>newEncControlSpots[1].getY()){newEncControlSpots[1]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
          
          //get the leftmost spot
          if(mySpot.getCentroidX()<newEncControlSpots[0].getX()){newEncControlSpots[0]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
          
          
        } 
        
        //end detect new reference spots
        

        //construct all triangles for the newEncounter
        newSpan = -1;
        newClosePairDist = 9999;
        int numSpots = newspots.length;
        //System.out.println("     I expect "+(numSpots * (numSpots - 1) * 1 / 6)+" triangles.");
        ArrayList newTriangles = new ArrayList(numSpots * (numSpots - 1) * (numSpots - 2) / 6);
        int newSpotArrayL = newspots.length - 1;
        for (int i = 0; i < newSpotArrayL; i++) {

          //for (int j = i + 1; j < (newspots.length); j++) {
            int newArrayL = newspots.length;
            //for (int k = j + 1; k < newArrayL; k++) {
             
            SpotTriangle tempTriangle=null;
            
            //left lobe of fluke
            if(newspots[i].getTheSpot().getCentroidX()<newEncControlSpots[1].getX()){
            
              tempTriangle = new SpotTriangle(newspots[i].getTheSpot(), (new Spot(0,newEncControlSpots[1].getX(),newEncControlSpots[1].getY())), (new Spot(0,newEncControlSpots[2].getX(),newEncControlSpots[2].getY())), epsilon);
            }
            //right lobe of fluke
            else{
              tempTriangle = new SpotTriangle(newspots[i].getTheSpot(), (new Spot(0,newEncControlSpots[1].getX(),newEncControlSpots[1].getY())), (new Spot(0,newEncControlSpots[0].getX(),newEncControlSpots[0].getY())), epsilon);
            }
            
            
            
            orient = 0;
              if (tempTriangle.clockwise) orient = 1;


              if (tempTriangle.D13 > newSpan) {


                newSpan = tempTriangle.D13;
              }
              if (tempTriangle.D12 < newClosePairDist) {
                newClosePairDist = tempTriangle.D12;
              }
              //System.out.println("      Triangle R is "+tempTriangle.R+" and C is "+tempTriangle.C);
              
              if ((tempTriangle.R <= R) && (tempTriangle.C <= C)) {
                newTriangles.add(tempTriangle);

              }
            //}
          //}
        }
        //System.out.println("     I found "+newTriangles.size()+" new encounter triangles.\n Filtering for Sizelim...");
        for (int i = 0; i < newTriangles.size(); i++) {
          SpotTriangle tempTriangle = (SpotTriangle) newTriangles.get(i);

          //old sizelim computation
          //System.out.println("      Evaluating a triangle with Sizelim of: "+(tempTriangle.D13 / newSpan));
          if (tempTriangle.D13 / newSpan >= Sizelim) {

            //System.out.println("Removing large triangle: "+tempTriangle.D13+" "+newSpan+" "+tempTriangle.D13/newSpan);
            newTriangles.remove(i);
            i--;

          }
        }
        
        System.out.println("     After Sizelim filering there are now "+newTriangles.size()+" new encounter triangles.");
        
        
        
        if (newClosePairDist < (3 * epsilon)) {
          System.out.println("WARNING!!!! Spots in the new encounter are too close together to support this high of an epsilon value!!!");
        }

        //construct all triangles for the base Encounter to be compared to
        baseSpan = -1;
        baseClosePairDist = 9999;
        
        //this is for combined patterns so get left and right
        SuperSpot[] baseSpots = new SuperSpot[0];
        ArrayList<SuperSpot> mybaseArray=new ArrayList<SuperSpot>();
        if(existingEnc.getSpots()!=null){mybaseArray.addAll(existingEnc.getSpots());}
        if(existingEnc.getRightSpots()!=null){mybaseArray.addAll(existingEnc.getRightSpots());}
        baseSpots=mybaseArray.toArray(baseSpots);
        
        int spotAL = baseSpots.length;
        //System.out.println("      I expect "+(spotAL*(spotAL-1)*(spotAL-2)/6)+" triangles.");
        ArrayList baseTriangles = new ArrayList(spotAL * (spotAL - 1) * (spotAL - 2) / 6);
        int spotArrayL = baseSpots.length - 1;
        int ensureNumIterations = 0;
        
        //start detect new reference spots
        
        java.awt.geom.Point2D.Double[] theEncControlSpots=new java.awt.geom.Point2D.Double[3];
        theEncControlSpots[0]=new java.awt.geom.Point2D.Double(9999999,0);
        theEncControlSpots[1]=new java.awt.geom.Point2D.Double(0,-999999);
        theEncControlSpots[2]=new java.awt.geom.Point2D.Double(-99999,0);
         for(int i=0;i<spotAL;i++){
          SuperSpot mySpot=baseSpots[i];
          
          //get the rightmost spot
          if(mySpot.getCentroidX()>theEncControlSpots[2].getX()){theEncControlSpots[2]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
          
          //get the bottommost spot
          if(mySpot.getCentroidY()>theEncControlSpots[1].getY()){theEncControlSpots[1]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
          
          //get the leftmost spot
          if(mySpot.getCentroidX()<theEncControlSpots[0].getX()){theEncControlSpots[0]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
          
        } 
        
        //end detect new reference spots
        
        
        for (int i = 0; i < spotAL; i++) {

          //for (int j = i + 1; j < (baseSpots.length); j++) {

          //  for (int k = j + 1; k < baseSpots.length; k++) {
              
            
          SpotTriangle tempTriangle=null;
          
          //left lobe of fluke
          if(baseSpots[i].getTheSpot().getCentroidX()<theEncControlSpots[1].getX()){
          
            tempTriangle = new SpotTriangle(baseSpots[i].getTheSpot(), (new Spot(0,theEncControlSpots[1].getX(),theEncControlSpots[1].getY())), (new Spot(0,theEncControlSpots[2].getX(),theEncControlSpots[2].getY())), epsilon);
          }
          //right lobe of fluke
          else{
            tempTriangle = new SpotTriangle(baseSpots[i].getTheSpot(), (new Spot(0,theEncControlSpots[1].getX(),theEncControlSpots[1].getY())), (new Spot(0,theEncControlSpots[0].getX(),theEncControlSpots[0].getY())), epsilon);
          }  
              
              
              
              orient = 0;
              if (tempTriangle.clockwise) orient = 1;
              //System.out.println("New "+i+" "+j+" "+k+" "+tempTriangle.C+" "+tempTriangle.tC2+" "+tempTriangle.R+" "+tempTriangle.tR2+" "+tempTriangle.D13+" "+orient);
              //System.out.println(i+" "+j+" "+k+" "+tempTriangle.Dxs+" "+tempTriangle.Dys+" "+tempTriangle.Dxl+" "+tempTriangle.Dyl+" "+orient);
              if (tempTriangle.D13 > baseSpan) {
                baseSpan = tempTriangle.D13;
              }
              if (tempTriangle.D12 < baseClosePairDist) {
                baseClosePairDist = tempTriangle.D12;
              }
              if ((tempTriangle.R <= R) && (tempTriangle.C <= C)) {
                baseTriangles.add(tempTriangle);
              }
           // }
          //}
        }
        //System.out.println("     I found "+baseTriangles.size()+" base encounter triangles.\n Filtering for Sizelim...");
        for (int i = 0; i < baseTriangles.size(); i++) {
          SpotTriangle tempTriangle = (SpotTriangle) baseTriangles.get(i);

          //old way
          if (tempTriangle.D13 / baseSpan >= Sizelim) {

            //new way
            //  if (tempTriangle.D13/baseSpan < Sizelim) {
            //System.out.println("Removing large triangle: "+tempTriangle.D13+" "+baseSpan+" "+tempTriangle.D13/baseSpan);
            baseTriangles.remove(i);
            i--;
          } else {
            //System.out.println("Keeping: "+i+" "+tempTriangle.D13+" "+baseSpan+" "+tempTriangle.D13/baseSpan);
          }
        }
        //System.out.println("     Now using "+baseTriangles.size()+" base encounter triangles.");
        //System.out.println("newSpan "+newSpan+" baseSpan "+baseSpan);
        //System.out.println("     baseClosePairDist is "+baseClosePairDist);
        if (baseClosePairDist < (3 * epsilon)) {
          System.out.println("WARNING!!!! Spots in the catalog encounter are too close together to support this high of an epsilon value!!!");
        }

        //System.out.println("   I have constructed all of the triangles!");

        //now swap the traingles if newTriangles>baseTriangles

        SpotTriangle[] tArray = new SpotTriangle[0];
        SpotTriangle[] baseArray = new SpotTriangle[0];

        if (newTriangles.size() > baseTriangles.size()) {
          swappedSpots = true;
          baseArray = (SpotTriangle[]) (newTriangles.toArray(baseArray));
          tArray = (SpotTriangle[]) (baseTriangles.toArray(tArray));
        } else {
          tArray = (SpotTriangle[]) (newTriangles.toArray(tArray));
          baseArray = (SpotTriangle[]) (baseTriangles.toArray(baseArray));
        }

        //now begin processing the triangles

        Arrays.sort(tArray, new RComparator());
        Arrays.sort(baseArray, new RComparator());

        //VmatchesA are the matched triangles of the new encounter whose spots were passed into this method
        ArrayList VmatchesA = new ArrayList(5000);

        //VmatchesB are the matched triangles of this encounter
        ArrayList VmatchesB = new ArrayList(5000);
        ArrayList bestSums = new ArrayList(5000);
        double holdingMatch = 0;

        boolean matched;
        int arrayL = tArray.length;
        int baseArrayL = baseArray.length;
        // below, 'A' refers to tArray which is the array of the new encounter triangles, 'B' to baseArray which is the array of this database encounter's triangles
        double RA, RB, CA, CB;
        double tRA2, tRB2, tCA2, tCB2;
        double RotA, rotdiff, bestrot;
        double sqrttR2sum, Rdiff2, Cdiff2, sumdiffs, bestsum, besttol;
        int bestiter2 = 0;
        for (int iter1 = 0; iter1 < arrayL; iter1++) {
          matched = false;
          bestsum = 99999;
          RA = tArray[iter1].R;
          tRA2 = tArray[iter1].tR2;
          CA = tArray[iter1].C;
          tCA2 = tArray[iter1].tC2;
          RotA = tArray[iter1].getMyVertexOneRotationInRadians();
          for (int iter2 = 0; iter2 < baseArrayL; iter2++) {
            RB = baseArray[iter2].R;
            tRB2 = baseArray[iter2].tR2;
            sqrttR2sum = Math.sqrt(tRA2 + tRB2);
            //System.out.println(iter1+" "+iter2+" RB "+RB+" RA-sqrttR2sum "+(RA-sqrttR2sum)+" RA+sqrttR2sum "+(RA+sqrttR2sum));
            if ((RB > (RA - sqrttR2sum)) && (RB < (RA + sqrttR2sum))) {
              //System.out.println("Testing...");
              CB = baseArray[iter2].C;
              tCB2 = baseArray[iter2].tC2;
              Rdiff2 = (RA - RB) * (RA - RB) / (tRA2 + tRB2);
              Cdiff2 = (CA - CB) * (CA - CB) / (tCA2 + tCB2);
              rotdiff = Math.abs(RotA - baseArray[iter2].getMyVertexOneRotationInRadians()) / allowedRotationDiff;
              if ((Rdiff2 < 1.0) && (Cdiff2 < 1.0) && (rotdiff < 1.0)) {
                sumdiffs = Rdiff2 + Cdiff2 + (rotdiff * rotdiff);
                //System.out.println("Match: "+iter1+" "+iter2+" RA "+RA+" RB "+RB+" CA "+CA+" CB "+CB+" CWA "+tArray[iter1].clockwise+" CWB "+baseArray[iter2].clockwise+" "+sumdiffs);
                //System.out.println("PerA "+tArray[iter1].logPerimeter+" PerB "+baseArray[iter2].logPerimeter);


                //added the requirement here that matched trianlges be of the same orientation - jah 1/19/04
                if (sumdiffs < bestsum) {

                  //check to make sure that the triangles are not extreme rotations of each other

                  //System.out.println("angle of rotation diff is: "+Math.toDegrees(tArray[iter1].getMyVertexOneRotationInRadians()-baseArray[iter2].getMyVertexOneRotationInRadians()));
                  matched = true;
                  bestiter2 = iter2;
                  bestsum = sumdiffs;
                  //VmatchesA.add(tArray[iter1]);
                  //VmatchesB.add(baseArray[iter2]);
                  //  }
                }
              }
            }
          }
          if (matched) {
            //System.out.println("Best iter2:"+bestiter2);
            //System.out.println("Match: "+bestsum+" "+tArray[iter1].R+" "+baseArray[bestiter2].R+" "+tArray[iter1].C+" "+baseArray[bestiter2].C+" "+tArray[iter1].D13/newSpan+" "+baseArray[bestiter2].D13/baseSpan+" "+tArray[iter1].clockwise+" "+baseArray[bestiter2].clockwise+" "+iter1+" "+bestiter2);
            VmatchesA.add(tArray[iter1]);
            VmatchesB.add(baseArray[bestiter2]);
            bestSums.add(new Double(bestsum));
          }
        }
        System.out.println("     I am now about to start filtering with "+VmatchesA.size()+" triangles!");
        //now begin filtering
        ArrayList logM = new ArrayList(VmatchesA.size());
        int nPLUS = 0;
        int nMINUS = 0;


        for (int iter3 = 0; iter3 < VmatchesA.size(); iter3++) {
          logM.add(new Double((((SpotTriangle) VmatchesA.get(iter3)).logPerimeter) - ((SpotTriangle) VmatchesB.get(iter3)).logPerimeter));
          //System.out.println("M value of: "+(new Double((((spotTriangle)VmatchesA.elementAt(iter3)).logPerimeter)-((spotTriangle)VmatchesB.elementAt(iter3)).logPerimeter)).doubleValue());
          if (((SpotTriangle) VmatchesA.get(iter3)).clockwise == ((SpotTriangle) VmatchesB.get(iter3)).clockwise) {
            nPLUS++;
          } else {
            nMINUS++;
          }
        }
        int mT = Math.abs(nPLUS - nMINUS);
        int mF = nPLUS + nMINUS - mT;
        double multiple = 0;
        boolean stillIterate = true;
        int numIterations = 0;
        System.out.println("   Going into the logM filter with "+VmatchesA.size()+" matching triangles. Before filtering, N+="+nPLUS+" N-="+nMINUS);

        double oldStdDeviationLogM = 10000;
        while (stillIterate && (numIterations < 20) && (VmatchesA.size() > 0)) {
          //System.out.println("          iterating filter with "+VmatchesA.size()+" triangles!");
          numIterations++;
          boolean haveMadeChange = false;

          //let's find some logM info
          double meanLogM = 0;
          double stdDeviationLogM = 0;

          //method to compute logM using only the standard deviations of same sense triangles
          int logMSize = logM.size();
          for (int iter4 = 0; iter4 < logMSize; iter4++) {
            boolean Aorientation = ((SpotTriangle) VmatchesA.get(iter4)).clockwise;
            boolean Borientation = ((SpotTriangle) VmatchesB.get(iter4)).clockwise;
            if (Aorientation == Borientation) {
              //logMSize++;
              meanLogM += ((Double) logM.get(iter4)).doubleValue();
            }
          }
          meanLogM = meanLogM / nPLUS;
          System.out.println("Found a mean of: "+meanLogM);


          //weighted method
          //int logMSize=logM.size();
          //int sumLogMDivBestSum=0;
          //int OneOverBestSum=0;

          //for(int iter4=0; iter4<logMSize; iter4++){
          //  sumLogMDivBestSum+=(((Double)logM.get(iter4)).doubleValue())/(((Double)bestSums.get(iter4)).doubleValue());
          //  OneOverBestSum+=1/(((Double)bestSums.get(iter4)).doubleValue());
          //  }
          //meanLogM=sumLogMDivBestSum/OneOverBestSum;


          for (int iter5 = 0; iter5 < logMSize; iter5++) {
            boolean Aorientation = ((SpotTriangle) VmatchesA.get(iter5)).clockwise;
            boolean Borientation = ((SpotTriangle) VmatchesB.get(iter5)).clockwise;
            if (Aorientation == Borientation) {
              stdDeviationLogM += Math.pow((((Double) logM.get(iter5)).doubleValue() - meanLogM), 2);
            }
          }
          System.out.println("Almost standard deviation is: "+stdDeviationLogM);
          System.out.println("LogM list size minus one is: "+(logM.size()-1));
          System.out.println("The real std dev. should be: "+Math.pow((stdDeviationLogM/(logM.size()-1)), 0.5));


          if (nPLUS > 1) {
            stdDeviationLogM = Math.pow((stdDeviationLogM / (nPLUS - 1)), 0.5);
          } else {
            stdDeviationLogM = 0.0;
          }

          //System.out.println("Found a std. dev. of: "+stdDeviationLogM);


          //now let's define a filter based on logM
          int greaterThanMeanLogM = 0;
          int lessThanMeanLogM = 0;
          for (int iterCount = 0; iterCount < logM.size(); iterCount++) {
            if ((((Double) logM.get(iterCount)).doubleValue()) > meanLogM) {
              greaterThanMeanLogM++;
            } else {
              lessThanMeanLogM++;
            }
          }
          boolean leftSideHeavy = false;
          boolean rightSideHeavy = false;
          boolean balanced = false;
          if (Math.pow((lessThanMeanLogM - greaterThanMeanLogM), 2) > (lessThanMeanLogM + greaterThanMeanLogM)) {
            if (lessThanMeanLogM > greaterThanMeanLogM) {
              leftSideHeavy = true;
            } else {
              rightSideHeavy = true;
            }
          } else {
            balanced = true;
          }

          //Groth's way
          //if(mF>mT) {multiple=1;}
          //else if((0.1*mT)>mF) {multiple=3;}
          //else{multiple=2;}

          // softer logM filter:
          //if(mF>2.0*mT) {multiple=1;}
          //else if((0.5*mT)>mF) {multiple=3;}
          //else{multiple=2;}


          // softer still:
          if (nMINUS > nPLUS) {
            multiple = 1;
          } else if ((0.5 * mT) > mF) {
            multiple = 3;
          } else {
            multiple = 2;
          }


          //now discard nonmatches
          int logMremovals = 0;
          int leftsideRemovals = 0, rightsideRemovals = 0;
          for (int iter6 = 0; iter6 < logM.size(); iter6++) {

            if (Math.abs(((Double) logM.get(iter6)).doubleValue() - meanLogM) > (multiple * stdDeviationLogM)) {
              if (leftSideHeavy && (((Double) logM.get(iter6)).doubleValue() < meanLogM)) {
                leftsideRemovals++;
              } else if (rightSideHeavy && (((Double) logM.get(iter6)).doubleValue() > meanLogM)) {
                rightsideRemovals++;
              } else if (leftSideHeavy && (((Double) logM.get(iter6)).doubleValue() > meanLogM)) {
                rightsideRemovals++;
              } else if (rightSideHeavy && (((Double) logM.get(iter6)).doubleValue() < meanLogM)) {
                leftsideRemovals++;
              }
              logM.remove(iter6);
              VmatchesA.remove(iter6);
              VmatchesB.remove(iter6);
              bestSums.remove(iter6);
              haveMadeChange = true;
              iter6--;
              logMremovals++;
            }
          }
          //System.out.print("     left heavy? "+leftSideHeavy+"   ");
          //System.out.print("     right heavy? "+rightSideHeavy+"   ");
          //System.out.println("     Balanced? "+balanced+"   ");
          //  System.out.println("     Removed "+logMremovals+" triangles on logM filter pass "+numIterations+" with a filter/multiple value of "+multiple);
          //System.out.println("          leftsideRemovals="+leftsideRemovals+"     rightsideRemovals="+rightsideRemovals);
          //System.out.println("          N+ is "+nPLUS+" and N- is "+nMINUS);

          if (!haveMadeChange) {
            stillIterate = false;
          }
          nPLUS = 0;
          nMINUS = 0;
          int iterLimit = VmatchesA.size();
          for (int iter7 = 0; iter7 < iterLimit; iter7++) {
            if (((SpotTriangle) VmatchesA.get(iter7)).clockwise == ((SpotTriangle) VmatchesB.get(iter7)).clockwise) {
              nPLUS++;
            } else {
              nMINUS++;
            }
          }
          mT = Math.abs(nPLUS - (nMINUS));
          mF = nPLUS + nMINUS - mT;

          oldStdDeviationLogM = stdDeviationLogM;
          //System.out.println("          Going into the next round with mT, mF: "+mT+","+mF);
        }


        for (int iter8 = 0; iter8 < VmatchesA.size(); iter8++) {
          if (((SpotTriangle) VmatchesA.get(iter8)).clockwise != ((SpotTriangle) VmatchesB.get(iter8)).clockwise) {
            logM.remove(iter8);
            VmatchesA.remove(iter8);
            VmatchesB.remove(iter8);
            bestSums.remove(iter8);
            iter8--;
          }

        }

        System.out.println("Going into Groth scoring with "+VmatchesA.size()+" matching triangles.");
        if (VmatchesA.size() == 0) {
          return (new MatchObject(existingEnc.getIndividualID(), 0, 0, existingEnc.getEncounterNumber()));
        }
        MatchedPoints mp = new MatchedPoints();
        int vMatchL = VmatchesA.size();
        for (int iter10 = 0; iter10 < vMatchL; iter10++) {
          for (int iter11 = 0; iter11 < 3; iter11++) {
            Spot spotA = ((SpotTriangle) VmatchesA.get(iter10)).getVertex(iter11 + 1);
            Spot spotB = ((SpotTriangle) VmatchesB.get(iter10)).getVertex(iter11 + 1);
            //for(int iter12=iter11+1; iter12<VmatchesA.size(); iter12++) {
            int tempPlace = mp.hasMatchedPair(spotA, spotB);
            if (tempPlace != -1) {
              ((VertexPointMatch) mp.get(tempPlace)).points++;

            } else {
              mp.add(new VertexPointMatch(spotA, spotB, 1));
            }
            //  }
          }

        }
        VertexPointMatch[] scores = new VertexPointMatch[0];
        scores = (VertexPointMatch[]) (mp.toArray(scores));
        Arrays.sort(scores, new ScoreComparator());
        //System.out.println("scores.length is: "+scores.length);
        if (scores[0].points == 1) {
          System.out.println("Exiting because I could not match a single triangle point more than once.");

          return (new MatchObject(existingEnc.getIndividualID(), 0, 0, existingEnc.getEncounterNumber()));
        }
        ArrayList secondRunSpots = new ArrayList();
        //ArrayList secondRunSpotsB=new ArrayList();
        int scoresSize = scores.length;
        secondRunSpots.add(scores[0]);
        int iter20 = 1;
        boolean keepOnCounting = true;
        boolean hasNotBeenSeenYet = true;

        /*old way
     ArrayList countedSpots=new ArrayList();
     countedSpots.add(new spot(0, scores[0].newX, scores[0].newY));
     while(keepOnCounting&&(iter20<scoresSize)){



         for(int iter30=0;iter30<countedSpots.size();iter30++){
             spot tempSpot=(spot)countedSpots.get(iter30);
             if((scores[iter20].newX==tempSpot.getCentroidX())&&(scores[iter20].newY==tempSpot.getCentroidY())){hasNotBeenSeenYet=false;}
         }

         if((scores[iter20].points>(scores[(iter20-1)].points/2))&&(scores[iter20].points>1)&&(hasNotBeenSeenYet)){
             secondRunSpots.add(scores[iter20]);
             countedSpots.add(new spot(0, scores[iter20].newX, scores[iter20].newY));
             }
         else{keepOnCounting=false;}
         iter20++;
        }*/

        //Zaven's correction
        ArrayList countedSpotsA = new ArrayList();
        ArrayList countedSpotsB = new ArrayList();
        countedSpotsA.add(new Spot(0, scores[0].newX, scores[0].newY));
        countedSpotsB.add(new Spot(0, scores[0].oldX, scores[0].oldY));
        while (keepOnCounting && (iter20 < scoresSize)) {

          for (int iter30 = 0; iter30 < countedSpotsA.size(); iter30++) {
            Spot tempSpot = (Spot) countedSpotsA.get(iter30);
            if ((scores[iter20].newX == tempSpot.getCentroidX()) && (scores[iter20].newY == tempSpot.getCentroidY())) {
              hasNotBeenSeenYet = false;
            }
            tempSpot = (Spot) countedSpotsB.get(iter30);
            if ((scores[iter20].oldX == tempSpot.getCentroidX()) && (scores[iter20].oldY == tempSpot.getCentroidY())) {
              hasNotBeenSeenYet = false;
            }
          }

          if ((scores[iter20].points > (scores[(iter20 - 1)].points / 2)) && (scores[iter20].points > 1) && (hasNotBeenSeenYet)) {
            secondRunSpots.add(scores[iter20]);
            countedSpotsA.add(new Spot(0, scores[iter20].newX, scores[iter20].newY));
            countedSpotsB.add(new Spot(0, scores[iter20].oldX, scores[iter20].oldY));
          } else {
            keepOnCounting = false;
          }
          iter20++;
        }


        VertexPointMatch[] scoredSpots = new VertexPointMatch[0];
        scoredSpots = (VertexPointMatch[]) (secondRunSpots.toArray(scoredSpots));

        System.out.print("     Scoring going into second pass: ");
        for (int iter40 = 0; iter40 < scoredSpots.length; iter40++) {
          //System.out.print(scoredSpots[iter40].points+"+");
        }
        //System.out.println("...");

        ArrayList secondRunSpotsA = new ArrayList();
        ArrayList secondRunSpotsB = new ArrayList();
        for (int iter25 = 0; iter25 < scoredSpots.length; iter25++) {
          boolean matchListA = false;
          for (int iter26 = 0; iter26 < secondRunSpotsA.size(); iter26++) {
            if ((scoredSpots[iter25].newX == (((SuperSpot) secondRunSpotsA.get(iter26)).getTheSpot().getCentroidX())) && (scoredSpots[iter25].newY == (((SuperSpot) secondRunSpotsA.get(iter26)).getTheSpot().getCentroidY()))) {
              matchListA = true;
            }
          }
          if (!matchListA) {
            secondRunSpotsA.add(new SuperSpot(new Spot(0, scoredSpots[iter25].newX, scoredSpots[iter25].newY)));
          }
          boolean matchListB = false;
          for (int iter26 = 0; iter26 < secondRunSpotsB.size(); iter26++) {
            if ((scoredSpots[iter25].oldX == (((SuperSpot) secondRunSpotsB.get(iter26)).getTheSpot().getCentroidX())) && (scoredSpots[iter25].oldY == (((SuperSpot) secondRunSpotsB.get(iter26)).getTheSpot().getCentroidY()))) {
              matchListB = true;
            }
          }
          if (!matchListB) {
            secondRunSpotsB.add(new SuperSpot(new Spot(0, scoredSpots[iter25].oldX, scoredSpots[iter25].oldY)));
          }
        }
        SuperSpot[] secondNewSpots = new SuperSpot[0];
        secondNewSpots = (SuperSpot[]) (secondRunSpotsA.toArray(secondNewSpots));
        SuperSpot[] secondBaseSpots = new SuperSpot[0];
        secondBaseSpots = (SuperSpot[]) (secondRunSpotsB.toArray(secondBaseSpots));

        System.out.println("secondNewSpots is :"+secondNewSpots.length);
        System.out.println("secondBaseSpots is :"+secondBaseSpots.length);

        //now run Groth's algorithm again if there are enough spots. if not, exit as this is not a match.
        VertexPointMatch[] secondPassSpots = scoredSpots;
        if ((secondNewSpots.length > 3) && (secondBaseSpots.length > 3)) {

          //run recursion on these spots now
          secondPassSpots = secondGrothPass(secondNewSpots, secondBaseSpots, epsilon, R, Sizelim, maxTriangleRotation, C);
          if (secondPassSpots.length < 3) {
            System.out.println("Exiting after the second pass because the returned number of spots was less than three. "+scoredSpots.length+"-"+(scoredSpots.length-secondPassSpots.length)+"="+secondPassSpots.length);
            return (new MatchObject(existingEnc.getIndividualID(), 0, 0, existingEnc.getEncounterNumber()));
          }
          //System.out.println("     The second pass cut out "+(scoredSpots.length-secondPassSpots.length)+" spots.");
        } else {
          System.out.println("Exiting processing because there were less than three spots going into the second filter pass. This is not a match.");
          return (new MatchObject(existingEnc.getIndividualID(), 0, 0, existingEnc.getEncounterNumber()));
        }
        // end second run


        //let's create and pass along an array of the logM values of the matched and scored triangles.
        int logMSize = logM.size();
        double[] logMbreakdown = new double[logMSize];
        for (int logMIter = 0; logMIter < logMSize; logMIter++) {
          logMbreakdown[logMIter] = ((Double) (logM.get(logMIter))).doubleValue();
        }

        String pointBreakdown = "";
        int iterLimit = secondPassSpots[0].points;
        int iter14 = 0;
        boolean ok2iterate = true;
        int scoresLength = secondPassSpots.length;
        while ((iter14 < scoresLength) && ok2iterate) {
          if (iter14 == 0) {

            bestScore += secondPassSpots[iter14].points;
            pointBreakdown += secondPassSpots[iter14].points + " + ";
            //System.out.print(secondPassSpots[iter14].points+"+");
            //}
            iter14++;
          }
          //modification...remove the drop in half limit that Groth recommends in his paper b/c point scores are so low.
          //else if(scores[iter14].points>=((scores[(iter14-1)].points)/2)) {
          else {
            bestScore += secondPassSpots[iter14].points;
            pointBreakdown += secondPassSpots[iter14].points + " + ";
            //System.out.print(secondPassSpots[iter14].points+"+");
            iter14++;
          }

        }
        adjustedScore = bestScore / (arrayL * 3);


        //System.out.println("\nTotal score is: "+bestScore);
        //System.out.println("\nAdjusted score is: "+adjustedScore);


        if (!swappedSpots) {

          for (int diters = 0; diters < secondPassSpots.length; diters++) {
            //System.out.println("was: "+secondPassSpots[diters].newX);
            secondPassSpots[diters].newX = secondPassSpots[diters].newX * normFactorNew;
            //System.out.println("changed to: "+secondPassSpots[diters].newX);
            secondPassSpots[diters].newY = secondPassSpots[diters].newY * normFactorNew;
            //System.out.println("changed to: "+secondPassSpots[diters].oldX);
            secondPassSpots[diters].oldX = secondPassSpots[diters].oldX * normFactorCatalog;
            //  System.out.println("changed to: "+secondPassSpots[diters].oldX);
            secondPassSpots[diters].oldY = secondPassSpots[diters].oldY * normFactorCatalog;
          }

        } else {
          for (int diters = 0; diters < secondPassSpots.length; diters++) {
            //System.out.println("was: "+secondPassSpots[diters].newX);
            secondPassSpots[diters].newX = secondPassSpots[diters].newX * normFactorCatalog;
            //System.out.println("changed to: "+secondPassSpots[diters].newX);
            secondPassSpots[diters].newY = secondPassSpots[diters].newY * normFactorCatalog;
            //System.out.println("changed to: "+secondPassSpots[diters].oldX);
            secondPassSpots[diters].oldX = secondPassSpots[diters].oldX * normFactorNew;
            //  System.out.println("changed to: "+secondPassSpots[diters].oldX);
            secondPassSpots[diters].oldY = secondPassSpots[diters].oldY * normFactorNew;
          }

        }


        //if newspots and spots were swapped at the beginning of this method to decrease processing time, we need to correct this for eventual spot mapping
        if (swappedSpots) {
          VertexPointMatch[] fixedSpots = new VertexPointMatch[secondPassSpots.length];
          for (int iter70 = 0; iter70 < secondPassSpots.length; iter70++) {
            fixedSpots[iter70] = new VertexPointMatch(secondPassSpots[iter70].oldX, secondPassSpots[iter70].oldY, secondPassSpots[iter70].newX, secondPassSpots[iter70].newY, secondPassSpots[iter70].points);
          }
          secondPassSpots = fixedSpots;
        }

        ArrayList secondPassSpotsAL = new ArrayList();
        for (int y = 0; y < secondPassSpots.length; y++) {
          secondPassSpotsAL.add(secondPassSpots[y]);
        }


        //send these matched results back!!!
        return (new MatchObject(existingEnc.getIndividualID(), bestScore, adjustedScore, VmatchesA.size(), secondPassSpotsAL, existingEnc.getEncounterNumber(), pointBreakdown, logMbreakdown, "", "", -1));


      } catch (Exception e) {
        e.printStackTrace();
        System.out.println("0 points awarded due to exception.");
        return (new MatchObject(existingEnc.getIndividualID(), 0, 0, existingEnc.getEncounterNumber()));
      }
    }
    
    public String getIndividualID(){return belongsToMarkedIndividual;}
    
    public static java.awt.geom.Point2D.Double getIntersectionPoint(Line2D.Double line1, Line2D.Double line2) {
      if (! line1.intersectsLine(line2) ) return null;
      double px = line1.getX1(),
          py = line1.getY1(),
          rx = line1.getX2()-px,
          ry = line1.getY2()-py;
        double qx = line2.getX1(),
              qy = line2.getY1(),
              sx = line2.getX2()-qx,
              sy = line2.getY2()-qy;

        double det = sx*ry - sy*rx;
        if (det == 0) {
          return null;
        } else {
          double z = (sx*(qy-py)+sy*(px-qx))/det;
          if (z==0 ||  z==1) return null;  // intersection at end point!
          return new java.awt.geom.Point2D.Double(
            (float)(px+z*rx), (float)(py+z*ry));
        }
   } // end intersection line-line
    
    
    
    public static Double getFlukeProportion(EncounterLite theEnc,EncounterLite theEnc2){
      
      try{
        ArrayList<SuperSpot> spots=new ArrayList<SuperSpot>();
        if(theEnc.getSpots()!=null){spots.addAll(theEnc.getSpots());}
        if(theEnc.getRightSpots()!=null){spots.addAll(theEnc.getRightSpots());}
          //newEncControlSpots = theEnc.getThreeLeftFiducialPoints();
          
        
        //sort the Array - lowest x to highest X coordinate
        Collections.sort(spots, new XComparator());
        
          
          java.awt.geom.Point2D.Double[] theEncControlSpots=new java.awt.geom.Point2D.Double[3];
          
          /*
          theEncControlSpots[0]=new java.awt.geom.Point2D.Double(9999999,0);
          theEncControlSpots[1]=new java.awt.geom.Point2D.Double(0,-999999);
          theEncControlSpots[2]=new java.awt.geom.Point2D.Double(-99999,0);
          //Builder theBuilder = TimeSeriesBase.builder();
          for(int i=0;i<spots.size();i++){
            SuperSpot mySpot=spots.get(i);
            
            //get the rightmost spot
            if(mySpot.getCentroidX()>theEncControlSpots[2].getX()){theEncControlSpots[2]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
            
            //get the bottommost spot
            if(mySpot.getCentroidY()>theEncControlSpots[1].getY()){theEncControlSpots[1]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
            
            //get the leftmost spot
            if(mySpot.getCentroidX()<theEncControlSpots[0].getX()){theEncControlSpots[0]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
            
            //let's do our FastDTW stuff too
            
            //theBuilder.add(spots.get(i).getCentroidX(),spots.get(i).getCentroidY());  
              
            
          } 
          */
          

    
        
        //EncounterLite enc=new EncounterLite(theEnc);
        // = new Point2D[3];
        //SuperSpot[] newspotsTemp = new SuperSpot[0];
        //newspotsTemp = (SuperSpot[]) enc.getRightSpots().toArray(newspotsTemp);
        ArrayList<SuperSpot> spots2=new ArrayList<SuperSpot>();
        if(theEnc2.getSpots()!=null){spots2.addAll(theEnc2.getSpots());}
        if(theEnc2.getRightSpots()!=null){spots2.addAll(theEnc2.getRightSpots());}
          //newEncControlSpots = theEnc2.getThreeLeftFiducialPoints();
          
          java.awt.geom.Point2D.Double[] newEncControlSpots=new java.awt.geom.Point2D.Double[3];
          
          /*
          newEncControlSpots[0]=new java.awt.geom.Point2D.Double(9999999,0);
          newEncControlSpots[1]=new java.awt.geom.Point2D.Double(0,-999999);
          newEncControlSpots[2]=new java.awt.geom.Point2D.Double(-99999,0);
          //Builder newBuilder = TimeSeriesBase.builder();
          
          for(int i=0;i<spots2.size();i++){
            SuperSpot mySpot=spots2.get(i);
            
            //get the rightmost spot
            if(mySpot.getCentroidX()>newEncControlSpots[2].getX()){newEncControlSpots[2]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
            
            //get the bottommost spot
            if(mySpot.getCentroidY()>newEncControlSpots[1].getY()){newEncControlSpots[1]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
            
            //get the leftmost spot
            if(mySpot.getCentroidX()<newEncControlSpots[0].getX()){newEncControlSpots[0]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
            
            
          }*/
          
          newEncControlSpots[0]=new java.awt.geom.Point2D.Double(theEnc2.getLeftReferenceSpots()[0].getCentroidX(),theEnc2.getLeftReferenceSpots()[0].getCentroidY());
          newEncControlSpots[1]=new java.awt.geom.Point2D.Double(theEnc2.getLeftReferenceSpots()[1].getCentroidX(),theEnc2.getLeftReferenceSpots()[1].getCentroidY());
          newEncControlSpots[2]=new java.awt.geom.Point2D.Double(theEnc2.getLeftReferenceSpots()[2].getCentroidX(),theEnc2.getLeftReferenceSpots()[2].getCentroidY());
          
          
          //return to using persisted refSpots
          //return to using refSpots
          theEncControlSpots[0]=new java.awt.geom.Point2D.Double(theEnc.getLeftReferenceSpots()[0].getCentroidX(),theEnc.getLeftReferenceSpots()[0].getCentroidY());
          theEncControlSpots[1]=new java.awt.geom.Point2D.Double(theEnc.getLeftReferenceSpots()[1].getCentroidX(),theEnc.getLeftReferenceSpots()[1].getCentroidY());
          theEncControlSpots[2]=new java.awt.geom.Point2D.Double(theEnc.getLeftReferenceSpots()[2].getCentroidX(),theEnc.getLeftReferenceSpots()[2].getCentroidY());
          
          
          
          Collections.sort(spots2, new XComparator());
          
        
          
          double width1=theEncControlSpots[2].getX()-theEncControlSpots[0].getX();
          double width2=newEncControlSpots[2].getX()-newEncControlSpots[0].getX();

          //heights are from control spot 1 to intersection with line formed by control spots 0 and 2
          java.awt.geom.Point2D.Double notchControlPoint1=theEncControlSpots[1];
          java.awt.geom.Line2D.Double widthLine1=new java.awt.geom.Line2D.Double(theEncControlSpots[0],theEncControlSpots[2]);
          double height1=widthLine1.ptLineDist(theEncControlSpots[1]);

          //heights are from control spot 1 to intersection with line formed by control spots 0 and 2
          java.awt.geom.Point2D.Double notchControlPoint2=newEncControlSpots[1];
          java.awt.geom.Line2D.Double widthLine2=new java.awt.geom.Line2D.Double(newEncControlSpots[0],newEncControlSpots[2]);
          double height2=widthLine2.ptLineDist(newEncControlSpots[1]);
          double finalNum=Math.abs(1-new Double((width1/height1)/(width2/height2)));
          return finalNum;

         
      
    }
     catch(Exception e){
       e.printStackTrace();
       return 1.0;
     }
    
    
  }
    

public static java.awt.geom.Point2D.Double deriveThirdIsoscelesPoint(double x1, double x2, double y1, double y2){

    double h = Math.sqrt((x1-x2)*(x1-x2) +(y1-y2)*(y1-y2));
    double l = h * Math.sqrt(2) / 2;
    //console.log('h = %f, l = %f', h, l);
    double A = Math.asin((y1 - y2) / h);
    double B = Math.PI / 4 - A;
    //console.log('A=%f B=%f', A, B);
    double n = l * Math.sin(B);
    double m = l * Math.cos(B);
    //console.log('m = %f, n = %f', m, n);
    double x = x2 - m;
    double y = y1 - m;
    //console.log('(%f, %f)', x, y);
    x = x1 + n;
    y = y2 - n;
    //console.log('(%f, %f)', x, y);
    return new java.awt.geom.Point2D.Double(x,y);
}
    

    private boolean isDorsalFin(Encounter enc) {
        ArrayList<SuperSpot> spots = enc.getLeftReferenceSpots();
        if ((spots == null) || (spots.size() == 3)) return false;
        //System.out.println("  DORSAL!!!!");  
        return true;
    }
    
    public static boolean isDorsalFin(EncounterLite enc) {
      if ((enc.getLeftReferenceSpots() == null) || (enc.getLeftReferenceSpots().length == 3)) return false;
      //System.out.println("  DORSAL!!!!");  
      return true;
  }

    private void processDorsalSpots(Encounter enc, double dorsalRotationInDegree) {
        //note: (left)spots are from paths (edges) and leftReferenceSpots come from ref pts

        if (enc.getLeftReferenceSpots() == null) return;
        
        
      
        //first the reference spots (which are more or less required)
        //ArrayList<SuperSpot> spots = enc.getLeftReferenceSpots();
        int spotsSize=enc.getLeftReferenceSpots().size();
        ArrayList<SuperSpot> newSpots=new ArrayList<SuperSpot>();
        for(int i=0;i<spotsSize;i++){
          newSpots.add(new SuperSpot(enc.getLeftReferenceSpots().get(i).getCentroidX(),enc.getLeftReferenceSpots().get(i).getCentroidY()));  //swap out for converted
        }
        //System.out.println("Original ref sots size: "+spotsSize+"    NewSpots size: "+newSpots.size());
        
        
        //for matching purposes, i am allowing just the 3 "main" reference points as well as full 10.. not sure if this is best course of action?  TODO
        if ((spotsSize < 10) && (spotsSize != 3)) return;

        ArrayList<SuperSpot> ord = new ArrayList<SuperSpot>();  //going to be the properly ordered and inverted set of spots
        double vx = newSpots.get(0).getCentroidX();  //vertical line at back of fin
        double hy = newSpots.get(0).getCentroidY();  //horizontal line at bottom of fin

        //now we do some flipping so it is flukier
        ord.add(new SuperSpot(vx, hy));
        ord.add(new SuperSpot(vx, hy + hy - newSpots.get(1).getCentroidY()));
        ord.add(new SuperSpot(vx - Math.abs(vx - newSpots.get(2).getCentroidX()), newSpots.get(2).getCentroidY()));

        //these values are for rotating the trailing edge out
        double midpX = ord.get(0).getCentroidX() + (ord.get(2).getCentroidX() - ord.get(0).getCentroidX()) * 0.75;
        double midpY = ord.get(0).getCentroidY();
        double m = (ord.get(1).getCentroidY() - midpY) / (ord.get(1).getCentroidX() - midpX);
        double B = midpY - m * midpX;
        double topX = ord.get(1).getCentroidX();
        double topY = ord.get(1).getCentroidY();
        double rotateRadians = Math.toRadians(dorsalRotationInDegree);

        //we need to (*after* above calculations!) first rotate out ref[0] at the lower corner
        double sx = Math.cos(rotateRadians) * (vx - topX) - Math.sin(rotateRadians) * (hy - topY) + topX;
        double sy = Math.sin(rotateRadians) * (vx - topX) + Math.cos(rotateRadians) * (hy - topY) + topY;
        ord.set(0, new SuperSpot(sx, sy));

        //note: now we will ultimately flip it at the end as well, so we need the x-axis to flip it around, which should keep us in quadrant 1; the midpoint should do the trick
        //double mirrorAxis = (ord.get(0).getCentroidX() + ord.get(2).getCentroidX()) / 2;
        double mirrorAxis = ord.get(0).getCentroidX();
        //now that we have this, we flip the x values on the existing reference points (and use this going forward as well)
        for (int i = 0 ; i < 3 ; i++) {
            ord.get(i).setCentroidX(mirrorX(mirrorAxis, ord.get(i).getCentroidX()));
        }

        if (spotsSize > 3) {
            for (int i = 0 ; i < 3 ; i++) {  //3 line segments walking up fin
                SuperSpot a = new SuperSpot(mirrorX(mirrorAxis, vx - Math.abs(vx - newSpots.get(i*2+3).getCentroidX())), hy + hy - newSpots.get(i*2+3).getCentroidY());
                SuperSpot b = new SuperSpot(mirrorX(mirrorAxis, vx - Math.abs(vx - newSpots.get(i*2+4).getCentroidX())), hy + hy - newSpots.get(i*2+4).getCentroidY());
                if (a.getCentroidX() > b.getCentroidX()) {  //need order to be leftmost first
                    ord.add(b);
                    ord.add(a);
                } else {
                    ord.add(a);
                    ord.add(b);
                }
            }
            ord.add(new SuperSpot(mirrorX(mirrorAxis, vx - Math.abs(vx - newSpots.get(9).getCentroidX())), hy + hy - newSpots.get(9).getCentroidY()));  //9th one is opposite tip
        }
        //System.out.println("ord refspots size: "+ord.size());
        processLeftReferenceSpots(ord);

        //now the regular spots from the traced edges
        spotsSize=enc.getSpots().size();
        //System.out.println("newSpots2 orginal size: "+spotsSize);
        newSpots=new ArrayList<SuperSpot>();
        for(int i=0;i<spotsSize;i++){
          newSpots.add( new SuperSpot(enc.getSpots().get(i).getCentroidX(),enc.getSpots().get(i).getCentroidY()));  //swap out for converted
        }
        //System.out.println("newSpots after copy: "+newSpots.size());

        ArrayList<SuperSpot> newSpots2=new ArrayList<SuperSpot>();

/*
        int topI = -1;
        double topY = 0;
        for (int i = 0 ; i < spotsSize ; i++) {
            double sy = hy + hy - newSpots.get(i).getCentroidY();
double sx = vx - Math.abs(vx - newSpots.get(i).getCentroidX());
System.out.println(i + ": (" + sx + "," + sy + ")");
            if (sy > topY) {
                topY = sy;
                topI = i;
            }
        }
        double topX = vx - Math.abs(vx - newSpots.get(topI).getCentroidX());
System.out.println("====== top: i=" + topI + " (" + topX + "," + topY + ")");
*/

//System.out.println("ORD1 ------------------------------- (" + ord.get(1).getCentroidX() + "," + ord.get(1).getCentroidY() + ")");
/*
    var midp = [ s[0][0] + (s[2][0] - s[0][0]) * 0.75, s[0][1] ];
    if (bestPathParam.debug) debugCtx(itool.ctx, 'midpt(' + midp[0]+','+midp[1]+')', midp);
    var m = (s[1][1] - midp[1]) / (s[1][0] - midp[0]);
    var b = midp[1] - m * midp[0];
    //console.log('y = %.2fx + %.2f', m, b);
    var notchpt = s.splice(1, 1)[0];  //remove "notch" point, to be added later
    s.sort(function(a,b) { return a[1] - b[1]; });  //first we sort all by Y value
//console.log('sorted %o', s);
    // we return based on pn (0 or 1) and which side of midpoint/dividing line spot is on
    var h = [ notchpt ];  //notch always first, either side
    for (var i = 0 ; i < s.length ; i++) {
        var midx = (s[i][1] - b) / m;
//debugCtx(itool.ctx, 's'+i, [midx, s[i][1]]);
*/
        for (int i = 0 ; i < spotsSize ; i++) {
                sx = vx - Math.abs(vx - newSpots.get(i).getCentroidX());
                sy = hy + hy - newSpots.get(i).getCentroidY();
                double midX = (sy - B) / m;
                if (sx > midX) {
                    double sx2 = Math.cos(rotateRadians) * (sx - topX) - Math.sin(rotateRadians) * (sy - topY) + topX;
                    sy = Math.sin(rotateRadians) * (sx - topX) + Math.cos(rotateRadians) * (sy - topY) + topY;
                    sx = sx2;
                    newSpots2.add(new SuperSpot(mirrorX(mirrorAxis, sx), sy));
                }
/*
                if ((sx == topX) && (sy == topY)) {
System.out.println("hit top at i=" + i);
                    hitTop = true;
                }
*/
                //newSpots2.add(new SuperSpot(mirrorX(mirrorAxis, sx), sy));
        }
        //System.out.println("newSpots2 after second copy: "+newSpots2.size());

        //NEW MODS FOR TRAILING EDGE
        
        
        //Let's get the trailing edge only
      /*
        double midX=0;
        double midY=0;
        double highestY=-999999;
        double highestX=-999999;
        int highestYIndex=0;
        for (int i = 0 ; i < spotsSize ; i++) {
          midX+=newSpots2.get(i).getCentroidX();
          midY+=newSpots2.get(i).getCentroidY();
          if(newSpots2.get(i).getCentroidY()>highestY){
            highestY=newSpots2.get(i).getCentroidY();
            highestX=newSpots2.get(i).getCentroidX();
            highestYIndex=i;
          }
        }
        midX=midX/spotsSize;
        midY=midY/spotsSize;
        
        
        double xBetweenHighestandMid=(midX+highestX)/2;
        
        System.out.println("MidX is: "+midX+" and MidY is: "+midY);
        */
        
        //boolean highestSpotEncountered=false;
        /*
        for (int i = 0 ; i < newSpots2.size() ; i++) {
          if(newSpots2.get(i).getCentroidX()<xBetweenHighestandMid){
          
            //System.out.println("     REMOVE: "+newSpots2.get(i).getCentroidX()+":"+newSpots2.get(i).getCentroidY());
            newSpots2.remove(i);
            i--;
          }
          
        }
        
        System.out.println("newSpots2 after reduction to trailing edge: "+newSpots2.size());
        */
        /*
        ArrayList<SuperSpot> newSpots3=new ArrayList<SuperSpot>();
        for (int i = 0 ; i < newSpots2.size() ; i++) {
          
          //put highest point at 0,0
          double x1=newSpots2.get(i).getCentroidX()-midX;
          double y1=newSpots2.get(i).getCentroidY()-midY;
          
          if(newSpots2.get(i).getCentroidX()>highestX){
            double radianValue=Math.toRadians(10);
            double x2 = x1 *Math.cos(radianValue) - y1* Math.sin(radianValue)+midX;
            double y2 = x1 *Math.sin(radianValue) + y1* Math.cos(radianValue)+midY;
            newSpots3.add(new SuperSpot(x2,y2));
          }
          else{
            double radianValue=Math.toRadians(-10);
            //double x2 = x1 *Math.cos(radianValue) - y1* Math.sin(radianValue)+midX;
            //double y2 = x1 *Math.sin(radianValue) + y1* Math.cos(radianValue)+midY;
            double x2 = highestX + (newSpots2.get(i).getCentroidX()-highestX)*Math.cos(radianValue) - (newSpots2.get(i).getCentroidY()-highestY)*Math.sin(radianValue);

            double y2 = highestY + (newSpots2.get(i).getCentroidX()-highestX)*Math.sin(radianValue) + (newSpots2.get(i).getCentroidY()-highestY)*Math.cos(radianValue);
            newSpots3.add(new SuperSpot(x2,y2));
          }
          
          
          
        }
        */
        //newSpots2=newSpots3;
        
        int newSpots2size=newSpots2.size();
        SummaryStatistics dSpots=new SummaryStatistics();
        for(int i=0;i<newSpots2size;i++){
          SuperSpot hSpot=newSpots2.get(i);
          dSpots.addValue(hSpot.getCentroidX());
        }
        double stdDev=dSpots.getStandardDeviation();
        double mean=dSpots.getMean();
        double lowerBound=mean-2*stdDev;
        double upperBound=mean+2*stdDev;
        
        
        //let's filter out the out 5%
        ArrayList<SuperSpot> newSpots3=new ArrayList<SuperSpot>();
        for(int i=0;i<newSpots2.size();i++){
          SuperSpot hSpot=newSpots2.get(i);
          double xVal=hSpot.getCentroidX();
          if(xVal<lowerBound){
           // System.out.println("Removing lowerbound: "+hSpot.getCentroidX()+" less than "+(mean-2*stdDev));
          }
          else if(xVal>upperBound){
            //System.out.println("Removing upperbound: "+hSpot.getCentroidX()+" above "+(mean+2*stdDev));
            
          }
          else{
            //System.out.println("Adding value: "+hSpot.getCentroidX());
            newSpots3.add(hSpot);}
        }
        
        
        
        
        //END NEW MODS FOR TRAILING EDGE
        //processLeftSpots(newSpots2);
        processLeftSpots(newSpots3);
       
    }
    
    
    public static Double getSwaleMatchScore(EncounterLite theEnc,EncounterLite theEnc2, double penalty, double reward, double epsilon){

      //steps to make this work for flukes
      //1. normalize fluke width 0 to 1 - call this x axis
      //2. make Line2D from tip to tip points for both patterns - oldLine and newLine
      //3. measure distance down from tip line - call this y axis
      //4. use oldEnc points as X[]
      //5. form every pair of lines between two points in newEnc and create and find Y distance to that point from its corresponding x coordinate from oldEnc's points at proportion of width 
      
        try{
          
          ArrayList<SuperSpot> oldSpots=theEnc.getSpots();
          if(theEnc.getRightSpots()!=null){
            oldSpots.addAll(theEnc.getRightSpots());
          }
            Collections.sort(oldSpots, new XComparator());
            
            //let's prefilter old spots for outlies outside the bounds
            SuperSpot oldLeftmostSpot=new SuperSpot(99999,-99999);
            SuperSpot oldRightmostSpot=new SuperSpot(-999999,-99999);
            for(int i=0;i<oldSpots.size();i++){
              if(theEnc.getLeftReferenceSpots()[0].getCentroidX()<theEnc.getLeftReferenceSpots()[2].getCentroidX()){
                  boolean haveRemoved=false;
                  SuperSpot theSpot=oldSpots.get(i);
                  if(theSpot.getCentroidX()<=theEnc.getLeftReferenceSpots()[0].getCentroidX()){
                    oldSpots.remove(i);
                    i--;
                    haveRemoved=true;
                  }
                  if(theSpot.getCentroidX()>=theEnc.getLeftReferenceSpots()[2].getCentroidX()){
                    oldSpots.remove(i);
                    i--;
                    haveRemoved=true;
                  }
                  if(!haveRemoved){
                    if(theSpot.getCentroidX()>oldRightmostSpot.getCentroidX()){oldRightmostSpot=theSpot;}
                    if(theSpot.getCentroidX()<oldLeftmostSpot.getCentroidX()){oldLeftmostSpot=theSpot;}
                  }
              }
            }
            
            
            
            int numOldSpots=oldSpots.size();
            
            //initialize our output series
            ArrayList<Point> theEncDataCollectionEvents=new ArrayList<Point>();
            ArrayList<Point> theEnc2DataCollectionEvents=new ArrayList<Point>();
            
          
          SuperSpot[] oldReferenceSpots=theEnc.getLeftReferenceSpots();
          Line2D.Double oldLine=new Line2D.Double(oldReferenceSpots[0].getCentroidX(), oldReferenceSpots[0].getCentroidY(), oldReferenceSpots[2].getCentroidX(), oldReferenceSpots[2].getCentroidY());
          double oldLineWidth=Math.abs(oldReferenceSpots[2].getCentroidX()-oldReferenceSpots[0].getCentroidX());
          if(EncounterLite.isDorsalFin(theEnc)){
            oldLineWidth=Math.abs(theEnc.getLeftmostSpot()-theEnc.getRightmostSpot());
          }
          
          //first populate OLD_VALUES - easy
          
          for(int i=0;i<numOldSpots;i++){
            SuperSpot theSpot=oldSpots.get(i);
            java.awt.geom.Point2D.Double thePoint=new java.awt.geom.Point2D.Double(theSpot.getCentroidX(),theSpot.getCentroidY());
            //System.out.println("Swale Dist1: "+oldLine.ptLineDist(thePoint)/oldLineWidth);
            double[] myDub={0,(oldLine.ptLineDist(thePoint)/oldLineWidth),i};
            theEncDataCollectionEvents.add( new org.ecocean.timeseries.core.Point( myDub ) );
            
            
          }
          
          
          //second populate NEW_VALUES - trickier
          
          //create an array of lines made from all point pairs in newEnc
          
          SuperSpot[] newReferenceSpots=theEnc2.getLeftReferenceSpots();
          Line2D.Double newLine=new Line2D.Double(newReferenceSpots[0].getCentroidX(), newReferenceSpots[0].getCentroidY(), newReferenceSpots[2].getCentroidX(), newReferenceSpots[2].getCentroidY());
          double newLineWidth=Math.abs(newReferenceSpots[2].getCentroidX()-newReferenceSpots[0].getCentroidX());
          if(EncounterLite.isDorsalFin(theEnc2)){
            newLineWidth=Math.abs(theEnc2.getLeftmostSpot()-theEnc2.getRightmostSpot());
          }
          
          ArrayList<SuperSpot> newSpots=new ArrayList<SuperSpot>();
          if(theEnc2.getSpots()!=null){newSpots.addAll(theEnc2.getSpots());};
              if(theEnc2.getRightSpots()!=null){newSpots.addAll(theEnc2.getRightSpots());};
          int numNewEncSpots=newSpots.size();
          
          //if(isDorsalFin(theEnc)){newLineWidth=getLineWidth(newSpots);}
          
          //reset newLineWidth HERE IF DORSAL
          //if(isDorsalFin(theEnc2)){}
          
          Line2D.Double[] newLines=new Line2D.Double[numNewEncSpots-1];
          Collections.sort(newSpots, new XComparator());
          
          
          for(int i=0;i<(numNewEncSpots-1);i++){
            //convert y coords to distance from newLine
            double x1=(newSpots.get(i).getCentroidX()-newReferenceSpots[0].getCentroidX())/newLineWidth;
            double x2=(newSpots.get(i+1).getCentroidX()-newReferenceSpots[0].getCentroidX())/newLineWidth;
            double yCoord1=newLine.ptLineDist(newSpots.get(i).getCentroidX(), newSpots.get(i).getCentroidY())/newLineWidth;
            double yCoord2=newLine.ptLineDist(newSpots.get(i+1).getCentroidX(), newSpots.get(i+1).getCentroidY())/newLineWidth;
            newLines[i]=new Line2D.Double(x1, yCoord1, x2, yCoord2);
          }
          int numNewLines=newLines.length;
          
          //now iterate and create our points
          SuperSpot newLeftmostSpot=new SuperSpot(99999,-99999);
          SuperSpot newRightmostSpot=new SuperSpot(-999999,-99999);
          for(int i=0;i<numOldSpots;i++){
            SuperSpot theSpot=oldSpots.get(i);
            double xCoordFraction=(theSpot.getCentroidX()-oldReferenceSpots[0].getCentroidX())/oldLineWidth;
            Line2D.Double theReallyLongLine=new Line2D.Double(xCoordFraction, -99999999, xCoordFraction, 99999999);
            
            //now we need to find where this point falls on the newEnc pattern
            Line2D.Double intersectionLine=null;
            int lineIterator=0;
            while((intersectionLine==null)&&(lineIterator<numNewLines)){
              //System.out.println("     Comparing line: ["+newLines[lineIterator].getX1()+","+newLines[lineIterator].getY1()+","+newLines[lineIterator].getX2()+","+newLines[lineIterator].getY2()+"]"+" to ["+theReallyLongLine.getX1()+","+theReallyLongLine.getY1()+","+theReallyLongLine.getX2()+","+theReallyLongLine.getY2()+"]");
              if(newLines[lineIterator].intersectsLine(theReallyLongLine)){
                intersectionLine=newLines[lineIterator];
                //System.out.println("!!!!!!FOUND the INTERSECT!!!!!!");
              }
              lineIterator++;
            }
            try{
              double slope=(intersectionLine.getY2()-intersectionLine.getY1())/(intersectionLine.getX2()-intersectionLine.getX1());
              double yCoord=intersectionLine.getY1()+(xCoordFraction-intersectionLine.getX1())*slope;
              
              SuperSpot newSpot=new SuperSpot(xCoordFraction,yCoord);
              
              //NEW_VALUES[i]=yCoord;
              //theEnc2DataCollectionEvents.addPoint(new DataCollectionEvent(i,yCoord));
              
              if(newSpot.getCentroidX()>newRightmostSpot.getCentroidX()){newRightmostSpot=newSpot;}
              if(newSpot.getCentroidX()<newLeftmostSpot.getCentroidX()){newLeftmostSpot=newSpot;}
            
              
              double[] myDub={0,yCoord,i};
              //System.out.println("Swale Dist2: "+yCoord);
              
              theEnc2DataCollectionEvents.add( new org.ecocean.timeseries.core.Point( myDub ) );
              
              
            }
        catch(Exception e){
           System.out.println("Hit an exception with spot: ["+theSpot.getCentroidX()+","+theSpot.getCentroidY()+"]");
          double[] myDub={0,0,i};
          theEnc2DataCollectionEvents.add( new org.ecocean.timeseries.core.Point( myDub ) );
          
        }
            
            
          }
          
        //SwaleSimilarityComputor swaleComp=new SwaleSimilarityComputor(penalty, reward, epsilon);
          SwaleOperator swaleOp=new SwaleOperator(penalty,reward,epsilon);
          
          //DistanceOperator op  = (DistanceOperator)Class.forName("core.distance.SwaleOperator").newInstance();
          Trajectory oldSeries=new Trajectory(0,theEncDataCollectionEvents,swaleOp);
          Trajectory newSeries=new Trajectory(1,theEnc2DataCollectionEvents,swaleOp);
          
          
          //java.lang.Double matchResult=new java.lang.Double(swaleComp.similarity(oldSerie, newSerie));
          java.lang.Double matchResult=new java.lang.Double(swaleOp.computeDistance(oldSeries, newSeries));
          
          
          //System.out.println("");  
          //System.out.println("   !!!!I found an MSM match score of: "+matchResult);  
          //System.out.println("");  
            return matchResult;
          }
          catch(Exception e){
            e.printStackTrace();
          }
          return null;
      
    }
    
    //Threshold: 7627.88339718089
    //Max Threshold: 7627.929936152444
    //Min Threshold: 7627.88339718089
    //Step: 4.6538971553307157E-4
    

    
    public static Double getHolmbergIntersectionScore(EncounterLite theEnc,EncounterLite theEnc2){
      
      try{
        ArrayList<SuperSpot> spots=new ArrayList<SuperSpot>();
        if(theEnc.getSpots()!=null){spots.addAll(theEnc.getSpots());}
        if(theEnc.getRightSpots()!=null){spots.addAll(theEnc.getRightSpots());}
        //sort the Array - lowest x to highest X coordinate
        Collections.sort(spots, new XComparator());
        //let's prefilter old spots for outlies outside the bounds
        for(int i=0;i<spots.size();i++){
          if(theEnc.getLeftReferenceSpots()[0].getCentroidX()<theEnc.getLeftReferenceSpots()[2].getCentroidX()){
            SuperSpot theSpot=spots.get(i);
            if(theSpot.getCentroidX()<=theEnc.getLeftReferenceSpots()[0].getCentroidX()){
              spots.remove(i);
              i--;
            }
            if(theSpot.getCentroidX()>=theEnc.getLeftReferenceSpots()[2].getCentroidX()){
              spots.remove(i);
              i--;
            }
        }
        }
        int numOldSpots=spots.size();
        
        
        
          
       java.awt.geom.Point2D.Double[] theEncControlSpots=new java.awt.geom.Point2D.Double[3];
       
         ArrayList<SuperSpot> spots2=new ArrayList<SuperSpot>();
        if(theEnc2.getSpots()!=null){spots2.addAll(theEnc2.getSpots());}
        if(theEnc2.getRightSpots()!=null){spots2.addAll(theEnc2.getRightSpots());}
          //newEncControlSpots = theEnc2.getThreeLeftFiducialPoints();
          
          java.awt.geom.Point2D.Double[] newEncControlSpots=new java.awt.geom.Point2D.Double[3];
      
          newEncControlSpots[0]=new java.awt.geom.Point2D.Double(theEnc2.getLeftReferenceSpots()[0].getCentroidX(),theEnc2.getLeftReferenceSpots()[0].getCentroidY());
          newEncControlSpots[1]=new java.awt.geom.Point2D.Double(theEnc2.getLeftReferenceSpots()[1].getCentroidX(),theEnc2.getLeftReferenceSpots()[1].getCentroidY());
          newEncControlSpots[2]=new java.awt.geom.Point2D.Double(theEnc2.getLeftReferenceSpots()[2].getCentroidX(),theEnc2.getLeftReferenceSpots()[2].getCentroidY());
          
          
          //return to using persisted refSpots
          theEncControlSpots[0]=new java.awt.geom.Point2D.Double(theEnc.getLeftReferenceSpots()[0].getCentroidX(),theEnc.getLeftReferenceSpots()[0].getCentroidY());
          theEncControlSpots[1]=new java.awt.geom.Point2D.Double(theEnc.getLeftReferenceSpots()[1].getCentroidX(),theEnc.getLeftReferenceSpots()[1].getCentroidY());
          theEncControlSpots[2]=new java.awt.geom.Point2D.Double(theEnc.getLeftReferenceSpots()[2].getCentroidX(),theEnc.getLeftReferenceSpots()[2].getCentroidY());
          
          Collections.sort(spots2, new XComparator());
          
        //new arrays
          ArrayList<SuperSpot> formerSpots=new ArrayList<SuperSpot>();
          ArrayList<SuperSpot> newerSpots=new ArrayList<SuperSpot>();
        
        SuperSpot[] oldReferenceSpots=theEnc.getLeftReferenceSpots();
        Line2D.Double oldLine=new Line2D.Double(oldReferenceSpots[0].getCentroidX(), oldReferenceSpots[0].getCentroidY(), oldReferenceSpots[2].getCentroidX(), oldReferenceSpots[2].getCentroidY());
        double oldLineWidth=Math.abs(oldReferenceSpots[2].getCentroidX()-oldReferenceSpots[0].getCentroidX());
        if(EncounterLite.isDorsalFin(theEnc)){
          oldLineWidth=Math.abs(theEnc.getLeftmostSpot()-theEnc.getRightmostSpot());
        }
        
        SuperSpot[] newReferenceSpots=theEnc2.getLeftReferenceSpots();
        Line2D.Double newLine=new Line2D.Double(newReferenceSpots[0].getCentroidX(), newReferenceSpots[0].getCentroidY(), newReferenceSpots[2].getCentroidX(), newReferenceSpots[2].getCentroidY());
        double newLineWidth=Math.abs(newReferenceSpots[2].getCentroidX()-newReferenceSpots[0].getCentroidX());
        if(EncounterLite.isDorsalFin(theEnc2)){
          newLineWidth=Math.abs(theEnc2.getLeftmostSpot()-theEnc2.getRightmostSpot());
        }
        
        //first populate OLD_VALUES - easy
        
        for(int i=0;i<numOldSpots;i++){
          SuperSpot theSpot=spots.get(i);
          java.awt.geom.Point2D.Double thePoint=new java.awt.geom.Point2D.Double(theSpot.getCentroidX(),theSpot.getCentroidY());
          SuperSpot m_spot=new SuperSpot(thePoint.getX()/oldLineWidth,oldLine.ptLineDist(thePoint)/oldLineWidth);
          formerSpots.add(m_spot);
          //System.out.println(".....adding: ["+m_spot.getCentroidX()+","+m_spot.getCentroidY()+"]");
        }
        
        
        //second populate NEW_VALUES - trickier
        
        //create an array of lines made from all point pairs in newEnc
        
        
        ArrayList<SuperSpot> newSpots=new ArrayList<SuperSpot>();
        if(theEnc2.getSpots()!=null){newSpots.addAll(theEnc2.getSpots());}
        if(theEnc2.getRightSpots()!=null){newSpots.addAll(theEnc2.getRightSpots());}
        int numNewEncSpots=newSpots.size();
        //if(isDorsalFin(theEnc)){newLineWidth=getLineWidth(newSpots);}
        Line2D.Double[] newLines=new Line2D.Double[numNewEncSpots-1];
        //Collections.sort(newSpots, new XComparator());
        for(int i=0;i<(numNewEncSpots-1);i++){
          //convert y coords to distance from newLine
          double x1=(newSpots.get(i).getCentroidX()-newReferenceSpots[0].getCentroidX())/newLineWidth;
          double x2=(newSpots.get(i+1).getCentroidX()-newReferenceSpots[0].getCentroidX())/newLineWidth;
          double yCoord1=newLine.ptLineDist(newSpots.get(i).getCentroidX(), newSpots.get(i).getCentroidY())/newLineWidth;
          double yCoord2=newLine.ptLineDist(newSpots.get(i+1).getCentroidX(), newSpots.get(i+1).getCentroidY())/newLineWidth;
          newLines[i]=new Line2D.Double(x1, yCoord1, x2, yCoord2);
        }
        int numNewLines=newLines.length;
        
        
        
        //now iterate and create our points
        for(int i=0;i<numOldSpots;i++){
          SuperSpot theSpot=spots.get(i);
          double xCoordFraction=(theSpot.getCentroidX()-oldReferenceSpots[0].getCentroidX())/oldLineWidth;
          Line2D.Double theReallyLongLine=new Line2D.Double(xCoordFraction, -99999999, xCoordFraction, 99999999);
          
          //now we need to find where this point falls on the newEnc pattern
          Line2D.Double intersectionLine=null;
          int lineIterator=0;
          while((intersectionLine==null)&&(lineIterator<numNewLines)){
            //System.out.println("     Comparing line: ["+newLines[lineIterator].getX1()+","+newLines[lineIterator].getY1()+","+newLines[lineIterator].getX2()+","+newLines[lineIterator].getY2()+"]"+" to ["+theReallyLongLine.getX1()+","+theReallyLongLine.getY1()+","+theReallyLongLine.getX2()+","+theReallyLongLine.getY2()+"]");
            if(newLines[lineIterator].intersectsLine(theReallyLongLine)){
              intersectionLine=newLines[lineIterator];
              //System.out.println("!!!!!!FOUND the INTERSECT!!!!!!");
            }
            lineIterator++;
          }
          try{
            double slope=(intersectionLine.getY2()-intersectionLine.getY1())/(intersectionLine.getX2()-intersectionLine.getX1());
            double yCoord=intersectionLine.getY1()+(xCoordFraction-intersectionLine.getX1())*slope;
            
            //Point2D.Double thePoint=new Point2D.Double(xCoordFraction,yCoord);
            
            SuperSpot m_spot=new SuperSpot(formerSpots.get(i).getCentroidX(),yCoord);
            //System.out.println(".....adding: ["+m_spot.getCentroidX()+","+yCoord+"]");
            newerSpots.add(m_spot);
          }
          catch(Exception e){
            System.out.println("Hit an exception with spot: ["+theSpot.getCentroidX()+","+theSpot.getCentroidY()+"]");
            SuperSpot m_spot=new SuperSpot(formerSpots.get(i).getCentroidX(),0);
            newerSpots.add(m_spot);
          }
        }
          //continue old method
          
          //in advance of any intersection
          //create a list of Poin2DDouble Pair proportional distances from the notch
          ArrayList<Double> intersectionsProportionalDistances=new ArrayList<Double>();
          
          //let's try some fun intersection analysis
          spots=formerSpots;
          spots2=newerSpots;
          int newPrintSize=spots2.size();
          int thisPrintSize=spots.size();
          
          //calculate smallest array size and then -1 for max number of potential lines to match
          int maxIntersectingLines=newPrintSize-1;
          if(thisPrintSize<newPrintSize){maxIntersectingLines=thisPrintSize-1;}
          
          double numIntersections=0;
          StringBuffer anglesOfIntersection=new StringBuffer("");
          for(int i=0;i<(newPrintSize-1);i++){
            //for(int j=i+1;j<newPrintSize;j++){
            int j=i+1;
            
            java.awt.geom.Point2D.Double transformedStartPoint=new java.awt.geom.Point2D.Double(spots2.get(i).getCentroidX(),spots2.get(i).getCentroidY());
            
            java.awt.geom.Point2D.Double transformedEndPoint=new java.awt.geom.Point2D.Double(spots2.get(j).getCentroidX(),spots2.get(j).getCentroidY());
            
              java.awt.geom.Point2D.Double newStart=(new  java.awt.geom.Point2D.Double(transformedStartPoint.getX(),transformedStartPoint.getY()));
              java.awt.geom.Point2D.Double newEnd=(new  java.awt.geom.Point2D.Double(transformedEndPoint.getX(),transformedEndPoint.getY()) ) ;
              java.awt.geom.Line2D.Double newLine2=new java.awt.geom.Line2D.Double(newStart,newEnd  );
            
              //now compare to thisPattern
              int m=0;
              //only allow one intersection per line
              boolean foundIntersect=false;
              while((!foundIntersect)&&(m<(thisPrintSize-1))){
       
                    int n=m+1;
                    
                    java.awt.geom.Point2D.Double thisStart=(new  java.awt.geom.Point2D.Double(spots.get(m).getCentroidX(),spots.get(m).getCentroidY()));
                    java.awt.geom.Point2D.Double thisEnd=(new  java.awt.geom.Point2D.Double(spots.get(n).getCentroidX(),spots.get(n).getCentroidY()) );   
                    java.awt.geom.Line2D.Double thisLine=new java.awt.geom.Line2D.Double(thisStart,thisEnd);
                    
                    if(newLine2.intersectsLine(thisLine)){
                        numIntersections++;
                        foundIntersect=true;
                        String intersectionAngle=java.lang.Double.toString(EncounterLite.angleBetween2Lines(newLine2, thisLine));
                        anglesOfIntersection.append(intersectionAngle+",");
                        
                        //calculate proportional distance to test if intersection was valid in original space
                        //untranslate new points since they were mapped into this points
                        java.awt.geom.Point2D.Double intersectionPoint=EncounterLite.getIntersectionPoint(newLine2,thisLine);
          
                        
                      }
                    m++;
              }
              
            
            
            
          }
          return (numIntersections/maxIntersectingLines);
          //return (numIntersections);
      
    }
     catch(Exception e){
       e.printStackTrace();
       return 0.0;
     }
    
    
  }
    
    public Long getDateLong(){return dateLong;}
    public void setDateLong(Long value){dateLong=value;}
    
    public String getGenusSpecies(){return genusSpecies;}
    public void setGenusSpecies(String value){genusSpecies=value;}
    
    public String getPatterningCode(){return patterningCode;}
    public void setPatterningCode(String value){patterningCode=value;}
    
    //Technique developed by Erin Falcone at Cascadia Research Collective
    //The lower the number the better
    //Both EncounterLite objects must have the full 19 left reference spots for this to work
    public static Double getCascadiaDorsalProportionsScore(EncounterLite theEnc, EncounterLite theEnc2){
      
      java.lang.Double theScore=weka.core.Utils.missingValue();
      if((theEnc.getLeftReferenceSpots().length<10)||(theEnc2.getLeftReferenceSpots().length<10)){return theScore;}
      
      try{

        
          SuperSpot[] oldReferenceSpots=theEnc.getLeftReferenceSpots();
          System.out.println("oldRefSpots size is: "+oldReferenceSpots.length);
          Line2D.Double oldLine=new Line2D.Double(oldReferenceSpots[0].getCentroidX(), oldReferenceSpots[0].getCentroidY(), oldReferenceSpots[1].getCentroidX(), oldReferenceSpots[1].getCentroidY());
          double measure1a=Math.abs(oldReferenceSpots[1].getCentroidX()-oldReferenceSpots[0].getCentroidX());
          double measure2a=Math.abs(oldReferenceSpots[1].getCentroidX()-oldReferenceSpots[9].getCentroidX());
          double measure3a=Math.abs(oldReferenceSpots[1].getCentroidX()-oldReferenceSpots[8].getCentroidX());
          double measure4a=Math.abs(oldReferenceSpots[8].getCentroidX()-oldReferenceSpots[7].getCentroidX());
          double measure5a=Math.abs(oldReferenceSpots[1].getCentroidX()-oldReferenceSpots[6].getCentroidX());
          double measure6a=Math.abs(oldReferenceSpots[6].getCentroidX()-oldReferenceSpots[5].getCentroidX());
          double measure7a=Math.abs(oldReferenceSpots[1].getCentroidX()-oldReferenceSpots[4].getCentroidX());
          double measure8a=Math.abs(oldReferenceSpots[4].getCentroidX()-oldReferenceSpots[3].getCentroidX());
        double r1a=measure2a/measure4a;
        double r2a=measure4a/measure6a;
        double r3a=measure6a/measure8a;
        double r4a=measure3a/measure4a;
        double r5a=measure5a/measure6a;
        double r6a=measure7a/measure8a;
          
          
          
          
          
          
          SuperSpot[] newReferenceSpots=theEnc2.getLeftReferenceSpots();
          System.out.println("newRefSpots size is: "+newReferenceSpots.length);
          
          Line2D.Double newLine=new Line2D.Double(newReferenceSpots[0].getCentroidX(), newReferenceSpots[0].getCentroidY(), newReferenceSpots[1].getCentroidX(), newReferenceSpots[1].getCentroidY());
          double measure1b=Math.abs(newReferenceSpots[1].getCentroidX()-newReferenceSpots[0].getCentroidX());
          double measure2b=Math.abs(newReferenceSpots[1].getCentroidX()-newReferenceSpots[9].getCentroidX());
          double measure3b=Math.abs(newReferenceSpots[1].getCentroidX()-newReferenceSpots[8].getCentroidX());
          double measure4b=Math.abs(newReferenceSpots[8].getCentroidX()-newReferenceSpots[7].getCentroidX());
          double measure5b=Math.abs(newReferenceSpots[1].getCentroidX()-newReferenceSpots[6].getCentroidX());
          double measure6b=Math.abs(newReferenceSpots[6].getCentroidX()-newReferenceSpots[5].getCentroidX());
          double measure7b=Math.abs(newReferenceSpots[1].getCentroidX()-newReferenceSpots[4].getCentroidX());
          double measure8b=Math.abs(newReferenceSpots[4].getCentroidX()-newReferenceSpots[3].getCentroidX());
        double r1b=measure2b/measure4b;
        double r2b=measure4b/measure6b;
        double r3b=measure6b/measure8b;
        double r4b=measure3b/measure4b;
        double r5b=measure5b/measure6b;
        double r6b=measure7b/measure8b;
        
        double combinedr1=Math.abs(1-r1a/r1b);
        double combinedr2=Math.abs(1-r2a/r2b);
        double combinedr3=Math.abs(1-r3a/r3b);
        double combinedr4=Math.abs(1-r4a/r4b);
        double combinedr5=Math.abs(1-r5a/r5b);
        double combinedr6=Math.abs(1-r6a/r6b);
        
        theScore=combinedr1+combinedr2+combinedr3+combinedr4+combinedr5+combinedr6;
        

        
      }
      catch(Exception e){
        e.printStackTrace();
      }
      
      
      return theScore;
    }
    
    
    private double mirrorX(double axisX, double inputX) {
        return -(inputX - axisX) + axisX;
    }

    public static Double getLineWidth(ArrayList<SuperSpot> spots){
      int numSPots=spots.size();
      SuperSpot leftmostSpot=new SuperSpot(999999,999999);
      SuperSpot rightmostSpot=new SuperSpot(-999999,999999);
      for(int i=0;i<numSPots;i++){
        SuperSpot theSpot=spots.get(i);
        if(theSpot.getCentroidX()>rightmostSpot.getCentroidX()){rightmostSpot=theSpot;}
        if(theSpot.getCentroidX()<leftmostSpot.getCentroidX()){leftmostSpot=theSpot;}
      }
      
      return Math.abs(rightmostSpot.getCentroidX()-leftmostSpot.getCentroidX());
    }

    
    public static Double getEuclideanDistanceScore(EncounterLite theEnc,EncounterLite theEnc2){

      //steps to make this work for flukes
      //1. normalize fluke width 0 to 1 - call this x axis
      //2. make Line2D from tip to tip points for both patterns - oldLine and newLine
      //3. measure distance down from tip line - call this y axis
      //4. use oldEnc points as X[]
      //5. form every pair of lines between two points in newEnc and create and find Y distance to that point from its corresponding x coordinate from oldEnc's points at proportion of width 
      
        try{
          
          ArrayList<SuperSpot> oldSpots=theEnc.getSpots();
          if(theEnc.getRightSpots()!=null){
            oldSpots.addAll(theEnc.getRightSpots());
          }
            Collections.sort(oldSpots, new XComparator());
            
            //let's prefilter old spots for outlies outside the bounds
            SuperSpot oldLeftmostSpot=new SuperSpot(99999,-99999);
            SuperSpot oldRightmostSpot=new SuperSpot(-999999,-99999);
            for(int i=0;i<oldSpots.size();i++){
              if(theEnc.getLeftReferenceSpots()[0].getCentroidX()<theEnc.getLeftReferenceSpots()[2].getCentroidX()){
                  boolean haveRemoved=false;
                  SuperSpot theSpot=oldSpots.get(i);
                  if(theSpot.getCentroidX()<=theEnc.getLeftReferenceSpots()[0].getCentroidX()){
                    oldSpots.remove(i);
                    i--;
                    haveRemoved=true;
                  }
                  if(theSpot.getCentroidX()>=theEnc.getLeftReferenceSpots()[2].getCentroidX()){
                    oldSpots.remove(i);
                    i--;
                    haveRemoved=true;
                  }
                  if(!haveRemoved){
                    if(theSpot.getCentroidX()>oldRightmostSpot.getCentroidX()){oldRightmostSpot=theSpot;}
                    if(theSpot.getCentroidX()<oldLeftmostSpot.getCentroidX()){oldLeftmostSpot=theSpot;}
                  }
              }
            }
            
            
            
            int numOldSpots=oldSpots.size();
            
            //initialize our output series
            ArrayList<Point> theEncDataCollectionEvents=new ArrayList<Point>();
            ArrayList<Point> theEnc2DataCollectionEvents=new ArrayList<Point>();
            
          
          SuperSpot[] oldReferenceSpots=theEnc.getLeftReferenceSpots();
          Line2D.Double oldLine=new Line2D.Double(oldReferenceSpots[0].getCentroidX(), oldReferenceSpots[0].getCentroidY(), oldReferenceSpots[2].getCentroidX(), oldReferenceSpots[2].getCentroidY());
          double oldLineWidth=Math.abs(oldReferenceSpots[2].getCentroidX()-oldReferenceSpots[0].getCentroidX());
          if(EncounterLite.isDorsalFin(theEnc)){
            oldLineWidth=Math.abs(theEnc.getLeftmostSpot()-theEnc.getRightmostSpot());
          }
          
          //first populate OLD_VALUES - easy
          
          for(int i=0;i<numOldSpots;i++){
            SuperSpot theSpot=oldSpots.get(i);
            java.awt.geom.Point2D.Double thePoint=new java.awt.geom.Point2D.Double(theSpot.getCentroidX(),theSpot.getCentroidY());
            //System.out.println("Swale Dist1: "+oldLine.ptLineDist(thePoint)/oldLineWidth);
            double[] myDub={0,(oldLine.ptLineDist(thePoint)/oldLineWidth),i};
            theEncDataCollectionEvents.add( new org.ecocean.timeseries.core.Point( myDub ) );
            
            
          }
          
          
          //second populate NEW_VALUES - trickier
          
          //create an array of lines made from all point pairs in newEnc
          
          SuperSpot[] newReferenceSpots=theEnc2.getLeftReferenceSpots();
          Line2D.Double newLine=new Line2D.Double(newReferenceSpots[0].getCentroidX(), newReferenceSpots[0].getCentroidY(), newReferenceSpots[2].getCentroidX(), newReferenceSpots[2].getCentroidY());
          double newLineWidth=Math.abs(newReferenceSpots[2].getCentroidX()-newReferenceSpots[0].getCentroidX());
          if(EncounterLite.isDorsalFin(theEnc2)){
            newLineWidth=Math.abs(theEnc2.getLeftmostSpot()-theEnc2.getRightmostSpot());
          }
          
          ArrayList<SuperSpot> newSpots=new ArrayList<SuperSpot>();
          if(theEnc2.getSpots()!=null){newSpots.addAll(theEnc2.getSpots());};
              if(theEnc2.getRightSpots()!=null){newSpots.addAll(theEnc2.getRightSpots());};
          int numNewEncSpots=newSpots.size();
          
          //if(isDorsalFin(theEnc)){newLineWidth=getLineWidth(newSpots);}
          
          //reset newLineWidth HERE IF DORSAL
          //if(isDorsalFin(theEnc2)){}
          
          Line2D.Double[] newLines=new Line2D.Double[numNewEncSpots-1];
          Collections.sort(newSpots, new XComparator());
          
          
          for(int i=0;i<(numNewEncSpots-1);i++){
            //convert y coords to distance from newLine
            double x1=(newSpots.get(i).getCentroidX()-newReferenceSpots[0].getCentroidX())/newLineWidth;
            double x2=(newSpots.get(i+1).getCentroidX()-newReferenceSpots[0].getCentroidX())/newLineWidth;
            double yCoord1=newLine.ptLineDist(newSpots.get(i).getCentroidX(), newSpots.get(i).getCentroidY())/newLineWidth;
            double yCoord2=newLine.ptLineDist(newSpots.get(i+1).getCentroidX(), newSpots.get(i+1).getCentroidY())/newLineWidth;
            newLines[i]=new Line2D.Double(x1, yCoord1, x2, yCoord2);
          }
          int numNewLines=newLines.length;
          
          //now iterate and create our points
          SuperSpot newLeftmostSpot=new SuperSpot(99999,-99999);
          SuperSpot newRightmostSpot=new SuperSpot(-999999,-99999);
          for(int i=0;i<numOldSpots;i++){
            SuperSpot theSpot=oldSpots.get(i);
            double xCoordFraction=(theSpot.getCentroidX()-oldReferenceSpots[0].getCentroidX())/oldLineWidth;
            Line2D.Double theReallyLongLine=new Line2D.Double(xCoordFraction, -99999999, xCoordFraction, 99999999);
            
            //now we need to find where this point falls on the newEnc pattern
            Line2D.Double intersectionLine=null;
            int lineIterator=0;
            while((intersectionLine==null)&&(lineIterator<numNewLines)){
              //System.out.println("     Comparing line: ["+newLines[lineIterator].getX1()+","+newLines[lineIterator].getY1()+","+newLines[lineIterator].getX2()+","+newLines[lineIterator].getY2()+"]"+" to ["+theReallyLongLine.getX1()+","+theReallyLongLine.getY1()+","+theReallyLongLine.getX2()+","+theReallyLongLine.getY2()+"]");
              if(newLines[lineIterator].intersectsLine(theReallyLongLine)){
                intersectionLine=newLines[lineIterator];
                //System.out.println("!!!!!!FOUND the INTERSECT!!!!!!");
              }
              lineIterator++;
            }
            try{
              double slope=(intersectionLine.getY2()-intersectionLine.getY1())/(intersectionLine.getX2()-intersectionLine.getX1());
              double yCoord=intersectionLine.getY1()+(xCoordFraction-intersectionLine.getX1())*slope;
              
              SuperSpot newSpot=new SuperSpot(xCoordFraction,yCoord);
              
              //NEW_VALUES[i]=yCoord;
              //theEnc2DataCollectionEvents.addPoint(new DataCollectionEvent(i,yCoord));
              
              if(newSpot.getCentroidX()>newRightmostSpot.getCentroidX()){newRightmostSpot=newSpot;}
              if(newSpot.getCentroidX()<newLeftmostSpot.getCentroidX()){newLeftmostSpot=newSpot;}
            
              
              double[] myDub={0,yCoord,i};
              //System.out.println("Swale Dist2: "+yCoord);
              
              theEnc2DataCollectionEvents.add( new org.ecocean.timeseries.core.Point( myDub ) );
              
              
            }
        catch(Exception e){
            System.out.println("Hit an exception with spot: ["+theSpot.getCentroidX()+","+theSpot.getCentroidY()+"]");
          double[] myDub={0,0,i};
          theEnc2DataCollectionEvents.add( new org.ecocean.timeseries.core.Point( myDub ) );
          
        }
            
            
          }
          
        //SwaleSimilarityComputor swaleComp=new SwaleSimilarityComputor(penalty, reward, epsilon);
          EuclideanOperator eucOp=new EuclideanOperator();
          
          //DistanceOperator op  = (DistanceOperator)Class.forName("core.distance.SwaleOperator").newInstance();
          Trajectory oldSeries=new Trajectory(0,theEncDataCollectionEvents,eucOp);
          Trajectory newSeries=new Trajectory(1,theEnc2DataCollectionEvents,eucOp);
          
          
          //java.lang.Double matchResult=new java.lang.Double(swaleComp.similarity(oldSerie, newSerie));
          java.lang.Double matchResult=new java.lang.Double(eucOp.computeDistance(oldSeries, newSeries));
          
          
          //System.out.println("");  
          //System.out.println("   !!!!I found an MSM match score of: "+matchResult);  
          //System.out.println("");  
            return matchResult;
          }
          catch(Exception e){
            e.printStackTrace();
          }
          return null;
      
    }
    
    
    
}

