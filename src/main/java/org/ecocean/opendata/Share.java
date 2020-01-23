/*
    optimistically named "Open Data" package will be for real-time sharing of data (e.g. OBIS, GBIF, etc)
*/

package org.ecocean.opendata;

import java.util.Properties;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import org.ecocean.Shepherd;
import org.ecocean.CommonConfiguration;
import org.ecocean.ShepherdProperties;
import org.ecocean.Util;
import org.ecocean.User;
import org.ecocean.Organization;

public abstract class Share {
    private static final String PROP_FILE = "opendata.properties";
    private static final String LICENSE_CC_BY_NC = "CC-BY-NC";
    private static String wbGUID = null;  //cache for getGUID()

    //we cache these (via init()) so that we dont have to read all the time (e.g. in isShareable())
    private User collaborationUser = null;
    private boolean triedCollaborationUser = false;
    private List<User> orgUsers = null;  //get the users within share organization
    private Boolean shareAll = null;

    protected String context = null;

    protected Share(final String context) {
        if (context == null) throw new IllegalArgumentException("need context");
        this.context = context;
    }

    public abstract void init();

    public String typeCode() {
        return this.getClass().getSimpleName();
    }

    public boolean isEnabled() {
        return Util.booleanNotFalse(getProperty("enabled", "false"));
    }


    //does the work of making the thing and "sending" it (as applicable)
    public abstract void generate() throws java.io.IOException;

    //can this item be shared in this capacity?
    public abstract boolean isShareable(Object obj);

    public String getProperty(String label) {  //no-default
        return getProperty(label, (String)null);
    }
    public String getProperty(String label, String def) {
        Properties p = getProperties();
        if (p == null) {
            System.out.println("Share.getProperty(" + label + ") has no properties; opendata.properties unavailable?");
            return null;
        }
        return p.getProperty(typeCode() + "." + label, def);
    }
    private Properties getProperties() {
        if (context == null) throw new IllegalArgumentException("must have context set");
        try {
            return ShepherdProperties.getProperties(PROP_FILE, "", context);
        } catch (Exception ex) {
            return null;
        }
    }

    public User getCollaborationUser() {
        if (collaborationUser != null) return collaborationUser;
        if (triedCollaborationUser) return null;  //dont need to check
        triedCollaborationUser = true;
        String uid = getProperty("collaborationUser", null);
        if (uid == null) return null;
        Shepherd myShepherd = new Shepherd(context);
        collaborationUser = myShepherd.getUserByUUID(uid);
        return collaborationUser;
    }

    public Organization getShareOrganization() {
        if (orgUsers == null) orgUsers = new ArrayList<User>();  //means we at least tried once!  (see methods below)
        String oid = getProperty("organizationId", null);
        if (oid == null) return null;
        Shepherd myShepherd = new Shepherd(context);
        Organization org = Organization.load(oid, myShepherd);
        if (org == null) return null;
        if (org.getMembers() != null) orgUsers = org.getMembers();
        return org;
    }
    public List<User> getShareOrganizationUsers() {  //note: this does NOT do deep traversal of members
        if (orgUsers != null) return orgUsers;
        Organization org = getShareOrganization();  //this will initialize orgUsers to empty ArrayList
        if (org == null) return orgUsers;
        if (org.getMembers() != null) orgUsers = org.getMembers();
        return orgUsers;
    }
    public boolean isShareOrganizationUser(User user) {
        if (user == null) return false;
        return getShareOrganizationUsers().contains(user);
    }
    public boolean isShareOrganizationUser(List<User> users) {
        if ((users == null) || (users.size() < 1)) return false;
        if (getShareOrganizationUsers().size() < 1) return false;
        return !Collections.disjoint(users, getShareOrganizationUsers());
    }

    public boolean getShareAll() {
        if (shareAll != null) return shareAll;
        shareAll = Util.booleanNotFalse(getProperty("shareAll", "false"));
        return shareAll;
    }

    //wildbook universal GUID, cached for convenience
    public String getGUID() {
        if (wbGUID != null) return wbGUID;
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.beginDBTransaction();
        wbGUID = CommonConfiguration.getGUID(myShepherd);
        myShepherd.commitDBTransaction();
        return wbGUID;
    }

    //"long" version which is combination of this wildbook uuid + object id (of your choice)
    public String getGUID(String id) {
        if (id == null) return null;
        return getGUID() + ":" + id;
    }

    public void log(String msg) {
        System.out.println(new org.joda.time.DateTime() + " [" + this.typeCode() + "] " + msg);
    }

}
