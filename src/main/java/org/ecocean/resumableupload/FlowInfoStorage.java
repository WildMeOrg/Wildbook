package org.ecocean.resumableupload;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-progress chunked uploads, keyed on the client's flowIdentifier AND the validated staging path.
 *
 * Keying on the identifier alone was wrong: flow.js's default identifier is size + "-" + filename,
 * identical every time a given photo is picked, and entries are only dropped when an upload
 * completes. So an abandoned upload's entry captured every later upload of the same photo, even
 * into a different submission -- writing its chunks into the abandoned upload's file, or (once
 * that was detected) refusing it outright. Including the destination makes those separate uploads,
 * and means guessing someone's identifier gains nothing unless you also share their destination.
 *
 * Entries still are not evicted on abandonment; that is a known, separate problem.
 */
public class FlowInfoStorage {
    private FlowInfoStorage() {}

    private static FlowInfoStorage sInstance;

    public static synchronized FlowInfoStorage getInstance() {
        if (sInstance == null) {
            sInstance = new FlowInfoStorage();
        }
        return sInstance;
    }

    private final ConcurrentHashMap<String, FlowInfo> mMap = new ConcurrentHashMap<String, FlowInfo>();

    /** The storage key for an upload. NUL cannot occur in a path, so the split is unambiguous. */
    static String keyFor(String flowIdentifier, String flowFilePath) {
        return String.valueOf(flowIdentifier) + '\u0000' + String.valueOf(flowFilePath);
    }

    /** The registered upload for this identifier and destination, or null. Never registers. */
    public FlowInfo lookup(String flowIdentifier, String flowFilePath) {
        return mMap.get(keyFor(flowIdentifier, flowFilePath));
    }

    /**
     * Registers the candidate unless an upload for the same identifier and destination already
     * exists, and returns whichever one the caller must use. Atomic, so concurrent first chunks of
     * one file converge on a single entry.
     */
    public FlowInfo register(FlowInfo candidate) {
        FlowInfo existing = mMap.putIfAbsent(candidate.storageKey(), candidate);

        return (existing == null) ? candidate : existing;
    }

    /** Removes exactly this entry; a stale reference cannot remove a newer upload under its key. */
    public void remove(FlowInfo info) {
        mMap.remove(info.storageKey(), info);
    }
}
