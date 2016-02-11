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
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import java.security.MessageDigest;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import javax.jdo.*;

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

    public abstract MediaAsset create(JSONObject params);

    //this should be unique (as possible) for a combination of params -- used for searching, see find()
    // limit to the somewhat arbitrary 75 char (which is enough for 64char of sha256 has + 11 "extra"?)
    public abstract String hashCode(JSONObject params);

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
        Extent mac = myShepherd.getPM().getExtent(MediaAsset.class, true);
        Query matches = myShepherd.getPM().newQuery(mac, "hashCode == \"" + hashCode + "\" && this.store.id == " + this.id);
        try {
            Collection c = (Collection) (matches.execute());
            ArrayList<MediaAsset> all = new ArrayList<MediaAsset>(c);
            matches.closeAll();
            return all;

        } catch (javax.jdo.JDOException ex) {
            System.out.println(this.toString() + " .findAll(" + hashCode + ") threw exception " + ex.toString());
ex.printStackTrace();
            return null;
        }
    }


    public MediaAsset findOrCreate(JSONObject params, Shepherd myShepherd) {
        MediaAsset ma = find(params, myShepherd);
        if (ma != null) return ma;
        return create(params);
    }


    public ArrayList<MediaAsset> findAllChildren(MediaAsset parent, Shepherd myShepherd) {
        if ((parent == null) || (parent.getId() < 1)) return null;
//System.out.println("pid = " + parent.getId());
        Extent mac = myShepherd.getPM().getExtent(MediaAsset.class, true);
//System.out.println("parentId == " + parent.getId() + " && this.store.id == " + this.id);
        Query matches = myShepherd.getPM().newQuery(mac, "parentId == " + parent.getId() + " && this.store.id == " + this.id);
        //Query matches = myShepherd.getPM().newQuery(mac, "parentId == 30 && this.store.id == " + this.id);
        try {
            Collection c = (Collection) (matches.execute());
            ArrayList<MediaAsset> all = new ArrayList<MediaAsset>(c);
            matches.closeAll();
            return all;

        } catch (javax.jdo.JDOException ex) {
            System.out.println(this.toString() + " .findAllChildren(" + parent.toString() + ") threw exception " + ex.toString());
ex.printStackTrace();
            return null;
        }
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

    public abstract AssetStoreType getType();

    public abstract MediaAsset updateChild(MediaAsset parent, String type, HashMap<String,Object> opts) throws IOException;

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
    protected abstract void copyAsset(final MediaAsset fromMA, final MediaAsset toMA) throws IOException;

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
     */
    public abstract JSONObject createParameters(final File file);

    public abstract void deleteFrom(final MediaAsset ma);

    //TODO how do we deterimine this?  speaking of, how do we determine when to use one store vs another!?
    //  for now, we will let order determine default.... thus burden is on list passed into init()
/* fail. TODO fix whole init() debacle eventually?
    public static AssetStore getDefault() {
        if ((stores == null) || (stores.size() < 1)) return null;
        return (AssetStore)stores.values().toArray()[0];
    }
*/
    public static AssetStore getDefault(Shepherd myShepherd) {
        init(AssetStoreFactory.getStores(myShepherd));
        if ((stores == null) || (stores.size() < 1)) return null;
        return (AssetStore)stores.values().toArray()[0];
    }

    public static AssetStore get(Shepherd myShepherd, int id) {
        init(AssetStoreFactory.getStores(myShepherd));
        return get(id);
    }

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


    public abstract MediaAssetMetadata extractMetadata(MediaAsset ma) throws IOException; 

    //these can be used by subclasses who can access files, for within .extractMetadata()

    public static JSONObject extractMetadataAttributes(File file) throws IOException {  //some "generic attributes" (i.e. not from specific sources like exif)
        BufferedImage bimg = null;
        try {
            bimg = ImageIO.read(file);
        } catch (javax.imageio.IIOException ex) {
            throw new IOException(ex.toString());
        }
        if (bimg == null) return null;
        JSONObject j = new JSONObject();
        j.put("width", (double)bimg.getWidth());
        j.put("height", (double)bimg.getHeight());
        j.put("contentType", Files.probeContentType(file.toPath()));
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
