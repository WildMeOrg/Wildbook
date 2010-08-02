package org.ecocean;

import java.util.Vector;
import java.util.Iterator;
import javax.jdo.Extent;
import javax.jdo.Query;
import javax.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

public class EncounterQueryProcessor {
  
  public static Vector<Encounter> processQuery(Shepherd myShepherd, HttpServletRequest request){
    
    Vector<Encounter> rEncounters=new Vector<Encounter>();  
    Iterator<Encounter> allEncounters;
    
    Extent<Encounter> encClass=myShepherd.getPM().getExtent(Encounter.class, true);
    Query query=myShepherd.getPM().newQuery(encClass);
    String filter="";


    //filter for location------------------------------------------
    if((request.getParameter("locationField")!=null)&&(!request.getParameter("locationField").equals(""))) {
      String locString=request.getParameter("locationField").toLowerCase().replaceAll("%20", " ").trim();
      if(filter.equals("")){filter="this.verbatimLocality.indexOf('"+locString+"') != -1";}
      else{filter+=" && this.verbatimLocality.indexOf('"+locString+"') != -1";}
    }
    //end location filter--------------------------------------------------------------------------------------
    
    //filter for unidentifiable encounters------------------------------------------
    if(request.getParameter("unidentifiable")==null) {
      if(filter.equals("")){filter="!this.unidentifiable";}
      else{filter+=" && !this.unidentifiable";}
    }
    //-----------------------------------------------------
    
    //---filter out approved
    if(request.getParameter("approved")==null) {
      if(filter.equals("")){filter="!this.approved";}
      else{filter+=" && !this.approved";}
    }
    //----------------------------
    
    //---filter out unapproved
    if(request.getParameter("unapproved")==null) {
      if(filter.equals("")){filter="(!this.approved && !this.unidentifiable)";}
      else{filter+=" && (!this.approved && !this.unidentifiable)";}
    }
    //----------------------------
    
    //filter for location ID------------------------------------------
    if((request.getParameter("locationCodeField")!=null)&&(!request.getParameter("locationCodeField").equals(""))) {
      String locString=request.getParameter("locationCodeField").toLowerCase().replaceAll("%20", " ").trim();
      //System.out.println("locString: "+locString);
      if(filter.equals("")){filter="this.locationID.startsWith('"+locString+"')";}
      else{filter+=" && this.locationID.startsWith('"+locString+"')";}

    }
    //------------------------------------------------------------------

    //filter for alternate ID------------------------------------------
    if((request.getParameter("alternateIDField")!=null)&&(!request.getParameter("alternateIDField").equals(""))) {
      String altID=request.getParameter("alternateIDField").toLowerCase().replaceAll("%20", " ").trim();
      if(filter.equals("")){filter="this.otherCatalogNumbers.startsWith('"+altID+"')";}
      else{filter+=" && this.otherCatalogNumbers.startsWith('"+altID+"')";}
      
    }
    //filter for behavior------------------------------------------
    if((request.getParameter("behaviorField")!=null)&&(!request.getParameter("behaviorField").equals(""))) {
      String behString=request.getParameter("behaviorField").toLowerCase().replaceAll("%20", " ").trim();
      if(filter.equals("")){filter="this.behavior.toLowerCase().indexOf('"+behString+"') != -1";}
      else{filter+=" && this.behavior.toLowerCase().indexOf('"+behString+"') != -1";}
      
    }
    //end behavior filter--------------------------------------------------------------------------------------

    //filter for sex------------------------------------------
    if(request.getParameter("male")==null) {
      if(filter.equals("")){filter="!this.sex.startsWith('male')";}
      else{filter+=" && !this.sex.startsWith('male')";}
    }
    if(request.getParameter("female")==null) {
      if(filter.equals("")){filter="!this.sex.startsWith('female')";}
      else{filter+=" && !this.sex.startsWith('female')";}
    }
    if(request.getParameter("unknown")==null) {
      if(filter.equals("")){filter="!this.sex.startsWith('unknown')";}
      else{filter+=" && !this.sex.startsWith('unknown')";}
    }
    //filter by sex--------------------------------------------------------------------------------------

    //filter by alive/dead status------------------------------------------
    if(request.getParameter("alive")==null) {
      if(filter.equals("")){filter="!this.livingStatus.startsWith('alive')";}
      else{filter+=" && !this.livingStatus.startsWith('alive')";}
    }
    if(request.getParameter("dead")==null) {
      if(filter.equals("")){filter="!this.livingStatus.startsWith('dead')";}
      else{filter+=" && !this.livingStatus.startsWith('dead')";}
    }
    //filter by alive/dead status--------------------------------------------------------------------------------------

    
    
    query.setFilter(filter);
    allEncounters=myShepherd.getAllEncountersNoQuery();
    while (allEncounters.hasNext()) {
      Encounter temp_enc=(Encounter)allEncounters.next();
      rEncounters.add(temp_enc);
    }
    
    //submitter or photographer name filter------------------------------------------
    if((request.getParameter("nameField")!=null)&&(!request.getParameter("nameField").equals(""))) {
      String locString=request.getParameter("nameField").replaceAll("%20"," ").toLowerCase();
        
      for(int q=0;q<rEncounters.size();q++) {
          Encounter rEnc=(Encounter)rEncounters.get(q);
          if((rEnc.getSubmitterName()!=null)&&(rEnc.getSubmitterName().toLowerCase().replaceAll("%20"," ").indexOf(locString)<0)&&(rEnc.getPhotographerName()!=null)&&(rEnc.getPhotographerName().toLowerCase().replaceAll("%20"," ").indexOf(locString)<0)&&(rEnc.getSubmitterEmail()!=null)&&(rEnc.getSubmitterEmail().toLowerCase().replaceAll("%20"," ").indexOf(locString)<0)&&(rEnc.getPhotographerEmail()!=null)&&(rEnc.getPhotographerEmail().toLowerCase().replaceAll("%20"," ").indexOf(locString)<0)){
            rEncounters.remove(q);
            q--;
            }
        }
    }
    //end name filter--------------------------------------------------------------------------------------

    
    
  //filter for encounters of MarkedIndividuals that have been resighted------------------------------------------
    if((request.getParameter("resightOnly")!=null)&&(request.getParameter("numResights")!=null)) {
      int numResights=1;

      try{
        numResights=(new Integer(request.getParameter("numResights"))).intValue();
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


 



  //filter for length------------------------------------------
  if((request.getParameter("selectLength")!=null)&&(request.getParameter("lengthField")!=null)&&(!request.getParameter("lengthField").equals("skip"))&&(!request.getParameter("selectLength").equals(""))) {

  try {

  double dbl_size=(new Double(request.getParameter("lengthField"))).doubleValue();

  if(request.getParameter("selectLength").equals("gt")) {
      for(int q=0;q<rEncounters.size();q++) {
        Encounter rEnc=(Encounter)rEncounters.get(q);
        if(rEnc.getSize()<dbl_size){
          rEncounters.remove(q);
          q--;
          }
      }
  }
  if(request.getParameter("selectLength").equals("lt")) {
      for(int q=0;q<rEncounters.size();q++) {
        Encounter rEnc=(Encounter)rEncounters.get(q);
        if((rEnc.getSize()>dbl_size)||(rEnc.getSize()<0.1)){
          rEncounters.remove(q);
          q--;
          }
      }
  }
  if(request.getParameter("selectLength").equals("eq")) {
      for(int q=0;q<rEncounters.size();q++) {
        Encounter rEnc=(Encounter)rEncounters.get(q);
        if(rEnc.getSize()!=dbl_size){
          rEncounters.remove(q);
          q--;
          }
      }
  }

  } catch(NumberFormatException nfe) {
    //do nothing, just skip on
    nfe.printStackTrace();
  }

  }
  //filter by length--------------------------------------------------------------------------------------


  //filter for vessel------------------------------------------
  if((request.getParameter("vesselField")!=null)&&(!request.getParameter("vesselField").equals(""))) {
      for(int q=0;q<rEncounters.size();q++) {
        Encounter rEnc=(Encounter)rEncounters.get(q);
        String vesString=request.getParameter("vesselField").toLowerCase();
        if((rEnc.getDynamicPropertyValue("Vessel")==null)||(rEnc.getDynamicPropertyValue("Vessel").toLowerCase().indexOf(vesString)==-1)){
          rEncounters.remove(q);
          q--;
          }
      }
  }
  //end vessel filter--------------------------------------------------------------------------------------




  //keyword filters-------------------------------------------------
  if(request.getParameterValues("keyword")!=null){
  String[] keywords=request.getParameterValues("keyword");
  int kwLength=keywords.length;
  for(int kwIter=0;kwIter<kwLength;kwIter++) {
      String kwParam=keywords[kwIter];
      if(myShepherd.isKeyword(kwParam)) {
        Keyword word=myShepherd.getKeyword(kwParam);
        
        for(int q=0;q<rEncounters.size();q++) {
          Encounter tShark=(Encounter)rEncounters.get(q);
          if(!word.isMemberOf(tShark)) {
            rEncounters.remove(q);
            q--;
          }
        } //end for
      } //end if isKeyword
  }
  }
  //end keyword filters-----------------------------------------------  
    
    
    
  //filter for date------------------------------------------
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

    
    for(int q=0;q<rEncounters.size();q++) {
      Encounter rEnc=(Encounter)rEncounters.get(q);
      int m_day=rEnc.getDay();
      int m_month=rEnc.getMonth();
      int m_year=rEnc.getYear();
      if((m_year>maxYear)||(m_year<minYear)){
        rEncounters.remove(q);
        q--;
      }
      else if(((m_year==minYear)&&(m_month<minMonth))||((m_year==maxYear)&&(m_month>maxMonth))) {
        rEncounters.remove(q);
        q--;
      }
      else if(((m_year==minYear)&&(m_month==minMonth)&&(m_day<minDay))||((m_year==maxYear)&&(m_month==maxMonth)&&(m_day>maxDay))) {
        rEncounters.remove(q);
        q--;
      }
    } //end for
      } catch(NumberFormatException nfe) {
    //do nothing, just skip on
    nfe.printStackTrace();
      }
    }

  //date filter--------------------------------------------------------------------------------------

    return rEncounters;
    
  }
  

}
