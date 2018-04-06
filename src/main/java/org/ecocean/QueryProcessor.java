package org.ecocean;

import java.util.Map;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import javax.jdo.Query;
import javax.servlet.http.HttpServletRequest;

import org.ecocean.Util.*;
import org.ecocean.servlet.ServletUtilities;


public abstract class QueryProcessor {

  static String BASE_FILTER;

  // a bunch of util methods
  protected static String prepForCondition(String filter) {
    if (endsWithAmpersands(filter)) return filter;
    else return (filter += " && ");
  }

  protected static boolean endsWithAmpersands (String filter) {
    return (filter.trim().endsWith("&&"));
  }

  protected static String removeTrailingAmpersands (String filter) {
    if (!endsWithAmpersands(filter)) return filter;
    String ret = filter.trim();
    return ret.substring(0, ret.length()-2);
  }

  protected static String stringFieldSubFilter(HttpServletRequest request, String fieldName) {
    String val = request.getParameter(fieldName);
    if ( !Util.stringExists(val) ) return "";
    val = Util.undoUrlEncoding(val);
    return Util.jdoStringContainsConstraint(fieldName, val, true);
  }

  protected static String filterWithBasicStringField(String filter, String fieldName, HttpServletRequest request) {
    String subFilter = stringFieldSubFilter(request, fieldName);
    System.out.println("filterWithBasicStringField: field "+fieldName+" made subFilter "+subFilter);
    if (!Util.stringExists(subFilter)) return filter;
    filter = prepForCondition(filter);
    filter+= subFilter;
    return filter;
  }
  // same as above, but also does bad practice of modifying input variable prettyPrint (this makes for nice 'n' readable code!)
  protected static String filterWithBasicStringField(String filter, String fieldName, HttpServletRequest request, StringBuffer prettyPrint) {
    prettyPrint.append(prettyPrintUpdateForBasicString(fieldName, request));
    return filterWithBasicStringField(filter, fieldName, request);
  }
  
  // This is probably not the ideal place for a method with this narrow of usage, but I've put it here in the interest
  // of saving time because it can be applied to survey and eventually indy.
  protected static String filterObservations(String filter, HttpServletRequest request, StringBuffer prettyPrint, String objectType) {
    filter = prepForCondition(filter);

    Enumeration<String> allParams = request.getParameterNames();
    if (allParams!=null) {
      int numObsSearched = 0;
      String keyID = "observationKey";
      String valID = "observationValue";
      HashMap<String,String> obKeys = new HashMap<>();
      HashMap<String,String> obVals = new HashMap<>();
      StringBuilder obQuery = new StringBuilder();
      while (allParams.hasMoreElements()) {
        String thisParam = allParams.nextElement();
        if (thisParam!=null&&thisParam.startsWith(keyID)) {
          System.out.println("===================================================== Checking this Param: "+thisParam);
          String keyParam = request.getParameter(thisParam).trim();
          String keyNum = thisParam.replace(keyID,"");
          if (keyParam!=null&&!keyParam.equals("")) {
            numObsSearched++;
            System.out.println("Searching Ob #"+numObsSearched);
            obKeys.put(keyNum,keyParam);            
          }
        }
        if (thisParam!=null&&thisParam.startsWith(valID)) {
          String valParam = request.getParameter(thisParam).trim();
          String valNum = thisParam.replace(valID,"");
          if (valParam!=null&&!valParam.equals("")) {
            obVals.put(valNum,valParam);            
          }
        }
      }  
      for (int i=0;i<=numObsSearched;i++) {
        String num = String.valueOf(i);
        if (Util.basicSanitize(obKeys.get(num))!=null) {
          String thisKey = Util.basicSanitize(obKeys.get(num));
          prettyPrint.append("observation ");
          prettyPrint.append(thisKey);
          prettyPrint.append("<br/>");
          obQuery.append("(observations.contains(observation"+num+") && ");
          obQuery.append("observation"+num+".name == "+Util.quote(thisKey.trim()));        
          if (obVals.get(num)!=null&&!obVals.get(num).trim().equals("")) {
            String thisVal = Util.basicSanitize(obVals.get(num));
            prettyPrint.append(" is ");
            prettyPrint.append(thisVal);              
            obQuery.append(" && observation"+num+".value == "+Util.quote(thisVal.trim())); 
          }
          obQuery.append(")");
          if (1<numObsSearched) {
            obQuery.append(" && ");
          }
        }
      }
      System.out.println("Actual ObQuery: "+obQuery);
      System.out.println("Actual Filter: "+filter);
      if (obQuery.length() > 0) {
        if (!filter.equals("SELECT FROM org.ecocean."+objectType+" WHERE 'ID' != null &&")) {
          if (!filter.trim().endsWith("&&")) {
            filter += " && ";            
          }
        }
        filter += obQuery.toString();
      }
    }
    return filter;
  }
  
  protected static int getNumberOfObservationsInQuery(HttpServletRequest request) {
    int numObsSearched = 0;
    if (request.getParameter("numSearchedObs")!=null) {
      numObsSearched = Integer.valueOf(request.getParameter("numSearchedObs"));
      System.out.println("Num Obs Searched? "+numObsSearched);
      if (request.getParameter("observationKey1")!=null&&!request.getParameter("observationKey1").equals("")) {
        return numObsSearched;     
      }
    }
    return 0;
  }

  protected static String prettyPrintUpdateForBasicString(String fieldName, HttpServletRequest request) {
    if (!Util.stringExists(request.getParameter(fieldName))) return "";
    return (fieldName+" contains \""+request.getParameter(fieldName)+"\".<br />");
  }


  // Assumes the params ne_lat...sw_long define a box where the
  // object's latitude and longitude should be within
  protected static String gpsBoxSubFilter(HttpServletRequest request) {
    String ne_latStr = request.getParameter("ne_lat");
    String ne_longStr = request.getParameter("ne_long");
    String sw_latStr = request.getParameter("sw_lat");
    String sw_longStr = request.getParameter("sw_long");

    if (!(Util.stringExists(ne_latStr) && Util.stringExists(ne_longStr) && Util.stringExists(sw_latStr) && Util.stringExists(sw_longStr))) return "";

    try {

      double ne_lat  = (new Double(ne_latStr ).doubleValue());
      double ne_long = (new Double(ne_longStr).doubleValue());
      double sw_lat  = (new Double(sw_latStr ).doubleValue());
      double sw_long = (new Double(sw_longStr).doubleValue());

      String subFilter="(";
      // TBQF I'm not sure what the below logic does
      if ((sw_long>0)&&(ne_long<0)){
        subFilter += "(latitude <= "+request.getParameter("ne_lat")+") && (latitude >= "+request.getParameter("sw_lat")+")";
        subFilter += " && ((longitude <= "+request.getParameter("ne_long")+") || (longitude >= "+request.getParameter("sw_long")+"))";
      }
      else {
        subFilter += "(latitude <= "+request.getParameter("ne_lat")+") && (latitude >= "+request.getParameter("sw_lat")+")";
        subFilter += " && (longitude <= "+request.getParameter("ne_long")+") && (longitude >= "+request.getParameter("sw_long")+")";
      }

      subFilter+=" )";
      return subFilter;
    }

    catch(Exception ee){
      System.out.println("Exception when trying to process lat and long data in DataSheetQueryProcessor!");
      ee.printStackTrace();
    }
    return "";
  }

  protected static String filterWithGpsBox(String filter, HttpServletRequest request) {
    String subFilter = gpsBoxSubFilter(request);
    if (!Util.stringExists(subFilter)) return filter;
    filter = prepForCondition(filter);
    return (filter + subFilter);
  }
  // like above but also prettyPrints
  protected static String filterWithGpsBox(String filter, HttpServletRequest request, StringBuffer prettyPrint) {
    prettyPrint.append(prettyPrintGpsBox(request));
    return (filterWithGpsBox(filter, request));
  }

  protected static String prettyPrintGpsBox(HttpServletRequest request) {
    String ne_latStr = request.getParameter("ne_lat");
    String ne_longStr = request.getParameter("ne_long");
    String sw_latStr = request.getParameter("sw_lat");
    String sw_longStr = request.getParameter("sw_long");

    if (!(Util.stringExists(ne_latStr) && Util.stringExists(ne_longStr) && Util.stringExists(sw_latStr) && Util.stringExists(sw_longStr))) return "";

    try {
      double ne_lat  = (new Double(ne_latStr ).doubleValue());
      double ne_long = (new Double(ne_longStr).doubleValue());
      double sw_lat  = (new Double(sw_latStr ).doubleValue());
      double sw_long = (new Double(sw_longStr).doubleValue());

      String ret = "GPS Boundary NE: \""+ne_lat+", "+ne_long+"\".<br />";
      ret += "GPS Boundary SW: \""+sw_lat+", "+sw_long+"\".<br />";
      return ret;
    }
    catch (Exception e) {
    }
    return "";

  }


  protected static String updateJdoqlVariableDeclaration(String jdoqlVariableDeclaration, String typeAndVariable) {
    StringBuilder sb = new StringBuilder(jdoqlVariableDeclaration);
    if (jdoqlVariableDeclaration.length() == 0) {
      sb.append(" VARIABLES ");
      sb.append(typeAndVariable);
    }
    else {
      if (!jdoqlVariableDeclaration.contains(typeAndVariable)) {
        sb.append("; ");
        sb.append(typeAndVariable);
      }
    }
    return sb.toString();
  }

  protected static String updateParametersDeclaration(String parameterDeclaration, String typeAndVariable) {
    StringBuilder sb = new StringBuilder(parameterDeclaration);
    if (parameterDeclaration.length() == 0) {
      sb.append(" PARAMETERS ");
    }
    else {
      sb.append(", ");
    }
    sb.append(typeAndVariable);
    return sb.toString();
  }
  
  public static String filterDateRanges(HttpServletRequest request, String filter, StringBuffer prettyPrint) {
    String endTimeFrom = null;
    String endTimeTo = null;
    String startTimeFrom = null;
    String startTimeTo = null;
    
    try {
      filter = prepForNext(filter);
      if (request.getParameter("startTimeFrom")!=null&&request.getParameter("startTimeFrom").length()>8) {
        startTimeFrom = monthDayYearToMilli(request.getParameter("startTimeFrom"));
        // Crush date
        String addition = " (startTime >=  "+startTimeFrom+") ";
        prettyPrint.append(addition);
        filter += addition;
      }      
    } catch (NullPointerException npe) {
      npe.printStackTrace();
    }
    
    try {
      filter = prepForNext(filter);
      if (request.getParameter("startTimeTo")!=null&&request.getParameter("startTimeTo").length()>8) {
        startTimeTo = monthDayYearToMilli(request.getParameter("startTimeTo"));
        // Crush date
        String addition = " (startTime <=  "+startTimeTo+") ";
        prettyPrint.append(addition);
        filter += addition;
      }      
    } catch (NullPointerException npe) {
      npe.printStackTrace();
    }
    
    try {
      filter = prepForNext(filter);
      if (request.getParameter("endTimeFrom")!=null&&request.getParameter("endTimeFrom").length()>8) {
        endTimeFrom = monthDayYearToMilli(request.getParameter("endTimeFrom"));
        // Crush date
        String addition = " (endTime >=  "+endTimeFrom+") ";
        prettyPrint.append(addition);
        filter += addition;
      }      
    } catch (NullPointerException npe) {
      npe.printStackTrace();
    }
    
    try {
      filter = prepForNext(filter);
      if (request.getParameter("endTimeTo")!=null&&request.getParameter("endTimeFrom").length()>8) {
        endTimeTo = monthDayYearToMilli(request.getParameter("endTimeTo"));
        // Crush date
        String addition = " (startTime <=  "+endTimeTo+") ";
        prettyPrint.append(addition);
        filter += addition;
      }      
    } catch (NullPointerException npe) {
      npe.printStackTrace();
    }
    
    filter = prepForNext(filter);
    System.out.println("This filter: "+filter);
    return filter;
  }
  
 public static String prepForNext(String filter) {
   if (!QueryProcessor.endsWithAmpersands(filter)) {
     filter = QueryProcessor.prepForCondition(filter);
   }
   return filter;
 }
 
 private static String monthDayYearToMilli(String newDate) {
   System.out.println("This is the input date: "+newDate);
   SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy");
   String month = newDate.substring(0,2);
   String day = newDate.substring(3,5);
   String year = newDate.substring(6,10);
   Date dt;
   try {
     dt = sdf.parse(month+"-"+day+"-"+year);
   } catch (ParseException e) {
     e.printStackTrace();
     System.out.println("Failed to Parse String : "+month+"-"+day+"-"+year);
     return null;
   }
   return String.valueOf(dt.getTime());
 }

}
