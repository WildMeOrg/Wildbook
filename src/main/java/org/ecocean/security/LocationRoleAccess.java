package org.ecocean.security;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.ecocean.LocationID;
import org.ecocean.Role;
import org.ecocean.User;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;

/**
 * Location-based roles.
 *
 * A Role whose name equals an encounter's locationID, or the id of an ancestor of that node in
 * the default locationID.json tree, grants view and edit access to the encounter. This is the
 * one place that decides which role names count for a given location, so the live access checks
 * (Encounter.canUserView/canUserEdit, Collaboration.canUserAccessEncounter,
 * ServletUtilities.isUserAuthorizedForEncounter) and both OpenSearch viewUsers writers
 * (Encounter.computeViewUsers and the background permissions pass) agree.
 *
 * Rules:
 * - a role at a node covers that node and its whole subtree; a child-node role never grants the
 *   parent;
 * - lineage always comes from the DEFAULT tree (no per-user/org qualifier), so the decision is
 *   the same for every viewer;
 * - a locationID that is missing from the tree, or that appears under more than one parent
 *   (ambiguous), gets exact-name matching only;
 * - blank ids in the tree are skipped; a null or blank locationID grants nothing;
 * - system role names never count as location roles, even when a location id collides with one.
 *
 * The lineage walk itself is tree geometry with no role concept in it, so it lives on
 * LocationID.lineageFor(); the system role names live on Role.SYSTEM_ROLE_NAMES, shared with
 * StartupWildbook and UserConsolidate.
 */
public final class LocationRoleAccess {
    private LocationRoleAccess() {}

    /** Role names that grant access to an encounter at locationID: itself plus its ancestors
     *  in the default tree, minus system role names. Empty for a null/blank locationID. */
    public static Set<String> roleNamesFor(String locationID) {
        if (isBlank(locationID)) return Collections.emptySet();
        JSONObject tree = null;
        try {
            tree = LocationID.getLocationIDStructure();
        } catch (Exception ex) {
            tree = null; // unreadable tree: exact match only
        }
        return roleNamesFor(locationID, tree);
    }

    static Set<String> roleNamesFor(String locationID, JSONObject tree) {
        Set<String> names = new LinkedHashSet<String>(LocationID.lineageFor(locationID, tree));
        names.removeAll(Role.SYSTEM_ROLE_NAMES);
        return names;
    }

    /** Shiro path: does the request's user hold any role that covers locationID? */
    public static boolean requestHasLocationRole(HttpServletRequest request, String locationID) {
        if (request == null) return false;
        for (String name : roleNamesFor(locationID)) {
            if (request.isUserInRole(name)) return true;
        }
        return false;
    }

    /** Database path, on the caller's Shepherd: does username hold any covering role in the
     *  Shepherd's context? */
    public static boolean userHasLocationRole(String username, String locationID,
        Shepherd myShepherd) {
        if ((username == null) || (myShepherd == null)) return false;
        Set<String> names = roleNamesFor(locationID);
        if (names.isEmpty()) return false;
        return myShepherd.doesUserHaveAnyRole(username, names, myShepherd.getContext());
    }

    /** User UUIDs of everyone holding a covering role in the Shepherd's context (for the
     *  OpenSearch viewUsers field). Users without a resolvable id are skipped. */
    public static Set<String> userIdsWithLocationRole(String locationID, Shepherd myShepherd) {
        Set<String> ids = new LinkedHashSet<String>();
        if (myShepherd == null) return ids;
        Set<String> names = roleNamesFor(locationID);
        if (names.isEmpty()) return ids;
        List<String> usernames = myShepherd.getUsernamesWithAnyRole(names, myShepherd.getContext());
        if (usernames == null) return ids;
        for (String username : usernames) {
            if (username == null) continue;
            User user = myShepherd.getUser(username);
            if ((user != null) && (user.getId() != null)) ids.add(user.getId());
        }
        return ids;
    }

    /**
     * Pure helper for the background permissions pass: the user ids granted by location roles
     * for locationID, given a precomputed role-name -> user-ids map. lineageCache memoizes
     * roleNamesFor per locationID so the tree is walked once per distinct location per pass.
     */
    public static Set<String> viewUserIdsForLocation(String locationID,
        Map<String, Set<String> > roleNameToUserIds, Map<String, Set<String> > lineageCache) {
        Set<String> ids = new LinkedHashSet<String>();
        if (isBlank(locationID) || (roleNameToUserIds == null)) return ids;
        Set<String> names = (lineageCache == null) ? null : lineageCache.get(locationID);
        if (names == null) {
            names = roleNamesFor(locationID);
            if (lineageCache != null) lineageCache.put(locationID, names);
        }
        for (String name : names) {
            Collection<String> users = roleNameToUserIds.get(name);
            if (users != null) ids.addAll(users);
        }
        return ids;
    }

    private static boolean isBlank(String str) {
        return (str == null) || str.trim().isEmpty();
    }
}
