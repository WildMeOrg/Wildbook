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
//import com.healthmarketscience.*;
import com.healthmarketscience.jackcess.*;
//import com.healthmarketscience.jackcess.query.*;
//import com.healthmarketscience.jackcess.scsu.*;

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

		//a necessary primary key iterator for genetic analyses
		Integer myKey=new Integer(0);

		//initial environment config
		String pathToAccessFile="C:/caribwhale/atlantic/AtlanticCatalogue.mdb";

		//String pathToUpdateFile="C:\\splash\\CRC SPLASHID additional sightings.mdb";

		String encountersDirPath="C:/apache-tomcat-7.0.32/webapps/shepherd_data_dir";
		String splashImagesDirPath="C:/caribwhale/TIFs";
		
		
		/**
		 * For thumbnail generation
		 */
		String urlToThumbnailJSPPage="http://www.splashcatalog.org/latestgenegis/resetThumbnail.jsp";
		ArrayList<String> thumbnailThese=new ArrayList<String>();
		ArrayList<String> thumbnailTheseImages=new ArrayList<String>();

		//let's get our Shepherd Project structures built
		//Shepherd myShepherd = new Shepherd();

		//let's load our Access database
		File accessDB=new File(pathToAccessFile);
		//File updateDB=new File(pathToUpdateFile);

		try{

			//lets' get to work!!!!!!!
			Database db=Database.open(accessDB);
			System.out.println("I have loaded the database!");
			//Database uDB=Database.open(updateDB);
			File copyImagesFromDir=new File(splashImagesDirPath);
			File encountersRootDir=new File(encountersDirPath);

			//update changes
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