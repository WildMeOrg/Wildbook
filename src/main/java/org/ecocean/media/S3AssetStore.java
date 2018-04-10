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
import org.ecocean.ImageProcessor;
import org.json.JSONObject;
import java.util.Iterator;
import java.util.HashMap;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.S3Object;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.ecocean.Shepherd;
import org.ecocean.Util;

import javax.jdo.*;
import java.util.Collection;

/**
 * S3AssetStore references MediaAssets on the current host's
 * filesystem.
 *
 * To create a new store outside of Java, you can directly insert a
 * row into the assetstore table like so (adjusting paths as needed):
 *
 * insert into assetstore (name,type,config,writable) values ('S3 store', 'S3', NULL, true);
 *
 * If you have only one asset store defined, it will be considered the
 * default (see AssetStore.loadDefault()).
 */
public class S3AssetStore extends AssetStore {
    private static final Logger logger = LoggerFactory.getLogger(org.ecocean.media.S3AssetStore.class);

    /**
    For information on credentials used to access Amazon AWS S3, see:
    https://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/credentials.html
    TODO possibly allow per-AssetStore or even per-MediaAsset credentials. these should be
    passed by reference (e.g. dont store them in, for example, MediaAsset parameters) perhaps to Profile or some properties etc?
    */
    AmazonS3 s3Client = null;

    /**
     * Create a new S3 asset store.
     *
     * @param name Friendly name for the store.
     *
     * @param writable True if we are allowed to save files under the
     * root.
     */
    public S3AssetStore(final String name, final AssetStoreConfig cfg, final boolean writable)
    {
        this(null, name, cfg, writable);
    }

    /**
     * Create a new S3 asset store.  Should only be used
     * internal to AssetStore.buildAssetStore().
     */
    S3AssetStore(final Integer id, final String name,
                    final AssetStoreConfig config, final boolean writable)
    {
        super(id, name, AssetStoreType.S3, config, writable);
    }

    public AssetStoreType getType() {
        return AssetStoreType.S3;
    }

    public AmazonS3 getS3Client() {
        if (s3Client != null) return s3Client;
        if ((config.getString("AWSAccessKeyId") != null) && (config.getString("AWSSecretAccessKey") != null)) {
            s3Client = new AmazonS3Client(new BasicAWSCredentials(config.getString("AWSAccessKeyId"), config.getString("AWSSecretAccessKey")));
        } else {  //we try the default credentials file
            s3Client = new AmazonS3Client(new ProfileCredentialsProvider());
        }
        return s3Client;
    }

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
        if (!force && Files.exists(lpath)) return true;  //we assume if we have it, then we should be cool
        System.out.println("S3.cacheLocal() trying to write to " + lpath);
        S3Object s3obj = getS3Object(ma);
        if (s3obj == null) return false;
        InputStream data = s3obj.getObjectContent();
        Files.copy(data,lpath, REPLACE_EXISTING);
        data.close();
        return true;
    }

    public Path localPath(MediaAsset ma) {
        JSONObject params = ma.getParameters();
        Object bp = getParameter(params, "bucket");
        Object kp = getParameter(params, "key");
        if ((bp == null) || (kp == null)) return null;
        return Paths.get("/tmp", bp.toString() + ":" + kp.toString().replaceAll("\\/", ":"));
    }


    public S3Object getS3Object(MediaAsset ma) {
        JSONObject params = ma.getParameters();
        Object bp = getParameter(params, "bucket");
        Object kp = getParameter(params, "key");
        if ((bp == null) || (kp == null)) return null;
        S3Object s3object = getS3Client().getObject(new GetObjectRequest(bp.toString(), kp.toString()));
        return s3object;
    }


    public MediaAsset copyIn(final File file, final JSONObject params, final boolean createMediaAsset) throws IOException {
        if (!this.writable) throw new IOException(this.name + " is a read-only AssetStore");
        if (!file.exists()) throw new IOException(file.toString() + " does not exist");

        //TODO handle > 5G files:  https://docs.aws.amazon.com/AmazonS3/latest/dev/UploadingObjects.html
        if (file.length() > 5 * 1024 * 1024 * 1024) throw new IOException("S3AssetStore does not yet support file upload > 5G");

        Object bp = getParameter(params, "bucket");
        Object kp = getParameter(params, "key");
        if ((bp == null) || (kp == null)) throw new IllegalArgumentException("Invalid bucket and/or key value");
        getS3Client().putObject(new PutObjectRequest(bp.toString(), kp.toString(), file));
        if (!createMediaAsset) return null;
        return new MediaAsset(this, params);
    }

    @Override
    //NOTE the aws credentials will be pulled from the current instance ("this") S3AssetStore, so must have access to both buckets
    //NOTE: *** s3 might give an "invalid key" if you try to copyObject a file immediately after it was created.  ymmv.
    public void copyAsset(final MediaAsset fromMA, final MediaAsset toMA) throws IOException {
        //i guess we could pass this case along to AssetStore.copyAssetAny() ??
        if ((fromMA == null) || (toMA == null) || (fromMA.getStore() == null) || (toMA.getStore() == null)) throw new IOException("null value(s) in copyAsset()");
        if (!(fromMA.getStore() instanceof S3AssetStore) || !(toMA.getStore() instanceof S3AssetStore)) throw new IOException("invalid AssetStore type(s)");
        if (!toMA.getStore().writable) throw new IOException(toMA.getStore().name + " is a read-only AssetStore");

        Object fromB = getParameter(fromMA.getParameters(), "bucket");
        Object fromK = getParameter(fromMA.getParameters(), "key");
        if ((fromB == null) || (fromK == null)) throw new IOException("Invalid bucket and/or key value for source MA " + fromMA);
        Object toB = getParameter(toMA.getParameters(), "bucket");
        Object toK = getParameter(toMA.getParameters(), "key");
        if ((toB == null) || (toK == null)) throw new IOException("Invalid bucket and/or key value for target MA " + toMA);
System.out.println("S3AssetStore.copyAsset(): " + fromB.toString() + "|" + fromK.toString() + " --> " + toB.toString() + "|" + toK.toString());
        //getS3Client() gets aws credentials from this instance S3AssetStore
        getS3Client().copyObject(new CopyObjectRequest(fromB.toString(), fromK.toString(), toB.toString(), toK.toString()));
    }

    @Override
    public void deleteFrom(final MediaAsset ma)
    {
        if (!this.contains(ma)) return;
        if (!this.writable) return;
        JSONObject params = ma.getParameters();
        Object bp = getParameter(params, "bucket");
        Object kp = getParameter(params, "key");
        if ((bp == null) || (kp == null)) throw new IllegalArgumentException("Invalid bucket and/or key value");
        getS3Client().deleteObject(new DeleteObjectRequest(bp.toString(), kp.toString()));
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
        if (!config.getBoolean("urlAccessible")) return null;
        JSONObject params = ma.getParameters();
/*  meh, fooey on this per-asset setting; lets only use per-store setting
        Object up = getParameter(params, "urlAccessible");
        if ((up == null) || !params.getBoolean("urlAccessible")) return null;
*/
        Object bp = getParameter(params, "bucket");
        Object kp = getParameter(params, "key");
        if ((bp == null) || (kp == null)) return null;
        URL u = null;
        try {
            u = new URL("https://" + bp.toString() + ".s3.amazonaws.com/" + kp.toString());
        } catch (Exception ex) {
        }
        return u;
    }

    @Override
    public String getFilename(MediaAsset ma) {
        JSONObject params = ma.getParameters();
        if (params == null) return null;
        Object kp = getParameter(params, "key");
        if (kp == null) return null;
        return kp.toString();
    }

    @Override
    public String hashCode(JSONObject params) {
        if (params == null) return null;
        Object bp = getParameter(params, "bucket");
        Object kp = getParameter(params, "key");
        if ((bp == null) || (kp == null)) return null;
        String prefix = bp.toString();
        if (prefix.length() > 10) prefix = prefix.substring(0,10);
        return prefix + S3AssetStore.hexStringSHA256(bp.toString() + "/" + kp.toString());
    }

    @Override
    public JSONObject createParameters(File file, String grouping) {
        JSONObject p = new JSONObject();
        if ((this.config == null) || (this.config.getString("bucket") == null)) throw new IllegalArgumentException(this + " does not have a default bucket value");
        p.put("bucket", this.config.getString("bucket"));
        //note: this key is simply to try to encourage uniqueness, but can be later re-set with something better if desired
        if (grouping == null) grouping = Util.hashDirectories(Util.generateUUID(), "/");
        if (file != null) p.put("key", grouping + "/" + file.getName());
        return p;
    }


}


