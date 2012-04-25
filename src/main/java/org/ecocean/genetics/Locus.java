package org.ecocean.genetics;

import java.util.List;
import java.util.ArrayList;

public class Locus implements java.io.Serializable{
  
  /**
   * 
   */
  private static final long serialVersionUID = 5458817893355984588L;
  private String name;
  private Integer allele0;
  private Integer allele1;
  private Integer allele2;
  private Integer allele3;
  
  //Empty JDO constructor
  //DO NOT USE
  public Locus(){}
  
  /**
   * Convenience constructor for diploid organisms.
   * @param allelle0
   * @param allelle1
   */
  public Locus(String name, Integer allele0, Integer allele1){

    this.allele0=allele0;
    this.allele1=allele1;
    this.name=name;
  }
  
  public Locus(String name, ArrayList<Integer> alleles){
    System.out.println("In ctor: "+alleles.toString());
    int size=alleles.size();
    if(size>0){this.allele0=alleles.get(0);System.out.println(alleles.get(0));}
    if(size>1){this.allele1=alleles.get(1);System.out.println(alleles.get(1));}
    if(size>2){this.allele2=alleles.get(2);System.out.println(alleles.get(2));}
    if(size>3){this.allele3=alleles.get(3);System.out.println(alleles.get(3));}
    this.name=name;
  }
  
  public Integer getAllele(int num){

    if((num==0)&&(allele0!=null)){return allele0;}
    else if((num==1)&&(allele1!=null)){return allele1;}
    else if((num==2)&&(allele2!=null)){return allele2;}
    else if((num==3)&&(allele3!=null)){return allele3;}
    return null;
  }
  
  public String getName(){return name;}
  public void setName(String newName){this.name=newName;}
  
  public String getHTMLString(){
   String returnString=name+":";
   if(allele0!=null){returnString+=" "+allele0.toString();}
   if(allele1!=null){returnString+=" "+allele1.toString();}
   if(allele2!=null){returnString+=" "+allele2.toString();}
   if(allele3!=null){returnString+=" "+allele3.toString();}
   return returnString;
  }
  
  public Integer getAllele0(){return allele0;}
  public Integer getAllele1(){return allele1;}
  public Integer getAllele2(){return allele2;}
  public Integer getAllele3(){return allele3;}
  
  public boolean hasAllele(Integer value){
    if((allele0!=null)&&(allele0.intValue()==value.intValue())){return true;}
    if((allele1!=null)&&(allele1.intValue()==value.intValue())){return true;}
    if((allele2!=null)&&(allele2.intValue()==value.intValue())){return true;}
    if((allele3!=null)&&(allele3.intValue()==value.intValue())){return true;}
    return false;
  }
  
  
}
