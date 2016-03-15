package org.ecocean;

import org.ecocean.Util;
import org.json.JSONObject;


public class WBQuery implements java.io.Serializable {

    private static final long serialVersionUID = -7934850478934029842L;

    protected JSONObject parameters;
    protected long id;
    protected String parametersAsString;
    protected String name;
    protected AccessControl owner;
    protected long revision;

    public WBQuery() {
    }

    public WBQuery(final int id, final JSONObject params, final AccessControl owner) {
        this.id = id;
        this.owner = owner;
        this.parameters = params;
        if (params != null) this.parametersAsString = params.toString();
        this.setRevision();
    }

    public WBQuery(final JSONObject params) {
        this(-1, params, null);
    }

    public WBQuery(final JSONObject params, final AccessControl owner) {
        this(-1, params, owner);
    }

    public JSONObject getParameters() {
        if (parameters != null) return parameters;
        //System.out.println("NOTE: getParameters() on " + this + " was null, so trying to get from parametersAsString()");
        JSONObject j = Util.stringToJSONObject(parametersAsString);
        parameters = j;
        return j;
    }

    public void setParameters(JSONObject p) {
        if (p == null) {
            System.out.println("WARNING: attempted to set null parameters on " + this + "; ignoring");
            return;
        }
        parameters = p;
        parametersAsString = p.toString();
    }


    public long setRevision() {
        this.revision = System.currentTimeMillis();
        return this.revision;
    }
}
