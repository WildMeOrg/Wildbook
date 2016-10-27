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

import org.ecocean.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.json.JSONArray;
import org.ecocean.media.*;
import java.util.ArrayList;
import java.util.HashMap;

import java.io.*;

public class EncounterCreate extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


    public void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ServletUtilities.doOptions(request, response);
    }

    //currently allow a simple GET version which only takes 1 or more imgSrc parameters
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost
        String[] srcs = request.getParameterValues("source");
        String species = request.getParameter("species");
        if ((srcs == null) || (species == null)) throw new IOException("invalid GET parameters");
        JSONArray jarr = new JSONArray();
        for (int i = 0 ; i < srcs.length ; i++) {
            JSONObject j = new JSONObject();
            j.put("imgSrc", srcs[i]);
            jarr.put(j);
        }
        JSONObject jin = new JSONObject();
        jin.put("species", species);
        jin.put("sources", jarr);
        PrintWriter out = response.getWriter();
        out.println(createEncounter(jin, request).toString());
    }


/*
    pass a json array of objects which must contain 'imgSrc' and 'species'(minimal required) and optional:
    - maLabels: json array of strings to add as labels to MediaAsset
*/
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost
        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();
        out.println(createEncounter(ServletUtilities.jsonFromHttpServletRequest(request), request).toString());
        out.close();
    }

    public JSONObject createEncounter(JSONObject jin, HttpServletRequest request) throws ServletException, IOException {
        JSONObject rtn = new JSONObject("{\"success\": false}");
        if (jin == null) {
            rtn.put("error", "empty input");
            return rtn;
        }
        JSONArray jsrcs = jin.optJSONArray("sources");
        String species = jin.optString("species", null);
        if ((jsrcs == null) || (jsrcs.length() < 1) || (species == null)) {
            rtn.put("error", "invalid input for sources or species");
            return rtn;
        }

        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("EncounterCreate.class");
        myShepherd.beginDBTransaction();

        AssetStore.init(AssetStoreFactory.getStores(myShepherd));
        if ((AssetStore.getStores() == null) || (AssetStore.getStores().size() < 1)) {
            throw new IOException("no AssetStores found");
        }
        AssetStore urlStore = null;
        for (AssetStore st : AssetStore.getStores().values()) {
            if (st instanceof URLAssetStore) {
                urlStore = (URLAssetStore)st;
                break;
            }
        }
        if (urlStore == null) throw new IOException("no URLAssetStore configured");

        JSONArray jmas = new JSONArray();
        JSONArray janns = new JSONArray();
        ArrayList<Annotation> anns = new ArrayList<Annotation>();
        for (int i = 0 ; i < jsrcs.length() ; i++) {
            JSONObject j = jsrcs.optJSONObject(i);
            if (j == null) continue;
            String imgSrc = j.optString("imgSrc", null);
            if (imgSrc == null) continue;
            JSONObject params = new JSONObject();
            params.put("url", imgSrc);
            MediaAsset ma = urlStore.create(params);
            ma.addLabel("_original");
            ma.addDerivationMethod("createEncounter", System.currentTimeMillis());
            if (j.optJSONArray("maLabels") != null) {
                JSONArray larr = j.getJSONArray("maLabels");
                for (int li = 0 ; li < larr.length() ; li++) {
                    if (larr.optString(li, null) != null) ma.addLabel(larr.getString(li));
                }
            }
/////////////// TODO add other things to MA ???
            ma.setAccessControl(request);
            ma.updateMetadata();
            Annotation ann = new Annotation(species, ma);
            MediaAssetFactory.save(ma, myShepherd);
            System.out.println("INFO: createEncounter() just created " + ma.toString());
            JSONObject jma = new JSONObject();
            jma.put("url", imgSrc);
            jma.put("id", ma.getId());
            if (ma.getMetadata() != null) jma.put("metadata", ma.getMetadata().getData());
            jmas.put(jma);
            anns.add(ann);
            janns.put(ann.getId());
        }
        if (anns.size() < 1) {
            rtn.put("error", "did not create any Annotations");
            return rtn;
        }
        if (jmas.length() > 0) rtn.put("assets", jmas);
        if (janns.length() > 0) rtn.put("annotations", janns);

        Encounter enc = new Encounter(anns);
        enc.setState(Encounter.STATE_MATCHING_ONLY);
        myShepherd.storeNewEncounter(enc, enc.getCatalogNumber());
        myShepherd.commitDBTransaction();
        rtn.put("encounterId", enc.getCatalogNumber());
        rtn.put("success", true);
        return rtn;
    }

/*
                    targetMA.updateMetadata();
                    targetMA.addLabel("_original");
                    targetMA.setAccessControl(request);
                    MediaAssetFactory.save(targetMA, myShepherd);
	            targetMA.updateStandardChildren(myShepherd);  //lets always do this (and can add flag to disable later if needed)
*/

}
  
  
