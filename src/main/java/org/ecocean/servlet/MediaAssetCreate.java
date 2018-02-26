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
        String uploadTmpDir = CommonConfiguration.getUploadTmpDir(context);
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
                }
            }
        }

        JSONObject js = new JSONObject();
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
            }
            rtn.put("withoutSet", jmas);
        }

        rtn.put("success", true);
        return rtn;
    }

}
  
  
