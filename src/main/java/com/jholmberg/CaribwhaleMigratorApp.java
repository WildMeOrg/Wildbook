/**
 *
 */
package com.jholmberg;

//import the Wildbook Framework
import org.ecocean.*;
import org.ecocean.servlet.*;
import org.ecocean.genetics.*;

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
		String pathToAccessFile="C:/caribwhale/atlantic/AtlanticCatalogue.mdb";
		String encountersDirPath="C:/apache-tomcat-7.0.32/webapps/shepherd_data_dir/encounters";
		String splashImagesDirPath="C:/caribwhale/TIFs";
		String pathToExcel = "C:/caribwhale/atlantic/All_Individuals_SUPINFO_20130624.xls";
		String pathToExcel2 = "C:\\caribwhale\\atlantic\\allIDN19842012_20130624.xls";
		File encountersDirFile=new File(encountersDirPath);
		if(!encountersDirFile.exists()){encountersDirFile.mkdir();}
		File sourceImagesDir=new File(splashImagesDirPath);
		
		/**
		 * For thumbnail generation
		 */
		String urlToThumbnailJSPPage="http://www.splashcatalog.org/latestgenegis/resetThumbnail.jsp";
		ArrayList<String> thumbnailThese=new ArrayList<String>();
		ArrayList<String> thumbnailTheseImages=new ArrayList<String>();

		//let's get our Shepherd Project structures built
		//Shepherd myShepherd = new Shepherd();
		ArrayList<String> missingPhotos=new ArrayList<String>();
		
		//well, we need a shepherd for sure!
		
		Shepherd myShepherd=new Shepherd();
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
			while(atlanticTableIterator.hasNext()){
				Map<String,Object> thisIndexRow=atlanticTableIterator.next();
				//String index=(new Integer(((Double)thisIndexRow.get("IDN")).intValue())).toString();
				String id ="";
				if(thisIndexRow.get("IDN")!=null){
				  id=((Integer)thisIndexRow.get("IDN")).toString();
				}
				String imageFilename="";
				if((thisIndexRow.get("Roll")!=null)&&(thisIndexRow.get("Frame")!=null)){
				  imageFilename=((String)thisIndexRow.get("Roll"))+"-"+((String)thisIndexRow.get("Frame"))+".tif";
				}
				if(!idMap.containsKey(id)){
					idMap.put(id, imageFilename);
					System.out.println("     Placing "+id+" for "+imageFilename+"...");
					
				//now check if the photo even exists
          File singFile = new File(splashImagesDirPath+"/"+imageFilename);
          if(!singFile.exists()){missingPhotos.add(singFile.getName());}
					
				}
			}

			//STEP 2 - let's create or individuals
			ArrayList<MarkedIndividual> indies=new ArrayList<MarkedIndividual>();
			Set<String> indieKeys=idMap.keySet();
			Iterator<String> itKeys=indieKeys.iterator();
			while(itKeys.hasNext()){
			  String individualID=itKeys.next();
			  MarkedIndividual thisIndie=new MarkedIndividual();
			  thisIndie.setIndividualID(individualID);
			  indies.add(thisIndie);
			}
			int numIndies=indies.size();
			myShepherd.getPM().makePersistentAll(indies);
			myShepherd.commitDBTransaction();
			myShepherd.beginDBTransaction();
			
			//STEP 3 - obtain data about each MarkedIndividual from Excel2
			//File excel2File=new File(pathToExcel2);
			Workbook w;
		    try {
		      File path=new File(pathToExcel2);
		      w = Workbook.getWorkbook(path);
		      // Get the first sheet
		      Sheet sheet = w.getSheet("Pix");
		      
		      
		      
		      for(int y=0;y<numIndies;y++){
		        MarkedIndividual indie=myShepherd.getMarkedIndividual(indies.get(y).getIndividualID());
		        String indiesFilename=idMap.get(indie.getIndividualID());
		        File thisFile=new File(sourceImagesDir,indiesFilename);
		        
		        //set up the placeholder encounter
		        Encounter placeholder=new Encounter();
            String pCatNumber=indie.getIndividualID()+"_DATASTORE";
            placeholder.setCatalogNumber(pCatNumber);
            myShepherd.getPM().makePersistent(placeholder);
            indie.addEncounter(placeholder);
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
		              

                  
                  String catNumber=Integer.toString(i);
		              enc.setCatalogNumber(catNumber);
		              
		              myShepherd.getPM().makePersistent(enc);
		              myShepherd.commitDBTransaction();
		              myShepherd.beginDBTransaction();
		              
                  //let's check for the photo assignment
                  
                  String sYear=Integer.toString(enc.getYear());
                  String sMonth=Integer.toString(enc.getMonth());
                  if(sMonth.length()<2){sMonth="0"+sMonth;}
                  String sDay=Integer.toString(enc.getDay());
                  if(sDay.length()<2){sDay="0"+sDay;}
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
                    myShepherd.getPM().makePersistent(sing);
                    
                    enc.addSinglePhotoVideo(sing);
                    myShepherd.commitDBTransaction();
                    myShepherd.beginDBTransaction();
                    
                  }
                  
		              indie.addEncounter(enc);
		              myShepherd.commitDBTransaction();
		              myShepherd.beginDBTransaction();
		            }
		            
		            
		         
		            
		          }
		          
		          
		          
		          
		          //if the MarkedIndividual does not have a SinglePhoto video assigned to an Encounter, then assign the image here and create/copy the directories and files
		          if((indie.getAllSinglePhotoVideo()==null)||(indie.getAllSinglePhotoVideo().size()==0)){
		            SinglePhotoVideo sing = new SinglePhotoVideo(placeholder.getCatalogNumber(), indiesFilename, (placeholderFileDir.getAbsolutePath()+"/"+indiesFilename));
                myShepherd.getPM().makePersistent(sing);
		            placeholder.addSinglePhotoVideo(sing);
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
                  
                  
                  //let's get sex
                  if(sheet1.getCell(9, f)!=null){
                    Cell sexCell=sheet1.getCell(9, f);
                    if(sexCell.getContents()!=null){
                      String thisSex=sexCell.getContents();
                      if(!thisSex.trim().equals("")){
                        indie.setSex(thisSex.trim());
                        System.out.println("     Set sex for indie "+indie.getIndividualID()+" to "+indie.getSex());
                        
                        //if genetic sex (Fg or Mg), create the correct data structures
                        if((indie.getSex().toLowerCase().equals("Mg"))||(indie.getSex().toLowerCase().equals("Fg"))){
                          TissueSample ts=new TissueSample(placeholder.getCatalogNumber(),(indie.getIndividualID()+"_SAMPLE"));
                          if((placeholder.getTissueSamples()!=null)&&(placeholder.getTissueSamples().size()>0)){
                            ts=placeholder.getTissueSamples().get(0);
                          }
                          else{
                            myShepherd.getPM().makePersistent(ts);
                            myShepherd.commitDBTransaction();
                            myShepherd.beginDBTransaction();
                          }
                          SexAnalysis sa=new SexAnalysis((indie.getIndividualID()+"_SEX"), thisSex, placeholder.getCatalogNumber(), ts.getSampleID());
                          myShepherd.getPM().makePersistent(sa);
                          ts.addGeneticAnalysis(sa);
                          myShepherd.commitDBTransaction();
                          myShepherd.beginDBTransaction();
                        }
                      }
                    }
                  }
                  
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
                            myShepherd.commitDBTransaction();
                            myShepherd.beginDBTransaction();
                          }
                          
                          placeholder.addTissueSample(ts);
                          MitochondrialDNAAnalysis haplo=new MitochondrialDNAAnalysis((indie.getIndividualID()+"_HAPLOTYPE"),thisHaplo,placeholder.getCatalogNumber(),ts.getSampleID());
                          myShepherd.getPM().makePersistent(haplo);
                          ts.addGeneticAnalysis(haplo);
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
                        System.out.println("     Set lifestage for indie "+indie.getIndividualID()+" to "+placeholder.getLifeStage());
                      }
                    }
                  }
                  
                  //let's get locationID
                  if(sheet1.getCell(19, f)!=null){
                    Cell locIDCell=sheet1.getCell(19, f);
                    if(locIDCell.getContents()!=null){
                      String thisLocID=locIDCell.getContents();
                      if(!thisLocID.trim().equals("")){
                        placeholder.setLocation(thisLocID);
                        System.out.println("     Set locationID for indie "+indie.getIndividualID()+" to "+placeholder.getLocationID());
                      }
                    }
                  }
                  
                //let's get nickname
                  if(sheet1.getCell(1, f)!=null){
                    Cell nicknameCell=sheet1.getCell(1, f);
                    if(nicknameCell.getContents()!=null){
                      String thisnickname=nicknameCell.getContents();
                      if(!thisnickname.trim().equals("")){
                        indie.setNickName(thisnickname);
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
		      
		      } //end for
		      
		      
		      
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
		System.out.println("\n\nStarting thumbnail work!");

		int numThumbnailsToGenerate=thumbnailThese.size();
		String IDKey="";
		for(int q=0;q<numThumbnailsToGenerate;q++){
			IDKey=thumbnailThese.get(q);
			//ping a URL to thumbnail generator - Tomcat must be up and running
		    try
		    {

		    	System.out.println("Trying to render a thumbnail for: "+IDKey+ "as "+thumbnailTheseImages.get(q));
		    	String urlString=urlToThumbnailJSPPage+"?number="+IDKey+"&imageNum=1&imageName="+thumbnailTheseImages.get(q);
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




private static void copyFile(File thisFile, File outputFile){
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