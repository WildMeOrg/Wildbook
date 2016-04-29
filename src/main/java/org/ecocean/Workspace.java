package org.ecocean;

import org.ecocean.WBQuery;

import org.datanucleus.api.rest.orgjson.JSONObject;
import org.datanucleus.api.rest.orgjson.JSONArray;
import org.datanucleus.api.rest.orgjson.JSONException;


// A workspace simply saves arguments to the TranslateQuery servlet, attaching a name to them.
public class Workspace implements java.io.Serializable {

  public String id;
  public JSONObject translateQueryArg;

  public Workspace(String id, JSONObject arg) {
    this.id = id;
    this.translateQueryArg = arg;
  }

  /**
   * empty constructor used by JDO Enhancer - DO NOT USE
   */
  public Workspace() {
  }


}
