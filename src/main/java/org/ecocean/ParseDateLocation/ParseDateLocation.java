package org.ecocean.ParseDateLocation;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.regex.*;

import javax.servlet.http.HttpServletRequest;

import org.ecocean.Encounter;
import org.ecocean.Occurrence;
import org.ecocean.Shepherd;
import org.ecocean.ShepherdProperties;
import org.ecocean.YouTube;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.YouTubeAssetStore;
import org.ecocean.ocr.ocr;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.translate.DetectTranslate;
import org.joda.time.LocalDateTime;

public class ParseDateLocation {

  public static String parseLocation (String text, String context){
    String location="";
    String locationCode="";
    Properties locationCodes = new Properties();

    //Detect language and translate if needed
    try{
      String detectedLanguage = DetectTranslate.detectLanguage(text, context);
      if(!detectedLanguage.toLowerCase().startsWith("en")){
        text= DetectTranslate.translateToEnglish(text, context);
        System.out.println("Translated text for parseLocation is " + text);
      }
    } catch(Exception e){
      System.out.println("Exception trying to detect language.");
      e.printStackTrace();
    }

    //Parse any text matching a location from submitActionClass.properties and map them to their location codes
    try{
      System.out.println("mf getting properties before");
      locationCodes=ShepherdProperties.getProperties("submitActionClass.properties", "",context);
      System.out.println("mf getting properties after");
      Enumeration locationCodesEnum = locationCodes.propertyNames();
      String textToLowerCase = text.toLowerCase();
      while (locationCodesEnum.hasMoreElements()) {
        String currentLocationQuery = ((String) locationCodesEnum.nextElement()).trim().toLowerCase();
        System.out.println("mf currentLocationQuery is " + currentLocationQuery);
        if (textToLowerCase.indexOf(currentLocationQuery) != -1) {
          System.out.println("mf got an index match!");
          locationCode = locationCodes.getProperty(currentLocationQuery);
          System.out.println("Location code encountered in parseLocation: " + locationCode);
          location+=(currentLocationQuery+", ");
        }
      }
    } catch(Exception e){
      e.printStackTrace();
      System.out.println("Exception trying to comb for location codes.");
    }

    //Use natural language processing to try to extract location
    try{
      location += ServletUtilities.nlpLocationParse(text);
    } catch(Exception e){
      System.out.println("Exception trying to run nlpLocationParse.");
      e.printStackTrace();
    }

    //Parse gps coordinates
    try{
      // String PATTERN = ".*?([+-]?\\d+\\.?\\d+)\\s*,\\s*([+-]?\\d+\\.?\\d+).*?"; //doesn't seem as robust as
      String PATTERN = ".?[-+]?([1-8]?\\d(\\.\\d+)?|90(\\.0+)?),\\s*[-+]?(180(\\.0+)?|((1[0-7]\\d)|([1-9]?\\d))(\\.\\d+)?).?";
      Pattern pattern = Pattern.compile(PATTERN);
      Matcher matcher = pattern.matcher(text);
      if(matcher.matches()){
        String gpsCoords = matcher.group(0);
        System.out.println("GPS coordinates found: " + gpsCoords + " Adding to location.");
        location += gpsCoords;
      }

    } catch(Exception e){
      System.out.println("Exception trying to parse gps coordinates.");
      e.printStackTrace();
    }

    return location;
  }

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

}
