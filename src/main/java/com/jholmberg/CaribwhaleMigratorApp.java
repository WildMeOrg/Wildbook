/**
 *
 */
package com.jholmberg;

//import the Shepherd Project Framework
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
		String encountersDirPath="C:/apache-tomcat-7.0.32/webapps/shepherd_data_dir";
		String splashImagesDirPath="C:/caribwhale/TIFs";
		String pathToExcel = "C:/caribwhale/atlantic/All_Individuals_SUPINFO_20130624.xls";
		String pathToExcel2 = "C:\\caribwhale\\atlantic\\allIDN19842012_20130624.xls";
		
		
		/**
		 * For thumbnail generation
		 */
		String urlToThumbnailJSPPage="http://www.splashcatalog.org/latestgenegis/resetThumbnail.jsp";
		ArrayList<String> thumbnailThese=new ArrayList<String>();
		ArrayList<String> thumbnailTheseImages=new ArrayList<String>();

		//let's get our Shepherd Project structures built
		//Shepherd myShepherd = new Shepherd();

		

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
			
			
			//STEP 3 - obtain data about each MarkedIndividual from Excel2
			//File excel2File=new File(pathToExcel2);
			Workbook w;
		    try {
		      File path=new File(pathToExcel2);
		      w = Workbook.getWorkbook(path);
		      // Get the first sheet
		      Sheet sheet = w.getSheet("Pix");
		      
		      
		      
		      for(int y=0;y<numIndies;y++){
		        MarkedIndividual indie=indies.get(y);
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
		              
		              

                  
                  String catNumber=Integer.toString(i);
		              enc.setCatalogNumber(catNumber);
		              
                  //let's check for the photo assignment
                  String indiesFilename=idMap.get(indie.getIndividualID());
                  String sYear=Integer.toString(enc.getYear());
                  String sMonth=Integer.toString(enc.getMonth());
                  if(sMonth.length()<2){sMonth="0"+sMonth;}
                  String sDay=Integer.toString(enc.getDay());
                  if(sDay.length()<2){sDay="0"+sDay;}
                  if(indiesFilename.indexOf((sYear+sMonth+sDay))!=-1){
         
                    SinglePhotoVideo sing = new SinglePhotoVideo(catNumber, indiesFilename, (splashImagesDirPath+"/"+indiesFilename));
                    enc.addSinglePhotoVideo(sing);
                  }
                  
		              indie.addEncounter(enc);
		            }
		            
		          }
		        System.out.println("Found "+indie.getEncounters().size()+" encounters for the indie "+indie.getIndividualID());
		      } //end for
		      
		      
		      
		    } catch (BiffException e) {
		      e.printStackTrace();
		    }
			


		}
		catch(Exception e){
			e.printStackTrace();
		}
		//myShepherd.closeDBTransaction();

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









}