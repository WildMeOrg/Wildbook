package org.ecocean.ai.utilities;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;
import java.util.regex.*;
import javax.servlet.http.HttpServletRequest;
import org.ecocean.ShepherdProperties;
import org.ecocean.ai.nmt.google.DetectTranslate;

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

    try{
      text = detectLanguageAndTranslateToEnglish(text, context);
    } catch(RuntimeException e){
      e.printStackTrace();
    }

    try{
      locations.add(getLocationCodeKey(text,context));
    } catch(RuntimeException e){
      e.printStackTrace();
    }

    try{
      ArrayList<String> nlpLocations = nlpLocationParse(text);
      for (int i = 0; i<nlpLocations.size(); i++){
        locations.add(nlpLocations.get(i));
      }
    } catch(RuntimeException e){
      e.printStackTrace();
    }


    //TODO parseGpsCoordinates ends up returning matches for dates, so commenting out for now
    // try{
    //   locations.add(parseGpsCoordinates(text));
    // } catch(RuntimeException e){
    //   e.printStackTrace();
    // }

    return locations;
  }

  public static String detectLanguageAndTranslateToEnglish(String text, String context) throws RuntimeException{
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
  public static String parseDate(HttpServletRequest request, String textInput, String context, Status tweet){

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
      myDate= org.ecocean.ai.nlp.SUTime.parseDateStringForBestDate(request, textInput, tweet);
      //parse through the selected date to grab year, month and day separately.Remove cero from month and day with intValue.
      System.out.println(">>>>>> NLP found date: "+myDate);
      
    }
    catch(Exception e){
      System.out.println("Exception in NLP in IBEISIA.class");
      e.printStackTrace();
    }


      return myDate;
  }

  //NOTE: parseDate method WITHOUT tweet4j status object as a parameter. There is another parseDate method!
  public static String parseDate(HttpServletRequest request, String textInput, String context){

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
      myDate= org.ecocean.ai.nlp.SUTime.parseDateStringForBestDate(request, textInput);
      System.out.println(">>>>>> NLP found date: "+myDate);


    }
    catch(Exception e){
      System.out.println("Exception in NLP in IBEISIA.class");
      e.printStackTrace();
    }


      return myDate;
  }

  /*
  // Same as above method, but this will return an arraylist instead of a string
  public static ArrayList<String> parseDateToArrayList(String inputText, String context){
    ArrayList<String> parsedDates = new ArrayList<String>();

    // Attempt to detect language of input text
    try {
      String detectedLanguage = DetectTranslate.detectLanguage(inputText, context);
      if(!detectedLanguage.toLowerCase().startsWith("en")){
        inputText = DetectTranslate.translateToEnglish(inputText, context);
      }
    } catch (Exception e){
      System.out.println("Exception trying to detect language");
      e.printStackTrace();
    }

    try {
      System.out.println(">>>>> looking for date with NLP");
      // Call NLP function to find and select a date from input
      // This will return an arraylist of date strings
      parsedDates = org.ecocean.ai.nlp.SUTime.
    } catch (Exception e){
      System.out.println("Exception in NLP");
      e.printStackTrace();
    }

    return parsedDates;
  }
  */

  
  /*
  public static void date(Occurrence occ, Shepherd myShepherd, HttpServletRequest request, String context, String text) {
    System.out.println(">>>>>> detection created " + occ.toString());

    //set the locationID/location/date on all encounters by inspecting detected comments on the first encounter
//    if((occ.getEncounters()!=null)&&(occ.getEncounters().get(0)!=null)){


      String locCode=null;
      String location="";
      int year=-1;
      int month=-1;
      int day=-1;

      List<Encounter> encounters=occ.getEncounters();
      int numEncounters=encounters.size();
      Encounter enc=encounters.get(0);

      String detectedLanguage="en";
      try{
        detectedLanguage= DetectTranslate.detect(text, context);

        if(!detectedLanguage.toLowerCase().startsWith("en")){
          text= DetectTranslate.translate(text, context);
        }
      }
      catch(Exception e){
        System.out.println("I hit an exception trying to detect language.");
        e.printStackTrace();
      }

      Properties props = new Properties();

        //OK, let's check the comments and tags for retrievable metadata
        try {

          //first parse for location and locationID
          props=ShepherdProperties.getProperties("submitActionClass.properties", "",context);
          Enumeration m_enum = props.propertyNames();
          while (m_enum.hasMoreElements()) {
            String aLocationSnippet = ((String) m_enum.nextElement()).trim();
            if (text.indexOf(aLocationSnippet) != -1) {
              locCode = props.getProperty(aLocationSnippet);
              location+=(aLocationSnippet+" ");
            }
          }



          //reset remarks to avoid dates embedded in researcher comments
//          remarks=enc.getOccurrenceRemarks().trim().toLowerCase();
          //if no one has set the date already, use NLP to try to figure it out
//          boolean setDate=true;
//          if(enc.getDateInMilliseconds()!=null){setDate=false;}
          //next use natural language processing for date
//          if(setDate){
            boolean NLPsuccess=false;
            try{
                System.out.println(">>>>>> looking for date with NLP");
                //call Stanford NLP function to find and select a date from ytRemarks
                String myDate= ServletUtilities.nlpDateParse(text);
                //parse through the selected date to grab year, month and day separately.Remove cero from month and day with intValue.
                if (myDate!=null) {
                    System.out.println(">>>>>> NLP found date: "+myDate);
                    int numCharact= myDate.length();

                    if(numCharact>=4){

                      try{
                        year=(new Integer(myDate.substring(0, 4))).intValue();
                        NLPsuccess=true;

                        if(numCharact>=7){
                          try {
                            month=(new Integer(myDate.substring(5, 7))).intValue();
                            if(numCharact>=10){
                              try {
                                day=(new Integer(myDate.substring(8, 10))).intValue();
                                }
                              catch (Exception e) { day=-1; }
                            }
                          else{day=-1;}
                          }
                          catch (Exception e) { month=-1;}
                        }
                        else{month=-1;}

                      }
                      catch(Exception e){
                        e.printStackTrace();
                      }
                  }

                }

            }
            catch(Exception e){
                System.out.println("Exception in NLP in IBEISIA.class");
                e.printStackTrace();
            }

              //NLP failure? let's try brute force detection across all languages supported by this Wildbook
              if(!NLPsuccess){
                System.out.println(">>>>>> looking for date with brute force");
                //next parse for year
                LocalDateTime dt = new LocalDateTime();
                int nowYear=dt.getYear();
                int oldestYear=nowYear-20;
                for(int i=nowYear;i>oldestYear;i--){
                  String yearCheck=(new Integer(i)).toString();
                  if (text.indexOf(yearCheck) != -1) {
                    year=i;
                    System.out.println("...detected a year in comments!");

                    }

                  }
            }

            //end brute force date detection if NLP failed


              //if we found a date via NLP or brute force, let's use it here
              if(year>-1){
                for(int i=0;i<numEncounters;i++){
                  Encounter enctemp=encounters.get(i);
                  enctemp.setYear(year);
                  if(month>-1){
                    enctemp.setMonth(month);
                    if(day>-1){enc.setDay(day);}
                  }
                }

              }

//        }//end if set date


          }

        catch (Exception props_e) {
          props_e.printStackTrace();
        }
//      }

      //if we found a locationID, iterate and set it on every Encounter
      if(locCode!=null){

        for(int i=0;i<numEncounters;i++){
          Encounter enctemp=encounters.get(i);
          enctemp.setLocationID(locCode);
          if(!location.equals("")){enctemp.setLocation(location.trim());}
        }
      }


      //set the Wildbook A.I. user if it exists
      if(myShepherd.getUser("wildbookai")!=null){
        for(int i=0;i<numEncounters;i++){
          Encounter enctemp=encounters.get(i);
          enctemp.setSubmitterID("wildbookai");
        }
      }

      Properties quest = new Properties();

      quest= ShepherdProperties.getProperties("quest.properties", detectedLanguage);

      String commentToPost=null;

      if((enc.getDateInMilliseconds()!=null)||(locCode!=null)){
        commentToPost= quest.getProperty("muchThanks");

      }

      else if((enc.getDateInMilliseconds()==null)&&(locCode==null)){
        commentToPost= quest.getProperty("thanksAnyway");

      }


      if(commentToPost!=null){
        String commentId= occ.getSocialMediaQueryCommentID();
        try{
          YouTube.sendReply(commentId, commentToPost);
        }
        catch(Exception e){e.printStackTrace();}
      }



//    }
  }
  */
  
  
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
  

}