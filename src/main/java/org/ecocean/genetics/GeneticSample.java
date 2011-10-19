package org.ecocean.genetics;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.ecocean.DataCollectionEvent;

public class GeneticSample extends DataCollectionEvent {


  private static final long serialVersionUID = -4918907304313880745L;
  private static String type="GeneticSample";

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
  public GeneticSample(){}
  
  /*
   * Required constructor for instance creation
   */
  public GeneticSample(String correspondingEncounterNumber, String sampleID) {
    super(correspondingEncounterNumber, type);
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

  public String getTissueType(){return tissueType;}
  public void setTissueType(String newType){this.tissueType=newType;}
  
  public String getPreservationMethod(){return preservationMethod;}
  public void setPreservationMethod(String newMethod){this.preservationMethod=newMethod;}
  
  public String getStorageLabID(){return storageLabID;}
  public void setStorageLabID(String newLab){this.storageLabID=newLab;}
  
  public String getSampleID(){return sampleID;}

  public String getAlternateSampleID(){return alternateSampleID;}
  private void setAlternateSampleID(String newID){this.alternateSampleID=newID;}

}
