package org.ecocean.customfield;

import org.ecocean.Util;
import org.apache.commons.lang3.builder.ToStringBuilder;
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
    private String name = null;  //this is a human-readable name which is required and needs to be unique
    private String type = null;
    private boolean multiple = false;

    public CustomFieldDefinition() {
        id = Util.generateUUID();
    }
    public CustomFieldDefinition(String className, String type, String name) {
        this();
        this.className = className;
        this.name = name;
        this.type = type;
    }
    public CustomFieldDefinition(String className, String type, String name, boolean mult) {
        this();
        this.className = className;
        this.name = name;
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
    public String getName() {
        return name;
    }
    public void setName(String n) {
        className = n;
    }
    public boolean getMultiple() {
        return multiple;
    }
    public void setMultiple(boolean m) {
        multiple = m;
    }

    public boolean equals(final Object d2) {
        if (d2 == null) return false;
        if (!(d2 instanceof CustomFieldDefinition)) return false;
        CustomFieldDefinition two = (CustomFieldDefinition)d2;
        if ((this.id == null) || (two == null) || (two.getId() == null)) return false;
        return this.id.equals(two.getId());
    }
    public int hashCode() {  //we need this along with equals() for collections methods (contains etc) to work!!
        if (id == null) return Util.generateUUID().hashCode();  //random(ish) so we dont get two users with no uuid equals! :/
        return id.hashCode();
    }

    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("name", name)
                .append("className", className)
                .append("type", type)
                .append("multiple", multiple)
                .toString();
    }
}

