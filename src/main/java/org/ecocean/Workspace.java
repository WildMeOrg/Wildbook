package org.ecocean;

import org.ecocean.WBQuery;

import org.datanucleus.api.rest.orgjson.JSONObject;
import org.datanucleus.api.rest.orgjson.JSONArray;
import org.datanucleus.api.rest.orgjson.JSONException;

import org.apache.commons.codec.digest.DigestUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Vector;
import java.util.HashMap;
import java.util.GregorianCalendar;
import java.lang.Math;
import java.io.*;
import java.lang.reflect.Field;
import javax.jdo.Query;

import javax.servlet.http.HttpServletRequest;

import org.ecocean.genetics.*;
import org.ecocean.tag.AcousticTag;
import org.ecocean.tag.MetalTag;
import org.ecocean.tag.SatelliteTag;
import org.ecocean.Util;
import org.ecocean.servlet.ServletUtilities;

import org.ecocean.media.*;

import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.ecocean.security.Collaboration;
import org.ecocean.servlet.ServletUtilities;

// A workspace simply saves arguments to the TranslateQuery servlet, attaching a name to them.
public class Workspace implements java.io.Serializable {

  static final long serialVersionUID = -146404246317121604L;

  public int id;

  public String name;
  public String owner;
  public JSONObject queryArg;
  public String queryAsString;

  public Date created;
  public Date modified;
  public Date accessed;

  // workspaces associated with particular MediaAssetSets might need to be
  // treated differently by the UI. We want that to be jdo-queryable. Hence,
  //private boolean isImageSet = false;

  /**
   * empty constructor used by JDO Enhancer - DO NOT USE
   */
  public Workspace() {
  }

  public Workspace(String name, String owner, JSONObject arg) {
    System.out.println("BEGIN WORKSPACE CREATION");
    this.name = name;
    this.owner = owner;
    this.queryArg = arg;
    this.queryAsString = this.queryArg.toString();
    this.created = new Date();
    this.modified = new Date();
    this.accessed = new Date();

    //this.isImageSet = "org.ecocean.media.MediaAssetSet".equals(this.queryArg.optString("class"));
  }

  public int getID() {
    return this.id;
  }
  public String getName() {
    return this.name;
  }
  public String getOwner() {
    return this.owner;
  }

  public void setArg(JSONObject arg) {
    this.queryArg = arg;
    this.queryAsString = this.queryArg.toString();
    //this.isImageSet = "org.ecocean.media.MediaAssetSet".equals(this.queryArg.optString("class"));
  }

  public JSONObject getArgJson() throws JSONException {
    return new JSONObject(this.queryAsString);
  }

  public String getArgs() {
    return this.queryAsString;
  }

  public void setCreated(Date created) {
    this.created = created;
    this.modified = created;
    this.accessed = created;
  }
  public Date getCreated() {
    return this.created;
  }

  public void setModified(Date modified) {
    this.modified = modified;
    this.accessed = modified;
  }
  public Date getModified() {
    return this.modified;
  }

  public void setAccessed(Date accessed) {
    this.accessed = accessed;
  }
  public Date getAccessed() {
    return this.accessed;
  }
/*
  public boolean getIsImageSet() {
    return this.isImageSet;
  }
*/
  public boolean computeIsImageSet() throws JSONException {
    //this.isImageSet = "org.ecocean.media.MediaAssetSet".equals(this.queryArg.optString("class"));
    JSONObject json = this.getArgJson();
    return (json!=null && json.optString("class").equals("org.ecocean.media.MediaAssetSet"));
  }

}
