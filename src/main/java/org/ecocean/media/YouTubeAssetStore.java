package org.ecocean.media;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.Shepherd;
import org.ecocean.Util;
import org.joda.time.DateTime;
import org.json.JSONException;
import org.json.JSONObject;


// TODO: soon to be deprecated: https://github.com/WildMeOrg/Wildbook/issues/694

/**
 * YouTubeAssetStore references MediaAssets that reside on YouTube.
 * currently this is read-only but later could be writable with an API key if needed?
 *
 */
public class YouTubeAssetStore extends AssetStore {
    public YouTubeAssetStore(final String name) {
        this(null, name, null, false);
    }

    YouTubeAssetStore(final Integer id, final String name, final AssetStoreConfig config,
        final boolean writable) {
        super(id, name, AssetStoreType.YouTube, config, false);
    }

    public AssetStoreType getType() {
        return AssetStoreType.YouTube;
    }

    @Override public MediaAsset create(final JSONObject params)
    throws IllegalArgumentException {
        if (idFromParameters(params) == null) throw new IllegalArgumentException("no id parameter");
        return new MediaAsset(this, params);
    }

    // convenience
    public MediaAsset create(final String ytid) {
        JSONObject p = new JSONObject();

        p.put("id", ytid);
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
        throw new IOException("copyAsset() not available for YouTubeAssetStore");
    }

    @Override public void deleteFrom(final MediaAsset ma) {
        return; 
    }

    @Override public URL webURL(final MediaAsset ma) {
        String id = idFromParameters(ma.getParameters());

        if (id == null) return null;
        try {
            return new URL("https://www.youtube.com/watch?v=" + id);
        } catch (java.net.MalformedURLException ex) { // "should never happen"
            return null;
        }
    }

    @Override public String hashCode(JSONObject params) {
        return "YouTube" + idFromParameters(params);
    }

    @Override public DateTime getDateTime(MediaAsset ma) {
        if ((ma == null) || (ma.getMetadata() == null) ||
            (ma.getMetadata().getData().optJSONObject("detailed") == null)) return null;
        String upd = ma.getMetadata().getData().getJSONObject("detailed").optString("upload_date",
            null);
        if ((upd == null) || (upd.length() != 8)) return null; // is like YYYYMMDD
        try {
            return new DateTime(Integer.parseInt(upd.substring(0, 4)),
                    Integer.parseInt(upd.substring(4, 6)), Integer.parseInt(upd.substring(6)), 0,
                    1);
        } catch (Exception ex) {}
        return null;
    }

    @Override public JSONObject createParameters(File file, String grouping) {
        JSONObject p = new JSONObject();

        // will we even ever need this for a read-only system???  
        return p;
    }

    @Override public String getFilename(MediaAsset ma) {
        return idFromParameters(ma.getParameters()); // meh?
    }

    private static String idFromParameters(JSONObject params) {
        if (params == null) return null;
        return params.optString("id", null);
    }

    // most likely you want grabAndParse() really.  this is just a wrapper to YouTube.grab() basically
    // how should we thread in bkgd??? ... probably this should by synchrous, but stuff can bg when needed (e.g. extractMetadata)
    public static List<File> grab(MediaAsset ma)
    throws IOException {
        throw new IOException("deprecated");
    }

    // returns success (stuff grabbed and parsed)
    // 'wait' refers only for other process, if we need to do the grabbing, right now it will always wait for that
    // NOTE: wait is untested!  
    public static boolean grabAndParse(Shepherd myShepherd, MediaAsset ma, boolean wait)
    throws IOException {
        boolean processing = ((ma.getDerivationMethod() != null) &&
            ma.getDerivationMethod().optBoolean("_processing", false));
        int count = 0;
        int max = 100;

        while (processing && (count < max)) {
            System.out.println("INFO: YouTubeAssetStore(" + ma +
                ").grabAndParse, waiting with count=" + count);
            if (!wait) return false;
            try {
                Thread.sleep(2000);
            } catch (java.lang.InterruptedException ex) {}
            count++;
            if (count >= max) return false;
            processing = ((ma.getDerivationMethod() != null) &&
                ma.getDerivationMethod().optBoolean("_processing", false));
        }
        // get here, we must not be processing, so lets do that!
        ma.addDerivationMethod("_processing", true);
        List<File> grabbed = YouTubeAssetStore.grab(ma);
        for (File f : grabbed) {
            System.out.println("- [" + f.getName() + "] grabAndParse: " + f);
            if (f.getName().matches(".+.info.json$")) {
                List<String> lines = Files.readAllLines(f.toPath(), Charset.defaultCharset());
                JSONObject detailed = null;
                try {
                    detailed = new JSONObject(StringUtils.join(lines, ""));
                } catch (JSONException jex) {
                    System.out.println("ERROR: " + ma +
                        " grabAndParse() failed to parse YouTube .info.json - " + jex.toString());
                }
// System.out.println("detailed -> " + detailed);
                if (detailed != null) {
                    JSONObject mj = new JSONObject();
                    if (ma.getMetadata() != null) {
                        ma.getMetadata().getDataAsString(); // HACK gets around weird dn caching issue. :(
                        mj = ma.getMetadata().getData();
                    }
                    mj.put("detailed", detailed);
                    ma.setMetadata(new MediaAssetMetadata(mj));
                }
                // note: these persist the children they make, fbow ?
            } else if (f.getName().matches(".+.jpg$")) {
                _createThumbChild(myShepherd, ma, f);
            } else if (f.getName().matches(".+.mp4$")) {
                _createVideoChild(myShepherd, ma, f);
            }
        }
        ma.addDerivationMethod("_processing", false);
        return true;
    }

    // most likely you want extractFramesAndParse() really.  this is just a wrapper to YouTube.extractFrames() basically
    // here, ma must be a video file!
    public static List<File> extractFrames(MediaAsset ma)
    throws IOException {
        throw new IOException("deprecated");
    }

    // returns success (frames extracted) -- passed in parent/_original MA ... maybe later we would want to also allow _video child option?  
    // 'wait' refers only for other process, if we need to do the grabbing, right now it will always wait for that
    // NOTE: wait is untested!  
    // note that uses same _processing derivation method as .grabAndParse() above.  this is cuz these should not be run simultaneous anyway
    public static boolean extractFramesAndParse(Shepherd myShepherd, MediaAsset ma, boolean wait)
    throws IOException {
        if (ma == null)
            throw new IOException(
                      "YouTubeAssetStore.extractFramesAndParse() given null MediaAsset");
        ArrayList<MediaAsset> vidKids = ma.findChildrenByLabel(myShepherd, "_video");
        if ((vidKids == null) || (vidKids.size() < 1))
            throw new IOException("YouTubeAssetStore.extractFramesAndParse(" + ma +
                    ") has no _video child");
        boolean processing = ((ma.getDerivationMethod() != null) &&
            ma.getDerivationMethod().optBoolean("_processing", false));
        int count = 0;
        int max = 100;
        while (processing && (count < max)) {
            System.out.println("INFO: YouTubeAssetStore(" + ma +
                ").extractFramesAndParse, waiting with count=" + count);
            if (!wait) return false;
            try {
                Thread.sleep(2000);
            } catch (java.lang.InterruptedException ex) {}
            count++;
            if (count >= max) return false;
            processing = ((ma.getDerivationMethod() != null) &&
                ma.getDerivationMethod().optBoolean("_processing", false));
        }
        // get here, we must not be processing, so lets do that!
        ma.addDerivationMethod("_processing", true);
        List<File> extracted = YouTubeAssetStore.extractFrames(vidKids.get(0));
        if (extracted == null) {
            System.out.println("WARNING: extractFrames(" + vidKids.get(0) +
                ") returned null; extractFramesAndParse() bailing");
            return false;
        }
        for (File f : extracted) {
            if (!f.getName().matches(".+.jpg$")) continue; // skip log files or whatev
            System.out.println("- [" + f.getName() + "] extractFramesAndParse: " + f);
            _createFrameChild(myShepherd, vidKids.get(0), f);
        }
        ma.addDerivationMethod("_processing", false);
        return true;
    }

    private static MediaAsset _createThumbChild(Shepherd myShepherd, MediaAsset parent, File f)
    throws java.io.IOException {
        if ((f == null) || !f.exists()) {
            System.out.println("WARNING: could not create thumb child for " + parent +
                ", file failure on " + f + "; skipping");
            return null;
        }
        AssetStore astore = AssetStore.getDefault(myShepherd);
        if (astore == null) {
            System.out.println("WARNING: could not create thumb child for " + parent +
                ", no valid asset store");
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
// System.out.println("thumb child created: " + kid);
        return kid;
    }

    private static MediaAsset _createVideoChild(Shepherd myShepherd, MediaAsset parent, File f)
    throws java.io.IOException {
        if ((f == null) || !f.exists()) {
            System.out.println("WARNING: could not create video child for " + parent +
                ", file failure on " + f + "; skipping");
            return null;
        }
        AssetStore astore = AssetStore.getDefault(myShepherd);
        if (astore == null) {
            System.out.println("WARNING: could not create video child for " + parent +
                ", no valid asset store");
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
// System.out.println("video child created: " + kid);
        return kid;
    }

    private static MediaAsset _createFrameChild(Shepherd myShepherd, MediaAsset parent, File f)
    throws java.io.IOException {
        if ((f == null) || !f.exists()) {
            System.out.println("WARNING: could not create frame child for " + parent +
                ", file failure on " + f + "; skipping");
            return null;
        }
        // AssetStore astore = AssetStore.getDefault(myShepherd); //err, um think we should use parent's (as it is a video stored somewhere, not a
        // YouTubeAssetStore
        AssetStore astore = parent.getStore();
        if (astore == null) {
            System.out.println("WARNING: could not create frame child for " + parent +
                ", no valid asset store");
            return null;
        }
        int offset = -1;
        // this assumes filename is like "frame00001.jpg" so needs to be altered if config/youtube_extract.sh ever changes that
        try {
            offset = Integer.parseInt(f.getName().substring(5, 10)) - 1; // ffmpeg starts counting at 1, so we adjust to be 0-based
        } catch (NumberFormatException | IndexOutOfBoundsException ex) {
            System.out.println("WARNING: YouTubeAssetStore._createFrameChild(" + parent +
                ") could not parse offset int value from " + f + " filename; using -1: " +
                ex.toString());
        }
        JSONObject sp = astore.createParameters(f);
        sp.put("key", parent.getUUID() + "/" + f.getName());
        //sp.put("extractFPS", YouTube.EXTRACT_FPS);
        sp.put("extractOffset", offset);
        MediaAsset kid = new MediaAsset(astore, sp);
        kid.copyIn(f);
        kid.setParentId(parent.getId());
        kid.addLabel("_frame");
        kid.updateMinimalMetadata();
        MediaAssetFactory.save(kid, myShepherd);
// System.out.println("frame child created: " + kid);
        return kid;
    }

    public MediaAssetMetadata extractMetadata(MediaAsset ma, boolean minimal)
    throws IOException {
        JSONObject data = new JSONObject();

        // we (attempt to) let the basic stuff finish synchronously, so we have a populated data chunk to save (hopefully)before the detailed one does
        if (!minimal) {
            data.put("detailed",
                new JSONObject("{\"_processing\": true, \"timestamp\": " +
                System.currentTimeMillis() + "}"));                                                                               // assume it will
                                                                                                                                  // be.... soon?
            //  do actual grabAndProcess here???
        }
        return new MediaAssetMetadata(data);
    }

    // this finds "a" (first? one?) YoutubeAssetStore if we have any.  (null if not)
    public static YouTubeAssetStore find(Shepherd myShepherd) {
        AssetStore.init(AssetStoreFactory.getStores(myShepherd));
        if ((AssetStore.getStores() == null) || (AssetStore.getStores().size() < 1)) return null;
        for (AssetStore st : AssetStore.getStores().values()) {
            if (st instanceof YouTubeAssetStore) return (YouTubeAssetStore)st;
        }
        return null;
    }

    /*
        note: this assumes the MediaAsset has been persisted, so we can create our own Shepherd object and affect it accordingly.... there are
           potentials for race conditions here for sure, especially if the caller has gone on to (or caused something to go on to) further alter the
           MediaAsset in memory etc.   use caution!
         perhaps for this reason we should have a synchronous version of this too?
     */
    public static void backgroundGrabAndParse(final MediaAsset otherMa,
        HttpServletRequest request) {
        String context = ServletUtilities.getContext(request);
        final Shepherd myShepherd = new Shepherd(context);

        System.out.println("forking >>>>");
        Runnable rn = new Runnable() {
            public void run() {
                myShepherd.setAction("YouTubeAssetStore.backgroundGrabAndParse");
                myShepherd.beginDBTransaction();
                MediaAsset ma = MediaAssetFactory.load(otherMa.getId(), myShepherd);
                System.out.println("about to grab!");
                boolean ok = false;
                try {
                    grabAndParse(myShepherd, ma, false);
                } catch (IOException ex) {
                    System.out.println("ERROR: IOException grabbing " + ma + ": " + ex.toString());
                }
                System.out.println("grabAndParse() -> " + ok + "; " + ((ma.getMetadata() ==
                    null) ? "(null metadata)" : ma.getMetadata().getDataAsString()));
                MediaAssetFactory.save(ma, myShepherd);
                myShepherd.commitDBTransaction();
                myShepherd.closeDBTransaction();
            }
        };
        new Thread(rn).start();
        System.out.println("<<<<< out of fork");
    }

    // can start at either root (_original) or _video ma
    public static ArrayList<MediaAsset> findFrames(final MediaAsset ma, Shepherd myShepherd) {
        if (ma == null) return null;
        MediaAsset vma = ma;
        if (!ma.hasLabel("_video")) {
            ArrayList<MediaAsset> vidKids = ma.findChildrenByLabel(myShepherd, "_video");
            if ((vidKids == null) || (vidKids.size() < 1)) return null; // nope
            vma = vidKids.get(0);
        }
        return vma.findChildrenByLabel(myShepherd, "_frame");
    }
}
