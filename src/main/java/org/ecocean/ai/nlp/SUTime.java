package org.ecocean.ai.nlp;


import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import org.ecocean.servlet.*;
import java.io.IOException;
import javax.servlet.http.*;
import edu.stanford.nlp.time.Options;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.TimeExpression;
import edu.stanford.nlp.time.SUTime.Temporal;
import edu.stanford.nlp.util.StringUtils;
import twitter4j.Status;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import org.apache.commons.lang3.StringEscapeUtils;




public class SUTime {


  public static boolean parseBoolean(String value) {
    if (StringUtils.isNullOrEmpty(value)) {
      return false;
    }
    if (value.equalsIgnoreCase("on")) {
      return true;
    }
    return Boolean.parseBoolean(value);
  }






  private static String getRuleFilepaths(HttpServletRequest request,String... files) {
    String rulesDir="";
    try {
      rulesDir = request.getSession().getServletContext().getRealPath("/WEB-INF/data/sutime/rules");
    }
    catch(NullPointerException npe) {
      //OK, we couldn't find a servlet context, so let's try to get the files from a hardcoded override directory
      rulesDir="/data/wildbook_data_dir/WEB-INF/data/sutime/rules";
      
    }
    
    StringBuilder sb = new StringBuilder();
    for (String file:files) {
      if (sb.length() > 0) {
        sb.append(",");
      }
      sb.append(rulesDir + "/" + file);
      System.out.println("Loading SUTime rules file: "+rulesDir + "/" + file);
    }
    return sb.toString();
  }

 
  private static Properties getTimeAnnotatorProperties(HttpServletRequest request) {
    // Parses request and set up properties for time annotators
    
    System.out.println("Entering "+"SUTime.getTimeAnnotatorProperties");
    
    boolean markTimeRanges =
            parseBoolean(request.getParameter("markTimeRanges"));
    boolean includeNested =
            parseBoolean(request.getParameter("includeNested"));
    boolean includeRange =
            parseBoolean(request.getParameter("includeRange"));

    String heuristicLevel = request.getParameter("relativeHeuristicLevel");
    Options.RelativeHeuristicLevel relativeHeuristicLevel =
            Options.RelativeHeuristicLevel.NONE;
    if ( ! StringUtils.isNullOrEmpty(heuristicLevel)) {
      relativeHeuristicLevel = Options.RelativeHeuristicLevel.valueOf(heuristicLevel);
    }
    String ruleFile = null;
    ruleFile = getRuleFilepaths(request, "defs.sutime.txt", "english.sutime.txt", "english.holidays.sutime.txt");


    // Create properties
    Properties props = new Properties();
    if (markTimeRanges) {
      props.setProperty("sutime.markTimeRanges", "true");
    }
    if (includeNested) {
      props.setProperty("sutime.includeNested", "true");
    }
    if (includeRange) {
      props.setProperty("sutime.includeRange", "true");
    }
    if (ruleFile != null) {
      props.setProperty("sutime.rules", ruleFile);
      props.setProperty("sutime.binders", "1");
      props.setProperty("sutime.binder.1", "edu.stanford.nlp.time.JollyDayHolidays");
      
      try {
        props.setProperty("sutime.binder.1.xml", request.getSession().getServletContext().getRealPath("/WEB-INF/data/holidays/Holidays_sutime.xml"));
      }
      catch(NullPointerException npe) {
        props.setProperty("sutime.binder.1.xml", "/data/wildbook_data_dir/WEB-INF/data/holidays/Holidays_sutime.xml");
        
      }
      
      props.setProperty("sutime.binder.1.pathtype", "file");
    }
    props.setProperty("sutime.teRelHeurLevel",
            relativeHeuristicLevel.toString());
    props.setProperty("sutime.verbose", "true");

//    props.setProperty("heideltime.path", getServletContext().getRealPath("/packages/heideltime"));
//    props.setProperty("gutime.path", getServletContext().getRealPath("/packages/gutime"));
    return props;
  }

  
  private static List<CoreMap> getTimeAnnotations(String query, Annotation anno, boolean includeOffsets) {
    List<CoreMap> timexAnns = anno.get(TimeAnnotations.TimexAnnotations.class);
    List<String> pieces = new ArrayList<String>();
    List<Boolean> tagged = new ArrayList<Boolean>();
    int previousEnd = 0;
    for (CoreMap timexAnn : timexAnns) {
      int begin =
              timexAnn.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
      int end =
              timexAnn.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
      if (begin >= previousEnd) {
        pieces.add(query.substring(previousEnd, begin));
        tagged.add(false);
        pieces.add(query.substring(begin, end));
        tagged.add(true);
        previousEnd = end;
      }
    }
    if (previousEnd < query.length()) {
      pieces.add(query.substring(previousEnd));
      tagged.add(false);
    }

    return timexAnns;


  }

  
  //use the current date as the reference date
  private static List<CoreMap> getDates(String query,
      HttpServletRequest request,
        SUTimePipeline pipeline) throws IOException {
        
      String dateString = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
      return getDates(query, request, pipeline, dateString);
  }
  
  
  //use a provided date as a reference date
  private static List<CoreMap> getDates(String query,
                        HttpServletRequest request,
                          SUTimePipeline pipeline, String dateString)
    throws IOException {

    System.out.println("Entering SUTime.getDates!");
    
    boolean includeOffsets = parseBoolean(request.getParameter("includeOffsets"));
    if ( ! StringUtils.isNullOrEmpty(query)) {
      Properties props = getTimeAnnotatorProperties(request);
      System.out.println(("Found props: "+props.toString()));
      String annotatorType = request.getParameter("annotator");
      if (annotatorType == null) {
        annotatorType = "sutime";
      }
      Annotator timeAnnotator = pipeline.getTimeAnnotator(annotatorType, props);
      if (timeAnnotator != null) {
        Annotation anno = pipeline.process(query, dateString, timeAnnotator);
        //out.println("<h3>Annotated Text</h3> <em>(tagged using " + annotatorType + "</em>)");
      
        
        List<CoreMap> timexAnns=getTimeAnnotations(query, anno, includeOffsets);
        
        return timexAnns;
      
      
      } else {
        System.out.println("<br><br>Error creating annotator for " + StringEscapeUtils.escapeHtml4(annotatorType));
      }
      

    }
    return null;

  }
  
  
  public static SUTimePipeline createPipeline(HttpServletRequest request) {
    
    System.out.println("Creating the SUTimePipeline!");
    SUTimePipeline pipeline; // = null;
    String dataDir = "";
    
    //check if we're calling this from a servlet context, which we should be
    try{
      dataDir=request.getSession().getServletContext().getRealPath("/WEB-INF/data");
      System.setProperty("de.jollyday.config", request.getSession().getServletContext().getRealPath("/WEB-INF/classes/holidays/jollyday.properties"));
    }
    catch(NullPointerException npe) {
      
      //OK, we couldn't find a servlet context, so let's try to get the files from a hardcoded override directory
      dataDir="/data/wildbook_data_dir/WEB-INF/data";
      System.setProperty("de.jollyday.config", ("/data/wildbook_data_dir/WEB-INF/classes/holidays/jollyday.properties"));
      
    }
    String taggerFilename = dataDir + "/english-left3words-distsim.tagger";
    Properties pipelineProps = new Properties();
    System.out.println("pos.model set to "+taggerFilename);
    pipelineProps.setProperty("pos.model", taggerFilename);
    pipeline = new SUTimePipeline(pipelineProps);
    return pipeline;
    
  }
  
  public static ArrayList<String> parseStringForDates(HttpServletRequest request, String text) {
    
    ArrayList<String> arrayListDates = new ArrayList<String>();
    
    SUTimePipeline pipeline=createPipeline(request);
    
    //clean up the text
    System.out.println("parseDates with text " + text);
    
    //text = text.replaceAll("[,.!?;:]", "$0 ");
    //System.out.println("text: " + text);
    //String[] text1 = text.replaceAll("[^A-Za-z0-9 ]", " ").toLowerCase().split("\\s+"); //TODO I think this does a better version of what the above (text = text.replaceAll("[,.!?;:]", "$0 ");) does?? -Mark Fisher
    //String text2 = String.join(" ", text1);

    //System.out.println("Cleaned up text to text2: " + text2);
    
    

      try {
        if (request.getCharacterEncoding() == null) {
          request.setCharacterEncoding("utf-8");
        }
      }
      catch(Exception e) {
        e.printStackTrace();
      }

      try {
        
        List<CoreMap> timexAnnsAll=getDates(text,request, pipeline);
        
        for (CoreMap cm : timexAnnsAll) {
          Temporal myDate = cm.get(TimeExpression.Annotation.class).getTemporal();
          //        TimeExpression.Annotation:The CoreMap key for storing a TimeExpression annotation.
          String dateStr = myDate.toString();
          System.out.println(".....found date: " + dateStr);
          arrayListDates.add(dateStr.replaceAll("-XX", ""));
        }
        System.out.println("NLP dates found+:" + arrayListDates);

        //if (!arrayListDates.isEmpty()) {
          //turn arrayList into an array to be able to use the old For loop and compare dates.
          
          //return arrayListDates;
        //}
      }
      catch(Exception ioe) {
        ioe.printStackTrace();
      }
      return arrayListDates;
  }  
  
  public static String parseDateStringForBestDate(HttpServletRequest request, String text) {

          ArrayList<String> arrayListDates=parseStringForDates(request, text);
          String selectedDate = "";

          try{
            selectedDate = selectBestDateFromCandidates(arrayListDates);
          } 
          catch(Exception e){
            e.printStackTrace();
          }
          if(selectedDate == null | selectedDate.equals("")){
            return null;
          } 
          else{
            return selectedDate;
          }
      
  }
  
public static String selectBestDateFromCandidates(ArrayList<String> candidates) throws Exception{
  String selectedDate = "";

  if(candidates.size() <1){
    throw new Exception("list of candidate dates was empty");
  } else if (candidates.size() >= 1) {

    //filter by options that are valid dates
    ArrayList<String> validDates = new ArrayList<String>();
    try{
      validDates = removeInvalidDates(candidates);
    } catch(Exception e){
      // e.printStackTrace();
    }
    try{
      System.out.println(validDates);
    } catch(Exception e){
      System.out.println("couldn't print validDates");
      // e.printStackTrace();
    }

    //filter by options that are not in the future
    ArrayList<String> validDatesWithFutureDatesRemoved = new ArrayList<String>();
    try{
      validDatesWithFutureDatesRemoved = removeFutureDates(validDates);
      System.out.println("Before future removal:");
      System.out.println(validDates);
      System.out.println("After future removal:");
      System.out.println(validDatesWithFutureDatesRemoved);
    } catch(Exception e){
      System.out.println("couldn't run removeFutureDates");
      // e.printStackTrace();
    }
    
    //remove excessive past dates
    ArrayList<String> validDatesWithExcessiveDatesRemoved = new ArrayList<String>();
    try{
      validDatesWithExcessiveDatesRemoved = removeExcessivePastDates(validDatesWithFutureDatesRemoved);
      System.out.println("Before excessive past removal:");
      System.out.println(validDatesWithFutureDatesRemoved);
      System.out.println("After excessive past removal:");
      System.out.println(validDatesWithExcessiveDatesRemoved );
    } catch(Exception e){
      System.out.println("couldn't run removeExcessivePastDates");
      // e.printStackTrace();
    }


    //if non-yesterday dates exist as well as yesterday ones, prefer the non-yesterdays. Otherwise, just get the yesterday.
    ArrayList<String> validDatesFilteredByYesterday = new ArrayList<String>();
    try{
      validDatesFilteredByYesterday = removeYesterdayDatesIfTheyAreNotTheOnlyDates(validDatesWithExcessiveDatesRemoved );
      System.out.println(validDatesFilteredByYesterday);
    } catch(Exception e){
      System.out.println("couldn't print validDatesFilteredByYesterday");
      // e.printStackTrace();
    }


    //Now select the longest one?
    if(validDatesFilteredByYesterday == null | validDatesFilteredByYesterday.size()<1){
      throw new Exception("validDatesFilteredByYesterday is null or empty before selecting the longest string");
    }
    if(validDatesFilteredByYesterday.size()>1){
      for (int j = 0; j < validDatesFilteredByYesterday.size(); j++) {
        for (int k = j + 1; k < validDatesFilteredByYesterday.size(); k++) {
          if (validDatesFilteredByYesterday.get(j).length() > validDatesFilteredByYesterday.get(k).length()) {
            selectedDate = validDatesFilteredByYesterday.get(j);
          } else if (validDatesFilteredByYesterday.get(j).length() < validDatesFilteredByYesterday.get(k).length()) {
            selectedDate = validDatesFilteredByYesterday.get(k);
          } else {
            selectedDate = validDatesFilteredByYesterday.get(0);
          }
        }
      }
    } else if(validDatesFilteredByYesterday.size()==1){
      selectedDate = validDatesFilteredByYesterday.get(0);
    }
  }

  if(selectedDate == null | selectedDate.equals("")){
    throw new Exception("selectedDate either null or empty after selecting for longest one");
  } else {
    return selectedDate;
  }
}

public static ArrayList<String> removeInvalidDates(ArrayList<String> candidates) throws Exception{
  ArrayList<String> validDates = new ArrayList<String>();
  DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
  DateFormat df2 = new SimpleDateFormat("yyyy-MM");
  DateFormat df3 = new SimpleDateFormat("yyyy");
  String newDateString = null;
  java.util.Date candiDate;
  for(int i =0; i<candidates.size(); i++){
    String candidateString = candidates.get(i);
    try {
      candiDate = df.parse(candidateString);
      newDateString = df.format(candiDate);
      validDates.add(newDateString);
    } catch (ParseException e) {
      try{
        candiDate = df2.parse(candidateString);
        newDateString = df2.format(candiDate);
        validDates.add(newDateString);
      } catch(Exception f){
        try{
          candiDate = df3.parse(candidateString);
          newDateString = df3.format(candiDate);
          validDates.add(newDateString);
        } catch(Exception g){
          g.printStackTrace();
          continue;
        }
      }

    }
  }
  if(validDates == null | validDates.size()<1){
    throw new Exception("validDates arrayList is empty or null in removeInvalidDates method");
  } else{
    return validDates;
  }
}

public static ArrayList<String> removeFutureDates(ArrayList<String> candidates) throws Exception{
  //TODO add handling for tweets coming from future in datelines
  ArrayList<String> returnCandidates = new ArrayList<String>();
  java.util.Date today =  getToday();
  for(int i = 0; i<candidates.size(); i++){
    String currentDateString = candidates.get(i);
    try{
      java.util.Date currentDateObj = convertStringToDate(currentDateString);
      if(currentDateObj == null){
        System.out.println("currentDateObj in removeFutureDates is null");
      }
      if(!currentDateObj.after(today)){
        returnCandidates.add(currentDateString);
      }
    } catch(Exception e){
      e.printStackTrace();
      continue;
    }
  }
  if(returnCandidates == null | returnCandidates.size()<1){
    throw new Exception("return list is null or empty after removeFutureDates runs");
  } else{
    return returnCandidates;
  }
}

public static java.util.Date convertStringToDate(String dateString) throws Exception{
  DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
  DateFormat df2 = new SimpleDateFormat("yyyy-MM");
  DateFormat df3 = new SimpleDateFormat("yyyy");
  java.util.Date returnDate = null;
  try{
    returnDate = df.parse(dateString);
  } catch(Exception e){
    try{
      returnDate = df2.parse(dateString);
    } catch(Exception f){
      try{
        returnDate = df3.parse(dateString);
      } catch(Exception g){
        g.printStackTrace();
      }
    }
  }

  if(returnDate == null){
    throw new Exception("date in convertStringToDate is null");
  } else{
    return returnDate;
  }
}

public static java.util.Date getToday() {
  final Calendar cal = Calendar.getInstance();
  return cal.getTime();
}

public static ArrayList<String> removeYesterdayDatesIfTheyAreNotTheOnlyDates(ArrayList<String> candidates) throws Exception{
  String yesterday = getYesterdayDateString();
  ArrayList<String> returnCandidates = new ArrayList<String>();
  int yesterdayCounter = 0;
  for(int i = 0; i<candidates.size(); i++){
    if (candidates.get(i).equals(yesterday)){
      yesterdayCounter ++;
    } else {
      returnCandidates.add(candidates.get(i));
    }
  }
  if(yesterdayCounter == candidates.size() | yesterdayCounter == 0){
    //yesterday is the only date or yesterday doesn't occur at all
    returnCandidates = candidates;
  } else if (yesterdayCounter != 0 && yesterdayCounter < candidates.size()){
    //keep returnCandidates as it is from the for loop above
  }
  if(returnCandidates == null | returnCandidates.size()<1){
    throw new Exception("removeYesterdayDatesIfTheyAreNotTheOnlyDates method returned null or empty arrayList");
  } else{
    return returnCandidates;
  }
}

//use this function to remove false positive from NLP that are clearly before the project started, such as "1492"
public static ArrayList<String> removeExcessivePastDates(ArrayList<String> candidates) throws Exception{
//TODO add handling for tweets coming from future in datelines
ArrayList<String> returnCandidates = new ArrayList<String>();
for(int i = 0; i<candidates.size(); i++){
  String currentDateString = candidates.get(i);
  try{
    java.util.Date currentDateObj = convertStringToDate(currentDateString);
    if(currentDateObj == null){
      System.out.println("currentDateObj in removeFutureDates is null");
    }
    if(!currentDateObj.before(convertStringToDate("1930-01-01"))){
      returnCandidates.add(currentDateString);
    }
  } catch(Exception e){
    e.printStackTrace();
    continue;
  }
}
if(returnCandidates == null | returnCandidates.size()<1){
  throw new Exception("return list is null or empty after removeExcessivePastDates runs");
} else{
  return returnCandidates;
}
}

public static String getYesterdayDateString() {
  DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
  return dateFormat.format(getYesterday());
}


public static java.util.Date getYesterday() {
  final Calendar cal = Calendar.getInstance();
  cal.add(Calendar.DATE, -1);
  return cal.getTime();
}

//overloaded version to deal with tweets
public static String parseDateStringForBestDate(HttpServletRequest request, String text, Status tweet) throws Exception{
System.out.println("Entering nlpDateParse twitter version with text " + text);
//create my pipeline with the help of the annotators I added.


  String selectedDate=parseDateStringForBestDate(request, text);

  if(selectedDate == null | selectedDate.equals("")){
    try{
      java.util.Date tweetDate = tweet.getCreatedAt();
      DateFormat dfTweetStamp = new SimpleDateFormat("yyyy-MM-dd");
      selectedDate = dfTweetStamp.format(tweetDate);
      System.out.println("Date of tweet when all other candidates were eliminated is: " + selectedDate);
      return selectedDate;
    } 
    catch(Exception e){
      e.printStackTrace();
      throw new Exception("Couldn't fetch a timeStamp for the tweet to use as a last-resort date after all other candidates were eliminated");
    }
  } else{
    return null;
  }

}


}


