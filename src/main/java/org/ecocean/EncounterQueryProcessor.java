package org.ecocean;

import java.util.Vector;
import java.util.Iterator;
import javax.jdo.Extent;
import javax.jdo.Query;
import javax.servlet.http.HttpServletRequest;
import java.lang.StringBuffer;
import java.util.GregorianCalendar;

public class EncounterQueryProcessor {
  
  public static String queryStringBuilder(HttpServletRequest request, StringBuffer prettyPrint){
    String filter="SELECT FROM org.ecocean.Encounter WHERE ";
    
  //filter for location------------------------------------------
    if((request.getParameter("locationField")!=null)&&(!request.getParameter("locationField").equals(""))) {
      String locString=request.getParameter("locationField").toLowerCase().replaceAll("%20", " ").trim();
      if(filter.equals("")){
        filter="(verbatimLocality.toLowerCase().indexOf('"+locString+"') != -1)";
      }
      else{filter+=" && (verbatimLocality.toLowerCase().indexOf('"+locString+"') != -1)";}
      prettyPrint.append("locationField contains \""+locString+"\".<br />");
    }
    //end location filter--------------------------------------------------------------------------------------
    
    //filter for unidentifiable encounters------------------------------------------
    if(request.getParameter("unidentifiable")==null) {
      if(filter.equals("SELECT FROM org.ecocean.Encounter WHERE ")){filter+="!unidentifiable";}
      else{filter+=" && !unidentifiable";}
      prettyPrint.append("Not identifiable.<br />");
    }
    //-----------------------------------------------------
    
    //---filter out approved
    if(request.getParameter("approved")==null) {
      if(filter.equals("SELECT FROM org.ecocean.Encounter WHERE ")){filter+="!approved";}
      else{filter+=" && !approved";}
      prettyPrint.append("Not approved.<br />");
    }
    //----------------------------
    
    //---filter out unapproved
    if(request.getParameter("unapproved")==null) {
      if(filter.equals("SELECT FROM org.ecocean.Encounter WHERE ")){filter+="(!approved && !unidentifiable)";}
      else{filter+=" && (!approved && !unidentifiable)";}
      prettyPrint.append("Not unapproved.<br />");
    }
    //----------------------------
    
    
    
    //------------------------------------------------------------------
    //locationID filters-------------------------------------------------
    String[] locCodes=request.getParameterValues("locationCodeField");
    if((locCodes!=null)&&(!locCodes[0].equals("None"))){
          prettyPrint.append("locationCodeField is one of the following: ");
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
                prettyPrint.append(kwParam+" ");
              }
            }
            locIDFilter+=" )";
            if(filter.equals("SELECT FROM org.ecocean.Encounter WHERE ")){filter+=locIDFilter;}
            else{filter+=(" && "+locIDFilter);}
            prettyPrint.append("<br />");
    }
    //end locationID filters-----------------------------------------------  
    
  //------------------------------------------------------------------
    //patterningCode filters-------------------------------------------------
    String[] patterningCodes=request.getParameterValues("patterningCode");
    if((patterningCodes!=null)&&(!patterningCodes[0].equals("None"))){
          prettyPrint.append("Color code is one of the following: ");
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
                prettyPrint.append(kwParam+" ");
              }
            }
            patterningCodeFilter+=" )";
            
            
            if(filter.equals("SELECT FROM org.ecocean.Encounter WHERE ")){filter+=patterningCodeFilter;}
            else{filter+=(" && "+patterningCodeFilter);}
            
            prettyPrint.append("<br />");
    }
    //end patterningCode filters-----------------------------------------------
    
    //------------------------------------------------------------------
    //behavior filters-------------------------------------------------
    String[] behaviors=request.getParameterValues("behaviorField");
    if((behaviors!=null)&&(!behaviors[0].equals("None"))){
          prettyPrint.append("behaviorField is one of the following: ");
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
                prettyPrint.append(kwParam+" ");
              }
            }
            locIDFilter+=" )";
            if(filter.equals("SELECT FROM org.ecocean.Encounter WHERE ")){filter+=locIDFilter;}
            else{filter+=(" && "+locIDFilter);}
            prettyPrint.append("<br />");
    }
    //end locationID filters-----------------------------------------------  
    
    

    
    
    
    //------------------------------------------------------------------
    //verbatimEventDate filters-------------------------------------------------
    String[] verbatimEventDates=request.getParameterValues("verbatimEventDateField");
    if((verbatimEventDates!=null)&&(!verbatimEventDates[0].equals("None"))){
          prettyPrint.append("verbatimEventDateField is one of the following: ");
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
                prettyPrint.append(kwParam+" ");
              }
            }
            locIDFilter+=" )";
            if(filter.equals("SELECT FROM org.ecocean.Encounter WHERE ")){filter+=locIDFilter;}
            else{filter+=(" && "+locIDFilter);}
            prettyPrint.append("<br />");
    }
    //end verbatimEventDate filters-----------------------------------------------  
    
    
    
    

    //filter for alternate ID------------------------------------------
    if((request.getParameter("alternateIDField")!=null)&&(!request.getParameter("alternateIDField").equals(""))) {
      String altID=request.getParameter("alternateIDField").replaceAll("%20", " ").trim();
      if(filter.equals("SELECT FROM org.ecocean.Encounter WHERE ")){filter+="otherCatalogNumbers.startsWith('"+altID+"')";}
      else{filter+=" && otherCatalogNumbers.startsWith('"+altID+"')";}
      prettyPrint.append("alternateIDField starts with \""+altID+"\".<br />");
      
    }
    
    //filter for identificationRemarks------------------------------------------
    if((request.getParameter("identificationRemarksField")!=null)&&(!request.getParameter("identificationRemarksField").equals(""))) {
      String idRemarks=request.getParameter("identificationRemarksField").trim();
      if(filter.equals("SELECT FROM org.ecocean.Encounter WHERE ")){filter+="identificationRemarks.startsWith('"+idRemarks+"')";}
      else{filter+=" && identificationRemarks.startsWith('"+idRemarks+"')";}
      prettyPrint.append("identificationRemarks starts with \""+idRemarks+"\".<br />");
      
    }
    
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
    if(request.getParameter("alive")==null) {
      if(filter.equals("SELECT FROM org.ecocean.Encounter WHERE ")){filter+="!livingStatus.startsWith('alive')";}
      else{filter+=" && !livingStatus.startsWith('alive')";}
      prettyPrint.append("Alive.<br />");
    }
    if(request.getParameter("dead")==null) {
      if(filter.equals("SELECT FROM org.ecocean.Encounter WHERE ")){filter+="!livingStatus.startsWith('dead')";}
      else{filter+=" && !livingStatus.startsWith('dead')";}
      prettyPrint.append("Dead.<br />");
    }
    //filter by alive/dead status--------------------------------------------------------------------------------------

    //submitter or photographer name filter------------------------------------------
    if((request.getParameter("nameField")!=null)&&(!request.getParameter("nameField").equals(""))) {
      String nameString=request.getParameter("nameField").replaceAll("%20"," ").toLowerCase().trim();
      String filterString="((recordedBy.toLowerCase().indexOf('"+nameString+"') != -1)||(submitterEmail.toLowerCase().indexOf('"+nameString+"') != -1)||(photographerName.toLowerCase().indexOf('"+nameString+"') != -1)||(photographerEmail.toLowerCase().indexOf('"+nameString+"') != -1))";
      if(filter.equals("")){filter=filterString;}
      else{filter+=(" && "+filterString);}
      prettyPrint.append("nameField contains: \""+nameString+"\"<br />");
    }
    //end name and email filter--------------------------------------------------------------------------------------

    //------------------------------------------------------------------
    //name and email filters-------------------------------------------------
    /*
    String[] researchGroups=request.getParameterValues("nameField");
    if((researchGroups!=null)&&(!researchGroups[0].equals("None"))){
          prettyPrint.append("nameField is one of the following: ");
          int kwLength=researchGroups.length;
            String locIDFilter="(";
            for(int kwIter=0;kwIter<kwLength;kwIter++) {
              
              String kwParam=researchGroups[kwIter].replaceAll("%20", " ").trim();
              if(!kwParam.equals("")){
                if(locIDFilter.equals("(")){
                  locIDFilter+=" recordedBy == \""+kwParam+"\"";
                }
                else{
                  locIDFilter+=" || recordedBy == \""+kwParam+"\"";
                }
                prettyPrint.append(kwParam+" ");
              }
            }
            locIDFilter+=" )";
            if(filter.equals("SELECT FROM org.ecocean.Encounter WHERE ")){filter+=locIDFilter;}
            else{filter+=(" && "+locIDFilter);}
            prettyPrint.append("<br />");
    }
    */
    //end name and email filters-----------------------------------------------  
    
    
    
    
    
    //filter for length------------------------------------------
    if((request.getParameter("selectLength")!=null)&&(request.getParameter("lengthField")!=null)&&(!request.getParameter("lengthField").equals("skip"))&&(!request.getParameter("selectLength").equals(""))) {

      String size=request.getParameter("lengthField").trim();
      
      if(request.getParameter("selectLength").equals("gt")) {
        String filterString="size > "+size;
        if(filter.equals("SELECT FROM org.ecocean.Encounter WHERE ")){filter+=filterString;}
        else{filter+=(" && "+filterString);}
        prettyPrint.append("selectLength is > "+size+".<br />");
      }
      else if(request.getParameter("selectLength").equals("lt")) {
        String filterString="size < "+size;
        if(filter.equals("SELECT FROM org.ecocean.Encounter WHERE ")){filter+=filterString;}
        else{filter+=(" && "+filterString);}
        prettyPrint.append("selectLength is < "+size+".<br />");
      }
      else if(request.getParameter("selectLength").equals("eq")) {
        String filterString="size == "+size;
        if(filter.equals("SELECT FROM org.ecocean.Encounter WHERE ")){filter+=filterString;}
        else{filter+=(" && "+filterString);}
        prettyPrint.append("selectLength is = "+size+".<br />");
      }
    }
    
    //filter for tissue sample------------------------------------------
    
    /*
    if(request.getParameter("hasTissueSample")!=null) {
      if(filter.equals("SELECT FROM org.ecocean.Encounter WHERE ")){
        filter+="(dynamicProperties.indexOf('Tissue Sample') != -1)";
      }
      else{filter+=" && (dynamicProperties.indexOf('Tissue Sample') != -1)";}
      prettyPrint.append("A tissue sample was taken.<br />");
    }
    */
    //end tissue sample filter--------------------------------------------------------------------------------------
  
    
    
    
    
    
    
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
    
    GregorianCalendar gcMin=new GregorianCalendar(minYear, minMonth, minDay);
    GregorianCalendar gcMax=new GregorianCalendar(maxYear, maxMonth, maxDay);
    
    if(filter.equals("SELECT FROM org.ecocean.Encounter WHERE ")){
      filter+="((dateInMilliseconds >= "+gcMin.getTimeInMillis()+") && (dateInMilliseconds <= "+gcMax.getTimeInMillis()+"))";
    }
    else{filter+="&& ((dateInMilliseconds >= "+gcMin.getTimeInMillis()+") && (dateInMilliseconds <= "+gcMax.getTimeInMillis()+"))";
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
    
    
    
    return filter;
    
  }
  
  public static EncounterQueryResult processQuery(Shepherd myShepherd, HttpServletRequest request, String order){
    
    Vector<Encounter> rEncounters=new Vector<Encounter>();  
    Iterator<Encounter> allEncounters;
    
    
    //Extent<Encounter> encClass=myShepherd.getPM().getExtent(Encounter.class, true);
    //Query query=myShepherd.getPM().newQuery(encClass);
    //if(!order.equals("")){query.setOrdering(order);}
    
    
    String filter="";
    StringBuffer prettyPrint=new StringBuffer("");

    filter=queryStringBuilder(request, prettyPrint);
    
    Query query=myShepherd.getPM().newQuery(filter);
    
    if(!filter.trim().equals("")){
        //filter="("+filter+")";      
        //query.setFilter(filter);
        allEncounters=myShepherd.getAllEncounters(query);
    }
    else{
      allEncounters=myShepherd.getAllEncountersNoFilter();
    }
    
    

    System.out.println("Final filter: "+filter);
    //allEncounters=myShepherd.getAllEncountersNoQuery();
    
    if(allEncounters!=null){
      while (allEncounters.hasNext()) {
        Encounter temp_enc=(Encounter)allEncounters.next();
        rEncounters.add(temp_enc);
      }
    }
    

    
    
  //filter for encounters of MarkedIndividuals that have been resighted------------------------------------------
    if((request.getParameter("resightOnly")!=null)&&(request.getParameter("numResights")!=null)) {
      int numResights=1;

      try{
        numResights=(new Integer(request.getParameter("numResights"))).intValue();
        prettyPrint.append("numResights is > "+numResights+".<br />");
        }
      catch(NumberFormatException nfe) {nfe.printStackTrace();}

      for(int q=0;q<rEncounters.size();q++) {
        Encounter rEnc=(Encounter)rEncounters.get(q);
        if(rEnc.isAssignedToMarkedIndividual().equals("Unassigned")){
          rEncounters.remove(q);
          q--;
          }
        else{
          MarkedIndividual s=myShepherd.getMarkedIndividual(rEnc.isAssignedToMarkedIndividual());
          if(s.totalEncounters()<numResights) {
            rEncounters.remove(q);
            q--;
          }
        }
      }
    }
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
              if(word.isMemberOf(tShark)) {
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

        
    return (new EncounterQueryResult(rEncounters,filter,prettyPrint.toString()));
    
  }
  

  
}
