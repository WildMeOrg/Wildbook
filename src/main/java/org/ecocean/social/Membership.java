package org.ecocean.social;

import javax.servlet.http.HttpServletRequest;

import org.datanucleus.api.rest.orgjson.JSONException;
import org.datanucleus.api.rest.orgjson.JSONObject;
import org.ecocean.MarkedIndividual;
import org.ecocean.Util;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.JSONArray;

public class Membership implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private String id = Util.generateUUID();
    private MarkedIndividual mi;
    private String role = null;
    private Long startDate = null;
    private Long endDate = null;

    public Membership() {}

    public Membership(MarkedIndividual mi, String role, Long startDate, Long endDate) {
        if (mi==null) throw new NullPointerException("MarkedIndividual for membership cannot be null.");
        this.mi = mi;
        if (role!=null&&!"".equals(role)) {
            this.role = role;
        }
        if (startDate!=null) {
            this.startDate = startDate;
        }
        if (endDate!=null) {
            this.endDate = endDate; 
        }
    }

    public Membership(MarkedIndividual mi, String role, Long startDate) {
        if (mi==null) throw new NullPointerException();
        this.mi = mi;
        if (role!=null&&!"".equals(role)) {
            this.role = role;
        }
        if (startDate!=null) {
            this.startDate = startDate;
        }
    }

    public Membership(MarkedIndividual mi, String role) {
        if (mi==null) throw new NullPointerException();
        this.mi = mi;
        if (role!=null&&!"".equals(role)) {
            this.role = role;
        }
    }

    public Membership(MarkedIndividual mi) {
        if (mi==null) throw new NullPointerException();
        this.mi = mi;
    }

    public MarkedIndividual getMarkedIndividual() {
        return mi;
    }

    public String getMarkedIndividualDisplayName() {
        return mi.getDisplayName();
    }

    public String getRole() {
        return role;
    }    

    public Long getStartDateLong() {
        return startDate;
    }

    public Long getEndDateLong() {
        return endDate;
    }

    public String getStartDate() { 
        if (startDate!=null) {
            DateTime dt = new DateTime(startDate);
            DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-mm-dd");
            return dt.toString(fmt);
        }
        return null;
    }

    public String getEndDate() {
        if (startDate!=null) {
            DateTime dt = new DateTime(startDate);
            DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-mm-dd");
            return dt.toString(fmt);
        }
        return null;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setStartDate(long startDate) {
        this.startDate = startDate;
    }

    public void setEndDate(long endDate) {
        this.endDate = endDate;
    }

    public String getId() {
        return id;
    }
    
    // Returns a somewhat rest-like JSON object containing the metadata
    public JSONObject uiJson(HttpServletRequest request) throws JSONException {
      JSONObject jobj = new JSONObject();
      jobj.put("id", this.getId());
      jobj.put("role", this.getRole());
      jobj.put("startDate", this.getStartDate());
      jobj.put("endDate", this.getEndDate());
      jobj.put("mi", this.getMarkedIndividual().uiJson(request,false));
      return sanitizeJson(request,decorateJson(request, jobj));
    }
    
    public JSONObject sanitizeJson(HttpServletRequest request, JSONObject jobj) throws JSONException {
      if (mi!=null && mi.canUserAccess(request)) return jobj;
      jobj.remove("mi");
      jobj.put("_sanitized", true);
      return jobj;
    }

    public JSONObject decorateJson(HttpServletRequest request, JSONObject jobj) throws JSONException {
      return jobj;
    }


}