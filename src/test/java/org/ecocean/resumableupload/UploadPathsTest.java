package org.ecocean.resumableupload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * UploadPaths guards the only place a chunked upload turns caller-supplied strings into a
 * filesystem path. The upload endpoint is reachable by any logged-in user and by any anonymous
 * session that has passed a captcha, so a traversal here is an arbitrary file write.
 */
class UploadPathsTest {
    @Test void ordinaryFilenamePassesThroughUnchanged() {
        assertEquals("photo.jpg", UploadPaths.basename("photo.jpg"));
    }

    @Test void spacesAndNonAsciiSurviveExactly() {
        // bulk import matches uploaded files by exact filename (Encounter.mediaAsset#), so
        // rewriting characters here would break every legitimate upload that contains one.
        assertEquals("my photo ñ (2).jpg", UploadPaths.basename("my photo ñ (2).jpg"),
            "a legitimate filename must not be rewritten");
    }

    @Test void posixTraversalIsReducedToItsLastSegment() {
        assertEquals("evil.jsp", UploadPaths.basename("../../evil.jsp"));
    }

    @Test void backslashIsTreatedAsASeparatorOnlyWhereThePlatformSaysSo() {
        // a backslash is a legal character in a POSIX filename, and bulk import matches uploaded
        // files by exact name -- so stripping it unconditionally would corrupt "a\\b.jpg" on Linux.
        // Containment, not string munging, is what keeps a backslash path from escaping.
        if (java.io.File.separatorChar == '\\') {
            assertEquals("evil.jsp", UploadPaths.basename("..\\..\\evil.jsp"));
        } else {
            assertEquals("..\\..\\evil.jsp", UploadPaths.basename("..\\..\\evil.jsp"),
                "on POSIX a backslash is an ordinary filename character and must be preserved");
            assertEquals("a\\b.jpg", UploadPaths.basename("a\\b.jpg"));
        }
    }

    @Test void absolutePathIsReducedToItsLastSegment() {
        assertEquals("shadow", UploadPaths.basename("/etc/shadow"));
    }

    @Test void relativeTraversalWithNoFilenameIsRejected() {
        assertNull(UploadPaths.basename(".."));
        assertNull(UploadPaths.basename("."));
        assertNull(UploadPaths.basename("../.."));
    }

    @Test void emptyOrAbsentFilenameIsRejected() {
        assertNull(UploadPaths.basename(""));
        assertNull(UploadPaths.basename("   "));
        assertNull(UploadPaths.basename(null));
        assertNull(UploadPaths.basename("foo/"), "a trailing separator leaves no filename");
    }
}
