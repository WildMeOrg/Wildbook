package org.ecocean.resumableupload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The containment guarantee: whatever the caller sends, the resolved path must sit inside the
 * configured upload directory. basename() and Util.safePath() handle the string level; this is
 * the check that has to hold even when the string level is bypassed.
 */
class UploadPathsContainmentTest {
    @TempDir Path tmp;

    @Test void ordinaryFilenameResolvesInsideTheBaseDirectory() throws IOException {
        File base = tmp.toFile();
        File resolved = UploadPaths.resolveWithin(base, "photo.jpg");

        assertNotNull(resolved);
        assertEquals(new File(base, "photo.jpg").getCanonicalPath(), resolved.getCanonicalPath());
    }

    @Test void traversingFilenameIsRefused() throws IOException {
        // basename() already flattens this; resolveWithin must independently refuse to write out
        assertNull(UploadPaths.resolveWithin(tmp.toFile(), ".."),
            "resolveWithin must refuse rather than silently write outside the base directory");
    }

    @Test void filenameCannotIntroduceADirectory() throws IOException {
        File resolved = UploadPaths.resolveWithin(tmp.toFile(), "../../evil.jsp");

        assertNotNull(resolved);
        assertEquals(tmp.toFile().getCanonicalPath(), resolved.getParentFile().getCanonicalPath(),
            "a traversing filename must be flattened into the base directory, never above it");
    }

    @Test void unusableFilenameIsRefused() throws IOException {
        assertNull(UploadPaths.resolveWithin(tmp.toFile(), ""));
        assertNull(UploadPaths.resolveWithin(tmp.toFile(), null));
        assertNull(UploadPaths.resolveWithin(null, "photo.jpg"));
    }

    @Test void plainSubdirectoryResolvesInsideTheUploadRoot() throws IOException {
        File resolved = UploadPaths.resolveDirWithin(tmp.toFile(), "_anonymous/submission/abc");

        assertNotNull(resolved);
        assertEquals(new File(tmp.toFile(), "_anonymous/submission/abc").getCanonicalPath(),
            resolved.getCanonicalPath());
    }

    @Test void emptySubdirResolvesToTheUploadRootItself() throws IOException {
        assertEquals(tmp.toFile().getCanonicalPath(),
            UploadPaths.resolveDirWithin(tmp.toFile(), "").getCanonicalPath());
    }

    @Test void traversingSubdirectoryIsRefused() throws IOException {
        assertNull(UploadPaths.resolveDirWithin(tmp.toFile(), "../../opt/tomcat/webapps/ROOT"),
            "a subdir must never resolve above the upload root");
    }

    @Test void symlinkedSubdirectoryCannotEscapeTheUploadRoot() throws IOException {
        Path root = Files.createDirectory(tmp.resolve("uploads"));
        Path outside = Files.createDirectory(tmp.resolve("outside"));

        try {
            Files.createSymbolicLink(root.resolve("escape"), outside);
        } catch (IOException | UnsupportedOperationException ex) {
            assumeTrue(false, "filesystem does not support symlinks here");
            return;
        }
        assertNull(UploadPaths.resolveDirWithin(root.toFile(), "escape"),
            "a symlinked subdirectory must not become a way out of the upload root");
    }
}
