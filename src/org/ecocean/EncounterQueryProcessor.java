/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean;

import javax.jdo.Extent;
import javax.jdo.Query;
import javax.servlet.http.HttpServletRequest;
import java.util.Iterator;
import java.util.Vector;

public class EncounterQueryProcessor {

  public static EncounterQueryResult processQuery(Shepherd myShepherd, HttpServletRequest request, String order) {

    Vector<Encounter> rEncounters = new Vector<Encounter>();
    Iterator<Encounter> allEncounters;

    Extent<Encounter> encClass = myShepherd.getPM().getExtent(Encounter.class, true);
    Query query = myShepherd.getPM().newQuery(encClass);
    if (!order.equals("")) {
      query.setOrdering(order);
    }
    String filter = "";
    StringBuffer prettyPrint = new StringBuffer("");


    //filter for location------------------------------------------
    if ((request.getParameter("locationField") != null) && (!request.getParameter("locationField").equals(""))) {
      String locString = request.getParameter("locationField").toLowerCase().replaceAll("%20", " ").trim();
      if (filter.equals("")) {
        filter = "(this.verbatimLocality.toLowerCase().indexOf('" + locString + "') != -1)";
      } else {
        filter += " && (this.verbatimLocality.toLowerCase().indexOf('" + locString + "') != -1)";
      }
      prettyPrint.append("locationField contains \"" + locString + "\".<br />");
    }
    //end location filter--------------------------------------------------------------------------------------

    //filter for unidentifiable encounters------------------------------------------
    if (request.getParameter("unidentifiable") == null) {
      if (filter.equals("")) {
        filter = "!this.unidentifiable";
      } else {
        filter += " && !this.unidentifiable";
      }
      prettyPrint.append("Not identifiable.<br />");
    }
    //-----------------------------------------------------

    //---filter out approved
    if (request.getParameter("approved") == null) {
      if (filter.equals("")) {
        filter = "!this.approved";
      } else {
        filter += " && !this.approved";
      }
      prettyPrint.append("Not approved.<br />");
    }
    //----------------------------

    //---filter out unapproved
    if (request.getParameter("unapproved") == null) {
      if (filter.equals("")) {
        filter = "(!this.approved && !this.unidentifiable)";
      } else {
        filter += " && (!this.approved && !this.unidentifiable)";
      }
      prettyPrint.append("Not unapproved.<br />");
    }
    //----------------------------


    //------------------------------------------------------------------
    //locationID filters-------------------------------------------------
    String[] locCodes = request.getParameterValues("locationCodeField");
    if ((locCodes != null) && (!locCodes[0].equals("None"))) {
      prettyPrint.append("locationCodeField is one of the following: ");
      int kwLength = locCodes.length;
      String locIDFilter = "(";
      for (int kwIter = 0; kwIter < kwLength; kwIter++) {
        String kwParam = locCodes[kwIter].replaceAll("%20", " ").trim();
        if (!kwParam.equals("")) {
          if (locIDFilter.equals("(")) {
            locIDFilter += " this.locationID == \"" + kwParam + "\"";
          } else {
            locIDFilter += " || this.locationID == \"" + kwParam + "\"";
          }
          prettyPrint.append(kwParam + " ");
        }
      }
      locIDFilter += " )";
      if (filter.equals("")) {
        filter = locIDFilter;
      } else {
        filter += (" && " + locIDFilter);
      }
      prettyPrint.append("<br />");
    }
    //end locationID filters-----------------------------------------------  

    //------------------------------------------------------------------
    //behavior filters-------------------------------------------------
    String[] behaviors = request.getParameterValues("behaviorField");
    if ((behaviors != null) && (!behaviors[0].equals("None"))) {
      prettyPrint.append("behaviorField is one of the following: ");
      int kwLength = behaviors.length;
      String locIDFilter = "(";
      for (int kwIter = 0; kwIter < kwLength; kwIter++) {
        String kwParam = behaviors[kwIter].replaceAll("%20", " ").trim();
        if (!kwParam.equals("")) {
          if (locIDFilter.equals("(")) {
            locIDFilter += " this.behavior == \"" + kwParam + "\"";
          } else {
            locIDFilter += " || this.behavior == \"" + kwParam + "\"";
          }
          prettyPrint.append(kwParam + " ");
        }
      }
      locIDFilter += " )";
      if (filter.equals("")) {
        filter = locIDFilter;
      } else {
        filter += (" && " + locIDFilter);
      }
      prettyPrint.append("<br />");
    }
    //end locationID filters-----------------------------------------------  


    //------------------------------------------------------------------
    //verbatimEventDate filters-------------------------------------------------
    String[] verbatimEventDates = request.getParameterValues("verbatimEventDateField");
    if ((verbatimEventDates != null) && (!verbatimEventDates[0].equals("None"))) {
      prettyPrint.append("verbatimEventDateField is one of the following: ");
      int kwLength = verbatimEventDates.length;
      String locIDFilter = "(";
      for (int kwIter = 0; kwIter < kwLength; kwIter++) {

        String kwParam = verbatimEventDates[kwIter].replaceAll("%20", " ").trim();
        if (!kwParam.equals("")) {
          if (locIDFilter.equals("(")) {
            locIDFilter += " this.verbatimEventDate == \"" + kwParam + "\"";
          } else {
            locIDFilter += " || this.verbatimEventDate == \"" + kwParam + "\"";
          }
          prettyPrint.append(kwParam + " ");
        }
      }
      locIDFilter += " )";
      if (filter.equals("")) {
        filter = locIDFilter;
      } else {
        filter += (" && " + locIDFilter);
      }
      prettyPrint.append("<br />");
    }
    //end verbatimEventDate filters-----------------------------------------------  


    //filter for alternate ID------------------------------------------
    if ((request.getParameter("alternateIDField") != null) && (!request.getParameter("alternateIDField").equals(""))) {
      String altID = request.getParameter("alternateIDField").toLowerCase().replaceAll("%20", " ").trim();
      if (filter.equals("")) {
        filter = "this.otherCatalogNumbers.startsWith('" + altID + "')";
      } else {
        filter += " && this.otherCatalogNumbers.startsWith('" + altID + "')";
      }
      prettyPrint.append("alternateIDField starts with \"" + altID + "\".<br />");

    }

    /**
     //filter for behavior------------------------------------------
     if((request.getParameter("behaviorField")!=null)&&(!request.getParameter("behaviorField").equals(""))) {
     String behString=request.getParameter("behaviorField").toLowerCase().replaceAll("%20", " ").trim();
     if(filter.equals("")){filter="this.behavior.toLowerCase().indexOf('"+behString+"') != -1";}
     else{filter+=" && this.behavior.toLowerCase().indexOf('"+behString+"') != -1";}
     prettyPrint.append("behaviorField contains \""+behString+"\".<br />");
     }
     //end behavior filter--------------------------------------------------------------------------------------
     */


    //filter for sex------------------------------------------
    if (request.getParameter("male") == null) {
      if (filter.equals("")) {
        filter = "!this.sex.startsWith('male')";
      } else {
        filter += " && !this.sex.startsWith('male')";
      }
      prettyPrint.append("Sex is not male.<br />");
    }
    if (request.getParameter("female") == null) {
      if (filter.equals("")) {
        filter = "!this.sex.startsWith('female')";
      } else {
        filter += " && !this.sex.startsWith('female')";
      }
      prettyPrint.append("Sex is not female.<br />");
    }
    if (request.getParameter("unknown") == null) {
      if (filter.equals("")) {
        filter = "!this.sex.startsWith('unknown')";
      } else {
        filter += " && !this.sex.startsWith('unknown')";
      }
      prettyPrint.append("Sex is unknown.<br />");
    }
    //filter by sex--------------------------------------------------------------------------------------

    //filter by alive/dead status------------------------------------------
    if (request.getParameter("alive") == null) {
      if (filter.equals("")) {
        filter = "!this.livingStatus.startsWith('alive')";
      } else {
        filter += " && !this.livingStatus.startsWith('alive')";
      }
      prettyPrint.append("Alive.<br />");
    }
    if (request.getParameter("dead") == null) {
      if (filter.equals("")) {
        filter = "!this.livingStatus.startsWith('dead')";
      } else {
        filter += " && !this.livingStatus.startsWith('dead')";
      }
      prettyPrint.append("Dead.<br />");
    }
    //filter by alive/dead status--------------------------------------------------------------------------------------

    //submitter or photographer name filter------------------------------------------
    if ((request.getParameter("nameField") != null) && (!request.getParameter("nameField").equals(""))) {
      String nameString = request.getParameter("nameField").replaceAll("%20", " ").toLowerCase().trim();
      String filterString = "((this.recordedBy.toLowerCase().indexOf('" + nameString + "') != -1)||(this.submitterEmail.toLowerCase().indexOf('" + nameString + "') != -1)||(this.photographerName.toLowerCase().indexOf('" + nameString + "') != -1)||(this.photographerEmail.toLowerCase().indexOf('" + nameString + "') != -1))";
      if (filter.equals("")) {
        filter = filterString;
      } else {
        filter += (" && " + filterString);
      }
      prettyPrint.append("nameField contains: \"" + nameString + "\"<br />");
    }
    //end name and email filter--------------------------------------------------------------------------------------

    //filter for length------------------------------------------
    if ((request.getParameter("selectLength") != null) && (request.getParameter("lengthField") != null) && (!request.getParameter("lengthField").equals("skip")) && (!request.getParameter("selectLength").equals(""))) {

      String size = request.getParameter("lengthField").trim();

      if (request.getParameter("selectLength").equals("gt")) {
        String filterString = "this.size > " + size;
        if (filter.equals("")) {
          filter = filterString;
        } else {
          filter += (" && " + filterString);
        }
        prettyPrint.append("selectLength is > " + size + ".<br />");
      } else if (request.getParameter("selectLength").equals("lt")) {
        String filterString = "this.size < " + size;
        if (filter.equals("")) {
          filter = filterString;
        } else {
          filter += (" && " + filterString);
        }
        prettyPrint.append("selectLength is < " + size + ".<br />");
      } else if (request.getParameter("selectLength").equals("eq")) {
        String filterString = "this.size == " + size;
        if (filter.equals("")) {
          filter = filterString;
        } else {
          filter += (" && " + filterString);
        }
        prettyPrint.append("selectLength is = " + size + ".<br />");
      }
    }
    if (!filter.equals("")) {
      filter = "(" + filter + ")";
    }
    System.out.println("Final filter: " + filter);
    query.setFilter(filter);
    //allEncounters=myShepherd.getAllEncountersNoQuery();
    allEncounters = myShepherd.getAllEncounters(query);
    while (allEncounters.hasNext()) {
      Encounter temp_enc = (Encounter) allEncounters.next();
      rEncounters.add(temp_enc);
    }


    //filter for encounters of MarkedIndividuals that have been resighted------------------------------------------
    if ((request.getParameter("resightOnly") != null) && (request.getParameter("numResights") != null)) {
      int numResights = 1;

      try {
        numResights = (new Integer(request.getParameter("numResights"))).intValue();
        prettyPrint.append("numResights is > " + numResights + ".<br />");
      } catch (NumberFormatException nfe) {
        nfe.printStackTrace();
      }

      for (int q = 0; q < rEncounters.size(); q++) {
        Encounter rEnc = (Encounter) rEncounters.get(q);
        if (rEnc.isAssignedToMarkedIndividual().equals("Unassigned")) {
          rEncounters.remove(q);
          q--;
        } else {
          MarkedIndividual s = myShepherd.getMarkedIndividual(rEnc.isAssignedToMarkedIndividual());
          if (s.totalEncounters() < numResights) {
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

    //------------------------------------------------------------------
    //GPS filters-------------------------------------------------
    if ((request.getParameter("ne_lat") != null) && (!request.getParameter("ne_lat").equals(""))) {
      if ((request.getParameter("ne_long") != null) && (!request.getParameter("ne_long").equals(""))) {
        if ((request.getParameter("sw_lat") != null) && (!request.getParameter("sw_lat").equals(""))) {
          if ((request.getParameter("sw_long") != null) && (!request.getParameter("sw_long").equals(""))) {

            for (int q = 0; q < rEncounters.size(); q++) {
              Encounter rEnc = (Encounter) rEncounters.get(q);
              if ((rEnc.getDecimalLatitude() == null) || (rEnc.getDecimalLongitude() == null)) {
                rEncounters.remove(q);
                q--;
              } else {
                try {

                  double encLat = (new Double(rEnc.getDecimalLatitude())).doubleValue();
                  double encLong = (new Double(rEnc.getDecimalLongitude())).doubleValue();

                  double ne_lat = (new Double(request.getParameter("ne_lat"))).doubleValue();
                  double ne_long = (new Double(request.getParameter("ne_long"))).doubleValue();
                  double sw_lat = (new Double(request.getParameter("sw_lat"))).doubleValue();
                  double sw_long = (new Double(request.getParameter("sw_long"))).doubleValue();
                  if ((sw_long > 0) && (ne_long < 0)) {
                    if (!((encLat <= ne_lat) && (encLat >= sw_lat) && ((encLong <= ne_long) || (encLong >= sw_long)))) {
                      rEncounters.remove(q);
                      q--;
                    }
                  } else {
                    if (!((encLat <= ne_lat) && (encLat >= sw_lat) && (encLong <= ne_long) && (encLong >= sw_long))) {
                      rEncounters.remove(q);
                      q--;
                    }
                  }
                } catch (NumberFormatException nfe) {
                  rEncounters.remove(q);
                  q--;
                  //nfe.printStackTrace();
                } catch (Exception ee) {
                  rEncounters.remove(q);
                  q--;
                  //ee.printStackTrace();
                }

              }
            }


            /** correct way to do this with JDOQL in the future
             String thisLocalFilter="(";

             //process lats
             thisLocalFilter+="(this.decimalLatitude <= "+request.getParameter("ne_lat")+") && (this.decimalLatitude >= "+request.getParameter("sw_lat")+")";

             //process longs
             thisLocalFilter+=" && (this.decimalLongitude <= "+request.getParameter("ne_long")+") && (this.decimalLongitude >= "+request.getParameter("sw_long")+")";

             thisLocalFilter+=" )";

             if(filter.equals("")){filter=thisLocalFilter;}
             else{filter+=" && "+thisLocalFilter;}
             */

            prettyPrint.append("GPS Boundary NE: \"" + request.getParameter("ne_lat") + ", " + request.getParameter("ne_long") + "\".<br />");
            prettyPrint.append("GPS Boundary SW: \"" + request.getParameter("sw_lat") + ", " + request.getParameter("sw_long") + "\".<br />");


          }
        }
      }
    }
    //end GPS filters-----------------------------------------------  

    //keyword filters-------------------------------------------------
    String[] keywords = request.getParameterValues("keyword");
    if ((keywords != null) && (!keywords[0].equals("None"))) {

      prettyPrint.append("Keywords: ");
      int kwLength = keywords.length;
      for (int y = 0; y < kwLength; y++) {
        String kwParam = keywords[y];
        prettyPrint.append(kwParam + " ");
      }

      for (int q = 0; q < rEncounters.size(); q++) {
        Encounter tShark = (Encounter) rEncounters.get(q);
        boolean hasNeededKeyword = false;
        for (int kwIter = 0; kwIter < kwLength; kwIter++) {
          String kwParam = keywords[kwIter];
          if (myShepherd.isKeyword(kwParam)) {
            Keyword word = myShepherd.getKeyword(kwParam);
            if (word.isMemberOf(tShark)) {
              hasNeededKeyword = true;

            }
          } //end if isKeyword
        }
        if (!hasNeededKeyword) {
          rEncounters.remove(q);
          q--;
        }


      } //end for
      prettyPrint.append("<br />");

    }
    //end keyword filters-----------------------------------------------


    //filter for date------------------------------------------
    if ((request.getParameter("day1") != null) && (request.getParameter("month1") != null) && (request.getParameter("year1") != null) && (request.getParameter("day2") != null) && (request.getParameter("month2") != null) && (request.getParameter("year2") != null)) {
      try {

        //get our date values
        int day1 = (new Integer(request.getParameter("day1"))).intValue();
        int day2 = (new Integer(request.getParameter("day2"))).intValue();
        int month1 = (new Integer(request.getParameter("month1"))).intValue();
        int month2 = (new Integer(request.getParameter("month2"))).intValue();
        int year1 = (new Integer(request.getParameter("year1"))).intValue();
        int year2 = (new Integer(request.getParameter("year2"))).intValue();

        prettyPrint.append("Dates between: " + year1 + "-" + month1 + "-" + day1 + " and " + year2 + "-" + month2 + "-" + day2 + "<br />");

        //order our values
        int minYear = year1;
        int minMonth = month1;
        int minDay = day1;
        int maxYear = year2;
        int maxMonth = month2;
        int maxDay = day2;
        if (year1 > year2) {
          minDay = day2;
          minMonth = month2;
          minYear = year2;
          maxDay = day1;
          maxMonth = month1;
          maxYear = year1;
        } else if (year1 == year2) {
          if (month1 > month2) {
            minDay = day2;
            minMonth = month2;
            minYear = year2;
            maxDay = day1;
            maxMonth = month1;
            maxYear = year1;
          } else if (month1 == month2) {
            if (day1 > day2) {
              minDay = day2;
              minMonth = month2;
              minYear = year2;
              maxDay = day1;
              maxMonth = month1;
              maxYear = year1;
            }
          }
        }


        for (int q = 0; q < rEncounters.size(); q++) {
          Encounter rEnc = (Encounter) rEncounters.get(q);
          int m_day = rEnc.getDay();
          int m_month = rEnc.getMonth();
          int m_year = rEnc.getYear();
          if ((m_year > maxYear) || (m_year < minYear)) {
            rEncounters.remove(q);
            q--;
          } else if (((m_year == minYear) && (m_month < minMonth)) || ((m_year == maxYear) && (m_month > maxMonth))) {
            rEncounters.remove(q);
            q--;
          } else if (((m_year == minYear) && (m_month == minMonth) && (m_day < minDay)) || ((m_year == maxYear) && (m_month == maxMonth) && (m_day > maxDay))) {
            rEncounters.remove(q);
            q--;
          }
        } //end for
      } catch (NumberFormatException nfe) {
        //do nothing, just skip on
        nfe.printStackTrace();
      }
    }

    //date filter--------------------------------------------------------------------------------------
    return (new EncounterQueryResult(rEncounters, filter, prettyPrint.toString()));

  }


}
