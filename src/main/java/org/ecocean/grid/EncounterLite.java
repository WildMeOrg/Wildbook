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

import com.reijns.I3S.Affine;
import com.reijns.I3S.Compare;
import com.reijns.I3S.FingerPrint;
import com.reijns.I3S.Point2D;
import org.ecocean.Encounter;
import org.ecocean.Spot;
import org.ecocean.SuperSpot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;

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

  public EncounterLite() {
  }

  public EncounterLite(Encounter enc) {
    this.date = enc.getDate();
    this.encounterNumber = enc.getEncounterNumber();
    if(enc.getIndividualID()!=null){
      this.belongsToMarkedIndividual = enc.getIndividualID();
    }
    if(enc.getSex()!=null){
      this.sex = enc.getSex();
    }
    //this.size = enc.getSize();
    /*if(enc.getSpots()!=null) {
        this.spots=new superSpot[enc.getSpots().length];
    }
    if(enc.getRightSpots()!=null) {
        this.rightSpots=new superSpot[enc.getRightSpots().length];
    }*/


    if ((enc.getLeftReferenceSpots() != null) && (enc.getLeftReferenceSpots().size() == 3)) {
      //this.leftReferenceSpots=new superSpot[3];
      //superSpot[] existingRefs=enc.getLeftReferenceSpots();
      //leftReferenceSpots[0]=new superSpot(existingRefs[0].getTheSpot());
      //leftReferenceSpots[1]=new superSpot(existingRefs[1].getTheSpot());
      //System.out.println("I found left reference spots!");
      //leftReferenceSpots[2]=new superSpot(existingRefs[2].getTheSpot());
    }
    if ((enc.getRightReferenceSpots() != null) && (enc.getRightReferenceSpots().size() == 3)) {
      //this.rightReferenceSpots=new superSpot[3];
      //superSpot[] existingRefs=enc.getRightReferenceSpots();
      //leftReferenceSpots[0]=new superSpot(existingRefs[0].getTheSpot());
      //leftReferenceSpots[1]=new superSpot(existingRefs[1].getTheSpot());
      //leftReferenceSpots[2]=new superSpot(existingRefs[2].getTheSpot());
      //System.out.println("I found right reference spots!");
    }

    //get spots
    if (enc.getSpots() != null) {

      processLeftSpots(enc.getSpots());

      if (enc.getLeftReferenceSpots() != null) {

        processLeftReferenceSpots(enc.getLeftReferenceSpots());
      }


    }

    //get right spots
    if (enc.getRightSpots() != null) {

      processRightSpots(enc.getRightSpots());
      if (enc.getRightReferenceSpots() != null) {

        processRightReferenceSpots(enc.getRightReferenceSpots());
      }


    }

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

  public MatchObject getPointsForBestMatch(SuperSpot[] newspotsTemp, double epsilon, double R, double Sizelim, double maxTriangleRotation, double C, boolean secondRun, boolean rightScan) {
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

        //old sizelim computation
        if (tempTriangle.D13 / newSpan >= Sizelim) {

          //System.out.println("Removing large triangle: "+tempTriangle.D13+" "+newSpan+" "+tempTriangle.D13/newSpan);
          newTriangles.remove(i);
          i--;

        }
      }
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
        //System.out.println("Almost standard deviation is: "+stdDeviationLogM);
        //System.out.println("LogM list size minus one is: "+(logM.size()-1));
        //System.out.println("The real std dev. should be: "+Math.pow((stdDeviationLogM/(logM.size()-1)), 0.5));


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

      //System.out.println("Going into scoring with "+VmatchesA.size()+" matching triangles.");
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

      //System.out.print("     Scoring going into second pass: ");
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

      //System.out.println("secondNewSpots is :"+secondNewSpots.length);
      //System.out.println("secondBaseSpots is :"+secondBaseSpots.length);

      //now run Groth's algorithm again if there are enough spots. if not, exit as this is not a match.
      VertexPointMatch[] secondPassSpots = scoredSpots;
      if ((secondNewSpots.length > 3) && (secondBaseSpots.length > 3)) {

        //run recursion on these spots now
        secondPassSpots = secondGrothPass(secondNewSpots, secondBaseSpots, epsilon, R, Sizelim, maxTriangleRotation, C);
        if (secondPassSpots.length < 3) {
          //System.out.println("Exiting after the second pass because the returned number of spots was less than three. "+scoredSpots.length+"-"+(scoredSpots.length-secondPassSpots.length)+"="+secondPassSpots.length);
          return (new MatchObject(belongsToMarkedIndividual, 0, 0, encounterNumber));
        }
        //System.out.println("     The second pass cut out "+(scoredSpots.length-secondPassSpots.length)+" spots.");
      } else {
        //System.out.println("Exiting processing because there were less than three spots going into the second filter pass. This is not a match.");
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

  private VertexPointMatch[] secondGrothPass(SuperSpot[] secondNewSpots, SuperSpot[] secondBaseSpots, double epsilon, double R, double Sizelim, double maxTriangleRotation, double C) {
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
   * This method allows us to use the I3S match algorithm as well.
   */
  public I3SMatchObject i3sScan(EncounterLite newEnc, boolean scanRight) {

    //superSpot objects are my equivalent in my DB of Point2D
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

    //affine transform for scale adjustment
    doAffine(newPrint);
    doAffine(thisPrint);

    Compare wsCompare = new Compare(thisPrint);
    FingerPrint[] fpBest = new FingerPrint[1];

    TreeMap hm = new TreeMap();

    boolean successfulCompare = wsCompare.find(newPrint, fpBest, 1, true, hm);
    //boolean successfulCompare=wsCompare.compareTwo(newPrint, thisPrint, hm,true);


    if (successfulCompare) {
      //System.out.println("Successful compare!");
    } else {
      System.out.println("Error in compare!");
    }


    //now return an I3S match object
    return (new I3SMatchObject(belongsToMarkedIndividual, fpBest[0].getScore(), encounterNumber, sex, getDate(), size, hm, 0));
  }

  private void doAffine(FingerPrint fp) {
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
}
