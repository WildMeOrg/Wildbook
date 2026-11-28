package org.ecocean.ia;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ecocean.Encounter;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.User;

/**
 * Who is looking at a match-results tree, and which encounters' location IDs they may see.
 *
 * A location ID is shown when the encounter is publicly readable (anonymous owner, or collaboration
 * security turned off), the viewer owns it, or Encounter.canUserView() allows it (admin, location
 * role, approved collaboration including the assumed orgAdmin ones). A null viewer sees none.
 *
 * One task can serialize hundreds of candidates and canUserView() may open a Shepherd per call, so
 * after the per-encounter owner check the answer is cached by (assigned username, location ID): the
 * only encounter fields the rest of canUserView() reads. Use one instance per request.
 */
public final class MatchResultViewer {
    private final User viewer;
    private final Shepherd myShepherd;
    private final Map<List<String>, Boolean> memo = new HashMap<List<String>, Boolean>();

    public MatchResultViewer(User viewer, Shepherd myShepherd) {
        this.viewer = viewer;
        this.myShepherd = myShepherd;
    }

    public boolean canSeeLocation(Encounter enc) {
        if ((viewer == null) || (enc == null)) return false;
        if (enc.isPubliclyReadable() || enc.isUserOwner(viewer)) return true;
        // a List key keeps null distinct from the string "null"
        List<String> key = Arrays.asList(enc.getAssignedUsername(), enc.getLocationID());
        Boolean allowed = memo.get(key);
        if (allowed == null) {
            allowed = enc.canUserView(viewer, myShepherd);
            memo.put(key, allowed);
        }
        return allowed;
    }
}
