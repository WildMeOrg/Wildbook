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

        //note: a null status will be considered throw-away, cuz we no doubt will get aborted uploads etc.  TODO cleanup of these with cronjob?
        MediaAssetSet maSet = new MediaAssetSet();
        myShepherd.beginDBTransaction();
        myShepherd.getPM().makePersistent(maSet);
        myShepherd.commitDBTransaction();

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
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.beginDBTransaction();
        //set up for response
        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();

        JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);
        JSONObject res = createMediaAssets(j.optJSONArray("MediaAssetCreate"), myShepherd);
        myShepherd.commitDBTransaction();
        out.println(res.toString());
        out.close();
    }


    //TODO could also return failures? errors?
    private JSONObject createMediaAssets(JSONArray jarr, Shepherd myShepherd) throws IOException {
        String context = myShepherd.getContext();
        JSONObject rtn = new JSONObject();
        if (jarr == null) return rtn;

//TODO handle other types of "sources" -- like file uploads (to server) -- for now we are assuming source is a **TEMPORARY** S3, so we must copy to a real location

        String s3key = CommonConfiguration.getProperty("s3upload_accessKeyId", context);
        if (s3key == null) throw new IOException("s3upload_ properties not set; no source AssetStore possible in createMediaAssets()");
        JSONObject s3j = new JSONObject();
        s3j.put("AWSAccessKeyId", s3key);
        s3j.put("AWSSecretAccessKey", CommonConfiguration.getProperty("s3upload_secretAccessKey", context));
        s3j.put("bucket", CommonConfiguration.getProperty("s3upload_bucket", context));
        AssetStoreConfig cfg = new AssetStoreConfig(s3j.toString());
System.out.println("source config -> " + cfg.toString());
        //note: sourceStore (and any MediaAssets created on it) should remain *temporary* and not be persisted!
        S3AssetStore sourceStore = new S3AssetStore("temporary upload s3", cfg, false);

        AssetStore targetStore = AssetStore.getDefault(myShepherd); //see below about disabled user-provided stores
        HashMap<String,MediaAssetSet> sets = new HashMap<String,MediaAssetSet>();

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

            String setId = st.optString("setId");
            if (setId == null) {
                System.out.println("WARNING createMediaAssets() setId is required; skipping");
                continue;
            }
            if (sets.get(setId) == null) {
                MediaAssetSet s = null;
                try {
                    s = ((MediaAssetSet) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(MediaAssetSet.class, setId), true)));
                } catch (Exception ex) { } //usually(?) not found :)
                if (s == null) {
                    System.out.println("WARNING createMediaAssets() could not find MediaAssetSet id=" + setId + "; skipping");
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
                JSONObject params = assets.optJSONObject(j);  //TODO sanitize
                if (params == null) continue;
                //key must begin with "SETID/" otherwise it fails security sanity check
                if (params.optString("key", "FAIL").indexOf(setId + "/") != 0) {
                    System.out.println("WARNING createMediaAssets() asset params=" + params.toString() + " failed key value for setId=" + setId + "; skipping");
                    continue;
                }
                MediaAsset sourceMA = sourceStore.create(params);

                File fakeFile = new File(params.get("key").toString());
                params = targetStore.createParameters(fakeFile); //really just use bucket here
                params.put("key", Util.hashDirectories(setId, "/") + "/" + fakeFile.getName());
System.out.println(i + ") params -> " + params.toString());

                MediaAsset targetMA = targetStore.create(params);
                boolean success = true;
                try {
                    // we cannot use sourceMA.copyAssetTo(targetMA) as that uses aws credentials of the source AssetStore; we are assuming the target
                    //   is stronger (since it is not the temporary store)
                    targetStore.copyAsset(sourceMA, targetMA);
                } catch (Exception ex) {
                    System.out.println("WARNING: MediaAssetCreate failed to copy " + sourceMA + " to " + targetMA + ": " + ex.toString());
                    success = false;
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
                    MediaAssetFactory.save(targetMA, myShepherd);
System.out.println("MediaAssetSet " + setId + " created " + targetMA);
                    sets.get(setId).addMediaAsset(targetMA);
                    sets.get(setId).setStatus("active");
                }
            }
        }

        JSONObject js = new JSONObject();
        for (MediaAssetSet s : sets.values()) {
            JSONArray jmas = new JSONArray();
            if ((s.getMediaAssets() != null) && (s.getMediaAssets().size() > 0)) {
                for (MediaAsset ma : s.getMediaAssets()) {
                    JSONObject jma = new JSONObject();
                    jma.put("id", ma.getId());
                    jma.put("_debug", ma.toString());
                    jma.put("_params", ma.getParameters().toString());
                    jma.put("_url", ma.webURL());
                    jmas.put(jma);
                }
            }
            if (jmas.length() > 0) js.put(s.getId(), jmas);
        }
        rtn.put("sets", js);
        rtn.put("success", true);
        return rtn;
    }

}
  
  
