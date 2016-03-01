package org.ecocean;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import java.io.*;

import javax.jdo.Query;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.ecocean.Util.MeasurementDesc;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.security.Collaboration;

import org.joda.time.DateTime;

public class EncounterQueryProcessor {

  private static final String SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE = "SELECT FROM org.ecocean.Encounter WHERE ";

  public static String queryStringBuilder(HttpServletRequest request, StringBuffer prettyPrint, Map<String, Object> paramMap){
    String filter= SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE;
    String jdoqlVariableDeclaration = "";
    String parameterDeclaration = "";

    String context = ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Properties props = ShepherdProperties.getProperties("searchResults_shared.properties", langCode, context);
    Properties cciProps = ShepherdProperties.getProperties("commonCoreInternational.properties", langCode, context);
    
    Shepherd myShepherd=new Shepherd(context);

  //filter for location------------------------------------------
    if((request.getParameter("locationField")!=null)&&(!request.getParameter("locationField").equals(""))) {
      String locString=request.getParameter("locationField").toLowerCase().replaceAll("%20", " ").trim();
      if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){
        filter+="(verbatimLocality.toLowerCase().indexOf('"+locString+"') != -1)";
      }
      else{filter+=" && (verbatimLocality.toLowerCase().indexOf('"+locString+"') != -1)";}
      prettyPrint.append(MessageFormat.format(props.getProperty("filterLocation"), locString));
      prettyPrint.append("<br />");
    }
    //end location filter--------------------------------------------------------------------------------------

    //------------------------------------------------------------------
    //username filters-------------------------------------------------
    String[] usernames=request.getParameterValues("username");
    if((usernames!=null)&&(!usernames[0].equals("None"))){
          List<String> list = new ArrayList<>();
          int kwLength=usernames.length;
            String locIDFilter="(";
            for(int kwIter=0;kwIter<kwLength;kwIter++) {
              String kwParam=usernames[kwIter].replaceAll("%20", " ").trim();
              if(!kwParam.equals("")){
                if(locIDFilter.equals("(")){
                  locIDFilter+=" submitterID == \""+kwParam+"\"";
                }
                else{
                  locIDFilter+=" || submitterID == \""+kwParam+"\"";
                }
                list.add(kwParam);
              }
            }
            locIDFilter+=" )";
            if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+=locIDFilter;}
            else{filter+=(" && "+locIDFilter);}
            prettyPrint.append(MessageFormat.format(props.getProperty("filterUsername"), StringUtils.collateStrings(list, ", ")));
            prettyPrint.append("<br />");
    }
    //end username filters-----------------------------------------------



    //filter for resighted encounter------------------------------------------
    if(request.getParameter("resightOnly")!=null) {
      //String locString=request.getParameter("locationField").toLowerCase().replaceAll("%20", " ").trim();
      if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){
        filter+="(individualID != \"Unassigned\")";
      }
      else{filter+=" && (individualID != \"Unassigned\")";}
      prettyPrint.append(props.getProperty("filterResighted"));
      prettyPrint.append("<br />");
    }
    //end resighted filter--------------------------------------------------------------------------------------

    //filter for unassigned encounters------------------------------------------
    if(request.getParameter("unassigned")!=null) {
      if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){
        filter+="(individualID == \"Unassigned\")";
      }
      else{filter+=" && (individualID == \"Unassigned\")";}
      prettyPrint.append("Unidentified.<br />");
      prettyPrint.append(props.getProperty("filterUnassigned"));
      prettyPrint.append("<br />");
    }
    //end unassigned filter--------------------------------------------------------------------------------------



/**
  //filter for unidentifiable encounters------------------------------------------
    if(request.getParameter("unidentifiable")==null) {
      if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+="!unidentifiable";}
      else{filter+=" && !unidentifiable";}
      prettyPrint.append("Not identifiable.<br />");
    }
    //-----------------------------------------------------

    //---filter out approved
    if((request.getParameter("approved")==null)&&(request.getParameter("unapproved")!=null)) {
      if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+="!approved";}
      else{filter+=" && !approved";}
      prettyPrint.append("Not approved.<br />");
    }
    //----------------------------

    //---filter out unapproved
    if((request.getParameter("unapproved")==null)&&(request.getParameter("approved")!=null)) {

      if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+="approved";}
      else{filter+=" && approved";}
      prettyPrint.append("Not unapproved.<br />");
    }
    //----------------------------

    //---filter out unapproved and unapproved, leaving only unidentifiable
    if((request.getParameter("unapproved")==null)&&(request.getParameter("approved")==null)&&(request.getParameter("unidentifiable")!=null)) {

      if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+="unidentifiable";}
      else{filter+=" && unidentifiable";}
      prettyPrint.append("Not unapproved.<br />");
    }
    //----------------------------

*/

    //------------------------------------------------------------------
    //locationID filters-------------------------------------------------
    Map<String, String> mapI18n = Util.getIndexedValuesMap(cciProps, "locationID");
    String[] locCodes=request.getParameterValues("locationCodeField");
    if((locCodes!=null)&&(!locCodes[0].equals("None"))){
          List<String> list = new ArrayList<>();
          int kwLength=locCodes.length;
            String locIDFilter="(";
            for(int kwIter=0;kwIter<kwLength;kwIter++) {
              String kwParam=locCodes[kwIter].replaceAll("%20", " ").trim();
              if(!kwParam.equals("")){
                if(locIDFilter.equals("(")){
                  locIDFilter+=" locationID == \""+kwParam+"\"";
                }
                else{
                  locIDFilter+=" || locationID == \""+kwParam+"\"";
                }
                String kwI18n = mapI18n.get("locationID" + CommonConfiguration.getIndexNumberForValue("locationID", kwParam, context));
                list.add(kwI18n);
              }
            }
            locIDFilter+=" )";
            if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+=locIDFilter;}
            else{filter+=(" && "+locIDFilter);}
            prettyPrint.append(MessageFormat.format(props.getProperty("filterLocationCode_or"), StringUtils.collateStrings(list, ", ")));
            prettyPrint.append("<br />");
    }
    //end locationID filters-----------------------------------------------


    //------------------------------------------------------------------
    //state filters-------------------------------------------------
    mapI18n = Util.getIndexedValuesMap(cciProps, "encounterState");
    String[] states=request.getParameterValues("state");
    if((states!=null)&&(!states[0].equals("None"))){
          List<String> list = new ArrayList<>();
          int kwLength=states.length;
            String locIDFilter="(";
            for(int kwIter=0;kwIter<kwLength;kwIter++) {
              String kwParam=states[kwIter].replaceAll("%20", " ").trim();
              if(!kwParam.equals("")){
                if(locIDFilter.equals("(")){
                  locIDFilter+=" state == \""+kwParam+"\"";
                }
                else{
                  locIDFilter+=" || state == \""+kwParam+"\"";
                }
                String kwI18n = mapI18n.get("encounterState" + CommonConfiguration.getIndexNumberForValue("encounterState", kwParam, context));
                list.add(kwI18n);
              }
            }
            locIDFilter+=" )";
            if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+=locIDFilter;}
            else{filter+=(" && "+locIDFilter);}
            prettyPrint.append(MessageFormat.format(props.getProperty("filterEncounterState"), StringUtils.collateStrings(list, ", ")));
            prettyPrint.append("<br />");
    }
    //end state filters-----------------------------------------------


    //------------------------------------------------------------------
    //individualID filters-------------------------------------------------
    //supports multiple individualID parameters as well as comma-separated lists of individualIDs within them
    String[] individualID=request.getParameterValues("individualID");
    if((individualID!=null)&&(!individualID[0].equals(""))&&(!individualID[0].equals("None"))){
          List<String> list = new ArrayList<>();
          int kwLength=individualID.length;
            String locIDFilter="(";
            for(int kwIter=0;kwIter<kwLength;kwIter++) {
              String kwParamMaster=individualID[kwIter].replaceAll("%20", " ").trim();
              
              StringTokenizer str=new StringTokenizer(kwParamMaster,",");
              int numTokens=str.countTokens();
              for(int k=0;k<numTokens;k++){
                String kwParam=str.nextToken().trim();
                if(!kwParam.equals("")){
                  if(locIDFilter.equals("(")){
                    locIDFilter+=" individualID == \""+kwParam+"\"";
                  }
                  else{
                    locIDFilter+=" || individualID == \""+kwParam+"\"";
                  }
                  list.add(kwParam);
                }
              
              }
              
            }
            locIDFilter+=" )";
            if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+=locIDFilter;}
            else{filter+=(" && "+locIDFilter);}
            prettyPrint.append(MessageFormat.format(props.getProperty("filterIndividualId"), StringUtils.collateStrings(list, ", ")));
            prettyPrint.append("<br />");
    }
    //end individualID filters-----------------------------------------------
    
    
    
    

  //------------------------------------------------------------------
    //patterningCode filters-------------------------------------------------
    mapI18n = Util.getIndexedValuesMap(cciProps, "patterningCode");
    String[] patterningCodes=request.getParameterValues("patterningCodeField");
    if((patterningCodes!=null)&&(!patterningCodes[0].equals("None"))){
          List<String> list = new ArrayList<>();
          int kwLength=patterningCodes.length;
            String patterningCodeFilter="(";
            for(int kwIter=0;kwIter<kwLength;kwIter++) {

              String kwParam=patterningCodes[kwIter].replaceAll("%20", " ").trim();
              if(!kwParam.equals("")){
                if(patterningCodeFilter.equals("(")){
                  patterningCodeFilter+=" patterningCode == \""+kwParam+"\"";
                }
                else{

                  patterningCodeFilter+=" || patterningCode == \""+kwParam+"\"";
                }
                String kwI18n = mapI18n.get("patterningCode" + CommonConfiguration.getIndexNumberForValue("patterningCode", kwParam, context));
                list.add(kwI18n);
              }
            }
            patterningCodeFilter+=" )";


            if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+=patterningCodeFilter;}
            else{filter+=(" && "+patterningCodeFilter);}

            prettyPrint.append(MessageFormat.format(props.getProperty("filterPatterningCode"), StringUtils.collateStrings(list, ", ")));
            prettyPrint.append("<br />");
    }
    //end patterningCode filters-----------------------------------------------

    //------------------------------------------------------------------
    //behavior filters-------------------------------------------------
    String[] behaviors=request.getParameterValues("behaviorField");
    if((behaviors!=null)&&(!behaviors[0].equals("None"))){
          List<String> list = new ArrayList<>();
          int kwLength=behaviors.length;
            String locIDFilter="(";
            for(int kwIter=0;kwIter<kwLength;kwIter++) {
              String kwParam=behaviors[kwIter].replaceAll("%20", " ").trim();
              if(!kwParam.equals("")){
                if(locIDFilter.equals("(")){
                  locIDFilter+=" behavior == \""+kwParam+"\"";
                }
                else{
                  locIDFilter+=" || behavior == \""+kwParam+"\"";
                }
                list.add(kwParam);
              }
            }
            locIDFilter+=" )";
            if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+=locIDFilter;}
            else{filter+=(" && "+locIDFilter);}
            prettyPrint.append(MessageFormat.format(props.getProperty("filterBehavior"), StringUtils.collateStrings(list, ", ")));
            prettyPrint.append("<br />");
    }
    //end behavior filters-----------------------------------------------
    //------------------------------------------------------------------

    //Tag Filters--------------------------------------------------------

    StringBuilder metalTagFilter = new StringBuilder();
    Enumeration<String> parameterNames = request.getParameterNames();
    int metalTagsInQuery = 0;
    while (parameterNames.hasMoreElements()) {
      String parameterName = parameterNames.nextElement();
      final String metalTagPrefix = "metalTag(";
      if (parameterName.startsWith(metalTagPrefix)) {
        String metalTagLocation = parameterName.substring(metalTagPrefix.length(), parameterName.lastIndexOf(')'));
        String value = request.getParameter(parameterName);
        if (value != null && value.trim().length() > 0) {
          prettyPrint.append(MessageFormat.format(props.getProperty("filterMetalTag"), metalTagLocation, value));
          prettyPrint.append("<br/>");
          String metalTagVar = "metalTag" + metalTagsInQuery++;
          metalTagFilter.append("(metalTags.contains(" + metalTagVar + ") && ");
          metalTagFilter.append(metalTagVar + ".location == " + Util.quote(metalTagLocation));
          String jdoParam = "tagNumber" + metalTagsInQuery;
          metalTagFilter.append(" && " + metalTagVar + ".tagNumber == " + jdoParam + ")");
          paramMap.put(jdoParam, value);
          parameterDeclaration = updateParametersDeclaration(parameterDeclaration, "String " + jdoParam);
        }
      }
    }
    if (metalTagFilter.length() > 0) {
      if (!filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)) {
        filter += " && ";
      }
      filter += metalTagFilter.toString();
      for (int i = 0; i < metalTagsInQuery; i++) {
        updateJdoqlVariableDeclaration(jdoqlVariableDeclaration, "org.ecocean.tag.MetalTag metalTag" + i);
      }
    }

    // We don't do metal tags (above) in processTagFilters because of the dependency on jdoqlVariableDeclaration
    String tagFilters = processTagFilters(request, prettyPrint, props);
    if (tagFilters.length() > 0) {
      if (!filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)) {
        filter += " && ";
      }
      filter += tagFilters.toString();
    }
    //end tag filters----------------------------------------------------

    //lifeStage filters-------------------------------------------------
    mapI18n = Util.getIndexedValuesMap(cciProps, "lifeStage");
    String[] stages=request.getParameterValues("lifeStageField");
    if((stages!=null)&&(!stages[0].equals("None"))&&(!stages[0].equals(""))){
          List<String> list = new ArrayList<>();
          int kwLength=stages.length;
            String stageFilter="(";
            for(int kwIter=0;kwIter<kwLength;kwIter++) {
              String kwParam=stages[kwIter].replaceAll("%20", " ").trim();
              if(!kwParam.equals("")){
                if(stageFilter.equals("(")){
                  stageFilter+=" lifeStage == \""+kwParam+"\"";
                }
                else{
                  stageFilter+=" || lifeStage == \""+kwParam+"\"";
                }
                String kwI18n = mapI18n.get("lifeStage" + CommonConfiguration.getIndexNumberForValue("lifeStage", kwParam, context));
                list.add(kwI18n);
              }
            }
            stageFilter+=" ) ";
            if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+=stageFilter;}
            else{filter+=(" && "+stageFilter);}
            prettyPrint.append(MessageFormat.format(props.getProperty("filterLifeStage"), StringUtils.collateStrings(list, ", ")));
            prettyPrint.append("<br />");
    }
    //end lifeStage filters

    //country filters-------------------------------------------------
    mapI18n = Util.getIndexedValuesMap(cciProps, "country");
    String[] countries=request.getParameterValues("country");
    if((countries!=null)&&(!countries[0].equals("None"))&&(!countries[0].equals(""))){
          List<String> list = new ArrayList<>();
          int kwLength=countries.length;
            String stageFilter="(";
            for(int kwIter=0;kwIter<kwLength;kwIter++) {
              String kwParam=countries[kwIter].replaceAll("%20", " ").trim();
              if(!kwParam.equals("")){
                if(stageFilter.equals("(")){
                  stageFilter+=" country == \""+kwParam+"\"";
                }
                else{
                  stageFilter+=" || country == \""+kwParam+"\"";
                }
                String kwI18n = mapI18n.get("country" + CommonConfiguration.getIndexNumberForValue("country", kwParam, context));
                list.add(kwI18n);
              }
            }
            stageFilter+=" ) ";
            if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+=stageFilter;}
            else{filter+=(" && "+stageFilter);}
      prettyPrint.append(MessageFormat.format(props.getProperty("filterCountry"), StringUtils.collateStrings(list, ", ")));
      prettyPrint.append("<br />");
    }
    //end country filters


    // Measurement filters-----------------------------------------------
    List<MeasurementDesc> measurementDescs = Util.findMeasurementDescs(langCode, context);
    String measurementPrefix = "measurement";
    StringBuilder measurementFilter = new StringBuilder(); //"( collectedData.contains(measurement) && (");
    boolean atLeastOneMeasurement = false;
    int measurementsInQuery = 0;
    for (MeasurementDesc measurementDesc : measurementDescs) {
      String valueParamName= measurementPrefix + measurementDesc.getType() + "(value)";
      String value = request.getParameter(valueParamName);
      if (value != null) {
        value = value.trim();
        if ( value.length() > 0) {
          String operatorParamName = measurementPrefix + measurementDesc.getType() + "(operator)";
          String operatorParamValue = request.getParameter(operatorParamName);
          if (operatorParamValue == null) {
            operatorParamValue = "";
          }
          String operator = null;
          if ("gt".equals(operatorParamValue)) {
            operator = ">";
          }
          else if ( "lt".equals(operatorParamValue)) {
            operator = "<";
          }
          else if ("eq".equals(operatorParamValue)) {
            operator = "==";
          }
          else if ("gteq".equals(operatorParamValue)) {
            operator = ">=";
          }
          else if ("lteq".equals(operatorParamValue)) {
            operator = "<=";
          }
          if (operator != null) {
            prettyPrint.append(MessageFormat.format(props.getProperty("filterMeasurement"), measurementDesc.getUnitsLabel(), operator, value));
            prettyPrint.append("<br/>");
            if (atLeastOneMeasurement) {
              measurementFilter.append("&&");
            }
            String measurementVar = "measurement" + measurementsInQuery++;
            measurementFilter.append("measurements.contains(" + measurementVar + ") && ");
            measurementFilter.append( "("+measurementVar + ".value " + operator + " " + value+")");
            measurementFilter.append(" && (" + measurementVar + ".type == ");
            measurementFilter.append("\"" + measurementDesc.getType() + "\")");
            atLeastOneMeasurement = true;
          }
        }
      }
    }
    if (atLeastOneMeasurement) {
      if(jdoqlVariableDeclaration.length() > 0){
        jdoqlVariableDeclaration += ";";
      }
      else {
        jdoqlVariableDeclaration=" VARIABLES ";
      }
      for (int i = 0; i < measurementsInQuery; i++) {
        if (i > 0) {
          jdoqlVariableDeclaration += "; ";
        }
        jdoqlVariableDeclaration += " org.ecocean.Measurement measurement" + i;
      }
      if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){
        filter+= measurementFilter.toString();
      }
      else{
        filter+=(" && "+ measurementFilter.toString());
      }
    }
    // end measurement filters


    // BiologicalMeasurement filters-----------------------------------------------
    List<MeasurementDesc> bioMeasurementDescs = Util.findBiologicalMeasurementDescs(langCode, context);
    String bioMeasurementPrefix = "biomeasurement";
    StringBuilder bioMeasurementFilter = new StringBuilder();
    bioMeasurementFilter.append("tissueSamples.contains(dce322) ");
    boolean bioAtLeastOneMeasurement = false;
    int bioMeasurementsInQuery = 0;


    for (MeasurementDesc measurementDesc : bioMeasurementDescs) {
      String valueParamName= bioMeasurementPrefix + measurementDesc.getType() + "(value)";
      String value = request.getParameter(valueParamName);
      if (value != null) {
        value = value.trim();
        if ( value.length() > 0) {
          String operatorParamName = bioMeasurementPrefix + measurementDesc.getType() + "(operator)";
          String operatorParamValue = request.getParameter(operatorParamName);
          if (operatorParamValue == null) {
            operatorParamValue = "";
          }
          String operator = null;
          if ("gt".equals(operatorParamValue)) {
            operator = ">";
          }
          else if ( "lt".equals(operatorParamValue)) {
            operator = "<";
          }
          else if ("eq".equals(operatorParamValue)) {
            operator = "==";
          }
          else if ("gteq".equals(operatorParamValue)) {
            operator = ">=";
          }
          else if ("lteq".equals(operatorParamValue)) {
            operator = "<=";
          }
          if (operator != null) {
            prettyPrint.append(MessageFormat.format(props.getProperty("filterBioMeasurement"), measurementDesc.getLabel(), operator, value));
            prettyPrint.append("<br/>");
            if (bioAtLeastOneMeasurement) {
              bioMeasurementFilter.append("&&");
            }
            String measurementVar = "biomeasurement" + bioMeasurementsInQuery++;
            bioMeasurementFilter.append(" & dce322.analyses.contains(" + measurementVar + ")  ");
            bioMeasurementFilter.append( " && ( "+measurementVar + ".value " + operator + " " + value+" )");
            bioMeasurementFilter.append(" && ( " + measurementVar + ".measurementType == ");
            bioMeasurementFilter.append("\"" + measurementDesc.getType() + "\" )");
            bioAtLeastOneMeasurement = true;
          }
        }
      }
    }


    if (bioAtLeastOneMeasurement) {
      if(jdoqlVariableDeclaration.length() > 0){
        jdoqlVariableDeclaration += ";org.ecocean.genetics.TissueSample dce322;";
      }
      else {
        jdoqlVariableDeclaration=" VARIABLES org.ecocean.genetics.TissueSample dce322;";
      }


      for (int i = 0; i < bioMeasurementsInQuery; i++) {
        if (i > 0) {
          jdoqlVariableDeclaration += "; ";
        }
        jdoqlVariableDeclaration += "org.ecocean.genetics.BiologicalMeasurement biomeasurement" + i;
      }
      if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){
        filter+= bioMeasurementFilter.toString();
      }
      else{
        filter+=(" && "+ bioMeasurementFilter.toString());
      }
    }
    // end BiologicalMeasurement filters





    //------------------------------------------------------------------
    //verbatimEventDate filters-------------------------------------------------
    String[] verbatimEventDates=request.getParameterValues("verbatimEventDateField");
    if((verbatimEventDates!=null)&&(!verbatimEventDates[0].equals("None"))){
          List<String> list = new ArrayList<>();
          int kwLength=verbatimEventDates.length;
            String locIDFilter="(";
            for(int kwIter=0;kwIter<kwLength;kwIter++) {

              String kwParam=verbatimEventDates[kwIter].replaceAll("%20", " ").trim();
              if(!kwParam.equals("")){
                if(locIDFilter.equals("(")){
                  locIDFilter+=" verbatimEventDate == \""+kwParam+"\"";
                }
                else{
                  locIDFilter+=" || verbatimEventDate == \""+kwParam+"\"";
                }
                list.add(kwParam);
              }
            }
            locIDFilter+=" )";
            if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+=locIDFilter;}
            else{filter+=(" && "+locIDFilter);}
            prettyPrint.append(MessageFormat.format(props.getProperty("filterVerbatimEventDate"), StringUtils.collateStrings(list, ", ")));
            prettyPrint.append("<br />");
    }
    //end verbatimEventDate filters-----------------------------------------------



    //------------------------------------------------------------------
    //hasTissueSample filters-------------------------------------------------
    if(request.getParameter("hasTissueSample")!=null){
          prettyPrint.append(props.getProperty("filterHasTissueSample"));
            if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+="tissueSamples.contains(dce)";}
            else if (filter.indexOf("tissueSamples.contains(dce)")==-1){filter+=(" && tissueSamples.contains(dce) ");}
            prettyPrint.append("<br />");
            if(jdoqlVariableDeclaration.equals("")){jdoqlVariableDeclaration=" VARIABLES org.ecocean.genetics.TissueSample dce";}
            else if(!jdoqlVariableDeclaration.contains("org.ecocean.genetics.TissueSample dce")){jdoqlVariableDeclaration+=";org.ecocean.genetics.TissueSample dce";}
    }
    //end hasTissueSample filters-----------------------------------------------

    //------------------------------------------------------------------
    //TissueSample sampleID filters-------------------------------------------------
    if((request.getParameter("tissueSampleID")!=null)&&(!request.getParameter("tissueSampleID").trim().equals(""))){
          prettyPrint.append(MessageFormat.format(props.getProperty("filterTissueSample"), request.getParameter("tissueSampleID")));
            if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+="tissueSamples.contains(dce123) && (dce123.sampleID.toLowerCase().indexOf('"+request.getParameter("tissueSampleID").trim().toLowerCase()+"') != -1)";}
            else if (filter.indexOf("tissueSamples.contains(dce)")==-1){filter+=(" && tissueSamples.contains(dce123) && (dce123.sampleID.toLowerCase().indexOf('"+request.getParameter("tissueSampleID").trim().toLowerCase()+"') != -1) ");}
            prettyPrint.append("<br />");
            if(jdoqlVariableDeclaration.equals("")){jdoqlVariableDeclaration=" VARIABLES org.ecocean.genetics.TissueSample dce123";}
            else if(!jdoqlVariableDeclaration.contains("org.ecocean.genetics.TissueSample dce123")){jdoqlVariableDeclaration+=";org.ecocean.genetics.TissueSample dce123";}
    }
    //end hasTissueSample filters-----------------------------------------------




    //------------------------------------------------------------------
    //hasPhoto filters-------------------------------------------------
    if(request.getParameter("hasPhoto")!=null){
          prettyPrint.append(props.getProperty("filterHasPhoto"));

            if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+="images.contains(dce15)";}
            else if (filter.indexOf("images.contains(dce15)")==-1){filter+=(" && images.contains(dce15) ");}

            prettyPrint.append("<br />");
            if(jdoqlVariableDeclaration.equals("")){jdoqlVariableDeclaration=" VARIABLES org.ecocean.SinglePhotoVideo dce15";}
            else if(!jdoqlVariableDeclaration.contains("org.ecocean.SinglePhotoVideo dce15")){jdoqlVariableDeclaration+=";org.ecocean.SinglePhotoVideo dce15";}

    }
    //end hasPhoto filters-----------------------------------------------
    
    //filter for encounters of MarkedIndividuals that have been resighted------------------------------------------
    if((request.getParameter("resightOnly")!=null)&&(request.getParameter("numResights")!=null)) {
      int numResights=1;

      try{
        numResights=(new Integer(request.getParameter("numResights"))).intValue();
        prettyPrint.append(MessageFormat.format(props.getProperty("filterNumberResights_gte"), numResights));
        prettyPrint.append("<br />");
        }
      catch(NumberFormatException nfe) {nfe.printStackTrace();}
      String localFilter="markedindy.encounters.contains(this) && markedindy.encounters.size() >= "+numResights+" ";
      if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+=localFilter;}
      else {filter+=(" && "+localFilter);}
      if(jdoqlVariableDeclaration.equals("")){jdoqlVariableDeclaration=" VARIABLES org.ecocean.MarkedIndividual markedindy";}
      else {jdoqlVariableDeclaration+=";org.ecocean.MarkedIndividual markedindy";}

      
      
      
    }
  //end if resightOnly--------------------------------------------------------------------------------------



    //------------------------------------------------------------------
    //keyword filters-------------------------------------------------
    myShepherd.beginDBTransaction();
    String[] keywords=request.getParameterValues("keyword");
    String photoKeywordOperator = "&&";
    if((request.getParameter("photoKeywordOperator")!=null)&&(request.getParameter("photoKeywordOperator").equals("_OR_"))){photoKeywordOperator = "||";}

    if((keywords!=null)&&(!keywords[0].equals("None"))){

      if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+="(";}
      else{filter+=" && (";}

      String ppKey = "filterKeyword_and";
      if((request.getParameter("photoKeywordOperator")!=null)&&(request.getParameter("photoKeywordOperator").equals("_OR_"))){
        ppKey = "filterKeyword_or";
      }
          List<String> list = new ArrayList<>();
          int kwLength=keywords.length;

            for(int kwIter=0;kwIter<kwLength;kwIter++) {
              String locIDFilter="(";
              String kwParam=keywords[kwIter].replaceAll("%20", " ").trim();
              if(!kwParam.equals("")){
                if(locIDFilter.equals("(")){
                  locIDFilter+=" word"+kwIter+".indexname == \""+kwParam+"\" ";
                }
                else{
                  locIDFilter+=" "+photoKeywordOperator+" word"+kwIter+".indexname == \""+kwParam+"\" ";
                }
                Keyword kw=myShepherd.getKeyword(kwParam.trim());
                list.add(kw.getReadableName());
              }
              locIDFilter+=" )";



                if(filter.indexOf("images.contains(photo"+kwIter+")")==-1){
                  if(kwIter>0){filter+=" "+photoKeywordOperator+" ";}
                  filter+=" ( images.contains(photo"+kwIter+")";
                }

                if(filter.indexOf("photo"+kwIter+".keywords.contains(word"+kwIter+")")==-1){filter+=" && photo"+kwIter+".keywords.contains(word"+kwIter+")";}
                filter+=(" && "+locIDFilter+")");
             // }



                if((kwIter==0)&&(jdoqlVariableDeclaration.equals(""))){jdoqlVariableDeclaration=" VARIABLES ";}
                if(kwIter>0){jdoqlVariableDeclaration+=";";}
              if(!jdoqlVariableDeclaration.contains("org.ecocean.SinglePhotoVideo photo"+kwIter)){jdoqlVariableDeclaration+="org.ecocean.SinglePhotoVideo photo"+kwIter;}
              if(!jdoqlVariableDeclaration.contains("org.ecocean.Keyword word"+kwIter)){jdoqlVariableDeclaration+=";org.ecocean.Keyword word"+kwIter;}


            }
            filter+=" ) ";

            prettyPrint.append(MessageFormat.format(props.getProperty(ppKey), StringUtils.collateStrings(list, "\"", "\"", ", ")));
            prettyPrint.append("<br />");
      }
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();

    //end photo keyword filters-----------------------------------------------



    //------------------------------------------------------------------
    //ms markers filters-------------------------------------------------
      myShepherd.beginDBTransaction();
      ArrayList<String> markers=myShepherd.getAllLoci();
        int numMarkers=markers.size();
        String theseMarkers="";
        boolean hasMarkers=false;
        for(int h=0;h<numMarkers;h++){

            String marker=markers.get(h);
            if(request.getParameter(marker)!=null){
              hasMarkers=true;
              String locIDFilter="(";
              locIDFilter+=" "+marker.replaceAll("-", "")+".name == \""+marker+"\" ";
              locIDFilter+=" )";


              int alleleNum=0;
              boolean hasMoreAlleles=true;
              while(hasMoreAlleles){

                if(request.getParameter((marker+"_alleleValue"+alleleNum))!=null){
                  try{
                    Integer thisInt=new Integer(request.getParameter((marker+"_alleleValue"+alleleNum)));
                    Integer relaxValue=new Integer(request.getParameter("alleleRelaxValue"));
                    Integer upperValue=thisInt+relaxValue;
                    Integer lowerValue=thisInt-relaxValue;
                    locIDFilter+=(" && ("+marker.replaceAll("-", "")+".allele"+alleleNum+" >= "+lowerValue+")"+" && ("+marker.replaceAll("-", "")+".allele"+alleleNum+" <= "+upperValue+")");

                  }
                  catch(Exception e){
                    hasMoreAlleles=false;
                  }
                }
                else{
                  hasMoreAlleles=false;
                }
                alleleNum++;
              }


              theseMarkers+=(marker+" ");


              if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+="tissueSamples.contains(dce) && dce.analyses.contains(msanalysis) && msanalysis.loci.contains("+marker.replaceAll("-", "")+") && "+locIDFilter;}
              else{
                if(filter.indexOf("tissueSamples.contains(dce)")==-1){filter+=" && tissueSamples.contains(dce)";}
                if(filter.indexOf("dce.analyses.contains(msanalysis)")==-1){filter+=" && dce.analyses.contains(msanalysis)";}
                if(filter.indexOf("msanalysis.loci.contains("+marker.replaceAll("-", "")+")")==-1){filter+=" && msanalysis.loci.contains("+marker.replaceAll("-", "")+")";}

                filter+=(" && "+locIDFilter);
              }


              if(jdoqlVariableDeclaration.equals("")){jdoqlVariableDeclaration=" VARIABLES org.ecocean.genetics.TissueSample dce;org.ecocean.genetics.MicrosatelliteMarkersAnalysis msanalysis;org.ecocean.genetics.Locus "+marker.replaceAll("-", "");}
              else{
                if(!jdoqlVariableDeclaration.contains("org.ecocean.genetics.TissueSample dce")){jdoqlVariableDeclaration+=";org.ecocean.genetics.TissueSample dce";}
                if(!jdoqlVariableDeclaration.contains("org.ecocean.genetics.MicrosatelliteMarkersAnalysis msanalysis")){jdoqlVariableDeclaration+=";org.ecocean.genetics.MicrosatelliteMarkersAnalysis msanalysis";}
                if(!jdoqlVariableDeclaration.contains("org.ecocean.genetics.Locus "+marker.replaceAll("-", ""))){jdoqlVariableDeclaration+=";org.ecocean.genetics.Locus "+marker.replaceAll("-", "");}

              }
            }
        }
        if(hasMarkers){
          prettyPrint.append(MessageFormat.format(props.getProperty("filterMarker"), theseMarkers));
          prettyPrint.append("<br />");
        }
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
    //end ms markers filters-----------------------------------------------



    //filter for alternate ID------------------------------------------
    if((request.getParameter("alternateIDField")!=null)&&(!request.getParameter("alternateIDField").equals(""))) {
      String altID=request.getParameter("alternateIDField").replaceAll("%20", " ").trim().toLowerCase();
      if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+="otherCatalogNumbers.toLowerCase().indexOf('"+altID+"') != -1";}
      else{filter+=" && otherCatalogNumbers.toLowerCase().indexOf('"+altID+"') != -1";}

      prettyPrint.append(MessageFormat.format(props.getProperty("filterAlternateId"), altID));
      prettyPrint.append("<br />");
    }

    //------------------------------------------------------------------
    //haplotype filters-------------------------------------------------
    String[] haplotypes=request.getParameterValues("haplotypeField");
    if((haplotypes!=null)&&(!haplotypes[0].equals("None"))){
          List<String> list = new ArrayList<>();
          int kwLength=haplotypes.length;
            String locIDFilter="(";
            for(int kwIter=0;kwIter<kwLength;kwIter++) {

              String kwParam=haplotypes[kwIter].replaceAll("%20", " ").trim();
              if(!kwParam.equals("")){
                if(locIDFilter.equals("(")){
                  locIDFilter+=" analysis.haplotype == \""+kwParam+"\" ";
                }
                else{
                  locIDFilter+=" || analysis.haplotype == \""+kwParam+"\" ";
                }
                list.add(kwParam);
              }
            }
            locIDFilter+=" )";
            if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+="tissueSamples.contains(dce) && dce.analyses.contains(analysis) && "+locIDFilter;}
            else{
              if(filter.indexOf("tissueSamples.contains(dce)")==-1){filter+=" && tissueSamples.contains(dce)";}

              if(filter.indexOf("dce.analyses.contains(analysis)")==-1){filter+=" && dce.analyses.contains(analysis)";}
              filter+=(" && "+locIDFilter);
            }

            prettyPrint.append(MessageFormat.format(props.getProperty("filterHaplotype"), StringUtils.collateStrings(list, ", ")));
            prettyPrint.append("<br />");
            if(jdoqlVariableDeclaration.equals("")){jdoqlVariableDeclaration=" VARIABLES org.ecocean.genetics.TissueSample dce;org.ecocean.genetics.MitochondrialDNAAnalysis analysis";}
            else{
              if(!jdoqlVariableDeclaration.contains("org.ecocean.genetics.TissueSample dce")){jdoqlVariableDeclaration+=";org.ecocean.genetics.TissueSample dce";}
              if(!jdoqlVariableDeclaration.contains("org.ecocean.genetics.MitochondrialDNAAnalysis analysis")){jdoqlVariableDeclaration+=";org.ecocean.genetics.MitochondrialDNAAnalysis analysis";}

            }

      }

    //end haplotype filters-----------------------------------------------




    //------------------------------------------------------------------
    //genetic sex filters-------------------------------------------------
    String[] genSexes=request.getParameterValues("geneticSexField");
    if((genSexes!=null)&&(!genSexes[0].equals("None"))){
          List<String> list = new ArrayList<>();
          int kwLength=genSexes.length;
            String locIDFilter="(";
            for(int kwIter=0;kwIter<kwLength;kwIter++) {

              String kwParam=genSexes[kwIter].replaceAll("%20", " ").trim();
              if(!kwParam.equals("")){
                if(locIDFilter.equals("(")){
                  locIDFilter+=" sexanalysis.sex == \""+kwParam+"\" ";
                }
                else{
                  locIDFilter+=" || sexanalysis.sex == \""+kwParam+"\" ";
                }
                list.add(kwParam);
              }
            }
            locIDFilter+=" )";
            if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+="tissueSamples.contains(dce9) && dce9.analyses.contains(sexanalysis) && "+locIDFilter;}
            else{
              if(filter.indexOf("tissueSamples.contains(dce9)")==-1){filter+=" && tissueSamples.contains(dce9)";}

              if(filter.indexOf("dce9.analyses.contains(sexanalysis)")==-1){filter+=" && dce9.analyses.contains(sexanalysis)";}
              filter+=(" && "+locIDFilter);
            }

            prettyPrint.append(MessageFormat.format(props.getProperty("filterGeneticSex"), StringUtils.collateStrings(list, ", ")));
            prettyPrint.append("<br />");
            if(jdoqlVariableDeclaration.equals("")){jdoqlVariableDeclaration=" VARIABLES org.ecocean.genetics.TissueSample dce9;org.ecocean.genetics.SexAnalysis sexanalysis";}
            else{
              if(!jdoqlVariableDeclaration.contains("org.ecocean.genetics.TissueSample dce9")){jdoqlVariableDeclaration+=";org.ecocean.genetics.TissueSample dce9";}
              if(!jdoqlVariableDeclaration.contains("org.ecocean.genetics.SexAnalysis sexanalysis")){jdoqlVariableDeclaration+=";org.ecocean.genetics.SexAnalysis sexanalysis";}

            }

      }

    //end genetic sex filters-----------------------------------------------
    
    
  	//start photo filename filtering
	    if((request.getParameter("filenameField")!=null)&&(!request.getParameter("filenameField").equals(""))) {

	      //clean the input string to create its equivalent as if it had been submitted through the web form
	      String nameString=ServletUtilities.cleanFileName(ServletUtilities.preventCrossSiteScriptingAttacks(request.getParameter("filenameField").trim()));

	      String locIDFilter="( photo.filename == \""+nameString+"\" )";
	      
	      
	      if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+="images.contains(photo) && "+locIDFilter;}
          else{
            filter+=" && images.contains(photo) && "+locIDFilter;
          }

          prettyPrint.append(MessageFormat.format(props.getProperty("filterPhotoFilename"), nameString));
          prettyPrint.append("<br />");
          if(jdoqlVariableDeclaration.equals("")){jdoqlVariableDeclaration=" VARIABLES org.ecocean.SinglePhotoVideo photo;";}
          else{
            jdoqlVariableDeclaration+=";org.ecocean.SinglePhotoVideo photo";
            
          }
  }

//end photo filename filtering



    //filter for genus------------------------------------------
	    if((request.getParameter("genusField")!=null)&&(!request.getParameter("genusField").equals(""))) {
	      String genusSpecies=request.getParameter("genusField").replaceAll("%20", " ").trim();
	      String genus="";
		  String specificEpithet = "";

		  //now we have to break apart genus species
		  StringTokenizer tokenizer=new StringTokenizer(genusSpecies," ");
		  		if(tokenizer.countTokens()==2){

					genus=tokenizer.nextToken();
					specificEpithet=tokenizer.nextToken();

					if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+="genus == '"+genus+"' ";}
					else{filter+=" && genus == '"+genus+"' ";}

					filter+=" && specificEpithet == '"+specificEpithet+"' ";

          prettyPrint.append(MessageFormat.format(props.getProperty("filterGenusSpecies"), genusSpecies));
          prettyPrint.append("<br />");

		        }

    }



    //filter for identificationRemarks------------------------------------------
    if((request.getParameter("identificationRemarksField")!=null)&&(!request.getParameter("identificationRemarksField").equals(""))) {
      String idRemarks=request.getParameter("identificationRemarksField").trim();
      if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+="identificationRemarks.startsWith('"+idRemarks+"')";}
      else{filter+=" && identificationRemarks.startsWith('"+idRemarks+"')";}

      prettyPrint.append(MessageFormat.format(props.getProperty("filterIdRemarks"), idRemarks));
      prettyPrint.append("<br />");

    }
    //end identification remarks filter


    //filter gpsOnly - return only Encounters with a defined location. This is mostly used for mapping JSP pages
    if(request.getAttribute("gpsOnly")!=null){

      if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+="decimalLatitude >= -90 && decimalLatitude <= 90 && decimalLongitude <= 180 && decimalLongitude >= -180";}
      else{filter+=" && decimalLatitude >= -90 && decimalLatitude <= 90 && decimalLongitude <= 180 && decimalLongitude >= -180";}
      prettyPrint.append(props.getProperty("filterHasGps"));
      prettyPrint.append("<br />");

    }
    //end filter gpsOnly

    /**
    //filter for behavior------------------------------------------
    if((request.getParameter("behaviorField")!=null)&&(!request.getParameter("behaviorField").equals(""))) {
      String behString=request.getParameter("behaviorField").toLowerCase().replaceAll("%20", " ").trim();
      if(filter.equals("")){filter="behavior.toLowerCase().indexOf('"+behString+"') != -1";}
      else{filter+=" && behavior.toLowerCase().indexOf('"+behString+"') != -1";}
      prettyPrint.append("behaviorField contains \""+behString+"\".<br />");
    }
    //end behavior filter--------------------------------------------------------------------------------------
     */

    //filter by alive/dead status------------------------------------------
    if((request.getParameter("alive")!=null)||(request.getParameter("dead")!=null)){
      if(request.getParameter("alive")==null) {
        if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+="!livingStatus.startsWith('alive')";}
        else{filter+=" && !livingStatus.startsWith('alive')";}
        prettyPrint.append(props.getProperty("filterStatus_notAlive"));
        prettyPrint.append("<br />");
      }
      if(request.getParameter("dead")==null) {
        if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+="!livingStatus.startsWith('dead')";}
        else{filter+=" && !livingStatus.startsWith('dead')";}
        prettyPrint.append(props.getProperty("filterStatus_notDead"));
        prettyPrint.append("<br />");
      }
    }
    //filter by alive/dead status--------------------------------------------------------------------------------------

    //submitter or photographer name filter------------------------------------------
    if((request.getParameter("nameField")!=null)&&(!request.getParameter("nameField").equals(""))) {
      String nameString=request.getParameter("nameField").replaceAll("%20"," ").toLowerCase().trim();
      String filterString="((recordedBy.toLowerCase().indexOf('"+nameString+"') != -1)||(submitterEmail.toLowerCase().indexOf('"+nameString+"') != -1)||(photographerName.toLowerCase().indexOf('"+nameString+"') != -1)||(photographerEmail.toLowerCase().indexOf('"+nameString+"') != -1))";
      if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+=filterString;}
      else{filter+=(" && "+filterString);}
      prettyPrint.append(MessageFormat.format(props.getProperty("filterName"), nameString));
      prettyPrint.append("<br />");
    }
    //end name and email filter--------------------------------------------------------------------------------------

    
    //additional comments filter------------------------------------------
    if((request.getParameter("additionalCommentsField")!=null)&&(!request.getParameter("additionalCommentsField").equals(""))) {
      String nameString=request.getParameter("additionalCommentsField").replaceAll("%20"," ").toLowerCase().trim();
      String filterString="(occurrenceRemarks.toLowerCase().indexOf('"+nameString+"') != -1)";
      if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+=filterString;}
      else{filter+=(" && "+filterString);}
      prettyPrint.append("Remarks contains: \""+nameString+"\"<br />");
      prettyPrint.append(MessageFormat.format(props.getProperty("filterAdditionalComments"), nameString));
      prettyPrint.append("<br />");
    }
    //end additional comments filter--------------------------------------------------------------------------------------




/*
This code is no longer necessary with Charles Overbeck's new multi-measurement feature.

    //filter for length------------------------------------------
    if((request.getParameter("selectLength")!=null)&&(request.getParameter("lengthField")!=null)&&(!request.getParameter("lengthField").equals("skip"))&&(!request.getParameter("selectLength").equals(""))) {

      String size=request.getParameter("lengthField").trim();

      if(request.getParameter("selectLength").equals("gt")) {
        String filterString="size > "+size;
        if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+=filterString;}
        else{filter+=(" && "+filterString);}
        prettyPrint.append("selectLength is > "+size+".<br />");
      }
      else if(request.getParameter("selectLength").equals("lt")) {
        String filterString="size < "+size;
        if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+=filterString;}
        else{filter+=(" && "+filterString);}
        prettyPrint.append("selectLength is < "+size+".<br />");
      }
      else if(request.getParameter("selectLength").equals("eq")) {
        String filterString="size == "+size;
        if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){filter+=filterString;}
        else{filter+=(" && "+filterString);}
        prettyPrint.append("selectLength is = "+size+".<br />");
      }
    }
*/

    String pattern = CommonConfiguration.getProperty("releaseDateFormat",context);
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);

    //start date filter----------------------------
    if((request.getParameter("day1")!=null)&&(request.getParameter("month1")!=null)&&(request.getParameter("year1")!=null)&&(request.getParameter("day2")!=null)&&(request.getParameter("month2")!=null)&&(request.getParameter("year2")!=null)) {
      try{

    //get our date values
    int day1=(new Integer(request.getParameter("day1"))).intValue();
    int day2=(new Integer(request.getParameter("day2"))).intValue();
    int month1=(new Integer(request.getParameter("month1"))).intValue();
    int month2=(new Integer(request.getParameter("month2"))).intValue();
    int year1=(new Integer(request.getParameter("year1"))).intValue();
    int year2=(new Integer(request.getParameter("year2"))).intValue();

    //order our values
    int minYear=year1;
    int minMonth=month1;
    int minDay=day1;
    int maxYear=year2;
    int maxMonth=month2;
    int maxDay=day2;
    if(year1>year2) {
      minDay=day2;
      minMonth=month2;
      minYear=year2;
      maxDay=day1;
      maxMonth=month1;
      maxYear=year1;
    }
    else if(year1==year2) {
      if(month1>month2) {
        minDay=day2;
        minMonth=month2;
        minYear=year2;
        maxDay=day1;
        maxMonth=month1;
        maxYear=year1;
      }
      else if(month1==month2) {
        if(day1>day2) {
          minDay=day2;
          minMonth=month2;
          minYear=year2;
          maxDay=day1;
          maxMonth=month1;
          maxYear=year1;
        }
      }
    }

    //GregorianCalendar gcMin=new GregorianCalendar(minYear, (minMonth-1), minDay, 0, 0);
    //GregorianCalendar gcMax=new GregorianCalendar(maxYear, (maxMonth-1), maxDay, 23, 59);

    //let's do some month and day checking to avoid exceptions
    org.joda.time.DateTime testMonth1=new org.joda.time.DateTime(minYear,minMonth,1,0,0);
    if(testMonth1.dayOfMonth().getMaximumValue()<minDay) minDay=testMonth1.dayOfMonth().getMaximumValue();    
    org.joda.time.DateTime testMonth2=new org.joda.time.DateTime(maxYear,maxMonth,1,0,0);
    if(testMonth2.dayOfMonth().getMaximumValue()<maxDay) maxDay=testMonth2.dayOfMonth().getMaximumValue();
    
    
    org.joda.time.DateTime gcMin =new org.joda.time.DateTime(minYear, (minMonth), minDay, 0, 0);
    org.joda.time.DateTime gcMax =new org.joda.time.DateTime(maxYear, (maxMonth), maxDay, 23, 59);

    prettyPrint.append(MessageFormat.format(props.getProperty("filterDates"), simpleDateFormat.format(gcMin.toDate()), simpleDateFormat.format(gcMax.toDate())));
    prettyPrint.append("<br />");

    if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){
      filter+="((dateInMilliseconds >= "+gcMin.getMillis()+") && (dateInMilliseconds <= "+gcMax.getMillis()+"))";
    }
    else{filter+=" && ((dateInMilliseconds >= "+gcMin.getMillis()+") && (dateInMilliseconds <= "+gcMax.getMillis()+"))";
    }
      
    //end date filter------------------------------------------
    
    
    //start date added filter----------------------------
    if((request.getParameter("addedday1")!=null)&&(request.getParameter("addedmonth1")!=null)&&(request.getParameter("addedyear1")!=null)&&(request.getParameter("addedday2")!=null)&&(request.getParameter("addedmonth2")!=null)&&(request.getParameter("addedyear2")!=null)) {
      try{

        //get our date values
        int addedday1=(new Integer(request.getParameter("addedday1"))).intValue();
        int addedday2=(new Integer(request.getParameter("addedday2"))).intValue();
        int addedmonth1=(new Integer(request.getParameter("addedmonth1"))).intValue();
        int addedmonth2=(new Integer(request.getParameter("addedmonth2"))).intValue();
        int addedyear1=(new Integer(request.getParameter("addedyear1"))).intValue();
        int addedyear2=(new Integer(request.getParameter("addedyear2"))).intValue();

        //order our values
        int addedminYear=addedyear1;
        int addedminMonth=addedmonth1;
        int addedminDay=addedday1;
        int addedmaxYear=addedyear2;
        int addedmaxMonth=addedmonth2;
        int addedmaxDay=addedday2;
        if(addedyear1>addedyear2) {
          addedminDay=addedday2;
          addedminMonth=addedmonth2;
          addedminYear=addedyear2;
          addedmaxDay=addedday1;
          addedmaxMonth=addedmonth1;
          addedmaxYear=addedyear1;
        }
        else if(addedyear1==addedyear2) {
          if(addedmonth1>addedmonth2) {
              addedminDay=addedday2;
              addedminMonth=addedmonth2;
              addedminYear=addedyear2;
              addedmaxDay=addedday1;
              addedmaxMonth=addedmonth1;
              addedmaxYear=addedyear1;
          }
          else if(addedmonth1==addedmonth2) {
            if(addedday1>addedday2) {
              addedminDay=addedday2;
              addedminMonth=addedmonth2;
              addedminYear=addedyear2;
              addedmaxDay=addedday1;
              addedmaxMonth=addedmonth1;
              addedmaxYear=addedyear1;
            }
          }
        }

        //GregorianCalendar gcMin=new GregorianCalendar(minYear, (minMonth-1), minDay, 0, 0);
        //GregorianCalendar gcMax=new GregorianCalendar(maxYear, (maxMonth-1), maxDay, 23, 59);

        //let's do some month and day checking to avoid exceptions
        org.joda.time.DateTime addedtestMonth1=new org.joda.time.DateTime(addedminYear,addedminMonth,1,0,0);
        if(addedtestMonth1.dayOfMonth().getMaximumValue()<addedminDay) addedminDay=addedtestMonth1.dayOfMonth().getMaximumValue();    
        org.joda.time.DateTime addedtestMonth2=new org.joda.time.DateTime(addedmaxYear,addedmaxMonth,1,0,0);
        if(addedtestMonth2.dayOfMonth().getMaximumValue()<addedmaxDay) addedmaxDay=addedtestMonth2.dayOfMonth().getMaximumValue();
    
    
        org.joda.time.DateTime addedgcMin =new org.joda.time.DateTime(addedminYear, (addedminMonth), addedminDay, 0, 0);
        org.joda.time.DateTime addedgcMax =new org.joda.time.DateTime(addedmaxYear, (addedmaxMonth), addedmaxDay, 23, 59);

        prettyPrint.append(MessageFormat.format(props.getProperty("filterEncounterCreationDates"), simpleDateFormat.format(addedgcMin.toDate()), simpleDateFormat.format(addedgcMax.toDate())));
        prettyPrint.append("<br />");
        
        if(filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)){
          filter+="((dwcDateAddedLong >= "+addedgcMin.getMillis()+") && (dwcDateAddedLong <= "+addedgcMax.getMillis()+"))";
        }
        else{
          filter+=" && ((dwcDateAddedLong >= "+addedgcMin.getMillis()+") && (dwcDateAddedLong <= "+addedgcMax.getMillis()+"))";
    
        }
        
    //end date added filter------------------------------------------
      } catch(NumberFormatException nfe) {
        //do nothing, just skip on
        nfe.printStackTrace();
          }
        }
    
    

    //filter for sex------------------------------------------

    if(request.getParameter("male")==null) {
      filter+=" && !sex.startsWith('male')";
      prettyPrint.append(props.getProperty("filterSex_notMale")).append("<br />");
    }
    if(request.getParameter("female")==null) {
      filter+=" && !sex.startsWith('female')";
      prettyPrint.append(props.getProperty("filterSex_notFemale")).append("<br />");
    }
    if(request.getParameter("unknown")==null) {
      filter+=" && !sex.startsWith('unknown') && sex != null";
      prettyPrint.append(props.getProperty("filterSex_notUnknown")).append("<br />");
    }

    //filter by sex--------------------------------------------------------------------------------------

      } catch(NumberFormatException nfe) {
        //do nothing, just skip on
        nfe.printStackTrace();
          }
        }
 

    String releaseDateFromStr = request.getParameter("releaseDateFrom");
    String releaseDateToStr = request.getParameter("releaseDateTo");
    if (releaseDateFromStr != null && releaseDateFromStr.trim().length() > 0) {
      try {
        Date releaseDateFrom = simpleDateFormat.parse(releaseDateFromStr);
        if (!filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)) {
          filter += " && ";
        }
        filter += "(releaseDate >= releaseDateFrom)";
        parameterDeclaration = updateParametersDeclaration(parameterDeclaration, "java.util.Date releaseDateFrom");
        paramMap.put("releaseDateFrom", releaseDateFrom);
        prettyPrint.append(MessageFormat.format(props.getProperty("filterReleaseDate_gte"), simpleDateFormat.format(releaseDateFrom)));
        prettyPrint.append("<br />");
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    if (releaseDateToStr != null && releaseDateToStr.trim().length() > 0) {
      try {
        Date releaseDateTo = simpleDateFormat.parse(releaseDateToStr);
        if (!filter.equals(SELECT_FROM_ORG_ECOCEAN_ENCOUNTER_WHERE)) {
          filter += " && ";
        }
        filter += "(releaseDate <= releaseDateTo)";
        parameterDeclaration = updateParametersDeclaration(parameterDeclaration, "java.util.Date releaseDateTo");
        paramMap.put("releaseDateTo", releaseDateTo);
        prettyPrint.append(MessageFormat.format(props.getProperty("filterReleaseDate_lte"), simpleDateFormat.format(releaseDateTo)));
        prettyPrint.append("<br />");
      } catch (Exception e) {
        e.printStackTrace();
      }
    }




    //end date filter ----------------------------------------

    //------------------------------------------------------------------
    //GPS filters-------------------------------------------------

    if((request.getParameter("ne_lat")!=null)&&(!request.getParameter("ne_lat").equals(""))) {
      if((request.getParameter("ne_long")!=null)&&(!request.getParameter("ne_long").equals(""))) {
        if((request.getParameter("sw_lat")!=null)&&(!request.getParameter("sw_lat").equals(""))) {
          if((request.getParameter("sw_long")!=null)&&(!request.getParameter("sw_long").equals(""))) {




                try{

                  String thisLocalFilter="(";

                  double ne_lat=(new Double(request.getParameter("ne_lat"))).doubleValue();
                  double ne_long = (new Double(request.getParameter("ne_long"))).doubleValue();
                  double sw_lat = (new Double(request.getParameter("sw_lat"))).doubleValue();
                  double sw_long=(new Double(request.getParameter("sw_long"))).doubleValue();

                  if((sw_long>0)&&(ne_long<0)){
                    //if(!((encLat<=ne_lat)&&(encLat>=sw_lat)&&((encLong<=ne_long)||(encLong>=sw_long)))){

                      //process lats
                      thisLocalFilter+="(decimalLatitude <= "+request.getParameter("ne_lat")+") && (decimalLatitude >= "+request.getParameter("sw_lat")+")";

                      //process longs
                      thisLocalFilter+=" && ((decimalLongitude <= "+request.getParameter("ne_long")+") || (decimalLongitude >= "+request.getParameter("sw_long")+"))";



                    //}
                  }
                  else{
                    //if(!((encLat<=ne_lat)&&(encLat>=sw_lat)&&(encLong<=ne_long)&&(encLong>=sw_long))){

                    //process lats
                    thisLocalFilter+="(decimalLatitude <= "+request.getParameter("ne_lat")+") && (decimalLatitude >= "+request.getParameter("sw_lat")+")";

                    //process longs
                    thisLocalFilter+=" && (decimalLongitude <= "+request.getParameter("ne_long")+") && (decimalLongitude >= "+request.getParameter("sw_long")+")";



                    //}
                  }

                  thisLocalFilter+=" )";
                  if(filter.equals("")){filter=thisLocalFilter;}
                  else{filter+=" && "+thisLocalFilter;}

                  prettyPrint.append(MessageFormat.format(props.getProperty("filterGpsBoundaryNE"), request.getParameter("ne_lat"), request.getParameter("ne_long"))).append("<br />");
                  prettyPrint.append(MessageFormat.format(props.getProperty("filterGpsBoundarySW"), request.getParameter("sw_lat"), request.getParameter("sw_long"))).append("<br />");



                }

                catch(Exception ee){

                  System.out.println("Exception when trying to process lat and long data in EncounterQueryProcessor!");
                  ee.printStackTrace();

                }








          }
        }
      }
    }


    //end GPS filters-----------------------------------------------

    filter+=jdoqlVariableDeclaration;

    filter += parameterDeclaration;

    return filter;

  }

  public static EncounterQueryResult processQuery(Shepherd myShepherd, HttpServletRequest request, String order){

    Vector<Encounter> rEncounters=new Vector<Encounter>();
    Iterator<Encounter> allEncounters;


		String currentUser = null;
		if (request.getUserPrincipal() != null) currentUser = request.getUserPrincipal().getName();

    //Extent<Encounter> encClass=myShepherd.getPM().getExtent(Encounter.class, true);
    //Query query=myShepherd.getPM().newQuery(encClass);
    //if(!order.equals("")){query.setOrdering(order);}


    String filter="";
    StringBuffer prettyPrint=new StringBuffer("");

    Map<String,Object> paramMap = new HashMap<String, Object>();

    filter=queryStringBuilder(request, prettyPrint, paramMap);

    Query query=myShepherd.getPM().newQuery(filter);
    if(!order.equals("")){query.setOrdering(order);}

    if(!filter.trim().equals("")){
        //filter="("+filter+")";
        //query.setFilter(filter);
        allEncounters=myShepherd.getAllEncounters(query, paramMap);
    }
    else{
      allEncounters=myShepherd.getAllEncountersNoFilter();
    }



    //System.out.println("Final filter: "+filter);
    //allEncounters=myShepherd.getAllEncountersNoQuery();

    if(allEncounters!=null){
      while (allEncounters.hasNext()) {
        Encounter temp_enc=(Encounter)allEncounters.next();
        rEncounters.add(temp_enc);
      }
    }




  //filter for encounters of MarkedIndividuals that have been resighted------------------------------------------
  /* 
    if((request.getParameter("resightOnly")!=null)&&(request.getParameter("numResights")!=null)) {
      int numResights=1;

      try{
        numResights=(new Integer(request.getParameter("numResights"))).intValue();
        prettyPrint.append("numResights is > "+numResights+".<br />");
        }
      catch(NumberFormatException nfe) {nfe.printStackTrace();}

      for(int q=0;q<rEncounters.size();q++) {
        Encounter rEnc=(Encounter)rEncounters.get(q);
        //if(rEnc.isAssignedToMarkedIndividual().equals("Unassigned")){
          //rEncounters.remove(q);
          //q--;
        //}
        //else{
          MarkedIndividual s=myShepherd.getMarkedIndividual(rEnc.isAssignedToMarkedIndividual());
          if(s.totalEncounters()<numResights) {
            rEncounters.remove(q);
            q--;
          }
        //}
      }
    }
    */
  //end if resightOnly--------------------------------------------------------------------------------------



    /**
  //filter for vessel------------------------------------------
  if((request.getParameter("vesselField")!=null)&&(!request.getParameter("vesselField").equals(""))) {
    String vesString=request.getParameter("vesselField");
    for(int q=0;q<rEncounters.size();q++) {
        Encounter rEnc=(Encounter)rEncounters.get(q);
        if((rEnc.getDynamicPropertyValue("Vessel")==null)||(rEnc.getDynamicPropertyValue("Vessel").toLowerCase().indexOf(vesString.toLowerCase())==-1)){
          rEncounters.remove(q);
          q--;
          }
      }
      prettyPrint.append("vesselField is "+vesString+".<br />");
  }
  //end vessel filter--------------------------------------------------------------------------------------
*/


/*
  //keyword filters-------------------------------------------------
  String[] keywords=request.getParameterValues("keyword");
  if((keywords!=null)&&(!keywords[0].equals("None"))){

      prettyPrint.append("Keywords: ");
      int kwLength=keywords.length;
      for(int y=0;y<kwLength;y++){
        String kwParam=keywords[y];
        prettyPrint.append(kwParam+" ");
      }

      for(int q=0;q<rEncounters.size();q++) {
          Encounter tShark=(Encounter)rEncounters.get(q);
          boolean hasNeededKeyword=false;
          for(int kwIter=0;kwIter<kwLength;kwIter++) {
            String kwParam=keywords[kwIter];
            if(myShepherd.isKeyword(kwParam)) {
              Keyword word=myShepherd.getKeyword(kwParam);
              //if(word.isMemberOf(tShark)) {
                hasNeededKeyword=true;

              }
            } //end if isKeyword
          }
          if(!hasNeededKeyword){
            rEncounters.remove(q);
            q--;
          }


      } //end for
      prettyPrint.append("<br />");

  }
  //end keyword filters-----------------------------------------------
*/



  	query.closeAll();


		//silo security logging
		ArrayList collabs = Collaboration.collaborationsForCurrentUser(request);
		String url = request.getRequestURL().toString() + "?" + request.getQueryString();
		Date now = new Date();

		String context="context0";
		context = ServletUtilities.getContext(request);
		String rootWebappPath = request.getSession().getServletContext().getRealPath("/");
		File webappsDir = new File(rootWebappPath).getParentFile();
		File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));

		for (int i = 0 ; i < rEncounters.size() ; i++) {
			Encounter rEnc = (Encounter)rEncounters.get(i);
			String owner = rEnc.getAssignedUsername();
			if ((currentUser != null) && !currentUser.equals("") && (owner != null) && !owner.equals(currentUser)) {
				Collaboration c = Collaboration.findCollaborationWithUser(owner, collabs);
				if ((c != null) && c.getState().equals(Collaboration.STATE_APPROVED)) {  //log it

					File userDir=new File(shepherdDataDir.getAbsolutePath() + "/users/" + owner);
    			if(!userDir.exists()){userDir.mkdir();}
					Writer logw = null;
					try {
						logw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(userDir, "collaboration.log"), true)));
						logw.write(now.getTime() + "\t" + currentUser + "\t" + rEnc.getCatalogNumber() + "\t" + url + "\t" + prettyPrint.toString() + "\n");
					} catch (IOException ex) {
						System.out.println(ex);
					} finally {
						try { logw.close(); } catch (Exception ex) {}
					}
				}
			}
		}

    return (new EncounterQueryResult(rEncounters,filter,prettyPrint.toString()));

  }


  private static String processTagFilters(HttpServletRequest request, StringBuffer prettyPrint, Properties props) {
    StringBuilder sb = new StringBuilder();
    sb.append(processAcousticTagFilter(request, prettyPrint, props));
    String satelliteTagFilter = processSatelliteTagFilter(request, prettyPrint, props);
    if (satelliteTagFilter.length() > 0) {
      if (sb.length() > 0) {
        sb.append(" && ");
      }
      sb.append(satelliteTagFilter);
    }
    return sb.toString();
  }


  private static String processSatelliteTagFilter(HttpServletRequest request,
      StringBuffer prettyPrint, Properties props) {
    StringBuilder sb = new StringBuilder();
    String name = request.getParameter("satelliteTagName");
    if (name != null && name.length() > 0 && !"None".equals(name)) {
      prettyPrint.append(MessageFormat.format(props.getProperty("filterSatelliteTagName"), name));
      prettyPrint.append("<br/>");
      sb.append('(');
      sb.append("satelliteTag.name == ");
      sb.append(Util.quote(name));
      sb.append(')');
    }
    String serialNumber = request.getParameter("satelliteTagSerial");
    if (serialNumber != null && serialNumber.length() > 0) {
      prettyPrint.append(MessageFormat.format(props.getProperty("filterSatelliteTagSerial"), serialNumber));
      prettyPrint.append("<br/>");
      if (sb.length() > 0) {
        sb.append(" && ");
      }
      sb.append('(');
      sb.append("satelliteTag.serialNumber == ");
      sb.append(Util.quote(serialNumber));
      sb.append(')');
    }
    String argosPttNumber = request.getParameter("satelliteTagArgosPttNumber");
    if (argosPttNumber != null && argosPttNumber.length() > 0) {
      prettyPrint.append(MessageFormat.format(props.getProperty("filterSatelliteTagArgos"), argosPttNumber));
      prettyPrint.append("<br/>");
      if (sb.length() > 0) {
        sb.append(" && ");
      }
      sb.append('(');
      sb.append("satelliteTag.argosPttNumber == ");
      sb.append(Util.quote(argosPttNumber));
      sb.append(')');
    }
    return sb.toString();
  }

  private static String processAcousticTagFilter(HttpServletRequest request,
      StringBuffer prettyPrint, Properties props) {
    StringBuilder tagFilter = new StringBuilder();
    String acousticTagSerial = request.getParameter("acousticTagSerial");
    if (acousticTagSerial != null && acousticTagSerial.length() > 0) {
      prettyPrint.append(MessageFormat.format(props.getProperty("filterAcousticTagSerial"), acousticTagSerial));
      prettyPrint.append("<br/>");
      tagFilter.append('(');
      tagFilter.append("acousticTag.serialNumber == ");
      tagFilter.append(Util.quote(acousticTagSerial));
      tagFilter.append(')');
    }
    String acousticTagId = request.getParameter("acousticTagId");
    if (acousticTagId != null && acousticTagId.length() > 0) {
      prettyPrint.append(MessageFormat.format(props.getProperty("filterAcousticTagId"), acousticTagId));
      prettyPrint.append("<br/>");
      if (tagFilter.length() > 0) {
        tagFilter.append(" && ");
      }
      tagFilter.append('(');
      tagFilter.append("acousticTag.idNumber == ");
      tagFilter.append(Util.quote(acousticTagId));
      tagFilter.append(')');
    }
    return tagFilter.toString();
  }

  private static String updateJdoqlVariableDeclaration(String jdoqlVariableDeclaration, String typeAndVariable) {
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

  private static String updateParametersDeclaration(
      String parameterDeclaration, String typeAndVariable) {
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
