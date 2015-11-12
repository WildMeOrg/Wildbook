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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AssetStore describes a location and methods for access to a set of
 * MediaAssets.  Concrete subtypes fill in the "hows".
 *
 * @see LocalAssetStore
 */
public abstract class AssetStore {
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


    public abstract URL webPath(Path path);

    public abstract MediaAsset create(Path path, String type);

    public abstract MediaAsset create(String path, String type);

    /**
     * Create a new asset from the given form submission part.  The
     * file is copied in to the store as part of this process.
     *
     * @param file File to copy in.
     *
     * @param path The (optional) subdirectory and (required) filename
     * relative to the asset store root in which to store the file.
     *
     * @param category Probably AssetType.ORIGINAL.
     */
    public abstract MediaAsset copyIn(final File file,
                                      final String path,
                                      final String category)
                                              throws IOException;

    public abstract void deleteFrom(final Path path);

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

    @Override
    public String toString()
    {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("name", name)
                .append("type", type)
                .toString();
    }
}
