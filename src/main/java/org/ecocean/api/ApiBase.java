package org.ecocean.api;

import org.ecocean.Util;
import org.ecocean.User;
import org.ecocean.Organization;

import java.util.List;
import org.json.JSONObject;
import javax.servlet.http.HttpServletRequest;

public abstract class ApiBase implements java.io.Serializable {
    private String id = null;
    private long version = 0l;
    private User creator = null;
    private List<Organization> organizations = null;


    public ApiBase() {
        id = Util.generateUUID();
        version = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public long getVersion() {
        return version;
    }
    public User getCreator() {
        return creator;
    }
    public void setCreator(User u) {
        creator = u;
    }
    public List<Organization> getOrganizations() {
        return organizations;
    }
    public void setOrganizations(List<Organization> orgs) {
        organizations = orgs;
    }

    public abstract String description();
}

