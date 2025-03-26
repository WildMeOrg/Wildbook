// TODO: Deprecate and remove as part of twitter bot deprecate

package org.ecocean.media;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.ecocean.Shepherd;
import org.ecocean.TwitterUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import twitter4j.Status;

/*
 * NOTE TwitterAssetStore references MediaAssets that reside on Twitter; tweets and their media currently this is read-only but later could be writable
 * with an API key if needed?
 */
public class TwitterAssetStore extends AssetStore {
    private static final String METADATA_KEY_RAWJSON = "twitterRawJson";

    public TwitterAssetStore(final String name) {
        this(null, name, null, false);
    }

    TwitterAssetStore(final Integer id, final String name, final AssetStoreConfig config,
        final boolean writable) {
        super(id, name, AssetStoreType.Twitter, config, false);
    }

    public AssetStoreType getType() {
        return AssetStoreType.Twitter;
    }

    // note: convention is a param.type of null (not set) means type is a tweet... otherwise must specify (e.g. "media")
    @Override public MediaAsset create(final JSONObject params)
    throws IllegalArgumentException {
        if (idFromParameters(params) == null) throw new IllegalArgumentException("no id parameter");
        return new MediaAsset(this, params);
    }

    // convenience
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

    @Override public boolean cacheLocal(MediaAsset ma, boolean force) {
        return false;
    }

    @Override public Path localPath(MediaAsset ma) {
        return null;
    }

    @Override public MediaAsset copyIn(final File file, final JSONObject params,
        final boolean createMediaAsset)
    throws IOException {
        throw new IOException(this.name + " is a read-only AssetStore");
    }

    @Override public void copyAsset(final MediaAsset fromMA, final MediaAsset toMA)
    throws IOException {
        throw new IOException("copyAsset() not available for TwitterAssetStore");
    }

    @Override public void deleteFrom(final MediaAsset ma) {
        return; 
    }

    @Override public URL webURL(final MediaAsset ma) {
        if (id == null) return null;
        String u = null;
        if (ma.hasLabel("_original")) {
            String id = idFromParameters(ma.getParameters());
            if (id == null) return null;
            u = "https://www.twitter.com/statuses/" + id;
        } else if (ma.hasLabel("_entity")) {
            // could also use params.type ("photo") if need be?
            if (ma.getParameters() != null) u = ma.getParameters().optString("media_url", null);
        }
        if (u == null) return null; // dunno/how!
        try {
            return new URL(u);
        } catch (java.net.MalformedURLException ex) { // "should never happen"
            System.out.println("TwitterAssetStore.webURL() on " + ma + ": " + ex.toString());
            return null;
        }
    }

    @Override public String hashCode(JSONObject params) {
        return "Twitter" + idFromParameters(params);
    }

    @Override public JSONObject createParameters(File file, String grouping) {
        JSONObject p = new JSONObject();

        return p;
    }

    @Override public String getFilename(MediaAsset ma) {
        return idFromParameters(ma.getParameters()); // meh?
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
            System.out.println("TwitterStore.longIdFromParameters() failed to parse '" + id +
                "': " + ex.toString());
            return null;
        }
    }

/*  regarding media (etc) in tweets:  seems .extended_entities.media is the same as .entities.media ???  but extended (duh?) might have more details?
        https://dev.twitter.com/overview/api/entities-in-twitter-objects https://dev.twitter.com/overview/api/entities
 */

///// note: might also want to walk .entities.urls -- https://dev.twitter.com/overview/api/entities-in-twitter-objects#urls
// aside(?) we might want to generate similary from a Status object (aka tweet)
// see:  http://twitter4j.org/javadoc/twitter4j/EntitySupport.html#getMediaEntities--
    public static List<MediaAsset> entitiesAsMediaAssets(MediaAsset ma) {
        JSONObject raw = getRawJSONObject(ma);
        // System.out.println(raw.toString());
        AssetStore store = ma.getStore();

        if (raw == null) return null;
        if ((raw.optJSONObject("extended_entities") == null) ||
            (raw.getJSONObject("extended_entities").optJSONArray("media") == null)) return null;
        List<MediaAsset> mas = new ArrayList<MediaAsset>();
        JSONArray jarr = raw.getJSONObject("extended_entities").getJSONArray("media");
        for (int i = 0; i < jarr.length(); i++) {
            JSONObject p = jarr.optJSONObject(i);
            if (p == null) continue;
            p.put("id", p.optString("id_str", null)); // squash the long id at "id" with string
            MediaAsset kid = store.create(p);
            kid.addLabel("_entity");
            setEntityMetadata(kid);
            kid.getMetadata().getDataAsString();
            kid.setParentId(ma.getId());
            // derivationMethods?  metadata? (of image) etc.... ??
            mas.add(kid);
        }
        return mas;
    }

    // this assumes we already set metadata
    public static JSONObject getRawJSONObject(MediaAsset ma) {
        if (ma == null) return null;
        MediaAssetMetadata md = ma.getMetadata();
        if ((md == null) || (md.getData() == null) || (md.getDataAsString() == null)) return null;
        return md.getData().optJSONObject(METADATA_KEY_RAWJSON);
    }

    // currently there really only is "minimal" for tweets: namely the json data from twitter
    // this is keyed as "twitterRawJson"
    // NOTE: this also assumes TwitterUtil.init(request) has been called
    public MediaAssetMetadata extractMetadata(MediaAsset ma, boolean minimal)
    throws IOException {
        JSONObject data = new JSONObject();
        Long id = longIdFromParameters(ma.getParameters());

        if (id == null) return new MediaAssetMetadata(data);
        Status tweet = TwitterUtil.getTweet(id);
        data.put(METADATA_KEY_RAWJSON, TwitterUtil.toJSONObject(tweet));
        return new MediaAssetMetadata(data);
    }

    private static void setEntityMetadata(MediaAsset ma) {
        if (ma.getParameters() == null) return;
        JSONObject d = new JSONObject("{\"attributes\": {} }");
        if ((ma.getParameters().optJSONObject("sizes") != null) &&
            (ma.getParameters().getJSONObject("sizes").optJSONObject("medium") != null)) {
            d.getJSONObject("attributes").put("width",
                ma.getParameters().getJSONObject("sizes").getJSONObject("medium").optDouble("w",
                0));
            d.getJSONObject("attributes").put("height",
                ma.getParameters().getJSONObject("sizes").getJSONObject("medium").optDouble("h",
                0));
        } else if ((ma.getParameters().optJSONObject("sizes") != null) &&
            (ma.getParameters().getJSONObject("sizes").optJSONObject("large") != null)) {
            d.getJSONObject("attributes").put("width",
                ma.getParameters().getJSONObject("sizes").getJSONObject("large").optDouble("w", 0));
            d.getJSONObject("attributes").put("height",
                ma.getParameters().getJSONObject("sizes").getJSONObject("large").optDouble("h", 0));
        }
        String mimeGuess = ma.getParameters().optString("type", "unknown");
        if (mimeGuess.equals("photo")) mimeGuess = "image";
        mimeGuess += "/";
        String url = ma.getParameters().optString("media_url", "__fail__").toLowerCase();
        if (url.endsWith(".jpg") || url.endsWith(".jpeg")) {
            mimeGuess += "jpeg";
        } else if (url.endsWith(".png")) {
            mimeGuess += "png";
        } else if (url.endsWith(".gif")) {
            mimeGuess += "gif";
        } else {
            mimeGuess += "unknown";
        }
        d.getJSONObject("attributes").put("contentType", mimeGuess);
        ma.setMetadata(new MediaAssetMetadata(d));
    }

    // this finds "a" (first? one?) TwitterAssetStore if we have any.  (null if not)
    public static TwitterAssetStore find(Shepherd myShepherd) {
        AssetStore.init(AssetStoreFactory.getStores(myShepherd));
        if ((AssetStore.getStores() == null) || (AssetStore.getStores().size() < 1)) return null;
        for (AssetStore st : AssetStore.getStores().values()) {
            if (st instanceof TwitterAssetStore) return (TwitterAssetStore)st;
        }
        return null;
    }
}
