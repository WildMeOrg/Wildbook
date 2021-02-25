package org.ecocean.ai.utilities;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.*;

import javax.jdo.Query;
import javax.servlet.http.HttpServletRequest;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.LinkedProperties;
import org.ecocean.Occurrence;
import org.ecocean.Shepherd;
import org.ecocean.ShepherdProperties;
import org.ecocean.YouTube;
import org.ecocean.ai.nlp.SUTime;
import org.ecocean.ai.nmt.azure.DetectTranslate;
import org.ecocean.identity.IBEISIA;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetMetadata;
import org.ecocean.media.YouTubeAssetStore;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONObject;

import com.google.api.services.youtube.model.Comment;
import com.google.api.services.youtube.model.CommentSnippet;
import com.google.api.services.youtube.model.CommentThread;
import com.google.api.services.youtube.model.CommentThreadReplies;
import com.google.api.services.youtube.model.CommentThreadSnippet;

import java.util.*;

import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;


import twitter4j.Status;


public class ParseDateLocation {

  public static ArrayList<String> parseLocation (String text, String context){
    ArrayList<String> locations = new ArrayList<>();

    //translate to Eglish if not English
    try{
      text = detectLanguageAndTranslateToEnglish(text, context);
    } catch(RuntimeException e){
      e.printStackTrace();
    }

    //check for obvious Encounter.locationIDs
    try{
      locations.add(getLocationCodeKey(text,context));
    } catch(RuntimeException e){
      e.printStackTrace();
    }

    /*
    //use NLP - TO DO
    try{
      ArrayList<String> nlpLocations = nlpLocationParse(text);
      for (int i = 0; i<nlpLocations.size(); i++){
        locations.add(nlpLocations.get(i));
      }
    } catch(RuntimeException e){
      e.printStackTrace();
    }
    */


    //TODO parseGpsCoordinates ends up returning matches for dates, so commenting out for now
    // try{
    //   locations.add(parseGpsCoordinates(text));
    // } catch(RuntimeException e){
    //   e.printStackTrace();
    // }

    return locations;
  }

  public static String detectLanguageAndTranslateToEnglish(String text, String context) throws RuntimeException{
    try{
      String detectedLanguage = DetectTranslate.detectLanguage(text);
      if(!detectedLanguage.toLowerCase().startsWith("en")){
        text= DetectTranslate.translateToEnglish(text);
      }
      if(text !=null && !text.equals("")){
        return text;
      } else{
        throw new RuntimeException("Translation failed: text started out as or became null or empty");
      }
    }
    catch(Exception e){
      e.printStackTrace();
      return null;
    }
  }

  public static String getLocationCodeKey(String text, String context) throws RuntimeException{
    Properties locationCodes = new Properties();
    String returnVal = "";
    locationCodes=ShepherdProperties.getProperties("submitActionClass.properties", "", context);
    Enumeration locationCodesEnum = locationCodes.propertyNames();
    String textToLowerCase = text.toLowerCase();
    while (locationCodesEnum.hasMoreElements()) {
      String currentLocationQuery = ((String) locationCodesEnum.nextElement()).trim().toLowerCase();
      if (textToLowerCase.indexOf(currentLocationQuery) != -1) {
        if (!returnVal.equals("")){
            returnVal += (", " + currentLocationQuery);
        } else{
          returnVal += currentLocationQuery;
        }

      }
    }
    if(returnVal != null && !returnVal.equals("")){
      return returnVal.trim();
    } else{
      throw new RuntimeException("parseLocationCodes produced a null or empty result");
    }
  }

  public static String parseGpsCoordinates(String text) throws RuntimeException{
    System.out.println("text going into parseGpsCoordinates: " + text);
    String returnVal = "";
    String PATTERN_1 = ".*?([+-]?\\d+\\.?\\d+)\\s*,\\s*([+-]?\\d+\\.?\\d+).*?"; //doesn't seem as robust as
    String PATTERN_2 = ".*?([-+]?)([1-8]?\\d(\\.\\d+)?|90(\\.0+)?),\\s*([-+]?)(180(\\.0+)?|((1[0-7]\\d)|([1-9]?\\d))(\\.\\d+)?).*?";

    // first try with less specific one
    Pattern pattern = Pattern.compile(PATTERN_1);
    Matcher matcher = pattern.matcher(text);
    if(matcher.matches()){
      String gpsCoords = matcher.group(1) + ", " + matcher.group(2);//matcher.group(0);
      returnVal = gpsCoords;
    }

    // then try with more specific one
    pattern = Pattern.compile(PATTERN_2);
    matcher = pattern.matcher(text);
    if(matcher.matches()){
      String gpsCoords = matcher.group(1) + matcher.group(2) + ", " + matcher.group(5) + matcher.group(6);
      returnVal = gpsCoords;
    }

    if(returnVal != null && !returnVal.equals("")){
      return returnVal;
    } else{
      //TODO there's probably some nlp gps coordinates we can try next if the regular expressions don't work above
      throw new RuntimeException("Gps coordinates were null or empty");
    }
  }


  //NOTE: overloaded parseDate method for tweet4j status objects specifically. There is another parseDate method!
  public static String parseDate(String rootDir, String context, Status tweet){
    String textInput=tweet.getText();
    if(textInput!=null){
    try{
      String detectedLanguage = DetectTranslate.detectLanguage(textInput);
      if(!detectedLanguage.toLowerCase().startsWith("en")){
        textInput= DetectTranslate.translateToEnglish(textInput);
        System.out.println("Translated text for parseLocation is " + textInput);
      }
    } catch(Exception e){
      System.out.println("Exception trying to detect language.");
      e.printStackTrace();
    }
    String myDate=null;
    //boolean NLPsuccess=false;
    try{
      System.out.println(">>>>>> looking for date with NLP");
      //call Stanford NLP function to find and select a date from ytRemarks
      myDate= org.ecocean.ai.nlp.SUTime.parseDateStringForBestDate(rootDir, tweet);
      //parse through the selected date to grab year, month and day separately.Remove cero from month and day with intValue.
      System.out.println(">>>>>> NLP found date: "+myDate);
      
    }
    catch(Exception e){
      System.out.println("Exception in NLP in IBEISIA.class");
      e.printStackTrace();
    }


    return myDate;
    }
    return null;
  }

    /*
        TODO FIXME this is made by jon as a desired set of arguments to call from IBEISIA.fromDetection()

           note: rootDir is base path for context, i.e. request.getSession().getServletContext().getRealPath("/")
                 suitable for, e.g. baseDir = ServletUtilities.dataDir(context, rootDir);
    */
  /*
  public static String parseDate(String textInput, String context, String rootDir){
    return null;
  }
  */

  //NOTE: parseDate method WITHOUT tweet4j status object as a parameter. There is another parseDate method!
  public static String parseDate(String rootDir, String textInput, String context){

    //int year=-1;
    //int month=-1;
    //int day=-1;

    try{
      String detectedLanguage = DetectTranslate.detectLanguage(textInput);
      if(!detectedLanguage.toLowerCase().startsWith("en")){
        textInput= DetectTranslate.translateToEnglish(textInput);
        System.out.println("Translated text for parseLocation is " + textInput);
      }
    } catch(Exception e){
      System.out.println("Exception trying to detect language.");
      e.printStackTrace();
    }
    
    String myDate=null;

    //boolean NLPsuccess=false;
    try{
      System.out.println(">>>>>> looking for date with NLP");
      //call Stanford NLP function to find and select a date from ytRemarks
      myDate= org.ecocean.ai.nlp.SUTime.parseDateStringForBestDate(rootDir, textInput);
      System.out.println(">>>>>> NLP found date: "+myDate);


    }
    catch(Exception e){
      System.out.println("Exception in NLP in IBEISIA.class");
      e.printStackTrace();
    }


      return myDate;
  }


  
  
  /*
  public static ArrayList<String> nlpLocationParse(String text) throws RuntimeException {
    ArrayList<String> locations = new ArrayList<>();
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner"); //TODO adding truecase before ner doesn't seem to be making a difference here. If this doesn't change with some tweaking, you may want to remove the stanford-corenlp class:model-english dependency. Update: the stanford-corenlp class:model-english dependency seems essential even when truecase is excluded. Very weird.
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    edu.stanford.nlp.pipeline.Annotation document = new edu.stanford.nlp.pipeline.Annotation(text);
    pipeline.annotate(document);
    List<CoreMap> sentences = document.get(SentencesAnnotation.class);
    for(CoreMap sentence: sentences) {
      for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
        String ne = token.get(NamedEntityTagAnnotation.class);
        if(ne.equals("LOCATION")){
          String word = token.get(TextAnnotation.class);
          System.out.println("Location captured: " + word);
          locations.add(word);
        }
      }
    }

    if (locations.size() > 0){
      return locations;
    } else{
      throw new RuntimeException("no locations found");
    }

  }
  */
  
/**
 * Pass in a MediaAsset that derives from a YouTube video, and this method will go check for any additional info, such as response comments,
 * that might help refine or populate derived Encounter dates and locations
 * 
 * @param ma
 * @param suDirPath
 * @param myShepherd a Shepherd object
 * @param context The context that WIldbook is running in. A null value will assume context0.
 * @param numVideosWithID Allows you to keep a running, thread-safe tabulation of videos that have resulted in individualIDs. Can be null.
 * @param numVideos Allows you to keep a running, thread-safe tabulation of a total number of videos as a result of running this method repeatedly. Can be null.
 * @param numUncuratedVideos Allows you to keep a running, thread-safe tabulation of videos that have NOT been curated (Encounter.state != approved or unidentifiable). Can be null.
 * @param numCommentedVideos Allows you to keep a running, thread-safe tabulation of videos that the IntelligentAgent has commented upon. Can be null.
 * @param numCommentedVideosReplies Allows you to keep a running, thread-safe tabulation of videos that the IntelligentAgent has commented upon AND that have replies. Can be null.
 * @param goodDataVideos Allows you to keep a running tab of appropriate videos processed outside this method. Can be null.
 * @param poorDataVideos Allows you to keep a running tab of inappropriate videos processed outside this method. Can be null.
 * @param persistDifferences Whether to save the updated date and location values found by the Intelligent Agent during executing this method.
 * @param numDatesFound Allows you to keep a running, thread-safe tabulation of a total number of video-derived date updates made as a result of running this method repeatedly. Can be null.
 * @param numLocationIDsFound Allows you to keep a running, thread-safe tabulation of a total number of video-derived location updates made as a result of running this method repeatedly. Can be null.
 * @return String an HTML table row <tr> of found text and changes
 */
public static String annotateChildrenOfYouTubeMediaAssetWithDateLocation(MediaAsset ma, 
                                                                         String suDirPath, 
                                                                         Shepherd myShepherd, 
                                                                         String context, 
                                                                         AtomicInteger numVideosWithID,
                                                                         AtomicInteger numVideos, 
                                                                         AtomicInteger numUncuratedVideos, 
                                                                         AtomicInteger numCommentedVideos,
                                                                         AtomicInteger numCommentedVideosReplies,
                                                                         ArrayList<MediaAsset> goodDataVideos,
                                                                         ArrayList<MediaAsset> poorDataVideos, 
                                                                         boolean persistDifferences, 
                                                                         AtomicInteger numDatesFound, 
                                                                         AtomicInteger numLocationIDsFound){

  //if we're going to persist changes, ensure the Shepherd object is ready
  if(persistDifferences && !myShepherd.getPM().currentTransaction().isActive()){
    myShepherd.beginDBTransaction();
  }
  
  //allow context to be NULL but assume context0 if it is set as null
  if(context==null)context="context0";
  
  //the return string of HTML content
  String resultsHTML="";
  

  //whether the video has resulted in an Encounter assigned to a MarkedIndividual
  boolean videoHasID=false;
  
  //whether we found a Wild Me comment on the video
  boolean hasWildMeComment=false;
  
  //whether any found Wild Me comment has a reply
  boolean hasWildMeCommentReplies=false;
  
  //the date of the video, allowing for relative evaluation of the true date via SUTime
  //to start with, we assume today's dat but will process this later.
  String relativeDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    
  //video has metadata for analysis?
    if ((ma.getMetadata() != null)) {
      if(numVideos!=null)numVideos.incrementAndGet();
      MediaAssetMetadata md = ma.getMetadata(); 
      
      //video metadata is not null, so proceed
      if (md.getData() != null) {
      
        //setup our metadata fields  
        String videoID=ma.getMetadata().getData().getJSONObject("detailed").optString("id");
        String videoTitle="";
        //just to save money on language detection, reduce number of characters sent
        String videoTitleShort=videoTitle;
        String videoComments="";
        String videoCommentsClean="";
        String locIDWords="";
        String videoDescription="";
        String videoTags="";
      
        //start capturing metadata about the YouTube video
      
        //video title short form just to save $$$ on language detection by sending fewer characters
        if(videoTitle.length()>500){videoTitleShort=videoTitle.substring(0,500);}
        if(md.getData().optJSONObject("basic") != null){
          videoTitle=AIUtilities.youtubePredictorPrepareString(md.getData().getJSONObject("basic").optString("title"));
        }
      

      if(md.getData().optJSONObject("detailed")!=null){
        videoDescription=AIUtilities.youtubePredictorPrepareString(md.getData().getJSONObject("detailed").optString("description"));
        videoTags=AIUtilities.youtubePredictorPrepareString(md.getData().getJSONObject("detailed").getJSONArray("tags").toString());   
      }
      
      //video description short form just to save $$$ on language detection by sending fewer characters
      String videoDescriptionShort=videoDescription;
      if(videoDescription.length()>1000){videoDescriptionShort=videoDescription.substring(0,1000);}
    
      //video tags short form just to save $$$ on language detection by sending fewer characters
      String videoTagsShort=videoTags;
      if(videoTags.length()>500){videoTagsShort=videoTags.substring(0,500);}
      
      String ytRemarks=videoTitle+" "+videoDescription+" "+videoTags;
      String storedLanguage="null";
      String detectedLanguage="en";
      boolean languageIsStored=false;
      
      
          //first, set metadata lanuage on the mediaasset
          if(md.getData().optJSONObject("detected")!=null){
            if(md.getData().getJSONObject("detected").optString("langCode")!=null){
              String storedData=md.getData().getJSONObject("detected").optString("langCode");
              if((!storedData.trim().equals(""))&&(storedData.toLowerCase().indexOf("unknown")==-1)){
                //if(!detectedLanguage.equals(storedData))changedDetectedLanguage=true;
                detectedLanguage=storedData;
                languageIsStored=true;
              }
              
            }
          }
  
     if(!languageIsStored){
       try{
         detectedLanguage=DetectTranslate.detectLanguage(videoTitleShort+" "+videoDescriptionShort+" "+videoTagsShort);
         JSONObject json= md.getData();
         JSONObject jsonDetected=new JSONObject();
         jsonDetected.put("langCode", detectedLanguage);
         json.put("detected", jsonDetected);
         md.setData(json);
         ma.setMetadata(new MediaAssetMetadata(json));
         //ma.setMetadata(md);
         myShepherd.commitDBTransaction();
         myShepherd.beginDBTransaction();
       }
       catch(Exception e){
         e.printStackTrace();
       }
     }
  
      
      
      //Let's get the Encounter objects related to this video
      //JDOQL query
      String qFilter="SELECT FROM org.ecocean.Encounter WHERE (occurrenceRemarks.indexOf('"+videoID+"') != -1)";
      Query newQ=myShepherd.getPM().newQuery(qFilter);
      Collection d=(Collection)newQ.execute();
      ArrayList<Encounter> encresults=new ArrayList<Encounter>(d);
      newQ.closeAll();
      int numEncs=encresults.size();
      
      //let's iterate our matching Encounters
      //first, check if any have been approved (curated) and count them
      boolean videoIsCurated=false;
      for(int y=0;y<numEncs;y++){
        Encounter enc=encresults.get(y);
        if((enc.getState()!=null)&&((enc.getState().equals("approved"))||(enc.getState().equals("unidentifiable")))){
          if((goodDataVideos!=null)&&!goodDataVideos.contains(ma))goodDataVideos.add(ma);
          videoIsCurated=true;
        }
  
        if((enc.getIndividualID()!=null)&&(!enc.getIndividualID().equals("")))videoHasID=true;
      }
      if(!videoIsCurated && (numUncuratedVideos!=null))numUncuratedVideos.incrementAndGet();
      
      
      Occurrence occur=null;    
      LinkedProperties props=(LinkedProperties)ShepherdProperties.getProperties("submitActionClass.properties", "",context);
  
      String chosenStyleDate="";
      String chosenStyleLocation="";
      
      //if we have matching encounters, then the video is either uncurated, or it has been determined to have useful data (curated)
      if(numEncs>0){
        
        //check for Occurrence
        String occurID="";
            
        //grab the first Encounter for analysis   
        Encounter enc=encresults.get(0);
            
        //get the current values for date and location ID   
        String currentDate="";
        String currentLocationID="";
        if(enc.getDate()!=null)currentDate=enc.getDate().replaceAll("Unknown", ""); 
        if(enc.getLocationID()!=null)currentLocationID=enc.getLocationID().replaceAll("None", "");  
        
        //our encounters should all have an Occurrence, one per video
        if(enc.getOccurrenceID()!=null){
          occur=myShepherd.getOccurrence(enc.getOccurrenceID());
          
          
          //let's get all our YouTube video metadata and comments
          List<CommentThread> comments=YouTube.getVideoCommentsList(occur, context);
          if((comments==null)||(comments.size()==0)){
            videoComments="";
            videoCommentsClean="";
          }
          else{
            boolean isWildMeComment=false;
                int numComments=comments.size();
                videoComments+="<ul>\n";
                for(int f=0;f<numComments;f++) {
                    CommentThread ct=comments.get(f);
  
                    CommentThreadSnippet cts=ct.getSnippet();
                    
                    Comment topLevelComment=cts.getTopLevelComment();
                    CommentSnippet commentSnippet=topLevelComment.getSnippet();
                    String authorName="";
                    if((commentSnippet!=null)&&(commentSnippet.getAuthorDisplayName()!=null)){
                      authorName=commentSnippet.getAuthorDisplayName();
                      
                      //TO DO: set this aside to a Properties file for the agent
                      if(authorName.equals("Wild Me"))isWildMeComment=true;
                      
                    }
                    String style="";
                        if(isWildMeComment){
                          style="color: green;font-weight: bold;";
                          hasWildMeComment=true;
                        }
                    videoComments+="<li style=\""+style+"\">"+authorName+": "+DetectTranslate.translateIfNotEnglish(topLevelComment.getSnippet().getTextDisplay());
                    
                    videoCommentsClean+=DetectTranslate.translateIfNotEnglish(topLevelComment.getSnippet().getTextDisplay()).toLowerCase()+" ";
                    
                    
                    if(ct.getReplies()!=null){
                       CommentThreadReplies ctr=ct.getReplies();
                     
                      List<Comment> replies=ctr.getComments();
                      int numReplies=0;
                      if(replies!=null)numReplies=replies.size();
                      if(numReplies>0){
                        if(isWildMeComment)hasWildMeCommentReplies=true;
                        videoComments+="<ul>\n";
                          for(int g=0;g<numReplies;g++) {
                          
                            Comment reply=replies.get(g);
                            
                            videoComments+="<li>"+DetectTranslate.translateIfNotEnglish(reply.getSnippet().getTextDisplay())+"</li>";
                            videoCommentsClean+=DetectTranslate.translateIfNotEnglish(reply.getSnippet().getTextDisplay()).toLowerCase()+" ";
                              
                           }
                          videoComments+="</ul>\n";
                      }
                     }
  
                    videoComments+="</li>\n";
                    style="";
                    
                }
            videoComments+="</ul>\n";
            
          }
          
          
          occurID=occur.getOccurrenceID();
  
          //prep the YouTube video date for SUTimee analysis
          String tempRelativeDate=null;
          try{    
            tempRelativeDate=YouTube.getVideoPublishedAt(occur, context);
          }
          catch(Exception e){}
          if((tempRelativeDate!=null)&&(tempRelativeDate.indexOf("T")!=-1)){
            tempRelativeDate=tempRelativeDate.substring(0,tempRelativeDate.indexOf("T"));
          }
          if((tempRelativeDate!=null)&&(!tempRelativeDate.equals(""))){
            DateTimeFormatter parser2 = DateTimeFormat.forPattern("yyyy-MM-dd");
            DateTime time = parser2.parseDateTime(tempRelativeDate);
            relativeDate=time.toString(parser2);  
          }
          
          
        }
        
        StringBuffer sbOriginalText=new StringBuffer("");
        sbOriginalText.append(videoTitle+" "+videoDescription+" "+videoTags+" "+videoCommentsClean);
        
        //let's do some translation to English for standardization
        videoTitle=DetectTranslate.translateIfNotEnglish(videoTitleShort);
        videoTags=DetectTranslate.translateIfNotEnglish(videoTagsShort);
        videoDescription=DetectTranslate.translateIfNotEnglish(videoDescriptionShort); 
        
        StringBuffer sb=new StringBuffer("");
        
        sb.append(videoTitle+" "+videoDescription+" "+videoTags+" "+videoCommentsClean);
        
  
        //get video date with SUTime
        String newDetectedDate="";
        try{
          newDetectedDate=SUTime.parseDateStringForBestDate(suDirPath, sb.toString(), relativeDate).replaceAll("null","");
        }
        catch(Exception e){
          e.printStackTrace();
        }
        if((numDatesFound!=null)&&(goodDataVideos!=null)&&goodDataVideos.contains(ma)&& !newDetectedDate.equals("")){numDatesFound.incrementAndGet();}
        
        //determine new LocationID, including comments
        String newLocationID="";
        String lowercaseRemarks=sb.toString().toLowerCase();
                try{
                  
                  
                  Iterator m_enum = props.orderedKeys().iterator();
                  while (m_enum.hasNext()) {
                    String aLocationSnippet = ((String) m_enum.next()).replaceFirst("\\s++$", "");
                    //System.out.println("     Looking for: "+aLocationSnippet);
                    if (lowercaseRemarks.indexOf(aLocationSnippet) != -1) {
                      newLocationID = props.getProperty(aLocationSnippet);
                      locIDWords+=" "+ aLocationSnippet;
                      //System.out.println(".....Building an idea of location: "+location);
                    }
                  }
  
  
                }
                catch(Exception e){
                  e.printStackTrace();
                }
                if(newLocationID==null)newLocationID="";
                if((numLocationIDsFound!=null)&&(goodDataVideos!=null)&&goodDataVideos.contains(ma) && !newLocationID.equals("")){numLocationIDsFound.incrementAndGet();}
  
          
          //here is where we would put logic to update encounters if appropriate
          if(persistDifferences){
          boolean madeAChange=false;
          
          for(int y=0;y<numEncs;y++){
            Encounter thisEnc=encresults.get(y);

              //SET LOCATION ID
              //first, if we even found a location ID in comments, lets' consider it.
              //otherwise, there's no point
              
              if((newLocationID!=null)&&(!newLocationID.trim().equals(""))){
              
              //next, if we have a new locationID and one was not set before, then this is an easy win
                if((thisEnc.getLocationID()==null)||(thisEnc.getLocationID().trim().equals(""))||(thisEnc.getLocationID().trim().equals("None"))){
                  thisEnc.setLocationID(newLocationID);
                  madeAChange=true;
                }
                else if(!thisEnc.getLocationID().trim().equals(newLocationID.trim())){
                  //ok, the location IDs are different, now what?
                
                  //maybe the newLocationID further specifies the older locationID, that would be a win   
                  if(newLocationID.trim().startsWith(thisEnc.getLocationID().trim())){
                    thisEnc.setLocationID(newLocationID.trim());
                    madeAChange=true;
                  }
                  //if the Encounter is not yet approved, then we can reset it as well since it's uncurated and may have been incorrectly detected with older values
                  else if((thisEnc.getState()!=null)&&(thisEnc.getState().equals("auto_sourced"))){
                    thisEnc.setLocationID(newLocationID.trim());
                    madeAChange=true;
                  } 
                }
              }
              //now persist
              if(madeAChange){
                myShepherd.commitDBTransaction();
                myShepherd.beginDBTransaction();
              }
              if(madeAChange)chosenStyleLocation="font-style: italic;";
              //END SET LOCATION ID
              
              
              
              //SET THE DATE
              madeAChange=false;
              chosenStyleDate+="year: "+thisEnc.getYear()+";millis:"+thisEnc.getDateInMilliseconds()+";locationID: "+thisEnc.getLocationID()+";";
              

              //let's check and fix date
              if((newDetectedDate!=null)&&(!newDetectedDate.trim().equals(""))){
                
                //well we have something to analyze at least
                //DateTimeFormatter parser3 = DateTimeFormat.forPattern("yyyy-MM-dd");
                DateTimeFormatter parser3 = ISODateTimeFormat.dateParser();
                DateTime dt=parser3.parseDateTime(newDetectedDate);
  
                
                //check for the easy case
                if((thisEnc.getDateInMilliseconds()==null)||(thisEnc.getYear()<=0)){
                  
                  if(newDetectedDate.length()==10){
                    thisEnc.setYear(dt.getYear());
                    thisEnc.setMonth(dt.getMonthOfYear());
                    thisEnc.setDay(dt.getDayOfMonth());
                  }
                  else if(newDetectedDate.length()==7){
                    thisEnc.setYear(dt.getYear());
                    thisEnc.setMonth(dt.getMonthOfYear());
                    
                  }
                  else if(newDetectedDate.length()==4){
                    thisEnc.setYear(dt.getYear());
                    
                  }
                  
                  //thisEnc.setDateInMilliseconds(dt.getMillis());
                  
                  
                  chosenStyleDate+="font-style: italic; color: red;";
                  madeAChange=true;
                }
                //if it's unapproved/uncurated, trust the newer value
                else if(thisEnc.getState().equals("auto_sourced")){
                  
                  if(newDetectedDate.length()==10){
                    thisEnc.setYear(dt.getYear());
                    thisEnc.setMonth(dt.getMonthOfYear());
                    thisEnc.setDay(dt.getDayOfMonth());
                  }
                  else if(newDetectedDate.length()==7){
                    thisEnc.setYear(dt.getYear());
                    thisEnc.setMonth(dt.getMonthOfYear());
                    
                  }
                  else if(newDetectedDate.length()==4){
                    thisEnc.setYear(dt.getYear());
                    
                  }
                  chosenStyleDate+="font-style: italic; color: green;";
                  madeAChange=true;
                }
              }
              //now persist
              if(madeAChange){
                myShepherd.commitDBTransaction();
                myShepherd.beginDBTransaction();
              }
              
              //END SET DATE

              }
          }
          
          resultsHTML="<tr><td><a target=\"_blank\" href=\"//../occurrence.jsp?number="+occurID+"\">"+occurID+"</a></td><td><a target=\"_blank\" href=\"https://www.youtube.com/watch?v="+videoID+"\">"+videoID+"</a></td><td>"+currentDate+"</td><td><p style=\""+chosenStyleDate+"\">"+newDetectedDate+"</p></td><td>"+currentLocationID+"</td><td><p style=\""+chosenStyleLocation+"\">"+newLocationID+"</p></td><td>"+videoTitle+"</td><td>"+videoDescription+"</td><td>"+videoComments+"</td><td>"+videoCommentsClean+"<br><br>LocID Words: "+locIDWords+"</br></br></td><td>"+relativeDate+"</td><td>"+storedLanguage+"/"+detectedLanguage+"</td></tr>";
      }
      //this video had no encounters, probably been curated as having no value
      else{
        if((poorDataVideos!=null)&&!poorDataVideos.contains(ma)){
          poorDataVideos.add(ma);
          if(numUncuratedVideos!=null)numUncuratedVideos.decrementAndGet();  
        }
      }

      }
      //video metadata is null, not much we can do here
      else{
          if((poorDataVideos!=null)&&!poorDataVideos.contains(ma))poorDataVideos.add(ma);
          }
      

    }
    //video had no metadata, not much we can do here
    //add to poorDataVideos because there's nothing we can with it
    else{
      if((poorDataVideos!=null)&&!poorDataVideos.contains(ma))poorDataVideos.add(ma);
    }

    //increment our counters as needed
    if(hasWildMeComment&&(numCommentedVideos!=null))numCommentedVideos.incrementAndGet();
    if(hasWildMeCommentReplies&&(numCommentedVideosReplies!=null))numCommentedVideosReplies.incrementAndGet();
    if(videoHasID && (numVideosWithID!=null))numVideosWithID.incrementAndGet();
  
  return resultsHTML;
}

  
  
  
  
public static boolean hasRunDetection(MediaAsset ma, Shepherd myShepherd){
  List<MediaAsset> children=YouTubeAssetStore.findFrames(ma, myShepherd);
  if(children!=null){
    int numChildren=children.size();
    for(int i=0;i<numChildren;i++){
      MediaAsset child=children.get(i);
      if((child.getDetectionStatus()!=null)&&(child.getDetectionStatus().equals(IBEISIA.STATUS_COMPLETE))){
        return true;
      }
    }
  }
  return false;
}

  

}