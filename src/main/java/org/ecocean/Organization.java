/*
    an Organization contains users.  and other Organizations!
*/

package org.ecocean;

import org.ecocean.Shepherd;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.media.MediaAsset;
import org.json.JSONObject;
import org.json.JSONArray;
import org.joda.time.DateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import org.apache.commons.lang3.builder.ToStringBuilder;
/*
//import java.util.UUID;   :(
import javax.jdo.Query;
*/

public class Organization implements java.io.Serializable {

    private String id = null;
    private String name = null;
    private String description = null;
    private String url = null;
    private MediaAsset logo = null;
    private long created = -1;
    private long modified = -1;
    private List<User> members = null;
    private Organization parent = null;
    private List<Organization> children = null;
    
    public Organization() {
        this((String)null);
    }
    public Organization(String name) {
        this.id = Util.generateUUID();
        this.name = name;
        created = System.currentTimeMillis();
        modified = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
    public void setName(String n) {
        name = n;
    }

    public String getUrl() {
        return url;
    }
    public void setUrl(String u) {
        url = u;
    }

    public String getDescription() {
        return description;
    }
    public void setDescription(String d) {
        description = d;
    }

    public MediaAsset getLogo() {
        return logo;
    }
    public void setLogo(MediaAsset ma) {
        logo = ma;
    }

    public List<User> getMembers() {
        return members;
    }
    public void setMembers(List<User> u) {
        members = u;
    }
    public void addMember(User u) {
        if (u == null) return;
        if (members == null) members = new ArrayList<User>();
        if (!members.contains(u)) members.add(u);
    }
    public int numMembers() {
        if (members == null) return 0;
        return members.size();
    }
    public boolean hasMembers() {
        return (numMembers() > 0);
    }

    public List<Organization> getChildren() {
        return children;
    }
    public void setChildren(List<Organization> kids) {
        children = kids;
    }
    public List<Organization> addChild(Organization kid) {
        if (children == null) children = new ArrayList<Organization>();
        if (kid == null) return children;
        if (!children.contains(kid)) children.add(kid);
        return children;
    }

/*  not sure if this is evil ??
    public void setParent(Organization t) {
        parent = t;
    }
*/
    public Organization getParent() {
        return parent;
    }
    public int numChildren() {
        return (children == null) ? 0 : children.size();
    }
    public boolean hasChildren() {
        return (this.numChildren() > 0);
    }

    //omg i am going to assume no looping
    public List<Organization> getLeafOrganizations() {
        List<Organization> leaves = new ArrayList<Organization>();
        if (!this.hasChildren()) {
            leaves.add(this);
            return leaves;
        }
        for (Organization kid : children) {
            leaves.addAll(kid.getLeafOrganizations());
        }
        return leaves;
    }

    public Organization getRootOrganization() {
        if (parent == null) return this;
        return parent.getRootOrganization();
    }

    //takes a bunch of tasks and returns only roots (without duplication)
    public static List<Organization> onlyRoots(List<Organization> all) {
        List<Organization> roots = new ArrayList<Organization>();
        for (Organization o : all) {
            Organization r = o.getRootOrganization();
            if (!roots.contains(r)) roots.add(r);
        }
        return roots;
    }

    //see also hasMemberDeep()
    public boolean hasMember(User u) {
        if ((members == null) || (u == null)) return false;
        return members.contains(u);
    }
    public boolean hasMemberDeep(User u) {
        if (this.hasMember(u)) return true;
        if (!this.hasChildren()) return false;  //no sub-orgs
        for (Organization kid : this.children) {
            boolean m = kid.hasMemberDeep(u);
            if (m) return true;
        }
        return false;
    }

    //TODO this is open for retooling!  e.g. we could have a special Role (OrgMgr whatev)
    public boolean canManage(User user, Shepherd myShepherd) {
        if (user == null) return false;
        if (user.getUsername().equals("tomcat")) return true;  //need someway to kickstart adding users to groups!
        if (!this.hasMemberDeep(user)) return false;  //basically user *must* be in group (hence tomcat clause above)
        return user.hasRoleByName("admin", myShepherd);
    }

    //do we recurse?  i think so... you would want a child org (member) to see what you named something
    public Set<String> getMultiValueKeys() {
        Set<String> keys = new HashSet();
        keys.add("_ORG_:" + this.id);
        if (this.hasChildren()) {
            for (Organization kid : this.children) {
                keys.addAll(kid.getMultiValueKeys());
            }
        }
        return keys;
    }

    public JSONObject toJSONObject() {
        return this.toJSONObject(false);
    }
    public JSONObject toJSONObject(boolean includeChildren) {
        JSONObject j = new JSONObject();
        j.put("id", id);
        j.put("name", name);
        j.put("created", created);
        j.put("modified", modified);
        j.put("createdDate", new DateTime(created));
        j.put("modifiedDate", new DateTime(modified));
        if (this.hasMembers()) {
            JSONArray jm = new JSONArray();
            for (User u : this.members) {
                JSONObject ju = new JSONObject();
                ju.put("id", u.getId());
                ju.put("username", u.getUsername());
                ju.put("fullName", u.getFullName());
                jm.put(ju);
            }
            j.put("members", jm);
        }
        if (includeChildren && this.hasChildren()) {
            JSONArray jc = new JSONArray();
            for (Organization kid : this.children) {
                jc.put(kid.toJSONObject(true));  //we once again assume no looping!  bon chance.
            }
            j.put("children", jc);
        }
        return j;
    }

    public String toString() {
        return new ToStringBuilder(this)
                .append(id)
                .append(name)
                .append("(" + new DateTime(created) + "|" + new DateTime(modified) + ")")
                .append(numMembers() + "Mems")
                .append(numChildren() + "Kids")
                .toString();
    }

    public static Organization load(String id, Shepherd myShepherd) {
        Organization o = null;
        try {
            o = ((Organization) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Organization.class, id), true)));
        } catch (Exception ex) {};  //swallow jdo not found noise
        return o;
    }

/*
    public static List<Organization> getOrganizationsFor(Annotation ann, Shepherd myShepherd) {
        String qstr = "SELECT FROM org.ecocean.ia.Organization WHERE objectAnnotations.contains(obj) && obj.id == \"" + ann.getId() + "\" VARIABLES org.ecocean.Annotation obj";
        Query query = myShepherd.getPM().newQuery(qstr);
        query.setOrdering("created");
        return (List<Organization>) query.execute();
    }
    public static List<Organization> getRootOrganizationsFor(Annotation ann, Shepherd myShepherd) {
        return onlyRoots(getOrganizationsFor(ann, myShepherd));
    }

    public static List<Organization> getOrganizationsFor(MediaAsset ma, Shepherd myShepherd) {
        String qstr = "SELECT FROM org.ecocean.ia.Organization WHERE objectMediaAssets.contains(obj) && obj.id == " + ma.getId() + " VARIABLES org.ecocean.media.MediaAsset obj";
        Query query = myShepherd.getPM().newQuery(qstr);
        query.setOrdering("created");
        return (List<Organization>) query.execute();
    }
    public static List<Organization> getRootOrganizationsFor(MediaAsset ma, Shepherd myShepherd) {
        return onlyRoots(getOrganizationsFor(ma, myShepherd));
    }

*/
}

