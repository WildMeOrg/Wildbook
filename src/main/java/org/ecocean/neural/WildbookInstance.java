package org.ecocean.neural;

import weka.core.DenseInstance;
import org.ecocean.grid.MatchObject;

public class WildbookInstance {
  
  MatchObject mo;
  weka.core.DenseInstance instance;
  
  public WildbookInstance(weka.core.DenseInstance instance){
    this.instance=instance;
  }
  
  public int compareTo(WildbookInstance wild){
    return 0;
  }
  
  public void setMatchObject(MatchObject mo){this.mo=mo;}
  public MatchObject getMatchObject(){return mo;}
  public DenseInstance getInstance(){return instance;}
  public void setDenseInstance(DenseInstance instance){this.instance=instance;}

}
