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
import org.ecocean.ia.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.json.JSONArray;
import org.ecocean.media.*;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.net.URL;

import java.io.*;

public class MediaAssetCreate extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


    public void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ServletUtilities.doOptions(request, response);
    }

    //this is a little hacky, but it is a way for the browser/client to request a MediaAssetSet with which to associate MediaAssets
    //  i guess we should *enforce* (require) this to have some sort of sanity around preventing backdoors to overwriting MediaAssets or whatever
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost
        if (request.getParameter("requestMediaAssetSet") == null) throw new IOException("invalid GET parameters");

        String context = "context0";
        context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("MediaAssetCreate.class");

        //note: a null status will be considered throw-away, cuz we no doubt will get aborted uploads etc.  TODO cleanup of these with cronjob?
        MediaAssetSet maSet = new MediaAssetSet();
        myShepherd.beginDBTransaction();
        myShepherd.getPM().makePersistent(maSet);
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();

        response.setContentType("text/plain");
        JSONObject res = new JSONObject();
        res.put("mediaAssetSetId", maSet.getId());

        PrintWriter out = response.getWriter();
        out.println(res.toString());
    }


/*

NOTE: for now(?) we *require* a *valid* setId *and* that the asset *key be prefixed with it* -- this is for security purposes,
      so that users dont fish out files stored in temporary bucket as their own.  unlikely? yes. impossible? no.
{
    "MediaAssetCreate" : [
        {
            "setId" : "xxx",
            "assetStoreId" : 4,  ///DISABLED FOR NOW (TODO enable later if we need it?? how to handle security? need valid targets)
            "assets" : [
                { "filename" : "foo.jpg" },   //this should live in uploadTmpDir (see below) on local drive
                { "bucket" : "A", "key" : "xxx/B" },
                { "bucket" : "Y", "key" : "xxx/Z" },
....
            ]
        },
    "skipIA": false,  //default is do-not-skipIA, but you may want off when done later (e.g. match.jsp which does in CreateEncounter)
.... (other types) ...
    ]

}
*/
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost
        String context="context0";
        //context=ServletUtilities.getContext(request);

        //set up for response
        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();
        JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);

        if (!ReCAPTCHA.sessionIsHuman(request)) {
            System.out.println("WARNING: MediaAssetCreate failed sessionIsHuman()");
            response.setStatus(403);
            out.println("ERROR");
            out.close();
            return;
        }
        
        JSONObject res=new JSONObject();
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("MediaAssetCreate.class_nonum");
        myShepherd.beginDBTransaction();
        try{
          res = createMediaAssets(j.optJSONArray("MediaAssetCreate"), myShepherd, request);
          myShepherd.commitDBTransaction();
        }
        catch(IOException ioe){
          ioe.printStackTrace();
          myShepherd.rollbackDBTransaction();}
        finally{
          myShepherd.closeDBTransaction();
        }

        //this has to be after commit (so queue can find them from different thread), so we do a little work here
        if (!j.optBoolean("skipIA", false)) {
            JSONArray ids = res.optJSONArray("allMediaAssetIds");
            if ((ids != null) && (ids.length() > 0)) {
                myShepherd = new Shepherd(context);
                myShepherd.setAction("MediaAssetCreate.class_IA.intake");
                myShepherd.beginDBTransaction();
                List<MediaAsset> allMAs = new ArrayList<MediaAsset>();
                for (int i = 0 ; i < ids.length() ; i++) {
                    int id = ids.optInt(i, -1);
                    if (id < 0) continue;
                    MediaAsset ma = MediaAssetFactory.load(id, myShepherd);
                    if (ma != null) allMAs.add(ma);
                }
                if (allMAs.size() > 0) {
                    Task task = IA.intakeMediaAssets(myShepherd, allMAs);
                    myShepherd.storeNewTask(task);
                    res.put("IATaskId", task.getId());
                }
                myShepherd.commitDBTransaction();
                myShepherd.closeDBTransaction();
            }
        }

        out.println(res.toString());
        out.close();
    }


    //TODO could also return failures? errors?
    private JSONObject createMediaAssets(JSONArray jarr, Shepherd myShepherd, HttpServletRequest request) throws IOException {
        String context = myShepherd.getContext();
        JSONObject rtn = new JSONObject();
        if (jarr == null) return rtn;

/*
    NOTE: for now we dont allow user to set AssetStore, so we have some "hard-coded" ways to deal with:
    - S3 (via CommonConfiguration settings)
    - local (via CommonConfiguration tmp dir and "filename" value)
    - URL
*/
        String uploadTmpDir = CommonConfiguration.getUploadTmpDirForUser(request);
        S3AssetStore sourceStoreS3 = null;
        String s3key = CommonConfiguration.getProperty("s3upload_accessKeyId", context);
        if (s3key != null) {
            JSONObject s3j = new JSONObject();
            s3j.put("AWSAccessKeyId", s3key);
            s3j.put("AWSSecretAccessKey", CommonConfiguration.getProperty("s3upload_secretAccessKey", context));
            s3j.put("bucket", CommonConfiguration.getProperty("s3upload_bucket", context));
            AssetStoreConfig cfg = new AssetStoreConfig(s3j.toString());
System.out.println("source config -> " + cfg.toString());
            //note: sourceStore (and any MediaAssets created on it) should remain *temporary* and not be persisted!
            sourceStoreS3 = new S3AssetStore("temporary upload s3", cfg, false);
        }

        AssetStore targetStore = AssetStore.getDefault(myShepherd); //see below about disabled user-provided stores
        HashMap<String,MediaAssetSet> sets = new HashMap<String,MediaAssetSet>();
        ArrayList<MediaAsset> haveNoSet = new ArrayList<MediaAsset>();
        URLAssetStore urlStore = URLAssetStore.find(myShepherd);   //this is only needed if we get passed params for url
        JSONArray attachRtn = new JSONArray();

        for (int i = 0 ; i < jarr.length() ; i++) {
            JSONObject st = jarr.optJSONObject(i);
            if (st == null) continue;
/*  disabled now for security(?) reasons ... TODO fix this -- if we have a need???
            int storeId = st.optInt("assetStoreId");
            if (storeId < 1) {
                System.out.println("WARNING: createMediaAssets() - no assetStoreId on i=" + i);
                continue;
            }
            AssetStore store = AssetStore.get(myShepherd, storeId);
            if (store == null) {
                System.out.println("WARNING: createMediaAssets() - AssetStore.get() failed for assetStoreId=" + storeId + ", i=" + i);
                continue;
            }
*/

            String setId = st.optString("setId", null);
            //attempt to validate setId (if we have one)
            if ((setId != null) && (sets.get(setId) == null)) {
                MediaAssetSet s = null;
                try {
                    s = ((MediaAssetSet) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(MediaAssetSet.class, setId), true)));
                } catch (Exception ex) { } //usually(?) not found :)
                if (s == null) {
                    System.out.println("WARNING createMediaAssets() could not find MediaAssetSet id=" + setId + "; skipping");  //invalid, so fail!
                    continue;
                } else {
                    sets.put(setId, s);
                }
            }

            JSONArray assets = st.optJSONArray("assets");
            if ((assets == null) || (assets.length() < 1)) {
                System.out.println("WARNING: createMediaAssets() - assets array missing or empty for i=" + i);
                continue;
            }

            List<MediaAsset> mas = new ArrayList<MediaAsset>();
            for (int j = 0 ; j < assets.length() ; j++) {
                boolean success = true;
                MediaAsset targetMA = null;
                JSONObject params = assets.optJSONObject(j);  //TODO sanitize

                if (params == null) continue;

                //TODO we should probably also use the "SETID/" prefix (see below) standard for local too right?????
                String fname = params.optString("filename", null);
                String url = params.optString("url", null);
                String accessKey = params.optString("accessKey", null);  //kinda specialty use to validate certain anon-uploaded cases (e.g. match.jsp)
                if (fname != null) {  //this is local
                    if (fname.indexOf("..") > -1) continue;  //no hax0ring plz
                    File inFile = new File(uploadTmpDir, fname);
                    params = targetStore.createParameters(inFile);
                    if (accessKey != null) params.put("accessKey", accessKey);
                    targetMA = targetStore.create(params);
                    try {
                        targetMA.copyIn(inFile);
                    } catch (Exception ex) {
                        System.out.println("WARNING: MediaAssetCreate failed to copyIn " + inFile + " to " + targetMA + ": " + ex.toString());
                        success = false;
                    }

                } else if (url != null) {
                    if (urlStore == null) {
                        System.out.println("WARNING: MediaAssetCreate found no URLAssetStore; skipping url param " + url);
                    } else {
                        targetMA = urlStore.create(params);
                    }

                } else {  //if we fall thru, then we are going to assume S3
                    if (sourceStoreS3 == null) throw new IOException("s3upload_ properties not set; no source S3 AssetStore possible in createMediaAssets()");
                    //key must begin with "SETID/" otherwise it fails security sanity check
                    if ((setId != null) && (params.optString("key", "FAIL").indexOf(setId + "/") != 0)) {
                        System.out.println("WARNING createMediaAssets() asset params=" + params.toString() + " failed key value for setId=" + setId + "; skipping");
                        continue;
                    }
                    MediaAsset sourceMA = sourceStoreS3.create(params);

                    File fakeFile = new File(params.get("key").toString());
                    params = targetStore.createParameters(fakeFile); //really just use bucket here
                    if (accessKey != null) params.put("accessKey", accessKey);
                    String dirId = setId;
                    if (dirId == null) dirId = Util.generateUUID();
                    params.put("key", Util.hashDirectories(dirId, "/") + "/" + fakeFile.getName());
System.out.println(i + ") params -> " + params.toString());
                    targetMA = targetStore.create(params);
                    try {
                        // we cannot use sourceMA.copyAssetTo(targetMA) as that uses aws credentials of the source AssetStore; we are assuming the target
                        //   is stronger (since it is not the temporary store)
                        targetStore.copyAsset(sourceMA, targetMA);
                    } catch (Exception ex) {
                        System.out.println("WARNING: MediaAssetCreate failed to copy " + sourceMA + " to " + targetMA + ": " + ex.toString());
                        success = false;
                    }
                }

                if (success) {
/*
   TODO  when annotation-building no longer needs dimensions, technically this Metadata building will not be required. however, we likely will need to
         eat the cost of s3 cacheLocal() anyway for the children creation.  however[2], we can likely just do it in the background.
         at least doing this now will avoid collision of it happening twice during form submission... ug yeah what about that?  ug, locking!

         update:  errrr, maybe not.  i think we *must* grab "real" (exif) metadata so we can get (primarily) date/time for image. :/
         but probably still could be done in the background....
*/
                    targetMA.validateSourceImage();
                    targetMA.updateMetadata();
                    targetMA.addLabel("_original");
                    targetMA.setAccessControl(request);
                    MediaAssetFactory.save(targetMA, myShepherd);
	            targetMA.updateStandardChildren(myShepherd);  //lets always do this (and can add flag to disable later if needed)
                    if (setId != null) {
System.out.println("MediaAssetSet " + setId + " created " + targetMA);
                        sets.get(setId).addMediaAsset(targetMA);
                        sets.get(setId).setStatus("active");
                    } else {
System.out.println("no MediaAssetSet; created " + targetMA);
                        haveNoSet.add(targetMA);
                    }
                    mas.add(targetMA);
                }

            }

            //this duplicates some of MediaAssetAttach, but lets us get done in one API call
            //  TODO we dont sanity-check *ownership* of the encounter.... :/
            JSONObject attEnc = st.optJSONObject("attachToEncounter");
            JSONObject attOcc = st.optJSONObject("attachToOccurrence");
            if (attEnc != null) {
                JSONObject artn = new JSONObject();
                Encounter enc = myShepherd.getEncounter(attEnc.optString("id", "__FAIL__"));
                if (enc != null) {
                    artn.put("id", enc.getCatalogNumber());
                    artn.put("type", "Encounter");
                    artn.put("assets", new JSONArray());
                    for (MediaAsset ema : mas) {
                        if (enc.hasTopLevelMediaAsset(ema.getId())) continue;
                        enc.addMediaAsset(ema);
                        artn.getJSONArray("assets").put(ema.getId());
                    }
                    System.out.println("MediaAssetCreate.attachToEncounter added " + artn.getJSONArray("assets").length() + " assets to Enc " + enc.getCatalogNumber());
                }
                attachRtn.put(artn);
            } else if (attOcc != null) {  //this requires a little extra, to make the enc, minimum is taxonomy
                String tax = attOcc.optString("taxonomy");
                JSONObject artn = new JSONObject();
                Occurrence occ = myShepherd.getOccurrence(attOcc.optString("id", "__FAIL__"));
                if ((tax == null) || (occ == null)) {
                    System.out.println("MediaAssetCreate.attachToOccurrence ERROR had invalid .taxonomy or bad id; skipping " + attOcc);
                } else {
                    ArrayList<Annotation> anns = new ArrayList<Annotation>();
                    for (MediaAsset ema : mas) {
                        Annotation ann = new Annotation(tax, ema);
                        anns.add(ann);
                        artn.getJSONArray("assets").put(ema.getId());
                    }
                    Encounter enc = new Encounter(anns);
                    enc.setTaxonomyFromString(tax);
                    enc.addComments("<p>created by MediaAssetCreate attaching to Occurrence " + occ.getOccurrenceID() + "</p>");
                    occ.addEncounter(enc);
                    artn.put("id", occ.getOccurrenceID());
                    artn.put("encounterId", enc.getCatalogNumber());
                    artn.put("type", "Occurrence");
                    artn.put("assets", new JSONArray());
                    myShepherd.getPM().makePersistent(enc);
                    System.out.println("MediaAssetCreate.attachToOccurrence added Enc " + enc.getCatalogNumber() + " to Occ " + occ.getOccurrenceID());
                }
                attachRtn.put(artn);
            }
        }

        if (attachRtn.length() > 0) rtn.put("attached", attachRtn);

        JSONObject js = new JSONObject();
        JSONArray allMAIds = new JSONArray();
        for (MediaAssetSet s : sets.values()) {
            JSONArray jmas = new JSONArray();
            if ((s.getMediaAssets() != null) && (s.getMediaAssets().size() > 0)) {
                for (MediaAsset ma : s.getMediaAssets()) {
                    JSONObject jma = new JSONObject();
                    try {
                        jma = Util.toggleJSONObject(ma.sanitizeJson(request, new org.datanucleus.api.rest.orgjson.JSONObject(), true));
                    } catch (Exception ex) {
                        System.out.println("WARNING: failed sanitizeJson on " + ma + ": " + ex.toString());
                    }
                    jma.put("_debug", ma.toString());
                    jma.put("_params", ma.getParameters().toString());
                    jmas.put(jma);
                    allMAIds.put(ma.getId());
                }
            }
            if (jmas.length() > 0) js.put(s.getId(), jmas);
        }
        rtn.put("sets", js);

        if (haveNoSet.size() > 0) {
            JSONArray jmas = new JSONArray();
            for (MediaAsset ma : haveNoSet) {
                JSONObject jma = new JSONObject();
                try {
                    jma = Util.toggleJSONObject(ma.sanitizeJson(request, new org.datanucleus.api.rest.orgjson.JSONObject(), true));
                } catch (Exception ex) {
                    System.out.println("WARNING: failed sanitizeJson on " + ma + ": " + ex.toString());
                }
                //"url" does not get set in sanitizeJson cuz it uses a new Shepherd object, sigh.  so:
                URL u = ma.safeURL(myShepherd);
                if (u != null) jma.put("url", u.toString());
                jma.put("_debug", ma.toString());
                jma.put("_params", ma.getParameters().toString());
                jmas.put(jma);
                allMAIds.put(ma.getId());
            }
            rtn.put("withoutSet", jmas);
        }

        rtn.put("allMediaAssetIds", allMAIds);
        rtn.put("success", true);
        return rtn;
    }

}
  
  
