/*
 * Wildbook - A Mark-Recapture Framework
 * Copyright (C) 2011-2015 Jason Holmberg
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

package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

import javax.jdo.Query;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;
import org.ecocean.Util;
import org.ecocean.User;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
//import com.samsix.util.string.StringUtilities;


public class SiteSearch extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private final Logger logger = LoggerFactory.getLogger(SiteSearch.class);

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
    }


    @Override
    public void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    public void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        String context="context0";
        context=ServletUtilities.getContext(request);
        
        // this stores the hashmaps for each individual/encounter so we can sort by label later
        Map<String,Map<String,String>> labelToHm = new HashMap<String,Map<String,String>>();
        
        ArrayList<Map<String, String>> list = new ArrayList<Map<String, String>>();
        
        //set up for response
        response.setContentType("text/json");
        PrintWriter out = response.getWriter();

        String term = request.getParameter("term");
        if (!Util.stringExists(term) || (term.length() < 2)) {
            out.println("[]");
            return;
        }
        
        //if it's a UUID, let's try to just return that object
        if(Util.isUUID(term)) {
          Shepherd myShepherd = new Shepherd(request);
          myShepherd.setAction("SiteSearch.class");
          myShepherd.beginDBTransaction();
          
          if(myShepherd.isMarkedIndividual(term.trim())) {
            MarkedIndividual ind=myShepherd.getMarkedIndividual(term.trim());
            HashMap<String, String> hm = new HashMap<String, String>();
            String label = ind.getDisplayName(request);
            hm.put("label", label);
            hm.put("value", ind.getIndividualID());
            hm.put("type", "individual");

            //
            // TODO: Read species from db. See SimpleIndividual
            //
            String gs = ind.getGenusSpeciesDeep();
            if (gs != null) {
              hm.put("species", gs);
            }
            if(ind.getNickName()!=null){
              hm.put("nickname", ind.getNickName());
            }
            labelToHm.put((label+"-"+ind.getIndividualID()), hm);
          }
          else if(myShepherd.isEncounter(term.trim())) {
            HashMap<String, String> hm = new HashMap<String, String>();
            Encounter enc=myShepherd.getEncounter(term.trim());
            String label = enc.getCatalogNumber();
            hm.put("label", label);
            hm.put("value", enc.getCatalogNumber());
            hm.put("type", "encounter");
            labelToHm.put(label, hm);            
          }
          
          myShepherd.rollbackDBTransaction();
          myShepherd.closeDBTransaction();
          
          // now we sort the labels and add them in order
          // this is a runtime hit and we should consider figuring out how to sort on labels 
          List<String> sortedLabels = Util.asSortedList(labelToHm.keySet());
          for (String label: sortedLabels) {
            Map<String, String> hm = labelToHm.get(label);
            list.add(hm);
          }
          out.println(new Gson().toJson(list));
          return;
          
        }

        String regex = ".*" + term.toLowerCase() + ".*";


        

        //
        // Query on Individuals
        //

        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("SiteSearch.class");
        myShepherd.beginDBTransaction();
        
        try {

          List<MarkedIndividual> individuals = MarkedIndividual.findByNames(myShepherd, regex);
    
          for (MarkedIndividual ind : individuals) {
                try {
                  HashMap<String, String> hm = new HashMap<String, String>();
                  String label = ind.getDisplayName(request);
                  hm.put("label", label);
                  hm.put("value", ind.getIndividualID());
                  hm.put("type", "individual");
      
                  //
                  // TODO: Read species from db. See SimpleIndividual
                  //
                  String gs = ind.getGenusSpeciesDeep();
                  if (gs != null) {
                    hm.put("species", gs);
                  }
                  if(ind.getNickName()!=null){
                    hm.put("nickname", ind.getNickName());
                  }
                  labelToHm.put((label+"-"+ind.getIndividualID()), hm);
                  //list.add(hm);
                }
                catch(Exception f) {f.printStackTrace();}
            }
  
  
            // now we sort the labels and add them in order
            // this is a runtime hit and we should consider figuring out how to sort on labels 
            List<String> sortedLabels = Util.asSortedList(labelToHm.keySet());
            for (String label: sortedLabels) {
              Map<String, String> hm = labelToHm.get(label);
              list.add(hm);
            }
        }
        catch(Exception e) {
          e.printStackTrace();
        }
        finally {
          myShepherd.rollbackDBTransaction();
          myShepherd.closeDBTransaction();
        }

        /*
        //
        // Query on Users
        //
        filter = "this.fullName.toLowerCase().matches('"
                + regex
                + "') || this.username.toLowerCase().matches('"
                + regex + "')";

        query = myShepherd.getPM().newQuery(User.class);
        query.setFilter(filter);

        @SuppressWarnings("unchecked")
        List<User> users = (List<User>) query.execute();

        for (User user : users) {
            HashMap<String, String> hm = new HashMap<String, String>();
            if (StringUtils.isBlank(user.getFullName())) {
                hm.put("label", user.getUsername());
            } else {
                hm.put("label", user.getFullName() + " (" + user.getUsername() + ")");
            }
            hm.put("value", user.getUsername());
            hm.put("type", "user");
            list.add(hm);
        }
        
        query.closeAll();
        */
        
        //query locationIDs
        boolean moreLocationIDs=true;
        int siteNum=0;
        while(moreLocationIDs) {
          String currentLocationID = "locationID"+siteNum;
          
          if (CommonConfiguration.getProperty(currentLocationID,context)!=null) {
            HashMap<String, String> hm = new HashMap<String, String>();
            String locID=CommonConfiguration.getProperty(currentLocationID,context);
            if(locID.toLowerCase().indexOf(term.toLowerCase())!=-1){
              hm.put("label", locID);            
              hm.put("value", locID);
              hm.put("type", "locationID");
              list.add(hm);
            }
            siteNum++;
          }
          else{
            moreLocationIDs=false;
          }
          
        }
        //end query locationIDs

        //
        // return our results
        //
        out.println(new Gson().toJson(list));
    }
}
