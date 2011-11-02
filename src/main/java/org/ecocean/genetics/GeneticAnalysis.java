package org.ecocean.genetics;

public abstract class GeneticAnalysis implements java.io.Serializable {


  private static final long serialVersionUID = 5845159302679998951L;
  
  //primary key and unique identifier
  private String analysisID;
  private String analysisType;
  private String correspondingEncounterNumber;
  
  //details about the processing lab making this analysis
  private String processingLabTaskID;
  private String processingLabName;
  private String processingLabContactName;
  private String processingLabContactDetails;
  private String sampleID;
  
  /**
   * Empty constructor required for JDO processing.
   * DO NOT USE
   */
  public GeneticAnalysis() {}
  
  public GeneticAnalysis(String analysisID, String analysisType, String correspondingEncounterNumber, String sampleID) {
    this.analysisID=analysisID;
    this.analysisType=analysisType;
    this.correspondingEncounterNumber=correspondingEncounterNumber;
    this.sampleID=sampleID;
  }
  
  public String getAnalysisID(){return analysisID;}
  
  public String getAnalysisType(){return analysisType;}
  public void setAnalysisType(String newType){this.analysisType=newType;}
  
  public String getProcessingLabName(){return processingLabName;}
  public void setProcessingLabName(String newString){this.processingLabName=newString;}
  
  public String getProcessingLabTaskID(){return processingLabTaskID;}
  public void setProcessingLabTaskID(String newString){this.processingLabTaskID=newString;}
  
  
  public String getProcessingLabContactName(){return processingLabContactName;}
  public void setProcessingLabContactName(String newString){this.processingLabContactName=newString;}
  
  public String getProcessingLabContactDetails(){return processingLabContactDetails;}
  public void setProcessingLabContactDetails(String newString){this.processingLabContactDetails=newString;}
  

  public String getCorrespondingEncounterNumber(){return correspondingEncounterNumber;}
  public String getSampleID(){return sampleID;}
  
  public String getHTMLString(){
    String paramValues="";
    if((this.getProcessingLabName()!=null)&&(!this.getProcessingLabName().equals(""))){paramValues+="     Processing lab name: "+this.getProcessingLabName()+"<br />";}
    if((this.getProcessingLabTaskID()!=null)&&(!this.getProcessingLabTaskID().equals(""))){paramValues+="     Processing lab task ID: "+this.getProcessingLabTaskID()+"<br />";}
    if((this.getProcessingLabContactName()!=null)&&(!this.getProcessingLabContactName().equals(""))){paramValues+="     Processing lab contact name: "+this.getProcessingLabContactName()+"<br />";}
    if((this.getProcessingLabContactDetails()!=null)&&(!this.getProcessingLabContactDetails().equals(""))){paramValues+="     Processing lab contact details: "+this.getProcessingLabContactDetails()+"<br />";}
    return paramValues; 
  }
  

}
