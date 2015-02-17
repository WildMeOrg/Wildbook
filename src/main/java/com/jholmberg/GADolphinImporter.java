package com.jholmberg;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.hssf.usermodel.*;
import org.ecocean.*;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.util.StringTokenizer;

import java.io.*;

public class GADolphinImporter {
	
	public static void main(String[] args) {
		
		String myFilename=args[0];
		File myFile=new File(myFilename);
		
		String imagesDirName=args[1];
		File imagesDir=new File(imagesDirName);
		
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
					Workbook wb = new HSSFWorkbook(inp);
					
					//let's get our sheets
					Sheet sightingLog = wb.getSheet("Sightling log");
					Sheet idLog = wb.getSheet("ID log");
					Sheet surveyLog = wb.getSheet("Survey log");
					
					//let's do sightings
				    for (Row row : surveyLog) {
				    	
				    	Cell cell1Date = row.getCell(1);
				    	Cell cell2SightingNumber = row.getCell(2);
				    	
				    	if((cell1Date.toString()!=null)&&(!cell1Date.toString().trim().equals(""))){
				    	
				    		
				    		//ok, we have a sighting date and sighting #- we can make an Encounter
				    		String stringDate= cell1Date.toString();
				    		String occurID = stringDate.trim()+"."+cell2SightingNumber.toString().trim();
				    		String team=row.getCell(0).toString();
				    		String timeString=row.getCell(3).toString();
				    		
				    		//dates are in M.D.Y format
				    		StringTokenizer str=new StringTokenizer(stringDate,".");
				    		int month=(new Integer(str.nextToken())).intValue();
				    		int day=(new Integer(str.nextToken())).intValue();
				    		int year=2000+(new Integer(str.nextToken())).intValue();
				    		int hour=0;
				    		String minutes="";
				    		
				    		//time
				    		if(timeString.length()==3){
				    			hour=(new Integer(timeString.substring(0,0))).intValue();
				    			minutes=timeString.substring(1,2);
				    		}
				    		else if(timeString.length()==4){
				    			hour=(new Integer(timeString.substring(0,1))).intValue();
				    			minutes=timeString.substring(2,3);
				    		}
				    		
				    		String gpsLatitude=row.getCell(5).toString();
				    		String gpsLongitude=row.getCell(6).toString();
				    		Double gpsLat=new Double(gpsLatitude);
				    		Double gpsLong=new Double(gpsLongitude);
				    		
				    		
				    		for (Row idrow : idLog) {
				    			
				    			Cell idDate = idrow.getCell(1);
						    	Cell idSightingNumber = idrow.getCell(2);
						    	
						    	if((idDate.toString()!=null)&&(!idDate.toString().trim().equals(""))){
				    			
						    		String idStringDate= idDate.toString();
						    		String thisSightingID = idStringDate.trim()+"."+idSightingNumber.toString().trim();
						    		
						    		if(thisSightingID.equals(occurID)){
						    			
						    			String prelimFinCode=idrow.getCell(3).toString();
						    			String myID="";
						    			String myNickname="";
						    			
						    			if((idrow.getCell(7)!=null)&&(!idrow.getCell(6).toString().trim().equals(""))){
						    				myID=idrow.getCell(7).toString().trim();
						    			}
						    			if((idrow.getCell(6)!=null)&&(!idrow.getCell(7).toString().trim().equals(""))){
						    				myNickname=idrow.getCell(6).toString().trim();
						    			}
						    			
						    			
						    		
						    			Encounter enc=new Encounter();
						    			
					    		
					    				//populate sighting date-time
					    				enc.setYear(year);
					    				enc.setMonth(month);
					    				enc.setDay(day);
					    				enc.setHour(hour);
					    				enc.setMinutes(minutes);
					    		
					    				//gps lat and long
					    				enc.setDWCDecimalLatitude(gpsLat);
					    				enc.setDWCDecimalLongitude(gpsLong);
					    		
					    				//populate alternate ID
					    				enc.setAlternateID(occurID);
					    		
					    				//set team
					    				enc.setSubmitterOrganization(team);
					    		
					    	    		//populate DarwinCore dates
					    	    		LocalDateTime dt = new LocalDateTime();
					    	    		DateTimeFormatter fmt = ISODateTimeFormat.date();
					    	    		String strOutputDateTime = fmt.print(dt);
					    	    		enc.setDWCDateAdded(strOutputDateTime);
					    	    		enc.setDWCDateAdded(new Long(dt.toDateTime().getMillis()));
					    	    		System.out.println("     I set the date as a LONG to: "+enc.getDWCDateAddedLong());
					    	    		enc.setDWCDateLastModified(strOutputDateTime);
					    	    		
					    	    		//prlim fin code
					    	    		enc.setDynamicProperty("Prelim fin code", prelimFinCode);
					    	    		
					    	    		//catalog number
					    	    		enc.setCatalogNumber(thisSightingID+prelimFinCode.replaceAll("" , ""));
					    	    		
					    	    		
					    	    		//add comments
					    	    		enc.addComments("Data imported for Georgia Aquarium.");
					    	    
					    	    		
					    	    		//finish persistence
					    	    		myShepherd.beginDBTransaction();
					    	    		myShepherd.getPM().makePersistent(enc);
					    	    		myShepherd.commitDBTransaction();
					    	    		
					    	    		
					    	    		
					    	    		//populate images
					    	    		//TBD
					    	    		
					    	    		
					    	    		
					    	    		//populate ID
					    	    		if(!myID.equals("")){
					    	    			myShepherd.beginDBTransaction();
					    	    			MarkedIndividual thisIndy=new MarkedIndividual();
					    	    			if(myShepherd.isMarkedIndividual(myID)){
					    	    				thisIndy=myShepherd.getMarkedIndividual(myID);
					    	    			}
					    	    			else{
					    	    				myShepherd.getPM().makePersistent(thisIndy);
					    	    				thisIndy.setIndividualID(myID);
					    	    			}
					    	    			enc.setIndividualID(myID);
					    	    			thisIndy.addEncounter(enc, context);
					    	    			
					    	    			if(!myNickname.equals("")){
					    	    				thisIndy.setNickName(myNickname);
					    	    			}
					    	    			
					    	    			myShepherd.commitDBTransaction();
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
					    	    		
					    	    		myShepherd.commitDBTransaction();
					    	    		
					    	    		
					    	    
						    		}
					    		
				    			
						    	}
				    			
				    			
				    			
				    			
				    		}
				    		
				    		
				    		
				    		
				    	    
				    		
				    		
				    	}
				    	
				    	
				        
				        
				        
				    }
				    
				    
					
				
				
				
				}
				catch(Exception e){
					System.out.println("Exception on filestream for: " +imagesDirName);
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
	

}
