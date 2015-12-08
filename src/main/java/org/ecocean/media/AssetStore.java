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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Collection;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import java.security.MessageDigest;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jdo.*;

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
        stores = new HashMap<Integer, AssetStore>();
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


/*
    public boolean getWritable() {
        return writable;
    }
*/

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
    public abstract MediaAsset copyIn(final File file,
                                      final JSONObject params)
                                              throws IOException;

    public abstract void deleteFrom(final MediaAsset ma);

    public static AssetStore getDefault()
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

    //utility function to always get a null or Object without throwing an exception
    public Object getParameter(JSONObject params, String key) {
        if (params == null) return null;
        if (!params.has(key)) return null;
        return params.get(key);
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
}
