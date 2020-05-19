package org.ecocean.customfield;

import org.ecocean.Util;
/*
import org.ecocean.User;
import org.ecocean.Organization;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;
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

public class CustomFieldDefinition implements java.io.Serializable {
    private String id = null;
    private String className = null;
    private String type = null;
    private boolean multiple = false;

    public CustomFieldDefinition() {
        id = Util.generateUUID();
    }
    public CustomFieldDefinition(String className, String type) {
        this();
        this.className = className;
        this.type = type;
    }
    public CustomFieldDefinition(String className, String type, boolean mult) {
        this();
        this.className = className;
        this.type = type;
        this.multiple = mult;
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getType() {
        return type;
    }
    public void setType(String t) {
        type = t;
    }
    public String getClassName() {
        return className;
    }
    public void setClassName(String c) {
        className = c;
    }
    public boolean getMultiple() {
        return multiple;
    }
    public void setMultiple(boolean m) {
        multiple = m;
    }

    //public String toString() {  return this.getClass().getName() + ":" + this.id; }
}

