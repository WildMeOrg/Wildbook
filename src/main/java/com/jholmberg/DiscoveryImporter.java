/**
 * 
 */
package com.jholmberg;

import org.ecocean.Annotation;
import org.ecocean.CommonConfiguration;
//import the Shepherd Project Framework
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;
import org.ecocean.SinglePhotoVideo;
import org.ecocean.Util;
import org.ecocean.Keyword;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.genetics.*;
import org.ecocean.media.AssetStore;
import org.ecocean.media.MediaAsset;

//import basic IO
import java.io.*;
import java.util.*;
import java.net.*;

//import date-time formatter for the custom SPLASH date format
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.joda.time.format.*;
import org.json.JSONObject;

//import jackcess
//import com.healthmarketscience.*;
import com.healthmarketscience.jackcess.*;
//import com.healthmarketscience.jackcess.query.*;
//import com.healthmarketscience.jackcess.scsu.*;


/**
 * @author jholmber
 *
 */
public class DiscoveryImporter {

	/**
	 * @param args
	 */
	
  
  //TODO: fix encounter directory path for file copy
	
	public static void main(String[] args) {
		
	  String context="context0";
	  
		//a necessary primary key iterator for genetic analyses
		Integer myKey=new Integer(0);
		
		//initial environment config
		String pathToAccessFile="C:/Users/jholmber/Dropbox/RingedSeal/PHSdata_to_Wildme.mdb";
		
		
		//String pathToUpdateFile="C:\\splash\\CRC SPLASHID additional sightings.mdb";
		String rootDir="C:/apache-tomcat-8.0.24/webapps";
		String encountersDirPath="C:/apache-tomcat-8.0.24/webapps/wildbook_data_dir/encounters";
		String splashImagesDirPath="C:/Users/jholmber/Dropbox/RingedSeal/DISCOVERY_DATA";
		String urlToThumbnailJSPPage="http://www..flukebook.org/resetThumbnail.jsp";
		String baseDir="C:/apache-tomcat-8.0.24/webapps/wildbook_data_dir";
		String importDate="2016-05-13";


		
		
		//an arraylist for later thumbnail generation
		ArrayList<String> thumbnailThese=new ArrayList<String>();
		ArrayList<String> thumbnailTheseImages=new ArrayList<String>();
		
		//let's get our Shepherd Project structures built
		Shepherd myShepherd = new Shepherd(context);
		
    //set up our media objects
    AssetStore astore = AssetStore.getDefault(myShepherd);

		
		//let's load our Access database
		File accessDB=new File(pathToAccessFile);

		
		try{
			
  			//lets' get to work!!!!!!!
  			Database db=Database.open(accessDB);
  
  			File copyImagesFromDir=new File(splashImagesDirPath);
  			File encountersRootDir=new File(encountersDirPath);
  			
  			//update changes
  			Table images=db.getTable("IMAGES");
  			Table features=db.getTable("Features");
  			
  	    Iterator<Map<String,Object>> tIdentificationsIterator = images.iterator();
        int numMatchingIdentifications=0;
        while(tIdentificationsIterator.hasNext()){
          Map<String,Object> thisRow=tIdentificationsIterator.next();

            numMatchingIdentifications++;
            processThisRow(thisRow, myShepherd, splashImagesDirPath, encountersDirPath, urlToThumbnailJSPPage,images, features,context,baseDir,astore,importDate );
            
            
      
        }

		}
		catch(Exception e){
			e.printStackTrace();
		}

		
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
	

	
	private static void processThisRow(
									   Map<String,Object> thisRow, 
									   Shepherd myShepherd, 
									   String splashImagesDirPath, 
									   String encountersRootDirPath, 
									   String urlToThumbnailJSPPage,
									   Table images,
									   Table features,
									   String context,
									   String baseDir,
									   AssetStore astore,
					            String importDate
									   ){
		
		//Itertaor for primary keys
		
	  boolean exists=false;
	  
    ArrayList<Annotation> newAnnotations = new ArrayList<Annotation>();
		
		//create the encounter
		String markedIndividualName=null;
		if(thisRow.get("INDIVIDUAL")!=null){
		  markedIndividualName=((Integer)thisRow.get("INDIVIDUAL")).toString().trim();
	    
		}
		Encounter enc=new Encounter();
		
	//set encounter number
    String IDKey=((Integer)thisRow.get("PKEY")).toString().trim();
    
        //String guid = CommonConfiguration.getGlobalUniqueIdentifierPrefix(context) + encID;

    enc.setCatalogNumber(IDKey);
    File encDir = new File(enc.dir(baseDir));
    //File encsDir=new File(encountersRootDirPath);
    //File encDir=new File(encsDir, IDKey);
    System.out.println(" fffffffffffffffffffffI am trying to create: "+encDir.getAbsolutePath());
    if(!encDir.exists()){encDir.mkdirs();}
    
    
    if(myShepherd.isEncounter(IDKey)){
      enc=(Encounter)myShepherd.getPM().detachCopy(myShepherd.getEncounter(IDKey));
      exists=true;
    }
    
		enc.setOccurrenceRemarks("");
		if(markedIndividualName!=null){
		  enc.assignToMarkedIndividual(markedIndividualName);
		}
		enc.setMatchedBy("Visual inspection");
		enc.setDWCDateAdded(ServletUtilities.getDate());
		enc.setDWCDateLastModified(ServletUtilities.getDate());
		
		enc.setGenus("Pusa");
		enc.setSpecificEpithet("hispida saimensis");
		
		LocalDateTime dt2 = new LocalDateTime();
		DateTimeFormatter fmt = ISODateTimeFormat.date();
    String strOutputDateTime = fmt.print(dt2);
    enc.setDWCDateAdded(strOutputDateTime);
    
    enc.setDWCDateLastModified(strOutputDateTime);
    enc.setDWCDateAdded(new Long(dt2.toDateTime().getMillis()));
    
    String location=null;
    if(thisRow.get("LOCATION")!=null){
      location=((Integer)thisRow.get("LOCATION")).toString().trim();
      enc.setLocation(location);
    }
		
    String locationID=null;
    if(thisRow.get("STUDY_SITE")!=null){
      locationID=((Integer)thisRow.get("STUDY_SITE")).toString().trim();
      enc.setLocationID(locationID);
    }

		enc.setLivingStatus("alive");
		
		
		if((String)thisRow.get("Can be shown in public view")!=null){
			enc.setDynamicProperty("PublicView",(String)thisRow.get("Can be shown in public view"));
		}
		
		if((String)thisRow.get("TYPE_SPECIMEN")!=null){
			enc.setDynamicProperty("TYPE_SPECIMEN",(String)thisRow.get("TYPE_SPECIMEN"));
		}
		
		enc.setComments("");
		if((String)thisRow.get("QUALITY")!=null){
			enc.setDynamicProperty("QUALITY",(String)thisRow.get("QUALITY"));
			enc.addComments((String)thisRow.get("QUALITY"));
		}
		
    if((String)thisRow.get("CATEGORY")!=null){
      enc.setDynamicProperty("CATEGORY",(String)thisRow.get("CATEGORY"));
    }
    
    if((String)thisRow.get("ASPECT")!=null){
      enc.setDynamicProperty("ASPECT",(String)thisRow.get("ASPECT"));
    }
    
    if((String)thisRow.get("RINGS")!=null){
      enc.setDynamicProperty("RINGS",(String)thisRow.get("RINGS"));
    }

		
		
		enc.setCatalogNumber(IDKey);
		System.out.println("Processing: "+IDKey);
		
		//expose with TapirLink
		enc.setOKExposeViaTapirLink(false);
		
		//state
		enc.setState("approved");
		
		//submitter
		enc.setSubmitterEmail("");
		enc.setSubmitterPhone("");
		enc.setSubmitterAddress("");
    if((String)thisRow.get("PHOTOTGRAPHER")!=null){
      enc.setPhotographerName((String)thisRow.get("PHOTOTGRAPHER"));
    }
		


			
		
		
		enc.setInformOthers("");

		if((Object)thisRow.get("DATE")!=null){
			String originalDate=((Object)thisRow.get("DATE")).toString().replaceAll(" EDT", "").replaceAll(" EST", "").replaceAll(" UTC", "");
			//System.out.println("     "+originalDate);
			
			DateTimeFormatter splashFMT = DateTimeFormat.forPattern("MM/dd/yyyy");
			DateTime dt = splashFMT.parseDateTime(originalDate);
			enc.setDay(dt.getDayOfMonth());
			enc.setMonth(dt.getMonthOfYear());
			enc.setYear(dt.getYear());
			enc.resetDateInMilliseconds();
		}
		
    if((Object)thisRow.get("TIME")!=null){
      String originalTime=((Object)thisRow.get("TIME")).toString().replaceAll(" EDT", "").replaceAll(" EST", "").replaceAll(" UTC", "").trim();
      int hourAdjuster=0;
      if(originalTime.indexOf("PM")!=-1){hourAdjuster=12;}
      originalTime.replaceAll("PM","").replaceAll("AM","").trim();
      
      StringTokenizer str=new StringTokenizer(originalTime, ":");
      int hour=(new Integer(str.nextToken())).intValue()+hourAdjuster;
      enc.setHour(hour);
      enc.setMinutes(str.nextToken());
      enc.resetDateInMilliseconds();
    }

		

		
		
//add photo
		
		if((thisRow.get("FILENAME")!=null)&&(thisRow.get("FILENAME")!=null)&&(((Object)thisRow.get("FILENAME")).toString().trim().equals(((Object)thisRow.get("FILENAME")).toString().trim()))){
      
        String imageName=((Object)thisRow.get("FILENAME")).toString().trim(); 
        File parentDir=new File(splashImagesDirPath+"/"+enc.getIndividualID());
        File thisFile = new File(parentDir,imageName);  
        
        //let's check for and try to fix the .jpg vs .JPG issue
        if(!thisFile.exists()){
          if(thisFile.getName().endsWith("jpg")){
            thisFile = new File(parentDir,imageName.replaceAll(".jpg", ".JPG"));
          }
          else if(thisFile.getName().endsWith("JPG")){
            thisFile = new File(parentDir,imageName.replaceAll(".JPG", ".jpg"));
          }
        }
        
          //check if file exists
          if(thisFile.exists()){
            
            
            //copy it
            //File encountersRootDirPathLocalFile=new File(encountersRootDirPath+"/"+IDKey);
            File outputFile = new File(encDir,imageName);
            
            
            if(!outputFile.exists()){
            try{
  
                  BufferedInputStream bis = new BufferedInputStream(new FileInputStream(thisFile), 4096);
                  BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputFile), 4096);
                       int theChar;
                       while ((theChar = bis.read()) != -1) {
                          bos.write(theChar);
                       }
                    bos.close();
                    bis.close();
              System.out.println("     !@@!@!#!@Completed copy of "+imageName+" "+IDKey);
  
              System.out.println("     !@@!@!#!@Completed copy of "+imageName+" "+IDKey);
  
              
              System.out.println("     !@@!@!#!@Completed copy of "+imageName+" "+IDKey);
  
              
  
            }
            catch(IOException ioe){
              System.out.println("IOException on file transfer for: "+imageName);
              ioe.printStackTrace();
            }
            }
            
            //now add it to the encounter
            //SinglePhotoVideo vid=new SinglePhotoVideo(enc.getCatalogNumber(),imageName, (encDir+"/"+imageName));
            JSONObject sp = astore.createParameters(new File(enc.subdir() + File.separator + thisFile.getName()));
            sp.put("key", Util.hashDirectories(enc.getCatalogNumber()) + "/" +  thisFile.getName());
            MediaAsset ma = new MediaAsset(astore, sp);
            File tmpFile = ma.localPath().toFile();  //conveniently(?) our local version to save ma.cacheLocal() from having to do anything?
            File tmpDir = tmpFile.getParentFile();
            if (!tmpDir.exists()) tmpDir.mkdirs();
            System.out.println("attempting to write uploaded file to " + tmpFile);
            try {
              if (tmpFile.exists()) {
                ma.addLabel("_original");
                ma.copyIn(tmpFile);
                ma.updateMetadata();
                newAnnotations.add(new Annotation(ma, Util.taxonomyString(enc.getGenus(), enc.getSpecificEpithet())));
            } 
            } 
            catch (Exception ex) {
                System.out.println("Could not write " + tmpFile + ": " + ex.toString());
            }

            
            
            
  
   
            
          } 
          
          enc.setAnnotations(newAnnotations);

    }
				
		
		enc.setDynamicProperty("ImportDate", importDate);
		
		
		//let's persist the encounter
		enc.resetDateInMilliseconds();

		if(exists){
		  myShepherd.beginDBTransaction();
		  enc.resetDateInMilliseconds();
		  myShepherd.commitDBTransaction();
		}
		else{
		    myShepherd.storeNewEncounter(enc, IDKey);
		}
		
		
		
		//START MARKED INDIVIDUAL LOGIC
		
		//let's check if the MarkedIndividual exists and create it if not
		myShepherd.beginDBTransaction();
		try{
			if(myShepherd.isMarkedIndividual(markedIndividualName)){
				MarkedIndividual markie=myShepherd.getMarkedIndividual(markedIndividualName);
				enc.setSex(markie.getSex());
				markie.addComments("<p>Added encounter "+enc.getCatalogNumber()+".</p>");


				markie.addEncounter(enc, context);
				markie.refreshDependentProperties(context);
				myShepherd.commitDBTransaction();
			
			}
			else{
			
				MarkedIndividual newWhale=new MarkedIndividual(markedIndividualName, enc);

				
				
				
				enc.setMatchedBy("Unmatched first encounter");
				newWhale.addComments("<p>Created "+markedIndividualName+" with the SplashMigratorApp.</p>");
				newWhale.setDateTimeCreated(ServletUtilities.getDate());
				

        Iterator<Map<String,Object>> featuresIter = features.iterator();
        while(featuresIter.hasNext()){
          Map<String,Object> thisFeatureRow=featuresIter.next();

            //now check for the markedindividual
            if(thisFeatureRow.get("INDIVIDUAL")!=null){
              String featureIndividualName=((Integer)thisRow.get("INDIVIDUAL")).toString().trim();
              if(featureIndividualName.equals(markedIndividualName)){
                
                //we have a match and can assign our indy some additional attributes
                if((String)thisFeatureRow.get("Sex")!=null){
                  newWhale.setSex(((String)thisRow.get("Sex")).toLowerCase());
                }
                
                if((String)thisFeatureRow.get("Name")!=null){
                  newWhale.setNickName((String)thisRow.get("Name"));
                }
                
                if((String)thisFeatureRow.get("Additional code")!=null){
                  newWhale.setAlternateID((String)thisRow.get("Additional code"));
                }
                
                if((String)thisFeatureRow.get("Age")!=null){
                  enc.setLifeStage(((String)thisRow.get("Age")).toLowerCase());
                }
                
                
                //TAG number
                
                
                
                //living status
                
                
                //Other
                
                
                //Pup1
                
                
                //Pup2
                
                
                
                //Pup3
                
                
                //Mother
                
                
                
                
                
              }
              
            }
      
        }
				
				
				
				enc.addComments("<p>Added to newly marked individual "+markedIndividualName+" by the SplashMigratorApp.</p>");
				myShepherd.commitDBTransaction();
				newWhale.refreshDependentProperties(context);
				myShepherd.addMarkedIndividual(newWhale);

			}
		}
		catch(Exception e){
			e.printStackTrace();
			myShepherd.rollbackDBTransaction();
		}
		
		
		//END MARKED INDIVIDUAL LOGIC
		
		
		
	}
	
	
	
	
private static Locus getLocus(String locusName, Map<String,Object> thisMarkerRow){
	System.out.println("     ***!!!***lOOKING FOR "+locusName+"-1 and "+locusName+"-2.");
	
	Integer position1=0;
	Integer position2 =0; 	
	if((thisMarkerRow.get(locusName+"-1")!=null)&&(thisMarkerRow.get(locusName+"-2")!=null)){
		
		position1= ((Double)thisMarkerRow.get(locusName+"-1")).intValue();
		position2= ((Double)thisMarkerRow.get(locusName+"-2")).intValue();	
	}
	System.out.println("     ***!!!***position 1 is: "+position1+" and position2 is: "+position2);
	Locus l=new Locus(locusName,position1,position2);
	System.out.println("     ***!!!***Locus details: "+l.getHTMLString());
	return l;
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