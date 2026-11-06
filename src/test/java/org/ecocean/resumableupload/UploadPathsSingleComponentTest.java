package org.ecocean.resumableupload;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Consumers of an upload directory (UploadedFiles.getFile, UploadedFiles.findFiles) join a
 * caller-supplied filename onto it. Those names arrive straight from an API payload -- see
 * BaseObject.processPost and EncounterPatchValidator.createMediaAsset -- and the resulting File
 * becomes a MediaAsset, so a traversing name is arbitrary file disclosure. Unlike the upload
 * write path, these must be refused outright rather than flattened, so the caller gets a clear
 * error instead of a confusing "not found".
 */
class UploadPathsSingleComponentTest {
    @Test void anOrdinaryFilenameIsASingleComponent() {
        assertTrue(UploadPaths.isSingleComponentName("photo.jpg"));
        assertTrue(UploadPaths.isSingleComponentName("my photo ñ (2).jpg"),
            "legitimate names with spaces and non-ASCII must still be accepted");
    }

    @Test void aLeadingDotIsAllowed() {
        // these are ordinary files in the submission dir, not traversal
        assertTrue(UploadPaths.isSingleComponentName(".hidden.jpg"));
    }

    @Test void anythingWithASeparatorIsRefused() {
        assertFalse(UploadPaths.isSingleComponentName("../../etc/passwd"));
        assertFalse(UploadPaths.isSingleComponentName("existingChild/../../secret"));
        assertFalse(UploadPaths.isSingleComponentName("/etc/passwd"));
        assertFalse(UploadPaths.isSingleComponentName("sub/photo.jpg"));
    }

    @Test void traversalComponentsAreRefused() {
        assertFalse(UploadPaths.isSingleComponentName(".."));
        assertFalse(UploadPaths.isSingleComponentName("."));
    }

    @Test void emptyOrAbsentIsRefused() {
        assertFalse(UploadPaths.isSingleComponentName(""));
        assertFalse(UploadPaths.isSingleComponentName("   "));
        assertFalse(UploadPaths.isSingleComponentName(null));
    }

    @Test void backslashIsRefusedOnlyWhereItSeparates() {
        if (java.io.File.separatorChar == '\\') {
            assertFalse(UploadPaths.isSingleComponentName("a\\b.jpg"));
        } else {
            assertTrue(UploadPaths.isSingleComponentName("a\\b.jpg"),
                "on POSIX a backslash is an ordinary filename character");
        }
    }
}
