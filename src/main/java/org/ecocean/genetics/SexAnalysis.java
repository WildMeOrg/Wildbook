package org.ecocean.genetics;


public class SexAnalysis extends GeneticAnalysis{
  private static final long serialVersionUID = -4515952757347048094L;

  private static String type="SexAnalysis";
  private String sex;

  //Empty constructor required for JDO.
  //DO NOT USE
  public SexAnalysis() {}
  
  public SexAnalysis(String analysisID, String sex, String correspondingEncounterNumber, String sampleID) {
    super(analysisID, type, correspondingEncounterNumber, sampleID);
    this.sex=sex;
  }
  
  public String getSex(){return sex.trim();}
  public void setSex(String newHaplo){this.sex=newHaplo;};


  
  public String getHTMLString(){
    String paramValues=super.getHTMLString();
    if((this.getSex()!=null)&&(!this.getSex().equals(""))){paramValues+="     Sex: "+this.getSex()+"<br />";}
  return paramValues; 
  }
  
  public String getSuperHTMLString(){
    String paramValues=super.getHTMLString();
    return paramValues; 
  }
  
  
}
