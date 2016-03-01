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

import java.io.*;

public class MediaAssetCreate extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }



    //this is a little hacky, but it is a way for the browser/client to request a MediaAssetSet with which to associate MediaAssets
    //  i guess we should *enforce* (require) this to have some sort of sanity around preventing backdoors to overwriting MediaAssets or whatever
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
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

{
    "MediaAssetCreate" : [
        {
            "assetStoreId" : 4,
            "assets" : [
                { "bucket" : "A", "key" : "B" },
                { "bucket" : "Y", "key" : "Z" },
....
            ]
        },
.... (other types) ...
    ]

}
*/
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String context="context0";
        //context=ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        //set up for response
        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();

        JSONObject j = jsonFromRequest(request);

        String batchId = Util.generateUUID();

        ArrayList<MediaAsset> mas = createMediaAssets(j.optJSONArray("MediaAssetCreate"), batchId, myShepherd);

        JSONArray res = new JSONArray();
        for (MediaAsset ma : mas) {
            res.put(ma.toString());
            res.put(ma.getParameters().toString());
            res.put(ma.webURL());
        }
        j.put("results", res);

        out.println(j.toString());
        out.close();
    }


    //TODO could also return failures? errors?
    private ArrayList<MediaAsset> createMediaAssets(JSONArray jarr, String batchId, Shepherd myShepherd) throws IOException {
        ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();
        if (jarr == null) return mas;

        for (int i = 0 ; i < jarr.length() ; i++) {
            JSONObject st = jarr.optJSONObject(i);
            if (st == null) continue;
            int storeId = st.optInt("assetStoreId");
            if (storeId < 1) {
                System.out.println("WARNING: createMediaAssets() - no assetStoreId on i=" + i);
                continue;
            }
            AssetStore store = AssetStore.get(myShepherd, storeId);
////// TODO sanity/safety check that we are getting from an acceptable asset store and not just any
            if (store == null) {
                System.out.println("WARNING: createMediaAssets() - AssetStore.get() failed for assetStoreId=" + storeId + ", i=" + i);
                continue;
            }
            JSONArray assets = st.optJSONArray("assets");
            if ((assets == null) || (assets.length() < 1)) {
                System.out.println("WARNING: createMediaAssets() - assets array missing or empty for i=" + i);
                continue;
            }
//////////TODO handle other types of "sources" -- like file uploads (to server) -- for now we are assuming source is a **TEMPORARY** S3, so we must copy to a real location
if (!store.getType().equals(AssetStoreType.S3)) throw new IOException("Only S3 sources supported for now in createMediaAssets()");

//flukebook-dev-upload-tmp  solveig-has.png
            for (int j = 0 ; j < assets.length() ; j++) {
                JSONObject params = assets.optJSONObject(j);  //TODO sanitize?
                if (params == null) continue;
                MediaAsset sourceMA = store.create(params);
                AssetStore targetStore = AssetStore.get(myShepherd,6); ///TODO this is hard-coded to get an S3 on my dev

                File fakeFile = new File(params.get("key").toString());
                params = targetStore.createParameters(fakeFile); //really just use bucket here
                params.put("key", Util.hashDirectories(batchId, "/") + "/" + fakeFile.getName());
System.out.println(params.toString());

                MediaAsset targetMA = targetStore.create(params);
                sourceMA.copyAssetTo(targetMA);
System.out.println("batchId " + batchId + " created " + targetMA);
                mas.add(targetMA);
            }
        }
        return mas;
    }

    private JSONObject jsonFromRequest(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = request.getReader();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } finally {
            reader.close();
        }
//ParseException
        return new JSONObject(sb.toString());
    }

}
  
  
