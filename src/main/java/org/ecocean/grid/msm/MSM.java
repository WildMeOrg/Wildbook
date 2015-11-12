package org.ecocean.grid.msm;

import java.io.*;
import org.ecocean.grid.*;
import org.ecocean.SuperSpot;
import java.awt.geom.Line2D;
import java.awt.geom.Line2D.Double;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;

public class MSM {

  /* 
   * SOurce paper: http://vlm1.uta.edu/~athitsos/publications/stefan_tkde2012_preprint.pdf
   * 
  input:
      X, Y - time series
  output: 
      MSM distance between X and Y. Note that this distance is computed
  using a hard wired value of c (cost of Split/Merge). Change this value if 
  you need to.  
  */
  public static double MSM_Distance(double[] X, double[] Y){      
    
    int m, n, i, j;
    
    m = X.length;
    n = Y.length;
      
    double Cost[][] = new double[m][n];
    
    // Initialization
    Cost[0][0] = Math.abs(X[0] - Y[0]);
    
    for (i = 1; i< m; i++) {
      Cost[i][0] = Cost[i-1][0] + C(X[i], X[i-1], Y[0]);
    }
        
    for (j = 1; j < n; j++) {
      Cost[0][j] = Cost[0][j-1] + C(Y[j], X[0], Y[j-1]);
    }
    
    // Main Loop
    for( i = 1; i < m; i++){
      for ( j = 1; j < n; j++){
        double d1,d2, d3;
        d1 = Cost[i-1][j-1] + Math.abs( X[i] - Y[j] );
        d2 = Cost[i-1][j] + C( X[i], X[i-1], Y[j]);
        d3 = Cost[i][j-1] + C( Y[j], X[i], Y[j-1]);
        Cost[i][j] = Math.min( d1, Math.min(d2,d3) );
      }
    }
    
    // Output   
    return Cost[m-1][n-1];
  }


  public static double C( double new_point, double x, double y){
    
    // c - cost of Split/Merge operation. Change this value to what is more
    // appropriate for your data.
    double c = 0.1;
    
    double dist = 0;
    
    if ( ( (x <= new_point) && (new_point <= y) ) || 
       ( (y <= new_point) && (new_point <= x) ) ) {
      dist = c;
    }
    else{
      dist = c + Math.min( Math.abs(new_point - x) , Math.abs(new_point - y) );
    }

    return dist;    
  }
  
  
  // Read an array containing the time series.
  // Used for testing the MSM distance with some simple inputs.
  public static double[] ReadTimeSeries(){
    
    int m, i;
      BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
      String str;
      StreamTokenizer st;
      double X[] = new double[0];
               
    try{
        System.out.print("\n length of the time series: ");
      m = Integer.parseInt(br.readLine());      
      X = new double[m];      
      System.out.print(" values of the time series (press Enter at the end, NOT after each number): ");
      str = br.readLine();
      st = new StreamTokenizer( new StringReader( str ) );
      for (i = 0; i < m; i++){      
        st.nextToken();
        X[i] = st.nval;
        // System.out.print(X[i] + "  "); // uncomment this line to verify
                        //that the data was read in correctly
      }     
    }
    catch (IOException ioe){
      System.out.println("Error reading the length of the time series.");   
    }   
    
    return X; 
  }


  /**
   * @param args
   */
  public static void main(String[] args) {
      
    int m, n, i;
      BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
      String str;
      StreamTokenizer st;
    
    // read 2 time series
    System.out.println("Please introduce two time series ( first the length and then the elements).");
    
      System.out.print("\nthe first time series:");
      double X[] = ReadTimeSeries();
      System.out.print("\nthe second time series:");
      double Y[] = ReadTimeSeries();
      double distance = MSM_Distance(X,Y);
      System.out.println("\nThe MSM distance between X and Y is: D(X,Y) = " + distance);
      
  }




//Make this work with Wildbook
public static java.lang.Double getMSMDistance(EncounterLite theEnc,EncounterLite theEnc2){
  
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
          
          for(int i=0;i<oldSpots.size();i++){
            SuperSpot theSpot=oldSpots.get(i);
            if(theSpot.getCentroidX()<=theEnc.getLeftReferenceSpots()[0].getCentroidX()){
              oldSpots.remove(i);
              i--;
            }
            if(theSpot.getCentroidX()>=theEnc.getLeftReferenceSpots()[2].getCentroidX()){
              oldSpots.remove(i);
              i--;
            }
          }
          
          
          int numOldSpots=oldSpots.size();
          double[] OLD_VALUES=new double[numOldSpots];
          double[] NEW_VALUES=new double[numOldSpots];
          
          SuperSpot[] oldReferenceSpots=theEnc.getLeftReferenceSpots();
          Line2D.Double oldLine=new Line2D.Double(oldReferenceSpots[0].getCentroidX(), oldReferenceSpots[0].getCentroidY(), oldReferenceSpots[2].getCentroidX(), oldReferenceSpots[2].getCentroidY());
          double oldLineWidth=Math.abs(oldReferenceSpots[2].getCentroidX()-oldReferenceSpots[0].getCentroidX());
          //System.out.println(" Old line width is: "+oldLineWidth);
          
          SuperSpot[] newReferenceSpots=theEnc2.getLeftReferenceSpots();
          Line2D.Double newLine=new Line2D.Double(newReferenceSpots[0].getCentroidX(), newReferenceSpots[0].getCentroidY(), newReferenceSpots[2].getCentroidX(), newReferenceSpots[2].getCentroidY());
          double newLineWidth=Math.abs(newReferenceSpots[2].getCentroidX()-newReferenceSpots[0].getCentroidX());
          //System.out.println(" New line width is: "+newLineWidth);
          
          //first populate OLD_VALUES - easy
          
          for(int i=0;i<numOldSpots;i++){
            SuperSpot theSpot=oldSpots.get(i);
            java.awt.geom.Point2D.Double thePoint=new java.awt.geom.Point2D.Double(theSpot.getCentroidX(),theSpot.getCentroidY());
            OLD_VALUES[i]=oldLine.ptLineDist(thePoint)/oldLineWidth;
            
            
          }
          
          
          //second populate NEW_VALUES - trickier
          
          //create an array of lines made from all point pairs in theEnc2
          
          ArrayList<SuperSpot> newSpots=theEnc2.getSpots();
          if(theEnc2.getRightSpots()!=null){
            newSpots.addAll(theEnc2.getRightSpots());
          }
          Collections.sort(newSpots, new XComparator());
          int numtheEnc2Spots=newSpots.size();
          Line2D.Double[] newLines=new Line2D.Double[numtheEnc2Spots-1];
          for(int i=0;i<(numtheEnc2Spots-1);i++){
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
            //System.out.println("Iterating!");
            SuperSpot theSpot=oldSpots.get(i);
            double xCoordFraction=(theSpot.getCentroidX()-oldReferenceSpots[0].getCentroidX())/oldLineWidth;
            //System.out.println("Iterating xCoordFraction: "+xCoordFraction);
            Line2D.Double theReallyLongLine=new Line2D.Double(xCoordFraction, -99999999, xCoordFraction, 99999999);
            
            //now we need to find where this point falls on the theEnc2 pattern
            Line2D.Double intersectionLine=null;
            int lineIterator=0;
            while((lineIterator<numNewLines)){
              //System.out.println("     Comparing line: ["+newLines[lineIterator].getX1()+","+newLines[lineIterator].getY1()+","+newLines[lineIterator].getX2()+","+newLines[lineIterator].getY2()+"]"+" to ["+theReallyLongLine.getX1()+","+theReallyLongLine.getY1()+","+theReallyLongLine.getX2()+","+theReallyLongLine.getY2()+"]");
              if(newLines[lineIterator].intersectsLine(theReallyLongLine)){
                intersectionLine=newLines[lineIterator];
                //System.out.println("     !!!!!!FOUND the INTERSECT!!!!!!");
              }
              lineIterator++;
            }
            try{
              
              //System.out.println("     lineY1="+intersectionLine.getY1()+" and Y2="+intersectionLine.getY2());
              double slope=(intersectionLine.getY2()-intersectionLine.getY1())/(intersectionLine.getX2()-intersectionLine.getX1());
              double yCoord=intersectionLine.getY1()+(xCoordFraction-intersectionLine.getX1())*slope;
              if(yCoord>0){NEW_VALUES[i]=yCoord;}
              else{NEW_VALUES[i]=0;}
              
              //System.out.println("     ycoord "+yCoord+" at "+xCoordFraction+ " and slope is: "+slope);
            }
        catch(Exception e){
          //System.out.println("Hit an exception with spot: ["+theSpot.getCentroidX()+","+theSpot.getCentroidY()+"]");
          NEW_VALUES[i]=0;
        }

            
            
          }
          
          java.lang.Double matchResult=new java.lang.Double(MSM.MSM_Distance(OLD_VALUES, NEW_VALUES));
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
