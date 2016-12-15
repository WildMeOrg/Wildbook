/*
 * This file is a part of Wildbook.
 * Copyright (C) 2016 WildMe
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

import twitter4j.Status;
import java.io.File;
import java.nio.file.Path;
import java.net.URL;
import java.io.IOException;
import org.json.JSONObject;
import org.ecocean.TwitterUtil;

/*
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.Charset;
import java.util.HashMap;
import org.ecocean.Util;
import org.ecocean.Shepherd;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.Annotation;
import org.ecocean.Twitter;
//import org.ecocean.ImageProcessor;
import org.json.JSONException;
import org.apache.commons.lang3.StringUtils;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
*/


/**
 * TwitterAssetStore references MediaAssets that reside on Twitter; tweets and their media
 * currently this is read-only but later could be writable with an API key if needed?
 *
 */
public class TwitterAssetStore extends AssetStore {
    public TwitterAssetStore(final String name) {
        this(null, name, null, false);
    }

    TwitterAssetStore(final Integer id, final String name, final AssetStoreConfig config, final boolean writable) {
        super(id, name, AssetStoreType.Twitter, config, false);
    }

    public AssetStoreType getType() {
        return AssetStoreType.Twitter;
    }

    //note: convention is a param.type of null (not set) means type is a tweet... otherwise must specify (e.g. "media")
    @Override
    public MediaAsset create(final JSONObject params) throws IllegalArgumentException {
        if (idFromParameters(params) == null) throw new IllegalArgumentException("no id parameter");
        return new MediaAsset(this, params);
    }
    //convenience
    public MediaAsset create(final String tweetId) {
        JSONObject p = new JSONObject();
        p.put("id", tweetId);
        return create(p);
    }
    public MediaAsset create(final String id, final String type) {
        JSONObject p = new JSONObject();
        p.put("id", id);
        p.put("type", type);
        return create(p);
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
        throw new IOException("copyAsset() not available for TwitterAssetStore");
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
//TODO fix this to use actual urls derived from json that is (hopefully) in metadata
            return new URL("https://www.twitter.com/statuses/" + id);  //guess this only works for tweets, not media?
        } catch (java.net.MalformedURLException ex) {   //"should never happen"
            return null;
        }
    }

    @Override
    public String hashCode(JSONObject params) {
        return "Twitter" + idFromParameters(params);
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
    private static Long longIdFromParameters(JSONObject params) {
        String id = idFromParameters(params);
        if (id == null) return null;
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException ex) {
            System.out.println("TwitterStore.longIdFromParameters() failed to parse '" + id + "': " + ex.toString());
            return null;
        }
    }

/*
    //most likely you want grabAndParse() really.
    //  how should we thread in bkgd??? ... probably this should by synchrous, but stuff can bg when needed (e.g. extractMetadata)
    public static List<File> grab(MediaAsset ma) throws java.io.IOException {
        if (ma == null) return null;
        String id = idFromParameters(ma.getParameters());
        if (id == null) return null;
        //when fetched: (1) parse .json and set metadata.detailed; (2) create child assets (using default store!) for video & thumb
        File dir = Files.createTempDirectory("youtube_get_" + id + "_" + ma.getId() + "_").toFile();
System.out.println("TwitterAssetStore.grab(" + ma + ") tempdir = " + dir);
        return Twitter.grab(id, dir);
    }


    //returns success (stuff grabbed and parsed)
    // 'wait' refers only for other process, if we need to do the grabbing, right now it will always wait for that
    //    NOTE: wait is untested!  TODO
    public static boolean grabAndParse(Shepherd myShepherd, MediaAsset ma, boolean wait) throws IOException {
        boolean processing = ((ma.getDerivationMethod() != null) && ma.getDerivationMethod().optBoolean("_processing", false));
        int count = 0;
        int max = 100;
        while (processing && (count < max)) {
            System.out.println("INFO: TwitterAssetStore(" + ma + ").grabAndParse, waiting with count=" + count);
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
        List<File> grabbed = TwitterAssetStore.grab(ma);
        for (File f : grabbed) {
System.out.println("- [" + f.getName() + "] grabAndParse: " + f);
            if (f.getName().matches(".+.info.json$")) {
                List<String> lines = Files.readAllLines(f.toPath(), Charset.defaultCharset());
                JSONObject detailed = null;
                try {
                    detailed = new JSONObject(StringUtils.join(lines, ""));
                } catch (JSONException jex) {
                    System.out.println("ERROR: " + ma + " grabAndParse() failed to parse Twitter .info.json - " + jex.toString());
                }
//System.out.println("detailed -> " + detailed);
                if (detailed != null) {
                    JSONObject mj = new JSONObject();
                    if (ma.getMetadata() != null) {
                        ma.getMetadata().getDataAsString();  //HACK gets around weird dn caching issue. :(
                        mj = ma.getMetadata().getData();
                    }
                    mj.put("detailed", detailed);
                    ma.setMetadata(new MediaAssetMetadata(mj));
                }

            //note: these persist the children they make, fbow ?
            } else if (f.getName().matches(".+.jpg$")) {
                _createThumbChild(myShepherd, ma, f);
            } else if (f.getName().matches(".+.mp4$")) {
                _createVideoChild(myShepherd, ma, f);
            }
        }
        return true;
    }

    private static MediaAsset _createThumbChild(Shepherd myShepherd, MediaAsset parent, File f) throws java.io.IOException {
        if ((f == null) || !f.exists()) {
            System.out.println("WARNING: could not create thumb child for " + parent + ", file failure on " + f + "; skipping");
            return null;
        }
        AssetStore astore = AssetStore.getDefault(myShepherd);
        if (astore == null) {
            System.out.println("WARNING: could not create thumb child for " + parent + ", no valid asset store");
            return null;
        }
        JSONObject sp = astore.createParameters(f);
        sp.put("key", parent.getUUID() + "/" + f.getName());
        MediaAsset kid = new MediaAsset(astore, sp);
        kid.copyIn(f);
        kid.setParentId(parent.getId());
        kid.addLabel("_thumb");
        kid.updateMinimalMetadata();
        MediaAssetFactory.save(kid, myShepherd);
System.out.println("thumb child created: " + kid);
        return kid;
    }

    private static MediaAsset _createVideoChild(Shepherd myShepherd, MediaAsset parent, File f) throws java.io.IOException {
        if ((f == null) || !f.exists()) {
            System.out.println("WARNING: could not create video child for " + parent + ", file failure on " + f + "; skipping");
            return null;
        }
        AssetStore astore = AssetStore.getDefault(myShepherd);
        if (astore == null) {
            System.out.println("WARNING: could not create video child for " + parent + ", no valid asset store");
            return null;
        }
        JSONObject sp = astore.createParameters(f);
        sp.put("key", parent.getUUID() + "/" + f.getName());
        MediaAsset kid = new MediaAsset(astore, sp);
        kid.copyIn(f);
        kid.setParentId(parent.getId());
        kid.addLabel("_video");
        kid.updateMinimalMetadata();
        MediaAssetFactory.save(kid, myShepherd);
System.out.println("video child created: " + kid);
        return kid;
    }
*/

    //currently there really only is "minimal" for tweets: namely the json data from twitter
    //  this is keyed as "twitterRawJson"
    //  NOTE: this also assumes TwitterUtil.init(request) has been called
    public MediaAssetMetadata extractMetadata(MediaAsset ma, boolean minimal) throws IOException {
        JSONObject data = new JSONObject();
        Long id = longIdFromParameters(ma.getParameters());
        if (id == null) return new MediaAssetMetadata(data);
        Status tweet = TwitterUtil.getTweet(id);
        data.put("twitterRawJson", TwitterUtil.toJSONObject(tweet));
        return new MediaAssetMetadata(data);
    }


/*
    //this finds "a" (first? one?) YoutubeAssetStore if we have any.  (null if not)
    public static TwitterAssetStore find(Shepherd myShepherd) {
        AssetStore.init(AssetStoreFactory.getStores(myShepherd));
        if ((AssetStore.getStores() == null) || (AssetStore.getStores().size() < 1)) return null;
        for (AssetStore st : AssetStore.getStores().values()) {
            if (st instanceof TwitterAssetStore) return (TwitterAssetStore)st;
        }
        return null;
    }
*/

}


