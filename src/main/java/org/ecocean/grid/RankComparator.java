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
      
      if (a1 == b1) {
        return 0;
      }
      
      double a1_adjustedValue = 0;
      double b1_adjustedValue = 0;
      
     
        //positive scoring algorithms
        if(algorithm.equals("intersection")){
          a1_adjustedValue=a1.getIntersectionCount();
          b1_adjustedValue=b1.getIntersectionCount();
        }
        else if(algorithm.equals("swale")){
          a1_adjustedValue=a1.getSwaleValue();
          b1_adjustedValue=b1.getSwaleValue();
        }
        
        
        //cross thread the negative scoring alforithms
        if(algorithm.equals("euclidean")){
          a1_adjustedValue=b1.getEuclideanDistanceValue().doubleValue();
          b1_adjustedValue=a1.getEuclideanDistanceValue().doubleValue();
        }
        else if(algorithm.equals("i3s")){
          a1_adjustedValue=b1.getI3SMatchValue();
          b1_adjustedValue=a1.getI3SMatchValue();
        }
        else if(algorithm.equals("MSM")){
          a1_adjustedValue=b1.getMSMValue().doubleValue();
          b1_adjustedValue=a1.getMSMValue().doubleValue();
        }
        else if(algorithm.equals("fastDTW")){
          a1_adjustedValue=b1.getLeftFastDTWResult().doubleValue();
          b1_adjustedValue=a1.getLeftFastDTWResult().doubleValue();
        }
        else if(algorithm.equals("proportion")){
          a1_adjustedValue=b1.getProportionValue().doubleValue();
          b1_adjustedValue=a1.getProportionValue().doubleValue();
        }
        
        return Double.compare(a1_adjustedValue, b1_adjustedValue);
        
      
      
    }
}
  

