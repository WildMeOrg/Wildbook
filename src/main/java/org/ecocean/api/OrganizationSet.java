package org.ecocean.api;

import org.ecocean.Organization;

import java.util.Set;
import java.util.HashSet;
/*
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import org.json.JSONObject;
import org.json.JSONArray;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
*/

public class OrganizationSet implements java.io.Serializable {
    private Set<Organization> set = null;

    public OrganizationSet() {}

    public OrganizationSet(Set<Organization> set) {
        this();
        this.set = set;
    }

    public Set<Organization> getSet() {
        return set;
    }
    public void setSet(Set<Organization> s) {
        set = s;
    }
    public void addOrganization(Organization org) {
        if (set == null) set = new HashSet<Organization>();
        set.add(org);
    }
}

