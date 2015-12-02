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

import org.ecocean.CommonConfiguration;
import org.ecocean.ImageAttributes;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Files;
//import java.time.LocalDateTime;
import org.joda.time.DateTime;
import java.util.Date;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.Set;
import java.util.HashMap;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.builder.ToStringBuilder;
import java.util.UUID;
import java.io.File;
//import java.io.FileInputStream;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
/*
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.util.Iterator;
*/

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.*;
import com.drew.metadata.exif.ExifSubIFDDirectory;

/**
 * MediaAsset describes a photo or video that can be displayed or used
 * for processing and analysis.
 */
public class MediaAsset implements java.io.Serializable {
    static final long serialVersionUID = 8844223450447974780L;
    protected int id = MediaAssetFactory.NOT_SAVED;

    protected AssetStore store;
    protected JSONObject parameters;

    protected Integer parentId;

    protected String revision;

    protected ImageAttributes imageAttributes = null;
    protected Metadata metadata = null;

    //protected MediaAssetType type;
    //protected Integer submitterid;


    //protected Set<String> tags;
    //protected Integer rootId;

    //protected AssetStore thumbStore;
    //protected Path thumbPath;
    //protected Path midPath;

    //private LocalDateTime metaTimestamp;
    //private Double metaLatitude;
    //private Double metaLongitude;


    /**
     * To be called by AssetStore factory method.
     */
/*
    public MediaAsset(final AssetStore store, final JSONObject params, final String category)
    {
        this(MediaAssetFactory.NOT_SAVED, store, params, MediaAssetType.fromFilename(path.toString()), category);
    }
*/


    public MediaAsset(final AssetStore store, final JSONObject params) {
        //this(store, params, null);
        this(MediaAssetFactory.NOT_SAVED, store, params);
    }


    public MediaAsset(final int id,
                      final AssetStore store,
                      final JSONObject params)
    {
        this.id = id;
        this.store = store;
        this.parameters = params;
        this.revision = String.valueOf(System.currentTimeMillis());
    }


    private URL getUrl(final AssetStore store, final Path path) {
        if (store == null) {
            return null;
        }

        return null; //store.webPath(path);
    }

    private String getUrlString(final URL url) {
        if (url == null) {
            return null;
        }

        return url.toExternalForm();
    }


    public int getId()
    {
        return id;
    }
    public void setId(int i) {
        id = i;
    }   

    //this is for Annotation mostly?
    public String getUUID() {
        if (id == MediaAssetFactory.NOT_SAVED) return null;
        //UUID v3 seems to take an arbitrary bytearray in, so we construct one that is basically "MAnnnn" where "nnnn" is the int id
        byte[] b = new byte[6];
        b[0] = (byte) 77;
        b[1] = (byte) 65;
        b[2] = (byte) (id >> 24);
        b[3] = (byte) (id >> 16);
        b[4] = (byte) (id >> 8);
        b[5] = (byte) (id >> 0);
        return UUID.nameUUIDFromBytes(b).toString();
    }

    public AssetStore getStore()
    {
        return store;
    }

    public JSONObject getParameters() {
        return parameters;
    }

    public String getParametersAsString() {
        if (parameters == null) return null;
        return parameters.toString();
    }

    public void setParametersAsString(String p) {
        try {
            parameters = new JSONObject(p);
        } catch (JSONException je) {
            System.out.println(this + " -- error parsing parameters json string (" + p + "): " + je.toString());
            parameters = null;
        }
    }

    public Path localPath()
    {
        if (store == null) return null;
        return store.localPath(this);
    }

    public boolean cacheLocal() throws Exception {
        if (store == null) return false;
        return store.cacheLocal(this, false);
    }

    public boolean cacheLocal(boolean force) throws Exception {
        if (store == null) return false;
        return store.cacheLocal(this, force);
    }

    //indisputable attributes about the image (e.g. type, dimensions, colorspaces etc)
    // note this is different from Metadata which is read from the image meta contents... tho it may *use* Metadata to derive... maybe???
    // TODO should be persisted, but for now on-the-fly :(
    public ImageAttributes getImageAttributes() throws Exception {
        if (this.imageAttributes != null) return this.imageAttributes;
        if (!this.cacheLocal()) return null;
        if (this.localPath() == null) return null;
        File lfile = this.localPath().toFile();
        if (!lfile.exists()) return null;
/*
        //ImageInputStream iis = ImageIO.createImageInputStream(new FileInputStream(lfile));
        ImageInputStream iis = ImageIO.createImageInputStream(lfile);
        Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(iis);
        //if (!imageReaders.hasNext()) return null;  TODO do we need?
        ImageReader reader = (ImageReader) imageReaders.next();  //note: we are only taking first.  is this bad?
        reader.setInput(iis);
        this.imageAttributes = new ImageAttributes((double)reader.getWidth(0), (double)reader.getHeight(0), reader.getFormatName());
*/
        BufferedImage bimg = ImageIO.read(lfile);
        if (bimg == null) return null;
        this.imageAttributes = new ImageAttributes((double)bimg.getWidth(), (double)bimg.getHeight(), Files.probeContentType(this.localPath()));
        return this.imageAttributes;
    }

    //basically eats any Exceptions
    public ImageAttributes getImageAttributesOrNull() {
        ImageAttributes iatts = null;
        try {
            iatts = this.getImageAttributes();
        } catch (Exception ex) { }
        return iatts;
    }

    /////////////// regarding pulling "useful" Metadata, see: https://github.com/drewnoakes/metadata-extractor/issues/10

    //TODO this should be persisted, but for now we get on the fly (yuk)
    public Metadata getImageMetadata() throws Exception {
        if (this.metadata != null) return this.metadata;
        if (!this.cacheLocal()) return null;
        if (this.localPath() == null) return null;
        File lfile = this.localPath().toFile();
        if (!lfile.exists()) return null;
        try {
            Metadata md = ImageMetadataReader.readMetadata(lfile);
            this.metadata = md;
            return md;
        } catch (Exception ex) {
            System.out.println("MediaAsset.getImageMetadata() threw exception " + ex.toString());
            return null;
        }
    }

    //will return the first Tag object that matches by name (in any Metadata directory)
    public Tag getImageMetadataByTagName(String name) {
        Metadata md = null;
        try {
            md = this.getImageMetadata();
        } catch (Exception ex) { }
        if (md == null) return null;
        for (Directory directory : md.getDirectories()) {
            for (Tag tag : directory.getTags()) {
                if (tag.getTagName().equals(name)) return tag;
            }
        }
        return null;
    }

    /**
     this function resolves (how???) various difference in "when" this image was taken.  it might use different metadata (in EXIF etc) and/or
     human-input (e.g. perhaps encounter data might trump it?)   TODO wtf should we do?
    */
    public DateTime getDateTime() {
        DateTime t = null;
        Metadata md = null;
        try {
            md = this.getImageMetadata();
        } catch (Exception ex) { }
        if (md != null) {
            ExifSubIFDDirectory directory = md.getDirectory(ExifSubIFDDirectory.class);
            if (directory != null) {
//System.out.println("well at least we got a directory");
                Date d = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                if (d != null) t = new DateTime(d);
            }
        }
        return t;
    }

    /**
      like getDateTime() this is considered "definitive" -- so it must resolve differences in metadata vs other (e.g. encounter etc) values
    */
    public Double getLatitude() {
        return null;
    }
    public Double getLongitude() {
        return null;
    }

/*
    public Path getThumbPath()
    {
        return thumbPath;
    }

    public Path getMidPath()
    {
        return midPath;
    }
*/

/*
    public MediaAssetType getType() {
        return type;
    }
*/

    /**
     * Return a full web-accessible url to the asset, or null if the
     * asset is not web-accessible.
     */
    public URL webURL() {
        if (store == null) return null;
        return store.webURL(this);
    }

    public String webURLString() {
        return getUrlString(this.webURL());
    }

/*
    public String thumbWebPathString() {
        return getUrlString(thumbWebPath());
    }

    public String midWebPathString() {
        return getUrlString(midWebPath());
    }

    public URL thumbWebPath() {
        return getUrl(thumbStore, thumbPath);
    }

    public void setThumb(final AssetStore store, final Path path)
    {
        thumbStore = store;
        thumbPath = path;
    }

    public AssetStore getThumbstore() {
        return thumbStore;
    }

    public URL midWebPath() {
        if (midPath == null) {
            return webPath();
        }

        //
        // Just use thumb store for now.
        //
        return getUrl(thumbStore, midPath);
    }

    public void setMid(final Path path) {
        //
        // Just use thumb store for now.
        //
        this.midPath = path;
    }

*/

/*
    public Integer getSubmitterId() {
        return submitterid;
    }

    public void setSubmitterId(final Integer submitterid) {
        this.submitterid = submitterid;
    }
*/


/*
    public LocalDateTime getMetaTimestamp() {
        return metaTimestamp;
    }


    public void setMetaTimestamp(LocalDateTime metaTimestamp) {
        this.metaTimestamp = metaTimestamp;
    }


    public Double getMetaLatitude() {
        return metaLatitude;
    }


    public void setMetaLatitude(Double metaLatitude) {
        this.metaLatitude = metaLatitude;
    }


    public Double getMetaLongitude() {
        return metaLongitude;
    }


    public void setMetaLongitude(Double metaLongitude) {
        this.metaLongitude = metaLongitude;
    }
*/



/*
    public void delete() {
        MediaAssetFactory.delete(this.id);
        MediaAssetFactory.deleteFromStore(this);
    }
*/
	public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request, org.datanucleus.api.rest.orgjson.JSONObject jobj) throws org.datanucleus.api.rest.orgjson.JSONException {
            //if (jobj.get("parametersAsString") != null) jobj.put("parameters", new org.datanucleus.api.rest.orgjson.JSONObject(jobj.get("parametersAsString")));
            //if (jobj.get("parametersAsString") != null) jobj.put("parameters", new JSONObject(jobj.getString("parametersAsString")));
            if (jobj.get("parametersAsString") != null) jobj.put("parameters", new org.datanucleus.api.rest.orgjson.JSONObject(jobj.getString("parametersAsString")));
            jobj.remove("parametersAsString");
            jobj.put("guid", "http://" + CommonConfiguration.getURLLocation(request) + "/api/org.ecocean.media.MediaAsset/" + id);

            //TODO something better with store?  fix .put("store", store) ???
            HashMap<String,String> s = new HashMap<String,String>();
            s.put("type", store.getType().toString());
            jobj.put("store", s);
            return jobj;
        }

    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("store", store.toString())
                .toString();
    }

}
