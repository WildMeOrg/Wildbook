package org.ecocean.api.submission;

import java.util.Arrays;
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
        String users = CommonConfiguration.getApiAccessProperty("submissions.allowedUserIds", context);
        return userId != null && users != null && Arrays.stream(users.split(","))
            .map(String::trim).anyMatch(userId::equals);
    }
    public static void requireAdmission(String context, String userId) {
        if (!enabled(context)) throw new SubmissionException(503, "ADMISSION_DISABLED", "Submission admission is disabled");
        if (!enrolled(context, userId)) throw new SubmissionException(403, "ACCESS_DENIED", "Account is not enrolled in the pilot");
    }
}
