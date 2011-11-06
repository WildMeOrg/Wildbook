package org.ecocean.genetics;

import java.util.List;
import java.util.ArrayList;

public class Locus implements java.io.Serializable{
  
  /**
   * 
   */
  private static final long serialVersionUID = 5458817893355984588L;
  private List<String> alleles;
  private String name;
  
  //Empty JDO constructor
  //DO NOT USE
  public Locus(){}
  
  /**
   * Convenience constructor for diploid organisms.
   * @param allelle1
   * @param allelle2
   */
  public Locus(String name, String allelle1, String allelle2){
    alleles=new ArrayList<String>();
    alleles.add(allelle1);
    alleles.add(allelle2);
    this.name=name;
  }
  
  public Locus(String name, List<String> alleles){
    this.alleles=alleles;
    this.name=name;
  }
  
  public String getAllele(int num){
    try{
      if(num<alleles.size()){return alleles.get(num);}
    }
    catch(Exception e){
      System.out.println("Allele "+num+" is out of the array bounds for attributes alleles in Locus.class.");
    }
    return null;
  }
  
  public String getName(){return name;}
  public void setName(String newName){this.name=newName;}
  
}
