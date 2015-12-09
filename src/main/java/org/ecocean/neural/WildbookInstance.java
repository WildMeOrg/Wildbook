package org.ecocean.neural;

import weka.core.DenseInstance;

public class WildbookInstance extends DenseInstance implements Comparable<WildbookInstance> {
  
  public WildbookInstance(int numAttributes){super(numAttributes);}
  
  public int compareTo(WildbookInstance wild){
    return 0;
  }

}
