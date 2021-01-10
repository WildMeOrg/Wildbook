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
import org.ecocean.Util;
import org.ecocean.LabeledKeyword;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.Annotation;
import org.ecocean.Taxonomy;
import org.ecocean.identity.IBEISIA;
import org.ecocean.ia.IA;
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
public class AddLabeledKeyword extends HttpServlet {

  //this could create a long-term leak, commenting out
  //Shepherd myShepherd;
  PrintWriter out;
  boolean locked=false;

  JSONObject jout;

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


/*
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }
*/

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    
    Shepherd myShepherd=new Shepherd(request);
    myShepherd.setAction("AddLabeledKeyword.class");
    myShepherd.beginDBTransaction();
    response.setContentType("text/html");
    out = response.getWriter();

    String context = ServletUtilities.getContext(request);
    jout = new JSONObject("{\"success\": false}");

    String label = request.getParameter("label");
    String value = request.getParameter("value");
    String mid   = request.getParameter("mid");
    System.out.printf("AddLabeledKeyword has our kw vars %s: %s for ma %s\n", label, value, mid);
    if (!Util.stringExists(label)) {
      errorAndClose("Missing 'label' param on AddLabeledKeyword", response,myShepherd);
      return;
    }
    if (!Util.stringExists(value)) {
      errorAndClose("Missing 'value' param on AddLabeledKeyword", response,myShepherd);
      return;
    }
    if (!Util.stringExists(mid)) {
      errorAndClose("Missing 'mid' param on AddLabeledKeyword", response,myShepherd);
      return;
    }
    System.out.printf("AddLabeledKeyword has our kw vars %s: %s for ma %s\n", label, value, mid);

    MediaAsset ma = myShepherd.getMediaAsset(mid);
    if (ma==null) {
      errorAndClose("Could not find MediaAsset "+mid+" specified as 'mid' in AddLabeledKeyword", response,myShepherd);
      return;
    }

    LabeledKeyword lkw = myShepherd.getOrCreateLabeledKeyword(label, value, true); // true: commit lkw to database
    
    //if the method above succeeded, then it ended on a commit in a store method, so now need to beginDBTRansaction again
    myShepherd.beginDBTransaction();
    
    ma.addKeyword(lkw);
    if (ma.hasKeyword(lkw)) {
      // construct jout to conform with RestKeyword
      jout.put("success",true);
      JSONObject newj = new JSONObject();
      newj.put(lkw.getIndexname(), lkw.getDisplayName());
      jout.put("newKeywords", newj);

      // this is just to get the results looking like RestKeyword so we can use the same UI tools
      JSONObject allKws = new JSONObject();
      for (Keyword k : ma.getKeywords()) {
        allKws.put(k.getIndexname(), k.getDisplayName());
      }
      JSONObject jassigned = new JSONObject();
      jassigned.put(Integer.toString(ma.getId()), allKws);
      jout.put("results", jassigned);
      // done w/ RestKeyword conformity

    }
    
    //if we're modifying ma, then another commit is needed to persist that
    myShepherd.commitDBTransaction();

    // else {
    //     jout.put("error", "unknown command");
    // }
    
    //if all went well, then close SHepherd
    myShepherd.closeDBTransaction();
    response.setContentType("text/plain");
    out.println(jout.toString());
    out.flush();
    out.close();
  }


  private void errorAndClose(String msg, HttpServletResponse response, Shepherd myShepherd) {
    jout.put("error", msg);
    out.println(jout);
    out.close();
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
  }



}

