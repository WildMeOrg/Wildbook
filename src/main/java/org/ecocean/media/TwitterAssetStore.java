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

import java.util.regex.*;

import twitter4j.Status;
import java.io.File;
import java.nio.file.Path;
import java.net.URL;
import java.io.IOException;
import org.json.JSONObject;
import org.json.JSONArray;
import org.ecocean.TwitterUtil;
import org.ecocean.media.MediaAssetMetadata;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import org.ecocean.Shepherd;

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
    private static final String METADATA_KEY_RAWJSON = "twitterRawJson";

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
    public MediaAsset create(final long tweetId) {
        return create(Long.toString(tweetId));
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
        if (id == null) return null;
        String u = null;
        if (ma.hasLabel("_original")) {
            String id = idFromParameters(ma.getParameters());
            if (id == null) return null;
            u = "https://www.twitter.com/statuses/" + id;
        } else if (ma.hasLabel("_entity")) {
            //could also use params.type ("photo") if need be?
            if (ma.getParameters() != null) u = ma.getParameters().optString("mediaURL", null);
        }
        if (u == null) return null;  //dunno/how!
        try {
            return new URL(u);
        } catch (java.net.MalformedURLException ex) {   //"should never happen"
            System.out.println("TwitterAssetStore.webURL() on " + ma + ": " + ex.toString());
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

/*   TODO not really sure what to do with updateChild() and friends here..... hmmmm...
     presently opting to create these manually via entitiesAsMediaAssets() (which will utilize .parent property)

    public List<String> allChildTypes() {
        return Arrays.asList(new String[]{"entity"});
    }
    //awkwardly named subset of the above which will be used to determine which should be derived with updateStandardChildren()
    public List<String> standardChildTypes() {
        return Arrays.asList(new String[]{"entity"});
    }
    public MediaAsset updateChild(MediaAsset parent, String type, HashMap<String,Object> opts) throws IOException {
    }
*/


/*  regarding media (etc) in tweets:  seems .extended_entities.media is the same as .entities.media ???  but extended (duh?) might have more details?
        https://dev.twitter.com/overview/api/entities-in-twitter-objects
        https://dev.twitter.com/overview/api/entities
*/

///// note: might also want to walk .entities.urls -- https://dev.twitter.com/overview/api/entities-in-twitter-objects#urls
    public static List<MediaAsset> entitiesAsMediaAssets(MediaAsset ma) {
        JSONObject raw = getRawJSONObject(ma);
        // System.out.println(raw.toString());
        AssetStore store = ma.getStore();
        if (raw == null) return null;
        if ((raw.optJSONObject("extended_entities") == null) || (raw.getJSONObject("extended_entities").optJSONArray("media") == null)) return null;
        List<MediaAsset> mas = new ArrayList<MediaAsset>();
        JSONArray jarr = raw.getJSONObject("extended_entities").getJSONArray("media");
        for (int i = 0 ; i < jarr.length() ; i++) {
            JSONObject p = jarr.optJSONObject(i);
            if (p == null) continue;
            p.put("id", p.optString("id_str", null));  //squash the long id at "id" with string
            MediaAsset kid = store.create(p);
            kid.addLabel("_entity");
            //TODO here
            setEntityMetadata(kid);
            kid.getMetadata().getDataAsString(); //TODO no idea what this does -MF
            kid.setParentId(ma.getId());
            //derivationMethods?  metadata? (of image) etc.... ??
            mas.add(kid);
        }
        return mas;
    }

    public static List<MediaAsset> entitiesAsMediaAssetsGsonObj(MediaAsset ma, Long parentTweetId) {
        JSONObject raw = getRawJSONObject(ma);
        // System.out.println(raw.toString());
        AssetStore store = ma.getStore();
        if (raw == null) return null;
        if ((raw.optJSONArray("extendedMediaEntities") == null)){
          System.out.println("aw.optJSONArray('extendedMediaEntities') is null");
          return null;
        }
        List<MediaAsset> mas = new ArrayList<MediaAsset>();
        JSONArray jarr = raw.getJSONArray("extendedMediaEntities");
        for (int i = 0 ; i < jarr.length() ; i++) {
            JSONObject p = jarr.optJSONObject(i);
            if (p == null) continue;
            p.put("id", p.optString("id", null));  //squash the long id at "id" with string
            MediaAsset kid = store.create(p);
            kid.addLabel("_entity");
            kid.addLabel("_parentTweet:" + Long.toString(parentTweetId));
            setEntityMetadata(kid);
            kid.getMetadata().getDataAsString(); //TODO no idea what this does -MF
            kid.setParentId(ma.getId());
            //derivationMethods?  metadata? (of image) etc.... ??
            mas.add(kid);
            System.out.println("i is: " + Integer.toString(i));
            JSONObject test = TwitterUtil.toJSONObject(kid);
            System.out.println(TwitterUtil.toJSONString(test));
        }
        return mas;
    }

    //this assumes we already set metadata
    public static JSONObject getRawJSONObject(MediaAsset ma) {
        if (ma == null) return null;
        MediaAssetMetadata md = ma.getMetadata();
        if ((md == null) || (md.getData() == null) || (md.getDataAsString() == null)) return null;
        return md.getData().optJSONObject(METADATA_KEY_RAWJSON);
    }

    //currently there really only is "minimal" for tweets: namely the json data from twitter
    //  this is keyed as "twitterRawJson"
    //  NOTE: this also assumes TwitterUtil.init(request) has been called
    public MediaAssetMetadata extractMetadata(MediaAsset ma, boolean minimal) throws IOException {
        ArrayList<String> labels =  ma.getLabels();
        System.out.println("labels:");
        System.out.println(labels);
        try{
          Long parentTweetId = getParentTweetIdFromLabels(labels);
        } catch(Exception e){
          e.printStackTrace();
        }

        JSONObject data = new JSONObject();
        Long id = longIdFromParameters(ma.getParameters());
        if (id == null) return new MediaAssetMetadata(data);
        Status tweet = null;
        if(labels.contains("_entity")){
          try{
            Long parentTweetIdFromEntity = getParentTweetIdFromLabels(labels);
            tweet = TwitterUtil.getTweet(parentTweetIdFromEntity);
          } catch(Exception e){
            e.printStackTrace();
          }
        } else{
          tweet = TwitterUtil.getTweet(id);
        }
        if(tweet != null){
          data.put(METADATA_KEY_RAWJSON, TwitterUtil.toJSONObject(tweet));
        }
        return new MediaAssetMetadata(data);
    }

    public Long getParentTweetIdFromLabels(ArrayList<String> labels) throws Exception{
      Long returnVal = null;
      for(int i = 0; i<labels.size(); i++){
        try{
          returnVal = parseParentTweetId(labels.get(i));
        } catch(Exception e){
          continue;
        }
      }
      if(returnVal == null){
        throw new Exception("returnVal in getParentTweetIdFromLabels is null");
      } else{
        return returnVal;
      }
    }

    public Long parseParentTweetId(String label) throws Exception{
      Long returnVal = null;
      String PATTERN = "_parentTweet:(\\d+)"; //doesn't seem as robust as
      Pattern pattern = Pattern.compile(PATTERN);
      Matcher matcher = pattern.matcher(label);
      if(matcher.matches()){
        returnVal = Long.parseLong(matcher.group(1));
      }
      if(returnVal == null){
        throw new Exception("returnVal in parseParentTweetId is null");
      } else{
        return returnVal;
      }
    }

    private static void setEntityMetadata(MediaAsset ma) {
        System.out.println("Hey mark. Got here.");
        System.out.println("MediaAssetId is: " + ma.toString());
        if (ma.getParameters() == null) return;
        JSONObject d = new JSONObject("{\"attributes\": {} }");
        if ((ma.getParameters().optJSONObject("sizes") != null) && (ma.getParameters().getJSONObject("sizes").optJSONObject("3") != null)) {
            d.getJSONObject("attributes").put("width", ma.getParameters().getJSONObject("sizes").getJSONObject("3").optDouble("width", 0));
            d.getJSONObject("attributes").put("height", ma.getParameters().getJSONObject("sizes").getJSONObject("3").optDouble("height", 0));
        }
	String mimeGuess = ma.getParameters().optString("type", "unknown");
	if (mimeGuess.equals("photo")) mimeGuess = "image";
	mimeGuess += "/";
	String url = ma.getParameters().optString("mediaURL", ".unknown");
	int i = url.lastIndexOf(".");
	String ext = "jpeg";
/*
	if (i > -1) {
		ext = url.substring(i);
	}
*/
	mimeGuess += ext;
	d.getJSONObject("attributes").put("contentType", mimeGuess);
        ma.setMetadata(new MediaAssetMetadata(d));
    }

    //this finds "a" (first? one?) TwitterAssetStore if we have any.  (null if not)
    public static TwitterAssetStore find(Shepherd myShepherd) {
        AssetStore.init(AssetStoreFactory.getStores(myShepherd));
        if ((AssetStore.getStores() == null) || (AssetStore.getStores().size() < 1)) return null;
        for (AssetStore st : AssetStore.getStores().values()) {
            if (st instanceof TwitterAssetStore) return (TwitterAssetStore)st;
        }
        return null;
    }


}
