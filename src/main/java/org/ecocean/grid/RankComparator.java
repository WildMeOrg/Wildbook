package org.ecocean.grid;

import java.util.Comparator;
import org.ecocean.neural.WildbookInstance;


public class RankComparator implements Comparator {

  String algorithm;
  
  public RankComparator(String algorithm){
    this.algorithm=algorithm;
  }

    @Override
    public int compare(Object e1, Object e2) {
        
      MatchObject a1=((WildbookInstance)e1).getMatchObject();
      MatchObject b1=((WildbookInstance)e2).getMatchObject();
      

      
    //intersection
      if((algorithm.equals("intersection"))||(algorithm.equals("swale"))){   
        
        double a1_adjustedValue = 0;
        double b1_adjustedValue = 0;
        
        if(algorithm.equals("intersection")){
          a1_adjustedValue=a1.getIntersectionCount();
          b1_adjustedValue=b1.getIntersectionCount();
        }
        else if(algorithm.equals("swale")){
          a1_adjustedValue=a1.getSwaleValue();
          b1_adjustedValue=b1.getSwaleValue();
        }
        
        if (a1_adjustedValue > b1_adjustedValue) {
          return -1;
        } 
        else if (a1_adjustedValue == b1_adjustedValue) {
          return 0;
        } 
        else {
          return 1;
        }
      }
      else if((algorithm.equals("euclidean"))||(algorithm.equals("i3s"))||(algorithm.equals("MSM"))||(algorithm.equals("fastDTW"))||(algorithm.equals("proportion"))){
        double a1_adjustedValue = 0;
        double b1_adjustedValue = 0;
        
        if(algorithm.equals("euclidean")){
          a1_adjustedValue=a1.getEuclideanDistanceValue().doubleValue();
          b1_adjustedValue=b1.getEuclideanDistanceValue().doubleValue();
        }
        else if(algorithm.equals("i3s")){
          a1_adjustedValue=a1.getI3SMatchValue();
          b1_adjustedValue=b1.getI3SMatchValue();
        }
        else if(algorithm.equals("MSM")){
          a1_adjustedValue=a1.getMSMValue().doubleValue();
          b1_adjustedValue=b1.getMSMValue().doubleValue();
        }
        else if(algorithm.equals("fastDTW")){
          a1_adjustedValue=a1.getLeftFastDTWResult().doubleValue();
          b1_adjustedValue=b1.getLeftFastDTWResult().doubleValue();
        }
        else if(algorithm.equals("proportion")){
          a1_adjustedValue=a1.getProportionValue().doubleValue();
          b1_adjustedValue=b1.getProportionValue().doubleValue();
        }
        
        if (a1_adjustedValue > b1_adjustedValue) {
          return 1;
        } 
        else if (a1_adjustedValue < b1_adjustedValue) {
          return -1;
        } 
        else {
          return 0;
        }
      }
      
      
      return 0;
      
    }
}
  

