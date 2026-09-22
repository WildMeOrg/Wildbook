package org.ecocean.resumableupload;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * FlowInfoStorage tracks in-progress chunked uploads. It used to be keyed on the client's
 * flowIdentifier alone -- and flow.js's default identifier is just size + "-" + filename, the
 * same every time a given photo is picked. An abandoned upload is never evicted, so re-adding
 * that photo to a NEW submission collided with the stale entry: before the containment fix its
 * chunks were silently written into the abandoned upload's file, and after it every chunk was
 * refused. Keying on identifier + destination makes those two genuinely separate uploads.
 */
class FlowInfoStorageTest {
    private static FlowInfo candidate(String identifier, String stagingPath, int chunkSize,
        long totalSize) {
        FlowInfo info = new FlowInfo();

        info.flowIdentifier = identifier;
        info.flowFilename = "photo.jpg";
        info.flowRelativePath = "photo.jpg";
        info.flowChunkSize = chunkSize;
        info.flowTotalSize = totalSize;
        info.flowFilePath = stagingPath;
        info.finalFilePath = stagingPath.substring(0, stagingPath.length() - 5);
        return info;
    }

    // storage is a JVM-wide singleton, so every test uses identifiers nothing else can collide with
    private static String freshId() {
        return "12345-photojpg-" + UUID.randomUUID();
    }

    @Test void sameIdentifierInADifferentDestinationIsASeparateUpload() {
        FlowInfoStorage storage = FlowInfoStorage.getInstance();
        String id = freshId();
        FlowInfo abandoned = candidate(id, "/up/_anonymous/submission/A/photo.jpg.temp", 5, 12L);
        FlowInfo retry = candidate(id, "/up/_anonymous/submission/B/photo.jpg.temp", 5, 12L);

        assertSame(abandoned, storage.register(abandoned));
        assertSame(retry, storage.register(retry),
            "re-adding the same photo to a new submission must not be blocked by, or written "
            + "into, the abandoned upload that shares its flow.js identifier");
        assertSame(abandoned, storage.lookup(id, abandoned.flowFilePath));
        assertSame(retry, storage.lookup(id, retry.flowFilePath));
        storage.remove(abandoned);
        storage.remove(retry);
    }

    @Test void sameIdentifierAndDestinationShareOneUpload() {
        // concurrent first chunks of one file must converge on a single entry, or chunk marks
        // recorded against the losing object would be lost and the upload would never complete
        FlowInfoStorage storage = FlowInfoStorage.getInstance();
        String id = freshId();
        FlowInfo first = candidate(id, "/up/x/photo.jpg.temp", 5, 12L);
        FlowInfo second = candidate(id, "/up/x/photo.jpg.temp", 5, 12L);

        assertSame(first, storage.register(first));
        assertSame(first, storage.register(second), "the already-registered upload must win");
        storage.remove(first);
    }

    @Test void lookupNeverRegistersAnything() {
        FlowInfoStorage storage = FlowInfoStorage.getInstance();
        String id = freshId();

        assertNull(storage.lookup(id, "/up/x/photo.jpg.temp"));
        assertNull(storage.lookup(id, "/up/x/photo.jpg.temp"),
            "a status probe must not create bookkeeping that a later upload then collides with");
    }

    @Test void removingAStaleReferenceLeavesANewerUploadAlone() {
        FlowInfoStorage storage = FlowInfoStorage.getInstance();
        String id = freshId();
        FlowInfo old = candidate(id, "/up/x/photo.jpg.temp", 5, 12L);

        storage.register(old);
        storage.remove(old);
        FlowInfo newer = candidate(id, "/up/x/photo.jpg.temp", 5, 12L);
        assertSame(newer, storage.register(newer));
        storage.remove(old);
        assertSame(newer, storage.lookup(id, newer.flowFilePath),
            "remove() must only drop the exact entry it was handed");
        storage.remove(newer);
    }

    @Test void geometryIsComparedAcrossRequests() {
        String id = freshId();
        FlowInfo a = candidate(id, "/up/x/photo.jpg.temp", 5, 12L);

        assertTrue(a.sameGeometry(candidate(id, "/up/x/photo.jpg.temp", 5, 12L)));
        assertFalse(a.sameGeometry(candidate(id, "/up/x/photo.jpg.temp", 5, 13L)),
            "a different declared total is a different upload");
        assertFalse(a.sameGeometry(candidate(id, "/up/x/photo.jpg.temp", 4, 12L)));
        assertNotSame(a, candidate(id, "/up/x/photo.jpg.temp", 5, 12L));
    }
}
