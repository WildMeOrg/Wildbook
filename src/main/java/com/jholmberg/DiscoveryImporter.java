/**
 *
 */
package com.jholmberg;

import org.ecocean.Annotation;
//import the Shepherd Project Framework
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.Occurrence;
import org.ecocean.Shepherd;
import org.ecocean.Util;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.media.*;

//import basic IO
import java.io.*;
import java.util.*;
import org.ecocean.tag.*;

//import date-time formatter for the custom SPLASH date format
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.joda.time.format.*;
import org.json.JSONObject;

import java.text.SimpleDateFormat;

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
		//String pathToAccessFile="C:/Users/jholmber/Dropbox/RingedSeal/PHSdata_to_Wildme.mdb";
		String pathToAccessFile="/data/RingedSeal/PHSdata_to_Wildme.mdb";

		//String pathToUpdateFile="C:\\splash\\CRC SPLASHID additional sightings.mdb";
		//String rootDir="C:/apache-tomcat-8.0.24/webapps";
		String rootDir="/var/lib/tomcat7/webapps";
		String assetStorePath="/data/wildbook_data_dir/encounters";
		//String rootURL="http://localhost:8080";
		String rootURL="http://52.40.15.8";
		String encountersDirPath=assetStorePath+"/encounters";
		//String splashImagesDirPath="C:/Users/jholmber/Dropbox/RingedSeal/DISCOVERY_DATA";
		String splashImagesDirPath="/data/RingedSeal/DISCOVERY_DATA";
		String urlToThumbnailJSPPage=rootURL+"wildbook/resetThumbnail.jsp";
		String importDate="2016-05-17";
		String assetStoreURL=rootURL+"/wildbook_data_dir/encounters";


		//Shepherd
	//let's get our Shepherd Project structures built
    Shepherd myShepherd = new Shepherd(context);


		//AssetSyore work
		////////////////begin local //////////////
    myShepherd.beginDBTransaction();
		LocalAssetStore as = new LocalAssetStore("WWFSeals-Asset-Store", new File(assetStorePath).toPath(), assetStoreURL, true);
		myShepherd.getPM().makePersistent(as);
		myShepherd.commitDBTransaction();
////////////////end local //////////////


		//an arraylist for later thumbnail generation
		ArrayList<String> thumbnailThese=new ArrayList<String>();
		ArrayList<String> thumbnailTheseImages=new ArrayList<String>();



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
          try{
            numMatchingIdentifications++;
            processThisRow(thisRow, myShepherd, splashImagesDirPath, encountersDirPath, urlToThumbnailJSPPage,images, features,context,assetStorePath,importDate );
          }
          catch(Exception e){
            e.printStackTrace();
            System.out.println("Exception in row: "+numMatchingIdentifications);
            myShepherd.rollbackDBTransaction();
          }

        }

        myShepherd.beginDBTransaction();
        //define relationships between animals and set dead on last encounter
        Iterator<Map<String,Object>> featuresIter2 = features.iterator();
        System.out.println("....starting relationship logic.");
        while(featuresIter2.hasNext()){
              Map<String,Object> thisFeatureRow=featuresIter2.next();
              if(thisFeatureRow.get("INDIVIDUAL")!=null){
                String individualID=(String)thisFeatureRow.get("INDIVIDUAL");
                System.out.println("...looking for: "+individualID);
                if(myShepherd.getMarkedIndividual(individualID)!=null){
                  System.out.println("......in the row for: "+individualID);
                  MarkedIndividual indy=myShepherd.getMarkedIndividual(individualID);

                  //Mother
                  if(thisFeatureRow.get("Mother")!=null){

                      String momValue=((String)thisFeatureRow.get("Mother")).trim();
                      if(momValue.indexOf(" ")!=-1){momValue=momValue.substring(momValue.indexOf(" ")).trim();}


                      System.out.println("......has mother: "+momValue);
                      if(myShepherd.getMarkedIndividual(momValue)!=null){

                        if(myShepherd.getRelationship("familial", momValue, individualID)==null){

                          System.out.println("......referencing Mother by individualID: "+individualID);
                          //MarkedIndividual mom=myShepherd.getMarkedIndividual(momValue);
                          org.ecocean.social.Relationship myRel=new org.ecocean.social.Relationship("familial",individualID,momValue,"pup","mother");
                          myShepherd.getPM().makePersistent(myRel);
                          myShepherd.commitDBTransaction();
                          myShepherd.beginDBTransaction();
                        }


                      }
                      else if(myShepherd.getMarkedIndividualsByNickname(momValue).size()>0){
                        System.out.println("......referencing Mother by nickname: "+momValue);

                        MarkedIndividual mom=(MarkedIndividual)myShepherd.getMarkedIndividualsByNickname(momValue).get(0);
                        if(myShepherd.getRelationship("familial", mom.getIndividualID(), individualID)==null){

                          org.ecocean.social.Relationship myRel=new org.ecocean.social.Relationship("familial",individualID,mom.getIndividualID(),"pup","mother");
                          myShepherd.getPM().makePersistent(myRel);
                          myShepherd.commitDBTransaction();
                          myShepherd.beginDBTransaction();
                        }

                      }

                  }
                  System.out.println("......DONE with mother logic!!!!");

                  //pups
                  for(int w=1;w<4;w++){
                    String pupString="Pup"+w;

                    if(thisFeatureRow.get(pupString)!=null){
                      System.out.println("......looking at "+pupString);
                      String pupValue=((String)thisFeatureRow.get(pupString)).trim();
                      pupValue=pupValue.replaceAll(",", "");
                      pupValue=pupValue.replaceAll("twins", "");
                      StringTokenizer strPup=new StringTokenizer(pupValue," ");
                      int numTokens=strPup.countTokens();
                      if(numTokens==1){indy.setDynamicProperty(pupString, strPup.nextToken());}
                      else if(numTokens>1){

                        String yearString=strPup.nextToken();
                        SimpleDateFormat f = new SimpleDateFormat("yyyy");
                        Date d = f.parse(yearString);
                        long milliseconds = d.getTime();
                        while(strPup.hasMoreTokens()){

                          String pupName=strPup.nextToken();

                          if(myShepherd.isMarkedIndividual(pupName)){
                            System.out.println("......found "+pupName+" is the pup of "+individualID);
                            MarkedIndividual puppy=myShepherd.getMarkedIndividual(pupName);

                            if(myShepherd.getRelationship("familial", puppy.getIndividualID(), individualID)==null){
                              org.ecocean.social.Relationship myRel=new org.ecocean.social.Relationship("familial",individualID,puppy.getIndividualID(),"mother","pup");
                              myShepherd.getPM().makePersistent(myRel);
                              myShepherd.commitDBTransaction();
                              myShepherd.beginDBTransaction();
                            }

                            puppy.setTimeOfBirth(milliseconds);

                            myShepherd.commitDBTransaction();
                            myShepherd.beginDBTransaction();
                          }
                          else if(myShepherd.getMarkedIndividualsByNickname(pupName).size()>0){
                            MarkedIndividual puppy=(MarkedIndividual)myShepherd.getMarkedIndividualsByNickname(pupName).get(0);

                            if(myShepherd.getRelationship("familial", puppy.getIndividualID(), individualID)==null){
                              org.ecocean.social.Relationship myRel=new org.ecocean.social.Relationship("familial",individualID,puppy.getIndividualID(),"mother","pup");
                              myShepherd.getPM().makePersistent(myRel);
                              myShepherd.commitDBTransaction();
                              myShepherd.beginDBTransaction();
                            }
                            puppy.setTimeOfBirth(milliseconds);

                            myShepherd.commitDBTransaction();
                            myShepherd.beginDBTransaction();
                          }

                        }



                      }
                    }

                  }

                }


              }


        }

		}
		catch(Exception e){
			e.printStackTrace();
		}



		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();

		System.out.println("!!!DONE!!!");

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
					            String importDate
									   ){

		//Itertaor for primary keys

	  boolean exists=false;

    ArrayList<Annotation> newAnnotations = new ArrayList<Annotation>();

  //create the encounter
    Encounter enc=new Encounter();


		//parse out MarkedIndividual data
		String markedIndividualName=null;
		if(thisRow.get("INDIVIDUAL")!=null){
		  markedIndividualName=((String)thisRow.get("INDIVIDUAL")).trim();
	    enc.setIndividualID(markedIndividualName);
		}


	//set encounter number
    String IDKey=((Integer)thisRow.get("PKey")).toString().trim();

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
      location=((String)thisRow.get("LOCATION")).trim();
      enc.setLocation(location);
    }

    String locationID=null;
    if(thisRow.get("STUDY_SITE")!=null){
      locationID=((String)thisRow.get("STUDY_SITE")).trim();
      enc.setLocationID(locationID);
    }

		enc.setLivingStatus("alive");


		if((String)thisRow.get("Can be shown in public view")!=null){
			enc.setDynamicProperty("PublicView",(String)thisRow.get("Can be shown in public view"));
		}

		if((Boolean)thisRow.get("TYPE_SPECIMEN")!=null){
			enc.setDynamicProperty("TYPE_SPECIMEN",((Boolean)thisRow.get("TYPE_SPECIMEN")).toString());
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
		enc.setSubmitterName("WWF Finland");
		enc.setSubmitterEmail("");
		enc.setSubmitterPhone("");
		enc.setSubmitterAddress("");
    if(thisRow.get("PHOTOGRAPHER")!=null){
      enc.setPhotographerName((String)thisRow.get("PHOTOGRAPHER"));
    }


    //get lat and long
    if(thisRow.get("LATITUDE")!=null){
      enc.setDecimalLatitude((Double)thisRow.get("LATITUDE"));
    }
    if(thisRow.get("LONGITUDE")!=null){
      enc.setDecimalLongitude((Double)thisRow.get("LONGITUDE"));
    }






		enc.setInformOthers("");

		if((Object)thisRow.get("DATE")!=null){
			String originalDate=((Object)thisRow.get("DATE")).toString();
			//System.out.println("     "+originalDate);

			DateTimeFormatter splashFMT = DateTimeFormat.forPattern("EEE MMM dd HH:mm:ss zzz yyyy");
			DateTime dt = splashFMT.parseDateTime(originalDate);
			enc.setDay(dt.getDayOfMonth());
			enc.setMonth(dt.getMonthOfYear());
			enc.setYear(dt.getYear());
			enc.resetDateInMilliseconds();
			System.out.println("...Date is: "+enc.getDate());
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
      System.out.println("...Date is: "+enc.getDate());
    }





//add photo

		if((thisRow.get("FILENAME")!=null)&&(thisRow.get("FILENAME")!=null)&&(((Object)thisRow.get("FILENAME")).toString().trim().equals(((Object)thisRow.get("FILENAME")).toString().trim()))){

		  System.out.println("Starting image processing...");

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

            /*
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

            }
            catch(IOException ioe){
              System.out.println("IOException on file transfer for: "+imageName);
              ioe.printStackTrace();
            }
            }
            */

            //now add it to the encounter
            myShepherd.beginDBTransaction();
            AssetStore astore = AssetStore.getDefault(myShepherd);
            JSONObject sp = astore.createParameters(new File(enc.subdir() + File.separator + thisFile.getName()));
            sp.put("key", Util.hashDirectories(enc.getCatalogNumber()) + "/" +  thisFile.getName());
            MediaAsset ma = new MediaAsset(astore, sp);
            myShepherd.getPM().makePersistent(ma);


              //copy in the file
              ma.addLabel("_original");
                try{
                  ma.copyIn(thisFile);
                  ma.updateMetadata();
                }
                catch(Exception io){io.printStackTrace();}
                myShepherd.commitDBTransaction();
                myShepherd.beginDBTransaction();


                Annotation annot=new Annotation(Util.taxonomyString(enc.getGenus(), enc.getSpecificEpithet()), ma);
                myShepherd.getPM().makePersistent(annot);
                myShepherd.commitDBTransaction();
                myShepherd.beginDBTransaction();
                newAnnotations.add(annot);
                myShepherd.commitDBTransaction();

          }

          enc.setAnnotations(newAnnotations);

    }

		//generate everything else we need for the MediaAsset
    enc.refreshAssetFormats(myShepherd);

		enc.setDynamicProperty("ImportDate", importDate);
		System.out.println("Finished importDate.");


		//let's persist the encounter
		enc.resetDateInMilliseconds();

		enc.setState("approved");

		if(exists){
		  myShepherd.beginDBTransaction();
		  enc.resetDateInMilliseconds();
		  myShepherd.commitDBTransaction();
		}
		else{
		    myShepherd.storeNewEncounter(enc, IDKey);
		}

		//temp try creating encounters to populate database
		myShepherd.beginDBTransaction();
		Occurrence occur=new Occurrence(enc.getCatalogNumber(),enc);
		MetalTag tag=new MetalTag(enc.getCatalogNumber(),"unknown");
		enc.addMetalTag(tag);
		occur.addEncounter(enc);
		myShepherd.getPM().makePersistent(occur);
		myShepherd.getPM().makePersistent(tag);
		myShepherd.commitDBTransaction();
		myShepherd.beginDBTransaction();
		occur.removeEncounter(enc);
		myShepherd.getPM().deletePersistent(occur);
		enc.removeMetalTag(tag);
		myShepherd.getPM().deletePersistent(tag);
		myShepherd.commitDBTransaction();
		//end occurrence creation


		//START MARKED INDIVIDUAL LOGIC

		//let's check if the MarkedIndividual exists and create it if not
		System.out.println("Starting indy logic for: "+markedIndividualName);
		myShepherd.beginDBTransaction();
		try{
			if(myShepherd.isMarkedIndividual(markedIndividualName)){
			  System.out.println(markedIndividualName+" is already a marked individual.");
				MarkedIndividual markie=myShepherd.getMarkedIndividual(markedIndividualName);
				enc.setSex(markie.getSex());
				markie.addComments("<p>Added encounter "+enc.getCatalogNumber()+".</p>");

				enc.setMatchedBy("Visual inspection");
				markie.addEncounter(enc, context);
				markie.refreshDependentProperties(context);
				myShepherd.commitDBTransaction();

			}
			else{

			  System.out.println("...is a new marked individual.");
				MarkedIndividual newWhale=new MarkedIndividual(markedIndividualName, enc);




				enc.setMatchedBy("Unmatched first encounter");
				newWhale.addComments("<p>Created "+markedIndividualName+" with the SplashMigratorApp.</p>");
				newWhale.setDateTimeCreated(ServletUtilities.getDate());


        Iterator<Map<String,Object>> featuresIter = features.iterator();
        while(featuresIter.hasNext()){
          Map<String,Object> thisFeatureRow=featuresIter.next();

            //now check for the markedindividual
            if(thisFeatureRow.get("INDIVIDUAL")!=null){
              String featureIndividualName=((String)thisFeatureRow.get("INDIVIDUAL")).toString().trim();
              if(featureIndividualName.equals(markedIndividualName)){
                System.out.println("Found a matching MarkedIndividual name!");
                //we have a match and can assign our indy some additional attributes
                if(thisFeatureRow.get("Sex")!=null){
                  String thisSex=((String)thisFeatureRow.get("Sex")).toLowerCase();
                  newWhale.setSex(thisSex);
                  System.out.println("...set indy sex: "+thisSex);
                }

                //dead
                if(thisFeatureRow.get("Dead")!=null){
                  String thisDead=((String)thisFeatureRow.get("Dead")).toLowerCase().replaceAll("yes", "").trim();
                  SimpleDateFormat f = new SimpleDateFormat("yyyy");
                  Date d = f.parse(thisDead);
                  long milliseconds = d.getTime();
                  newWhale.setTimeOfDeath(milliseconds);
                  System.out.println("...set dead year: "+thisDead);
                }


                if((String)thisFeatureRow.get("Name")!=null){
                  String thisNickname=(String)thisFeatureRow.get("Name");
                  newWhale.setNickName(thisNickname);

                  System.out.println("...set indy nickname: "+thisNickname);
                }

                if((String)thisFeatureRow.get("Additional code")!=null){
                  String addCode=(String)thisFeatureRow.get("Additional code");
                  newWhale.setAlternateID(addCode);
                  System.out.println("...set additional code: "+addCode);
                }

                if((String)thisFeatureRow.get("Age")!=null){
                  String lifer=((String)thisFeatureRow.get("Age")).toLowerCase();
                  enc.setLifeStage(lifer);
                  System.out.println("...set additional code: "+lifer);
                }


                //TAG number
                if((String)thisFeatureRow.get("Tagnumber")!=null){
                  String altID="";
                  if(newWhale.getAlternateID()!=null)altID=newWhale.getAlternateID();
                  String tagNumber=(String)thisFeatureRow.get("Tagnumber");
                  newWhale.setAlternateID(altID+(tagNumber).toLowerCase());

                  myShepherd.commitDBTransaction();
                  myShepherd.beginDBTransaction();
                  System.out.println("...set alternateID: "+altID);
                }



                if((thisFeatureRow.get("Other")!=null)&& (!((String)thisFeatureRow.get("Other")).trim().equals("")) ){
                  String otherValue=((String)thisFeatureRow.get("Other")).trim().replaceAll(",", "").replaceAll(";", "");
                  StringTokenizer strOther=new StringTokenizer(otherValue," ");
                  int numTokens=strOther.countTokens();
                  ArrayList<String> otherTokens=new ArrayList<String>();
                  while(strOther.hasMoreTokens()){
                    otherTokens.add(strOther.nextToken());
                  }

                  for(int k=0;k<numTokens;k++){
                    String tokenValue=otherTokens.get(k);

                    //year of birth
                    if(tokenValue.toLowerCase().trim().equals("born")){
                      String string_date = otherTokens.get(k+1);

                      SimpleDateFormat f = new SimpleDateFormat("yyyy");
                      Date d = f.parse(string_date);
                      long milliseconds = d.getTime();
                      newWhale.setTimeOfBirth(milliseconds);
                    }



                    //carcass dp
                    if(tokenValue.toLowerCase().trim().equals("carcass")){
                      newWhale.setDynamicProperty("Carcass", ("Carcass "+otherTokens.get(k+1)));
                    }

                    //set general Other DP as  catch-all
                    newWhale.setDynamicProperty("Other", otherValue);
                    System.out.println("...set Other value: "+otherValue);


                  }


                }

                //living status
                if((String)thisFeatureRow.get("Dead")!=null){
                  String deadValue=(String)thisFeatureRow.get("Dead");
                  newWhale.setDynamicProperty("Dead", deadValue);
                  System.out.println("...set dead value: "+deadValue);
                }



              }

            }

        }



				enc.addComments("<p>Added to newly marked individual "+markedIndividualName+" by the SplashMigratorApp.</p>");
				newWhale.refreshDependentProperties(context);
				myShepherd.commitDBTransaction();

				myShepherd.addMarkedIndividual(newWhale);
				System.out.println("New indy "+markedIndividualName+" was successfully stored.");




			}
		}
		catch(Exception e){
			e.printStackTrace();
			myShepherd.rollbackDBTransaction();
		}



		//END MARKED INDIVIDUAL LOGIC



	}








public static String getExactFileName(File f) {
  String returnVal;
  try {
    returnVal = f.getCanonicalPath();
    returnVal =
      returnVal.substring(returnVal.lastIndexOf(File.separator)+1);
  }
  catch(IOException e) {
    e.printStackTrace();
    returnVal = "";
  }
  return returnVal;
}


}
