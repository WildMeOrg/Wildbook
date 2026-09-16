package org.ecocean;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * <code>User</code> stores information about a contact/user. Examples: photographer, submitter
 * @author Ed Stastny
 */
public class Role implements java.io.Serializable {
    private static final long serialVersionUID = -7034712240056255450L;

    /**
     * The role names Wildbook defines itself, highest privilege first. They are seeded at first
     * startup (StartupWildbook), ranked when merging two users (UserConsolidate), and excluded
     * from location-based role matching (LocationRoleAccess) so that a location id which happens
     * to collide with one of them is never read as a location grant.
     *
     * This is not the list of roles an admin may assign: that is the indexed "roleN" property in
     * commonConfiguration.properties, which installs extend with their own location role names
     * (see appadmin/users.jsp). Site configuration must not change what counts as a system role,
     * because the names below are also hard-coded in the Shiro rules in web.xml.
     */
    public static final List<String> SYSTEM_ROLES = Collections.unmodifiableList(
        Arrays.asList("admin", "orgAdmin", "researcher", "rest", "machinelearning"));

    /** SYSTEM_ROLES as a set, for membership tests where the hierarchy order does not matter. */
    public static final Set<String> SYSTEM_ROLE_NAMES = Collections.unmodifiableSet(
        new LinkedHashSet<String>(SYSTEM_ROLES));

    private String username;
    private String rolename;
    private String context;

    // JDOQL required empty instantiator
    public Role() {}

    public Role(String username, String rolename) {
        this.username = username;
        this.rolename = rolename;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRolename() {
        return rolename;
    }

    public void setRolename(String rolename) {
        this.rolename = rolename;
    }

    public String getContext() {
        if (context == null) return "context0";
        return context;
    }

    public void setContext(String newContext) {
        this.context = newContext;
    }
}
