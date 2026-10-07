package org.ecocean.api.submission;

import javax.jdo.Query;
import org.ecocean.Role;
import org.ecocean.User;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.CommonConfiguration;

/** Installation-local pilot controls. Read access survives admission shutdown. */
public class SubmissionPolicy {
    public static final String READ = "submissions:read";
    public static final String WRITE = "submissions:write";
    public static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    public static final int MAX_ROWS = 200;
    public static final long DRAFT_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000;
    public static boolean enabled(String context) {
        return "true".equalsIgnoreCase(CommonConfiguration.getApiAccessProperty("submissions.enabled", context));
    }
    public static boolean commitEnabled(String context) {
        return "true".equalsIgnoreCase(CommonConfiguration.getApiAccessProperty("submissions.commitEnabled", context));
    }
    public static boolean workerEnabled(String context) {
        return "true".equalsIgnoreCase(CommonConfiguration.getApiAccessProperty("submissions.workerEnabled", context));
    }
    public static boolean enrolled(String context, String userId) {
        if (userId == null) return false;
        Shepherd sh = new Shepherd(context);
        try {
            sh.beginDBTransaction();
            return enrolled(sh, userId);
        } finally { sh.rollbackAndClose(); }
    }

    static boolean enrolled(Shepherd sh, String userId) {
        if (userId == null) return false;
        User user = sh.getUserByUUID(userId);
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) return false;
        // Query persisted grants on every admission check; JWT/session roles are not authority.
        Query<?> query = sh.getPM().newQuery(Role.class,
            "username == :username && rolename == :role && context == :context");
        try {
            query.setIgnoreCache(true);
            query.setResult("count(this)");
            return ((Number)query.execute(user.getUsername(), Role.API_SUBMISSION, sh.getContext())).longValue() > 0;
        } finally { query.closeAll(); }
    }

    public static void requireAdmission(String context, String userId) {
        if (!enabled(context)) throw new SubmissionException(503, "ADMISSION_DISABLED", "Submission admission is disabled");
        if (!enrolled(context, userId)) throw new SubmissionException(403, "ACCESS_DENIED", "Account requires the api-submission role");
    }
}
