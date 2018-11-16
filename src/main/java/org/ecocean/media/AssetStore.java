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

import org.ecocean.Shepherd;
import org.ecocean.Annotation;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.json.JSONArray;
import java.security.MessageDigest;
import javax.servlet.http.HttpServletRequest;
import org.joda.time.DateTime;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import javax.jdo.*;

import org.ecocean.ImageProcessor;
import org.ecocean.Util;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.*;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.imageio.IIOException;

/**
 * AssetStore describes a location and methods for access to a set of
 * MediaAssets.  Concrete subtypes fill in the "hows".
 *
 * @see LocalAssetStore
 */
public abstract class AssetStore implements java.io.Serializable {
    private static Logger logger = LoggerFactory.getLogger(AssetStore.class);

    private static Map<Integer, AssetStore> stores;

    protected Integer id;
    protected String name;
    protected AssetStoreType type = AssetStoreType.LOCAL;
    protected AssetStoreConfig config;
    protected boolean writable = true;

    //right now this is open for interpretation.  for starters, it can be "default" to designate default (duh) to use.
    //  probably also will be used to denote S3 temporary upload asset
    protected String usage;


    /**
     * Create a new AssetStore.
     */
    protected AssetStore(final Integer id, final String name,
                         final AssetStoreType type,
                         final AssetStoreConfig config,
                         final boolean writable)
    {
        if (name == null) throw new IllegalArgumentException("null name");
        if (type == null) throw new IllegalArgumentException("null type");

        this.id = id;
        this.name = name;
        this.type = type;
        this.config = config;
        this.writable = writable;
    }

    public static synchronized void init(final List<AssetStore> storelist) {
        stores = new LinkedHashMap<Integer, AssetStore>();
        for (AssetStore store : storelist) {
            stores.put(store.id, store);
        }
    }


    private static Map<Integer, AssetStore> getMap()
    {
        if (stores == null) {
            logger.warn("Asset Stores were not set up!");
            return Collections.emptyMap();
        }

        return stores;
    }


    public AssetStoreConfig getConfig() {
        return config;
    }
    public void setConfig(AssetStoreConfig c) {
        config = c;
    }

    public Integer getId() {
        return this.id;
    }

    public static synchronized void add(final AssetStore store)
    {
        getMap().put(store.id, store);
    }


    public static synchronized void remove(final AssetStore store)
    {
        getMap().remove(store.id);
    }

    public static AssetStore get(final Integer id)
    {
        return getMap().get(id);
    }

    public static AssetStore get(final String name)
    {
        for (AssetStore store : getMap().values()) {
            if (store.name != null && store.name.equals(name)) {
                return store;
            }
        }

        return null;
    }


    //returns false if "cannot cache local"
    //force=true will grab it even if we think we have one local
    public abstract boolean cacheLocal(MediaAsset ma, boolean force) throws IOException;

    public abstract Path localPath(MediaAsset ma);

    public abstract URL webURL(MediaAsset ma);

    public abstract String getFilename(MediaAsset ma);  //this should be null if there is no such thing.  "filename" is subjective here (e.g. youtube id?)

    public abstract MediaAsset create(JSONObject params);

    //this should be unique (as possible) for a combination of params -- used for searching, see find()
    // limit to the somewhat arbitrary 75 char (which is enough for 64char of sha256 has + 11 "extra"?)
    public abstract String hashCode(JSONObject params);

    //not so much creation date/time but sourced from asset (e.g. exif or youtube metadata)
    //  override as desired per type but probably should really call asset.getDateTime() which will do other work to check metadata etc
    public DateTime getDateTime(MediaAsset ma) {
        return null;
    }

    //these have to do with "child types" which are essentially derived MediaAssets ... much work TODO here -- including possibly making this its own class?
    //  i am not making this an abstract now but rather subclass can override. maybe silly? future will decide
    //  also, order matters here!  should be from "best" to "worst" so that things can degrade nicely when better ones are not available
    public List<String> allChildTypes() {
        return Arrays.asList(new String[]{"master", "mid", "watermark", "thumb"});
    }
    //awkwardly named subset of the above which will be used to determine which should be derived with updateStandardChildren()
    public List<String> standardChildTypes() {
        return Arrays.asList(new String[]{"master", "thumb", "mid", "watermark"});
    }
    public boolean isValidChildType(String type) {
        if (allChildTypes() == null) return false;
        return allChildTypes().contains(type);
    }

    public String hashCode(MediaAsset ma) {
        return hashCode(ma.getParameters());
    }

    public MediaAsset find(JSONObject params, Shepherd myShepherd) {
        return find(hashCode(params), myShepherd);
    }

    public MediaAsset find(String hashCode, Shepherd myShepherd) {
        ArrayList<MediaAsset> all = findAll(hashCode, myShepherd);
        if ((all == null) || (all.size() < 1)) return null;
        return all.get(0);
    }

    public ArrayList<MediaAsset> findAll(String hashCode, Shepherd myShepherd) {
        if (hashCode == null) return null;
        ArrayList<MediaAsset> all=new ArrayList<MediaAsset>();
        Extent mac = myShepherd.getPM().getExtent(MediaAsset.class, true);
        Query matches = myShepherd.getPM().newQuery(mac, "hashCode == \"" + hashCode + "\" && this.store.id == " + this.id);
        try {
            Collection c = (Collection) (matches.execute());
            all = new ArrayList<MediaAsset>(c);
            //matches.closeAll();
            //return all;

        } catch (javax.jdo.JDOException ex) {
            System.out.println(this.toString() + " .findAll(" + hashCode + ") threw exception " + ex.toString());
            ex.printStackTrace();
            //return null;
        }
        matches.closeAll();
        return all;
    }


    public MediaAsset findOrCreate(JSONObject params, Shepherd myShepherd) {
        MediaAsset ma = find(params, myShepherd);
        if (ma != null) return ma;
        return create(params);
    }


    /*
      hello!  2017-03-09 important paradigm shift here!  now this no longer limiting search to parent-store.
       (1) this "should be" backwards compatible; (2) we now have parent-child assets that cross store boundaries (YouTubeAssetStore / children)
       (3) restricting to store is kinda silly cuz id is primary key so would never have duplicate id across more than one store anyway
    */
    
    public ArrayList<MediaAsset> findAllChildren(MediaAsset parent, Shepherd myShepherd) {
        if ((parent == null) || (parent.getId() < 1)) return null;
        ArrayList<MediaAsset> all=new ArrayList<MediaAsset>();
        Extent mac = myShepherd.getPM().getExtent(MediaAsset.class, true);
        //Query matches = myShepherd.getPM().newQuery(mac, "parentId == " + parent.getId() + " && this.store.id == " + this.id);
        Query matches = myShepherd.getPM().newQuery(mac, "parentId == " + parent.getId());
        try {
            Collection c = (Collection) (matches.execute());
            all = new ArrayList<MediaAsset>(c);
            //matches.closeAll();
            //return all;

        } catch (javax.jdo.JDOException ex) {
            System.out.println(this.toString() + " .findAllChildren(" + parent.toString() + ") threw exception " + ex.toString());
            ex.printStackTrace();
        }
        matches.closeAll();
        return all;
    }


    //utility function to get hex string of SHA256 digest of an input string
    //   h/t https://stackoverflow.com/a/3103722
    public static String hexStringSHA256(String in) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-256");
            md.update(in.getBytes("UTF-8"));
        } catch (Exception ex) {  //i like to think this should never happen. FLW
            System.out.println("hexStringSHA256 threw: " + ex.toString());
            return "[" + in.substring(0,62) + "]";
        }
        return String.format("%064x", new java.math.BigInteger(1, md.digest()));
    }

    public void setUsage(String u) {
        usage = u;
    }
    public String getUsage() {
        return usage;
    }

    public abstract AssetStoreType getType();


    //subclass can override, but this should work for AssetStores which can handle making a local cached copy of file
    public MediaAsset updateChild(MediaAsset parent, String type, HashMap<String,Object> opts) throws IOException {
        if (parent == null) return null;
        //right now we strictly bail on non-images. in the future we *should* let various methods try to do whatever this means for their type  TODO
        if (!parent.isMimeTypeMajor("image")) {
            System.out.println("NOTICE: updateChild(" + parent + ") aborted due to non-image; major mime type = " + parent.getMimeTypeMajor());
            return null;
        }
        if (!isValidChildType(type)) {
            System.out.println("NOTICE: updateChild(" + parent + ") aborted due to invalid child type = " + type);
            return null;  //should throw exception???
        }
        try {
            parent.cacheLocal();
        } catch (Exception ex) {
            throw new IOException("updateChild() error caching local file: " + ex.toString());
        }
        File sourceFile = parent.localPath().toFile();
        File targetFile = new File(sourceFile.getParent().toString() + File.separator + Util.generateUUID() + "-" + type + ".jpg");
        boolean allowed = _updateChildLocalWork(parent, type, opts, sourceFile, targetFile);  //does the heavy lifting
        if (!allowed) return null;  //usually means read-only (big trouble throws exception, including targetFile not existing)
        JSONObject sp = this.createParameters(targetFile);
        MediaAsset ma = this.copyIn(targetFile, sp);
        if (ma == null) return null; //not sure how this would happen *without* an exception, but meh.
        ma.addLabel("_" + type);
        ma.setParentId(parent.getId());

        if ((opts != null) && (opts.get("feature") != null)) {
            Feature ft = (Feature)opts.get("feature");
            ma.addDerivationMethod("feature", ft.getId());
        }

        return ma;
    }


    //a helper/utility app for the above (if applicable) that works on localfiles (since many flavors will want that)
    protected boolean _updateChildLocalWork(MediaAsset parentMA, String type, HashMap<String,Object> opts, File sourceFile, File targetFile) throws IOException {
        if (!this.writable) return false; //should we silently fail or throw exception??
        if (!sourceFile.exists()) throw new IOException("updateChild() " + sourceFile.toString() + " does not exist");

        String action = "resize";
        int width = 0;
        int height = 0;
        float[] transformArray = new float[0];
        boolean needsTransform = false;
        String args = null;  //i think the only real arg would be watermark text (which is largely unused)

        switch (type) {
            case "master":
                action = "maintainAspectRatio";
                width = 4096;
                height = 4096;
                break;
            case "thumb":
                width = 100;
                height = 75;
                break;
            case "mid":
                width = 1024;
                height = 768;
                break;
            case "watermark":
                action = "watermark";
                width = 250;
                height = 200;
                break;
/*
            case "spot":  //really now comes from Annotation too, so kinda weirdly maybe should be "annot"ish....
                needsTransform = true;
                transformArray = (float[])opts.get("transformArray");
                break;
*/
            case "feature":
                needsTransform = true;
                Feature ft = (Feature)opts.get("feature");
                if (ft == null) throw new IOException("updateChild() has 'feature' type without a Feature passed in via opts");

                /*
                    right now we only handle bbox (xywh) and transforms ... so we kinda get ugly here
                    in the future, all this would be place with better FeatureType-specific magic.  :/  TODO !?
                */
                JSONObject params = ft.getParameters();
System.out.println("updateChild() is trying feature! --> params = " + params);
                width = (int)Math.round(params.optDouble("width", -1));
                height = (int)Math.round(params.optDouble("height", -1));
                if ((width < 0) || (height < 0)) throw new IOException("updateChild() could not get w/h for feature " + ft + " parameters " + params);

                transformArray = new float[]{1,0,0,1,0,0};
                if (params.optJSONArray("transformMatrix") != null) {
                    JSONArray tarr = params.optJSONArray("transformMatrix");
                    for (int i = 0 ; i < tarr.length() ; i++) {
                        if (i > 5) break;  //fail!
                        transformArray[i] = (float)tarr.optDouble(i, 0);
                    }
                }

                if (Util.isIdentityMatrix(transformArray)) {
                    //lets set offsets only (ImageMagick shell script will basically ignore most of matrix)
                    transformArray[4] = (float)params.optDouble("x", 0);
                    transformArray[5] = (float)params.optDouble("y", 0);
                }
System.out.println("got transformArray -> " + transformArray);
                break;
            default:
                throw new IOException("updateChild() type " + type + " unknown");
        }
System.out.println("AssetStore.updateChild(): " + sourceFile + " --> " + targetFile);

/* a quandry - i *think* "we all" (?) have generally agreed that a *new* MediaAsset should be created for each change in the contents of the source file.
   as such, finding an existing child MediaAsset of the type desired probably means it should either be deleted or orphaned ... or maybe simply marked older?
   in short: "revisioning".  further, if the *parent has changed* should it also then not be a NEW MediaAsset itself anyway!? as such, we "should never" be
   altering an existing child type on an existing parent.  i think.  ???  sigh.... not sure what TODO  -jon */

        ImageProcessor iproc = null;
        if (needsTransform) {
            iproc = new ImageProcessor("context0", sourceFile.toString(), targetFile.toString(), width, height, transformArray, parentMA);
        } else {
            iproc = new ImageProcessor("context0", action, width, height, sourceFile.toString(), targetFile.toString(), args, parentMA);
        }

        Thread t = new Thread(iproc);
        t.start();
        try {
            t.join();  //we have to wait for it to finish, so we can do the copyIn() below
        } catch (InterruptedException ex) {
            throw new IOException("updateChild() ImageProcessor failed due to interruption: " + ex.toString());
        }

        if (!targetFile.exists()) throw new IOException("updateChild() failed to create " + targetFile.toString());
        return true;
    }


/*  do we even want to allow this?
    public MediaAsset create(String jsonString) {
    }
*/

    /**
     * Create a new asset from a File. The file is
     * copied in to the store as part of this process.
     *
     * @param file File to copy in.
     *
     * @param params The (store-type-specific) JSONObject with settings
     * on how to store the incoming file.
     *
     */
    public MediaAsset copyIn(final File file, final JSONObject params) throws IOException {
        return copyIn(file, params, true);
    }

    /**
     * Like above, but pass in MediaAsset and copy file to AssetStore accordingly (does the real dirty work for above).
     *
     * @param file File to copy in
     *
     * @param ma The MediaAsset
     */
    public void copyIn(final File file, final MediaAsset ma) throws IOException {
        copyIn(file, ma.getParameters(), false);
    }

    protected abstract MediaAsset copyIn(final File file, final JSONObject params, final boolean createMediaAsset) throws IOException;

    /**
     * does a store-specific copy of asset (contents) from one MediaAsset (location) to another
     *  note: these may be on different stores, so we handle the various cases
     */
    public void copyAssetAny(final MediaAsset fromMA, final MediaAsset toMA) throws IOException {
//System.out.println("FROM " + fromMA);
//System.out.println("TO " + toMA);
        if (fromMA == null) throw new IOException("copyAssetAny(): fromMA is null");
        if (toMA == null) throw new IOException("copyAssetAny(): toMA is null");
        if (fromMA.getStore() == null) throw new IOException("copyAssetAny(): fromMA store is null");
        if (toMA.getStore() == null) throw new IOException("copyAssetAny(): toMA store is null");
        if (fromMA.getStore().typeEquals(toMA.getStore())) {
            fromMA.getStore().copyAsset(fromMA, toMA);
        } else {
            copyAssetAcross(fromMA, toMA);
        }
    }

    //this is within the same flavor of AssetStore, so is handled by the subclass
    public abstract void copyAsset(final MediaAsset fromMA, final MediaAsset toMA) throws IOException;

    //to copy across flavors of AssetStore
    private void copyAssetAcross(final MediaAsset fromMA, final MediaAsset toMA) throws IOException {
        throw new IOException("copyAssetAcross() not yet implemented!  :/");
/*
        //we basically always use a local version as the go-between...
        Path fromPath = null;
        try {
            fromMa.cacheLocal();
            fromPath = fromMa.localPath();
        } catch (Exception ex) {
            throw new IOException("error creating local copy of " + fromMA.toString() + ": " + ex.toString());
        }
        try {
            toMA.copyIn(fromPath.toFile());
        } catch (Exception ex) {
            throw new IOException("error copying to " + toMA.toString() + ": " + ex.toString());
        }
*/
    }

    /**
     *  should create the ("base") set of parameters for the specific store-type based on file.
     *  note this can take into account store-specific config settings (like bucket for S3)
     *   (optional) "grouping" acts sort of like a common subdir to put it under (**if** available for that store!)
     *  can (should?) just return null for read-only stores?
     */
    public abstract JSONObject createParameters(final File file, final String grouping);
    public JSONObject createParameters(final File file) {
        return createParameters(file, null);
    }

    public abstract void deleteFrom(final MediaAsset ma);


    //right now default is determined by (a) a .usage value of "default"; or (b) if that does not exist, the first AssetStore by order
    public static AssetStore getDefault(Shepherd myShepherd) {
        init(AssetStoreFactory.getStores(myShepherd));
        if ((stores == null) || (stores.size() < 1)) {
            System.out.println("WARNING: AssetStore.getDefault() can find no AssetStores. This is likely bad! Please create one.");
            return null;  //i have a good mind to throw an exception here...
        }
        for (AssetStore st : stores.values()) {
            if ("default".equals(st.getUsage())) return st;
        }
        return (AssetStore)stores.values().toArray()[0];
    }

    public static AssetStore get(Shepherd myShepherd, int id) {
        init(AssetStoreFactory.getStores(myShepherd));
        return get(id);
    }


/*
    public static AssetStore getByUsage(Shepherd myShepherd, String usage) {
        if (usage == null) return null;
        init(AssetStoreFactory.getStores(myShepherd));
        if ((stores == null) || (stores.size() < 1)) return null;
        for (AssetStore st : stores.values()) {
            if (usage.equals(st.getUsage())) return st;
        }
        return null;
    }
*/


/*
    {
        for (AssetStore store : getMap().values()) {
            if (store.type == AssetStoreType.LOCAL) {
                return store;
            }
        }

        //
        // Otherwise return the first one in the map?
        //
        if (stores.values().iterator().hasNext()) {
            return stores.values().iterator().next();
        }

        return null;
    }
*/

    public static Map<Integer, AssetStore> getStores() {
        return stores;
    }

    //utility function to always get a null or Object without throwing an exception
    public Object getParameter(JSONObject params, String key) {
        if (params == null) return null;
        if (!params.has(key)) return null;
        return params.get(key);
    }

    //really only conceptually, not whether asset is really there!
    public boolean contains(MediaAsset ma) {
        if ((ma == null) || (ma.getStore() == null)) return false;
        return this.equals(ma.getStore());
    }


    public boolean equals(AssetStore s) {
        if (s == null) return false;
        return (this.getId() == s.getId());
    }

    public boolean typeEquals(AssetStore s) {
        if (s == null) return false;
        return this.getType().equals(s.getType());
    }


    @Override
    public String toString()
    {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("name", name)
                .append("type", this.getType())
                .toString();
    }


    //override this per-AssetStore if needed
    //  Annotation can be useful, but may be null! so allow for that.
    public String mediaAssetToHtmlElement(MediaAsset ma, HttpServletRequest request, Shepherd myShepherd, Annotation ann) {
        URL url = ma.webURL();
        if (url == null) return "<div id=\"media-asset-" + ma.getId() + "\" class=\"media-asset no-media-url\">no webURL</div>";

//TODO branch based upon permissions

        String more = "";
        ArrayList<MediaAsset> kids = new ArrayList<MediaAsset>();

//FOR NOW (TODO) we are disabling non-trivial annotations display, such is life?
if ((ann != null) && !ann.isTrivial()) return "<!-- skipping non-trivial annotation -->";
/*
        //for non-trivial annotations, lets try to find the 
        if ((ann != null) && !ann.isTrivial()) {
            kids = ma.findChildrenByLabel(myShepherd, "_annotation");
            if ((kids != null) && (kids.size() > 0) && (kids.get(0).webURL() != null)) ma = kids.get(0);
            url = ma.webURL();
        }
        if (ann != null) more += " data-annotation-id=\"" + ann.getId() + "\" ";
*/

        String smallUrl = url.toString();
        kids = ma.findChildrenByLabel(myShepherd, "_watermark");
        if ((kids != null) && (kids.size() > 0) && (kids.get(0).webURL() != null)) smallUrl = kids.get(0).webURL().toString(); 

        //this should be based on permission for example:
        more += " data-full-url=\"" + url + "\" ";

        return "<div id=\"media-asset-" + ma.getId() + "\" class=\"media-asset media-default\" " + more + "><img src=\"" + smallUrl + "\" /></div>";
        //return "<div>(" + ma.toString() + "<br />" + smallUrl + "<br />" + url + ")</div>";
    }

    public MediaAssetMetadata extractMetadata(MediaAsset ma) throws IOException {
        return extractMetadata(ma, false);
    }

    //this can be overridden if needed, but this should be fine for any AssetStore which can cacheLocal
    //  minimal means width/height/type (MetadataAttributes) only -- good for derived (i.e. exif-boring) images
    public MediaAssetMetadata extractMetadata(MediaAsset ma, boolean minimal) throws IOException {
        try {
            ma.cacheLocal();
        } catch (Exception ex) {
            throw new IOException("extractMetadata() error caching local file: " + ex.toString());
        }
        File file = ma.localPath().toFile();
        if (!file.exists()) throw new IOException(file + " does not exist");
        JSONObject data = new JSONObject();
        JSONObject attr = extractMetadataAttributes(file);
        if (attr != null) data.put("attributes", attr);
        if (!minimal) {
            //we swallow the exif IOException, since it can flake (non-jpegs etc) and we "dont care" -- we would rather just have MetadataAttributes than nothing
            try {
                JSONObject exif = extractMetadataExif(file);
                if (exif != null) data.put("exif", exif);
            } catch (IOException ioe) {
                System.out.println("WARNING: extractMetadataExif threw " + ioe.toString() + " on " + ma + "; ignoring");
            }
        }

        return new MediaAssetMetadata(data);
    }


    //these can be used by subclasses who can access files, for within .extractMetadata()

    public static JSONObject extractMetadataAttributes(File file) throws IOException {  //some "generic attributes" (i.e. not from specific sources like exif)
        JSONObject j = new JSONObject();
        j.put("contentType", Files.probeContentType(file.toPath()));  //hopefully we can always/atleast get this

        //we only kinda care about bimg failure -- see: non-images
        BufferedImage bimg = null;
        try {
            bimg = ImageIO.read(file);
        } catch (javax.imageio.IIOException ex) { }
        if (bimg != null) {
            j.put("width", (double)bimg.getWidth());
            j.put("height", (double)bimg.getHeight());
        }
        return j;
    }


    /////////////// regarding pulling "useful" Metadata, see: https://github.com/drewnoakes/metadata-extractor/issues/10
    public static JSONObject extractMetadataExif(File file) throws IOException {
        Metadata md = null;
        try {
            md = ImageMetadataReader.readMetadata(file);
        } catch (Exception ex) {
            throw new IOException("MediaAsset.getImageMetadata() threw exception " + ex.toString());
        }

        JSONObject j = new JSONObject();
        for (Directory directory : md.getDirectories()) {
            JSONObject d = new JSONObject();
            for (Tag tag : directory.getTags()) {
                d.put(tag.getTagName(), tag.getDescription());
            }
            j.put(directory.getName(), d);
        }
        return j;
    }

}
