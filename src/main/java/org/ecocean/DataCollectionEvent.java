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
private String type;

/*
 * Empty constructor required for JDO persistence
 * 
 */
public DataCollectionEvent(){}

public DataCollectionEvent(String correspondingEncounterNumber, String type){
	this.correspondingEncounterNumber=correspondingEncounterNumber;
	this.type=type;
}

public String getCorrespondingEncounterNumber(){return correspondingEncounterNumber;}
public void setCorrespondingEncounterNumber(String encounterNumber){this.correspondingEncounterNumber=encounterNumber;}

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
}
