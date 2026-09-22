package org.ecocean.resumableupload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Which subdirectory an upload lands in. A valid submissionId pins the destination; otherwise the
 * legacy /import flow's caller-supplied subdir is honoured, but only when it is safe.
 */
class UploadPathsSubdirTest {
    private static final String UUID_A = "6f9619ff-8b86-d011-b42d-00c04fc964ff";

    @Test void validSubmissionIdPinsTheAnonymousSubmissionDirectory() {
        assertEquals("_anonymous/submission/" + UUID_A, UploadPaths.uploadSubdir("ignored", UUID_A),
            "a valid submissionId must win over any caller-supplied subdir");
    }

    @Test void absentSubdirMeansTheUploadRootWithNoSubdirectory() {
        assertEquals("", UploadPaths.uploadSubdir(null, null));
        assertEquals("", UploadPaths.uploadSubdir("   ", null));
    }

    @Test void ordinarySubdirIsKept() {
        // the legacy /import flow passes a username here
        assertEquals("jason", UploadPaths.uploadSubdir("jason", null));
    }

    @Test void traversingSubdirIsRefused() {
        assertNull(UploadPaths.uploadSubdir("../../../opt/tomcat/webapps/ROOT", null),
            "an unsafe subdir must be refused, not silently reduced to the upload root");
        assertNull(UploadPaths.uploadSubdir("a/../../b", null));
        assertNull(UploadPaths.uploadSubdir("..", null));
    }

    @Test void leadingDotIsAllowedBecauseItIsALegalUsername() {
        // User.setUsername imposes no restrictions and UserCreate only trims, so ".alice" and
        // "alice..smith" are real possible usernames -- and the legacy /import flow passes the
        // username here. Rejecting every dot would lock those users out of importing.
        assertEquals(".alice", UploadPaths.uploadSubdir(".alice", null));
    }

    @Test void dotsInsideANameAreAllowedButTraversalComponentsAreNot() {
        assertEquals("alice..smith", UploadPaths.uploadSubdir("alice..smith", null),
            "'..' inside a component is not traversal");
        assertNull(UploadPaths.uploadSubdir("alice/../../etc", null),
            "'..' as a whole component is traversal");
    }

    @Test void emptyAndDotComponentsAreRefused() {
        assertNull(UploadPaths.uploadSubdir("a//b", null));
        assertNull(UploadPaths.uploadSubdir("a/./b", null));
    }

    @Test void leadingSlashIsStrippedSoSubdirCannotGoAbsolute() {
        assertEquals("tmp/x", UploadPaths.uploadSubdir("/tmp/x", null));
    }

    @Test void nonUuidSubmissionIdFallsBackToSubdirRatherThanBeingTrusted() {
        assertNull(UploadPaths.uploadSubdir("../evil", "not-a-uuid"));
        assertEquals("jason", UploadPaths.uploadSubdir("jason", "not-a-uuid"));
    }
}
