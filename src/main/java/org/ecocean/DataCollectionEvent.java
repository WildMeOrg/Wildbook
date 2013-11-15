/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean;

import javax.servlet.http.HttpServletRequest;

/**
 *
 *
 * @author jholmber
 */
public abstract class DataCollectionEvent implements java.io.Serializable {
  private static final long serialVersionUID = -4268335414161281405L;


/**
* DataCollectionEvent is an abstract base class that provides registration fields
* for common metadata related to data that may be collected about an individual during an Encounter instance
* of wildlife data in the field. The following types of classes are expected to extend DataCollectionEvent:
*   -PhotoVideoCollectionEvent class for managing photo and video data
*   -GeneticCollectionEvent for managing haplotype and microsatellite marker data taken during an Encounter instance
*   -TrackingDataEvent for managing SAT and PAT archival tag files related to an Encounter instance
*   -MeasurementCollectionEvent for morphometric data (e.g., length, width, height)
*/

/**
 * correspondingEncounterNumber species which Encounter instance this data collection even corresponds with
 */
private String correspondingEncounterNumber;
private String type;
private String dataCollectionEventID;

private String samplingProtocol;
private String samplingEffort;
private String eventStartDate;
private String eventEndDate;
private String fieldNumber;
private String fieldNotes;
private String eventRemarks;
private String institutionID;
private String collectionID;
private String datasetID;
private String institutionCode;
private String collectionCode;
private String datasetName;


/*
 * Empty constructor required for JDO persistence
 *
 */
public DataCollectionEvent(){}

public DataCollectionEvent(String correspondingEncounterNumber, String type){
	this.correspondingEncounterNumber=correspondingEncounterNumber;
	this.type=type;
}

public DataCollectionEvent(String correspondingEncounterNumber, String type, HttpServletRequest request){
  this.correspondingEncounterNumber=correspondingEncounterNumber;
  this.type=type;

  if(((request.getParameter("samplingProtocol"))!=null)&&(!request.getParameter("samplingProtocol").equals(""))){this.samplingProtocol=request.getParameter("samplingProtocol");}
  if(((request.getParameter("samplingEffort"))!=null)&&(!request.getParameter("samplingEffort").equals(""))){this.samplingEffort=request.getParameter("samplingEffort");}
  if(((request.getParameter("eventStartDate"))!=null)&&(!request.getParameter("eventStartDate").equals(""))){this.eventStartDate=request.getParameter("eventStartDate");}
  if(((request.getParameter("eventEndDate"))!=null)&&(!request.getParameter("eventEndDate").equals(""))){this.eventEndDate=request.getParameter("eventEndDate");}
  if(((request.getParameter("fieldNumber"))!=null)&&(!request.getParameter("fieldNumber").equals(""))){this.fieldNumber=request.getParameter("fieldNumber");}
  if(((request.getParameter("fieldNotes"))!=null)&&(!request.getParameter("fieldNotes").equals(""))){this.fieldNotes=request.getParameter("fieldNotes");}
  if(((request.getParameter("eventRemarks"))!=null)&&(!request.getParameter("eventRemarks").equals(""))){this.eventRemarks=request.getParameter("eventRemarks");}
  if(((request.getParameter("institutionID"))!=null)&&(!request.getParameter("institutionID").equals(""))){this.institutionID=request.getParameter("institutionID");}
  if(((request.getParameter("collectionID"))!=null)&&(!request.getParameter("collectionID").equals(""))){this.collectionID=request.getParameter("collectionID");}
  if(((request.getParameter("datasetID"))!=null)&&(!request.getParameter("datasetID").equals(""))){this.datasetID=request.getParameter("datasetID");}
  if(((request.getParameter("institutionCode"))!=null)&&(!request.getParameter("institutionCode").equals(""))){this.institutionCode=request.getParameter("institutionCode");}
  if(((request.getParameter("collectionCode"))!=null)&&(!request.getParameter("collectionCode").equals(""))){this.collectionCode=request.getParameter("collectionCode");}
  if(((request.getParameter("datasetName"))!=null)&&(!request.getParameter("datasetName").equals(""))){this.datasetName=request.getParameter("datasetName");}
}

public String getCorrespondingEncounterNumber(){return correspondingEncounterNumber;}
public void setCorrespondingEncounterNumber(String encounterNumber){
  if(encounterNumber!=null){
    this.correspondingEncounterNumber=encounterNumber;
  }
  else{
    this.correspondingEncounterNumber=null;
  }
}

public String getDataCollectionEventID(){return dataCollectionEventID;}
public void setDataCollectionEventID(String id){this.dataCollectionEventID=id;}

public String getSamplingProtocol(){return samplingProtocol;}
public void setSamplingProtocol(String protocol){this.samplingProtocol=protocol;}

public String getSamplingEffort(){return samplingEffort;}
public void setSamplingEffort(String effort){this.samplingEffort=effort;}

public String getEventStartDate(){return eventStartDate;}
public void setEventStartDate(String date){this.eventStartDate=date;}

public String getEventEndDate(){return eventEndDate;}
public void setEventEndDate(String date){this.eventEndDate=date;}

public String getFieldNumber(){return fieldNumber;}
public void setFieldNumber(String num){this.fieldNumber=num;}

public String getFieldNotes(){return fieldNotes;}
public void setFieldNotes(String notes){this.fieldNotes=notes;}

public String getEventRemarks(){return eventRemarks;}
public void setEventRemarks(String remarks){this.eventRemarks=remarks;}

public String getInstitutionID(){return institutionID;}
public void setInstitutionID(String id){this.institutionID=id;}

public String getCollectionID(){return collectionID;}
public void setCollectionID(String id){this.collectionID=id;}

public String getDatasetID(){return datasetID;}
public void setDatasetID(String id){this.datasetID=id;}

public String getInstitutionCode(){return institutionCode;}
public void setInstitutionCode(String id){this.institutionCode=id;}

public String getCollectionCode(){return collectionCode;}
public void setCollectionCode(String id){this.collectionCode=id;}

public String getDatasetName(){return datasetName;}
public void setDatasetName(String id){this.datasetName=id;}

public String getType(){return type;}

public void resetAbstractClassParameters(HttpServletRequest request){
  if(((request.getParameter("samplingProtocol"))!=null)&&(!request.getParameter("samplingProtocol").equals(""))){this.samplingProtocol=request.getParameter("samplingProtocol");}
  if(((request.getParameter("samplingEffort"))!=null)&&(!request.getParameter("samplingEffort").equals(""))){this.samplingEffort=request.getParameter("samplingEffort");}
  if(((request.getParameter("eventStartDate"))!=null)&&(!request.getParameter("eventStartDate").equals(""))){this.eventStartDate=request.getParameter("eventStartDate");}
  if(((request.getParameter("eventEndDate"))!=null)&&(!request.getParameter("eventEndDate").equals(""))){this.eventEndDate=request.getParameter("eventEndDate");}
  if(((request.getParameter("fieldNumber"))!=null)&&(!request.getParameter("fieldNumber").equals(""))){this.fieldNumber=request.getParameter("fieldNumber");}
  if(((request.getParameter("fieldNotes"))!=null)&&(!request.getParameter("fieldNotes").equals(""))){this.fieldNotes=request.getParameter("fieldNotes");}
  if(((request.getParameter("eventRemarks"))!=null)&&(!request.getParameter("eventRemarks").equals(""))){this.eventRemarks=request.getParameter("eventRemarks");}
  if(((request.getParameter("institutionID"))!=null)&&(!request.getParameter("institutionID").equals(""))){this.institutionID=request.getParameter("institutionID");}
  if(((request.getParameter("collectionID"))!=null)&&(!request.getParameter("collectionID").equals(""))){this.collectionID=request.getParameter("collectionID");}
  if(((request.getParameter("datasetID"))!=null)&&(!request.getParameter("datasetID").equals(""))){this.datasetID=request.getParameter("datasetID");}
  if(((request.getParameter("institutionCode"))!=null)&&(!request.getParameter("institutionCode").equals(""))){this.institutionCode=request.getParameter("institutionCode");}
  if(((request.getParameter("collectionCode"))!=null)&&(!request.getParameter("collectionCode").equals(""))){this.collectionCode=request.getParameter("collectionCode");}
  if(((request.getParameter("datasetName"))!=null)&&(!request.getParameter("datasetName").equals(""))){this.datasetName=request.getParameter("datasetName");}


}

public String getHTMLString(){
  String paramValues="";
  if((this.getCollectionCode()!=null)&&(!this.getCollectionCode().equals(""))){paramValues+="     Collection code: "+this.getCollectionCode()+"<br />";}
  if((this.getCollectionID()!=null)&&(!this.getCollectionID().equals(""))){paramValues+="     Collection ID: "+this.getCollectionID()+"<br />";}
  if((this.getDatasetID()!=null)&&(!this.getDatasetID().equals(""))){paramValues+="     Dataset ID: "+this.getDatasetID()+"<br />";}
  if((this.getDatasetName()!=null)&&(!this.getDatasetName().equals(""))){paramValues+="     Dataset name: "+this.getDatasetName()+"<br />";}
  if((this.getEventStartDate()!=null)&&(!this.getEventStartDate().equals(""))){paramValues+="     Event start date: "+this.getEventStartDate()+"<br />";}
  if((this.getEventEndDate()!=null)&&(!this.getEventEndDate().equals(""))){paramValues+="     Event end date: "+this.getEventEndDate()+"<br />";}
  if((this.getEventRemarks()!=null)&&(!this.getEventRemarks().equals(""))){paramValues+="     Event remarks: "+this.getEventRemarks()+"<br />";}
  if((this.getFieldNotes()!=null)&&(!this.getFieldNotes().equals(""))){paramValues+="     Field notes: "+this.getFieldNotes()+"<br />";}
  if((this.getFieldNumber()!=null)&&(!this.getFieldNumber().equals(""))){paramValues+="     Field number: "+this.getFieldNumber()+"<br />";}
  if((this.getInstitutionCode()!=null)&&(!this.getInstitutionCode().equals(""))){paramValues+="     Institution code: "+this.getInstitutionCode()+"<br />";}
  if((this.getInstitutionID()!=null)&&(!this.getInstitutionID().equals(""))){paramValues+="     Institution ID: "+this.getInstitutionID()+"<br />";}
  if((this.getSamplingEffort()!=null)&&(!this.getSamplingEffort().equals(""))){paramValues+="     Sampli]/ng effort: "+this.getSamplingEffort()+"<br />";}
  if((this.getSamplingProtocol()!=null)&&(!this.getSamplingProtocol().equals(""))){paramValues+="     Sampling protocol: "+this.getSamplingProtocol()+"<br />";}
  return paramValues;
}

}
