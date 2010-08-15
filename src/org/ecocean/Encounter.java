package org.ecocean;

import java.lang.Math;
import java.util.Vector;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.TreeMap;


/**
 *An <code>encounter</code> object stores the complete data for a single sighting/capture report.
 *<code>Encounters</code> are added to MarkedIndividual objects as multiple encounters are associated with
 *known individuals. 
 *<p>
 *@author	Jason Holmberg
 *@version	2.0
*/
public class Encounter implements java.io.Serializable{
	static final long serialVersionUID = -146404246317385604L;
	
	/**
	 * The following attributes are described in the Darwin Core quick reference at:
	 * http://rs.tdwg.org/dwc/terms/#dcterms:type
	 * 
	 * Wherever possible, this class will be extended with Darwin Core attributes for greater adoption of the standard.
	 * 
	 */
	private String sex="unknown";	
	private String locationID="None";
	private double maximumDepthInMeters;
	private double maximumElevationInMeters;
	private String catalogNumber="";
	private String individualID;
	private int day=0;
	private int month=0;
	private int year=0;
	private String decimalLatitude;
	private String decimalLongitude;
	private String verbatimLocality; 
	private String occurrenceRemarks="";
	private String modified;
	private String occurrenceID;
	private String recordedBy;
	private String otherCatalogNumbers;
	private String behavior;
	private String eventID;
	private String measurementUnit; 
	private String verbatimEventDate;
	private String dynamicProperties;
	public String identificationRemarks="";
	
	
	/*
	 * The following fields are specific to this mark-recapture project and do not have an easy to map Darwin Core equivalent.
	 */
	
	//An URL to a thumbnail image representing the encounter.
	//This is 
	private String dwcImageURL;
	
	//Defines whether the sighting represents a living or deceased individual.
	//Currently supported values are: "alive" and "dead".
	private String livingStatus;
	
	//Date the encounter was added to the library.
	private String dwcDateAdded;
	
	//Size of the individual in meters
	private double size;
		
	//Additional comments added by library users
	private String researcherComments="None";
	
	//username of the logged in researcher assigned to the encounter
	private String submitterID; 
	//name, email, phone, address of the encounter reporter
	private String submitterEmail, submitterPhone, submitterAddress;
	private String informothers;
	//name, email, phone, address of the encounter photographer
	private String photographerName, photographerEmail, photographerPhone, photographerAddress;
	//a Vector of Strings defining the relative path to each photo. The path is relative to the servlet base directory
	private Vector additionalImageNames=new Vector();	
	//a Vector of Strings of email addresses to notify when this encounter is modified
	private Vector interestedResearchers=new Vector();
	//time metrics of the report
	private int hour=0;
	private String minutes="00"; 
	//describes how the shark was measured
	private String size_guess="none provided"; 
	//String reported GPS values for lat and long of the encounter
	private String gpsLongitude="", gpsLatitude="";
	//whether this encounter has been rejected and should be hidden from public display
	//unidentifiable encounters generally contain some data worth saving but not enough for accurate photo-identification
	public boolean unidentifiable=false; 
	//whether this encounter has a left-side spot image extracted 
	public boolean hasSpotImage=false;
	//whether this encounter has a right-side spot image extracted
	public boolean hasRightSpotImage=false; 
	//Indicates whether this record can be exposed via TapirLink
	private boolean okExposeViaTapirLink=false;
	//whether this encounter has been approved for public display
	public boolean approved=true; 
	//integers of the latitude and longitude degrees
	//private int lat=-1000, longitude=-1000;
	//name of the stored file from which the left-side spots were extracted
	public String spotImageFileName=""; 
	//name of the stored file from which the right-side spots were extracted
	public String rightSpotImageFileName="";
	//string descriptor of the most obvious scar (if any) as reported by the original submitter
	//we also use keywords to be more specific
	public String distinguishingScar="None"; 
	//describes how this encounter was matched to an existing shark - by eye, by pattern recognition algorithm etc.

	private int numSpotsLeft=0;
	private int numSpotsRight=0;
	
	
	//SPOTS
	//an array of the extracted left-side superSpots
	//private superSpot[] spots;
	private ArrayList<SuperSpot> spots;
	
	//an array of the extracted right-side superSpots
	//private superSpot[] rightSpots;
	private ArrayList<SuperSpot> rightSpots;
	
	//an array of the three extracted left-side superSpots used for the affine transform of the I3S algorithm
	//private superSpot[] leftReferenceSpots;
	private ArrayList<SuperSpot> leftReferenceSpots;
	
	//an array of the three extracted right-side superSpots used for the affine transform of the I3S algorithm
	//private superSpot[] rightReferenceSpots;
	private ArrayList<SuperSpot> rightReferenceSpots;
	
	


	
	//start constructors
	
	/**empty constructor required by the JDO Enhancer
	 */
	public Encounter(){}

	/**temporary constructor for testing purposes only - REMOVE ME
	 */
	//public encounter(superSpot[] spots) {
	//	this.spots=spots;
    //    }
        
     /**Use this constructor to add the minimum level of information for a new encounter
      *The Vector <code>additionalImages</code> must be a Vector of Blob objects
	  *@see com.poet.jdo.Blob, shepherd#makeBlobFromImageFile(File imageFile)
	  */
	public Encounter(int day, int month, int year, int hour, String minutes, String size_guess, String location, String submitterName, String submitterEmail, Vector additionalImageNames) {
		this.verbatimLocality=location;
		this.recordedBy=submitterName;
		this.submitterEmail=submitterEmail;
		this.additionalImageNames=additionalImageNames;
		this.day=day;
		this.month=month;
		this.year=year;
		this.hour=hour;
		this.minutes=minutes;
		this.size_guess=size_guess;
		this.individualID="Unassigned";
    }
        

	/**Returns an array of all of the superSpots for this encounter.
	 *@return	the array of superSpots, taken from the croppedImage, that make up the digital fingerprint for this encounter
	 */
	public ArrayList<SuperSpot> getSpots(){return spots;}
	
	public ArrayList<SuperSpot> getRightSpots(){return rightSpots;}
	
	/**Returns an array of all of the superSpots for this encounter.
	 *@return	the array of superSpots, taken from the croppedImage, that make up the digital fingerprint for this encounter
	 */
	public void setSpots(ArrayList<SuperSpot> newSpots){spots=newSpots;}
	public void setRightSpots(ArrayList<SuperSpot> newSpots){rightSpots=newSpots;}
	
	/**Removes any spot data
	 */
	public void removeSpots(){spots=null;}
	public void removeRightSpots(){rightSpots=null;}
	public void nukeAllSpots(){leftReferenceSpots=null;rightReferenceSpots=null;spots=null;rightSpots=null;}
	
	/**Returns the number of spots in the cropped image stored for this encounter.
	 *@return the number of superSpots that make up the digital fingerprint for this encounter
	 */
	public int getNumSpots(){
		return spots.size();
	}
	
	public int getNumRightSpots(){
		return rightSpots.size();
	}
	
	public boolean hasLeftSpotImage(){return hasSpotImage;}
	public boolean hasRightSpotImage(){return hasRightSpotImage;}
	
	
	/**Sets the recorded length of the shark for this encounter.
	 *
	 */
	public void setSize(double mysize){size=mysize;}
	
	/**Returns the recorded length of the shark for this encounter.
	 *@return	the length of the shark
	 */
	public double getSize(){return size;}
	
	/**Sets the units of the recorded size and depth of the shark for this encounter.
	 *Acceptable entries are either "Feet" or "Meters"
	 */
	public void setMeasureUnits(String measure){measurementUnit=measure;}
	
	/**Returns the units of the recorded size and depth of the shark for this encounter.
	 *@return	the units of measure used by the recorded of this encounter, either "feet" or "meters"
	 */
	public String getMeasureUnits(){return measurementUnit;}
	public String getMeasurementUnit(){return measurementUnit;}
	
	/**Returns the recorded location of this encounter.
	 *@return	the location of this encounter
	 */
	public String getLocation(){return verbatimLocality;}
	
	public void setLocation(String location){this.verbatimLocality=location;}
	
	/**Sets the recorded sex of the shark in this encounter.
	 *Acceptable values are "Male" or "Female"
	 */
	public void setSex(String thesex){sex=thesex;}
	
	/**Returns the recorded sex of the shark in this encounter.
	 *@return the sex of the shark, either "male" or "female"
	 */
	public String getSex(){return sex;}
	
	/**Returns any submitted comments about scarring on the shark.
	 *@return any comments regarding observed scarring on the shark's body
	 */

	public String getComments(){return occurrenceRemarks;}
	
	/**Sets the initially submitted comments about markings and additional details on the shark.
	 *
	 */
	public void setComments(String newComments){occurrenceRemarks=newComments;}
	
	/**Returns any comments added by researchers
	 *@return any comments added by authroized researchers
	 */

	public String getRComments(){return researcherComments;}
	
	/**Adds additional comments about the encounter
	 *@param newComments	any additional comments to be added to the encounter
	 */
	public void addComments(String newComments){
		if ((researcherComments!=null)&&(!(researcherComments.equals("None")))) {
			researcherComments+=newComments;
			}
		else {researcherComments=newComments;}
	}
	
	/**Returns the name of the person who submitted this encounter data.
	 *@return	the name of the person who submitted this encounter to the database
	 */
	public String getSubmitterName(){return recordedBy;}
	public void setSubmitterName(String newname){recordedBy=newname;}
	
	/**Returns the e-mail address of the person who submitted this encounter data
	 *@return the e-mail address of the person who submitted this encounter data
	 */
	public String getSubmitterEmail(){return submitterEmail;}
	public void setSubmitterEmail(String newemail){submitterEmail=newemail;}
	
	/**Returns the phone number of the person who submitted this encounter data.
	 *@return the phone number of the person who submitted this encounter data
	 */
	public String getSubmitterPhone(){return submitterPhone;}
	
	/**Sets the phone number of the person who submitted this encounter data.
	 *
	 */
	public void setSubmitterPhone(String newphone){submitterPhone=newphone;}
	
	/**Returns the mailing address of the person who submitted this encounter data.
	 *@return the mailing address of the person who submitted this encounter data
	 */
	public String getSubmitterAddress(){return submitterAddress;}
	
	/**Sets the mailing address of the person who submitted this encounter data.
	 *
	 */
	public void setSubmitterAddress(String address){submitterAddress=address;}
	
	/**Returns the name of the person who took the primaryImage this encounter.
	 *@return the name of the photographer who took the primary image for this encounter
	 */
	public String getPhotographerName(){return photographerName;}
	
	/**Sets the name of the person who took the primaryImage this encounter.
	 * 
	 */
	public void setPhotographerName(String name){photographerName=name;}
	
	/**Returns the e-mail address of the person who took the primaryImage this encounter.
	 *@return	@return the e-mail address of the photographer who took the primary image for this encounter
	 */
	public String getPhotographerEmail(){return photographerEmail;}
	
	/**Sets the e-mail address of the person who took the primaryImage this encounter.
	 *
	 */
	public void setPhotographerEmail(String email){photographerEmail=email;}
	
	/**Returns the phone number of the person who took the primaryImage this encounter.
	 *@return the phone number of the photographer who took the primary image for this encounter
	 */
	public String getPhotographerPhone(){return photographerPhone;}
	
	/**Sets the phone number of the person who took the primaryImage this encounter.
	 *
	 */
	public void setPhotographerPhone(String phone){photographerPhone=phone;}
	
	/**Returns the mailing address of the person who took the primaryImage this encounter.
	 *@return the mailing address of the photographer who took the primary image for this encounter
	 */
	public String getPhotographerAddress(){return photographerAddress;}
	
	/**Sets the mailing address of the person who took the primaryImage this encounter.
	 *
	 */
	public void setPhotographerAddress(String address){photographerAddress=address;}
	
	/**Sets the recorded depth of this encounter.
	 *
	 */
	public void setDepth(double mydepth){maximumDepthInMeters=mydepth;}
	
	/**Returns the recorded depth of this encounter.
	 *@return the recorded depth for this encounter
	 */
	public double getDepth(){return maximumDepthInMeters;}
	

	//public Vector getAdditionalImages() {return additionalImages;}
	
	/**Returns the file names of all images taken for this encounter.
	 *@return a vector of image name Strings
	 */
	public Vector getAdditionalImageNames() {return additionalImageNames;}
	
	/**Adds another image to the collection of images for this encounter.
	*These images should be the additional or non-side shots.
	*@param	imageFile	an additional image, converted to type Blob, to add to this encounter
	*@see com.poet.jdo.Blob, shepherd#makeBlobFromImageFile(File imageFile)
	*/
	public void addAdditionalImageName(String fileName) {
		additionalImageNames.add(fileName);
		//additionalImageNames.add(fileName);
		}
		
	public void approve() {
		approved=true;
		okExposeViaTapirLink=true;
		}
		
	public void resetAdditionalImageName(int position, String fileName) {
		additionalImageNames.set(position, fileName);
		//additionalImageNames.add(fileName);
		}
	
	/**Removes the specified additional image from this encounter.
	*@param	imageFile	the image to be removed from the additional images stored for this encounter
	*/
	public void removeAdditionalImageName(String imageFile) {
		for(int i=0;i<additionalImageNames.size();i++){
			String thisName=(String)additionalImageNames.get(i);
			if((thisName.equals(imageFile))||(thisName.indexOf("#")!=-1)){
				additionalImageNames.remove(i);
				i--;
				}
			}
		
		
		
		}
	
	
	/**Returns the unique encounter identifier number for this encounter.
	 *@return	a unique integer String used to identify this encounter in the database
	 *@see com.poet.jdo.Blob, shepherd#makeBlobFromImageFile(File imageFile)
	 */
	public String getEncounterNumber() {return catalogNumber;}
	
	/**Returns the date of this encounter.
	 *@return	a Date object
	 *@see java.util.Date
	 */
	public String getDate() {
		String date="";
		String time="";
		if(year==-1) {return "Unknown";}
		else if(month==-1) {return (new Integer(year)).toString();}
		
		if(hour!=-1) {
			time=(new Integer(hour)).toString()+":"+minutes;
		}

		if(day>0) {
		
			date=(new Integer(year)).toString()+"-"+(new Integer(month)).toString()+"-"+(new Integer(day)).toString()+" "+time;
		
		} else {
			
			date=(new Integer(year)).toString()+"-"+(new Integer(month)).toString()+" "+time;
		}
		
		return date;
		}
		
	public String getShortDate() {
		String date="";
		if(year==-1) {return "Unknown";}
		else if(month==-1) {return (new Integer(year)).toString();}
		if(day>0) {
		
			date=(new Integer(day)).toString()+"/"+(new Integer(month)).toString()+"/"+(new Integer(year)).toString();
		
		} else {
			
			date=(new Integer(month)).toString()+"/"+(new Integer(year)).toString();
		}
		
		return date;
		}
		
	/**Returns the String discussing how the size of this animal was approximated.
	 *@return	a String with text about how the size of this animal was estimated/measured
	 *
	 */
	public String getSizeGuess() {
		return size_guess;
		}
		
	public void setDay(int day) {
		this.day=day;
		}
		
	public void setHour(int hour) {
		this.hour=hour;
		}
		
	public void setMinutes(String minutes) {
		this.minutes=minutes;
		}
		
	public String getMinutes() {
		return minutes;
		}
		
	public int getHour() {
		return hour;
		}
		
	public void setMonth(int month) {
		this.month=month;
		}
	public void setYear(int year) {
		this.year=year;
		}
		
		
	public int getDay() {
		return day;
		}
		
	public int getMonth() {
		return month;
		}
	public int getYear() {
		return year;
		}
	
		
	/**Returns the String holding specific location data used for searching
	 *@return	the String holding specific location data used for searching
	 *
	 */
	public String getLocationCode() {
		return locationID;
		}
		
	/**Sets the String holding specific location data used for searching
	 */
	public void setLocationCode(String newLoc) {
		locationID=newLoc;
		}
		
	/**Returns the String holding specific location data used for searching
	 *@return	the String holding specific location data used for searching
	 *
	 */
	public String getDistinguishingScar() {
		return distinguishingScar;
		}
		
	/**Sets the String holding scarring information for the encounter
	 */
	public void setDistinguishingScar(String scar) {
		distinguishingScar=scar;
		}
		
	/**Sets the String documenting how the size of this animal was approximated.
	 */
	public void setSizeGuess(String newGuess) {
		size_guess=newGuess;
		}
	
	public String getMatchedBy(){
		if((identificationRemarks==null)||(identificationRemarks.equals(""))){return "Unknown";}
		return identificationRemarks;
	}
	
	public void setMatchedBy(String matchType) {
		identificationRemarks=matchType;
	}
	

	/**Sets the unique encounter identifier to be usd with this encounter.
	 *Once this is set, it cannot be changed without possible impact to the
	 *database structure.
	 *@param num the unique integer to be used to uniquely identify this encoun ter in the database
	 */
	public void setEncounterNumber(String num) {
		catalogNumber=num;
	}
	
	public String isAssignedToMarkedIndividual() {
		
		return individualID;
			
	}
	
	public void assignToMarkedIndividual(String sharky) {
		individualID=sharky;	
	}
	public boolean wasRejected() {

		return unidentifiable;	
	}
	
	public void reject() {
		unidentifiable=true;	
		//okExposeViaTapirLink=false;
	}
	
	public void reaccept() {
		unidentifiable=false;
		//okExposeViaTapirLink=true;
	}

	public String getGPSLongitude() {
		if(gpsLongitude==null) {return "";}
		else {return gpsLongitude;}	
	}
	
	public void setGPSLongitude(String newLong) {

		gpsLongitude=newLong;	
	}
	public String getGPSLatitude() {
		if(gpsLatitude==null) {return "";}
		else{return gpsLatitude;}
	}
	
	public void setGPSLatitude(String newLat) {
		gpsLatitude=newLat;	
	}
	
	/*public void setLatInteger(int newLat) {
		lat=newLat;	
	}

	public void setLongInteger(int newLong) {
		longitude=newLong;	
	}
	
	
	public int getLatInteger() {
			return lat;	
	}

	public int getLongInteger() {
		return longitude;	
	}
	*/
	
	public Encounter getClone() {
		Encounter tempEnc=new Encounter();
		try{
			tempEnc=(Encounter)this.clone();
		} catch(java.lang.CloneNotSupportedException e) {e.printStackTrace();}
		
		return tempEnc;	
	}
	
	public String getSpotImageFileName(){
		return spotImageFileName;
	}
	
	public void setSpotImageFileName(String name){
		spotImageFileName=name;
	}
	
	//-------------
	//for the right side spot image
	
	public String getRightSpotImageFileName(){
		return rightSpotImageFileName;
	}
	
	public void setRightSpotImageFileName(String name){
		rightSpotImageFileName=name;
	}
	
	//----------------
	
	
	
	public void setSubmitterID(String name){
		submitterID=name;
	}
	
	public String getSubmitterID(){
		return submitterID;
	}
	
	public Vector getInterestedResearchers() {
		return interestedResearchers;
	}
	
	public void addInterestedResearcher(String email) {
		interestedResearchers.add(email);
	}
	
	public boolean isApproved() {
		return approved;
	}
	
	public void removeInterestedResearcher(String email) {
		for(int i=0;i<interestedResearchers.size();i++){
			String rName=(String)interestedResearchers.get(i);
			if(rName.equals(email)){
				interestedResearchers.remove(i);
			}
		}
	}
	
		
	
	public double getRightmostSpot() {
		double rightest=0;
		for (int iter=0;iter<spots.size();iter++){
			if (spots.get(iter).getTheSpot().getCentroidX()>rightest) {rightest=spots.get(iter).getTheSpot().getCentroidX();}
			}
		return rightest;
	}
	
	public double getLeftmostSpot() {
		double leftest=getRightmostSpot();
		for (int iter=0;iter<spots.size();iter++){
			if (spots.get(iter).getTheSpot().getCentroidX()<leftest) {leftest=spots.get(iter).getTheSpot().getCentroidX();}
			}
		return leftest;
	}
		
	public double getHighestSpot() {
		double highest=getLowestSpot();
		for (int iter=0;iter<spots.size();iter++){
			if (spots.get(iter).getTheSpot().getCentroidY()<highest) {highest=spots.get(iter).getTheSpot().getCentroidY();}
			}
		return highest;
	}
	
	public double getLowestSpot() {
		double lowest=0;
		for (int iter=0;iter<spots.size();iter++){
			if (spots.get(iter).getTheSpot().getCentroidY()>lowest) {lowest=spots.get(iter).getTheSpot().getCentroidY();}
			}
		return lowest;
	}
	
	public com.reijns.I3S.Point2D[] getThreeLeftFiducialPoints() {
		com.reijns.I3S.Point2D[] Rray=new com.reijns.I3S.Point2D[3];
		if(getLeftReferenceSpots()!=null) {
			
			ArrayList<SuperSpot> refsLeft=getLeftReferenceSpots();
			
			Rray[0]=new com.reijns.I3S.Point2D(refsLeft.get(0).getTheSpot().getCentroidX(),refsLeft.get(0).getTheSpot().getCentroidY());
			Rray[1]=new com.reijns.I3S.Point2D(refsLeft.get(1).getTheSpot().getCentroidX(),refsLeft.get(1).getTheSpot().getCentroidY());
			Rray[2]=new com.reijns.I3S.Point2D(refsLeft.get(2).getTheSpot().getCentroidX(),refsLeft.get(2).getTheSpot().getCentroidY());
			System.out.println("	I found three left reference points!");
			
		}
		else {
			com.reijns.I3S.Point2D topLeft=new com.reijns.I3S.Point2D(getLeftmostSpot(),getHighestSpot());
			com.reijns.I3S.Point2D bottomLeft=new com.reijns.I3S.Point2D(getLeftmostSpot(),getLowestSpot());
			com.reijns.I3S.Point2D bottomRight=new com.reijns.I3S.Point2D(getRightmostSpot(),getLowestSpot());
			Rray[0]=topLeft;
			Rray[1]=bottomLeft;
			Rray[2]=bottomRight;
		}
		
		return Rray;
	} 
	
	public com.reijns.I3S.Point2D[] getThreeRightFiducialPoints() {
		com.reijns.I3S.Point2D[] Rray=new com.reijns.I3S.Point2D[3];
		if(getRightReferenceSpots()!=null) {
			ArrayList<SuperSpot> refsRight=getRightReferenceSpots();
			Rray[0]=new com.reijns.I3S.Point2D(refsRight.get(0).getTheSpot().getCentroidX(),refsRight.get(0).getTheSpot().getCentroidY());
			Rray[1]=new com.reijns.I3S.Point2D(refsRight.get(1).getTheSpot().getCentroidX(),refsRight.get(1).getTheSpot().getCentroidY());
			Rray[2]=new com.reijns.I3S.Point2D(refsRight.get(2).getTheSpot().getCentroidX(),refsRight.get(2).getTheSpot().getCentroidY());
			
		}
		else {
		
			com.reijns.I3S.Point2D topRight=new com.reijns.I3S.Point2D(getRightmostRightSpot(),getHighestRightSpot());
			com.reijns.I3S.Point2D bottomRight=new com.reijns.I3S.Point2D(getRightmostRightSpot(),getLowestRightSpot());
			com.reijns.I3S.Point2D bottomLeft=new com.reijns.I3S.Point2D(getLeftmostRightSpot(),getLowestRightSpot());
		
			Rray[0]=topRight;
			Rray[1]=bottomRight;
			Rray[2]=bottomLeft;
		}
		return Rray;
	} 
	
	public double getRightmostRightSpot() {
		double rightest=0;
		for (int iter=0;iter<rightSpots.size();iter++){
			if (rightSpots.get(iter).getTheSpot().getCentroidX()>rightest) {rightest=rightSpots.get(iter).getTheSpot().getCentroidX();}
			}
		return rightest;
	}
	
	
	public double getLeftmostRightSpot() {
		double leftest=getRightmostRightSpot();
		for (int iter=0;iter<rightSpots.size();iter++){
			if (rightSpots.get(iter).getTheSpot().getCentroidX()<leftest) {leftest=rightSpots.get(iter).getTheSpot().getCentroidX();}
			}
		return leftest;
	}
		
	public double getHighestRightSpot() {
		double highest=getLowestRightSpot();
		for (int iter=0;iter<rightSpots.size();iter++){
			if (rightSpots.get(iter).getTheSpot().getCentroidY()<highest) {highest=rightSpots.get(iter).getTheSpot().getCentroidY();}
			}
		return highest;
	}
	
	public double getLowestRightSpot() {
		double lowest=0;
		for (int iter=0;iter<rightSpots.size();iter++){
			if (rightSpots.get(iter).getTheSpot().getCentroidY()>lowest) {lowest=rightSpots.get(iter).getTheSpot().getCentroidY();}
			}
		return lowest;
	}
	


	public 	ArrayList<SuperSpot> getLeftReferenceSpots(){
		return leftReferenceSpots;
	}	
	
	public 	ArrayList<SuperSpot> getRightReferenceSpots(){
		return rightReferenceSpots;
	}
	
	public 	void setLeftReferenceSpots(ArrayList<SuperSpot> leftReferenceSpots){
		this.leftReferenceSpots=leftReferenceSpots;
	}
	
	public 	void setRightReferenceSpots(ArrayList<SuperSpot> rightReferenceSpots){
		this.rightReferenceSpots=rightReferenceSpots;
	}
	

	   
	   /**
	   * @param population array values to get the variance for
	   * @return the variance
	   */
	   public double variance(double[] population) {
	           long n = 0;
	           double mean = 0;
	           double s = 0.0;
	           
	           for (double x : population) {
	                   n++;
	                   double delta = x - mean;
	                   mean += delta / n;
	                   s += delta * (x - mean);
	           }
	           // if you want to calculate std deviation 
	           // of a sample change this to (s/(n-1))
	           //return (s / n);
	           return (s / (n-1));
	   }
	    
	   /**
	   * @param population array values to get the standard deviation for
	   * @return the standard deviation
	   */
	   public double standard_deviation(double[] population) {
	           return Math.sqrt(variance(population));
	   }  
	   
	
	

	   
		public void setNumLeftSpots(int numspots){numSpotsLeft=numspots;}
		public void setNumRightSpots(int numspots){numSpotsRight=numspots;}
		public void setDWCGlobalUniqueIdentifier(String guid){occurrenceID=guid;}
		public String getDWCGlobalUniqueIdentifier(){return occurrenceID;}
		
		public void setDWCImageURL(String link){dwcImageURL=link;}
		
		public String getDWCDateLastModified(){return modified;}
		public void setDWCDateLastModified(String lastModified){modified=lastModified;}
		
		public String getDWCDateAdded(){return dwcDateAdded;}
		public void setDWCDateAdded(String m_dateAdded){dwcDateAdded=m_dateAdded;}
		
		//public void setDateAdded(long date){dateAdded=date;}
		//public long getDateAdded(){return dateAdded;}
		
		public void setDWCDecimalLatitude(double lat){
			if(lat==-9999.0){
				decimalLatitude=null;
			}
			else{
				decimalLatitude=(new Double(lat)).toString();
			}
		}
		public String getDWCDecimalLatitude(){return decimalLatitude;}
		public void setDWCDecimalLongitude(double longit){
			if(longit==-9999.0){
				decimalLongitude=null;
			}
			else{
				decimalLongitude=(new Double(longit)).toString();
			}
		}
		public String getDWCDecimalLongitude(){return decimalLongitude;}
		
		public boolean getOKExposeViaTapirLink(){return okExposeViaTapirLink;}
		
		public void setOKExposeViaTapirLink(boolean ok){
			okExposeViaTapirLink=ok;
		}
		
		public void setAlternateID(String newID){
			this.otherCatalogNumbers=newID;
		}
		
		public String getAlternateID(){
			if(otherCatalogNumbers==null){return "None";}
			return otherCatalogNumbers;
		}
		
		public String getInformOthers(){
		  if(informothers==null){return "";}
		  return informothers;
		 }
		
		public void setInformOthers(String others){this.informothers=others;}
		
		public String getLocationID(){return locationID;}
		public void setLocationID(String newLocationID){this.locationID=newLocationID;}
		
		public double getMaximumDepthInMeters(){return maximumDepthInMeters;}
		public void setMaximumDepthInMeters(double newDepth){this.maximumDepthInMeters=newDepth;}
		
		public double getMaximumElevationInMeters(){return maximumElevationInMeters;}
		public void setMaximumElevationInMeters(double newElev){this.maximumElevationInMeters=newElev;}
		
		
		public String getCatalogNumber(){return catalogNumber;}
		public void setCatalogNumber(String newNumber){this.catalogNumber=newNumber;}
		
		public String getVerbatimLocality(){return verbatimLocality;}
		public void setVerbatimLocality(String vlcl){this.verbatimLocality=vlcl;}
		
		public String getIndividualID(){return individualID;}
		public void setIndividualID(String indy){this.individualID=indy;}
		
		public String getDecimalLatitude(){return decimalLatitude;}
		public void setDecimalLatitude(String lat){this.decimalLatitude=lat;}
		
		public String getDecimalLongitude(){return decimalLongitude;}
		public void setDecimalLongitude(String longy){this.decimalLongitude=longy;}
		
		public String getOccurrenceRemarks(){return occurrenceRemarks;}
		public void setOccurrenceRemarks(String remarks){this.occurrenceRemarks=remarks;}
		
		public String getRecordedBy(){return recordedBy;}
		public void setRecordedBy(String submitterName){this.recordedBy=submitterName;}
		
		public String getOtherCatalogNumbers(){return otherCatalogNumbers;}
		public void setOtherCatalogNumbers(String otherNums){this.otherCatalogNumbers=otherNums;}
		
		public String getLivingStatus(){return livingStatus;}
		public void setLivingStatus(String status){this.livingStatus=status;}
	
		
    public String getBehavior(){return behavior;}
    public void setBehavior(String beh){
      this.behavior=beh;
    }
    
    public String getEventID(){return eventID;}
    public void setEventID(String id){
      this.eventID=id;
    }
		
    public String getVerbatimEventDate(){return verbatimEventDate;}
    public void setVerbatimEventDate(String dt){
      this.verbatimEventDate=dt;
    }
    
    public String getDynamicProperties(){
      return dynamicProperties;
    }
    public void setDynamicProperty(String name, String value){
      name=name.replaceAll(";", "_").trim().replaceAll("%20", " ");
      value=value.replaceAll(";", "_").trim();
      
      if(dynamicProperties==null){dynamicProperties=name+"="+value+";";}
      else{
        
        //let's create a TreeMap of the properties
        TreeMap<String,String> tm=new TreeMap<String,String>();
        StringTokenizer st=new StringTokenizer(dynamicProperties, ";");
        while(st.hasMoreTokens()){
          String token = st.nextToken();
          int equalPlace=token.indexOf("=");
          tm.put(token.substring(0,equalPlace), token.substring(equalPlace+1));
        }
        if(tm.containsKey(name)){
          tm.remove(name);
          tm.put(name, value);
          
          //now let's recreate the dynamicProperties String
          String newProps=tm.toString();
          int stringSize=newProps.length();
          dynamicProperties=newProps.substring(1,(stringSize-1)).replaceAll(", ", ";")+";";
        }
        else{
          dynamicProperties=dynamicProperties+name+"="+value+";";
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
          tm.put(token.substring(0,equalPlace), token.substring(equalPlace+1));
        }
        if(tm.containsKey(name)){return tm.get(name);}
      }
      return null;
    }
    
    public void removeDynamicProperty(String name){
      name=name.replaceAll(";", "_").trim().replaceAll("%20", " ");
      if(dynamicProperties!=null){
        
        //let's create a TreeMap of the properties
        TreeMap<String,String> tm=new TreeMap<String,String>();
        StringTokenizer st=new StringTokenizer(dynamicProperties, ";");
        while(st.hasMoreTokens()){
          String token = st.nextToken();
          int equalPlace=token.indexOf("=");
          tm.put(token.substring(0,(equalPlace)), token.substring(equalPlace+1));
        }
        if(tm.containsKey(name)){
          tm.remove(name);
          
          //now let's recreate the dynamicProperties String
          String newProps=tm.toString();
          int stringSize=newProps.length();
          dynamicProperties=newProps.substring(1,(stringSize-1)).replaceAll(", ", ";")+";";
        }
      }
    }
    
    
    public String getIdentificationRemarks(){return identificationRemarks;}
    
    
}
	
	