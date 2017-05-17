/**
 * 
 */
package org.ecocean.servlet.importer;

import org.ecocean.CommonConfiguration;
//import the Shepherd Project Framework
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;
import org.ecocean.SinglePhotoVideo;
import org.ecocean.Keyword;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.genetics.*;

//import basic IO
import java.io.*;
import java.util.*;
import java.net.*;

//import date-time formatter for the custom SPLASH date format
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
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
public class AccessImport {

  /**
   * @param args
   */
  
  
  //TODO: fix encounter directory path for file copy
  
  public static void main(String[] args) {
    
    String context="context0";
    
    //a necessary primary key iterator for genetic analyses
    Integer myKey=new Integer(0);
    
    //initial environment config
    String pathToAccessFile="/var/www/webadmin/data/SPLASH All Seasons.mdb";
    
    String pathToUpdateFile="/var/www/webadmin/data/CRC SPLASHID additional sightings.mdb";
    
    //String pathToUpdateFile="C:\\splash\\CRC SPLASHID additional sightings.mdb";
    String rootDir="/opt/tomcat7/webapps";
    String encountersDirPath="/opt/tomcat7/webapps/caribwhale_data_dir/encounters";
    String splashImagesDirPath="/var/www/webadmin/data/splash_source_images";
    String urlToThumbnailJSPPage="http://www..flukebook.org/resetThumbnail.jsp";
    
    
    //final String baseDir = ServletUtilities.dataDir(context, rootDir);
    
    String baseDir="/opt/tomcat7/webapps/caribwhale_data_dir";
    
    //an arraylist for later thumbnail generation
    ArrayList<String> thumbnailThese=new ArrayList<String>();
    ArrayList<String> thumbnailTheseImages=new ArrayList<String>();
    
    //let's get our Shepherd Project structures built
    Shepherd myShepherd = new Shepherd(context);
    
    //let's load our Access database
    File accessDB=new File(pathToAccessFile);
    File updateDB=new File(pathToUpdateFile);
    
    try{
      
      //lets' get to work!!!!!!!
      Database db=Database.open(accessDB);
      Database uDB=Database.open(updateDB);
      File copyImagesFromDir=new File(splashImagesDirPath);
      File encountersRootDir=new File(encountersDirPath);
      
      //update changes
      Table tDailyEffort=db.getTable("tDailyEffort");
      Table tSightings=db.getTable("tSightings");
      Table tIdentifications=db.getTable("tIdentifications");
      


      
      
      Table tSPLASHIDFilenames=db.getTable("tSPLASHIDFilenames");
      Table tSPLASHIDSexes=db.getTable("tSPLASHIDSexes");
      Table tFlukeQualCodes=db.getTable("tFlukeQualCodes");
      Table tBehaviorIndex=db.getTable("ltIndBeh");
      Table tRegion=db.getTable("ltRegion");
      Table ltResearchGroup=db.getTable("ltResearch Group");
      Table tSampleLabData=db.getTable("tSampleLabData");
      Table haplotypes=db.getTable("OSU Sex-Hap Nov 2011");
      Table tSampleMicrosatData=db.getTable("tSampleMicrosatData");
      
      System.out.println("Loading Filename-SPLASHID...");
      Table tBestFilenames=db.getTable("Filename-SPLASHID");
      Iterator<Map<String,Object>> tBestFilenamesIterator = tBestFilenames.iterator();
      TreeMap<String,String> bestFilenamesMap = new TreeMap<String,String>();
      while(tBestFilenamesIterator.hasNext()){
        Map<String,Object> thisIndexRow=tBestFilenamesIterator.next();
        String index=(new Integer(((Double)thisIndexRow.get("SPLASHID")).intValue())).toString();
        String name=(String)thisIndexRow.get("Filename");
        if(!bestFilenamesMap.containsKey(index)){
          bestFilenamesMap.put(index, name);
          System.out.println("     Placing "+name+" for "+index+"...");
        }
      }
      
      
      //tSampleMicrosatData
      
      //first, let's get the behaviorindex and populate an ArrayList
      
      
      Iterator<Map<String,Object>> tBehaviorCodesIterator = tBehaviorIndex.iterator();
      TreeMap<String,String> behMap = new TreeMap<String,String>();
      while(tBehaviorCodesIterator.hasNext()){
        Map<String,Object> thisIndexRow=tBehaviorCodesIterator.next();
        String index=(String)thisIndexRow.get("Abbr Beh Role");
        String name=(String)thisIndexRow.get("Individual Role");
        if(!behMap.containsKey(index)){
          behMap.put(index, name);
          
        }
      }
      
      
      
      //first, let's get the region index and populate an ArrayList
      
      Iterator<Map<String,Object>> tRegionCodesIterator = tRegion.iterator();
      TreeMap<String,String> regionMap = new TreeMap<String,String>();
      while(tRegionCodesIterator.hasNext()){
        Map<String,Object> thisIndexRow=tRegionCodesIterator.next();
        String index=(String)thisIndexRow.get("Region");
        String name=(String)thisIndexRow.get("RegionName");
        if(!regionMap.containsKey(index)){
          regionMap.put(index, name);
          //System.out.println("Adding region: "+index+", "+name);
        }
        
      }
      
      //first, let's get the research group index and populate an ArrayList
      Iterator<Map<String,Object>> tRGIterator = ltResearchGroup.iterator();
      TreeMap<String,String> rgMap = new TreeMap<String,String>();
      while(tRGIterator.hasNext()){
        Map<String,Object> thisIndexRow=tRGIterator.next();
        String index=(String)thisIndexRow.get("RG");
        String name=(String)thisIndexRow.get("Research Group");
        if(!rgMap.containsKey(index)){
          rgMap.put(index, name);
          //System.out.println("Adding research group: "+index+", "+name);
        }
      }
      
      
      //1. We start with tIdentifications and we only use rows that have a SPlashID. There may be more than one entry.
      Iterator<Map<String,Object>> tIdentificationsIterator = tIdentifications.iterator();
      int numMatchingIdentifications=0;
      while(tIdentificationsIterator.hasNext()){
        Map<String,Object> thisRow=tIdentificationsIterator.next();
        if(thisRow.get("SPLASH ID")!=null){
          String splashID=thisRow.get("SPLASH ID").toString();
          numMatchingIdentifications++;

          //if(numMatchingIdentifications<10){
          
          //update changes
          processThisRow(thisRow, myShepherd, splashImagesDirPath, encountersDirPath, tSPLASHIDFilenames, urlToThumbnailJSPPage, tSPLASHIDSexes, tSightings, thumbnailThese, thumbnailTheseImages, tDailyEffort, tFlukeQualCodes, tBehaviorIndex, behMap, regionMap, rgMap, tSampleLabData, haplotypes, myKey, tSampleMicrosatData, bestFilenamesMap,context,baseDir );
          
        }
    
      }
      
       
      //addition
      tDailyEffort=uDB.getTable("tDailyEffort");
      tSightings=uDB.getTable("tSightings");
      tIdentifications=uDB.getTable("tIdentifications");
      tIdentificationsIterator = tIdentifications.iterator();
      numMatchingIdentifications=0;
      while(tIdentificationsIterator.hasNext()){
        Map<String,Object> thisRow=tIdentificationsIterator.next();
        if(thisRow.get("SPLASH ID")!=null){
          String splashID=thisRow.get("SPLASH ID").toString();
          numMatchingIdentifications++;

          //update changes
          processThisRow(thisRow, myShepherd, splashImagesDirPath, encountersDirPath, tSPLASHIDFilenames, urlToThumbnailJSPPage, tSPLASHIDSexes, tSightings, thumbnailThese, thumbnailTheseImages, tDailyEffort, tFlukeQualCodes, tBehaviorIndex, behMap, regionMap, rgMap, tSampleLabData, haplotypes, myKey, tSampleMicrosatData, bestFilenamesMap,context,baseDir );
          
        }
    
      }
      
      
      
      //2. Then we link over to table tSightings to build encounters for each markedindividual loaded from tIdentifications.
      
      
      
      
        
      
      
    }
    catch(Exception e){
      e.printStackTrace();
    }
    myShepherd.closeDBTransaction();
    
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
  
  private static void processThisRow(
                     Map<String,Object> thisRow, 
                     Shepherd myShepherd, 
                     String splashImagesDirPath, 
                     String encountersRootDirPath, 
                     Table tSPLASHIDFilenames, 
                     String urlToThumbnailJSPPage,
                     Table tSPLASHIDSexes, 
                     Table tSightings,
                     ArrayList<String> thumbnailThese,
                     ArrayList<String> thumbnailTheseImages,
                     Table tDailyEffort,
                     Table tFlukeQualCodes,
                     Table tBehaviorIndex,
                     TreeMap behMap,
                     TreeMap regionMap,
                     TreeMap rgMap,
                     Table tSampleLabData,
                     Table haplotypes, 
                     Integer myKey, 
                     Table tSampleMicrosatData,
                     TreeMap<String,String> bestFilenamesMap,
                     String context,
                     String baseDir
                     ){
    
    //Itertaor for primary keys
    
    boolean exists=false;
    
    //create the encounter
    String markedIndividualName=((Integer)thisRow.get("SPLASH ID")).toString().trim();
    Encounter enc=new Encounter();
    
  //set encounter number
    String IDKey=enc.generateEncounterNumber();
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
    enc.assignToMarkedIndividual(markedIndividualName);
    enc.setMatchedBy("Visual inspection");
    enc.setDWCDateAdded(ServletUtilities.getDate());
    enc.setDWCDateLastModified(ServletUtilities.getDate());
    
    enc.setGenus("Megaptera");
    enc.setSpecificEpithet("novaeangliae");
    
    LocalDateTime dt2 = new LocalDateTime();
    DateTimeFormatter fmt = ISODateTimeFormat.date();
    String strOutputDateTime = fmt.print(dt2);
    enc.setDWCDateAdded(strOutputDateTime);
    
    enc.setDWCDateLastModified(strOutputDateTime);
    enc.setDWCDateAdded(new Long(dt2.toDateTime().getMillis()));
    enc.setLocation("Northern Pacific Ocean");
    enc.setLocationCode("");
    //enc.approve();
    enc.setLivingStatus("alive");
    
    //set eventID
    if((String)thisRow.get("Sighting")!=null){
      enc.setEventID((String)thisRow.get("Sighting"));
      //System.out.println("     eventID: "+enc.getEventID());
    }
    
    
    if((String)thisRow.get("Field ID")!=null){
      enc.setDynamicProperty("Field ID",(String)thisRow.get("Field ID"));
    }
    
    if((String)thisRow.get("Beh Role")!=null){
      enc.setDynamicProperty("Beh Role",(String)thisRow.get("Beh Role"));
    }
    
    if((String)thisRow.get("Best Fluke")!=null){
      enc.setDynamicProperty("Best Fluke",(String)thisRow.get("Best Fluke"));
    }
    
    
    //set behavior role
    if((String)thisRow.get("BRSPLASH")!=null){
      
      String rowValue=(String)thisRow.get("BRSPLASH");
      if(behMap.containsKey(rowValue)){rowValue=(String)behMap.get(rowValue);}
      //System.out.println("     Setting bhevaior: "+rowValue);
      enc.setBehavior(rowValue);
      
    }
    
    
    enc.setCatalogNumber(IDKey);
    thumbnailThese.add(IDKey);
    System.out.println("Processing: "+IDKey);
    
    //expose with TapirLink
    enc.setOKExposeViaTapirLink(true);
    
    //state
    enc.setState("approved");
    
    //submitter
    enc.setSubmitterEmail("");
    enc.setSubmitterPhone("");
    enc.setSubmitterAddress("");
    
    //other data to set blank for now
    if((String)thisRow.get("Working ID")!=null){
      enc.setAlternateID((String)thisRow.get("Working ID"));
    }
    
    TissueSample tiss=new TissueSample();
    if(((String)thisRow.get("Sample Num")!=null)&&(!((String)thisRow.get("Sample Num")).trim().equals(""))){
      
      String sampleString=((String)thisRow.get("Sample Num"));
      String otherSampleID="";
      String labLoc="";
      
      //let's check tSampleLabData for a matching LABID
      Iterator<Map<String,Object>> tLabIterator = tSampleLabData.iterator();
      while(tLabIterator.hasNext()){
        Map<String,Object> thisLabRow=tLabIterator.next();
        if((thisLabRow.get("Sample #")!=null)&&(((String)thisLabRow.get("Sample #"))).equals(sampleString)){
          
          tiss=new TissueSample(enc.getCatalogNumber(), sampleString);
          
          if(thisLabRow.get("LABID")!=null){otherSampleID=(new Integer(((Double)thisLabRow.get("LABID")).intValue())).toString();}
          if(thisLabRow.get("Lab Loc")!=null){labLoc=(String)thisLabRow.get("Lab Loc");}
          if(!otherSampleID.equals("")){tiss.setAlternateSampleID(otherSampleID);}
          if(!labLoc.equals("")){tiss.setStorageLabID(labLoc);}
          
          

          String myHaplo="";
          if(thisLabRow.get("DLpHap")!=null){myHaplo=(String)thisLabRow.get("DLpHap");}
          
          String myGenSex="";
          String swfid="";
          if(thisLabRow.get("Lab Sex")!=null){myGenSex=(String)thisLabRow.get("Lab Sex");}
          if(thisLabRow.get("LABID")!=null){
            swfid=((Double)thisLabRow.get("LABID")).toString();
          }
          
          if(!myGenSex.equals("")&&(!myShepherd.isGeneticAnalysis(tiss.getSampleID(), enc.getCatalogNumber(), (enc.getCatalogNumber()+":"+tiss.getSampleID()+":sex"), "SexAnalysis"))){
            SexAnalysis sa=new SexAnalysis((enc.getCatalogNumber()+":"+tiss.getSampleID()+":sex"), "X", enc.getCatalogNumber(), tiss.getSampleID());
            if(myGenSex.equals("M")){sa.setSex("M");}
            else if(myGenSex.equals("F")){sa.setSex("F");}
            sa.setProcessingLabTaskID(swfid);
            tiss.addGeneticAnalysis(sa);
            myKey++;
          }
          
          if((!myHaplo.equals(""))&&(!myShepherd.isGeneticAnalysis(tiss.getSampleID(), enc.getCatalogNumber(), (enc.getCatalogNumber()+":"+tiss.getSampleID()+":sex"), "MitochondrialDNA"))){
            MitochondrialDNAAnalysis hapResult=new MitochondrialDNAAnalysis((enc.getCatalogNumber()+":"+tiss.getSampleID()+":haplotype"), myHaplo, enc.getCatalogNumber(), tiss.getSampleID());
            hapResult.setProcessingLabTaskID(swfid);
            myKey++;
            tiss.addGeneticAnalysis(hapResult);
            
            //System.out.println("     Setting haplotype as: "+myHaplo);
            
          }
          
          //let's check for ms markers
          Iterator<Map<String,Object>> tMSMarker = tSampleMicrosatData.iterator();
          while(tMSMarker.hasNext()){
            Map<String,Object> thisMarkerRow=tMSMarker.next();
            if((thisMarkerRow.get("LABID")!=null)&&(thisLabRow.get("LABID")!=null)){
              
              String thisMarkerRowLabID = ((Double)thisMarkerRow.get("LABID")).toString();
              String thisLabRowLabID = ((Double)thisLabRow.get("LABID")).toString();
              
              if(thisLabRowLabID.equals(thisMarkerRowLabID)){
                
                //System.out.println("    *******I have found an msMarker ROW!!!");
                
                //create the new ms marker analysis object
                ArrayList<Locus> loci= new ArrayList<Locus>();
                
                //GATA417
                loci.add(getLocus("GATA417", thisMarkerRow));
                
                //Ev37
                loci.add(getLocus("Ev37", thisMarkerRow));
                
                
                //Ev96
                loci.add(getLocus("Ev96", thisMarkerRow));
                
                
                //rw4-10
                loci.add(getLocus("rw4-10", thisMarkerRow));
                
                
                //GT211
                loci.add(getLocus("GT211", thisMarkerRow));
                
                
                //Ev14
                loci.add(getLocus("Ev14", thisMarkerRow));
                
                
                //rw48
                loci.add(getLocus("rw48", thisMarkerRow));
                
                
                //GATA28
                loci.add(getLocus("GATA28", thisMarkerRow));
                
                
                //GT23
                loci.add(getLocus("GT23", thisMarkerRow));
                
                
                //GT575
                loci.add(getLocus("GT575", thisMarkerRow));
                
                
                //Ev1
                loci.add(getLocus("Ev1", thisMarkerRow));
                
                
                //Ev104
                loci.add(getLocus("Ev104", thisMarkerRow));
                
                
                //Ev21
                loci.add(getLocus("Ev21", thisMarkerRow));
                
                
                //Ev94
                loci.add(getLocus("Ev94", thisMarkerRow));
                
                //let's clean up the loci and remove zero values
                int numLoci=loci.size();
                for(int h=0;h<loci.size();h++){
                  Locus l=loci.get(h);
                  boolean hasNonZeroAlleleValue=false;
                  if(l.getAllele0()>0){hasNonZeroAlleleValue=true;}
                  if(l.getAllele1()>0){hasNonZeroAlleleValue=true;}
                  if(!hasNonZeroAlleleValue){
                    loci.remove(h);
                    h--;
                  }
                  
                }
                
                
                if(loci.size()>0){
                  MicrosatelliteMarkersAnalysis msAnalysis = new MicrosatelliteMarkersAnalysis((enc.getCatalogNumber()+":"+tiss.getSampleID()), tiss.getSampleID(), enc.getCatalogNumber(),loci);
                  tiss.addGeneticAnalysis(msAnalysis);
                }
                
                
                //System.out.println("     Setting an msMarker analysis: "+msAnalysis.getAllelesHTMLString());
                
                
              }
              
              
              
            } 
          }
          
        
          enc.addTissueSample(tiss);
        }
          
      }
      

      //set other tissue sample properties
      
      
      

      
      
      //let's check for a haplotype
      //Iterator<Map<String,Object>> tHaplotypesIterator = haplotypes.iterator();
      //TreeMap<String,String> haploMap = new TreeMap<String,String>();
      //while(tHaplotypesIterator.hasNext()){
        //Map<String,Object> thisHaploRow=tHaplotypesIterator.next();
        //if(thisHaploRow.get("Sample #")!=null){
        //String sampleNum=(String)thisHaploRow.get("Sample #");
        
        //if(sampleNum.toLowerCase().trim().equals(tiss.getSampleID().trim().toLowerCase())){
        
          
          
        //} 
      //}
      //}
        
      
      
    }
    
    enc.setInformOthers("");
    //enc.setSizeGuess("");
    enc.setComments("");
    
    //populate its attribute values
    if((String)thisRow.get("Scarring")!=null){enc.setDistinguishingScar((String)thisRow.get("Scarring"));}
    
    if((String)thisRow.get("Comments")!=null){enc.setOccurrenceRemarks((String)thisRow.get("Comments"));}
    else{enc.setOccurrenceRemarks((String)thisRow.get(""));}
    
    if((Object)thisRow.get("Date")!=null){
      String originalDate=((Object)thisRow.get("Date")).toString().replaceAll(" EDT", "").replaceAll(" EST", "").replaceAll(" UTC", "");
      //System.out.println("     "+originalDate);
      
      DateTimeFormatter splashFMT = new DateTimeFormatterBuilder()
              .appendDayOfWeekShortText()
              .appendLiteral(' ')
              .appendMonthOfYearShortText()
              .appendLiteral(' ')
              .appendDayOfMonth(2)
              .appendLiteral(' ')
              .appendHourOfDay(2)
              .appendLiteral(':')
              .appendMinuteOfHour(2)
              .appendLiteral(':')
              .appendSecondOfMinute(2)
              .appendLiteral(' ')
              .appendYear(4, 4)
              .toFormatter();
      DateTime dt = splashFMT.parseDateTime(originalDate);
      enc.setDay(dt.getDayOfMonth());
      enc.setMonth(dt.getMonthOfYear());
      enc.setYear(dt.getYear());
      
      

    }
    
    //let's get what we can from tSightings
    Iterator<Map<String,Object>> tSightingsIterator = tSightings.iterator();
    //int numMatchingIdentifications=0;
    while(tSightingsIterator.hasNext()){
      Map<String,Object> thisSightRow=tSightingsIterator.next();
      if((thisSightRow.get("Research Group")!=null)&&(thisRow.get("Research Group")!=null)&&(((Object)thisSightRow.get("Research Group")).toString().trim().equals(((Object)thisRow.get("Research Group")).toString().trim()))){
        if((thisSightRow.get("Date")!=null)&&(thisRow.get("Date")!=null)&&(((Object)thisSightRow.get("Date")).toString().trim().equals(((Object)thisRow.get("Date")).toString().trim()))){
          if((thisSightRow.get("Vessel")!=null)&&(thisRow.get("Vessel")!=null)&&(((Object)thisSightRow.get("Vessel")).toString().trim().equals(((Object)thisRow.get("Vessel")).toString().trim()))){
            if((thisSightRow.get("Sighting")!=null)&&(thisRow.get("Sighting")!=null)&&(((Object)thisSightRow.get("Sighting")).toString().trim().equals(((Object)thisRow.get("Sighting")).toString().trim()))){
            
              //System.out.println("     I have found a matching tSighting!");
              
              //let's get the matching tDailyEffort row
              Iterator<Map<String,Object>> tEffortIterator = tDailyEffort.iterator();

              while(tEffortIterator.hasNext()){
                Map<String,Object> thisEffortRow=tEffortIterator.next();
                if((thisEffortRow.get("Research Group")!=null)&&(thisSightRow.get("Research Group")!=null)&&(((Object)thisEffortRow.get("Research Group")).toString().trim().equals(((Object)thisSightRow.get("Research Group")).toString().trim()))){
                  if((thisSightRow.get("Date")!=null)&&(thisEffortRow.get("Date")!=null)&&(((Object)thisSightRow.get("Date")).toString().trim().equals(((Object)thisEffortRow.get("Date")).toString().trim()))){
                    if((thisSightRow.get("Vessel")!=null)&&(thisEffortRow.get("Vessel")!=null)&&(((Object)thisSightRow.get("Vessel")).toString().trim().equals(((Object)thisEffortRow.get("Vessel")).toString().trim()))){
                      
                      //we have an effort match!
                      //System.out.println("     We have an effort match!");
                      
                      if(((String)thisEffortRow.get("Sub-area")!=null)){
                        enc.setVerbatimLocality(((String)thisEffortRow.get("Sub-area")));
                        //System.out.println("     Sub-area: "+(String)thisEffortRow.get("Sub-area"));
                      }
                      
                      if(((String)thisEffortRow.get("Locality")!=null)){
                        enc.setDynamicProperty("Locality",((String)thisEffortRow.get("Locality")));
                        //System.out.println("     Sub-area: "+(String)thisEffortRow.get("Sub-area"));
                      }
                      
                      
                      
                      if(((String)thisEffortRow.get("Region")!=null)){
                        String val=(String)thisEffortRow.get("Region");
                        enc.setLocationID(val);
                        //System.out.println("     Region is: "+val);
                        
                        
                        //Iterator rIter=regionMap.values().iterator();
                        //while(rIter.hasNext()){
                        //  System.out.println((String)rIter.next());
                        //}
                        
                        
                        if(regionMap.containsKey(val)){
                          enc.setDynamicProperty("Region Name", (String)regionMap.get(val));
                          //System.out.println("      I mapped the region ID to: "+(String)regionMap.get(val));
                        }
                        //System.out.println("     Region: "+(String)thisEffortRow.get("Region"));
                      }
                      
                      
                      
                      
                      if(((String)thisEffortRow.get("Season")!=null)){
                        enc.setVerbatimEventDate(((String)thisEffortRow.get("Season")));
                        //System.out.println("     Season: "+(String)thisEffortRow.get("Season"));
                      }
                    }
                  }
                }
              }
              
              
              //OK, we have a matching row
              
              //GPS
              enc.setDWCDecimalLatitude(-9999.0);
              enc.setDWCDecimalLongitude(-9999.0);
              enc.setGPSLatitude("");
              enc.setGPSLongitude("");
              if(thisSightRow.get("Start Dec Lat")!=null){
                double lat=(new Double(((Object)thisSightRow.get("Start Dec Lat")).toString().trim())).doubleValue();
                enc.setDWCDecimalLatitude(lat);
                //System.out.println("     I set lat as: "+lat);
              }
              if(thisSightRow.get("Start Dec Long")!=null){
                double longie=(new Double(((Object)thisSightRow.get("Start Dec Long")).toString().trim())).doubleValue();
                enc.setDWCDecimalLongitude(longie);
                //System.out.println("     I set long as: "+longie);
              }
              
              if(thisSightRow.get("Sighting")!=null){
                String value=((String)thisSightRow.get("Sighting")).toString().trim();
                enc.setDynamicProperty("Sighting", value);
              }
              
              if(thisSightRow.get("Pos Type")!=null){
                String value=((String)thisSightRow.get("Pos Type")).toString().trim();
                enc.setDynamicProperty("Pos Type", value);
              }
              
              if(thisSightRow.get("Comments")!=null){
                String value=((String)thisSightRow.get("Comments")).toString().trim();
                if(!value.trim().equals("")){
                  enc.setDynamicProperty("Sighting Comments", value);
                }
              } 
              
              if(thisSightRow.get("Est Size Best")!=null){
                String value=((Integer)thisSightRow.get("Est Size Best")).toString().trim();
                enc.setDynamicProperty("Est Size Best", value);
              }
              
              //set Submitter
              if(((String)thisSightRow.get("Research Group")!=null)){
                String group = (String)thisSightRow.get("Research Group");
              if(rgMap.containsKey(group)){
                enc.setSubmitterName((String)rgMap.get(group));
                
              }
              else{
                enc.setSubmitterName(((String)thisSightRow.get("Research Group")));
              } 
                
                
                //enc.setPhotographerName(((String)thisSightRow.get("Research Group")));
              }
              
              //depth
              //enc.setDepth(-1);
              if(thisSightRow.get("Depth (m)")!=null){
                try{
                  double depth=(new Double(((Object)thisSightRow.get("Depth (m)")).toString().trim())).doubleValue();
                  if(depth>0){enc.setDepth(depth);}
                  //System.out.println("     I set depth as: "+depth);
                }
                catch(NumberFormatException nfe){
                  //System.out.println("     Caught a numberFormatException on this depth.");
                  //System.out.println("     Depth is listed as: "+((Object)thisSightRow.get("Depth (m)")).toString());
                  //System.out.println("     SightingKey is: "+((Object)thisSightRow.get("SightingKey")).toString());
                }
              }
              
              //size
              //enc.setSize(0);

              
              //time
              enc.setHour(-1);
              enc.setMinutes("00");
              if(thisSightRow.get("Start Time")!=null){
                String startTime=((Object)thisSightRow.get("Start Time")).toString().trim();
                StringTokenizer st=new StringTokenizer(startTime, ":");
                //System.out.println(startTime);
                if(st.countTokens()>0){
                  String myString=st.nextToken();
                  int thisHour=new Integer(myString.substring(myString.length()-2)).intValue();
                  enc.setHour(thisHour);
                  String thisMinutes=st.nextToken();
                  enc.setMinutes(thisMinutes);
                  //System.out.println("     Setting time: "+thisHour+":"+thisMinutes);
                }
              }
              
              //photographer
              String photogs="";
              if(thisSightRow.get("Photographer 1")!=null){
                if(!((Object)thisSightRow.get("Photographer 1")).toString().trim().equals("")){
                  photogs=photogs+((Object)thisSightRow.get("Photographer 1")).toString();
                }
              }
              if(thisSightRow.get("Photographer 2")!=null){
                if(!((Object)thisSightRow.get("Photographer 2")).toString().trim().equals("")){
                  photogs=photogs+", "+((Object)thisSightRow.get("Photographer 2")).toString();
                }
              }
              if(thisSightRow.get("Photographer 3")!=null){
                if(!((Object)thisSightRow.get("Photographer 3")).toString().trim().equals("")){
                  photogs=photogs+", "+((Object)thisSightRow.get("Photographer 3")).toString();
                }
              }
              enc.setPhotographerName(photogs);
              enc.setPhotographerEmail("");
              enc.setPhotographerPhone("");
              enc.setPhotographerAddress("");

              
    
              if(thisSightRow.get("Vessel")!=null){
                String comments=((Object)thisSightRow.get("Vessel")).toString();
                enc.setDynamicProperty("Vessel",comments);
                //System.out.println("Vessel original: "+comments);
                //System.out.println("Vessel saved: "+enc.getDynamicPropertyValue("vessel"));
                //System.out.println("All dynamicProperties: "+enc.getDynamicProperties());
              }
              
              if(thisSightRow.get("Group Beh")!=null){
                String comments=((Object)thisSightRow.get("Group Beh")).toString();
                //String originalValue="";
                //if(enc.getOccurrenceRemarks()!=null){originalValue=enc.getOccurrenceRemarks();}
                //enc.setOccurrenceRemarks(originalValue+"<br>Group Behavior: "+comments);
                enc.setDynamicProperty("Group Behavior", comments);
              }
              
              
              if(thisSightRow.get("Num Calves")!=null){
                String comments=((Object)thisSightRow.get("Num Calves")).toString();
                enc.setDynamicProperty("Number Calves", comments);
              }
              if(thisSightRow.get("Group Type")!=null){
                String comments=((Object)thisSightRow.get("Group Type")).toString();
                enc.setDynamicProperty("Group Type", comments);
              }
              
              
            }
          }
        }
      }
      
    }
    
    
    
    String imageName="";
    //create its directory
    


    //now the setup
    String colorCode="";
    
    
    if(bestFilenamesMap.containsKey(enc.getIndividualID())){
      imageName=bestFilenamesMap.get(enc.getIndividualID());  
      File parentDir=new File(splashImagesDirPath);
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
          SinglePhotoVideo vid=new SinglePhotoVideo(enc.getCatalogNumber(),imageName, (encDir+"/"+imageName));
          enc.addSinglePhotoVideo(vid);
          thumbnailTheseImages.add(imageName);
          
          enc.setDynamicProperty("ImportDate", "2015-11-11");
          enc.setDynamicProperty("ImportGroup", "SPLASH-2015-11-11");
        
          //we have a match in the tFlukeQualCodes table
          //start color code iterations
          Iterator<Map<String,Object>> tFlukeQualCodesIterator = tFlukeQualCodes.iterator();
          
          while(tFlukeQualCodesIterator.hasNext()){ 
          Map<String,Object> thisFlukeRow=tFlukeQualCodesIterator.next();
          if((thisFlukeRow.get("Best Fluke")!=null)&&(thisRow.get("Best Fluke")!=null)&&(((Object)thisFlukeRow.get("Best Fluke")).toString().trim().equals(((Object)thisRow.get("Best Fluke")).toString().trim()))){
            
            
          if(thisFlukeRow.get("Color")!=null){
            String color=((String)thisFlukeRow.get("Color")).toString().toUpperCase();
            
            boolean newKeyword=false;
            
            
            
            if(!myShepherd.isKeyword(color)){
              Keyword kw=new Keyword(color);
              myShepherd.storeNewKeyword(kw);
              
            }
            
            myShepherd.beginDBTransaction();
            Keyword kw=myShepherd.getKeyword(color);
              vid.addKeyword(kw);
              myShepherd.commitDBTransaction();
              
        
          
          }
          }
          }
          //end color code iterations
          
        } 

    }
    
    
//add bestFluke
    
    if((thisRow.get("Best Fluke")!=null)&&(thisRow.get("Best Fluke")!=null)&&(((Object)thisRow.get("Best Fluke")).toString().trim().equals(((Object)thisRow.get("Best Fluke")).toString().trim()))){
      
      imageName=((Object)thisRow.get("Best Fluke")).toString().trim(); 
      File parentDir=new File(splashImagesDirPath);
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
          SinglePhotoVideo vid=new SinglePhotoVideo(enc.getCatalogNumber(),imageName, (encDir+"/"+imageName));
          enc.addSinglePhotoVideo(vid);
          thumbnailTheseImages.add(imageName);
          
          enc.setDynamicProperty("ImportDate", "2015-11-16");
          enc.setDynamicProperty("ImportGroup", "SPLASH-2015-11-16");
        
          //we have a match in the tFlukeQualCodes table
          //start color code iterations
          Iterator<Map<String,Object>> tFlukeQualCodesIterator = tFlukeQualCodes.iterator();
          
          while(tFlukeQualCodesIterator.hasNext()){ 
          Map<String,Object> thisFlukeRow=tFlukeQualCodesIterator.next();
          if((thisFlukeRow.get("Best Fluke")!=null)&&(thisRow.get("Best Fluke")!=null)&&(((Object)thisFlukeRow.get("Best Fluke")).toString().trim().equals(((Object)thisRow.get("Best Fluke")).toString().trim()))){
            
            
          if(thisFlukeRow.get("Color")!=null){
            String color=((String)thisFlukeRow.get("Color")).toString().toUpperCase();
            
            boolean newKeyword=false;
            
            
            
            if(!myShepherd.isKeyword(color)){
              Keyword kw=new Keyword(color);
              myShepherd.storeNewKeyword(kw);
              
            }
            
            myShepherd.beginDBTransaction();
            Keyword kw=myShepherd.getKeyword(color);
              vid.addKeyword(kw);
              myShepherd.commitDBTransaction();
              
        
          
          }
          }
          }
          //end color code iterations
          
        } 

    }
        
    
//add bestFluke   
    
    
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
    
    
    //let's check if the MarkedIndividual exists and create it if not
    myShepherd.beginDBTransaction();
    try{
      if(myShepherd.isMarkedIndividual(markedIndividualName)){
        MarkedIndividual markie=myShepherd.getMarkedIndividual(markedIndividualName);
        enc.setSex(markie.getSex());
        markie.addComments("<p>Added encounter "+enc.getCatalogNumber()+".</p>");
        if(!colorCode.equals("")){
          markie.setPatterningCode(colorCode);
          enc.setPatterningCode(colorCode);
        }
        
        //let's check enc so that it doesn't have a duplicate SinglePhotoVideo as another encounter in MarkedIndividual
        if((enc.getSinglePhotoVideo()!=null) && (enc.getSinglePhotoVideo().size()>0)){
          int locSize=enc.getSinglePhotoVideo().size();
          for(int h=0;h<locSize;h++){
            SinglePhotoVideo s=enc.getSinglePhotoVideo().get(h);
            
            //now we have to check it against the other photos the MarkedIndividual to see if it's a duplicate
            Vector vec=markie.getEncounters();
            int vecSize=vec.size();
            for(int p=0;p<vecSize;p++){
              Encounter otherEnc=(Encounter)vec.get(p);
              int locSize2=otherEnc.getSinglePhotoVideo().size();
              for(int l=0;l<locSize2;l++){
                SinglePhotoVideo s2=otherEnc.getSinglePhotoVideo().get(l);
                if(s.getFilename().equals(s2.getFilename())){
                  enc.removeSinglePhotoVideo(s);
                  l=100;
                  p=100;
                  h--;
                  vecSize--;
                  locSize=enc.getSinglePhotoVideo().size();
                }
              } 
            }
          }
          
        }
        markie.addEncounter(enc, context);
        markie.refreshDependentProperties(context);
        myShepherd.commitDBTransaction();
      
      }
      else{
      
        MarkedIndividual newWhale=new MarkedIndividual(markedIndividualName, enc);
      /*  if(!colorCode.equals("")){
          newWhale.setPatterningCode(colorCode);
          enc.setPatterningCode(colorCode);
        }
      */
        enc.setMatchedBy("Unmatched first encounter");
        newWhale.addComments("<p>Created "+markedIndividualName+" with the SplashMigratorApp.</p>");
        newWhale.setDateTimeCreated(ServletUtilities.getDate());
        
        //let's try to determine the sex
        Iterator<Map<String,Object>> tSPLASHIDSexesIterator = tSPLASHIDSexes.iterator();
        //System.out.println("     Starting to analyze sex...");
        while(tSPLASHIDSexesIterator.hasNext()){
          
          Map<String,Object> thisSexRow=tSPLASHIDSexesIterator.next();
          //System.out.println("     Iterating sexes...!");
          if((thisSexRow.get("SPLASH ID")!=null)&&(((Object)thisSexRow.get("SPLASH ID")).toString().trim().equals(newWhale.getName()))){
            //System.out.println("     I have found a matching tSex Row!");
            
            //BestSex
            if(thisSexRow.get("BestSex")!=null){
              String thisSex=((Object)thisSexRow.get("BestSex")).toString().toLowerCase();
              //System.out.println("     I have found a matching tSex: "+thisSex);
              if(thisSex.equals("m")){newWhale.setSex("male");}
              else if(thisSex.equals("f")){newWhale.setSex("female");}
              else{newWhale.setSex("unknown");}
              enc.setSex(newWhale.getSex());
            }
            else{
              newWhale.setSex("unknown");
            }
            
            
            //GenSex
            if(thisSexRow.get("GenSex")!=null){
              String thisSex=((Object)thisSexRow.get("GenSex")).toString().toLowerCase();
              //System.out.println("     I have found a matching GenSex: "+thisSex);

            }
            
            //BehSex
            if(thisSexRow.get("BehSex")!=null){
              String thisSex=((Object)thisSexRow.get("BehSex")).toString().toLowerCase();
              //System.out.println("     I have found a matching BehSex: "+thisSex);
              //if(thisSex.equals("m")){newWhale.setDynamicProperty("BehSex","male");}
              //else if(thisSex.equals("f")){newWhale.setDynamicProperty("BehSex","female");}
              //else{newWhale.setDynamicProperty("BehSex","unknown");}
              
              newWhale.setDynamicProperty("BehSex",thisSex);
              
            }
            
            //BestSexConf
            if(thisSexRow.get("BestSexConf")!=null){
              String thisSex=((Object)thisSexRow.get("BestSexConf")).toString().toLowerCase();
              //System.out.println("     I have found a matching BestSexConf: "+thisSex);
              newWhale.setDynamicProperty("Best Sex Confidence",thisSex);

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

  

  
  

}