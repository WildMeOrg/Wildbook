/**
 *
 */
package com.jholmberg;

//import the Wildbook Framework
import org.ecocean.*;
import org.ecocean.servlet.*;
import org.ecocean.genetics.*;
import org.ecocean.social.*;

import org.ecocean.servlet.ServletUtilities;

//import basic IO
import java.io.*;
import java.util.*;
import java.net.*;

//import date-time formatter for the custom date format
import org.joda.time.DateTime;
import org.joda.time.format.*;

//import jackcess
import com.healthmarketscience.jackcess.*;

//import jexcel
import jxl.*;
import jxl.read.biff.BiffException;

import java.util.TreeMap;


/**
 * @author jholmber
 *
 */
public class CaribwhaleMigratorApp {

	/**
	 * @param args
	 */


	public static void main(String[] args) {


		//initial environment config
		//these eventually need to be loaded from a .properties file in the classpath
		String pathToAccessFile="/home/webadmin/caribwhale/AtlanticCatalogue.mdb";
		String encountersDirPath="/opt/tomcat7/webapps/caribwhale_data_dir/encounters";
		String splashImagesDirPath="/home/webadmin/caribwhale/JPGs";
		String pathToExcel = "/home/webadmin/caribwhale/All_Individuals_SUPINFO_20130624.xls";
		String pathToExcel2 = "/home/webadmin/caribwhale/allIDN19842012_20130624.xls";
		String flukesToMatchPath="/home/webadmin/caribwhale/FlukestoMatch.mdb";
		String pathToGPSExcel = "/home/webadmin/caribwhale/All_Pics_with_GPS_20130909.xls";
		File encountersDirFile=new File(encountersDirPath);
		if(!encountersDirFile.exists()){encountersDirFile.mkdir();}
		File sourceImagesDir=new File(splashImagesDirPath);
		
		/**
		 * For thumbnail generation
		 */
		String urlToThumbnailJSPPage="http://localhost:8080/latestgenegis/resetThumbnail.jsp";
		ArrayList<String> thumbnailThese=new ArrayList<String>();
		ArrayList<String> thumbnailTheseImages=new ArrayList<String>();

		//let's get our Shepherd Project structures built
		//Shepherd myShepherd = new Shepherd();
		ArrayList<String> missingPhotos=new ArrayList<String>();
		
		//well, we need a shepherd for sure!
		
		String context="context0";
		
		Shepherd myShepherd=new Shepherd(context);
		myShepherd.beginDBTransaction();
		

		try{

			//lets' get to work!!!!!!!
			
			//Database uDB=Database.open(updateDB);
			File copyImagesFromDir=new File(splashImagesDirPath);
			File encountersRootDir=new File(encountersDirPath);

			
	
			
			
//STEP 1: Get the individual IDs and the best filename from the Access database
			//let's load our Access database
	    File accessDB=new File(pathToAccessFile);
			Database db=Database.open(accessDB);
      System.out.println("I have loaded the database!");
      Table atlanticTable=db.getTable("AtlanticSpermCatalogue");
			Iterator<Map<String,Object>> atlanticTableIterator = atlanticTable.iterator();
			TreeMap<String,String> idMap = new TreeMap<String,String>();
			TreeMap<String,String> locIDMap = new TreeMap<String,String>();
			
			//store communities
			TreeMap<String,ArrayList<String>> communityList = new TreeMap<String,ArrayList<String>>();
			
			while(atlanticTableIterator.hasNext()){
				Map<String,Object> thisIndexRow=atlanticTableIterator.next();
				//String index=(new Integer(((Double)thisIndexRow.get("IDN")).intValue())).toString();
				String id ="";
				if(thisIndexRow.get("IDN")!=null){
				  id=((Integer)thisIndexRow.get("IDN")).toString();
				}
				String imageFilename="";
				if((thisIndexRow.get("Roll")!=null)&&(thisIndexRow.get("Frame")!=null)){
				  imageFilename=((String)thisIndexRow.get("Roll"))+"-"+((String)thisIndexRow.get("Frame"))+".jpg";
				}
				
				String localLocID="";
				if(thisIndexRow.get("Area")!=null){
				  localLocID=((Integer)thisIndexRow.get("Area")).toString();
        }
				
				if(!idMap.containsKey(id)){
					idMap.put(id, imageFilename);
					System.out.println("     Placing "+id+" for "+imageFilename+"...");
					
				//now check if the photo even exists
          File singFile = new File(splashImagesDirPath+"/"+imageFilename);
          if(!singFile.exists()){missingPhotos.add(singFile.getName());}
					
				}
				
        if(!locIDMap.containsKey(id)){
          locIDMap.put(id, localLocID);
          
        }
			}
			
			
			
//STEP 2 - let's create our individuals
			ArrayList<MarkedIndividual> indies=new ArrayList<MarkedIndividual>();
			Set<String> indieKeys=idMap.keySet();
			Iterator<String> itKeys=indieKeys.iterator();
			while(itKeys.hasNext()){
			  String individualID=itKeys.next();
			  MarkedIndividual thisIndie=new MarkedIndividual();
			  if(myShepherd.isMarkedIndividual(individualID)){
			    thisIndie=myShepherd.getMarkedIndividual(individualID);
			  }
			  else{
			    myShepherd.getPM().makePersistent(thisIndie);
			    thisIndie.setIndividualID(individualID);
	        thisIndie.setDateTimeCreated(ServletUtilities.getDate());
			  }
			  
			  indies.add(thisIndie);
			}
			int numIndies=indies.size();
			//myShepherd.getPM().makePersistentAll(indies);
			myShepherd.commitDBTransaction();
			myShepherd.beginDBTransaction();
			
			
			
			
//STEP 3 - obtain data about each MarkedIndividual from Excel2
			//File excel2File=new File(pathToExcel2);
			Workbook w;
			Workbook gpsW;
		    try {
		      File path=new File(pathToExcel2);
		      w = Workbook.getWorkbook(path);
		      File gpsPath=new File(pathToGPSExcel);
          gpsW = Workbook.getWorkbook(gpsPath);
		      // Get the first sheet
		      Sheet sheet = w.getSheet("Pix");
		      Sheet gpsSheet=gpsW.getSheet(0);

		      for(int y=0;y<numIndies;y++){
		        MarkedIndividual indie=myShepherd.getMarkedIndividual(indies.get(y).getIndividualID());
		        String indiesFilename=idMap.get(indie.getIndividualID());
		        String indiesArea=locIDMap.get(indie.getIndividualID());
		        File thisFile=new File(sourceImagesDir,indiesFilename);
		        //boolean haveAssignedPhoto=false;
		        
		        //set up the placeholder encounter
		        Encounter placeholder=new Encounter();
		        String pCatNumber=indie.getIndividualID()+"_DATASTORE";
		        if(myShepherd.isEncounter(pCatNumber)){
		          placeholder=myShepherd.getEncounter(pCatNumber);
		        }
		        else{
		          placeholder.setCatalogNumber(pCatNumber);
		          placeholder.setDWCDateAdded(ServletUtilities.getDate());
		          myShepherd.getPM().makePersistent(placeholder);
		          indie.addEncounter(placeholder, context);
		        }
		        
		        
		        
		       
		       placeholder.setDWCDateLastModified(ServletUtilities.getDate());
		        placeholder.setGenus("Physeter");
		        placeholder.setSpecificEpithet("macrocephalus");
		        placeholder.setState("approved");
		        placeholder.setSubmitterName("Shane Gero");
		        placeholder.setSubmitterEmail("geroshane@gmail.com");
            
            
            placeholder.setLocationID(indiesArea);
            /*
             * 
             * 1=Caribbean,2=Sargasso,3=Gully,4=GOM,5=Azores,6=Other'
             */
            if(placeholder.getLocationID().equals("1")){placeholder.setVerbatimLocality("Caribbean");}
            else if(placeholder.getLocationID().equals("2")){placeholder.setVerbatimLocality("Sargasso");}
            else if(placeholder.getLocationID().equals("3")){placeholder.setVerbatimLocality("Gully");}
            else if(placeholder.getLocationID().equals("4")){placeholder.setVerbatimLocality("Gulf of Mexico");}
            else if(placeholder.getLocationID().equals("5")){placeholder.setVerbatimLocality("Azores");}
            else if(placeholder.getLocationID().equals("6")){placeholder.setVerbatimLocality("Other");}
            
           
            myShepherd.commitDBTransaction();
            myShepherd.beginDBTransaction();
            File placeholderFileDir=new File(encountersDirFile,pCatNumber);
            if(!placeholderFileDir.exists()){placeholderFileDir.mkdir();}
            
		        //for (int j = 0; j < sheet.getColumns(); j++) {
		          for (int i = 0; i < sheet.getRows(); i++) {
		            Cell IDcell = sheet.getCell(21, i);
		            
		            
		            if((IDcell.getContents()!=null)&&(IDcell.getContents().trim().equals(indie.getIndividualID()))){
		              
		              //OK, we have a matching Encounter row
		              //System.out.println("WE HAVE A MATCH!!!!!!!!!");
		              Encounter enc=new Encounter();
		              String catNumber=Integer.toString(i);
		              if(myShepherd.isEncounter(catNumber)){
		                enc=myShepherd.getEncounter(catNumber);
		              }
		              else{
		                enc.setCatalogNumber(catNumber);
		                enc.setDWCDateAdded(ServletUtilities.getDate());
		                myShepherd.getPM().makePersistent(enc);
		                indie.addEncounter(enc, context);
		              }
		              enc.setDWCDateLastModified(ServletUtilities.getDate());
		              
                  
                  
                  myShepherd.commitDBTransaction();
                  myShepherd.beginDBTransaction();
                  
                  enc=myShepherd.getEncounter(catNumber);
                  
                  //start occurrence handling
                  Cell occurCell = sheet.getCell(7, i);
                  if(occurCell.getContents()!=null){
                    
                    String occurrenceNum=occurCell.getContents().trim();
                    if(!occurrenceNum.equals("")){
                      
                        System.out.println("     I have an occurrence: "+occurrenceNum);
                        Occurrence occur=new Occurrence();
                        if(!myShepherd.isOccurrence(occurrenceNum)){
                          occur=new Occurrence(occurrenceNum, enc);
                          myShepherd.getPM().makePersistent(occur);
                          myShepherd.commitDBTransaction();
                          myShepherd.beginDBTransaction();
                        }
                        else{
                          occur=myShepherd.getOccurrence(occurrenceNum);
                          if(!occur.getEncounters().contains(enc)){
                            occur.addEncounter(enc);
                            myShepherd.commitDBTransaction();
                            myShepherd.beginDBTransaction();
                          }
                          
                        }
                      
                    }
                    
                  }
                  //end occurrence handling
                  
                  //start relationship handling
                  Cell groupingCell = sheet.getCell(22, i);
                  if(groupingCell!=null){
                    String group=groupingCell.getContents().trim();
                    if(!group.equals("")){
                      
                      //ok, it is part of a group
                      //indie
                      if(!communityList.containsKey(group)){
                        
                        //ok, we found a new community, let's add it
                        ArrayList<String> members=new ArrayList<String>();
                        members.add(indie.getIndividualID());
                        communityList.put(group, members);
          
                      }
                      else{
                        
                        //ok, we found the community already exists, so let's just add this member
                        ArrayList<String> members=communityList.get(group);
                        if(!members.contains(indie.getIndividualID())){
                          
                          members.add(indie.getIndividualID());
                          communityList.put(group, members);
                          
                        }
                        
                      }
                      
                    }
                  }
                  
                  
                  //end relationship handling
		              
		              Cell filenameCell = sheet.getCell(1, i);
                  if(filenameCell.getContents()!=null){
                    String filename=filenameCell.getContents().trim();
                    
                    //let's check for the file
                  //copy this image over to the encounterDir too
                   File encounterDir=new File(encountersDirFile,enc.getCatalogNumber());
                    if(!encounterDir.exists()){encounterDir.mkdir();}
                    File outputFile=new File(encounterDir,(filename+".JPG"));
                    if(!outputFile.exists()){
                      outputFile=new File(encounterDir,(filename+".jpg"));
                    }
                    File inputFile=new File(sourceImagesDir, (filename+".JPG"));
                    if(!inputFile.exists()){
                      inputFile=new File(sourceImagesDir, (filename+".jpg"));
                    }
                    
                  //copy it in and add the singlephotovideo object
                    
                    
                    if((!outputFile.exists())||(outputFile.length()<1)&&(inputFile.exists()) && (!filename.trim().equals(""))){
                      System.out.println("     I found the file and I am copying it: "+outputFile.getName());
                      copyFile(inputFile, outputFile);
                    }
                    else if(!inputFile.exists() && (!filename.trim().equals(""))){
                      missingPhotos.add(inputFile.getName());
                    }
                   
                    boolean bogusFile=false;
                    if((filename.trim().equals(""))||(filename.trim().toLowerCase().replaceAll(" ", "").indexOf("nopic")!=-1))bogusFile=true;
                    
                    
                    if(!bogusFile){
                      
                    
                    SinglePhotoVideo sing = new SinglePhotoVideo(enc.getCatalogNumber(), (outputFile.getName()), (encounterDir.getAbsolutePath()+"/"+outputFile.getName()));
                    
                    
                    if(!enc.hasSinglePhotoVideoByFileName(outputFile.getName())){
                      myShepherd.getPM().makePersistent(sing);
                      enc.addSinglePhotoVideo(sing);
                    }
                    
                    
                    
                    thumbnailTheseImages.add(enc.getCatalogNumber());
                  
                    
                    myShepherd.commitDBTransaction();
                    myShepherd.beginDBTransaction();
                    
                    System.out.println("   Checking post image persist. Encounter "+enc.getCatalogNumber()+" now has #encs: "+enc.getImages().size());
                    
                    
                    
                    //let's check the filename against the GPS coordinate file
                    
                    for (int d = 0; d < gpsSheet.getRows(); d++) {
                      Cell gpsFilenameCell = gpsSheet.getCell(1, d);
                      if(gpsFilenameCell.getContents()!=null){
                        String gpsFileNameString = gpsFilenameCell.getContents().trim();
                        if(gpsFileNameString.equals(filename)){
                          
                          try{
                            //we have a photo match, and we can now get the GPS coordinates
                            Cell latCell = gpsSheet.getCell(10, d);
                            if(latCell.getContents()!=null){
                              Double lat=(new Double(latCell.getContents()));
                              enc.setDecimalLatitude(lat);
                            }
                          
                            Cell longCell = gpsSheet.getCell(11, d);
                            if(longCell.getContents()!=null){
                              Double localLong=(new Double(longCell.getContents()));
                              enc.setDecimalLongitude(localLong);
                            }
                          
                            if((enc.getDecimalLatitude()!=null)&&(enc.getDecimalLongitude()!=null)){
                              System.out.println("     GPS!!!!  FOUND and SET GPS: "+latCell.getContents()+" "+longCell.getContents());
                            }
                          }
                          catch(NumberFormatException nfe99){}
                          
                        }
                      }
                    } //end for loop
                    }
                  }
		              
		              
		              Cell yearCell = sheet.getCell(24, i);
		              if(yearCell.getContents()!=null){
		                int year=(new Integer(yearCell.getContents())).intValue();
		                enc.setYear(year);
		              }
		              
		              Cell monthCell = sheet.getCell(26, i);
		              if(monthCell.getContents()!=null){
                    int month=(new Integer(monthCell.getContents())).intValue();
                    enc.setMonth(month);
                  }
		              
		              Cell dayCell = sheet.getCell(27, i);
		              if(dayCell.getContents()!=null){
                    int day=(new Integer(dayCell.getContents())).intValue();
                    enc.setDay(day);
                  }
                  
                  
		              Cell hourCell = sheet.getCell(29, i);
		              if(hourCell.getContents()!=null){
                    int hour=(new Integer(hourCell.getContents())).intValue();
                    enc.setHour(hour);
                  }
		              
		              Cell minutesCell = sheet.getCell(30, i);
		              if(minutesCell.getContents()!=null){
                    String mins=minutesCell.getContents();
                    enc.setMinutes(mins);
                  }
		              
		              Cell countryCell = sheet.getCell(35, i);
                  if(countryCell.getContents()!=null){
                    String country=countryCell.getContents();
                    enc.setCountry(country);
                  }
		              
                  enc.setState("approved");
                  
                  enc.setGenus("Physeter");
                  enc.setSpecificEpithet("macrocephalus");
                  
                  
		              enc.setSubmitterName("Shane Gero");
		              enc.setSubmitterEmail("geroshane@gmail.com");
		              
		              myShepherd.getPM().makePersistent(enc);
		              myShepherd.commitDBTransaction();
		              myShepherd.beginDBTransaction();
		              
                  //let's check for the photo assignment
                  
                  String sYear=Integer.toString(enc.getYear());
                  String sMonth=Integer.toString(enc.getMonth());
                  if(sMonth.length()<2){sMonth="0"+sMonth;}
                  String sDay=Integer.toString(enc.getDay());
                  if(sDay.length()<2){sDay="0"+sDay;}
                  
                  //method below replaced by availability of broader photo catalog
                  /**
                  if(indiesFilename.indexOf((sYear+sMonth+sDay))!=-1){
                    
                    //copy this image over to the encounterDir too
                    File thisEncounterDir=new File(encountersDirFile,enc.getCatalogNumber());
                    if(!thisEncounterDir.exists()){thisEncounterDir.mkdir();}
                    File encounterDir=new File(encountersDirFile,enc.getCatalogNumber());
                    if(!encounterDir.exists()){encounterDir.mkdir();}
                    File outputFile=new File(encounterDir,indiesFilename);
                    if(!outputFile.exists()){
                      copyFile(thisFile, outputFile);
                    }
                    
                    SinglePhotoVideo sing = new SinglePhotoVideo(catNumber, indiesFilename, (encounterDir.getAbsolutePath()+"/"+indiesFilename));
                    
                    
                    //if(!haveAssignedPhoto){
                      myShepherd.getPM().makePersistent(sing);
                      enc.addSinglePhotoVideo(sing);
                     // haveAssignedPhoto=true;
                      thumbnailTheseImages.add(enc.getCatalogNumber());
                   // }
                    
                    myShepherd.commitDBTransaction();
                    myShepherd.beginDBTransaction();
                    
                  }
                  */
                  
		              
		              myShepherd.commitDBTransaction();
		              myShepherd.beginDBTransaction();
		            }
		            
		            
		         
		            
		          }
		          
		          
		          
		          
		          //if the MarkedIndividual does not have a SinglePhoto video assigned to an Encounter, then assign the image here and create/copy the directories and files
		          if((indie.getAllSinglePhotoVideo()==null)||(indie.getAllSinglePhotoVideo().size()==0)){
		            SinglePhotoVideo sing = new SinglePhotoVideo(placeholder.getCatalogNumber(), indiesFilename, (placeholderFileDir.getAbsolutePath()+"/"+indiesFilename));
               
		            if(!placeholder.hasSinglePhotoVideoByFileName(indiesFilename)){
		              myShepherd.getPM().makePersistent(sing);
		              placeholder.addSinglePhotoVideo(sing);
		              thumbnailTheseImages.add(placeholder.getCatalogNumber());
		             // haveAssignedPhoto=true;
		            }
		            
		            myShepherd.commitDBTransaction();
		            myShepherd.beginDBTransaction();
               
                File outputFile=new File(placeholderFileDir,indiesFilename);
                if(!outputFile.exists()){
                  copyFile(thisFile,outputFile);
                }
		          }
		          
		          
		        System.out.println("Found "+indie.getEncounters().size()+" encounters for the indie "+indie.getIndividualID());
		        //STEP 4 - obtain data about each MarkedIndividual from Excel
            
            Workbook w2;
            File path1=new File(pathToExcel);
            w2 = Workbook.getWorkbook(path1);
            // Get the first sheet
            Sheet sheet1 = w2.getSheet(0);
            for (int f = 0; f < sheet1.getRows(); f++) {
              Cell IDcell1 = sheet1.getCell(0, f);
              String id="";
              if(sheet1.getCell(0, f).getContents()!=null){
                id=sheet1.getCell(0, f).getContents().trim();
                
                if(id.equals(indie.getIndividualID())){
                  
                  //alright, now we have the row we need in the Excel file to populate attributes of the MarkedIndividual
                  
                  
                  //let's get year of birth
                  if(sheet1.getCell(15, f)!=null){
                    Cell yobCell=sheet1.getCell(15, f);
                    if(yobCell.getContents()!=null){
                      try{
                        String thisYob=(String)yobCell.getContents().trim();
                        DateTime dt=new DateTime(thisYob);
                        indie.setTimeOfBirth(dt.getMillis());
                        System.out.println("     YOB SET!!");
                      }
                      catch(Exception e){
                        e.printStackTrace();
                      }
                      
                    
                    }
                  }
                  
                  //let's get year of death
                  if(sheet1.getCell(16, f)!=null){
                    Cell yodCell=sheet1.getCell(16, f);
                    if(yodCell.getContents()!=null){
                      try{
                        String thisYod=(String)yodCell.getContents().trim();
                        DateTime dt=new DateTime(thisYod);
                        indie.setTimeOfDeath(dt.getMillis());
                        System.out.println("     YOD SET!!");
                      }
                      catch(Exception e){
                        e.printStackTrace();
                      }
                      
                    
                    }
                  }
                  
                  
                  
                  
                  
                  
                  //let's get sex
                  if(sheet1.getCell(9, f)!=null){
                    Cell sexCell=sheet1.getCell(9, f);
                    if(sexCell.getContents()!=null){
                      String thisSex=sexCell.getContents().trim();
                      if(!thisSex.equals("")){
                        
                        if(thisSex.toLowerCase().startsWith("m")){indie.setSex("male");}
                        else if(thisSex.toLowerCase().startsWith("f")){indie.setSex("female");}
                        else{indie.setSex("unknown");}
                        
                        myShepherd.commitDBTransaction();
                        myShepherd.beginDBTransaction();
                        
                        System.out.println("     Set sex for indie "+indie.getIndividualID()+" to "+indie.getSex());
                        
                        //if genetic sex (Fg or Mg), create the correct data structures
                        if((thisSex.toLowerCase().trim().equals("mg"))||(thisSex.toLowerCase().trim().equals("fg"))){
                          TissueSample ts=new TissueSample(placeholder.getCatalogNumber(),(indie.getIndividualID()+"_SAMPLE"));
                          if((placeholder.getTissueSamples()!=null)&&(placeholder.getTissueSamples().size()>0)){
                            ts=placeholder.getTissueSamples().get(0);
                          }
                          else{
                            myShepherd.getPM().makePersistent(ts);
                            placeholder.addTissueSample(ts);
                            System.out.println("     Adding a new tissue sample!!!");
                            myShepherd.commitDBTransaction();
                            myShepherd.beginDBTransaction();
                            System.out.println("     SampleID is: "+ts.getSampleID());
                          }
                          SexAnalysis sa=new SexAnalysis((indie.getIndividualID()+"_SEX"), thisSex.trim(), placeholder.getCatalogNumber(), ts.getSampleID());
                          if(myShepherd.isGeneticAnalysis(ts.getSampleID(), placeholder.getCatalogNumber(), (indie.getIndividualID()+"_SEX"), "SexAnalysis")){
                            sa=myShepherd.getSexAnalysis(ts.getSampleID(), placeholder.getCatalogNumber(), (indie.getIndividualID()+"_SEX"));
                          }
                          else{myShepherd.getPM().makePersistent(sa);}
                          
                          
                          
                          myShepherd.commitDBTransaction();
                          myShepherd.beginDBTransaction();
                          
                          //check if this analysis already exists
                          
                          ts.addGeneticAnalysis(sa);
                          
                          
                          myShepherd.commitDBTransaction();
                          myShepherd.beginDBTransaction();
                          System.out.println("     Adding a GENETIC SEX: "+sa.getSex());
                          System.out.println("     Placeholder confirms: "+placeholder.getGeneticSex());
                          System.out.println("     Indie confirms: "+indie.getGeneticSex());
                        }
                      }
                      else{indie.setSex("unknown");}
                    }
                    else{indie.setSex("unknown");}
                  }
                  else{indie.setSex("unknown");}
                  
                  //let's get haplotype
                  if(sheet1.getCell(12, f)!=null){
                    Cell haploCell=sheet1.getCell(12, f);
                    if(haploCell.getContents()!=null){
                      String thisHaplo=haploCell.getContents();
                      if(!thisHaplo.trim().equals("")){
                         
                          //haplotype gets added to the placeholderEncounter
                          //add the tissue sample
                        
                          TissueSample ts=new TissueSample(placeholder.getCatalogNumber(),(indie.getIndividualID()+"_SAMPLE"));
                          if((placeholder.getTissueSamples()!=null)&&(placeholder.getTissueSamples().size()>0)){
                            ts=placeholder.getTissueSamples().get(0);
                          }
                          else{
                            myShepherd.getPM().makePersistent(ts);
                            placeholder.addTissueSample(ts);
                            System.out.println("     Adding a new tissue sample!!!");
                            
                            myShepherd.commitDBTransaction();
                            myShepherd.beginDBTransaction();
                          }
                          
                         
                          MitochondrialDNAAnalysis haplo=new MitochondrialDNAAnalysis((indie.getIndividualID()+"_HAPLOTYPE"),thisHaplo,placeholder.getCatalogNumber(),ts.getSampleID());
                          
                          if(myShepherd.isGeneticAnalysis(ts.getSampleID(), placeholder.getCatalogNumber(), (indie.getIndividualID()+"_HAPLOTYPE"), "MitochondrialDNA")){
                            haplo=myShepherd.getMitochondrialDNAAnalysis(ts.getSampleID(), placeholder.getCatalogNumber(), (indie.getIndividualID()+"_HAPLOTYPE"));
                          }
                          else{
                            myShepherd.getPM().makePersistent(haplo);
                            ts.addGeneticAnalysis(haplo);
                            indie.doNotSetLocalHaplotypeReflection(haplo.getHaplotype());
                            
                          }
                          
                          
                          myShepherd.commitDBTransaction();
                          myShepherd.beginDBTransaction();

                        System.out.println("     Set haplo for indie "+indie.getIndividualID()+" to "+indie.getHaplotype());
                      }
                    }
                  }
                  
                  //let's get class/lifestage
                  if(sheet1.getCell(10, f)!=null){
                    Cell classCell=sheet1.getCell(10, f);
                    if(classCell.getContents()!=null){
                      String thisClass=classCell.getContents();
                      if(!thisClass.trim().equals("")){
                        placeholder.setLifeStage(thisClass);
                        myShepherd.commitDBTransaction();
                        myShepherd.beginDBTransaction();
                        System.out.println("     Set lifestage for indie "+indie.getIndividualID()+" to "+placeholder.getLifeStage());
                      }
                    }
                  }
                  
                  indie.setAlternateID("");
                  
                  //let's get  previous names
                  if(sheet1.getCell(3, f)!=null){
                    Cell classCell=sheet1.getCell(3, f);
                    if(classCell.getContents()!=null){
                      String thisClass=classCell.getContents();
                      if(!thisClass.trim().equals("")){
                        //placeholder.setDynamicProperty("AETcode", thisClass.trim());
                        String existingAltIDs="";
                        if(indie.getAlternateID()!=null){
                          existingAltIDs=indie.getAlternateID();
                          if(!existingAltIDs.trim().equals("")){existingAltIDs+=",";}
                        }
                        if(existingAltIDs.indexOf(thisClass.trim())==-1){
                          indie.setAlternateID(existingAltIDs+thisClass.trim());
                        }
                        myShepherd.commitDBTransaction();
                        myShepherd.beginDBTransaction();
                        System.out.println("     Set pre for indie "+indie.getAlternateID());
                      }
                    }
                  }
                  
                //let's get AETcode
                  if(sheet1.getCell(4, f)!=null){
                    Cell classCell=sheet1.getCell(4, f);
                    if(classCell.getContents()!=null){
                      String thisClass=classCell.getContents();
                      if(!thisClass.trim().equals("")){
                        placeholder.setDynamicProperty("AETcode", thisClass.trim());
                        String existingAltIDs="";
                        if(indie.getAlternateID()!=null){
                          existingAltIDs=indie.getAlternateID();
                          if(!existingAltIDs.trim().equals("")){existingAltIDs+=",";}
                        }
                        if(existingAltIDs.indexOf("AET:"+thisClass.trim())==-1){
                          
                          indie.setAlternateID(existingAltIDs+"AET:"+thisClass.trim());
                        }
                        myShepherd.commitDBTransaction();
                        myShepherd.beginDBTransaction();
                        System.out.println("     Set AET code for indie "+indie.getAlternateID());
                      }
                    }
                  }
                  
                //let's get IFAWcode
                  if(sheet1.getCell(5, f)!=null){
                    Cell classCell=sheet1.getCell(5, f);
                    if(classCell.getContents()!=null){
                      String thisClass=classCell.getContents();
                      if(!thisClass.trim().equals("")){
                        placeholder.setDynamicProperty("IFAWcode", thisClass.trim());
                        String existingAltIDs="";
                        if(indie.getAlternateID()!=null){
                          existingAltIDs=indie.getAlternateID();
                          if(!existingAltIDs.trim().equals("")){existingAltIDs+=",";}
                        }
                         if(existingAltIDs.indexOf("IFAW:"+thisClass.trim())==-1){
                          
                          indie.setAlternateID(existingAltIDs+"IFAW:"+thisClass.trim());
                        }
                        myShepherd.commitDBTransaction();
                        myShepherd.beginDBTransaction();
                        System.out.println("     Set IFAW code for indie "+indie.getAlternateID());
                      }
                    }
                  }
                  
                  
                  //let's get STEFFENname
                  if(sheet1.getCell(6, f)!=null){
                    Cell classCell=sheet1.getCell(6, f);
                    if(classCell.getContents()!=null){
                      String thisClass=classCell.getContents();
                      if(!thisClass.trim().equals("")){
                        placeholder.setDynamicProperty("STEFFENname", thisClass.trim());
                        String existingAltIDs="";
                        if(indie.getAlternateID()!=null){
                          existingAltIDs=indie.getAlternateID();
                          if(!existingAltIDs.trim().equals("")){existingAltIDs+=",";}
                        }
                        if(existingAltIDs.indexOf("STEFFEN:"+thisClass.trim())==-1){
                          
                          indie.setAlternateID(existingAltIDs+"STEFFEN:"+thisClass.trim());
                        }
                      myShepherd.commitDBTransaction();
                        myShepherd.beginDBTransaction();
                        System.out.println("     Set STEFFEN name for indie "+indie.getAlternateID());
                      }
                    }
                  }
                  
                  //let's get PERNELnames
                  if(sheet1.getCell(7, f)!=null){
                    Cell classCell=sheet1.getCell(7, f);
                    if(classCell.getContents()!=null){
                      String thisClass=classCell.getContents();
                      if(!thisClass.trim().equals("")){
                        placeholder.setDynamicProperty("PERNELnames", thisClass.trim());
                        String existingAltIDs="";
                        if(indie.getAlternateID()!=null){
                          existingAltIDs=indie.getAlternateID();
                          if(!existingAltIDs.trim().equals("")){existingAltIDs+=",";}
                        }
                        if(existingAltIDs.indexOf("PERNEL:"+thisClass.trim())==-1){
                          
                          indie.setAlternateID(existingAltIDs+"PERNEL:"+thisClass.trim());
                        }
                        myShepherd.commitDBTransaction();
                        myShepherd.beginDBTransaction();
                        System.out.println("     Set PERNEL name for indie "+indie.getAlternateID());
                      }
                    }
                  }
                  
                  //let's get locationID
                  if(sheet1.getCell(19, f)!=null){
                    Cell locIDCell=sheet1.getCell(19, f);
                    if(locIDCell.getContents()!=null){
                      String thisLocID=locIDCell.getContents();
                      if(!thisLocID.trim().equals("")){
                        placeholder.setLocationID(thisLocID.trim());
                        myShepherd.commitDBTransaction();
                        myShepherd.beginDBTransaction();
                        /*
                         * 
                         * 1=Caribbean,2=Sargasso,3=Gully,4=GOM,5=Azores,6=Other'
                         */
                        if(placeholder.getLocationID().equals("1")){placeholder.setVerbatimLocality("Caribbean");}
                        else if(placeholder.getLocationID().equals("2")){placeholder.setVerbatimLocality("Sargasso");}
                        else if(placeholder.getLocationID().equals("3")){placeholder.setVerbatimLocality("Gully");}
                        else if(placeholder.getLocationID().equals("4")){placeholder.setVerbatimLocality("Gulf of Mexico");}
                        else if(placeholder.getLocationID().equals("5")){placeholder.setVerbatimLocality("Azores");}
                        else if(placeholder.getLocationID().equals("6")){placeholder.setVerbatimLocality("Other");}
                        myShepherd.commitDBTransaction();
                        myShepherd.beginDBTransaction();
                        System.out.println("     Set locationID for indie "+indie.getIndividualID()+" to "+placeholder.getLocationID());
                      }
                    }
                  }
                  
                //let's get nickname
                  if(sheet1.getCell(1, f)!=null){
                    indie.setNickName("");
                    Cell nicknameCell=sheet1.getCell(1, f);
                    if(nicknameCell.getContents()!=null){
                      String thisnickname=nicknameCell.getContents();
                      if(!thisnickname.trim().equals("")){
                        indie.setNickName(thisnickname);
                        myShepherd.commitDBTransaction();
                        myShepherd.beginDBTransaction();
                        System.out.println("     Set nickname for indie "+indie.getIndividualID()+" to "+indie.getNickName());
                      }
                    }
                  }
                  
                  
                  //let's get additional indie comments
                  if(sheet1.getCell(61, f)!=null){
                    Cell addCommentsCell=sheet1.getCell(61, f);
                    if(addCommentsCell.getContents()!=null){
                      String thisaddComments=addCommentsCell.getContents();
                      if(!thisaddComments.trim().equals("")){
                        indie.addComments(thisaddComments);
                        myShepherd.commitDBTransaction();
                        myShepherd.beginDBTransaction();
                        System.out.println("     Set additional comments for indie "+indie.getIndividualID()+" to "+indie.getComments());
                      }
                    }
                  }
                  myShepherd.commitDBTransaction();
                  myShepherd.beginDBTransaction();
                  
             
                  
                  
                  
                  
                  
                }
                
              }
            }
            
            //System.out.println("End step 4...");
            
            //now check FlukestoMatch for additional encounters
            File flukesToMatchDB=new File(flukesToMatchPath);
            Database flukesDB=Database.open(flukesToMatchDB);
            //System.out.println("     Entering FlukestoMatch...");
            Set<String> tableNames=flukesDB.getTableNames();
            int numTableNames=tableNames.size();
            //let's iterate the tables
            Iterator names=tableNames.iterator();
            while(names.hasNext()){
              String localName=(String)names.next();
              Table thisTable=flukesDB.getTable(localName);
              //System.out.println("          Entering FlukestoMatch:"+localName);
              Iterator<Map<String,Object>> tableIterator = thisTable.iterator();
              int rowNum=0;
              while(tableIterator.hasNext()){
                Map<String,Object> thisIndexRow=tableIterator.next();
                if(thisIndexRow.get("IDN")!=null){
                  String localID="";
                  try{
                    localID=((Integer)thisIndexRow.get("IDN")).toString();
                  }
                  catch(ClassCastException cce){
                    //cce.printStackTrace();
                  }
                  
                  if(localID.trim().equals(indie.getIndividualID())){
                
                    //hooray we have a match in a table in flukes to match
                    System.out.println("     %%%%Found a match in FlukestoMatch:"+thisTable.getName());
                    
                    //let's create an encounter and set the date
                    
                    
                    Encounter flukesEnc=new Encounter();
                    String localCatNumber=indie.getIndividualID()+":FlukestoMatch:"+thisTable.getName()+":"+rowNum;
                    if(myShepherd.isEncounter(localCatNumber)){
                      flukesEnc=myShepherd.getEncounter(localCatNumber);
                    }
                    else{
                      flukesEnc.setCatalogNumber(localCatNumber);
                      flukesEnc.setDWCDateAdded(ServletUtilities.getDate());
                      indie.addEncounter(flukesEnc, context);
                    }
                    
                    flukesEnc.setDWCDateLastModified(ServletUtilities.getDate());
                    
                    myShepherd.getPM().makePersistent(flukesEnc);
                    
                    myShepherd.commitDBTransaction();
                    myShepherd.beginDBTransaction();
                    try{
                    if((thisIndexRow.get("Year")!=null)){
                      int year=((Double)thisIndexRow.get("Year")).intValue();
                      if(year>0)flukesEnc.setYear(year);
                    }
                    if((thisIndexRow.get("year")!=null)){
                      int year=((Double)thisIndexRow.get("year")).intValue();
                      if(year>0)flukesEnc.setYear(year);
                    }
                    System.out.println("          Found a year!");
                    if((thisIndexRow.get("Month")!=null)){
                      int month=((Integer)thisIndexRow.get("Month")).intValue();
                      if(month>0)flukesEnc.setMonth(month);
                    }
                    if((thisIndexRow.get("month")!=null)){
                      int month=((Integer)thisIndexRow.get("month")).intValue();
                      if(month>0)flukesEnc.setMonth(month);
                    }
                    System.out.println("          Found a month!");
                    if((thisIndexRow.get("Day")!=null)){
                      int day=((Integer)thisIndexRow.get("Day")).intValue();
                      if(day>0)flukesEnc.setDay(day);
                    }
                    if((thisIndexRow.get("day")!=null)){
                      int day=((Integer)thisIndexRow.get("day")).intValue();
                      if(day>0)flukesEnc.setDay(day);
                    }
                    System.out.println("          Found a day!");
                    if((thisIndexRow.get("Hour")!=null)){
                      int hour=-1;
                      try{hour=(new Integer((String)thisIndexRow.get("Hour"))).intValue();}
                      catch(ClassCastException here){}
                      if(hour>-1){flukesEnc.setHour(hour);}
                    }
                    if((thisIndexRow.get("hour")!=null)){
                      int hour=-1;
                      try{hour=(new Integer((String)thisIndexRow.get("hour"))).intValue();}
                      catch(ClassCastException here){}
                      if(hour>-1){flukesEnc.setHour(hour);}
                    }
                    System.out.println("          Found an hour!");
                    if((thisIndexRow.get("Minute")!=null)){
                      String mins="";
                      try{mins=(String)thisIndexRow.get("Minute");}
                      catch(ClassCastException here){}
                      if(!mins.equals("")){flukesEnc.setMinutes(mins);}
                    }
                    if((thisIndexRow.get("minute")!=null)){
                      String mins="";
                      try{mins=(String)thisIndexRow.get("minute");}
                      catch(ClassCastException here){}
                      if(!mins.equals("")){flukesEnc.setMinutes(mins);}
                    }
                    System.out.println("          Found a minute!");
                    
                    if((thisIndexRow.get("Comments")!=null)){
                      String comm=(String)thisIndexRow.get("Comments");
                      flukesEnc.addComments(comm);
                    }
                    System.out.println("          Found comments!");
                    
                    if((thisIndexRow.get("Class")!=null)){
                      String classy=(String)thisIndexRow.get("Class");
                      flukesEnc.setLifeStage(classy);
                    }
                    System.out.println("          Found a class!");
                    //set tissue sample if taken
                    if((thisIndexRow.get("Sample")!=null)){
                      String sample=(String)thisIndexRow.get("Sample");
                      StringTokenizer str=new StringTokenizer(sample,",");
                      while(str.hasMoreTokens()){
                        String token=str.nextToken();
                        TissueSample ts=new TissueSample(flukesEnc.getCatalogNumber(),token);
                        myShepherd.getPM().makePersistent(ts);
                        flukesEnc.addTissueSample(ts);
                        myShepherd.commitDBTransaction();
                        myShepherd.beginDBTransaction();
                        System.out.println("          Found a tissue sample!");
                      }
                    }
                    }
                    catch(ClassCastException cce2){cce2.printStackTrace();}
                    myShepherd.commitDBTransaction();
                    myShepherd.beginDBTransaction();
                  
                  }
                }
                //}
                //}
            
                rowNum++;  
                }
              
              
            }
            
            indie.resetMaxNumYearsBetweenSightings();
            
            myShepherd.commitDBTransaction();
            myShepherd.beginDBTransaction();
            
            
            
		      
		      } //end for
		      
		      
		      
		      //ok, we are done populating our MarkedIndividuals, Encounter and Occurrences
		      //last step, we need to use the communityList object to populate our Relationship classes
		      Set<String> communityNames=communityList.keySet();
		      Iterator<String> communities=communityNames.iterator();
		      while(communities.hasNext()){
		        
		        //let's get the communityName and membership list
		        String comName=communities.next();
		        ArrayList<String> members=communityList.get(comName);
		        int numMembers=members.size();
		        
		        for(int p=0;p<(numMembers-1);p++){
		          for(int q=(p+1);q<numMembers;q++){
		            String indieName1=members.get(p);
		            String indieName2=members.get(q);
		            
		            org.ecocean.social.Relationship myRel=new org.ecocean.social.Relationship("CommunityMembership", indieName1, indieName2,"member","member");
		            myRel.setRelatedSocialUnitName(comName);
		            
		            //if it does not exist, create the relationship
		            if(!myShepherd.isRelationship(myRel.getType(), myRel.getMarkedIndividualName1(), myRel.getMarkedIndividualName2(), myRel.getMarkedIndividualRole1(), myRel.getMarkedIndividualRole2(), myRel.getRelatedSocialUnitName(), true)){
		              myShepherd.getPM().makePersistent(myRel);
	                myShepherd.commitDBTransaction();
	                myShepherd.beginDBTransaction();
		            }
		            
		            //now check if community has been created!
		           if(!myShepherd.isCommunity(comName)){
		             org.ecocean.social.SocialUnit myCom=new org.ecocean.social.SocialUnit(comName);
		             myShepherd.getPM().makePersistent(myCom);
                 myShepherd.commitDBTransaction();
                 myShepherd.beginDBTransaction();
		             
		           } 
		           
		            
		          }
		        }
		        
		      }
		      //end relationship creation
		      
		      
		      //look for mother-calf relationships
		      Workbook w2;
          File path1=new File(pathToExcel);
          w2 = Workbook.getWorkbook(path1);
          // Get the first sheet
          Sheet sheet1 = w2.getSheet(0);
		      for (int f = 0; f < sheet1.getRows(); f++) {
		        
		        //if mother or offspring columns are populated
		        if((sheet1.getCell(13, f)!=null)||(sheet1.getCell(14, f)!=null)){
		        
		          Cell IDcell1 = sheet1.getCell(0, f);
		          if(IDcell1!=null){
		          
		            String myID=IDcell1.getContents().trim();
		            if(myShepherd.isMarkedIndividual(myID)){
		            
		              
		              //check for a mother
		              if((sheet1.getCell(13, f)!=null)&&(!sheet1.getCell(13, f).getContents().trim().equals(""))){
		                  String mother=sheet1.getCell(13, f).getContents().trim();
		                  
		                  MarkedIndividual motherIndie=new MarkedIndividual();
		                  if(myShepherd.isMarkedIndividual(mother)){motherIndie=myShepherd.getMarkedIndividual(mother);}
		                  else if(myShepherd.getMarkedIndividualsByNickname(mother)!=null){
		                    List moms=myShepherd.getMarkedIndividualsByNickname(mother);
		                    if(moms.size()>0){
		                      motherIndie=(MarkedIndividual)moms.get(0);
		                    }
		                  }
		                  
		                  if(motherIndie.getIndividualID()!=null){
		                    
		                    org.ecocean.social.Relationship myRel=new org.ecocean.social.Relationship("Familial",myID,motherIndie.getIndividualID(),"calf","mother");
                        
		                    
		                    //if it does not exist, create the relationship
		                    if(!myShepherd.isRelationship(myRel.getType(), myRel.getMarkedIndividualName1(), myRel.getMarkedIndividualName2(), myRel.getMarkedIndividualRole1(), myRel.getMarkedIndividualRole2(), true)){
		                      myShepherd.getPM().makePersistent(myRel);
		                      myShepherd.commitDBTransaction();
		                      myShepherd.beginDBTransaction();
		                    }
		                    
		                    
		                    
		                  }

		              }
		              //end check for mother
		              
		            
		            
		            }
		          
		          }
		      }
		        
		        
		      }
		      
		      
		      
		      
		    } catch (BiffException e) {
		      e.printStackTrace();
		    }
			


		}
		catch(Exception e){
			e.printStackTrace();
		}
		//myShepherd.closeDBTransaction();

		//now print out the missingImages
		int numMissingPhotos=missingPhotos.size();
		for(int k=0;k<numMissingPhotos;k++){
		  System.out.println("Missing photo: "+missingPhotos.get(k));
		}
		
		myShepherd.commitDBTransaction();
		myShepherd.closeDBTransaction();
		myShepherd=null;
		
		
		//pause to let the user fire up the Tomcat web server
		System.out.println("Please start Tomcat and then press ENTER to continue...");
		char c='0';
		while(c == '0'){
			try{
			c = (char)System.in.read();
		}
			catch(IOException ioe){
				ioe.printStackTrace();
			}
		}
		
		
		System.out.println("\n\nDONE!!!!!");

		/**
		System.out.println("\n\nStarting thumbnail work!");

		int numThumbnailsToGenerate=thumbnailThese.size();
		String IDKey="";
		for(int q=0;q<numThumbnailsToGenerate;q++){
			IDKey=thumbnailThese.get(q);
			//ping a URL to thumbnail generator - Tomcat must be up and running
		    try
		    {

		    	System.out.println("Trying to render a thumbnail for: "+thumbnailTheseImages.get(q));
		    	String urlString=urlToThumbnailJSPPage+"?number="+IDKey+"&imageNum=1";
		    	System.out.println("     "+urlString);
		    	URL url = new URL(urlString);

		        BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
		        in.close();
		    }
		    catch (MalformedURLException e) {

		    	System.out.println("Error trying to render the thumbnail for "+IDKey+".");
		    	e.printStackTrace();

		    }
		    catch (IOException ioe) {

		    	System.out.println("Error trying to render the thumbnail for "+IDKey+".");
		    	ioe.printStackTrace();

		    }



		}
		*/


	}

	public static String getExactFileName(File f) {
		String returnVal;
		try {
			returnVal = f.getCanonicalPath();
			returnVal =
				returnVal.substring(returnVal.lastIndexOf(File.separator)+1);
		}
		catch(IOException e) {
			returnVal = "";
		}
		return returnVal;
	}




public static void copyFile(File thisFile, File outputFile){
  String indiesFilename=thisFile.getName();
  try{

    
    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(thisFile), 4096);
    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputFile), 4096);
    int theChar;
    while ((theChar = bis.read()) != -1) {
      bos.write(theChar);
    }
    bos.close();
    bis.close();
    //System.out.println(" !@@!@!#!@Completed copy of "+indiesFilename);


    }
    catch(IOException ioe){
      System.out.println("IOException on file transfer for: "+indiesFilename);
      ioe.printStackTrace();
    }
}




}