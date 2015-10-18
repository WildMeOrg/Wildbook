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

import java.util.Comparator;


import weka.classifiers.meta.AdaBoostM1;
import weka.core.Instance;
import weka.core.Instances;

import javax.servlet.http.HttpServletRequest;


//train weka
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instances;
import weka.core.Instance;
import weka.classifiers.meta.AdaBoostM1;
import weka.classifiers.Evaluation;


public class FlukeMatchComparator implements Comparator<org.ecocean.grid.MatchObject> {
  
  private HttpServletRequest request;
  private Instances myInstances=null;
  private AdaBoostM1 booster = null;
  
  
  public FlukeMatchComparator(HttpServletRequest request,AdaBoostM1 booster,Instances myInstances){
    this.request=request;
    this.myInstances=myInstances;
    this.booster=booster;
    
    System.out.println("      ...Instantiate FlukeMatchComparator...");
  }

  public int compare(MatchObject a, MatchObject b) {
    
    double a1_adjustedValue=0;
    double b1_adjustedValue=0;

      
      MatchObject a1 = (MatchObject) a;
      MatchObject b1 = (MatchObject) b;
      
      Instance a1Example = new Instance(7);
      Instance b1Example = new Instance(7);
      
        a1Example.setDataset(myInstances);
        a1Example.setValue(0, a1.getIntersectionCount());
        a1Example.setValue(1, a1.getLeftFastDTWResult().doubleValue());
        a1Example.setValue(2,  a1.getI3SMatchValue());
        a1Example.setValue(3, (new Double(a1.getProportionValue()).doubleValue()));
        a1Example.setValue(4, (new Double(a1.getMSMValue()).doubleValue()));
        a1Example.setValue(5, (new Double(a1.getSwaleValue()).doubleValue()));
        a1Example.setValue(6, (new Double(a1.getDateDiff()).doubleValue()));
        
        
        b1Example.setDataset(myInstances);
        b1Example.setValue(0, b1.getIntersectionCount());
        b1Example.setValue(1, b1.getLeftFastDTWResult().doubleValue());
        b1Example.setValue(2,  b1.getI3SMatchValue());
        b1Example.setValue(3, (new Double(b1.getProportionValue()).doubleValue()));
        b1Example.setValue(4, (new Double(b1.getMSMValue()).doubleValue()));
        b1Example.setValue(5, (new Double(b1.getSwaleValue()).doubleValue()));
        b1Example.setValue(6, (new Double(b1.getDateDiff()).doubleValue()));
      
        try{
        a1_adjustedValue=booster.distributionForInstance(a1Example)[0];
        b1_adjustedValue=booster.distributionForInstance(b1Example)[0];
        }
        catch(Exception e){e.printStackTrace();}
      
      
      if(a1_adjustedValue == b1_adjustedValue){
            //if a tie, sort by how well they beat each other
            int aTieScore=0;
            int bTieScore=0;
            
            //I3S comparison
            if(a1.getI3SMatchValue()<b1.getI3SMatchValue()){aTieScore++;}
            else if(b1.getI3SMatchValue()<a1.getI3SMatchValue()){bTieScore++;}
            //Intersection comparison
            if(a1.getIntersectionCount()>b1.getIntersectionCount()){aTieScore++;}
            else if(b1.getIntersectionCount()>a1.getIntersectionCount()){bTieScore++;}
            //FastDTW comparison
            if(a1.getLeftFastDTWResult()<b1.getLeftFastDTWResult()){aTieScore++;}
            else if(b1.getLeftFastDTWResult()<a1.getLeftFastDTWResult()){bTieScore++;}
            //proportion comparison
            if(a1.getProportionValue()<b1.getProportionValue()){aTieScore++;}
            else if(b1.getProportionValue()<a1.getProportionValue()){bTieScore++;}
            if(a1.getMSMValue()<b1.getMSMValue()){aTieScore++;}
            else if(b1.getMSMValue()<a1.getMSMValue()){bTieScore++;}
            if(a1.getSwaleValue()>b1.getSwaleValue()){aTieScore++;}
            else if(b1.getSwaleValue()>a1.getSwaleValue()){bTieScore++;}
            
            if(a1.getDateDiff()<b1.getDateDiff()){aTieScore++;}
            else if(b1.getDateDiff()<a1.getDateDiff()){bTieScore++;}
           
            
            if(aTieScore>bTieScore){
              System.out.println("     Breaking a tie in FlukeMatchComparator "+a1.individualName+" with AdaBoost score "+a1_adjustedValue+" effectively has a better score than "+b1.individualName+ " because aTieScore "+aTieScore+" is greater than "+bTieScore);
              
              return -1;
            }
            else if(aTieScore<bTieScore){
              System.out.println("     Breaking a tie in FlukeMatchComparator "+b1.individualName+" with AdaBoost score "+b1_adjustedValue+" effectively has a better score than "+a1.individualName+ " because bTieScore "+bTieScore+" is greater than "+aTieScore);
              
              return 1;
            }
            else{
            //damn! tied again. Let's look at percentage better
              return 0;
            }
      }    
      else if(a1_adjustedValue > b1_adjustedValue){return -1;}
      else{return 1;}

          
  }
}