package org.ecocean;

import java.util.Map;
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
  // same as above, but also does bad practice of modifying inpnut variable prettyPrint (this makes for nice 'n' readable code!)
  protected static String filterWithBasicStringField(String filter, String fieldName, HttpServletRequest request, StringBuffer prettyPrint) {
    prettyPrint.append(prettyPrintUpdateForBasicString(fieldName, request));
    return filterWithBasicStringField(filter, fieldName, request);
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

}
