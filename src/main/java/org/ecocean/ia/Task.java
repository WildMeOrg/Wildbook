/*
    an instance of an ia.Task can be persisted and represents the state of that task
    ... replacement (and improvement upon, hopefully) messy identity/IdentityServiceLog.java
*/

package org.ecocean.ia;

import org.ecocean.Shepherd;
import org.ecocean.Annotation;
import org.ecocean.Util;
import org.ecocean.media.MediaAsset;
import org.ecocean.identity.IBEISIA;
//import java.util.UUID;   :(
import java.util.List;
import java.util.ArrayList;
import org.joda.time.DateTime;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Task implements java.io.Serializable {

    private String id = null;
    private long created = -1;
    private long modified = -1;
    //private List<Object> objects = null;  //in some perfect world i could figure out how to persist this.  :/  oh, for a wb base class.
    private List<MediaAsset> objectMediaAssets = null;
    private List<Annotation> objectAnnotations = null;

    public Task() {
        id = Util.generateUUID();
        created = System.currentTimeMillis();
        modified = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public List<MediaAsset> getMediaAssetObjects() {
        return objectMediaAssets;
    }
    public List<Annotation> getAnnotationObjects() {
        return objectAnnotations;
    }

    public int countMediaAssetObjects() {
        return (objectMediaAssets == null) ? 0 : objectMediaAssets.size();
    }
    public int countAnnotationObjects() {
        return (objectAnnotations == null) ? 0 : objectAnnotations.size();
    }
    public int countObjects() {
        return countMediaAssetObjects() + countAnnotationObjects();
    }

    //not sure if these two are mutually exclusive by definition, but lets assume not (wtf would that even mean? i dunno)
    public boolean hasMediaAssetObject() {
        return (countMediaAssetObjects() > 0);
    }
    public boolean hasAnnotationObject() {
        return (countAnnotationObjects() > 0);
    }

    public void setObjectMediaAssets(List<MediaAsset> mas) {
        objectMediaAssets = mas;
    }
    public void setObjectAnnotations(List<Annotation> anns) {
        objectAnnotations = anns;
    }
    public List<MediaAsset> getObjectMediaAssets() {
        return objectMediaAssets;
    }
    public List<Annotation> getObjectAnnotations() {
        return objectAnnotations;
    }

    public String toString() {
        return new ToStringBuilder(this)
                .append(id)
                .append("(" + new DateTime(created) + "|" + new DateTime(modified) + ")")
                .append(countMediaAssetObjects() + "MA")
                .append(countAnnotationObjects() + "Ann")
                .toString();
    }

}

