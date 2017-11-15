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

public class SurveyQueryProcessor extends QueryProcessor {

  private static final String BASE_FILTER = "SELECT FROM org.ecocean.Survey WHERE \"ID\" != null && ";

  public static final String[] SIMPLE_STRING_FIELDS = new String[]{"soil","rain","activity","habitatOpenness","grassGreenness","grassHeight","weather","wind"};

  

  public static String queryStringBuilder(HttpServletRequest request, StringBuffer prettyPrint, Map<String, Object> paramMap){

    String filter= BASE_FILTER;
    String jdoqlVariableDeclaration = "";
    String parameterDeclaration = "";
    String context="context0";
    context=ServletUtilities.getContext(request);

    Shepherd myShepherd=new Shepherd(context);
    //myShepherd.setAction("SurveyQueryProcessor.class");

    //filter for id------------------------------------------
    filter = QueryProcessor.filterWithBasicStringField(filter, "id", request, prettyPrint);
    System.out.println("           beginning filter = "+filter);

    // filter for simple string fields
    for (String fieldName : SIMPLE_STRING_FIELDS) {
      System.out.println("   parsing Survey query for field "+fieldName);
      System.out.println("           current filter = "+filter);
      filter = QueryProcessor.filterWithBasicStringField(filter, fieldName, request, prettyPrint);
    }

    // GPS box
    filter = QueryProcessor.filterWithGpsBox(filter, request, prettyPrint);
    
    
    //Observations
    filter = QueryProcessor.filterObservations(filter, request, prettyPrint, "Survey");
    int numObs = QueryProcessor.getNumberOfObservationsInQuery(request);
    for (int i = 1;i<=numObs;i++) {
      jdoqlVariableDeclaration = QueryProcessor.updateJdoqlVariableDeclaration(jdoqlVariableDeclaration, "org.ecocean.Observation observation" + i);      
    }
    
    // make sure no trailing ampersands
    filter = QueryProcessor.removeTrailingAmpersands(filter);
    filter += jdoqlVariableDeclaration;
    filter += parameterDeclaration;
    System.out.println("SurveyQueryProcessor filter: "+filter);
    return filter;
  }

  public static SurveyQueryResult processQuery(Shepherd myShepherd, HttpServletRequest request, String order){

    Vector<Survey> rSurveys=new Vector<Survey>();
    Iterator<Survey> allSurveys;
    String filter="";
    StringBuffer prettyPrint=new StringBuffer("");
    Map<String,Object> paramMap = new HashMap<String, Object>();

    filter=queryStringBuilder(request, prettyPrint, paramMap);
    System.out.println("SurveyQueryResult: has filter "+filter);
    Query query=myShepherd.getPM().newQuery(filter);
    System.out.println("                       got query "+query);
    System.out.println("                       has paramMap "+paramMap);
    if(!order.equals("")){query.setOrdering(order);}
    System.out.println("                 still has query "+query);
    if(!filter.trim().equals("")){
      System.out.println(" about to call myShepherd.getAllSurveys on query "+query);
      allSurveys=myShepherd.getAllSurveys(query, paramMap);
    } else {
      System.out.println(" about to call myShepherd.getAllSurveysNoQuery() ");
      allSurveys=myShepherd.getAllSurveysNoQuery();
    }
    System.out.println("               *still* has query "+query);


    if(allSurveys!=null){
      while (allSurveys.hasNext()) {
        Survey temp_dat=allSurveys.next();
        rSurveys.add(temp_dat);
      }
    }
    query.closeAll();

    System.out.println("about to return SurveyQueryResult with filter "+filter+" and nOccs="+rSurveys.size());
    return (new SurveyQueryResult(rSurveys,filter,prettyPrint.toString()));
  }
  
}
