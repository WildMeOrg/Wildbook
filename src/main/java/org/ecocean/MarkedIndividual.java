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

import java.util.*;

/**
 * A <code>MarkedIndividual</code> object stores the complete <code>encounter</code> data for a single marked individual in a mark-recapture study.
 * <code>Encounters</code> are added to MarkedIndividual objects as multiple encounters are associated with
 * known marked individuals.
 * <p/>
 *
 * @author Jason Holmberg
 * @version 2.0
 * @see Encounter, Shepherd
 */
public class MarkedIndividual {

  //unique name of the MarkedIndividual, such as 'A-109'
  private String name = "";

  //alternate id for the MarkedIndividual, such as a physical tag number of reference in another database
  private String alternateid;

  //additional comments added by researchers
  private String comments = "None";

  //sex of the MarkedIndividual
  private String sex = "Unknown";

  //unused String that allows groups of MarkedIndividuals by optional parameters
  private String seriesCode = "None";

  //nickname for the MarkedIndividual...not used for any scientific purpose
  //also the nicknamer for credit
  private String nickName = "", nickNamer = "";

  //Vector of approved encounter objects added to this MarkedIndividual
  private Vector encounters = new Vector();

  //Vector of unapproved encounter objects added to this MarkedIndividual
  private Vector unidentifiableEncounters = new Vector();

  //Vector of String filenames of additional files added to the MarkedIndividual
  private Vector dataFiles = new Vector();

  //number of encounters of this MarkedIndividual
  private int numberEncounters;

  //number of unapproved encounters (log) of this MarkedIndividual
  private int numUnidentifiableEncounters;

  //a Vector of Strings of email addresses to notify when this MarkedIndividual is modified
  private Vector interestedResearchers = new Vector();

  private String dateTimeCreated;

  private String dynamicProperties;

  private String patterningCode;
  
  private int maxYearsBetweenResightings;
  
  public MarkedIndividual(String name, Encounter enc) {

    this.name = name;
    encounters.add(enc);
    dataFiles = new Vector();
    numberEncounters = 1;
    this.sex = enc.getSex();
    numUnidentifiableEncounters = 0;
  }

  /**
   * empty constructor used by JDO Enhancer - DO NOT USE
   */
  public MarkedIndividual() {
  }


  /**Adds a new encounter to this MarkedIndividual.
   *@param  newEncounter  the new <code>encounter</code> to add
   *@return true for successful addition, false for unsuccessful - Note: this change must still be committed for it to be stored in the database
   *@see  Shepherd#commitDBTransaction()
   */
  
  public boolean addEncounter(Encounter newEncounter) {
    
    newEncounter.assignToMarkedIndividual(name); 
    if(unidentifiableEncounters==null) {unidentifiableEncounters=new Vector();}
    if(newEncounter.wasRejected()) {
      numUnidentifiableEncounters++; 
      resetMaxNumYearsBetweenSightings();
      return unidentifiableEncounters.add(newEncounter);
      
      }
    else {
      numberEncounters++; 
      resetMaxNumYearsBetweenSightings();
      return encounters.add(newEncounter); }
    }
  
   /**Removes an encounter from this MarkedIndividual.
   *@param  getRidOfMe  the <code>encounter</code> to remove from this MarkedIndividual
   *@return true for successful removal, false for unsuccessful - Note: this change must still be committed for it to be stored in the database
   *@see  Shepherd#commitDBTransaction()
   */
  public boolean removeEncounter(Encounter getRidOfMe){
    if(getRidOfMe.wasRejected()) {
      numUnidentifiableEncounters--; 
      boolean changed=false;
      for(int i=0;i<unidentifiableEncounters.size();i++) {
        Encounter tempEnc=(Encounter)unidentifiableEncounters.get(i);
        if(tempEnc.getEncounterNumber().equals(getRidOfMe.getEncounterNumber())) {
          unidentifiableEncounters.remove(i);
          i--;
          changed=true;
          }
        }
      resetMaxNumYearsBetweenSightings();
      return changed;
      
      }
    else {
      numberEncounters--; 
      boolean changed=false;
      for(int i=0;i<encounters.size();i++) {
        Encounter tempEnc=(Encounter)encounters.get(i);
        if(tempEnc.getEncounterNumber().equals(getRidOfMe.getEncounterNumber())) {
          encounters.remove(i);
          i--;
          changed=true;
          }
        }
      resetMaxNumYearsBetweenSightings();
      return changed;
    }
  }

  /**
   * Returns the total number of submitted encounters for this MarkedIndividual
   *
   * @return the total number of encounters recorded for this MarkedIndividual
   */
  public int totalEncounters() {
    return encounters.size();
  }

  public int totalLogEncounters() {
    if (unidentifiableEncounters == null) {
      unidentifiableEncounters = new Vector();
    }
    return unidentifiableEncounters.size();
  }

  public Vector returnEncountersWithGPSData() {
    if(unidentifiableEncounters==null) {unidentifiableEncounters=new Vector();}
    Vector haveData=new Vector();
    for(int c=0;c<encounters.size();c++) {
      Encounter temp=(Encounter)encounters.get(c);
      if((temp.getDWCDecimalLatitude()!=null)&&(temp.getDWCDecimalLongitude()!=null)) {
        haveData.add(temp);
        }
      
      } 
    for(int d=0;d<numUnidentifiableEncounters;d++) {
      Encounter temp=(Encounter)unidentifiableEncounters.get(d);
      if((temp.getDWCDecimalLatitude()!=null)&&(temp.getDWCDecimalLongitude()!=null)) {
        
        haveData.add(temp);
        }
      
      } 
    return haveData;
    
  }

  public boolean isDeceased() {
    if (unidentifiableEncounters == null) {
      unidentifiableEncounters = new Vector();
    }
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if (temp.getLivingStatus().equals("dead")) {
        return true;
      }
    }
    for (int d = 0; d < numUnidentifiableEncounters; d++) {
      Encounter temp = (Encounter) unidentifiableEncounters.get(d);
      if (temp.getLivingStatus().equals("dead")) {
        return true;
      }
    }
    return false;
  }

  public boolean wasSightedInYear(int year) {
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if (temp.getYear() == year) {
        return true;
      }
    }
    return false;
  }

  public boolean wasSightedInYear(int year, String locCode) {
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if ((temp.getYear() == year) && (temp.getLocationCode().startsWith(locCode))) {
        return true;
      }
    }
    return false;
  }

  public boolean wasSightedInYearLeftTagsOnly(int year, String locCode) {
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if ((temp.getYear() == year) && (temp.getLocationCode().startsWith(locCode)) && (temp.getNumSpots() > 0)) {
        return true;
      }
    }
    return false;
  }

  public double averageLengthInYear(int year) {
    int numLengths = 0;
    double total = 0;
    double avg = 0;
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if ((temp.getYear() == year) && (temp.getSize() > 0.01)) {
        total += temp.getSize();
        numLengths++;
      }
    }
    if (numLengths > 0) {
      avg = total / numLengths;
    }
    return avg;
  }

  public double averageMeasuredLengthInYear(int year, boolean allowGuideGuess) {
    int numLengths = 0;
    double total = 0;
    double avg = 0;
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if (temp.getYear() == year) {
        if (temp.getSize() > 0.01) {
          if ((temp.getSizeGuess().equals("directly measured")) || ((allowGuideGuess) && (temp.getSizeGuess().equals("guide/researcher's guess")))) {

            total += temp.getSize();
            numLengths++;
          }
        }
      }
    }
    if (numLengths > 0) {
      avg = total / numLengths;
    }
    return avg;
  }

  //use the index identifier, not the full name of the keyword
  public boolean isDescribedByPhotoKeyword(Keyword word) {
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      Vector images = temp.getAdditionalImageNames();
      int size = images.size();
      for (int i = 0; i < size; i++) {
        String imageName = temp.getEncounterNumber() + "/" + ((String) images.get(i));
        if (word.isMemberOf(imageName)) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean hasApprovedEncounters() {
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if (temp.isApproved()) {
        return true;
      }
    }
    return false;
  }

  public boolean wasSightedInMonth(int year, int month) {
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if ((temp.getYear() == year) && (temp.getMonth() == month)) {
        return true;
      }
    }
    return false;
  }


  public boolean wasSightedInPeriod(int m_startYear, int m_startMonth, int m_endYear, int m_endMonth) {
    int endYear = m_endYear;
    int endMonth = m_endMonth;
    //int endDay=m_endDay;
    int startYear = m_startYear;
    int startMonth = m_startMonth;
    //int startDay=m_startDay;

    //test that start and end dates are not reversed
    if (endYear < startYear) {
      endYear = m_startYear;
      endMonth = m_startMonth;
      //endDay=m_startDay;
      startYear = m_endYear;
      startMonth = m_endMonth;
      //startDay=m_endDay;
    } else if ((endYear == startYear) && (endMonth < startMonth)) {
      endYear = m_startYear;
      endMonth = m_startMonth;
      //endDay=m_startDay;
      startYear = m_endYear;
      startMonth = m_endMonth;
      //startDay=m_endDay;
    }
    /*else if((endYear==startYear)&&(endMonth==startMonth)&&(endDay>startDay)) {
        endYear=m_startYear;
        endMonth=m_startMonth;
        endDay=m_startDay;
        startYear=m_endYear;
        startMonth=m_endMonth;
        startDay=m_endDay;
      }*/

    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if ((temp.getYear() > startYear) && (temp.getYear() < endYear)) {
        return true;
      } else if ((temp.getYear() == startYear) && (temp.getYear() < endYear) && (temp.getMonth() >= startMonth)) {
        return true;
      }
      //else if((temp.getYear()==startYear)&&(temp.getYear()<endYear)&&(temp.getMonth()==startMonth)){return true;}

      else if ((temp.getYear() > startYear) && (temp.getYear() == endYear) && (temp.getMonth() <= endMonth)) {
        return true;
      } else if ((temp.getYear() >= startYear) && (temp.getYear() <= endYear) && (temp.getMonth() >= startMonth) && (temp.getMonth() <= endMonth)) {
        return true;
      }


    }
    return false;
  }

  public boolean wasSightedInPeriod(int m_startYear, int m_startMonth, int m_startDay, int m_endYear, int m_endMonth, int m_endDay, String locCode) {
    int endYear = m_endYear;
    int endMonth = m_endMonth;
    int endDay = m_endDay;
    int startYear = m_startYear;
    int startMonth = m_startMonth;
    int startDay = m_startDay;


    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);

      if (temp.getLocationCode().startsWith(locCode)) {
        if ((temp.getYear() >= startYear) && (temp.getYear() <= endYear)) {
          if ((temp.getMonth() >= startMonth) && (temp.getMonth() <= endMonth)) {
            if ((temp.getDay() >= startDay) & (temp.getDay() <= endDay)) {
              return true;
            }
          }
        }


      }

    }
    return false;
  }

  public boolean wasSightedInPeriod(int m_startYear, int m_startMonth, int m_startDay, int m_endYear, int m_endMonth, int m_endDay) {
    int endYear = m_endYear;
    int endMonth = m_endMonth;
    int endDay = m_endDay;
    int startYear = m_startYear;
    int startMonth = m_startMonth;
    int startDay = m_startDay;


    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);

      if ((temp.getYear() >= startYear) && (temp.getYear() <= endYear)) {
        if ((temp.getMonth() >= startMonth) && (temp.getMonth() <= endMonth)) {
          if ((temp.getDay() >= startDay) & (temp.getDay() <= endDay)) {
            return true;
          }
        }
      }


    }
    return false;
  }

  public boolean wasSightedInPeriodLeftOnly(int m_startYear, int m_startMonth, int m_endYear, int m_endMonth) {
    int endYear = m_endYear;
    int endMonth = m_endMonth;
    int startYear = m_startYear;
    int startMonth = m_startMonth;

    //test that start and end dates are not reversed
    if (endYear < startYear) {
      endYear = m_startYear;
      endMonth = m_startMonth;
      startYear = m_endYear;
      startMonth = m_endMonth;
    } else if ((endYear == startYear) && (endMonth < startMonth)) {
      endYear = m_startYear;
      endMonth = m_startMonth;
      startYear = m_endYear;
      startMonth = m_endMonth;
    }
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if ((temp.getYear() > startYear) && (temp.getYear() < endYear) && (temp.getNumSpots() > 0)) {
        return true;
      } else if ((temp.getYear() == startYear) && (temp.getYear() < endYear) && (temp.getMonth() >= startMonth) && (temp.getNumSpots() > 0)) {
        return true;
      } else if ((temp.getYear() > startYear) && (temp.getYear() == endYear) && (temp.getMonth() <= endMonth) && (temp.getNumSpots() > 0)) {
        return true;
      } else if ((temp.getYear() >= startYear) && (temp.getYear() <= endYear) && (temp.getMonth() >= startMonth) && (temp.getMonth() <= endMonth) && (temp.getNumSpots() > 0)) {
        return true;
      }
    }
    return false;
  }


  /**
   * Returns the user-input name of the MarkedIndividual, which is also used as an Index in the FastObjects database
   *
   * @return the name of the MarkedIndividual as a String
   */
  public String getName() {
    return name;
  }

  public String getNickName() {
    if (nickName != null) {
      return nickName;
    } else {
      return "Unassigned";
    }
  }

  public String getNickNamer() {
    if (nickNamer != null) {
      return nickNamer;
    } else {
      return "Unknown";
    }
  }

  /**
   * Sets the nickname of the MarkedIndividual.
   */
  public void setNickName(String newName) {
    nickName = newName;
  }

  public void setNickNamer(String newNamer) {
    nickNamer = newNamer;
  }

  public void setName(String newName) {
    name = newName;
  }

  /**
   * Returns the specified encounter, where the encounter numbers range from 0 to n-1, where n is the total number of encounters stored
   * for this MarkedIndividual.
   *
   * @return the encounter at position i in the stored Vector of encounters
   * @param  i  the specified encounter number, where i=0...(n-1)
   */
  public Encounter getEncounter(int i) {
    return (Encounter) encounters.get(i);
  }

  public Encounter getLogEncounter(int i) {
    return (Encounter) unidentifiableEncounters.get(i);
  }

  /**
   * Returns the complete Vector of stored encounters for this MarkedIndividual.
   *
   * @return a Vector of encounters
   * @see java.util.Vector
   */
  public Vector getEncounters() {
    return encounters;
  }

  //sorted with the most recent first
  public Encounter[] getDateSortedEncounters(boolean includeLogEncounters) {
    //System.out.println("Starting getDateSortedEncounters");
    Vector final_encs = new Vector();
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      final_encs.add(temp);
    }
    //System.out.println(".....added encounters...");
    if (includeLogEncounters) {
      int numLogs = unidentifiableEncounters.size();
      for (int c = 0; c < numLogs; c++) {
        Encounter temp = (Encounter) unidentifiableEncounters.get(c);
        final_encs.add(temp);
      }
      //System.out.println(".....added log encounters...");
    }
    int finalNum = final_encs.size();
    Encounter[] encs2 = new Encounter[finalNum];
    //System.out.println(".....allocated array");
    for (int q = 0; q < finalNum; q++) {
      encs2[q] = (Encounter) final_encs.get(q);
    }
    //System.out.println(".....assigned values to array...");

    EncounterDateComparator dc = new EncounterDateComparator();
    Arrays.sort(encs2, dc);
    //System.out.println(".....done sort...");
    return encs2;
  }

  public Vector getUnidentifiableEncounters() {
    if (unidentifiableEncounters == null) {
      unidentifiableEncounters = new Vector();
    }
    return unidentifiableEncounters;
  }

  /**
   * Returns any additional, general comments recorded for this MarkedIndividual as a whole.
   *
   * @return a String of comments
   */
  public String getComments() {
    if (comments != null) {

      return comments;
    } else {
      return "None";
    }
  }

  /**
   * Adds any general comments recorded for this MarkedIndividual as a whole.
   *
   * @return a String of comments
   */
  public void addComments(String newComments) {
    if ((comments != null) && (!(comments.equals("None")))) {
      comments += newComments;
    } else {
      comments = newComments;
    }
  }

  /**
   * Returns the complete Vector of stored satellite tag data files for this MarkedIndividual.
   *
   * @return a Vector of Files
   * @see java.util.Vector
   */
  public Vector getDataFiles() {
    return dataFiles;
  }

  /**
   * Returns the sex of this MarkedIndividual.
   *
   * @return a String
   */
  public String getSex() {
    return sex;
  }

  /**
   * Sets the sex of this MarkedIndividual.
   */
  public void setSex(String newSex) {
    sex = newSex;
  }


  public double getLastEstimatedSize() {
    double lastSize = 0;
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if (temp.getSize() > lastSize) {
        lastSize = temp.getSize();
      }
    }
    return lastSize;
  }

  public boolean wasSightedInLocationCode(String locationCode) {
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if (temp.getLocationCode().startsWith(locationCode)) {
        return true;
      }
    }
    return false;
  }

  public ArrayList<String> particpatesInTheseVerbatimEventDates() {
    ArrayList<String> vbed = new ArrayList<String>();
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if ((temp.getVerbatimEventDate() != null) && (!vbed.contains(temp.getVerbatimEventDate()))) {
        vbed.add(temp.getVerbatimEventDate());
      }
    }
    return vbed;
  }

  public boolean wasSightedInVerbatimEventDate(String ved) {
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if ((temp.getVerbatimEventDate() != null) && (temp.getVerbatimEventDate().equals(ved))) {
        return true;
      }
    }
    return false;
  }

  public boolean wasSightedByUser(String user) {
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if ((temp.getSubmitterID() != null) && (temp.getSubmitterID().equals(user))) {
        return true;
      }
    }
    return false;
  }

  public int getMaxNumYearsBetweenSightings(){
    return maxYearsBetweenResightings;
  }

  public int getEarliestSightingYear() {
    int lowestYear = 5000;
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if (temp.getYear() < lowestYear) lowestYear = temp.getYear();
    }
    return lowestYear;
  }

  public String getSeriesCode() {
    return seriesCode;
  }

  public Vector getInterestedResearchers() {
    return interestedResearchers;
  }

  public void addInterestedResearcher(String email) {
    interestedResearchers.add(email);
  }

  public void removeInterestedResearcher(String email) {
    for (int i = 0; i < interestedResearchers.size(); i++) {
      String rName = (String) interestedResearchers.get(i);
      if (rName.equals(email)) {
        interestedResearchers.remove(i);
      }
    }
  }

  public void setSeriesCode(String newCode) {
    seriesCode = newCode;
  }

  /**
   * Adds a satellite tag data file for this MarkedIndividual.
   *
   * @param  dataFile  the satellite tag data file to be added
   */
  public void addDataFile(String dataFile) {
    dataFiles.add(dataFile);
  }

  /**
   * Removes a satellite tag data file for this MarkedIndividual.
   *
   * @param  dataFile  The satellite data file, as a String, to be removed.
   */
  public void removeDataFile(String dataFile) {
    dataFiles.remove(dataFile);
  }

  public int getNumberTrainableEncounters() {
    int count = 0;
    for (int iter = 0; iter < encounters.size(); iter++) {
      Encounter enc = (Encounter) encounters.get(iter);
      if (enc.getSpots().size() > 0) {
        count++;
      }
    }
    return count;
  }


  public int getNumberRightTrainableEncounters() {
    int count = 0;
    for (int iter = 0; iter < encounters.size(); iter++) {
      Encounter enc = (Encounter) encounters.get(iter);
      if (enc.getRightSpots().size() > 0) {
        count++;
      }
    }
    return count;
  }

  public Vector getTrainableEncounters() {
    int count = 0;
    Vector results = new Vector();
    for (int iter = 0; iter < encounters.size(); iter++) {
      Encounter enc = (Encounter) encounters.get(iter);
      if (enc.getSpots().size() > 0) {
        results.add(enc);
      }
    }
    return results;
  }

  public Vector getRightTrainableEncounters() {
    int count = 0;
    Vector results = new Vector();
    for (int iter = 0; iter < encounters.size(); iter++) {
      Encounter enc = (Encounter) encounters.get(iter);
      if (enc.getRightSpots().size() > 0) {
        results.add(enc);
      }
    }
    return results;
  }

  /*public int getFirstTrainingEncounter() {
     for(int iter=0;iter<encounters.size(); iter++) {
       encounter enc=(encounter)encounters.get(iter);
       if (enc.getSpots()!=null) {return iter;}
       }
     return 0;
   }*/

  /*public int getSecondTrainingEncounter() {
     for(int iter=(getFirstTrainingEncounter()+1);iter<encounters.size(); iter++) {
       encounter enc=(encounter)encounters.get(iter);
       if (enc.getSpots()!=null) {return iter;}
       }
     return 0;
   }*/


  //months 1-12, days, 1-31
  public double avgLengthInPeriod(int m_startYear, int m_startMonth, int m_endYear, int m_endMonth) {

    double avgLength = 0;
    int numMeasurements = 0;

    int endYear = m_endYear;
    int endMonth = m_endMonth;
    int startYear = m_startYear;
    int startMonth = m_startMonth;

    //test that start and end dates are not reversed
    if (endYear < startYear) {
      endYear = m_startYear;
      endMonth = m_startMonth;
      startYear = m_endYear;
      startMonth = m_endMonth;
    } else if ((endYear == startYear) && (endMonth < startMonth)) {
      endYear = m_startYear;
      endMonth = m_startMonth;
      startYear = m_endYear;
      startMonth = m_endMonth;
    }

    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if ((temp.getYear() > startYear) && (temp.getYear() < endYear)) {
        if (temp.getSize() > 0.0) {
          avgLength += temp.getSize();
          numMeasurements++;
        }
      } else if ((temp.getYear() == startYear) && (temp.getYear() < endYear) && (temp.getMonth() >= startMonth)) {
        if (temp.getSize() > 0.0) {
          avgLength += temp.getSize();
          numMeasurements++;
        }
      } else if ((temp.getYear() > startYear) && (temp.getYear() == endYear) && (temp.getMonth() <= endMonth)) {
        if (temp.getSize() > 0.0) {
          avgLength += temp.getSize();
          numMeasurements++;
        }
      } else if ((temp.getYear() >= startYear) && (temp.getYear() <= endYear) && (temp.getMonth() >= startMonth) && (temp.getMonth() <= endMonth)) {
        if (temp.getSize() > 0.0) {
          avgLength += temp.getSize();
          numMeasurements++;
        }
      }


    }
    if (numMeasurements > 0) {
      return (avgLength / numMeasurements);
    } else {
      return 0.0;
    }
  }

  public String getDateTimeCreated() {
    if (dateTimeCreated != null) {
      return dateTimeCreated;
    }
    return "";
  }

  public void setDateTimeCreated(String time) {
    dateTimeCreated = time;
  }

  public void setAlternateID(String newID) {
    this.alternateid = newID;
  }

  public String getAlternateID() {
    if (alternateid == null) {
      return "None";
    }
    return alternateid;
  }

  /*
   * Returns a bracketed, comma-delimited string of all of the alternateIDs registered for this marked individual, including those only assigned at the Encounter level
   */
   public String getAllAlternateIDs(){
     ArrayList<String> allIDs = new ArrayList<String>();
     
      //add any alt IDs for the individual itself 
      if(alternateid!=null){allIDs.add(alternateid);}
      
      //add an alt IDs for the individual's encounters
      int numEncs=encounters.size();
      for(int c=0;c<numEncs;c++) {
        Encounter temp=(Encounter)encounters.get(c);
        if((temp.getAlternateID()!=null)&&(!temp.getAlternateID().equals("None"))&&(!allIDs.contains(temp.getAlternateID()))) {allIDs.add(temp.getAlternateID());}
      }
      
      return allIDs.toString();
    }

  public String getDynamicProperties() {
    return dynamicProperties;
  }

  public void setDynamicProperty(String name, String value) {
    name = name.replaceAll(";", "_").trim().replaceAll("%20", " ");
    value = value.replaceAll(";", "_").trim();

    if (dynamicProperties == null) {
      dynamicProperties = name + "=" + value + ";";
    } else {

      //let's create a TreeMap of the properties
      TreeMap<String, String> tm = new TreeMap<String, String>();
      StringTokenizer st = new StringTokenizer(dynamicProperties, ";");
      while (st.hasMoreTokens()) {
        String token = st.nextToken();
        int equalPlace = token.indexOf("=");
        tm.put(token.substring(0, equalPlace), token.substring(equalPlace + 1));
      }
      if (tm.containsKey(name)) {
        tm.remove(name);
        tm.put(name, value);

        //now let's recreate the dynamicProperties String
        String newProps = tm.toString();
        int stringSize = newProps.length();
        dynamicProperties = newProps.substring(1, (stringSize - 1)).replaceAll(", ", ";") + ";";
      } else {
        dynamicProperties = dynamicProperties + name + "=" + value + ";";
      }
    }
  }

  public String getDynamicPropertyValue(String name){
    if(dynamicProperties!=null){
      name=name.replaceAll("%20", " ");
      //let's create a TreeMap of the properties
      TreeMap<String,String> tm=new TreeMap<String,String>();
      StringTokenizer st=new StringTokenizer(dynamicProperties, ";");
      while(st.hasMoreTokens()){
        String token = st.nextToken();
        int equalPlace=token.indexOf("=");
        try{
          tm.put(token.substring(0,equalPlace), token.substring(equalPlace+1));
        }
        catch(IndexOutOfBoundsException ioob){}
      }
      if(tm.containsKey(name)){return tm.get(name);}
    }
    return null;
  }

  public void removeDynamicProperty(String name) {
    name = name.replaceAll(";", "_").trim().replaceAll("%20", " ");
    if (dynamicProperties != null) {

      //let's create a TreeMap of the properties
      TreeMap<String, String> tm = new TreeMap<String, String>();
      StringTokenizer st = new StringTokenizer(dynamicProperties, ";");
      while (st.hasMoreTokens()) {
        String token = st.nextToken();
        int equalPlace = token.indexOf("=");
        tm.put(token.substring(0, (equalPlace)), token.substring(equalPlace + 1));
      }
      if (tm.containsKey(name)) {
        tm.remove(name);

        //now let's recreate the dynamicProperties String
        String newProps = tm.toString();
        int stringSize = newProps.length();
        dynamicProperties = newProps.substring(1, (stringSize - 1)).replaceAll(", ", ";") + ";";
      }
    }
  }

  public ArrayList<Keyword> getAllAppliedKeywordNames(Shepherd myShepherd) {
    ArrayList<Keyword> al = new ArrayList<Keyword>();
    int numEncounters = encounters.size();
    for (int i = 0; i < numEncounters; i++) {
      Encounter enc = (Encounter) encounters.get(i);
      Iterator it = myShepherd.getAllKeywords();
      while (it.hasNext()) {
        Keyword word = (Keyword) it.next();
        if ((word.isMemberOf(enc)) && (!al.contains(word))) {
          al.add(word);
        }
      }
    }
    return al;
  }

  public ArrayList<String> getAllValuesForDynamicProperty(String propertyName) {
    ArrayList<String> listPropertyValues = new ArrayList<String>();

    //first, check if the individual has the property applied
    if (getDynamicPropertyValue(propertyName) != null) {
      listPropertyValues.add(getDynamicPropertyValue(propertyName));
    }

    //next check the encounters
    int numEncounters = encounters.size();
    for (int i = 0; i < numEncounters; i++) {
      Encounter enc = (Encounter) encounters.get(i);
      if (enc.getDynamicPropertyValue(propertyName) != null) {
        listPropertyValues.add(enc.getDynamicPropertyValue(propertyName));
      }
    }
    return listPropertyValues;
  }

  public String getPatterningCode(){
    
    int numEncs=encounters.size();
    for(int i=0;i<numEncs;i++){
      Encounter enc=(Encounter)encounters.get(i);
      if(enc.getPatterningCode()!=null){return enc.getPatterningCode();}
    }
    return null;
  }
  
  public void setPatterningCode(String newCode){this.patterningCode=newCode;}
  
  public void resetMaxNumYearsBetweenSightings(){
    int maxYears=0;
    int lowestYear=3000;
    int highestYear=0;
    for(int c=0;c<encounters.size();c++) {
      Encounter temp=(Encounter)encounters.get(c);
      if(temp.getYear()<lowestYear) lowestYear=temp.getYear();
      if(temp.getYear()>highestYear) highestYear=temp.getYear();
      maxYears=highestYear-lowestYear;
      }
    maxYearsBetweenResightings=maxYears;
    }
  
}