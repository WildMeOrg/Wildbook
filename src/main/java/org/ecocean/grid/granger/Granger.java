package org.ecocean.grid.granger;

import java.io.*;
import org.ecocean.grid.*;
import org.ecocean.SuperSpot;
import java.awt.geom.Line2D;
import java.awt.geom.Line2D.Double;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;

import org.apache.commons.math.MathException;
import org.apache.commons.math.distribution.FDistribution;
import org.apache.commons.math.distribution.FDistributionImpl;
import org.apache.commons.math.stat.regression.OLSMultipleLinearRegression;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;


//sourced from: https://code.google.com/p/jquant/source/browse/trunk/src/ru/algorithmist/jquant/math/GrangerTest.java?r=8
public class Granger {

  /**
   * Returns p-value for Granger causality test.
   *
   * @param y - predictable variable
   * @param x - predictor
   * @param L - lag, should be 1 or greater.
   * @return p-value of Granger causality
   */
  public static double granger(double[] y, double[] x, int L){
      OLSMultipleLinearRegression h0 = new OLSMultipleLinearRegression();
      OLSMultipleLinearRegression h1 = new OLSMultipleLinearRegression();

      double[][] laggedY = createLaggedSide(L, y);

      double[][] laggedXY = createLaggedSide(L, x, y);

      int n = laggedY.length;

      h0.newSampleData(strip(L, y), laggedY);
      h1.newSampleData(strip(L, y), laggedXY);

      double rs0[] = h0.estimateResiduals();
      double rs1[] = h1.estimateResiduals();


      double RSS0 = sqrSum(rs0);
      double RSS1 = sqrSum(rs1);

      double ftest = ((RSS0 - RSS1)/L) / (RSS1 / ( n - 2*L - 1));

      System.out.println(RSS0 + " " + RSS1);
      System.out.println("F-test " + ftest);

      FDistribution fDist = new FDistributionImpl(L, n-2*L-1);
      try {
          double pValue = 1.0 - fDist.cumulativeProbability(ftest);
          System.out.println("P-value " + pValue);
          return  pValue;
      } catch (MathException e) {
          throw new RuntimeException(e);
      }

  }


  private static double[][] createLaggedSide(int L, double[]... a) {
      int n = a[0].length - L;
      double[][] res = new double[n][L*a.length+1];
      for(int i=0; i<a.length; i++){
          double[] ai = a[i];
          for(int l=0; l<L; l++){
              for(int j=0; j<n; j++){
                  res[j][i*L+l] = ai[l+j];
              }
          }
      }
      for(int i=0; i<n; i++){
          res[i][L*a.length] = 1;
      }
      return res;
  }

  public static double sqrSum(double[] a){
      double res = 0;
      for(double v : a){
          res+=v*v;
      }
      return res;
  }


   public static double[] strip(int l, double[] a){

      double[] res = new double[a.length-l];
      System.arraycopy(a, l, res, 0, res.length);
      return res;
  }







//Make this work with Wildbook
public static java.lang.Double getMSMDistance(EncounterLite oldEnc,EncounterLite newEnc){
  
  //steps to make this work for flukes
  //1. normalize fluke width 0 to 1 - call this x axis
  //2. make Line2D from tip to tip points for both patterns - oldLine and newLine
  //3. measure distance down from tip line - call this y axis
  //4. use oldEnc points as X[]
  //5. form every pair of lines between two points in newEnc and create and find Y distance to that point from its corresponding x coordinate from oldEnc's points at proportion of width 
  
    try{
      
      ArrayList<SuperSpot> oldSpots=oldEnc.getSpots();
      oldSpots.addAll(oldEnc.getRightSpots());
        Collections.sort(oldSpots, new XComparator());
        
        //let's prefilter old spots for outlies outside the bounds
        for(int i=0;i<oldSpots.size();i++){
          SuperSpot theSpot=oldSpots.get(i);
          if(theSpot.getCentroidX()<=oldEnc.getLeftReferenceSpots()[0].getCentroidX()){
            oldSpots.remove(i);
            i--;
          }
          if(theSpot.getCentroidX()>=oldEnc.getLeftReferenceSpots()[2].getCentroidX()){
            oldSpots.remove(i);
            i--;
          }
        }
        int numOldSpots=oldSpots.size();
        
        //initialize our output arrays
        double[] OLD_VALUES=new double[numOldSpots];
        double[] NEW_VALUES=new double[numOldSpots];
      
      SuperSpot[] oldReferenceSpots=oldEnc.getLeftReferenceSpots();
      Line2D.Double oldLine=new Line2D.Double(oldReferenceSpots[0].getCentroidX(), oldReferenceSpots[0].getCentroidY(), oldReferenceSpots[2].getCentroidX(), oldReferenceSpots[2].getCentroidY());
      double oldLineWidth=Math.abs(oldReferenceSpots[2].getCentroidX()-oldReferenceSpots[0].getCentroidX());
      
      SuperSpot[] newReferenceSpots=newEnc.getLeftReferenceSpots();
      Line2D.Double newLine=new Line2D.Double(newReferenceSpots[0].getCentroidX(), newReferenceSpots[0].getCentroidY(), newReferenceSpots[2].getCentroidX(), newReferenceSpots[2].getCentroidY());
      double newLineWidth=Math.abs(newReferenceSpots[2].getCentroidX()-newReferenceSpots[0].getCentroidX());
      
      
      //first populate OLD_VALUES - easy
      
      for(int i=0;i<numOldSpots;i++){
        SuperSpot theSpot=oldSpots.get(i);
        Point2D.Double thePoint=new Point2D.Double(theSpot.getCentroidX(),theSpot.getCentroidY());
        OLD_VALUES[i]=oldLine.ptLineDist(thePoint)/oldLineWidth;
        
      }
      
      
      //second populate NEW_VALUES - trickier
      
      //create an array of lines made from all point pairs in newEnc
      
      
      ArrayList<SuperSpot> newSpots=newEnc.getSpots();
      newSpots.addAll(newEnc.getRightSpots());
      int numNewEncSpots=newSpots.size();
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
          
          //Point2D.Double thePoint=new Point2D.Double(xCoordFraction,yCoord);
          
          NEW_VALUES[i]=yCoord;
        }
    catch(Exception e){
        //System.out.println("Hit an exception with spot: ["+theSpot.getCentroidX()+","+theSpot.getCentroidY()+"]");
        NEW_VALUES[i]=0;
    }
        
        
      }
      
      java.lang.Double matchResult=new java.lang.Double(granger(OLD_VALUES, NEW_VALUES, 1));
      System.out.println("");  
      System.out.println("   !!!!I found an MSM match score of: "+matchResult);  
      System.out.println("");  
        return matchResult;
      }
      catch(Exception e){
        e.printStackTrace();
      }
      return null;
  
}

}
