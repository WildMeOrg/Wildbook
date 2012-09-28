package org.ecocean.genetics;

import org.ecocean.DataCollectionEvent;
 
public class BiologicalMeasurement extends GeneticAnalysis implements java.io.Serializable {

  private static final long serialVersionUID = -7934850478287322048L;
  
  private Double value;
  
  private String units;
  
  private String samplingProtocol;
  
  private String type;
  
  public BiologicalMeasurement() {
  }
  
  public BiologicalMeasurement(String sampleID, String analysisID, String correspondingEncounterNumber, String type, Double value, String units, String samplingProtocol) {
    super(analysisID, type, correspondingEncounterNumber, sampleID);
    this.value = value;
    this.units = units;
    this.samplingProtocol=samplingProtocol;
    this.type=type;
  }

  public Double getValue() {
    return value;
  }

  public void setValue(Double value) {
    this.value = value;
  }
  
  public String getUnits() {
    return units;
  }
  
  public String getSamplingProtocol(){return samplingProtocol;}
  public void setSamplingProtocol(String protocol){this.samplingProtocol=protocol;}
  
  public String getType(){return type;}
  public void setType(String newType){this.type=newType;}

}
