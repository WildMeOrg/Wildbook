package org.ecocean.api.submission;

import org.ecocean.User;
import org.ecocean.auth.ServiceKeyAuth;
import org.ecocean.auth.ServiceKeyException;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.submission.Submission;
import org.json.JSONObject;

/**
 * Who owns what a service-account submission imports. A draft owned by an organization's service
 * account may name, once, the collector: {@code public}, or a direct member of that organization
 * who is not a service account. Everything is read fresh from the datastore, and nothing here ever
 * creates a User.
 */
public final class SubmissionAttribution {
    public static final String FIELD = "onBehalfOf";
    public static final String PUBLIC = "public";
    public static final String NOT_ALLOWED = "SUBMITTER_NOT_ALLOWED";
    public static final String NOT_ALLOWED_MESSAGE = "onBehalfOf must be public or a member of this organization";
    public static final String OWNER_NOT_ALLOWED = "ATTRIBUTION_NOT_ALLOWED";

    private SubmissionAttribution() {}

    /** The collector the draft names, or null when it names none. */
    public static String requested(Submission draft) {
        if (draft == null || draft.getCreateJson() == null) return null;
        return new JSONObject(draft.getCreateJson()).optString(FIELD, null);
    }

    /** The organization the draft owner serves; 403 unless the owner is (still) a service account. */
    public static String serviceOrganization(Shepherd sh, String ownerId) {
        User owner;
        try {
            owner = ServiceKeyAuth.fresh(sh.getPM(), User.class, "uuid == :id", ownerId);
        } catch (ServiceKeyException ex) {
            throw unavailable();
        }
        String organizationId = owner == null ? null : owner.getServiceOrganizationId();
        if (organizationId == null)
            throw new SubmissionException(403, OWNER_NOT_ALLOWED, "Only an organization service key may submit on behalf of someone");
        return organizationId;
    }

    /**
     * The collector as {username, userId, organizationId} (both ids JSON null for public), or null
     * when the name is not allowed. The name must match exactly.
     */
    public static JSONObject collector(Shepherd sh, String organizationId, String name) {
        if (name == null || organizationId == null) return null;
        if (PUBLIC.equals(name))
            return new JSONObject().put("username", PUBLIC).put("userId", JSONObject.NULL).put("organizationId", JSONObject.NULL);
        try {
            User user = ServiceKeyAuth.fresh(sh.getPM(), User.class, "username == :name", name);
            if (user == null || user.getId() == null || !name.equals(user.getUsername())) return null;
            if (user.getServiceOrganizationId() != null) return null;
            if (!ServiceKeyAuth.directOrganizationIds(sh.getPM(), user.getId()).contains(organizationId)) return null;
            return new JSONObject().put("username", user.getUsername()).put("userId", user.getId()).put("organizationId", organizationId);
        } catch (ServiceKeyException ex) {
            throw unavailable();
        }
    }

    /** The collector for an attributed draft, or null when the draft names none; resolves the owner first. */
    public static JSONObject resolve(Shepherd sh, Submission draft) {
        String name = requested(draft);
        if (name == null) return null;
        return collector(sh, serviceOrganization(sh, draft.getOwnerId()), name);
    }

    private static SubmissionException unavailable() {
        return new SubmissionException(503, "CAPABILITY_UNAVAILABLE", "Account check unavailable; retry later");
    }
}
