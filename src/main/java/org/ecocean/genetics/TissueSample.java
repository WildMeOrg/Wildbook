package org.ecocean.genetics;


import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;


import org.ecocean.DataCollectionEvent;
import org.ecocean.Measurement;
import org.ecocean.ShepherdProperties;
import org.ecocean.StringUtils;

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
  
  // Legal permit info for data gathering..
  private String permit;

  // State of the biopsy.
  private String state;
  

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
  
  public String getPermit() {
    return permit;
  }
  
  public void setPermit(String newPermit) {
    this.permit = newPermit;
  }
  
  public String getState() {
    return state;
  }
  
  public void setState(String newState) {
    this.state = newState;
  }

  public int getNumAnalyses(){return analyses.size();}

  public String getHTMLString(String langCode, String context) {
    Properties props = ShepherdProperties.getProperties("dataCollectionEvent.properties", langCode, context);
    StringBuilder sb = new StringBuilder();
    sb.append(super.getHTMLString());
    if (!StringUtils.isNullOrEmpty(this.getAlternateSampleID())) sb.append(MessageFormat.format(props.getProperty("alternateSampleId"), this.getAlternateSampleID())).append("<br />");
    if (!StringUtils.isNullOrEmpty(this.getTissueType())) sb.append(MessageFormat.format(props.getProperty("tissueType"), this.getTissueType())).append("<br />");
    if (!StringUtils.isNullOrEmpty(this.getPreservationMethod())) sb.append(MessageFormat.format(props.getProperty("preservationMethod"), this.getPreservationMethod())).append("<br />");
    if (!StringUtils.isNullOrEmpty(this.getStorageLabID())) sb.append(MessageFormat.format(props.getProperty("storageLabId"), this.getStorageLabID())).append("<br />");
    return sb.toString();
  }

  public String getHTMLString() {
    return getHTMLString("en", "context0");
  }

  public boolean hasMeasurements(){
    if((analyses!=null)&&(analyses.size()>0)){
      int numMeasurements=analyses.size();
      for(int i=0;i<numMeasurements;i++){
        GeneticAnalysis m=analyses.get(i);
        if(m.getAnalysisType().equals("BiologicalMeasurement")){
          BiologicalMeasurement f=(BiologicalMeasurement)m;
          if(f.getValue()!=null){return true;}
        }
      }
    }
    return false;
  }

  public boolean hasMeasurement(String measurementType){
    if((analyses!=null)&&(analyses.size()>0)){
      int numMeasurements=analyses.size();
      for(int i=0;i<numMeasurements;i++){
        GeneticAnalysis m=analyses.get(i);
        if(m.getAnalysisType().equals("BiologicalMeasurement")){
          BiologicalMeasurement f=(BiologicalMeasurement)m;
          if((f.getMeasurementType().equals(measurementType))&&(f.getValue()!=null)){return true;}
        }
      }
    }
    return false;
  }

  public List<BiologicalMeasurement> getBiologicalMeasurements(){
    ArrayList<BiologicalMeasurement> measures=new ArrayList<BiologicalMeasurement>();
    if((analyses!=null)&&(analyses.size()>0)){
      int numMeasurements=analyses.size();
      for(int i=0;i<numMeasurements;i++){
        GeneticAnalysis m=analyses.get(i);
        if(m.getAnalysisType().equals("BiologicalMeasurement")){
          BiologicalMeasurement f=(BiologicalMeasurement)m;
          if(f.getValue()!=null){measures.add(f);}
        }
      }
    }
    return measures;
  }

  public BiologicalMeasurement getBiologicalMeasurement(String type){
    if((analyses!=null)&&(analyses.size()>0)){
      int numMeasurements=analyses.size();
      for(int i=0;i<numMeasurements;i++){
        GeneticAnalysis m=analyses.get(i);
        if(m.getAnalysisType().equals("BiologicalMeasurement")){
          BiologicalMeasurement f=(BiologicalMeasurement)m;
          if((f.getValue()!=null)&&(f.getMeasurementType()!=null)&&(f.getMeasurementType().equals(type))){return f;}
        }
      }
    }
    return null;
  }

}
