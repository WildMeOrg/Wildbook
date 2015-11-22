package org.ecocean.media;

/*
  TODO note: this is very ibeis-specific concept of "Annotation"
     we should probably consider a general version which can be manipulated into an ibeis one somehow
*/

import org.ecocean.media.MediaAsset;
import org.json.JSONObject;

//import java.time.LocalDateTime;

public class Annotation implements java.io.Serializable {
    public Annotation() {}  //empty for jdo
    private String annot_uuid;  //TODO java.util.UUID ?
    private int annot_xtl;
    private int annot_ytl;
    private int annot_width;
    private int annot_height;
    private double annot_theta;
    //*'annot_yaw': 'REAL',
    //~'annot_detect_confidence': 'REAL',
    //~'annot_exemplar_flag': 'INTEGER',
    //~'annot_note': 'TEXT',
    //~'annot_visual_uuid': 'UUID',
    //~'annot_semantic_uuid': 'UUID',
    //*'annot_quality': 'INTEGER',
    //~'annot_tags': 'TEXT',
    private String species_text;
    private String name_text;
    //private String image_uuid;  //TODO UUID?
    private MediaAsset asset;

    //the "trivial" Annotation - its bounding box is the same as the MediaAsset image
    public Annotation(MediaAsset ma, String species) {
        ImageAttributes iatt = ma.getImageAttributes();
        Annotation(ma, species, iatt);
    }

    public Annotation(MediaAsset ma, String species, ImageAttributes iatt) {
        this.asset = ma;
        this.annot_uuid = org.ecocean.Util.generateUUID();
        this.annot_xtl = (int) iatt.getXOffset();
        this.annot_ytl = (int) iatt.getYOffset();
        this.annot_width = (int) iatt.getWidth(); 
        this.annot_height = (int) iatt.getHeight();
        this.annot_theta = 0;  /// TODO ????
        this.species_text = species;
        this.image_uuid = ma.getUUID();
        this.name_text = this.annot_uuid + " on " + this.image_uuid;
    }

    public MediaAsset getMediaAsset() {
        return asset;
    }

    public JSONObject toJSONObject() {
        JSONObject obj = new JSONObject();
        obj.put("annot_uuid", annot_uuid);
        obj.put("annot_xtl", annot_xtl);
        obj.put("annot_ytl", annot_ytl);
        obj.put("annot_width", annot_width);
        obj.put("annot_height", annot_height);
        obj.put("annot_theta", annot_theta);
        obj.put("species_text", species_text);
        obj.put("image_uuid", image_uuid);
        obj.put("name_text", name_text);
        return obj;
    }
}
