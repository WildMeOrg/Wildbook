package org.ecocean.genetics;

public class Locus implements java.io.Serializable{
  
  /**
   * 
   */
  private static final long serialVersionUID = 5458817893355984588L;
  private String allelle1;
  private String allelle2;
  
  
  //Empty JDO constructor
  //DO NOT USE
  public Locus(){}
  
  public Locus(String allelle1, String allelle2){
    this.allelle1=allelle1;
    this.allelle2=allelle2;
  }
  
  public String getAllelle1(){return allelle1;}
  public String getAllelle2(){return allelle2;}

}
