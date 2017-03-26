package org.ecocean.datacollection;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.io.*;

import javax.jdo.Query;
import javax.servlet.http.HttpServletRequest;

import org.ecocean.*;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.security.Collaboration;

import org.joda.time.DateTime;

public class DataSheetQueryProcessor extends QueryProcessor {

  private static final String BASE_FILTER = "SELECT FROM org.ecocean.datacollection.DataSheet WHERE \"DATACOLLECTIONEVENTID\" != null && ";

  public static final String[] SIMPLE_STRING_FIELDS = new String[]{"name","type","samplingProtocol", "samplingEffort", "fieldNotes", "eventRemarks", "institutionCode", "institutionID"};


  public static String queryStringBuilder(HttpServletRequest request, StringBuffer prettyPrint, Map<String, Object> paramMap){

    String filter= BASE_FILTER;
    String jdoqlVariableDeclaration = "";
    String parameterDeclaration = "";
    String context="context0";
    context=ServletUtilities.getContext(request);

    Shepherd myShepherd=new Shepherd(context);
    myShepherd.setAction("DataSheetQueryProcessor.class");

    //filter for id------------------------------------------
    filter = QueryProcessor.filterWithBasicStringField(filter, "id", request, prettyPrint);

    // filter for simple string fields
    for (String fieldName : SIMPLE_STRING_FIELDS) {
      System.out.println("   parsing datasheet query for field "+fieldName);
      System.out.println("           current filter = "+filter);
      filter = QueryProcessor.filterWithBasicStringField(filter, fieldName, request, prettyPrint);
    }

    // GPS box
    filter = QueryProcessor.filterWithGpsBox(filter, request, prettyPrint);

    // make sure no trailing ampersands
    filter = QueryProcessor.removeTrailingAmpersands(filter);
    filter += jdoqlVariableDeclaration;
    filter += parameterDeclaration;
    System.out.println("DataSheetQueryProcessor filter: "+filter);
    return filter;
  }

  public static DataSheetQueryResult processQuery(Shepherd myShepherd, HttpServletRequest request, String order){

    Vector<DataSheet> rDataSheets=new Vector<DataSheet>();
    Iterator<DataSheet> allDataSheets;
    String filter="";
    StringBuffer prettyPrint=new StringBuffer("");
    Map<String,Object> paramMap = new HashMap<String, Object>();

    filter=queryStringBuilder(request, prettyPrint, paramMap);
    Query query=myShepherd.getPM().newQuery(filter);
    if(!order.equals("")){query.setOrdering(order);}

    if(!filter.trim().equals("")){
      allDataSheets=myShepherd.getAllDataSheets(query, paramMap);
    } else {
      allDataSheets=myShepherd.getAllDataSheetsNoQuery();
    }

    if(allDataSheets!=null){
      while (allDataSheets.hasNext()) {
        DataSheet temp_dat=allDataSheets.next();
        rDataSheets.add(temp_dat);
      }
    }
  	query.closeAll();

    return (new DataSheetQueryResult(rDataSheets,filter,prettyPrint.toString()));
  }
}
