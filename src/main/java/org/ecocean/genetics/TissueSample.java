package org.ecocean.genetics;


import java.util.ArrayList;
import java.util.List;


import org.ecocean.DataCollectionEvent;
import javax.servlet.http.HttpServletRequest;


public class TissueSample extends DataCollectionEvent {


  private static final long serialVersionUID = -4918907304313880745L;
  private static String type="TissueSample";

  private String tissueType;
  private String preservationMethod;
  //e.g., SWFSCz ID
  private String storageLabID;
  private String sampleID;
  private String alternateSampleID;
  private List<GeneticAnalysis> analyses;


  /**
   * Empty constructor required for JDO persistence.
   * DO NOT USE
   */
  public TissueSample(){}
  
  /*
   * Required constructor for instance creation
   */
  public TissueSample(String correspondingEncounterNumber, String sampleID) {
    super(correspondingEncounterNumber, type);
    this.sampleID=sampleID;
    analyses=new ArrayList<GeneticAnalysis>();
  }
  
  public TissueSample(String correspondingEncounterNumber, String sampleID, HttpServletRequest request) {
    super(correspondingEncounterNumber, type, request);
    this.sampleID=sampleID;
    analyses=new ArrayList<GeneticAnalysis>();
  }
  
  public void addGeneticAnalysis(GeneticAnalysis ga){
    if(!analyses.contains(ga)){analyses.add(ga);}
  }
  
  public void removeGeneticAnalysis(GeneticAnalysis ga){
    if(analyses.contains(ga)){analyses.remove(ga);}
  }
  
  public void removeGeneticAnalysis(int ga){
    if(analyses.size()>ga){analyses.remove(ga);}
  }
  
  public List<GeneticAnalysis> getGeneticAnalyses(){return analyses;}

  public String getTissueType(){return tissueType;}
  public void setTissueType(String newType){this.tissueType=newType;}
  
  public String getPreservationMethod(){return preservationMethod;}
  public void setPreservationMethod(String newMethod){this.preservationMethod=newMethod;}
  
  public String getStorageLabID(){return storageLabID;}
  public void setStorageLabID(String newLab){this.storageLabID=newLab;}
  
  public String getSampleID(){return sampleID;}

  public String getAlternateSampleID(){return alternateSampleID;}
  public void setAlternateSampleID(String newID){this.alternateSampleID=newID;}

  public int getNumAnalyses(){return analyses.size();}
  
  public String getHTMLString(){
    String removedParameters="<br />";
    if(this.getAlternateSampleID()!=null){removedParameters+="     Alternate Sample ID: "+this.getAlternateSampleID()+"<br />";}
    if(this.getCollectionCode()!=null){removedParameters+="     Collection code: "+this.getCollectionCode()+"<br />";}
    if(this.getCollectionID()!=null){removedParameters+="     Collection ID: "+this.getCollectionID()+"<br />";}
    if(this.getDatasetID()!=null){removedParameters+="     Dataset ID: "+this.getDatasetID()+"<br />";}
    if(this.getDatasetName()!=null){removedParameters+="     Dataset name: "+this.getDatasetName()+"<br />";}
    if(this.getEventStartDate()!=null){removedParameters+="     Event start date: "+this.getEventStartDate()+"<br />";}
    if(this.getEventEndDate()!=null){removedParameters+="     Event end date: "+this.getEventEndDate()+"<br />";}
    if(this.getEventRemarks()!=null){removedParameters+="     Event remarks: "+this.getEventRemarks()+"<br />";}
    if(this.getFieldNotes()!=null){removedParameters+="     Field notes: "+this.getFieldNotes()+"<br />";}
    if(this.getFieldNumber()!=null){removedParameters+="     Field number: "+this.getFieldNumber()+"<br />";}
    if(this.getInstitutionCode()!=null){removedParameters+="     Institution code: "+this.getInstitutionCode()+"<br />";}
    if(this.getInstitutionID()!=null){removedParameters+="     Instituion ID: "+this.getInstitutionID()+"<br />";}
    if(this.getPreservationMethod()!=null){removedParameters+="     Preservation method: "+this.getPreservationMethod()+"<br />";}
    if(this.getSamplingEffort()!=null){removedParameters+="     Samplng effort: "+this.getSamplingEffort()+"<br />";}
    if(this.getSamplingProtocol()!=null){removedParameters+="     Sampling protocol: "+this.getSamplingProtocol()+"<br />";}
    if(this.getStorageLabID()!=null){removedParameters+="     Storage lab ID: "+this.getStorageLabID()+"<br />";}
    if(this.getTissueType()!=null){removedParameters+="     Tissue type: "+this.getTissueType()+"<br />";}
    return removedParameters; 
  }
  
}
