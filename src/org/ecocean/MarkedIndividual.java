package org.ecocean;

import java.util.Vector;
import java.util.Arrays;

/**
 *A <code>MarkedIndividual</code> object stores the complete <code>encounter</code> data for a single marked individual in a mark-recapture study.
 *<code>Encounters</code> are added to MarkedIndividual objects as multiple encounters are associated with
 *known marked individuals. 
 *<p>
 *@author	Jason Holmberg
 *@version	2.0
 *@see Encounter, Shepherd
*/
public class MarkedIndividual{
	
	//unique name of the MarkedIndividual, such as 'A-109'
	private String name=""; 
	
	//alternate id for the MarkedIndividual, such as a physical tag number of reference in another database
	private String alternateid;
	
	//additional comments added by researchers
	private String comments="None"; 
	
	//sex of the MarkedIndividual
	private String sex="Unknown";
		
	//unused String that allows groups of MarkedIndividuals by optional parameters
	private String seriesCode="None"; 
	
	//nickname for the MarkedIndividual...not used for any scientific purpose
	//also the nicknamer for credit
	private String nickName="", nickNamer="";
	
	//Vector of approved encounter objects added to this MarkedIndividual
	private Vector encounters=new Vector();
	
	//Vector of unapproved encounter objects added to this MarkedIndividual
	private Vector unidentifiableEncounters=new Vector();
	
	//Vector of String filenames of additional files added to the MarkedIndividual 
	private Vector dataFiles=new Vector();
	
	//number of encounters of this MarkedIndividual
	private int numberEncounters;
	
	//number of unapproved encounters (log) of this MarkedIndividual
	private int numUnidentifiableEncounters;
	
	//a Vector of Strings of email addresses to notify when this MarkedIndividual is modified
	private Vector interestedResearchers=new Vector();
	
	private String dateTimeCreated;
	
	public MarkedIndividual(String name, Encounter enc) {
		
		this.name=name;
		encounters.add(enc);
		dataFiles=new Vector();
		numberEncounters=1;
		this.sex=enc.getSex();
		numUnidentifiableEncounters=0;
		}
		
	/**empty constructor used by JDO Enhancer - DO NOT USE
	 */
	public MarkedIndividual(){}
	
	
	
	/**Adds a new encounter to this MarkedIndividual.
	 *@param	newEncounter	the new <code>encounter</code> to add
	 *@return	true for successful addition, false for unsuccessful - Note: this change must still be committed for it to be stored in the database
	 *@see	Shepherd#commitDBTransaction()
	 */
	
	public boolean addEncounter(Encounter newEncounter) {
		
		newEncounter.assignToMarkedIndividual(name); 
		if(unidentifiableEncounters==null) {unidentifiableEncounters=new Vector();}
		if(newEncounter.wasRejected()) {
			numUnidentifiableEncounters++; 
			return unidentifiableEncounters.add(newEncounter);
			
			}
		else {
			numberEncounters++; 
			return encounters.add(newEncounter); }
		}
	
	 /**Removes an encounter from this MarkedIndividual.
	 *@param	getRidOfMe	the <code>encounter</code> to remove from this MarkedIndividual
	 *@return	true for successful removal, false for unsuccessful - Note: this change must still be committed for it to be stored in the database
	 *@see	Shepherd#commitDBTransaction()
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
			return changed;
		}
	}
	
	/**Returns the total number of submitted encounters for this MarkedIndividual
	 *@return the total number of encounters recorded for this MarkedIndividual
	 */
	public int totalEncounters() {return encounters.size();}
	
	public int totalLogEncounters() {
		if(unidentifiableEncounters==null) {unidentifiableEncounters=new Vector();}
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
		if(unidentifiableEncounters==null) {unidentifiableEncounters=new Vector();}
		for(int c=0;c<encounters.size();c++) {
			Encounter temp=(Encounter)encounters.get(c);
			if(temp.getLivingStatus().equals("dead")) {
				return true;
			}
		}	
		for(int d=0;d<numUnidentifiableEncounters;d++) {
			Encounter temp=(Encounter)unidentifiableEncounters.get(d);
			if(temp.getLivingStatus().equals("dead")) {
				return true;
			}	
		}	
		return false;
	}
	
	public boolean wasSightedInYear(int year) {
		for(int c=0;c<encounters.size();c++) {
			Encounter temp=(Encounter)encounters.get(c);
			if(temp.getYear()==year) {return true;}
		}
		return false;
	}
	
	public boolean wasSightedInYear(int year, String locCode) {
		for(int c=0;c<encounters.size();c++) {
			Encounter temp=(Encounter)encounters.get(c);
			if((temp.getYear()==year)&&(temp.getLocationCode().startsWith(locCode))) {return true;}
		}
		return false;
	}
	
	public boolean wasSightedInYearLeftTagsOnly(int year, String locCode) {
		for(int c=0;c<encounters.size();c++) {
			Encounter temp=(Encounter)encounters.get(c);
			if((temp.getYear()==year)&&(temp.getLocationCode().startsWith(locCode))&&(temp.getNumSpots()>0)) {return true;}
		}
		return false;
	}
	
	public double averageLengthInYear(int year) {
		int numLengths=0;
		double total=0;
		double avg=0;
		for(int c=0;c<encounters.size();c++) {
			Encounter temp=(Encounter)encounters.get(c);
			if((temp.getYear()==year)&&(temp.getSize()>0.01)) {
				total+=temp.getSize();
				numLengths++;
			}
		}
		if(numLengths>0) {avg=total/numLengths;}
		return avg;
	}
	
	public double averageMeasuredLengthInYear(int year, boolean allowGuideGuess) {
		int numLengths=0;
		double total=0;
		double avg=0;
		for(int c=0;c<encounters.size();c++) {
			Encounter temp=(Encounter)encounters.get(c);
			if(temp.getYear()==year) {
				if (temp.getSize()>0.01){
					if((temp.getSizeGuess().equals("directly measured"))||((allowGuideGuess)&&(temp.getSizeGuess().equals("guide/researcher's guess")))) {
				
						total+=temp.getSize();
						numLengths++;
					}
				}
			}
		}
		if(numLengths>0) {avg=total/numLengths;}
		return avg;
	}
	
	//use the index identifier, not the full name of the keyword
	public boolean isDescribedByPhotoKeyword(Keyword word) {
		for(int c=0;c<encounters.size();c++) {
			Encounter temp=(Encounter)encounters.get(c);
			Vector images=temp.getAdditionalImageNames();
			int size=images.size();
			for(int i=0;i<size;i++) {
				String imageName=temp.getEncounterNumber()+"/"+((String)images.get(i));
				if(word.isMemberOf(imageName)) {return true;}
			}
		}
		return false;
	}
	
	public boolean hasApprovedEncounters() {
		for(int c=0;c<encounters.size();c++) {
			Encounter temp=(Encounter)encounters.get(c);
			if(temp.isApproved()) {return true;}
		}
		return false;
	}
	
	public boolean wasSightedInMonth(int year, int month) {
		for(int c=0;c<encounters.size();c++) {
			Encounter temp=(Encounter)encounters.get(c);
			if((temp.getYear()==year)&&(temp.getMonth()==month)) {return true;}
		}
		return false;
	}
	

	public boolean wasSightedInPeriod(int m_startYear, int m_startMonth, int m_endYear, int m_endMonth) {
		int endYear=m_endYear;
		int endMonth=m_endMonth;
		//int endDay=m_endDay;
		int startYear=m_startYear;
		int startMonth=m_startMonth;
		//int startDay=m_startDay;
		
		//test that start and end dates are not reversed
		if(endYear<startYear) {
			endYear=m_startYear;
			endMonth=m_startMonth;
			//endDay=m_startDay;
			startYear=m_endYear;
			startMonth=m_endMonth;
			//startDay=m_endDay;
		}
		else if((endYear==startYear)&&(endMonth<startMonth)){
			endYear=m_startYear;
			endMonth=m_startMonth;
			//endDay=m_startDay;
			startYear=m_endYear;
			startMonth=m_endMonth;
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
		
		for(int c=0;c<encounters.size();c++) {
			Encounter temp=(Encounter)encounters.get(c);
			if((temp.getYear()>startYear)&&(temp.getYear()<endYear)){return true;}
			else if((temp.getYear()==startYear)&&(temp.getYear()<endYear)&&(temp.getMonth()>=startMonth)){return true;}
			//else if((temp.getYear()==startYear)&&(temp.getYear()<endYear)&&(temp.getMonth()==startMonth)){return true;}
			
			else if((temp.getYear()>startYear)&&(temp.getYear()==endYear)&&(temp.getMonth()<=endMonth)){return true;}
			else if((temp.getYear()>=startYear)&&(temp.getYear()<=endYear)&&(temp.getMonth()>=startMonth)&&(temp.getMonth()<=endMonth)){return true;}
			
		
		}
		return false;
	}
	
	public boolean wasSightedInPeriod(int m_startYear, int m_startMonth, int m_startDay, int m_endYear, int m_endMonth, int m_endDay, String locCode) {
		int endYear=m_endYear;
		int endMonth=m_endMonth;
		int endDay=m_endDay;
		int startYear=m_startYear;
		int startMonth=m_startMonth;
		int startDay=m_startDay;

		
		/*//test that start and end dates are not reversed
		if(endYear<startYear) {
			endYear=m_startYear;
			endMonth=m_startMonth;
			endDay=m_startDay;
			startYear=m_endYear;
			startMonth=m_endMonth;
			startDay=m_endDay;
			endDay=m_startDay;
		}
		else if((endYear==startYear)&&(endMonth<startMonth)){
			endYear=m_startYear;
			endMonth=m_startMonth;
			endDay=m_startDay;
			startYear=m_endYear;
			startMonth=m_endMonth;
			startDay=m_endDay;
			endDay=m_startDay;
		}
		else if((endYear==startYear)&&(endMonth==startMonth)&&(endDay>startDay)) {
			endYear=m_startYear;
			endMonth=m_startMonth;
			endDay=m_startDay;
			startYear=m_endYear;
			startMonth=m_endMonth;
			startDay=m_endDay;
			endDay=m_startDay;
		}*/
		
		for(int c=0;c<encounters.size();c++) {
			Encounter temp=(Encounter)encounters.get(c);

			if(temp.getLocationCode().startsWith(locCode)){
				if((temp.getYear()>=startYear)&&(temp.getYear()<=endYear)){
					if((temp.getMonth()>=startMonth)&&(temp.getMonth()<=endMonth)){
						if((temp.getDay()>=startDay)&(temp.getDay()<=endDay)){
							return true;
						}
					}
				}
				
				
			}
		
		}
		return false;
	}
	
	public boolean wasSightedInPeriodLeftOnly(int m_startYear, int m_startMonth, int m_endYear, int m_endMonth) {
		int endYear=m_endYear;
		int endMonth=m_endMonth;
		int startYear=m_startYear;
		int startMonth=m_startMonth;

		//test that start and end dates are not reversed
		if(endYear<startYear) {
			endYear=m_startYear;
			endMonth=m_startMonth;
			startYear=m_endYear;
			startMonth=m_endMonth;
		}
		else if((endYear==startYear)&&(endMonth<startMonth)){
			endYear=m_startYear;
			endMonth=m_startMonth;
			startYear=m_endYear;
			startMonth=m_endMonth;
		}
		for(int c=0;c<encounters.size();c++) {
			Encounter temp=(Encounter)encounters.get(c);
			if((temp.getYear()>startYear)&&(temp.getYear()<endYear)&&(temp.getNumSpots()>0)){return true;}
			else if((temp.getYear()==startYear)&&(temp.getYear()<endYear)&&(temp.getMonth()>=startMonth)&&(temp.getNumSpots()>0)){return true;}
			else if((temp.getYear()>startYear)&&(temp.getYear()==endYear)&&(temp.getMonth()<=endMonth)&&(temp.getNumSpots()>0)){return true;}
			else if((temp.getYear()>=startYear)&&(temp.getYear()<=endYear)&&(temp.getMonth()>=startMonth)&&(temp.getMonth()<=endMonth)&&(temp.getNumSpots()>0)){return true;}
		}
		return false;
	}
	
	
	
	/**Returns the user-input name of the MarkedIndividual, which is also used as an Index in the FastObjects database
	 *@return	the name of the MarkedIndividual as a String
	 */
	public String getName() {return name;}
	
	public String getNickName() {
		if(nickName!=null){
			return nickName;
		}
		else {return "Unassigned";}
	}
	
	public String getNickNamer() {
		if(nickNamer!=null){
			return nickNamer;
		}
		else {return "Unknown";}
	}
	
	/**Sets the nickname of the MarkedIndividual.
	 */
	public void setNickName(String newName) {nickName=newName;}
	
	public void setNickNamer(String newNamer) {nickNamer=newNamer;}
	public void setName(String newName){ name = newName;}
	
	/**Returns the specified encounter, where the encounter numbers range from 0 to n-1, where n is the total number of encounters stored
	 *for this MarkedIndividual.
	 *@param	i	the specified encounter number, where i=0...(n-1)
	 *@return the encounter at position i in the stored Vector of encounters
	 */
	public Encounter getEncounter(int i) {return (Encounter)encounters.get(i);}
	
	public Encounter getLogEncounter(int i) {return (Encounter)unidentifiableEncounters.get(i);}
		
	/**Returns the complete Vector of stored encounters for this MarkedIndividual.
	 *@return	a Vector of encounters
	 *@see java.util.Vector
	 */
	public Vector getEncounters() {return encounters;}
	
	//sorted with the most recent first
	public Encounter[] getDateSortedEncounters(boolean includeLogEncounters){
		//System.out.println("Starting getDateSortedEncounters");
		Vector final_encs=new Vector();
		for(int c=0;c<encounters.size();c++) {
			Encounter temp=(Encounter)encounters.get(c);
			final_encs.add(temp);
		}
		//System.out.println(".....added encounters...");
		if(includeLogEncounters) {
			int numLogs=unidentifiableEncounters.size();
			for(int c=0;c<numLogs;c++) {
				Encounter temp=(Encounter)unidentifiableEncounters.get(c);
				final_encs.add(temp);
			}
			//System.out.println(".....added log encounters...");	
		}
		int finalNum=final_encs.size();
		Encounter[] encs2=new Encounter[finalNum];
		//System.out.println(".....allocated array");
		for(int q=0;q<finalNum;q++) {
			encs2[q]=(Encounter)final_encs.get(q);
		}
		//System.out.println(".....assigned values to array...");
		
		EncounterDateComparator dc=new EncounterDateComparator();
		Arrays.sort(encs2, dc);
		//System.out.println(".....done sort...");
		return encs2;
	}
	
	public Vector getUnidentifiableEncounters() {
		if(unidentifiableEncounters==null) {unidentifiableEncounters=new Vector();}
		return unidentifiableEncounters;
	}
	
	/**Returns any additional, general comments recorded for this MarkedIndividual as a whole.
	 *@return	a String of comments
	 */
	public String getComments() {
		if (comments!=null) {
		
		return comments;}
		else {return "None";}
	}
	
	/**Adds any general comments recorded for this MarkedIndividual as a whole.
	 *@return	a String of comments
	 */
	public void addComments(String newComments) {
		if ((comments!=null)&&(!(comments.equals("None")))) {comments+=newComments;} 
		else {comments=newComments;}
	}
	
	 /**Returns the complete Vector of stored satellite tag data files for this MarkedIndividual.
	 *@return	a Vector of Files
	 *@see java.util.Vector
	 */
	public Vector getDataFiles() {return dataFiles;}
	
	/**Returns the sex of this MarkedIndividual.
	 *@return	a String
	 */
	public String getSex() {return sex;}
	
	/**Sets the sex of this MarkedIndividual.
	 *
	 */
	public void setSex(String newSex) {sex=newSex;}
	
	
	public double getLastEstimatedSize(){
		double lastSize=0;
		for(int c=0;c<encounters.size();c++) {
			Encounter temp=(Encounter)encounters.get(c);
			if(temp.getSize()>lastSize) {lastSize=temp.getSize();}
			}
		return lastSize;
		}
	
	public boolean wasSightedInLocationCode(String locationCode){
		for(int c=0;c<encounters.size();c++) {
			Encounter temp=(Encounter)encounters.get(c);
			if(temp.getLocationCode().startsWith(locationCode)) {return true;}
			}
		return false;
		}
	
	public boolean wasSightedByUser(String user){
		for(int c=0;c<encounters.size();c++) {
			Encounter temp=(Encounter)encounters.get(c);
			if((temp.getSubmitterID()!=null)&&(temp.getSubmitterID().equals(user))) {return true;}
			}
		return false;
		}
		
	public int getMaxNumYearsBetweenSightings(){
		int maxYears=0;
		int lowestYear=3000;
		int highestYear=0;
		for(int c=0;c<encounters.size();c++) {
			Encounter temp=(Encounter)encounters.get(c);
			if(temp.getYear()<lowestYear) lowestYear=temp.getYear();
			if(temp.getYear()>highestYear) highestYear=temp.getYear();
			maxYears=highestYear-lowestYear;
			}
		return maxYears;
		}
		
	public int getEarliestSightingYear(){
		int lowestYear=5000;
		for(int c=0;c<encounters.size();c++) {
			Encounter temp=(Encounter)encounters.get(c);
			if(temp.getYear()<lowestYear) lowestYear=temp.getYear();
			}
		return lowestYear;
		}
	
	public String getSeriesCode() {return seriesCode;}
	
	public Vector getInterestedResearchers() {
		return interestedResearchers;
	}
	
	public void addInterestedResearcher(String email) {
		interestedResearchers.add(email);
	}
	
	public void removeInterestedResearcher(String email) {
		for(int i=0;i<interestedResearchers.size();i++){
			String rName=(String)interestedResearchers.get(i);
			if(rName.equals(email)){
				interestedResearchers.remove(i);
			}
		}
	}

	public void setSeriesCode(String newCode) {seriesCode=newCode;}
	
	/**Adds a satellite tag data file for this MarkedIndividual.
	 *@param	dataFile	the satellite tag data file to be added
	 */
	public void addDataFile(String dataFile) {dataFiles.add(dataFile);}
	
	/**Removes a satellite tag data file for this MarkedIndividual.
	 *@param	dataFile	The satellite data file, as a String, to be removed.
	 */
	public void removeDataFile(String dataFile) {dataFiles.remove(dataFile);}
	
	public int getNumberTrainableEncounters() {
		int count=0;
		for(int iter=0;iter<encounters.size(); iter++) {
			Encounter enc=(Encounter)encounters.get(iter);
			if (enc.getSpots().size()>0) {count++;}
			}
		return count;
		}
		
		
	public int getNumberRightTrainableEncounters() {
		int count=0;
		for(int iter=0;iter<encounters.size(); iter++) {
			Encounter enc=(Encounter)encounters.get(iter);
			if (enc.getRightSpots().size()>0) {count++;}
			}
		return count;
		}
		
	public Vector getTrainableEncounters() {
		int count=0;
		Vector results=new Vector();
		for(int iter=0;iter<encounters.size(); iter++) {
			Encounter enc=(Encounter)encounters.get(iter);
			if (enc.getSpots().size()>0) {results.add(enc);}
			}
		return results;
		}
		
	public Vector getRightTrainableEncounters() {
		int count=0;
		Vector results=new Vector();
		for(int iter=0;iter<encounters.size(); iter++) {
			Encounter enc=(Encounter)encounters.get(iter);
			if (enc.getRightSpots().size()>0) {results.add(enc);}
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
		
		double avgLength=0;
		int numMeasurements=0;
		
		int endYear=m_endYear;
		int endMonth=m_endMonth;
		int startYear=m_startYear;
		int startMonth=m_startMonth;
		
		//test that start and end dates are not reversed
		if(endYear<startYear) {
			endYear=m_startYear;
			endMonth=m_startMonth;
			startYear=m_endYear;
			startMonth=m_endMonth;
		}
		else if((endYear==startYear)&&(endMonth<startMonth)){
			endYear=m_startYear;
			endMonth=m_startMonth;
			startYear=m_endYear;
			startMonth=m_endMonth;
		}
		
		for(int c=0;c<encounters.size();c++) {
			Encounter temp=(Encounter)encounters.get(c);
			if((temp.getYear()>startYear)&&(temp.getYear()<endYear)){
				if(temp.getSize()>0.0){
					avgLength+=temp.getSize();
					numMeasurements++;
				}
			}
			else if((temp.getYear()==startYear)&&(temp.getYear()<endYear)&&(temp.getMonth()>=startMonth)){
				if(temp.getSize()>0.0){
					avgLength+=temp.getSize();
					numMeasurements++;
				}
			}

			else if((temp.getYear()>startYear)&&(temp.getYear()==endYear)&&(temp.getMonth()<=endMonth)){
				if(temp.getSize()>0.0){
					avgLength+=temp.getSize();
					numMeasurements++;
				}
			}
			else if((temp.getYear()>=startYear)&&(temp.getYear()<=endYear)&&(temp.getMonth()>=startMonth)&&(temp.getMonth()<=endMonth)){
				if(temp.getSize()>0.0){
					avgLength+=temp.getSize();
					numMeasurements++;
				}
			}
			
		
		}
		if(numMeasurements>0){return (avgLength/numMeasurements);}
		else{return 0.0;}
	}
	
	public String getDateTimeCreated(){
		if (dateTimeCreated!=null) {return dateTimeCreated;}
		return "";
	}
	public void setDateTimeCreated(String time){dateTimeCreated=time;}
	
	public void setAlternateID(String newID){
		this.alternateid=newID;
	}
	
	public String getAlternateID(){
		if(alternateid==null){return "None";}
		return alternateid;
	}
	
}