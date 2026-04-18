package org.ecocean.identity;

import java.util.concurrent.ConcurrentHashMap;

public final class BulkFinalIdentificationRegistry {
    private static final ConcurrentHashMap<String, Boolean> CLAIMED =
        new ConcurrentHashMap<>();

    private BulkFinalIdentificationRegistry() {}

    public static boolean tryClaim(String taskId) {
        if (taskId == null) return false;
        return CLAIMED.putIfAbsent(taskId, Boolean.TRUE) == null;
    }

    public static void release(String taskId) {
        if (taskId != null) CLAIMED.remove(taskId);
    }

    public static void clearForTesting() {
        CLAIMED.clear();
    }
}
