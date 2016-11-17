/*
 * This file is a part of Wildbook.
 * Copyright (C) 2015 WildMe
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Foobar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Wildbook.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.ecocean.media;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.Charset;
import java.util.HashMap;
import org.ecocean.Util;
import org.ecocean.Annotation;
import org.ecocean.YouTube;
//import org.ecocean.ImageProcessor;
import org.json.JSONObject;
import org.json.JSONException;
import org.apache.commons.lang3.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * YouTubeAssetStore references MediaAssets that reside on YouTube.
 * currently this is read-only but later could be writable with an API key if needed?
 *
 */
public class YouTubeAssetStore extends AssetStore {
    public YouTubeAssetStore(final String name) {
        this(null, name, null, false);
    }

    YouTubeAssetStore(final Integer id, final String name, final AssetStoreConfig config, final boolean writable) {
        super(id, name, AssetStoreType.URL, config, false);
    }

    public AssetStoreType getType() {
        return AssetStoreType.YouTube;
    }

    @Override
    public MediaAsset create(final JSONObject params) throws IllegalArgumentException {
        if (idFromParameters(params) == null) throw new IllegalArgumentException("no id parameter");
        return new MediaAsset(this, params);
    }

    @Override
    public boolean cacheLocal(MediaAsset ma, boolean force) {
        return false;
    }

    @Override
    public Path localPath(MediaAsset ma) {
        return null;
    }

    @Override
    public MediaAsset copyIn(final File file, final JSONObject params, final boolean createMediaAsset) throws IOException {
        throw new IOException(this.name + " is a read-only AssetStore");
    }

    @Override
    public void copyAsset(final MediaAsset fromMA, final MediaAsset toMA) throws IOException {
        throw new IOException("copyAsset() not available for YouTubeAssetStore");
    }

    @Override
    public void deleteFrom(final MediaAsset ma) {
        return;  //TODO exception?
    }

    @Override
    public URL webURL(final MediaAsset ma) {
        String id = idFromParameters(ma.getParameters());
        if (id == null) return null;
        try {
            return new URL("https://www.youtube.com/watch?v=" + id);
        } catch (java.net.MalformedURLException ex) {   //"should never happen"
            return null;
        }
    }

    @Override
    public String hashCode(JSONObject params) {
        return "YouTube" + idFromParameters(params);
    }


    @Override
    public JSONObject createParameters(File file, String grouping) {
        JSONObject p = new JSONObject();
        // will we even ever need this for a read-only system???  TODO so returning empty [or should be null?]
        return p;
    }

    @Override
    public String getFilename(MediaAsset ma) {
        return idFromParameters(ma.getParameters());  //meh?
    }

    private static String idFromParameters(JSONObject params) {
        if (params == null) return null;
        return params.optString("id", null);
    }

    //how should we thread in bkgd??? ... probably this should by synchrous, but stuff can bg when needed (e.g. extractMetadata)
    public static List<File> grab(MediaAsset ma) throws java.io.IOException {
        if (ma == null) return null;
        String id = idFromParameters(ma.getParameters());
        if (id == null) return null;
        //when fetched: (1) parse .json and set metadata.detailed; (2) create child assets (using default store!) for video & thumb
        File dir = Files.createTempDirectory("youtube_get_" + id + "_" + ma.getId() + "_").toFile();
System.out.println("YouTubeAssetStore.grab(" + ma + ") tempdir = " + dir);
        return YouTube.grab(id, dir);
    }


    //returns success (stuff grabbed and parsed)
    // 'wait' refers only for other process, if we need to do the grabbing, right now it will always wait for that
    public boolean grabAndParse(MediaAsset ma, boolean wait) throws IOException {
        boolean processing = ((ma.getDerivationMethod() != null) && ma.getDerivationMethod().optBoolean("_processing", false));
        int count = 0;
        int max = 100;
        while (processing && (count < max)) {
            System.out.println("INFO: YouTubeAssetStore(" + ma + ").grabAndParse, waiting with count=" + count);
            if (!wait) return false;
            try {
                Thread.sleep(2000);
            } catch (java.lang.InterruptedException ex) {}
            count++;
            if (count >= max) return false;
            processing = ((ma.getDerivationMethod() != null) && ma.getDerivationMethod().optBoolean("_processing", false));
        }
        //get here, we must not be processing, so lets do that!
        ma.addDerivationMethod("_processing", true);
        List<File> grabbed = this.grab(ma);
        for (File f : grabbed) {
System.out.println("- grabAndParse: " + f);
            if (f.getName().matches(".info.json$")) {
                List<String> lines = Files.readAllLines(f.toPath(), Charset.defaultCharset());
                JSONObject detailed = null;
                try {
                    detailed = new JSONObject(StringUtils.join(lines, ""));
                } catch (JSONException jex) {
                    System.out.println("ERROR: " + ma + " grabAndParse() failed to parse YouTube .info.json - " + jex.toString());
                }
                if (detailed != null) {
                    MediaAssetMetadata md = ma.getMetadata();
                }
            } else if (f.getName().matches(".jpg$")) {
                ///////// get image as _thumb child
            } else if (f.getName().matches(".mp4$")) {
                ///////// get video as _video child
            }
        }
        return true;
    }

    //this can be overridden if needed, but this should be fine for any AssetStore which can cacheLocal
    //  minimal means width/height/type (MetadataAttributes) only -- good for derived (i.e. exif-boring) images
    public MediaAssetMetadata extractMetadata(MediaAsset ma, boolean minimal) throws IOException {
        JSONObject data = new JSONObject();

        //we (attempt to) let the basic stuff finish synchronously, so we have a populated data chunk to save (hopefully)before the detailed one does
        try {
            data.put("basic", YouTube.simpleInfo(idFromParameters(ma.getParameters())));
        } catch (Exception ex) {
            System.out.println(ma + " failed simpleInfo(): " + ex.toString());
        }

        if (!minimal) {
            data.put("detailed", new JSONObject("{\"_processing\": true}"));
        }

        return new MediaAssetMetadata(data);
    }
}


