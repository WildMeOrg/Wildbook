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

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import org.ecocean.Shepherd;
import org.ecocean.Util;
import org.ecocean.Annotation;
//import org.ecocean.ImageProcessor;
import org.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * URLAssetStore references MediaAssets that reside at arbitrary URLs.
 *
 */
public class URLAssetStore extends AssetStore {
    //private static final String KEY_ROOT = "root";


    /**
     * Create a new local filesystem asset store.
     *
     * @param name Friendly name for the store.
     */
    public URLAssetStore(final String name) {
        this(null, name, null, false);
    }

    URLAssetStore(final Integer id, final String name, final AssetStoreConfig config, final boolean writable) {
        super(id, name, AssetStoreType.URL, config, false); //note: made this always non-writable... yes?
    }

    public AssetStoreType getType() {
        return AssetStoreType.URL;
    }

    @Override
    public MediaAsset create(final JSONObject params) throws IllegalArgumentException {
        if (urlFromParameters(params) == null) throw new IllegalArgumentException("no url parameter");
        return new MediaAsset(this, params);
    }
    //convenience
    public MediaAsset create(final URL url) {
        return create(url.toString());
    }
    public MediaAsset create(final String url) {
        JSONObject p = new JSONObject();
        p.put("url", url);
        return create(p);
    }

    @Override
    public boolean cacheLocal(MediaAsset ma, boolean force) {
        Path lpath = localPath(ma);
        if (lpath == null) return false;  //TODO or throw Exception?
        if (!force && Files.exists(lpath)) return true;  //we assume if we have it, then we should be cool
        System.out.println("URL.cacheLocal() trying to write to " + lpath);
        String url = urlFromParameters(ma.getParameters());
        try {
            fetchFileFromURL(new URL(url), lpath.toFile());
        } catch (Exception ioe) {
            System.out.println("WARN: cacheLocal got " + ioe.toString() + " while fetching " + url + " via " + ma.toString());
            return false;
        }
        return true;
    }

    @Override
    public Path localPath(MediaAsset ma) {
        JSONObject params = ma.getParameters();
        String url = urlFromParameters(params);
        if (url == null) return null;
        String suffix = "";
        int i = url.lastIndexOf("/");
        if (i > -1) suffix = url.substring(i + 1);
        return Paths.get("/tmp", hashCode(params) + suffix);
    }

    @Override
    public MediaAsset copyIn(final File file, final JSONObject params, final boolean createMediaAsset) throws IOException {
        throw new IOException(this.name + " is a read-only AssetStore");
        //if (!this.writable) throw new IOException(this.name + " is a read-only AssetStore");
    }

    @Override
    public void copyAsset(final MediaAsset fromMA, final MediaAsset toMA) throws IOException {
        throw new IOException("oops, URL.copyAsset() still not implemented. :/");  //TODO
    }

    @Override
    public void deleteFrom(final MediaAsset ma) {
        return;  //TODO exception?
        //if (!this.contains(ma)) return;
        //if (!this.writable) return;
    }

    @Override
    public URL webURL(final MediaAsset ma) {
        if (ma == null) return null;
        String url = urlFromParameters(ma.getParameters());
        if (url == null) return null;
        try {
            URL u = new URL(url);
            return u;
        } catch (MalformedURLException e) {
            System.out.println("WARNING: " + url + " generated " + e.toString());
            return null;
        }
    }

    @Override
    public String hashCode(JSONObject params) {
        String url = urlFromParameters(params);
        if (url == null) return null;
        return URLAssetStore.hexStringSHA256(url);
    }


    @Override
    public JSONObject createParameters(File file, String grouping) {
        JSONObject p = new JSONObject();
        // will we even ever need this for a read-only system???  TODO so returning empty [or should be null?]
        return p;
    }

    @Override
    public String getFilename(MediaAsset ma) {
        URL u = this.webURL(ma);
        if (u == null) return null;
        return u.getPath();  // note this will return "/foo/bar/whatever.jpg" for example, but meh good enough?
    }


    private String urlFromParameters(JSONObject params) {
        if (params == null) return null;
        return params.optString("url");
    }

    public static void fetchFileFromURL(URL srcUrl, File targetFile) throws IOException {
        InputStream is = srcUrl.openStream();
        OutputStream os = new FileOutputStream(targetFile);
        byte[] b = new byte[2048];
        int length;
        while ((length = is.read(b)) != -1) {
            os.write(b, 0, length);
        }
        is.close();
        os.close();
    }


    //this finds "a" (first? one?) URLAssetStore if we have any.  (null if not)
    public static URLAssetStore find(Shepherd myShepherd) {
        AssetStore.init(AssetStoreFactory.getStores(myShepherd));
        if ((AssetStore.getStores() == null) || (AssetStore.getStores().size() < 1)) return null;
        for (AssetStore st : AssetStore.getStores().values()) {
            if (st instanceof URLAssetStore) return (URLAssetStore)st;
        }
        return null;
    }

}


