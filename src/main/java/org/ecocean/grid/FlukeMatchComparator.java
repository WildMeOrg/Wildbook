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
import weka.classifiers.bayes.BayesNet;


public class FlukeMatchComparator implements Comparator<org.ecocean.grid.MatchObject> {
  
  private HttpServletRequest request;
  private Instances myInstances=null;
  private AdaBoostM1 booster = null;
  private BayesNet bayesBooster = null;
  
  
  public FlukeMatchComparator(HttpServletRequest request,AdaBoostM1 booster, BayesNet bayesBooster,Instances myInstances){
    this.request=request;
    this.myInstances=myInstances;
    this.booster=booster;
    this.bayesBooster=bayesBooster;
    
    System.out.println("      ...Instantiate FlukeMatchComparator...");
  }

  public int compare(MatchObject a, MatchObject b) {
    
    double a1_adjustedValue=0;
    double b1_adjustedValue=0;

      
      MatchObject a1 = a;
      MatchObject b1 = b;
      
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
          a1_adjustedValue=bayesBooster.distributionForInstance(a1Example)[0];
          b1_adjustedValue=bayesBooster.distributionForInstance(b1Example)[0];
        }
        catch(Exception e){e.printStackTrace();}
      
      
      
      if(a1_adjustedValue > b1_adjustedValue){return -1;}
      else if(a1_adjustedValue < b1_adjustedValue){return 1;}
      else if(a1_adjustedValue==b1_adjustedValue){return 0;}
      
      return 0;

          
  }
}