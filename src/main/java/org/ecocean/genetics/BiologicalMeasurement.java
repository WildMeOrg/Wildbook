package org.ecocean.genetics;

import org.ecocean.DataCollectionEvent;
 
public class BiologicalMeasurement extends GeneticAnalysis implements java.io.Serializable {

  private static final long serialVersionUID = -7934850478287322048L;
  
  private Double value;
  
  private String units;
  
  private String samplingProtocol="";
  
  private String measurementType;
  
  
  private static String type="BiologicalMeasurement";
  
  public BiologicalMeasurement() {
  }
  
  public BiologicalMeasurement(String sampleID, String analysisID, String correspondingEncounterNumber, String measurementType, Double value, String units, String samplingProtocol) {
    super(analysisID, type, correspondingEncounterNumber, sampleID);
    this.value = value;
    this.units = units;
    if(samplingProtocol!=null){
      this.samplingProtocol=samplingProtocol;
    }
    this.measurementType=measurementType;
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
  
  public String getMeasurementType(){return measurementType;}
  public void setMeasurementType(String newType){this.measurementType=newType;}
  
  public static String getType(){return type;}
  
  public String getSuperHTMLString(){
    String paramValues=super.getHTMLString();
    return paramValues; 
  }
  
  public String getHTMLString(){
    String paramValues=super.getHTMLString();
    if((this.getMeasurementType()!=null)&&(this.getValue()!=null)&&(this.getUnits()!=null)&& (this.getSamplingProtocol()!=null)){paramValues+="     "+this.getMeasurementType()+" measurement: "+this.getValue().toString()+" "+this.getUnits()+" ("+this.getSampleID()+")<br />";}
  return paramValues; 
  }

}
