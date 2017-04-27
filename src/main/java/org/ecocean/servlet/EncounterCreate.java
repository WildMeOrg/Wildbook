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
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
//import java.util.HashMap;

import java.io.*;

public class EncounterCreate extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


    public void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ServletUtilities.doOptions(request, response);
    }

/*  NOTE disabling for now (until needed) since we need to deal with accessKey stuff etc
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
*/


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


/*
NOTE: right now this is not very general-purpose; only really used for match.jsp creation.  TODO make it more general!
      toward this end, we should(?) consider anonymous use to be from match.jsp (thus force some things accordingly)... maybe?
*/
    public JSONObject createEncounter(JSONObject jin, HttpServletRequest request) throws ServletException, IOException {
        JSONObject rtn = new JSONObject("{\"success\": false}");
        if (jin == null) {
            rtn.put("error", "empty input");
            return rtn;
        }

        if ((jin.optJSONArray("encounters") != null) && (jin.optJSONArray("tasks") != null) && (jin.optString("accessKey", null) != null)) {
            return sendEmail(request, jin.getJSONArray("encounters"), jin.getJSONArray("tasks"), jin.getString("accessKey"));
        }

        JSONArray jsrcs = jin.optJSONArray("sources");
        String species = jin.optString("species", null);
        long dateMilliseconds = jin.optLong("dateMilliseconds", -1);
        String dateString = jin.optString("dateString", null);
        String locationString = jin.optString("locationString", "");
        String email = jin.optString("email", null);
        //NOTE: technically we could not require date and rely on exif data -- but we simply cant be sure it is there/correct
        if ((jsrcs == null) || (jsrcs.length() < 1) || (species == null) || ((dateMilliseconds < 0) && (dateString == null)) ||
                !Util.isValidEmailAddress(email)) {
            rtn.put("error", "invalid input for sources or species or date or email");
            return rtn;
        }

        boolean anonymousUser = AccessControl.isAnonymous(request);
        if (anonymousUser) request.getSession().setAttribute("USER_EMAIL", email);  //save this for subsequent usage (if needed, e.g. match.jsp)

        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("EncounterCreate.class");
        myShepherd.beginDBTransaction();

        URLAssetStore urlStore = URLAssetStore.find(myShepherd);  //only needed for url-sourced images really
/*
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
*/

        JSONArray jmas = new JSONArray();
        JSONArray janns = new JSONArray();
        ArrayList<Annotation> anns = new ArrayList<Annotation>();
        for (int i = 0 ; i < jsrcs.length() ; i++) {
            JSONObject j = jsrcs.optJSONObject(i);
            if (j == null) continue;

            //currently support two types of sources: a url (which creates a MediaAsset) and (existing) asset id
            String imgSrc = j.optString("imgSrc", null);
            int maId = j.optInt("mediaAssetId", -1);

            Annotation ann = null;
            MediaAsset ma = null;
            if (imgSrc != null) {
                if (urlStore == null) throw new IOException("EncounterCreate failed: no URLAssetStore");
                JSONObject params = new JSONObject();
                params.put("url", imgSrc);
                ma = urlStore.create(params);
                ma.addLabel("_original");
                ma.addDerivationMethod("createEncounter", System.currentTimeMillis());
/////////////// TODO add other things to MA ???
                ma.setAccessControl(request);
                ma.updateMetadata();
                ann = new Annotation(species, ma);
                System.out.println("INFO: createEncounter() just created " + ma.toString());
            } else if (maId > 0) {
                ma = MediaAssetFactory.load(maId, myShepherd);
                if (ma == null) continue;  //nope
                if (!allowedAccess(ma, jin, request, myShepherd)) {
                    System.out.println("WARNING: createEncounter() invalid access to " + ma + "; skipping");
                    continue;
                }
                ann = new Annotation(species, ma);
                System.out.println("INFO: createEncounter() just created " + ann.toString());
            } else {
                continue;  //unknown source
            }
            if (j.optJSONArray("maLabels") != null) {
                JSONArray larr = j.getJSONArray("maLabels");
                for (int li = 0 ; li < larr.length() ; li++) {
                    if (larr.optString(li, null) != null) ma.addLabel(larr.getString(li));
                }
            }
            MediaAssetFactory.save(ma, myShepherd);
            JSONObject jma = new JSONObject();
            jma.put("url", ma.webURL());
            jma.put("id", ma.getId());
            if (ma.getMetadata() != null) jma.put("metadata", ma.getMetadata().getData());
            jmas.put(jma);
            anns.add(ann);
            janns.put(ann.getId());
        }
        if (anns.size() < 1) {
            rtn.put("error", "did not create any Annotations");
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            return rtn;
        }
        if (jmas.length() > 0) rtn.put("assets", jmas);
        if (janns.length() > 0) rtn.put("annotations", janns);

        String accessKey = jin.optString("accessKey", "_NO_KEY_"); //should always be set!
        Encounter enc = new Encounter(anns);
        if (anonymousUser) {
            //TODO do we do some kinda sanity check on accessKey?  i think we just trust/use whatever is here and subsequent access needs this value.
            enc.setSubmitterEmail(email);
            enc.setMatchingOnly();
        } else {
            //(for logged in user only) allow option to forcibly set matchingOnly=false to toggle (default is matching only)
            if (jin.optBoolean("matchingOnly", true)) enc.setMatchingOnly();
        }
        enc.setAccessControl(request);

        if (dateMilliseconds > 0) {
            enc.setDateInMilliseconds(dateMilliseconds);
        } else {
            try {  //like:  2016 or 2016-02 or 2016-02-03 or 2016-02-03 04:05
                enc.setYear(Integer.parseInt(dateString.substring(0,4)));
                if (dateString.length() > 6) enc.setMonth(Integer.parseInt(dateString.substring(5,7)));
                if (dateString.length() > 9) enc.setDay(Integer.parseInt(dateString.substring(8,10)));
                if (dateString.length() > 15) {
                    enc.setHour(Integer.parseInt(dateString.substring(11,13)));
                    enc.setMinutes(dateString.substring(14,16));
                }
                if (!locationString.equals("")) enc.setVerbatimLocality(locationString);
            } catch (java.lang.NumberFormatException nfe) {
                System.out.println("ERROR: could not parse date from [" + dateString + "]: " + nfe.toString());
            }
        }
        System.out.println("INFO: created " + enc.toString() + " for " + email + " with key " + accessKey);
        myShepherd.storeNewEncounter(enc, enc.getCatalogNumber());
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
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

    //allows for future expansion?
    private static boolean allowedAccess(MediaAsset ma, JSONObject jobj, HttpServletRequest req, Shepherd myShepherd) {
        String accessKey = jobj.optString("accessKey", null);
        if (accessKey == null) return false;  //only method available presently
        JSONObject p = ma.getParameters();
        if (p == null) return false;
        return accessKey.equals(p.optString("accessKey", null));
    }

    private static JSONObject sendEmail(HttpServletRequest request, JSONArray encIds, JSONArray taskIds, String accessKey) {
        JSONObject rtn = new JSONObject("{\"success\": false}");
        String context = ServletUtilities.getContext(request);

        String linkPrefix = null;
        try {
            linkPrefix = CommonConfiguration.getServerURL(request, request.getContextPath());
        } catch (java.net.URISyntaxException ex) {  //"should never happen"
            rtn.put("error", "bad URL configuration: " + ex.toString());

            return rtn;
        }

        String encLinks = "";
        String encLinksHtml = "";
        String userEmail = null;
        int ecount = 0;
        JSONObject aj = new JSONObject();  //just for allowedAccess call
        aj.put("accessKey", accessKey);

        /*
            make a big assumption here -- same # of encs as tasks, and encs map one-to-one to tasks.
            and encs have only one asset each.  while this is likely all true for now,
            this may not always be true. :( but this saves us some work (for now)                           TODO fix?
        */
        String[] fname = new String[encIds.length()];

        Shepherd tShepherd = new Shepherd(context);
        tShepherd.setAction("EncounterCreate.sendEmail.class");
        tShepherd.beginDBTransaction();
        try{
          for (int i = 0 ; i < encIds.length() ; i++) {
              Encounter enc = tShepherd.getEncounter(encIds.optString(i, "_FAIL_"));
              if (enc == null) continue;
              if ((enc.getMedia() == null) || (enc.getMedia().size() < 1)) continue;
              boolean allowed = false;
              for (MediaAsset ma : enc.getMedia()) {
                  fname[i] = ma.getFilename();
                  if (fname[i] != null) {  //only take final part to drop (pseudo)dirs ... maybe we should do this via File or something, meh.
                      int s = fname[i].lastIndexOf("/");
                      if (s > -1) fname[i] = fname[i].substring(s + 1);
                  }
                  if (allowedAccess(ma, aj, request, tShepherd)) allowed = true;  //this means we only need key to *one* of the assets.  good?
              }
              if (!allowed) continue;
              //ok, this is really us!
              if (userEmail == null) {
                  userEmail = enc.getSubmitterEmail();
              } else if (!userEmail.equals(enc.getSubmitterEmail())) {
                  rtn.put("error", "inconsistent encounter email addresses");
                  return rtn;
              }
              ecount++;
              encLinks += " - " + linkPrefix + "/encounters/encounter.jsp?number=" + enc.getCatalogNumber() + "&accessKey=" + accessKey + "\n";
              encLinksHtml += "<li><a href=\"" + linkPrefix + "/encounters/encounter.jsp?number=" + enc.getCatalogNumber() + "&accessKey=" + accessKey + "\">Encounter " + ecount + "</a></li>\n";
          }
        }
        catch(Exception e){
          e.printStackTrace();
        }
        finally{
          tShepherd.rollbackDBTransaction();
          tShepherd.closeDBTransaction();
        }
        if (ecount < 1) {
            rtn.put("error", "no valid encounters");
            //myShepherd.rollbackDBTransaction();
            return rtn;
        }

        String taskLinks = "";
        String taskLinksHtml = "";
        int tcount = 0;
        for (int i = 0 ; i < taskIds.length() ; i++) {
            String id = taskIds.optString(i, null);
            if (id == null) continue;
            //TODO just trusting these are real.  we could verify... but do we need to?
            tcount++;
            taskLinks += " - " + linkPrefix + "/encounters/matchResults.jsp?taskId=" + id + "&accessKey=" + accessKey + "\n";
            taskLinksHtml += "<li><a title=\"" + id + "\" href=\"" + linkPrefix + "/encounters/matchResults.jsp?taskId=" + id + "&accessKey=" + accessKey + "\">(" + tcount + ") " + ((i >= fname.length) ? "Result " + (i+1) : fname[i]) + "</a></li>\n";
        }
/*  we are going to allow this now -- so they at least get *something* ... i.e. if encounters get made
        if (tcount < 1) {
            rtn.put("error", "no valid identification tasks");
            //myShepherd.rollbackDBTransaction();
            return rtn;
        }
*/
        if (tcount < 1) taskLinks = "<p style=\"font-size: 1.3em; padding: 10px 20px; color: #F00;\">There were errors during processing.  Please forward us this email for assistance.</p>";

        Map<String, String> tagMap = NotificationMailer.createBasicTagMap(request, (Encounter)null);
        tagMap.put("@ACCESSKEY@", accessKey);
        tagMap.put("@ENCLINKS@", encLinks);
        tagMap.put("@TASKLINKS@", taskLinks);
        tagMap.put("@ENCLINKSHTML@", encLinksHtml);
        tagMap.put("@TASKLINKSHTML@", taskLinksHtml);
        NotificationMailer mailer = new NotificationMailer(context, null, userEmail, "encountersCreated", tagMap);
        ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();
        es.execute(mailer);
        es.shutdown();
        //myShepherd.rollbackDBTransaction();
        rtn.put("success", true);
        rtn.put("message", "email sent successfully");
        return rtn;
    }

}
  
  
