package org.ecocean;

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

public class OccurrenceQueryProcessor extends QueryProcessor {

  private static final String BASE_FILTER = "SELECT FROM org.ecocean.Occurrence WHERE \"OCCURRENCEID\" != null && ";

  public static final String[] SIMPLE_STRING_FIELDS = new String[]{"soil","rain","activity","habitatOpenness","grassGreenness","grassHeight","weather","wind"};



  public static String queryStringBuilder(HttpServletRequest request, StringBuffer prettyPrint, Map<String, Object> paramMap){

    String filter= BASE_FILTER;
    String jdoqlVariableDeclaration = "";
    String parameterDeclaration = "";
    String context="context0";
    context=ServletUtilities.getContext(request);

    Shepherd myShepherd=new Shepherd(context);
    //myShepherd.setAction("OccurrenceQueryProcessor.class");

    //filter for id------------------------------------------
    filter = QueryProcessor.filterWithBasicStringField(filter, "id", request, prettyPrint);
    System.out.println("           beginning filter = "+filter);

    // filter for simple string fields
    for (String fieldName : SIMPLE_STRING_FIELDS) {
      System.out.println("   parsing occurrence query for field "+fieldName);
      System.out.println("           current filter = "+filter);
      filter = QueryProcessor.filterWithBasicStringField(filter, fieldName, request, prettyPrint);
    }

    // GPS box
    filter = QueryProcessor.filterWithGpsBox(filter, request, prettyPrint);

    // make sure no trailing ampersands
    filter = QueryProcessor.removeTrailingAmpersands(filter);
    filter += jdoqlVariableDeclaration;
    filter += parameterDeclaration;
    System.out.println("OccurrenceQueryProcessor filter: "+filter);
    return filter;
  }

  public static OccurrenceQueryResult processQuery(Shepherd myShepherd, HttpServletRequest request, String order){

    Vector<Occurrence> rOccurrences=new Vector<Occurrence>();
    Iterator<Occurrence> allOccurrences;
    String filter="";
    StringBuffer prettyPrint=new StringBuffer("");
    Map<String,Object> paramMap = new HashMap<String, Object>();

    filter=queryStringBuilder(request, prettyPrint, paramMap);
    System.out.println("OccurrenceQueryResult: has filter "+filter);
    Query query=myShepherd.getPM().newQuery(filter);
    System.out.println("                       got query "+query);
    System.out.println("                       has paramMap "+paramMap);
    if(!order.equals("")){query.setOrdering(order);}
    System.out.println("                 still has query "+query);
    if(!filter.trim().equals("")){
      System.out.println(" about to call myShepherd.getAllOccurrences on query "+query);
      allOccurrences=myShepherd.getAllOccurrences(query, paramMap);
    } else {
      System.out.println(" about to call myShepherd.getAllOccurrencesNoQuery() ");
      allOccurrences=myShepherd.getAllOccurrencesNoQuery();
    }
    System.out.println("               *still* has query "+query);


    if(allOccurrences!=null){
      while (allOccurrences.hasNext()) {
        Occurrence temp_dat=allOccurrences.next();
        rOccurrences.add(temp_dat);
      }
    }
  	query.closeAll();

    System.out.println("about to return OccurrenceQueryResult with filter "+filter+" and nOccs="+rOccurrences.size());
    return (new OccurrenceQueryResult(rOccurrences,filter,prettyPrint.toString()));
  }
}
