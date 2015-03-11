package com.jholmberg;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.ecocean.*;
import org.ecocean.servlet.ServletUtilities;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.apache.commons.io.*;

import java.io.*;
import java.util.*;

import org.ecocean.servlet.ServletUtilities;

public class GADolphinImporter {
	
	public static void main(String[] args) {
		
		String myFilename=args[0];
		File myFile=new File(myFilename);
		
		String imagesDirName=args[1];
		File imagesDir=new File(imagesDirName);
		
		String dataDirPath = args [2];
		File datatDir=new File(dataDirPath);
		
		String context="context0";
		
		// Get a shepherd
		Shepherd myShepherd=new Shepherd(context);
		
		
		
		
		
		if(myFile.exists()){
			if(imagesDir.exists()){
			
				//alright, we have an images directory and a data file.
				try{
					
					//load 'er up!
					FileInputStream inp = new FileInputStream(myFile);
			
					// create a new workbook
					Workbook wb = new XSSFWorkbook(inp);
					
					//let's get our sheets
					Sheet sightingLog = wb.getSheet("Sighting log");
					Sheet idLog = wb.getSheet("ID log");
					Sheet surveyLog = wb.getSheet("Survey log");
					
					int count=0;
					
					//let's do sightings
				    for (Row row : sightingLog) {
				    	
				    	//ignore first header row
				    	if(count>0){
				    		
				    	System.out.println("Starting sighting row: "+count);
				    	
				    	Cell cell1Date = row.getCell(1);
				    	Cell cell2SightingNumber = row.getCell(2);
				    	
				    	String encounterIDExtra="";
				    	
				    	if((cell1Date.toString()!=null)&&(!cell1Date.toString().trim().equals(""))){
				    	
				    		
				    		//ok, we have a sighting date and sighting #- we can make an Encounter
				    		String stringDate= cell1Date.toString();
				    		
				    		if(stringDate.indexOf(" ")!=-1){
				    			int placementSpace = stringDate.indexOf(" ");
				    			encounterIDExtra=stringDate.substring(placementSpace).trim();
				    			stringDate=stringDate.substring(0,placementSpace);
				    			
				    		}
				    		
				    		String occurID = stringDate.trim()+"."+cell2SightingNumber.toString().trim();
				    		String team=row.getCell(0).toString();
				    		String timeString=row.getCell(3).toString().replaceAll(":", "").replaceAll("AM", "").replaceAll("PM", "").replaceAll(":00 ","").trim();
				    		if(timeString.indexOf(".")!=-1){
				    			int marker=timeString.indexOf(".");
				    			timeString=timeString.substring(0,marker);
				    		}
				    		
				    		
				    		//dates are in M.D.Y format
				    		StringTokenizer str=new StringTokenizer(stringDate,".");
				    		int month=(new Integer(str.nextToken())).intValue();
				    		int day=(new Integer(str.nextToken())).intValue();
				    		int year=2000+(new Integer(str.nextToken())).intValue();
				    		int hour=-1;
				    		String minutes="00";
				    		
				    		
				    		System.out.println("     @@@@@: I see time string as "+timeString);
				    		//time
				    		if(timeString.length()==3){
				    			hour=(new Integer(timeString.substring(0,1))).intValue();
				    			minutes=timeString.substring(1,3);
				    			System.out.println("     @@@@@: Setting time as: "+hour+":"+minutes);
				    		}
				    		else if(timeString.length()==4){
				    			hour=(new Integer(timeString.substring(0,2))).intValue();
				    			minutes=timeString.substring(2,4);
				    			System.out.println("     @@@@@: Setting time as: "+hour+":"+minutes);
				    		}
				    		
				    		
				    		
				    		int count2=0;
				    		
				    		for (Row idrow : idLog) {
				    			
				    			//ignore first header row
				    			if(count2>0){
				    			
				    			Cell idDate = idrow.getCell(1);
						    	Cell idSightingNumber = idrow.getCell(2);
						    	
						    	if((idDate!=null) && (idDate.toString()!=null)&&(!idDate.toString().trim().equals(""))){
				    			
						    		String idStringDate= idDate.toString();
						    		if(idStringDate.indexOf(" ")!=-1){
						    			int placementSpace = idStringDate.indexOf(" ");
						    			idStringDate=idStringDate.substring(0,placementSpace);
						    		}
						    		String thisSightingID = idStringDate.trim()+"."+idSightingNumber.toString().trim();
						    		
						    		if(thisSightingID.equals(occurID)){
						    			
						    					
						    			String myID="";
						    			String myNickname="";
						    			
						    			if((idrow.getCell(7)!=null)&&(!idrow.getCell(7).toString().trim().equals(""))){
						    				myID=idrow.getCell(7).toString().trim();
						    			}
						    			if((idrow.getCell(6)!=null)&&(!idrow.getCell(6).toString().trim().equals(""))){
						    				myNickname=idrow.getCell(6).toString().trim();
						    			}
						    			
						    			
						    			
					    	    		//determine prelim fin code
					    	    		String prelimFinCode="";
						    			if((idrow.getCell(3)!=null)&&(!idrow.getCell(3).toString().trim().equals(""))){
						    				prelimFinCode=idrow.getCell(3).toString();
						    				//enc.setDynamicProperty("Prelim fin code", prelimFinCode);
						    			}
						    			
						    		
						    			Encounter enc=new Encounter();
						    			
						    			
						    			
					    	    		//catalog number
					    	    		String proposedCatalogNumber=thisSightingID+encounterIDExtra+prelimFinCode.replaceAll(" " , "");
					    	    		if(myShepherd.isEncounter(proposedCatalogNumber)){
					    	    			int iter=0;
					    	    			while(myShepherd.isEncounter((proposedCatalogNumber+"_"+iter))){
					    	    				iter++;
					    	    			}
					    	    			proposedCatalogNumber+=("_"+iter);
					    	    		}
					    	    		
					    	    		System.out.println("     Defining encounter number: "+enc.getCatalogNumber());
					    	    		boolean isNewEncounter=false;
					    	    		
					    	    		if(myShepherd.isEncounter(proposedCatalogNumber)){
					    	    			enc=myShepherd.getEncounter(proposedCatalogNumber);
					    	    		}
					    	    		else{enc.setCatalogNumber(proposedCatalogNumber);isNewEncounter=true;}
					    	    		
					    	    		
					    	    		//set prelim fin code
					    	    		if((idrow.getCell(3)!=null)&&(!idrow.getCell(3).toString().trim().equals(""))){
						    				enc.setDynamicProperty("Prelim fin code", prelimFinCode);
						    			}
					    	    		
						    			
						    			enc.setState("approved");
						    			enc.setIndividualID("Unassigned");
						    			
					    		
					    				//populate sighting date-time
					    				enc.setYear(year);
					    				enc.setMonth(month);
					    				enc.setDay(day);
					    				if(hour >-1){
					    					enc.setHour(hour);
					    					enc.setMinutes(minutes);
					    				}
					    		
					    				//gps lat and long
					    				if((row.getCell(5)!=null)&&(row.getCell(6)!=null)&&(!row.getCell(5).toString().replaceAll("~", "").equals(""))&&(!row.getCell(6).toString().replaceAll("~", "").equals(""))){
					    					String gpsLatitude=row.getCell(5).toString().replaceAll("~", "");
					    					String gpsLongitude=row.getCell(6).toString().replaceAll("~", "");
					    					Double gpsLat=new Double(gpsLatitude);
					    					Double gpsLong=new Double(gpsLongitude);
					    					enc.setDWCDecimalLatitude(gpsLat);
					    					enc.setDWCDecimalLongitude(gpsLong*-1);
					    				}
					    				
					    				//rows M through AM in sightingLog
					    				
					    				
					    				
					    				if((row.getCell(12)!=null)&&(!row.getCell(12).toString().equals(""))){
					    					String heading=row.getCell(12).toString();
					    					enc.setDynamicProperty("Initial Heading", heading);
					    					
					    				}
					    				if((row.getCell(13)!=null)&&(!row.getCell(13).toString().equals(""))){
					    					String mill=row.getCell(13).toString();
					    					enc.setDynamicProperty("Mill", mill);
					    					
					    				}
					    				if((row.getCell(14)!=null)&&(!row.getCell(14).toString().equals(""))){
					    					String value=row.getCell(14).toString();
					    					enc.setDynamicProperty("Feed", value);
					    					
					    				}
					    				if((row.getCell(15)!=null)&&(!row.getCell(15).toString().equals(""))){
					    					String value=row.getCell(15).toString();
					    					enc.setDynamicProperty("Prob Feed", value);
					    					
					    				}
					    				if((row.getCell(16)!=null)&&(!row.getCell(16).toString().equals(""))){
					    					String value=row.getCell(16).toString();
					    					enc.setDynamicProperty("Travel", value);
					    					
					    				}
					    				if((row.getCell(17)!=null)&&(!row.getCell(17).toString().equals(""))){
					    					String value=row.getCell(17).toString();
					    					enc.setDynamicProperty("Play", value);
					    					
					    				}
					    				if((row.getCell(18)!=null)&&(!row.getCell(18).toString().equals(""))){
					    					String value=row.getCell(18).toString();
					    					enc.setDynamicProperty("Rest", value);
					    					
					    				}
					    				if((row.getCell(19)!=null)&&(!row.getCell(19).toString().equals(""))){
					    					String value=row.getCell(19).toString();
					    					enc.setDynamicProperty("Social", value);
					    					
					    				}
					    				if((row.getCell(20)!=null)&&(!row.getCell(20).toString().equals(""))){
					    					String value=row.getCell(20).toString();
					    					enc.setDynamicProperty("Other", value);
					    					
					    				}
					    				if((row.getCell(21)!=null)&&(!row.getCell(21).toString().equals(""))){
					    					String value=row.getCell(21).toString();
					    					enc.setDynamicProperty("Field Estimate Total Min", value);
					    					
					    				}
					    				if((row.getCell(22)!=null)&&(!row.getCell(22).toString().equals(""))){
					    					String value=row.getCell(22).toString();
					    					enc.setDynamicProperty("Field Estimate Total Max", value);
					    					
					    				}
					    				if((row.getCell(23)!=null)&&(!row.getCell(23).toString().equals(""))){
					    					String value=row.getCell(23).toString();
					    					enc.setDynamicProperty("Field Estimate Total Best", value);
					    					
					    				}
					    				if((row.getCell(24)!=null)&&(!row.getCell(24).toString().equals(""))){
					    					String value=row.getCell(24).toString();
					    					enc.setDynamicProperty("FE Calves Min", value);
					    					
					    				}
					    				if((row.getCell(25)!=null)&&(!row.getCell(25).toString().equals(""))){
					    					String value=row.getCell(25).toString();
					    					enc.setDynamicProperty("FE Calves Max", value);
					    					
					    				}
					    				if((row.getCell(26)!=null)&&(!row.getCell(26).toString().equals(""))){
					    					String value=row.getCell(26).toString();
					    					enc.setDynamicProperty("FE Calves Best", value);
					    					
					    				}
					    				if((row.getCell(27)!=null)&&(!row.getCell(27).toString().equals(""))){
					    					String value=row.getCell(27).toString();
					    					enc.setDynamicProperty("FE YOY Min", value);
					    					
					    				}
					    				if((row.getCell(28)!=null)&&(!row.getCell(28).toString().equals(""))){
					    					String value=row.getCell(28).toString();
					    					enc.setDynamicProperty("FE YOY Max", value);
					    					
					    				}
					    				if((row.getCell(29)!=null)&&(!row.getCell(29).toString().equals(""))){
					    					String value=row.getCell(29).toString();
					    					enc.setDynamicProperty("FE YOY Best", value);
					    					
					    				}
					    				if((row.getCell(30)!=null)&&(!row.getCell(30).toString().equals(""))){
					    					String value=row.getCell(30).toString();
					    					enc.setDynamicProperty("PhotoID Total Min", value);
					    					
					    				}
					    				if((row.getCell(31)!=null)&&(!row.getCell(31).toString().equals(""))){
					    					String value=row.getCell(31).toString();
					    					enc.setDynamicProperty("PhotoID Total Max", value);
					    					
					    				}
					    				if((row.getCell(32)!=null)&&(!row.getCell(32).toString().equals(""))){
					    					String value=row.getCell(32).toString();
					    					enc.setDynamicProperty("PhotoID Total Best", value);
					    					
					    				}
					    				if((row.getCell(33)!=null)&&(!row.getCell(33).toString().equals(""))){
					    					String value=row.getCell(33).toString();
					    					enc.setDynamicProperty("PhotoID Calves Min", value);
					    					
					    				}
					    				if((row.getCell(34)!=null)&&(!row.getCell(34).toString().equals(""))){
					    					String value=row.getCell(34).toString();
					    					enc.setDynamicProperty("PhotoID Calves Max", value);
					    					
					    				}
					    				if((row.getCell(35)!=null)&&(!row.getCell(35).toString().equals(""))){
					    					String value=row.getCell(35).toString();
					    					enc.setDynamicProperty("PhotoID Calves Best", value);
					    					
					    				}
					    				if((row.getCell(36)!=null)&&(!row.getCell(36).toString().equals(""))){
					    					String value=row.getCell(36).toString();
					    					enc.setDynamicProperty("PhotoID YOY Min", value);
					    					
					    				}
					    				if((row.getCell(37)!=null)&&(!row.getCell(37).toString().equals(""))){
					    					String value=row.getCell(37).toString();
					    					enc.setDynamicProperty("PhotoID YOY Max", value);
					    					
					    				}
					    				if((row.getCell(38)!=null)&&(!row.getCell(38).toString().equals(""))){
					    					String value=row.getCell(38).toString();
					    					enc.setDynamicProperty("PhotoID YOY Best", value);
					    					
					    				}
					    				
					    		
					    				//populate alternate ID
					    				enc.setAlternateID(occurID);
					    		
					    				//set team
					    				enc.setSubmitterOrganization(team);
					    		
					    	    		//populate DarwinCore dates
					    	    		LocalDateTime dt = new LocalDateTime();
					    	    		DateTimeFormatter fmt = ISODateTimeFormat.date();
					    	    		String strOutputDateTime = fmt.print(dt);
					    	    		if(isNewEncounter){
					    	    			enc.setDWCDateAdded(strOutputDateTime);
					    	    			enc.setDWCDateAdded(new Long(dt.toDateTime().getMillis()));
					    	    		}
					    	    		
					    	    		System.out.println("     I set the date as a LONG to: "+enc.getDWCDateAddedLong());
					    	    		enc.setDWCDateLastModified(strOutputDateTime);
					    	    		

						    			
					    	    		
					    	    		
					    	    		//sighting number
						    			String intSightingNumber = idSightingNumber.toString().trim();
						    			if(intSightingNumber.indexOf(".")!=-1){
						    				int position= intSightingNumber.indexOf(".");
						    				intSightingNumber=intSightingNumber.substring(0,position);
						    			}
					    	    		enc.setDynamicProperty("Sighting Number", intSightingNumber);
					    	    		//TBD - make this an int as a string
					    	    		

					    	    		
					    	    		//add comments
					    	    		if(isNewEncounter){
					    	    			enc.addComments("Data imported for Georgia Aquarium.");
					    	    		}
					    	    		
					    	    		//genus species
					    	    		enc.setGenus("Tursiops");
					    	    		enc.setSpecificEpithet("truncatus");
					    	    		
					    	    		//set user
					    	    		enc.setSubmitterID("MDenny");
					    	    		
					    	    		//finish persistence
					    	    		myShepherd.beginDBTransaction();
					    	    		if(!myShepherd.isEncounter(enc.getCatalogNumber())){
					    	    			myShepherd.getPM().makePersistent(enc);
					    	    			System.out.println("Creating encounter: "+enc.getCatalogNumber());
					    	    		}
					    	    		myShepherd.commitDBTransaction();
					    	    		
					    	    		
					    	    		
					    	    		
					    	    		
					    	    		
					    	    		
					    	    		//populate ID
					    	    		if(!myID.trim().equals("")){
					    	    			
					    	    			
					    	    			
					    	    			
					    	    			myShepherd.beginDBTransaction();
					    	    			MarkedIndividual thisIndy=new MarkedIndividual();
					    	    			if(myShepherd.isMarkedIndividual(myID.trim())){
					    	    				thisIndy=myShepherd.getMarkedIndividual(myID.trim());
					    	    				System.out.println("     Loading existing individual: "+myID);
					    	    			}
					    	    			else{
					    	    				System.out.println("     Making new individual: "+myID);
					    	    				thisIndy.setIndividualID(myID.trim());
					    	    				
					    	    				myShepherd.getPM().makePersistent(thisIndy);
					    	    				
					    	    			}
					    	    			
					    	    			//let's check and see if we have to remove this encounter from a markedindividual first
					    	    			if(!isNewEncounter){
					    	    				
					    	    				//this is an encounter we have imported before
					    	    				if(!enc.getIndividualID().equals(myID.trim())){
					    	    					
					    	    					//ok, the new ID does not match the old. we are migrating this Encounter to a new Marked Individual
					    	    					if((myShepherd.isMarkedIndividual(enc.getIndividualID()))){
					    	    						MarkedIndividual oldIndy = myShepherd.getMarkedIndividual(enc.getIndividualID());
					    	    						oldIndy.removeEncounter(enc, context);
					    	    						if(oldIndy.getEncounters().size()==0){myShepherd.getPM().deletePersistent(oldIndy);}
					    	    						myShepherd.commitDBTransaction();
					    	    						myShepherd.beginDBTransaction();
					    	    					}
					    	    					
					    	    				}
					    	    				
					    	    			}
					    	    			
					    	    			enc.setIndividualID(myID.trim());
					    	    			System.out.println("     Setting ID: "+myID);
					    	    			thisIndy.addEncounter(enc, context);
					    	    			
					    	    			if(!myNickname.equals("")){
					    	    				thisIndy.setNickName(myNickname);
					    	    			}
					    	    			
					    	    			myShepherd.commitDBTransaction();
					    	    			
					    	    			//populate images
					    	    			//images are based on ID name, so we have to do them here
						    	    		populateImagesForEncounter(enc, imagesDir, myShepherd, myID, dataDirPath);
					    	    			
					    	    		}
					    	    		
					    	    		
					    	    		
					    	    		
					    	    		//checkOccurrence and persist if necessary
					    	    		myShepherd.beginDBTransaction();
					    	    		Occurrence thisOccur=new Occurrence();
					    	    		if(myShepherd.isOccurrence("GACFS_"+occurID)){
					    	    			thisOccur=myShepherd.getOccurrence("GACFS_"+occurID);
					    	    			thisOccur.addEncounter(enc);
					    	    		}
					    	    		else{
					    	    			thisOccur=new Occurrence("GACFS_"+occurID,enc);
					    	    			myShepherd.getPM().makePersistent(thisOccur);
					    	    		}
					    	    		enc.setOccurrenceID(occurID);
					    	    		System.out.println("     Setting Occurrence: "+occurID);
					    	    		
					    	    		myShepherd.commitDBTransaction();
					    	    		
					    	    		
					    	    
						    		}
					    		
				    			
						    	}
				    			
				    			
				    		}
				    		count2++;
				    			
				    		}
				    		
				    		
				    		
				    		
				    	    
				    		
				    		
				    	}
				    	
				    	}
				        
				        count++;
				        
				    }
				    
				    
					
				    inp.close();
				
				
				}
				catch(Exception e){
					System.out.println("Exception on filestream for: " +imagesDirName);
					e.printStackTrace();
					myShepherd.closeDBTransaction();
					System.exit(1);
				}
			
			
			}
			else{
				System.out.println("Could not load images dir: " +myFilename);
				System.exit(1);
			}
			
		}
		else{
			System.out.println("Could not load Excel data file: " +args[0]);
			System.exit(1);
		}
		
	}
	
	private static void populateImagesForEncounter(Encounter enc, File imagesDir, Shepherd myShepherd, String myID, String dataDirPath){
		
		//wipe out previous photos
		int numPhotos=myShepherd.getAllSinglePhotoVideosForEncounter(enc.getCatalogNumber()).size();
		for(int i=0;i<numPhotos;i++){
			enc.removeSinglePhotoVideo(i);
			myShepherd.commitDBTransaction();
			myShepherd.beginDBTransaction();
		}
		
		
		System.out.println("     @@@@@Entering photo processing...");
		
		//ok, time to see if we have an image
		String[] supportedExtensions=new String[4];
		supportedExtensions[0]="jpg";
		supportedExtensions[1]="JPG";
		supportedExtensions[2]="jpeg";
		supportedExtensions[3]="JPEG";
		 Collection<File> jpgFiles= FileUtils.listFiles(imagesDir,supportedExtensions,true);
		Iterator<File> it=jpgFiles.iterator();
		while(it.hasNext()){
			
			
			File thisFile=it.next();
			String thisFilename=thisFile.getName().toLowerCase();
			//System.out.println("          &&&&&Iterating filenames: "+thisFilename);
			if(thisFilename.startsWith(myID.toLowerCase())){
				
				
				
				myShepherd.beginDBTransaction();
				
				String year=(new Integer(enc.getYear())).toString();
				String month=(new Integer(enc.getMonth())).toString();
				if(month.length()==1){month="0"+month;}
				String day=(new Integer(enc.getDay())).toString();
				if(day.length()==1){day="0"+day;}
				
				String sightingNumber="s"+enc.getDynamicPropertyValue("Sighting Number");
				String consolidatedString=year+" "+month+" "+day+" "+sightingNumber;
				
				//System.out.println("          &&&&&Comparing: "+thisFilename+" and "+consolidatedString);
				//try{
				//	System.in.read();
				//}
				//catch(Exception e){}
				
				if(thisFilename.indexOf(consolidatedString)!=-1){
					
					//now copy the file into Wildbook
					File dir = new File(enc.dir(dataDirPath));
					if (!dir.exists()) { dir.mkdirs(); }

					//String origFilename = new File(formFile.getName()).getName();
					//String filename = ServletUtilities.cleanFileName(new File(formFile.getName()).getName());

					File destfile = new File(dir, ServletUtilities.cleanFileName(thisFile.getName()));
					
					if(!destfile.exists()){
						String fullFileSystemPath = destfile.getAbsolutePath();
						CaribwhaleMigratorApp.copyFile(thisFile,destfile);
						System.out.println("   &&&&copying file to: full path??? = " + fullFileSystemPath + " WRITTEN!");
					}
					
					
					//we have a match! let's add it to the encounter
					SinglePhotoVideo spv=new SinglePhotoVideo(enc.getCatalogNumber(),destfile);
					myShepherd.getPM().makePersistent(spv);
					
					
					
					enc.addSinglePhotoVideo(spv);
					
					myShepherd.commitDBTransaction();
					
					

					
					
					
				}
				else{
	    		
					myShepherd.rollbackDBTransaction();
				}
				
				
			}
			
		}
		
		
		return;
	}
	
	

}
