package org.ecocean;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;
import java.lang.StringBuffer;

import javax.servlet.http.HttpServletRequest;
//import javax.jdo.Extent;
import javax.jdo.Query;

import org.ecocean.Util.MeasurementEventDesc;
import org.ecocean.servlet.ServletUtilities;

import java.util.Iterator;

import org.joda.time.DateTime;

import static org.ecocean.Util.requestHasVal;
import static org.ecocean.Util.addToJDOFilter;
import static org.ecocean.Util.jdoStringContainsConstraint;
import static org.ecocean.Util.undoUrlEncoding;

import static org.ecocean.servlet.ServletUtilities.getParameterOrAttribute;



// Inspired by IndividualQueryProcessor
public class NestQueryProcessor {

  private static final String SELECT_FROM_ORG_ECOCEAN_NEST_WHERE = "SELECT FROM org.ecocean.Nest WHERE ";
  private static final String VARIABLES_STATEMENT = " ";

  public static String addToFilter(String constraint, String filter) {
    return addToJDOFilter(constraint, filter, SELECT_FROM_ORG_ECOCEAN_NEST_WHERE);
  }

  public static boolean requestHasVal(HttpServletRequest request, String paramName) {
    return ((request.getParameter(paramName)!=null) && (!request.getParameter(paramName).equals("")));
  }


  public static String queryStringBuilder(HttpServletRequest request, StringBuffer prettyPrint, Map<String, Object> paramMap){

    String parameterDeclaration = "";

    String context="context0";
    context=ServletUtilities.getContext(request);

    Shepherd myShepherd=new Shepherd(context);

    int day1=1, day2=31, month1=1, month2=12, year1=0, year2=3000;
    try{month1=(new Integer(request.getParameter("month1"))).intValue();} catch(Exception nfe) {}
    try{month2=(new Integer(request.getParameter("month2"))).intValue();} catch(Exception nfe) {}
    try{year1=(new Integer(request.getParameter("year1"))).intValue();} catch(Exception nfe) {}
    try{year2=(new Integer(request.getParameter("year2"))).intValue();} catch(Exception nfe) {}
    try{day1=(new Integer(request.getParameter("day1"))).intValue();} catch(Exception nfe) {}
    try{day2=(new Integer(request.getParameter("day2"))).intValue();} catch(Exception nfe) {}

    String filter = SELECT_FROM_ORG_ECOCEAN_NEST_WHERE;
    String initFilter = filter;
    String jdoqlVariableDeclaration = VARIABLES_STATEMENT;

    //filter for location------------------------------------------
    System.out.println("------------------");
    System.out.println("NESTQUERYPROCESSOR: beginning");
    System.out.println("NestQueryProcessor init filter: "+filter);

    System.out.println("NestQueryProcessor parameters = "+Util.toString(request.getParameterNames()));

    if (requestHasVal(request, "id")) {
      String idString = undoUrlEncoding(request.getParameter("id"));
      String idConstraint = jdoStringContainsConstraint("id", idString);
      filter = addToFilter(idConstraint, filter);
      System.out.println("NestQueryProcessor added id to filter: "+filter);
    }


    if (requestHasVal(request, "locationID")) {
      String locString = undoUrlEncoding(request.getParameter("locationID"));
      String locConstraint = jdoStringContainsConstraint("locationID", locString);
      filter = addToFilter(locConstraint, filter);
      System.out.println("NestQueryProcessor added locationID to filter: "+filter);
    }

    if (requestHasVal(request, "name")) {
      String nameStr = undoUrlEncoding(request.getParameter("name"));
      String nameConstraint = jdoStringContainsConstraint("name", nameStr);
      filter = addToFilter(nameConstraint, filter);
      System.out.println("NestQueryProcessor added name to filter: "+filter);
    }



    //------------------------------------------------------------------
    //individualID filters-------------------------------------------------
    //supports multiple individualID parameters as well as comma-separated lists of individualIDs within them
    /*
    String[] individualID=request.getParameterValues("individualID");
    if((individualID!=null)&&(!individualID[0].equals(""))&&(!individualID[0].equals("None"))){
          prettyPrint.append("Individual ID is one of the following: ");
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
                  prettyPrint.append(kwParam+" ");
                }

              }

            }
            locIDFilter+=" )";
            if(filter.equals(SELECT_FROM_ORG_ECOCEAN_NEST_WHERE)){filter+=locIDFilter;}
            else{filter+=(" && "+locIDFilter);}
            prettyPrint.append("<br />");
    }*/
    //end individualID filters-----------------------------------------------

      //------------------------------------------------------------------
	    //username filters-------------------------------------------------
	    String[] usernames=request.getParameterValues("username");
	    if((usernames!=null)&&(!usernames[0].equals("None"))){
	          prettyPrint.append("Username is one of the following: ");
	          int kwLength=usernames.length;
	            String patterningCodeFilter="(";
	            for(int kwIter=0;kwIter<kwLength;kwIter++) {

	              String kwParam=usernames[kwIter].replaceAll("%20", " ").trim();
	              if(!kwParam.equals("")){
	                if(patterningCodeFilter.equals("(")){
	                  patterningCodeFilter+=" enc1515.submitterID == \""+kwParam+"\"";
	                }
	                else{

	                  patterningCodeFilter+=" || enc1515.submitterID == \""+kwParam+"\"";
	                }
	                prettyPrint.append(kwParam+" ");
	              }
	            }
	            patterningCodeFilter+=" )";


	            if(filter.equals(SELECT_FROM_ORG_ECOCEAN_NEST_WHERE)){filter+=("encounters.contains(enc1515) &&"+ patterningCodeFilter);}
	            else{filter+=(" && "+patterningCodeFilter+" &&  encounters.contains(enc1515)");}
	            if(!jdoqlVariableDeclaration.contains("org.ecocean.Encounter enc1515")){jdoqlVariableDeclaration+=";org.ecocean.Encounter enc1515";}

	            prettyPrint.append("<br />");
	    }

    // MeasurementEvent filters-----------------------------------------------
    List<MeasurementEventDesc> measurementDescs = Util.findMeasurementEventDescs("en",context);
    String measurementPrefix = "measurement";
    StringBuilder measurementFilter = new StringBuilder(); //"( collectedData.contains(measurement) && (");
    boolean atLeastOneMeasurementEvent = false;
    int measurementsInQuery = 0;
    for (MeasurementEventDesc measurementDesc : measurementDescs) {
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
            prettyPrint.append(measurementDesc.getUnitsLabel());
            prettyPrint.append(" is ");
            prettyPrint.append(operator);
            prettyPrint.append(value);
            prettyPrint.append("<br/>");
            if (atLeastOneMeasurementEvent) {
              measurementFilter.append("&&");
            }
            String measurementVar = "measurement" + measurementsInQuery++;
            measurementFilter.append("(encounters.contains(enc)) && (enc.measurements.contains(" + measurementVar + ") && ");
            measurementFilter.append( measurementVar + ".value " + operator + " " + value);
            measurementFilter.append(" && " + measurementVar + ".type == ");
            measurementFilter.append("\"" + measurementDesc.getType() + "\")");
            atLeastOneMeasurementEvent = true;
          }
        }
      }
    }
    if (atLeastOneMeasurementEvent) {
      if(jdoqlVariableDeclaration.length() > 0){
        jdoqlVariableDeclaration += ";";
      }
      //jdoqlVariableDeclaration=" VARIABLES ";
      for (int i = 0; i < measurementsInQuery; i++) {
        if (i > 0) {
          jdoqlVariableDeclaration += "; ";
        }
        jdoqlVariableDeclaration += " org.ecocean.datacollection.MeasurementEvent measurement" + i;
      }
      if(filter.equals(SELECT_FROM_ORG_ECOCEAN_NEST_WHERE)){
        filter+= measurementFilter.toString();
      }
      else{
        filter+=(" && "+ measurementFilter.toString());
      }
    }
    // end measurement filters



    String releaseDateFromStr = request.getParameter("releaseDateFrom");
    String releaseDateToStr = request.getParameter("releaseDateTo");
    String pattern = CommonConfiguration.getProperty("releaseDateFormat",context);
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
    if (releaseDateFromStr != null && releaseDateFromStr.trim().length() > 0) {
      try {
        Date releaseDateFrom = simpleDateFormat.parse(releaseDateFromStr);
        if (!filter.equals(SELECT_FROM_ORG_ECOCEAN_NEST_WHERE)) {
          filter += " && ";
        }
        filter += "(enc13.releaseDate >= releaseDateFrom)";
        filter += " && encounters.contains(enc13) ";
        parameterDeclaration = updateParametersDeclaration(parameterDeclaration, "java.util.Date releaseDateFrom");
        jdoqlVariableDeclaration = updateJdoqlVariableDeclaration(jdoqlVariableDeclaration, "org.ecocean.Encounter enc13");
        paramMap.put("releaseDateFrom", releaseDateFrom);
        prettyPrint.append("release date >= " + simpleDateFormat.format(releaseDateFrom));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    if (releaseDateToStr != null && releaseDateToStr.trim().length() > 0) {
      try {
        Date releaseDateTo = simpleDateFormat.parse(releaseDateToStr);
        if (!filter.equals(SELECT_FROM_ORG_ECOCEAN_NEST_WHERE)) {
          filter += " && ";
        }
        filter += "(enc13.releaseDate <= releaseDateTo)";
        if (!filter.contains("enc13")) {
          filter += " && encounters.contains(enc13) ";
        }
        parameterDeclaration = updateParametersDeclaration(parameterDeclaration, "java.util.Date releaseDateTo");
        jdoqlVariableDeclaration = updateJdoqlVariableDeclaration(jdoqlVariableDeclaration, "org.ecocean.Encounter enc13");
        paramMap.put("releaseDateTo", releaseDateTo);
        prettyPrint.append("releaseDate <= " + simpleDateFormat.format(releaseDateTo));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }


    // Tag filters------------------------------------------------------
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
          prettyPrint.append("metal tag ");
          prettyPrint.append(metalTagLocation);
          prettyPrint.append(" is ");
          prettyPrint.append(value);
          prettyPrint.append("<br/>");
          String metalTagVar = "metalTag" + metalTagsInQuery++;
          metalTagFilter.append("(enc12.metalTags.contains(" + metalTagVar + ") && ");
          metalTagFilter.append(metalTagVar + ".location == " + Util.quote(metalTagLocation));
          String jdoParam = "tagNumber" + metalTagsInQuery;
          metalTagFilter.append(" && " + metalTagVar + ".tagNumber == " + Util.quote(value) + ")");
        }
      }
    }
    if (metalTagFilter.length() > 0) {
      if (!filter.equals(SELECT_FROM_ORG_ECOCEAN_NEST_WHERE)) {
        filter += " && ";
      }
      filter += metalTagFilter.toString();
      for (int i = 0; i < metalTagsInQuery; i++) {
        jdoqlVariableDeclaration = updateJdoqlVariableDeclaration(jdoqlVariableDeclaration, "org.ecocean.tag.MetalTag metalTag" + i);
      }
      jdoqlVariableDeclaration = updateJdoqlVariableDeclaration(jdoqlVariableDeclaration, "org.ecocean.Encounter enc12");
    }

    String satelliteTagFilter = processSatelliteTagFilter(request, prettyPrint);
    if (satelliteTagFilter.length() > 0) {
      if (!filter.equals(SELECT_FROM_ORG_ECOCEAN_NEST_WHERE)) {
        filter += " && ";
      }
      filter += " (encounters.contains(enc10)) && ";
      filter += satelliteTagFilter;
      jdoqlVariableDeclaration = updateJdoqlVariableDeclaration(jdoqlVariableDeclaration, "org.ecocean.Encounter enc10");
    }
    String acousticTagFilter = processAcousticTagFilter(request, prettyPrint);
    if (acousticTagFilter.length() > 0) {
      if (!filter.equals(SELECT_FROM_ORG_ECOCEAN_NEST_WHERE)) {
        filter += " && ";
      }
      filter += acousticTagFilter;
      filter += " && (encounters.contains(enc11)) ";
      jdoqlVariableDeclaration = updateJdoqlVariableDeclaration(jdoqlVariableDeclaration, "org.ecocean.Encounter enc11");
    }

    // end Tag Filters -------------------------------------------------

    //------------------------------------------------------------------
    //hasPhoto filters-------------------------------------------------
    if(request.getParameter("hasPhoto")!=null){
          prettyPrint.append("Has at least one photo.");

            if(filter.equals(SELECT_FROM_ORG_ECOCEAN_NEST_WHERE)){filter+="encounters.contains(enc464) && enc464.annotations.contains(annot2) && annot2.features.contains(feat2) && feat2.asset != null ";}
            else if (filter.indexOf("enc464.annotations.contains(photo2)")==-1){filter+=(" && encounters.contains(enc464) && enc464.annotations.contains(annot2) && annot2.features.contains(feat2) && feat2.asset != null ");}

            prettyPrint.append("<br />");
            if(!jdoqlVariableDeclaration.contains("org.ecocean.Encounter enc464")){jdoqlVariableDeclaration+=";org.ecocean.Encounter enc464";}
            if(!jdoqlVariableDeclaration.contains("org.ecocean.Annotation annot2")){jdoqlVariableDeclaration+=";org.ecocean.Annotation annot2";}
            if(!jdoqlVariableDeclaration.contains("org.ecocean.media.Feature feat2")){jdoqlVariableDeclaration+=";org.ecocean.media.Feature feat2";}

    }
    //end hasPhoto filters-----------------------------------------------




    //filter for nick name------------------------------------------
    if((request.getParameter("nickNameField")!=null)&&(!request.getParameter("nickNameField").equals(""))) {
      String nickName=request.getParameter("nickNameField").replaceAll("%20", " ").trim().toLowerCase();
      if(filter.equals(SELECT_FROM_ORG_ECOCEAN_NEST_WHERE)){filter+="(nickName.toLowerCase().indexOf('"+nickName+"') != -1)";}

      prettyPrint.append("nickName field contains \""+nickName+"\".<br />");
    }



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

          if(filter.equals(SELECT_FROM_ORG_ECOCEAN_NEST_WHERE)){filter+="enc.genus == '"+genus+"' ";}
          else{filter+=" && enc.genus == '"+genus+"' ";}

          filter+=" && enc.specificEpithet == '"+specificEpithet+"' ";

              prettyPrint.append("genus and species are \""+genusSpecies+"\".<br />");

            }

    }


    //start DOB filter----------------------------
    if((request.getParameter("DOBstart")!=null)&&(request.getParameter("DOBend")!=null)&&(!request.getParameter("DOBstart").equals(""))&&(!request.getParameter("DOBend").equals(""))){

      try{


        DateTime gcMin=new DateTime(request.getParameter("DOBstart"));
        DateTime gcMax=new DateTime(request.getParameter("DOBend"));



        if(filter.equals(SELECT_FROM_ORG_ECOCEAN_NEST_WHERE)){
          filter+="((timeOfBirth >= "+gcMin.getMillis()+") && (timeOfBirth <= "+gcMax.getMillis()+"))";
        }
        else{
          filter+=" && ((timeOfBirth >= "+gcMin.getMillis()+") && (timeOfBirth <= "+gcMax.getMillis()+"))";
        }
        prettyPrint.append("Date of birth between: "+request.getParameter("DOBstart")+" and "+request.getParameter("DOBend")+"<br />");


      }
      catch(Exception nfe) {
        //do nothing, just skip on
        nfe.printStackTrace();
      }
    }
    //end DOB filter ----------------------------------------




    //start date filter----------------------------
    if((request.getParameter("day1")!=null)&&(request.getParameter("month1")!=null)&&(request.getParameter("year1")!=null)&&(request.getParameter("day2")!=null)&&(request.getParameter("month2")!=null)&&(request.getParameter("year2")!=null)) {
      try{


    prettyPrint.append("Dates between: "+year1+"-"+month1+"-"+day1+" and "+year2+"-"+month2+"-"+day2+"<br />");

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


    if(filter.equals(SELECT_FROM_ORG_ECOCEAN_NEST_WHERE)){
      filter+="((enc.dateInMilliseconds >= "+gcMin.getMillis()+") && (enc.dateInMilliseconds <= "+gcMax.getMillis()+"))";
    }
    else{filter+=" && ((enc.dateInMilliseconds >= "+gcMin.getMillis()+") && (enc.dateInMilliseconds <= "+gcMax.getMillis()+"))";
    }




      } catch(NumberFormatException nfe) {
    //do nothing, just skip on
    nfe.printStackTrace();
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
                      thisLocalFilter+="(enc.decimalLatitude <= "+request.getParameter("ne_lat")+") && (enc.decimalLatitude >= "+request.getParameter("sw_lat")+")";

                      //process longs
                      thisLocalFilter+=" && ((enc.decimalLongitude <= "+request.getParameter("ne_long")+") || (enc.decimalLongitude >= "+request.getParameter("sw_long")+"))";



                    //}
                  }
                  else{
                    //if(!((encLat<=ne_lat)&&(encLat>=sw_lat)&&(encLong<=ne_long)&&(encLong>=sw_long))){

                    //process lats
                    thisLocalFilter+="(enc.decimalLatitude <= "+request.getParameter("ne_lat")+") && (enc.decimalLatitude >= "+request.getParameter("sw_lat")+")";

                    //process longs
                    thisLocalFilter+=" && (enc.decimalLongitude <= "+request.getParameter("ne_long")+") && (enc.decimalLongitude >= "+request.getParameter("sw_long")+")";



                    //}
                  }

                  thisLocalFilter+=" )";
                  if(filter.equals("")){filter=thisLocalFilter;}
                  else{filter+=" && "+thisLocalFilter;}

                  prettyPrint.append("GPS Boundary NE: \""+request.getParameter("ne_lat")+", "+request.getParameter("ne_long")+"\".<br />");
                  prettyPrint.append("GPS Boundary SW: \""+request.getParameter("sw_lat")+", "+request.getParameter("sw_long")+"\".<br />");



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


    if(request.getParameter("noQuery")==null){


    //build the rest of the MarkedIndividual query filter string






    } //end if not noQuery

	//in the case where no parameters were specified, we need to replace the final "&&"
		//System.out.println("filter is--"+filter+"--");
		if(filter.equals(SELECT_FROM_ORG_ECOCEAN_NEST_WHERE)){filter="SELECT FROM org.ecocean.Nest";}


    filter += jdoqlVariableDeclaration;
    filter += parameterDeclaration;
    myShepherd=null;
    System.out.println("NestQueryProcessor returning filter: "+filter);
    return filter;

  }

  public static NestQueryResult processQuery(Shepherd myShepherd, HttpServletRequest request, String order){
      System.out.println("NestQueryProcessor: beginning processQuery.");
      Iterator<Nest> allSharks;
      Vector<Nest> rIndividuals=new Vector<Nest>();
      StringBuffer prettyPrint=new StringBuffer();
      Map<String,Object> paramMap = new HashMap<String, Object>();
      String filter=queryStringBuilder(request, prettyPrint, paramMap);
      System.out.println("processQuery: filter = "+filter+".");

      //query.setFilter(filter);
      Query query=myShepherd.getPM().newQuery(filter);
      if((order!=null)&&(!order.trim().equals(""))){
        query.setOrdering(order);
      }
      try{

        //range: can be passed as parameter (from user) or attribute (from other servlet)
        int rangeStart = -1;
        int rangeEnd = -1;
        try {
          if (request.getParameter("rangeStart")!=null) {rangeStart=Integer.parseInt(request.getParameter("rangeStart"));}
          else if (request.getAttribute("rangeStart")!=null) {rangeStart=(Integer)request.getAttribute("rangeStart");}
          if (request.getParameter("rangeEnd")!=null) {rangeEnd=Integer.parseInt(request.getParameter("rangeEnd"));}
          else if (request.getAttribute("rangeEnd")!=null) {rangeEnd=(Integer)request.getAttribute("rangeEnd");}
        } catch (NumberFormatException nfe) {}

        if (rangeStart!=-1 && rangeEnd!=-1) {
          query.setRange(rangeStart, rangeEnd);
        }

        allSharks = myShepherd.getAllNests(query, "name descending", paramMap);

        //process over to Vector
        if(allSharks!=null){
          while (allSharks.hasNext()) {
            Nest temp_shark=allSharks.next();
            rIndividuals.add(temp_shark);
          }
        }
      }
      catch(NullPointerException npe){}




    //check whether locationIDs are AND'd rather than OR'd


    query.closeAll();
    System.out.println("About to return NestQueryResult with num rIndividuals = "+rIndividuals.size());
		return (new NestQueryResult(rIndividuals,filter,prettyPrint.toString()));

  }

  private static String processSatelliteTagFilter(HttpServletRequest request,
      StringBuffer prettyPrint) {
    StringBuilder sb = new StringBuilder();
    String name = request.getParameter("satelliteTagName");
    if (name != null && name.length() > 0 && !"None".equals(name)) {
      prettyPrint.append("satellite tag name is: ");
      prettyPrint.append(name);
      prettyPrint.append("<br/>");
      sb.append('(');
      sb.append("enc10.satelliteTag.name == ");
      sb.append(Util.quote(name));
      sb.append(')');
    }
    String serialNumber = request.getParameter("satelliteTagSerial");
    if (serialNumber != null && serialNumber.length() > 0) {
      prettyPrint.append("satellite tag serial is: ");
      prettyPrint.append(serialNumber);
      prettyPrint.append("<br/>");
      if (sb.length() > 0) {
        sb.append(" && ");
      }
      sb.append('(');
      sb.append("enc10.satelliteTag.serialNumber == ");
      sb.append(Util.quote(serialNumber));
      sb.append(')');
    }
    String argosPttNumber = request.getParameter("satelliteTagArgosPttNumber");
    if (argosPttNumber != null && argosPttNumber.length() > 0) {
      prettyPrint.append("satellite tag Argos PTT Number is: ");
      prettyPrint.append(argosPttNumber);
      prettyPrint.append("<br/>");
      if (sb.length() > 0) {
        sb.append(" && ");
      }
      sb.append('(');
      sb.append("enc10.satelliteTag.argosPttNumber == ");
      sb.append(Util.quote(argosPttNumber));
      sb.append(')');
    }
    return sb.toString();
  }

  private static String processAcousticTagFilter(HttpServletRequest request,
      StringBuffer prettyPrint) {
    StringBuilder tagFilter = new StringBuilder();
    String acousticTagSerial = request.getParameter("acousticTagSerial");
    if (acousticTagSerial != null && acousticTagSerial.length() > 0) {
      prettyPrint.append("acoustic tag serial number is: ");
      prettyPrint.append(acousticTagSerial);
      prettyPrint.append("<br/>");
      tagFilter.append('(');
      tagFilter.append("enc11.acousticTag.serialNumber == ");
      tagFilter.append(Util.quote(acousticTagSerial));
      tagFilter.append(')');
    }
    String acousticTagId = request.getParameter("acousticTagId");
    if (acousticTagId != null && acousticTagId.length() > 0) {
      prettyPrint.append("acoustic tag id is: ");
      prettyPrint.append(acousticTagId);
      prettyPrint.append("<br/>");
      if (tagFilter.length() > 0) {
        tagFilter.append(" && ");
      }
      tagFilter.append('(');
      tagFilter.append("enc11.acousticTag.idNumber == ");
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
