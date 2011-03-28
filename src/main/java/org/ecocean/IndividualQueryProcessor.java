package org.ecocean;

import java.util.Vector;
import java.lang.StringBuffer;
import javax.servlet.http.HttpServletRequest;
import javax.jdo.Extent;
import javax.jdo.Query;
import java.util.Iterator;
//import java.util.StringTokenizer;
//import java.util.Collections;


public class IndividualQueryProcessor {

  public static MarkedIndividualQueryResult processQuery(Shepherd myShepherd, HttpServletRequest request, String order){

      Vector<MarkedIndividual> rIndividuals=new Vector<MarkedIndividual>();
      StringBuffer prettyPrint=new StringBuffer();
      String filter="SELECT FROM org.ecocean.MarkedIndividual";
      Iterator allSharks;

      int day1=1, day2=31, month1=1, month2=12, year1=0, year2=3000;
      try{month1=(new Integer(request.getParameter("month1"))).intValue();} catch(NumberFormatException nfe) {}
      try{month2=(new Integer(request.getParameter("month2"))).intValue();} catch(NumberFormatException nfe) {}
      try{year1=(new Integer(request.getParameter("year1"))).intValue();} catch(NumberFormatException nfe) {}
      try{year2=(new Integer(request.getParameter("year2"))).intValue();} catch(NumberFormatException nfe) {}
      try{day1=(new Integer(request.getParameter("day1"))).intValue();} catch(NumberFormatException nfe) {}
      try{day2=(new Integer(request.getParameter("day2"))).intValue();} catch(NumberFormatException nfe) {}



      String encFilter="";

      if(request.getParameter("noQuery")==null){

        filter+=" WHERE ";

        encFilter=EncounterQueryProcessor.queryStringBuilder(request, prettyPrint).replaceAll("SELECT FROM", "SELECT DISTINCT individualID FROM");
        filter+="( "+encFilter+" ).contains(this.name)";




      //build the rest of the MarkedIndividual query filter string

      //--filter by years between resights---------------------------
      if((request.getParameter("resightGap")!=null)&&(!request.getParameter("resightGap").equals(""))&&(request.getParameter("resightGapOperator")!=null)) {

              int numResights=0;
              String operator = "greater";
              try{
                numResights=(new Integer(request.getParameter("resightGap"))).intValue();
                operator = request.getParameter("resightGapOperator");
              }
              catch(NumberFormatException nfe) {}


                if(operator.equals("greater")){
                    operator=">=";
                    prettyPrint.append("Number of years between resights is >= "+request.getParameter("resightGap")+"<br />");

                }
                else if(operator.equals("less")){
                  operator="<=";
                  prettyPrint.append("Number of years between resights is <= "+request.getParameter("resightGap")+"<br />");

                }
                else if(operator.equals("equals")){
                  operator="==";
                  prettyPrint.append("Number of years between resights is = "+request.getParameter("resightGap")+"<br />");

                }

       filter+=" && ( maxYearsBetweenResightings "+operator+" "+numResights+" )";

      }
      //---end if resightOnly---------------------------------------



      //filter for sex------------------------------------------

      if(request.getParameter("male")==null) {
        filter+=" && !sex.startsWith('male')";
        prettyPrint.append("Sex is not male.<br />");
      }
      if(request.getParameter("female")==null) {
        filter+=" && !sex.startsWith('female')";
        prettyPrint.append("Sex is not female.<br />");
      }
      if(request.getParameter("unknown")==null) {
        filter+=" && !sex.startsWith('unknown')";
        prettyPrint.append("Sex is unknown.<br />");
      }

      //filter by sex--------------------------------------------------------------------------------------


      } //end if not noQuery

      System.out.println("IndividualQueryProcessor filter: "+filter);

      //query.setFilter(filter);
      Query query=myShepherd.getPM().newQuery(filter);

      try{
        if(request.getParameter("sort")!=null) {
          if(request.getParameter("sort").equals("sex")){allSharks=myShepherd.getAllMarkedIndividuals(query, "sex ascending");}
          else if(request.getParameter("sort").equals("name")) {allSharks=myShepherd.getAllMarkedIndividuals(query, "name ascending");}
          else if(request.getParameter("sort").equals("numberEncounters")) {allSharks=myShepherd.getAllMarkedIndividuals(query, "numberEncounters descending");}
          else{
            allSharks=myShepherd.getAllMarkedIndividuals(query, "name ascending");
          }
        }
        else{
          allSharks=myShepherd.getAllMarkedIndividuals(query, "name ascending");
          //keyword and then name ascending
        }
        //process over to Vector
        if(allSharks!=null){
          while (allSharks.hasNext()) {
            MarkedIndividual temp_shark=(MarkedIndividual)allSharks.next();
            rIndividuals.add(temp_shark);
          }
        }
      }
      catch(NullPointerException npe){}


    //individuals with a photo keyword assigned to one of their encounters
    String[] keywords = request.getParameterValues("keyword");
    if ((keywords != null) && (!keywords[0].equals("None"))) {

      prettyPrint.append("Keywords: ");
      int kwLength = keywords.length;

      for (int kwIter = 0; kwIter < kwLength; kwIter++) {
        String kwParam = keywords[kwIter].replaceAll("%20", " ").trim();
        prettyPrint.append(kwParam + " ");
      }
      prettyPrint.append("<br />");
      for (int kwIter = 0; kwIter < kwLength; kwIter++) {
        String kwParam = keywords[kwIter];
        if (myShepherd.isKeyword(kwParam)) {
          Keyword word = myShepherd.getKeyword(kwParam);
          for (int q = 0; q < rIndividuals.size(); q++) {
            MarkedIndividual tShark = (MarkedIndividual) rIndividuals.get(q);
            if (!tShark.isDescribedByPhotoKeyword(word)) {
              rIndividuals.remove(q);
              q--;
            }
          } //end for
        } //end if isKeyword
      }
    }




    //min number of resights
    if ((request.getParameter("numResights") != null) && (!request.getParameter("numResights").equals("")) && (request.getParameter("numResightsOperator") != null)) {
      prettyPrint.append("Number of resights is " + request.getParameter("numResightsOperator") + " than " + request.getParameter("numResights") + "<br />");

      int numResights = 1;
      String operator = "greater";
      try {
        numResights = (new Integer(request.getParameter("numResights"))).intValue();
        operator = request.getParameter("numResightsOperator");
      } catch (NumberFormatException nfe) {
      }
      for (int q = 0; q < rIndividuals.size(); q++) {
        MarkedIndividual tShark = (MarkedIndividual) rIndividuals.get(q);


        if (operator.equals("greater")) {
          if (tShark.getMaxNumYearsBetweenSightings() < numResights) {
            rIndividuals.remove(q);
            q--;
          }
        } else if (operator.equals("less")) {
          if (tShark.getMaxNumYearsBetweenSightings() > numResights) {
            rIndividuals.remove(q);
            q--;
          }
        } else if (operator.equals("equals")) {
          if (tShark.getMaxNumYearsBetweenSightings() != numResights) {
            rIndividuals.remove(q);
            q--;
          }
        }


      } //end for
    }//end if resightOnly


      return (new MarkedIndividualQueryResult(rIndividuals,filter,prettyPrint.toString()));

  }

}
