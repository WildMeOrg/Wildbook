package org.ecocean;

import org.ecocean.Util;
//import org.ecocean.external.ExternalSubmission;
import org.json.JSONObject;

public class SubmissionContentReference implements java.io.Serializable {
    protected String parametersAsString;

    public SubmissionContentReference() {
    }
    public SubmissionContentReference(final JSONObject params) {
        this.setParameters(params);
    }
    public JSONObject getParameters() {
        return Util.stringToJSONObject(parametersAsString);
    }
    public void setParameters(JSONObject p) {
        if (p == null) {
            parametersAsString = null;
        } else {
            parametersAsString = p.toString();
        }
    }


    public String toString() {
        return parametersAsString;
    }
}
