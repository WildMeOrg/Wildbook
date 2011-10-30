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
  //ISO formatted date (e.g., 2009-10-23)
  private String processingDateStart;
//ISO formatted date (e.g., 2009-10-23)
  private String processingDateEnd;
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
  
  public String getProcessingLabname(){return processingLabName;}
  public void setProcessingLabName(String newString){this.processingLabName=newString;}
  
  public String getProcessingLabTaskID(){return processingLabTaskID;}
  public void setProcessingLabTaskID(String newString){this.processingLabTaskID=newString;}
  
  
  public String getProcessingLabContactName(){return processingLabContactName;}
  public void setProcessingLabContactName(String newString){this.processingLabContactName=newString;}
  
  public String getProcessingLabContactDetails(){return processingLabContactDetails;}
  public void setProcessingLabContactDetails(String newString){this.processingLabContactDetails=newString;}
  
  public String getProcessingDateStart(){return processingDateStart;}
  public void setProcessingDateStart(String newString){this.processingDateStart=newString;}
  
  public String getProcessingDateEnd(){return processingDateEnd;}
  public void setProcessingDateEnd(String newString){this.processingDateEnd=newString;}
  
  public String getCorrespondingEncounterNumber(){return correspondingEncounterNumber;}
  public String getSampleID(){return sampleID;}

}
