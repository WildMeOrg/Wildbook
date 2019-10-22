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
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import org.apache.commons.lang3.builder.ToStringBuilder;
/*
//import java.util.UUID;   :(
import javax.jdo.Query;
*/

public class Organization implements java.io.Serializable {
    public static final String ROLE_ADMIN = "orgSuper";  //this role (and "admin") can edit any org
    public static final String ROLE_MANAGER = "orgAdmin";  //this role can edit orgs they are members of

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

    // if individualNameKey != null, this organization has a special nameKey that is used to create an org-wide catalog
    // e.g. IndoCet wants to generate IndoCet names;
    private String individualNameKey = null;
    
    public Organization() {
        this((String)null);
    }
    public Organization(String name) {
        this.id = Util.generateUUID();
        this.name = name;
        created = System.currentTimeMillis();
        this.updateModified();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
    public void setName(String n) {
        name = n;
        this.updateModified();
    }

    public String getUrl() {
        return url;
    }
    public void setUrl(String u) {
        url = u;
        this.updateModified();
    }

    public String getDescription() {
        return description;
    }
    public void setDescription(String d) {
        description = d;
        this.updateModified();
    }

    public MediaAsset getLogo() {
        return logo;
    }
    public void setLogo(MediaAsset ma) {
        logo = ma;
        this.updateModified();
    }

    public List<User> getMembers() {
        return members;
    }
    public void setMembers(List<User> u) {
        members = u;
        this.membersReciprocate(u);
        this.updateModified();
    }
    public void addMember(User u) {
        if (u == null) return;
        if (members == null) members = new ArrayList<User>();
        if (!members.contains(u)) members.add(u);
        this.membersReciprocate(u);
        this.updateModified();
    }
    public int addMembers(List<User> ulist) {
        int ct = 0;
        if ((ulist == null) || (ulist.size() < 1)) return 0;
        if (members == null) members = new ArrayList<User>();
        for (User mem : ulist) {
            if (!members.contains(mem)) {
                members.add(mem);
                ct++;
            }
        }
        this.membersReciprocate(ulist);
        this.updateModified();
        return ct;
    }
    public void removeMember(User u) {
        if ((u == null) || (members == null)) return;
        if (u.getOrganizations() == null) return;
        u.getOrganizations().remove(this);
        members.remove(u);
        this.updateModified();
    }
    public void removeMembers(List<User> ulist) {
        if ((members == null) || (ulist == null)) return;
        members.removeAll(ulist);
        //now the other end (thx dn)
        for (User u : ulist) {
            u.getOrganizations().remove(this);
        }
        this.updateModified();
    }
    //this removes user for this org and any suborgs (returns how many removed from)
    public int removeMemberDeep(User u) {
        if (u == null) return 0;
        int ct = 0;
        if (this.members.contains(u)) {
            ct++;
            this.removeMember(u);
        }
        if (!this.hasChildren()) return ct;
        for (Organization org : this.children) {
            if (org == null) continue;
            ct += org.removeMemberDeep(u);
        }
        return ct;
    }
    public int numMembers() {
        if (members == null) return 0;
        return members.size();
    }
    public boolean hasMembers() {
        return (numMembers() > 0);
    }

    public String getIndividualNameKey() {
        return individualNameKey;
    }
    public void setIndividualNameKey(String nameKey) {
        individualNameKey = nameKey;
    }

    public List<Organization> getChildren() {
        return children;
    }
    //note: setChildren() and addChild() will not complete if (any) child is related, to prevent looping
    //  return success (false would mean related/loop issue)
    public boolean setChildren(List<Organization> kids) {
        if (kids != null) {
            for (Organization org : kids) {
                if (this.isRelatedTo(org)) {
                    System.out.println("WARNING: " + this + " .setChildren() failed due to related to " + org);
                    return false;
                }
            }
        }
        children = kids;
        this.updateModified();
        return true;
    }
    //if this returns null, failed due to relatedness
    public List<Organization> addChild(Organization kid) {
        if (this.isRelatedTo(kid)) {
            System.out.println("WARNING: " + this + " .addChild(" + kid + ") failed due to relatedness");
            return null;
        }
        if (children == null) children = new ArrayList<Organization>();
        if (kid == null) return children;
        if (!children.contains(kid)) {
            children.add(kid);
            kid.parent = this;
        }
        this.updateModified();
        return children;
    }

    //returns true if successfully removed
    public boolean removeChild(Organization kid) {
        if ((kid == null) || (children == null)) return false;
        if (!children.contains(kid)) return false;
        children.remove(kid);
        kid.parent = null;
        this.updateModified();
        return true;
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

    //checks to see if passed org exists above us in tree
    // note: this will return true if passed self
    // XXX this assumes no looping!!! XXX
    public boolean hasAncestor(Organization org) {
        if (org == null) return false;
        if (org.equals(this)) return true;
        if (parent == null) return false;
        return parent.hasAncestor(org);
    }
    //they are in each others tree if:
    public boolean isRelatedTo(Organization org) {
        if (org == null) return false;
        return (this.hasAncestor(org) || org.hasAncestor(this));
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

    //  logic basically goes like this:  (1) "admin" and ROLE_ADMIN role can touch any org; (2) ROLE_MANAGER role can affect any org *they are in*
    public boolean canManage(User user, Shepherd myShepherd) {
        if (user == null) return false;
        if (user.hasRoleByName("admin", myShepherd) || user.hasRoleByName(ROLE_ADMIN, myShepherd)) return true;
        if (!this.hasMember(user)) return false;  //TODO should this be .hasMemberDeep() ?
        return user.hasRoleByName(ROLE_MANAGER, myShepherd);
    }
    //this is for visibility of the org at all (for now all are assumed private; maybe later we can have a property on org?)
    public boolean canView(User user, Shepherd myShepherd) {
        if (this.canManage(user, myShepherd)) return true;
        return this.hasMember(user);
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


    //pass in another org and effectively take over its content.
    //  note: this doesnt kill the other org - that must be done manually (if desired)
    public int mergeFrom(Organization other) {
        int ct = this.addMembers(other.members);  //really very simple for now
        for (User mem : other.members) {
            mem.addOrganization(this);
        }
        this.updateModified();
        return ct;
    }


    //this is to handle the bidirectional dn madness when *adding* members
    //  (removing are handled internally above)
    private void membersReciprocate(List<User> mems) {
        if (mems == null) return;
        for (User mem : mems) {
            if ((mem.getOrganizations() != null) && !mem.getOrganizations().contains(this)) mem.getOrganizations().add(this);
        }
    }
    private void membersReciprocate(User mem) {
        if (mem == null) return;
        List<User> mems = new ArrayList<User>();
        mems.add(mem);
        membersReciprocate(mems);
    }



    public void updateModified() {
        modified = System.currentTimeMillis();
    }

    public JSONObject toJSONObject() {
        return this.toJSONObject(false);
    }
    public JSONObject toJSONObject(boolean includeChildren) {
        JSONObject j = new JSONObject();
        j.put("id", id);
        j.put("name", name);
        j.put("url", url);
        if (logo != null) j.put("logo", logo.toSimpleJSONObject());
        j.put("description", description);
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
        Organization parent = this.getParent();
        if (parent != null) j.put("parentId", parent.getId());
        return j;
    }

    //basically mean uuid-equivalent, so deal
    public boolean equals(final Object u2) {
        if (u2 == null) return false;
        if (!(u2 instanceof Organization)) return false;
        Organization two = (Organization)u2;
        if ((this.id == null) || (two == null) || (two.getId() == null)) return false;
        return this.id.equals(two.getId());
    }
    public int hashCode() {  //we need this along with equals() for collections methods (contains etc) to work!!
        if (id == null) return Util.generateUUID().hashCode();  //random(ish) so we dont get two users with no uuid equals! :/
        return id.hashCode();
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

