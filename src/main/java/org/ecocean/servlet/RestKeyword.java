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

package org.ecocean.servlet;

import org.ecocean.CommonConfiguration;
import org.ecocean.Keyword;
import org.ecocean.Shepherd;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.jdo.Query;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

//import javax.jdo.*;
//import com.poet.jdo.*;


//handles operations to sharks. possible operations include, create new, add encounter, remove encounter from
public class RestKeyword extends HttpServlet {


    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }


/*
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }
*/

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        

        JSONObject jin = ServletUtilities.jsonFromHttpServletRequest(request);
        JSONObject jout = new JSONObject("{\"success\": false}");


        if (request.getUserPrincipal() == null) {  //TODO use AccessControl...
            response.setStatus(401);
            jout.put("error", "access denied");

        } 
        else if (jin.optJSONObject("onMediaAssets") != null) {
            JSONObject jadd = jin.getJSONObject("onMediaAssets");
            JSONArray aids = jadd.optJSONArray("assetIds");
            JSONArray remove = jadd.optJSONArray("remove");
            JSONArray add = jadd.optJSONArray("add");
            JSONArray newAdd = jadd.optJSONArray("newAdd");
            if (aids == null) {
                jout.put("error", "no required assetIds [] passed");
            } 
            else if ((remove == null) && (add == null) && (newAdd == null)) {
                jout.put("error", "must have one of remove, add, or newAdd");
            } 
            else {
              Shepherd myShepherd = new Shepherd(context);
              myShepherd.setAction("RestKeyword.class");
              try{  
                  myShepherd.beginDBTransaction();
                  List<MediaAsset> mas = new ArrayList<MediaAsset>();
                  for (int i = 0 ; i < aids.length() ; i++) {
                      MediaAsset ma = MediaAssetFactory.load(aids.optInt(i, -99), myShepherd);
                      if (ma != null) mas.add(ma);
                  }
                  if (mas.size() < 1) {
                      jout.put("error", "no MediaAssets to act upon");
                  } 
                  else {
                      List<Keyword> toAdd = new ArrayList<Keyword>();
                      if ((add != null) && (add.length() > 0)) {
                          for (int k = 0 ; k < add.length() ; k++) {
                              Keyword kw = null;
                              try {
                                  kw = (Keyword)(myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Keyword.class, add.optString(k, null)), true));
                              } catch (Exception ex) {}
                              if (kw == null) continue;
                              if (!toAdd.contains(kw)) toAdd.add(kw);
                          }
                      }
                      if ((newAdd != null) && (newAdd.length() > 0)) {
                          JSONObject newj = new JSONObject();
                          for (int k = 0 ; k < newAdd.length() ; k++) {
                              String name = newAdd.optString(k, null);
                              if (name == null) continue;
                              Keyword kw = null;
                              try {
                                  kw = myShepherd.getKeyword(name);
                              } catch (Exception ex) {}
                              if (kw != null) {
                                  if (!toAdd.contains(kw)) toAdd.add(kw);
                              } else {
                                  kw = new Keyword(name);
                                  myShepherd.getPM().makePersistent(kw);
                                  toAdd.add(kw);
                                  newj.put(kw.getIndexname(), kw.getReadableName());
                              }
                              if (newj.length() > 0) jout.put("newKeywords", newj);
  System.out.println("INFO: RestKeyword new keywords = " + newj);
                          }
                      }
  
                      List<String> toRemove = new ArrayList<String>();
                      if ((remove != null) && (remove.length() > 0)) {
                          for (int i = 0 ; i < remove.length() ; i++) {
                              String id = remove.optString(i, null);
                              if ((id != null) && !toRemove.contains(id)) toRemove.add(id);
                          }
                      }
  
  System.out.println("INFO: RestKeyword mas = " + mas.toString());
  System.out.println("INFO: RestKeyword toAdd = " + toAdd.toString());
  System.out.println("INFO: RestKeyword toRemove = " + toRemove.toString());
  
                      JSONObject jassigned = new JSONObject();
                      for (MediaAsset ma : mas) {
                          ArrayList<Keyword> mine = ma.getKeywords();
  System.out.println("mine -> " + mine);
                          if (mine == null) mine = new ArrayList<Keyword>();
                          ArrayList<Keyword> newList = new ArrayList<Keyword>();
                          //first add what should be added
                          for (int i = 0 ; i < toAdd.size() ; i++) {
                              if (!mine.contains(toAdd.get(i))) newList.add(toAdd.get(i));
                          }
                          //then add from mine except where should be removed
                          for (int i = 0 ; i < mine.size() ; i++) {
                              if (!toRemove.contains(mine.get(i).getIndexname())) newList.add(mine.get(i));
                          }
  System.out.println(ma + " ----------> " + newList);
                          JSONObject mj = new JSONObject();
                          for (Keyword k : newList) {
                              mj.put(k.getIndexname(), k.getReadableName());
                          }
                          if (newList.size() < 1) {
                              ma.setKeywords(null);
                          } else {
                              ma.setKeywords(newList);
                          }
  System.out.println("getKeywords() -> " + ma.getKeywords());
                          jassigned.put(Integer.toString(ma.getId()), mj);
                          MediaAssetFactory.save(ma, myShepherd);
                      }
                      jout.put("results", jassigned);
                      jout.put("success", true);
                      myShepherd.commitDBTransaction();
                      //myShepherd.closeDBTransaction();
                  }
              }
              catch(Exception e){
                e.printStackTrace();
                myShepherd.rollbackDBTransaction();
              }
              finally{
                myShepherd.closeDBTransaction();
              }
            }

        } 
        else {
            jout.put("error", "unknown command");
        }

        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();
        out.println(jout.toString());
        out.flush();
        out.close();
        
    }


}

