package org.ecocean;

import java.util.Vector;
import java.lang.StringBuffer;
import javax.servlet.http.HttpServletRequest;
import javax.jdo.Extent;
import javax.jdo.Query;
import java.util.Iterator;
import java.util.StringTokenizer;


public class IndividualQueryProcessor {
  
  public static MarkedIndividualQueryResult processQuery(Shepherd myShepherd, HttpServletRequest request, String order){
    
      Vector<MarkedIndividual> rIndividuals=new Vector<MarkedIndividual>();  
      StringBuffer queryPrettyPrint=new StringBuffer();
      String filter="";
      Iterator allSharks;
      
      int day1=1, day2=31, month1=1, month2=12, year1=0, year2=3000;
      try{month1=(new Integer(request.getParameter("month1"))).intValue();} catch(NumberFormatException nfe) {}
      try{month2=(new Integer(request.getParameter("month2"))).intValue();} catch(NumberFormatException nfe) {}
      try{year1=(new Integer(request.getParameter("year1"))).intValue();} catch(NumberFormatException nfe) {}
      try{year2=(new Integer(request.getParameter("year2"))).intValue();} catch(NumberFormatException nfe) {}
      
      Extent indieClass=myShepherd.getPM().getExtent(MarkedIndividual.class, true);
      Query query=myShepherd.getPM().newQuery(indieClass);
      if(request.getParameter("sort")!=null) {
        if(request.getParameter("sort").equals("sex")){allSharks=myShepherd.getAllMarkedIndividuals(query, "sex ascending");}
        else if(request.getParameter("sort").equals("name")) {allSharks=myShepherd.getAllMarkedIndividuals(query, "name ascending");}
        else if(request.getParameter("sort").equals("numberEncounters")) {allSharks=myShepherd.getAllMarkedIndividuals(query, "numberEncounters descending");}
        else{allSharks=myShepherd.getAllMarkedIndividuals(query);}
      }
      else{
        allSharks=myShepherd.getAllMarkedIndividuals(query);
      }
      //process over to Vector
      while (allSharks.hasNext()) {
        MarkedIndividual temp_shark=(MarkedIndividual)allSharks.next();
        rIndividuals.add(temp_shark);
      }

      //locationID filters-------------------------------------------------
      String[] locCodes=request.getParameterValues("locationCodeField");
      if((locCodes!=null)&&(!locCodes[0].equals("None"))){
        queryPrettyPrint.append("locationCodeField is one of the following: ");
            int kwLength=locCodes.length;
            for(int q=0;q<rIndividuals.size();q++) {
              MarkedIndividual tShark=(MarkedIndividual)rIndividuals.get(q);
              boolean wasSightedInOneOfThese=false;
            for(int kwIter=0;kwIter<kwLength;kwIter++) {
                
                String kwParam=locCodes[kwIter].replaceAll("%20", " ").trim();
                if(!kwParam.equals("")){
                  if(tShark.wasSightedInLocationCode(kwParam)) {
                    wasSightedInOneOfThese=true;
                  }
                  queryPrettyPrint.append(kwParam+" ");

                }
                
              }
              if(!wasSightedInOneOfThese) {
                 rIndividuals.remove(q);
                 q--;
              }
              
            }     //end for  
            

              queryPrettyPrint.append("<br />");
      }
      //end locationID filters-----------------------------------------------  
      
      /*   
      //individuals in a particular location ID
      if((request.getParameter("locationCodeField")!=null)&&(!request.getParameter("locationCodeField").equals(""))) {
              for(int q=0;q<rIndividuals.size();q++) {
                MarkedIndividual tShark=(MarkedIndividual)rIndividuals.get(q);
                
                StringTokenizer st=new StringTokenizer(request.getParameter("locationCodeField"),",");
                boolean exit=false;
                while((st.hasMoreTokens())&&(!exit)){
                  if(!tShark.wasSightedInLocationCode(st.nextToken())) {
                    rIndividuals.remove(q);
                    q--;
                    exit=true;
                  }
                }
              }     //end for
      }//end if in locationCode
      */
      
      //individuals with a particular alternateID
      if((request.getParameter("alternateIDField")!=null)&&(!request.getParameter("alternateIDField").equals(""))) {
              for(int q=0;q<rIndividuals.size();q++) {
                MarkedIndividual tShark=(MarkedIndividual)rIndividuals.get(q);
                if((tShark.getAlternateID()==null)||(!tShark.getAlternateID().startsWith(request.getParameter("alternateIDField")))) {
                  rIndividuals.remove(q);
                  q--;
                }
                
              }     //end for
      }//end if with alternateID


      //individuals with a photo keyword assigned to one of their encounters
      if(request.getParameterValues("keyword")!=null){
      String[] keywords=request.getParameterValues("keyword");
      int kwLength=keywords.length;
      for(int kwIter=0;kwIter<kwLength;kwIter++) {
          String kwParam=keywords[kwIter];
          if(myShepherd.isKeyword(kwParam)) {
            Keyword word=myShepherd.getKeyword(kwParam);
            for(int q=0;q<rIndividuals.size();q++) {
              MarkedIndividual tShark=(MarkedIndividual)rIndividuals.get(q);
              if(!tShark.isDescribedByPhotoKeyword(word)) {
                rIndividuals.remove(q);
                q--;
              }
            } //end for
          } //end if isKeyword
      }
      }



      //individuals of a particular sex
      if(request.getParameter("sex")!=null) {
              for(int q=0;q<rIndividuals.size();q++) {
                MarkedIndividual tShark=(MarkedIndividual)rIndividuals.get(q);
                if((request.getParameter("sex").equals("male"))&&(!tShark.getSex().equals("male"))) {
                  rIndividuals.remove(q);
                  q--;
                }
                else if((request.getParameter("sex").equals("female"))&&(!tShark.getSex().equals("female"))) {
                  rIndividuals.remove(q);
                  q--;
                }
                else if((request.getParameter("sex").equals("unknown"))&&(!tShark.getSex().equals("unknown"))) {
                  rIndividuals.remove(q);
                  q--;
                }
                else if((request.getParameter("sex").equals("mf"))&&(tShark.getSex().equals("unknown"))) {
                  rIndividuals.remove(q);
                  q--;
                }
              } //end for
      }//end if of sex




      //individuals of a particular size
      if((request.getParameter("selectLength")!=null)&&(request.getParameter("lengthField")!=null)) {
              try {
                double size;
                size=(new Double(request.getParameter("lengthField"))).doubleValue();
                for(int q=0;q<rIndividuals.size();q++) {
                MarkedIndividual tShark=(MarkedIndividual)rIndividuals.get(q);
                if(request.getParameter("selectLength").equals("greater")){
                  if(tShark.avgLengthInPeriod(year1, month1, year2, month2)<size) {
                    rIndividuals.remove(q);
                    q--;
                  }
                }
                else if(request.getParameter("selectLength").equals("less")) {
                  if(tShark.avgLengthInPeriod(year1, month1, year2, month2)>size) {
                    rIndividuals.remove(q);
                    q--;
                  }
                }

              } //end for
            } catch(NumberFormatException nfe) {}
      }//end if is of size
            
      //min number of resights      
      if((request.getParameter("numResights")!=null)&&(request.getParameter("numResightsOperator")!=null)) {
              int numResights=1;
              String operator = "greater";
              try{
                numResights=(new Integer(request.getParameter("numResights"))).intValue();
                operator = request.getParameter("numResightsOperator");
              }
              catch(NumberFormatException nfe) {}
              for(int q=0;q<rIndividuals.size();q++) {
                MarkedIndividual tShark=(MarkedIndividual)rIndividuals.get(q);
                
                
                if(operator.equals("greater")){
                  if(tShark.getMaxNumYearsBetweenSightings()<numResights) {
                    rIndividuals.remove(q);
                    q--;
                  }
                }
                else if(operator.equals("less")){
                  if(tShark.getMaxNumYearsBetweenSightings()>numResights) {
                    rIndividuals.remove(q);
                    q--;
                  }
                }
                else if(operator.equals("equals")){
                  if(tShark.getMaxNumYearsBetweenSightings() != numResights) {
                    rIndividuals.remove(q);
                    q--;
                  }
                }
                
                
              } //end for
      }//end if resightOnly

      //min number of spots   
      if(request.getParameter("numspots")!=null) {
              int numspots=1;
              try{
                numspots=(new Integer(request.getParameter("numspots"))).intValue();
                }
              catch(NumberFormatException nfe) {}
              for(int q=0;q<rIndividuals.size();q++) {
                MarkedIndividual tShark=(MarkedIndividual)rIndividuals.get(q);
                int total=tShark.totalEncounters();
                boolean removeShark=true;
                for(int k=0;k<total;k++) {
                  Encounter enc=tShark.getEncounter(k);
                  if(enc.getNumSpots()>=numspots) {removeShark=false;}

                } //end for encounters
                if(removeShark) {
                    rIndividuals.remove(q);
                    q--;
                } //end if

              } //end for sharks
      }//end if numspots


      //now filter for date-----------------------------
      for(int q=0;q<rIndividuals.size();q++) {
                MarkedIndividual tShark=(MarkedIndividual)rIndividuals.get(q);
                if(!tShark.wasSightedInPeriod(year1, month1, year2, month2)) {
                  rIndividuals.remove(q);
                  q--;
                }
      } //end for
      //--------------------------------------------------
      
      return (new MarkedIndividualQueryResult(rIndividuals,filter,queryPrettyPrint.toString()));
    
  }

}
