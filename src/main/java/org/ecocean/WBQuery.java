package org.ecocean;

import org.ecocean.Util;
import org.ecocean.media.MediaAsset;
import org.json.JSONObject;

import java.util.List;
import javax.jdo.Query;

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
            //System.out.println("WARNING: attempted to set null parameters on " + this + "; ignoring");
            return;
        }
        parameters = p;
        parametersAsString = p.toString();
    }

    //this *should* magically return a List of the proper classed object. good luck with that!
    public List<Object> doQuery(Shepherd myShepherd) throws RuntimeException {
        Query query = toQuery(myShepherd);
        return (List<Object>) query.execute();
    }


/* something like this?
    WBQuery qry = new WBQuery(new JSONObject("{ \"foo\" : \"bar\" }"));
    List<Object> res = qry.doQuery(myShepherd);
*/
    public Query toQuery(Shepherd myShepherd) throws RuntimeException {
        Query query = null;
        try {  //lets catch any shenanigans that happens here, and throw our own RuntimeException
            query = myShepherd.getPM().newQuery(toJDOQL());
            query.setClass(getCandidateClass());
            querySetRange(query);
            querySetOrdering(query);
        } catch (Exception ex) {
            throw new RuntimeException(ex.toString());
        }
        return query;
    }

    //TODO
    public String toJDOQL() {
        /////getParameters() will give the JSONObject we need to magically turn into JDOQL!!
        return "SELECT FROM org.ecocean.media.MediaAsset";
    }

    public Class getCandidateClass() throws java.lang.ClassNotFoundException {
        String className = null;
        if (getParameters() != null) className = getParameters().optString("class");
        if (className == null) throw new ClassNotFoundException("missing class name in query");
        return Class.forName(className);  //this also will throw Exception if no good
    }

    //TODO
    public void querySetRange(Query query) {
        query.setRange(0,10);
    }

    //TODO
    public void querySetOrdering(Query query) {
        query.setOrdering("id DESC");
    }

    public long setRevision() {
        this.revision = System.currentTimeMillis();
        return this.revision;
    }
}
