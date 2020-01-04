package org.ecocean;

import org.json.JSONObject;
//import org.apache.commons.lang3.builder.ToStringBuilder;

public class Decision {

    private int id;
    private User user;
    private Encounter encounter;
    private long timestamp;
    private String property;
    private String value;

    public Decision() {
        this.timestamp = System.currentTimeMillis();
    }
    public Decision(User user, Encounter enc, String property, JSONObject value) {
        this.timestamp = System.currentTimeMillis();
        this.user = user;
        this.encounter = enc;
        this.property = property;
        if (value != null) this.value = value.toString();
    }

    public int getId() {
        return id;
    }

    public User getUser() {
        return user;
    }
    public void setUser(User u) {
        user = u;
    }

    public Encounter getEncounter() {
        return encounter;
    }
    public void setEncounter(Encounter enc) {
        encounter = enc;
    }

    public String getProperty() {
        return property;
    }
    public void setProperty(String prop) {
        property = prop;
    }

    public JSONObject getValue() {
        return Util.stringToJSONObject(value);
    }
    public void setValue(JSONObject val) {
        if (val == null) {
            value = null;
        } else {
            value = val.toString();
        }
    }

    public long getTimestamp() {
        return timestamp;
    }


/*
    public String toString() {
        return new ToStringBuilder(this)
                .append(indexname)
                .append(readableName)
                .toString();
    }
*/

}
