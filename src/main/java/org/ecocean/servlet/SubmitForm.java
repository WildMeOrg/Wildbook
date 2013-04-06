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

package org.ecocean.servlet;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionError;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.upload.FormFile;
import org.apache.struts.upload.MultipartRequestHandler;


public class SubmitForm extends ActionForm {

  public SubmitForm() {
  }

  public static final String ERROR_PROPERTY_MAX_LENGTH_EXCEEDED = "The maximum upload length has been exceeded by the client.";

  //define the variables for encounter submission
  private String mailList = "no";
  private Calendar date = Calendar.getInstance();
  private String uniqueID = (new Integer(date.get(Calendar.DAY_OF_MONTH))).toString() + (new Integer(date.get(Calendar.MONTH) + 1)).toString() + (new Integer(date.get(Calendar.YEAR))).toString() + (new Integer(date.get(Calendar.HOUR_OF_DAY))).toString() + (new Integer(date.get(Calendar.MINUTE))).toString() + (new Integer(date.get(Calendar.SECOND))).toString();
  private String releaseDate = "";
  private String size="", depth="";
  private String elevation="";
  private String measureUnits = "", location = "", sex = "unknown", comments = "", primaryImageName = "", guess = "no estimate provided";
  private String submitterName = "", submitterEmail = "", submitterPhone = "", submitterAddress = "", submitterOrganization="", submitterProject="";
  private String photographerName = "", photographerEmail = "", photographerPhone = "", photographerAddress = "";
  private Vector additionalImageNames = new Vector();
  private String livingStatus = "";
  private int encounterNumber = 0;
  private int day = 1, month = 1, year = 2003, hour = 12;
  private String lat = "", longitude = "", latDirection = "", longDirection = "", scars = "None";
  private String minutes = "00", gpsLongitudeMinutes = "", gpsLongitudeSeconds = "", gpsLatitudeMinutes = "", gpsLatitudeSeconds = "", submitterID = "N/A", informothers = "";
  private String genusSpecies="";
  private String behavior="";
  private String lifeStage="";
  private String country="";
  
  private Map<String, Object> measurements = new HashMap<String, Object>();
  
  private Map<String, Object> metalTags = new HashMap<String, Object>();
  private String acousticTagSerial = "";
  private String acousticTagId = "";
  private String satelliteTagSerial = "";
  private String satelliteTagArgosPttNumber = "";
  private String satelliteTagName = "";
  
  
  /**
   * The value of the text the user has sent as form data
   */
  protected String theText;

  /**
   * The value of the embedded query string parameter
   */
  protected String queryParam;

  /**
   * Whether or not to write to a file
   */
  protected boolean writeFile = true;

  /**
   * The files that the user has uploaded
   */
  protected FormFile theFile1;
  protected FormFile theFile2;
  protected FormFile theFile3;
  protected FormFile theFile4;

  /**
   * The file path to write to
   */
  protected String filePath1;
  protected String filePath2;
  protected String filePath3;
  protected String filePath4;


  //reset all variables
  public void reset() {
    writeFile = false;
    mailList = "no";
    date = Calendar.getInstance();
    uniqueID = (new Integer(date.get(Calendar.DAY_OF_MONTH))).toString() + (new Integer(date.get(Calendar.MONTH) + 1)).toString() + (new Integer(date.get(Calendar.YEAR))).toString() + (new Integer(date.get(Calendar.HOUR_OF_DAY))).toString() + (new Integer(date.get(Calendar.MINUTE))).toString() + (new Integer(date.get(Calendar.SECOND))).toString();
    releaseDate = null;
    size = "";
    depth = "";
    elevation = "";
    measureUnits = "";
    location = "";
    sex = "unknown";
    comments = "";
    primaryImageName = "";
    guess = "no estimate provided";
    submitterName = "";
    submitterEmail = "";
    submitterPhone = "";
    submitterAddress = "";
    submitterOrganization = "";
    submitterProject = "";
    photographerName = "";
    photographerEmail = "";
    photographerPhone = "";
    photographerAddress = "";
    additionalImageNames = new Vector();
    encounterNumber = 0;
    day = 1;
    month = 1;
    year = 2003;
    hour = 12;
    lat = "";
    longitude = "";
    latDirection = "";
    longDirection = "";
    scars = "None";
    minutes = "00";
    gpsLongitudeMinutes = "";
    gpsLongitudeSeconds = "";
    gpsLatitudeMinutes = "";
    gpsLatitudeSeconds = "";
    submitterID = "N/A";
    informothers = "";
    livingStatus = "";
    genusSpecies="";
    lifeStage="";
    measurements.clear();
    metalTags.clear();
    acousticTagSerial = "";
    acousticTagId = "";
    satelliteTagSerial = "";
    satelliteTagArgosPttNumber = "";
    satelliteTagName = "";
    country="";
  }


  public String getMailList() {
    return this.mailList;
  }

  public void setMailList(String mailList) {
    this.mailList = mailList;
  }

  public Calendar getDate() {
    return this.date;
  }

  public void setDate(Calendar date) {
    this.date = date;
  }
  
  public void setReleaseDate(String releaseDate) {
    this.releaseDate = releaseDate;
  }
  
  public String getReleaseDate() {
    return releaseDate;
  }

  public String getUniqueID() {
    return this.uniqueID;
  }

  public void setUniqueID(String uniqueID) {
    this.uniqueID = uniqueID;
  }

  public String getSubmitterID() {
    return this.submitterID;
  }

  public void setSubmitterID(String submitterID) {
    this.submitterID = submitterID;
  }

  public String getSize() {
	  System.out.println("Size in SubmitForm is:"+this.size);
    return this.size;
  }

  public void setSize(String size) {
    this.size = size;
  }

  public String getDepth() {
	System.out.println("Depth in SubmitForm is:"+this.depth);
    return this.depth;
  }

  public void setDepth(String depth) {
    this.depth = depth;
  }

  public String getElevation() {
	  System.out.println("Elevation in SubmitForm is:"+this.elevation);
    return this.elevation;
  }

  public void setElevation(String elevation) {
    this.elevation = elevation;
  }

  public String getMeasureUnits() {
    return this.measureUnits;
  }

  public void setMeasureUnits(String measureUnits) {
    this.measureUnits = measureUnits;
  }

  public String getLocation() {
    return this.location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public String getLivingStatus() {
    return this.livingStatus;
  }

  public void setLivingStatus(String livingStatus) {
    this.livingStatus = livingStatus;
  }


  public String getSex() {
    return this.sex;
  }

  public void setSex(String sex) {
    this.sex = sex;
  }

  public String getComments() {
    return this.comments;
  }

  public void setComments(String comments) {
    this.comments = comments;
  }

  public String getPrimaryImageName() {
    return this.primaryImageName;
  }

  public void setPrimaryImageName(String primaryImageName) {
    this.primaryImageName = primaryImageName;
  }

  public String getGuess() {
    return this.guess;
  }

  public void setGuess(String guess) {
    this.guess = guess;
  }

  public String getSubmitterName() {
    return this.submitterName;
  }

  public void setSubmitterName(String submitterName) {
    this.submitterName = submitterName;
  }

  public String getSubmitterEmail() {
    return this.submitterEmail;
  }

  public void setSubmitterEmail(String submitterEmail) {
    this.submitterEmail = submitterEmail;
  }

  public String getSubmitterPhone() {
    return this.submitterPhone;
  }

  public void setSubmitterPhone(String submitterPhone) {
    this.submitterPhone = submitterPhone;
  }

  public String getSubmitterAddress() {
    return this.submitterAddress;
  }

  public void setSubmitterAddress(String submitterAddress) {
    this.submitterAddress = submitterAddress;
  }

  public String getSubmitterOrganization() {
      return this.submitterOrganization;
  }
  public void setSubmitterOrganization(String submitterOrganization) {
      this.submitterOrganization = submitterOrganization;
  }
  
  public String getCountry() {
    return this.country;
}
public void setCountry(String newCountry) {
    this.country = newCountry;
}

  public String getSubmitterProject() {
        return this.submitterProject;
  }
  public void setSubmitterProject(String submitterProject) {
        this.submitterProject = submitterProject;
  }

  public String getPhotographerName() {
    return this.photographerName;
  }

  public void setPhotographerName(String photographerName) {
    this.photographerName = photographerName;
  }

  public String getPhotographerEmail() {
    return this.photographerEmail;
  }

  public void setPhotographerEmail(String photographerEmail) {
    this.photographerEmail = photographerEmail;
  }

  public String getPhotographerPhone() {
    return this.photographerPhone;
  }

  public void setPhotographerPhone(String photographerPhone) {
    this.photographerPhone = photographerPhone;
  }

  public String getPhotographerAddress() {
    return this.photographerAddress;
  }

  public void setPhotographerAddress(String photographerAddress) {
    this.photographerAddress = photographerAddress;
  }

  public Vector getAdditionalImageNames() {
    return this.additionalImageNames;
  }

  public void setAdditionalImageNames(Vector additionalImageNames) {
    this.additionalImageNames = additionalImageNames;
  }

  public int getEncounterNumber() {
    return this.encounterNumber;
  }

  public void setEncounterNumber(int encounterNumber) {
    this.encounterNumber = encounterNumber;
  }

  public int getDay() {
    return this.day;
  }

  public void setDay(int day) {
    this.day = day;
  }

  public int getMonth() {
    return this.month;
  }

  public void setMonth(int month) {
    this.month = month;
  }

  public int getYear() {
    return this.year;
  }

  public void setYear(int year) {
    this.year = year;
  }

  public int getHour() {
    return this.hour;
  }

  public void setHour(int hour) {
    this.hour = hour;
  }

  public String getLat() {
    return this.lat;
  }

  public void setLat(String lat) {
    this.lat = lat;
  }

  public String getLongitude() {
    return this.longitude;
  }

  public void setLongitude(String longitude) {
    this.longitude = longitude;
  }

  public String getLatDirection() {
    return this.latDirection;
  }

  public void setLatDirection(String latDirection) {
    this.latDirection = latDirection;
  }

  public String getLongDirection() {
    return this.longDirection;
  }

  public void setLongDirection(String longDirection) {
    this.longDirection = longDirection;
  }

  public String getScars() {
    return this.scars;
  }

  public void setScars(String scars) {
    this.scars = scars;
  }

  public String getMinutes() {
    return this.minutes;
  }

  public void setMinutes(String minutes) {
    this.minutes = minutes;
  }

  public String getGpsLongitudeMinutes() {
    return this.gpsLongitudeMinutes;
  }

  public void setGpsLongitudeMinutes(String gpsLongitudeMinutes) {
    this.gpsLongitudeMinutes = gpsLongitudeMinutes;
  }

  public String getGpsLongitudeSeconds() {
    return this.gpsLongitudeSeconds;
  }

  public void setGpsLongitudeSeconds(String gpsLongitudeSeconds) {
    this.gpsLongitudeSeconds = gpsLongitudeSeconds;
  }

  public String getGpsLatitudeMinutes() {
    return this.gpsLatitudeMinutes;
  }

  public void setGpsLatitudeMinutes(String gpsLatitudeMinutes) {
    this.gpsLatitudeMinutes = gpsLatitudeMinutes;
  }

  public String getGpsLatitudeSeconds() {
    return this.gpsLatitudeSeconds;
  }

  public void setGpsLatitudeSeconds(String gpsLatitudeSeconds) {
    this.gpsLatitudeSeconds = gpsLatitudeSeconds;
  }

    public String getBehavior() {
      return this.behavior;
    }

    public void setBehavior(String behavior) {
      this.behavior = behavior;
  }
    
    public String getLifeStage() {
      return this.lifeStage;
    }

    public void setLifeStage(String lifeStage) {
      this.lifeStage = lifeStage;
  }
    
  public Object getMeasurement(String key) {
    return measurements.get(key);
  }
  
  public void setMeasurement(String key, Object measurement) {
    measurements.put(key, measurement);
  }
  
  public Map<String, Object> getMeasurements() {
    return measurements;
  }

  public Map<String, Object> getMetalTags() {
    return metalTags;
  }


  public void setMetalTag(String key, Object value) {
    metalTags.put(key, value);
  }

  public Object getMetalTag(String key) {
    return metalTags.get(key);
  }
  public String getAcousticTagSerial() {
    return acousticTagSerial;
  }

  public void setAcousticTagSerial(String acousticTagSerial) {
    this.acousticTagSerial = acousticTagSerial;
  }

  public String getAcousticTagId() {
    return acousticTagId;
  }

  public void setAcousticTagId(String acousticTagId) {
    this.acousticTagId = acousticTagId;
  }

  public String getSatelliteTagSerial() {
    return satelliteTagSerial;
  }

  public void setSatelliteTagSerial(String satelliteTagSerial) {
    this.satelliteTagSerial = satelliteTagSerial;
  }

  public String getSatelliteTagArgosPttNumber() {
    return satelliteTagArgosPttNumber;
  }

  public void setSatelliteTagArgosPttNumber(String satelliteTagArgosPttNumber) {
    this.satelliteTagArgosPttNumber = satelliteTagArgosPttNumber;
  }

  public String getSatelliteTagName() {
    return satelliteTagName;
  }

  public void setSatelliteTagName(String satelliteTagName) {
    this.satelliteTagName = satelliteTagName;
  }

  public String getInformothers() {
    return this.informothers;
  }

  public void setInformothers(String informit) {
    this.informothers = informit;
  }

  public String getGenusSpecies(){return this.genusSpecies;}
  public void setGenusSpecies(String newValue) {
      this.genusSpecies = newValue;
  }


  /**
   * Retrieve the value of the text the user has sent as form data
   */
  public String getTheText() {
    return theText;
  }

  /**
   * Set the value of the form data text
   */
  public void setTheText(String theText) {
    this.theText = theText;
  }

  /**
   * Retrieve the value of the query string parameter
   */
  public String getQueryParam() {
    return queryParam;
  }

  /**
   * Set the value of the query string parameter
   */
  public void setQueryParam(String queryParam) {
    this.queryParam = queryParam;
  }

  /**
   * Retrieve a representation of the file the user has uploaded
   */
  public FormFile getTheFile1() {
    if (theFile1 != null) {
      System.out.println("File one is good.");
    }
    return theFile1;
  }

  public FormFile getTheFile2() {
    if (theFile2 != null) {
      System.out.println("File 2 is good.");
    }
    return theFile2;
  }

  public FormFile getTheFile3() {
    if (theFile3 != null) {
      System.out.println("File 3 is good.");
    }
    return theFile3;
  }

  public FormFile getTheFile4() {
    if (theFile4 != null) {
      System.out.println("File 4 is good.");
    }
    return theFile4;
  }

  /**
   * Set a representation of the file the user has uploaded
   */
  public void setTheFile1(FormFile theFile1) {
    this.theFile1 = theFile1;
  }

  public void setTheFile2(FormFile theFile2) {
    this.theFile2 = theFile2;
  }

  public void setTheFile3(FormFile theFile3) {
    this.theFile3 = theFile3;
  }

  public void setTheFile4(FormFile theFile4) {
    this.theFile4 = theFile4;
  }

  /**
   * Set whether or not to write to a file
   */
  public void setWriteFile(boolean writeFile) {
    this.writeFile = writeFile;
  }

  /**
   * Get whether or not to write to a file
   */
  public boolean getWriteFile() {
    return writeFile;
  }

  /**
   * Set the path to write a file to
   */
  public void setFilePath1(String filePath1) {
    this.filePath1 = filePath1;
  }

  public void setFilePath2(String filePath2) {
    this.filePath2 = filePath2;
  }

  public void setFilePath3(String filePath3) {
    this.filePath3 = filePath3;
  }

  public void setFilePath4(String filePath4) {
    this.filePath4 = filePath4;
  }

  /**
   * Get the path to write a file to
   */
  /*public String getFilePath(int a) {
      if(a==0) {
        return filePath1;
    }
    else if(a==1) {return filePath2;}
    else if(a==2){return filePath3;}
    else{return filePath4;}
  }*/


  /**
   * Check to make sure the client hasn't exceeded the maximum allowed upload size inside of this
   * validate method.
   */
  public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
    ActionErrors errors = new ActionErrors();
    //has the maximum length been exceeded?
    Boolean maxLengthExceeded = (Boolean)
      request.getAttribute(MultipartRequestHandler.ATTRIBUTE_MAX_LENGTH_EXCEEDED);
    if ((maxLengthExceeded != null) && (maxLengthExceeded.booleanValue())) {
      writeFile = false;
      errors.add(ERROR_PROPERTY_MAX_LENGTH_EXCEEDED, new ActionError("maxLengthExceeded"));
    }

    System.out.println("SubmitForm sees location as: " + request.getParameter("location"));

    return errors;

  }
}
