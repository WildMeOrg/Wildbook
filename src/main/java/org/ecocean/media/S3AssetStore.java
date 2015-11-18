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
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.json.JSONObject;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * S3AssetStore references MediaAssets on the current host's
 * filesystem.
 *
 * To create a new store outside of Java, you can directly insert a
 * row into the assetstore table like so (adjusting paths as needed):
 *
 * insert into assetstore (name,type,config,writable) values ('local store', 'LOCAL', '{"root":"/filesystem/path/to/localassetstore","webroot":"http://host/web/path/to/localassetstore"}', true);
 *
 * Then ensure your webserver is configured to serve the filesystem
 * path at the webroot.
 *
 * If you have only one asset store defined, it will be considered the
 * default (see AssetStore.loadDefault()).
 */
public class S3AssetStore extends AssetStore {
    private static final Logger logger = LoggerFactory.getLogger(org.ecocean.media.S3AssetStore.class);
    AmazonS3 s3Client = new AmazonS3Client(new ProfileCredentialsProvider());

    /**
     * Create a new local filesystem asset store.
     *
     * @param name Friendly name for the store.
     *
     * @param root Filesystem path to the base of the asset directory.
     * Must not be null.
     *
     * @param webRoot Base web url under which asset paths are
     * appended.  If null, this store offers no web access to assets.
     *
     * @param wriable True if we are allowed to save files under the
     * root.
     */
    public S3AssetStore(final String name, final boolean writable)
    {
        this(null, name, null, writable);
    }

    /**
     * Create a new local filesystem asset store.  Should only be used
     * internal to AssetStore.buildAssetStore().
     *
     * @param root Filesystem path to the base of the asset directory.
     * Must not be null.
     *
     * @param webRoot Base web url under which asset paths are
     * appended.  If null, this store offers no web access to assets.
     */
    S3AssetStore(final Integer id, final String name,
                    final AssetStoreConfig config, final boolean writable)
    {
        super(id, name, AssetStoreType.S3, config, writable);
    }

    /**
     * Create our config map.
     */
/*
    private static AssetStoreConfig makeConfig(final Path root, final String webRoot) {
        AssetStoreConfig config = new AssetStoreConfig();

        if (root != null) config.put(KEY_ROOT, root);
        if (webRoot != null) config.put(KEY_WEB_ROOT, webRoot);

        return config;
    }


    public Path root() {
        
        if (root == null) {
            root = config.getPath(KEY_ROOT);
            logger.info("Asset Store [" + name + "] using root [" + root + "]");
        }
        return root;
    }

    private String webRoot() {
        if (webRoot == null) {
            webRoot = config.getString(KEY_WEB_ROOT);
            logger.info("Asset Store [" + name + "] using web root [" + webRoot + "]");
        }
        return webRoot;
    }

*/
    /**
     * Create a new MediaAsset that points to an existing file under
     * our root.
     *
     * @param path Relative or absolute path to a file.  Must be under
     * the asset store root.
     *
     * @return The MediaAsset, or null if the path is invalid (not
     * under the asset root or nonexistent).
     */
    @Override
    public MediaAsset create(final JSONObject params) throws IllegalArgumentException {
        //TODO sanity check of params?
        try {
            return new MediaAsset(this, params);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }


    public boolean cacheLocal(MediaAsset ma, boolean force) throws IOException {
        Path lpath = localPath(ma);
        if (lpath == null) return false;  //TODO or throw Exception?
System.out.println("cacheLocal trying to write to " + lpath);
        S3Object s3obj = getS3Object(ma);
        if (s3obj == null) return false;
        InputStream data = s3obj.getObjectContent();
        Files.copy(data,lpath);
        data.close();
        return true;
    }

    public Path localPath(MediaAsset ma) {
        JSONObject params = ma.getParameters();
        if (params == null) return null;
        if ((params.get("bucket") == null) || (params.get("key") == null)) return null;
        return Paths.get("/tmp", params.getString("bucket") + ":" + params.getString("key"));
    }


    public S3Object getS3Object(MediaAsset ma) {
        JSONObject params = ma.getParameters();
        if (params == null) return null;
        if ((params.get("bucket") == null) || (params.get("key") == null)) return null;
        S3Object s3object = s3Client.getObject(new GetObjectRequest(params.getString("bucket"), params.getString("key")));
        return s3object;
    }


    /**
     * Create a new asset from the given form submission part.  The
     * file is copied in to the store as part of this process.
     *
     * @param file File to copy in.
     *
     * @param path The (optional) subdirectory and (required) filename
     * relative to the asset store root in which to store the file.
     *
     */
    @Override
    public MediaAsset copyIn(final File file,
                             final String path)
        throws IOException
    {
/*
        Path root = root();
        Path subpath = checkPath(root, new File(path).toPath());

        Path fullpath = root.resolve(subpath);

        fullpath.getParent().toFile().mkdirs();

        logger.debug("copying from " + file + " to " + fullpath);

        Files.copy(file.toPath(), fullpath, REPLACE_EXISTING);

        //return new MediaAsset(this, subpath); //create JSON with this path!
        return new MediaAsset(this, null);
*/
        return null;
    }


    @Override
    public void deleteFrom(final MediaAsset ma)
    {
/*
        if (path == null) {
            return;
        }

        File file = getFile(path);
        if (!file.exists()) {
            return;
        }

        file.delete();

        File parentDir = file.getParentFile();

        File[] files = parentDir.listFiles();
        if (files == null || files.length == 0) { //some JVMs return null for empty dirs
            parentDir.delete();
        }
*/
    }


/*
    public File getFile(final Path path) {
        return new File(root().toString(), path.toString());
    }
*/



    /**
     * Return a full URL to the given MediaAsset, or null if the asset
     * is not web-accessible.
     */
    @Override
    public URL webURL(final MediaAsset ma) {
        return null;
    }

}


