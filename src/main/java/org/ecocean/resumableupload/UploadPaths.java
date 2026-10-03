package org.ecocean.resumableupload;

import java.io.File;
import java.io.IOException;
import org.ecocean.Util;

/**
 * Turns caller-supplied upload strings into filesystem paths, safely.
 *
 * The chunked-upload endpoint is reachable by any logged-in user and by any anonymous session
 * that has passed a captcha, and it creates and writes files at the path it is given -- so an
 * unconstrained path here is an arbitrary file write. Every value that reaches a File must come
 * through this class.
 *
 * Methods return null to mean "refuse this request" rather than throwing, so callers answer with
 * a 400 instead of a container error page.
 */
public final class UploadPaths {
    public static final String ANONYMOUS_SUBMISSION_PREFIX = "_anonymous/submission/";
    /** Suffix an in-progress upload is staged under until every chunk has arrived. */
    public static final String STAGING_SUFFIX = ".temp";
    /** Beyond this a megabyte value would overflow when converted to bytes. */
    public static final long MAX_CONFIGURABLE_MEGABYTES = 1024L * 1024L;

    private UploadPaths() {}

    /**
     * The filename with any directory part removed, or null if nothing usable remains.
     *
     * The name is otherwise returned byte-for-byte: bulk import matches uploaded files by exact
     * filename (the Encounter.mediaAsset# columns), so rewriting characters the way
     * ServletUtilities.cleanFileName() does would break legitimate uploads containing a space or
     * any non-ASCII character.
     */
    public static String basename(String rawFilename) {
        if (rawFilename == null) return null;
        String name = rawFilename;
        // '/' always separates. A backslash is only a separator where the platform says so: on
        // POSIX it is an ordinary filename character, and bulk import matches uploaded files by
        // exact name, so stripping it there would corrupt a legitimate "a\\b.jpg".
        int cut = name.lastIndexOf('/');
        if (File.separatorChar == '\\') cut = Math.max(cut, name.lastIndexOf('\\'));
        if (cut > -1) name = name.substring(cut + 1);
        if (name.isBlank()) return null;
        String probe = name.trim();
        if (".".equals(probe) || "..".equals(probe)) return null;
        return name;
    }

    /**
     * Whether a name is a single, safe path component. Consumers that join a caller-supplied
     * filename onto an upload directory must refuse anything else outright rather than flatten it,
     * so the caller gets a clear error instead of a puzzling "file not found".
     */
    public static boolean isSingleComponentName(String name) {
        if ((name == null) || name.isBlank()) return false;
        if (name.indexOf('/') > -1) return false;
        if ((File.separatorChar == '\\') && (name.indexOf('\\') > -1)) return false;
        String probe = name.trim();
        return !".".equals(probe) && !"..".equals(probe);
    }

    /**
     * The file the named upload should be written to, or null if it would land outside baseDir.
     */
    public static File resolveWithin(File baseDir, String rawFilename)
    throws IOException {
        if (baseDir == null) return null;
        String name = basename(rawFilename);
        if (name == null) return null;
        return contained(baseDir, new File(baseDir, name), false);
    }

    /**
     * The directory an upload should land in, or null if it would sit outside uploadRoot. This is
     * the level at which a symlink can escape, since basename() has already flattened filenames.
     */
    public static File resolveDirWithin(File uploadRoot, String subdir)
    throws IOException {
        if (uploadRoot == null) return null;
        if ((subdir == null) || subdir.isBlank()) return contained(uploadRoot, uploadRoot, true);
        return contained(uploadRoot, new File(uploadRoot, subdir), true);
    }

    private static File contained(File baseDir, File target, boolean allowBaseItself)
    throws IOException {
        String base = baseDir.getCanonicalPath();
        String resolved = target.getCanonicalPath();

        // return the path we just checked; canonicalising a second time would re-resolve and
        // could hand back something other than what was validated
        if (resolved.equals(base)) return allowBaseItself ? new File(resolved) : null;
        if (!resolved.startsWith(base + File.separator)) return null;
        return new File(resolved);
    }

    /**
     * The subdirectory segment an upload belongs in: "" for none, or null if the caller-supplied
     * subdir is unsafe and the request must be refused.
     *
     * A valid submissionId pins the destination. Otherwise the legacy /import flow's subdir is
     * honoured, but only if no component of it is "." or "..".
     */
    public static String uploadSubdir(String rawSubdir, String submissionId) {
        if (Util.isUUID(submissionId)) return ANONYMOUS_SUBMISSION_PREFIX + submissionId;
        if ((rawSubdir == null) || rawSubdir.isBlank()) return "";
        // Component-wise, deliberately NOT Util.safePath: that rejects any '..' anywhere and any
        // leading dot, but User.setUsername imposes no restrictions and the legacy /import flow
        // passes a username here -- so ".alice" and "alice..smith" must keep working. Only a
        // component that IS "." or ".." is traversal.
        StringBuilder cleaned = new StringBuilder();
        for (String part : rawSubdir.split("/", -1)) {
            if (part.isEmpty()) {
                if (cleaned.length() == 0) continue; // tolerate a leading '/'
                return null;                         // an interior empty component is malformed
            }
            if (".".equals(part) || "..".equals(part)) return null;
            if (part.indexOf('\\') > -1 && (File.separatorChar == '\\')) return null;
            if (cleaned.length() > 0) cleaned.append('/');
            cleaned.append(part);
        }
        if (cleaned.length() == 0) return null;
        return cleaned.toString();
    }

    /**
     * The per-request upload cap in bytes, from a configured megabyte value. Anything absent,
     * unparseable or non-positive falls back rather than leaving the parser unbounded.
     */
    public static long maxUploadBytes(String rawMegabytes, int fallbackMegabytes) {
        long megabytes = fallbackMegabytes;

        if ((rawMegabytes != null) && !rawMegabytes.isBlank()) {
            try {
                long parsed = Long.parseLong(rawMegabytes.trim());
                // a value that would overflow to a negative byte count reads as "unlimited" to
                // commons-fileupload -- the opposite of what was configured, so fall back instead
                if ((parsed > 0L) && (parsed <= MAX_CONFIGURABLE_MEGABYTES)) megabytes = parsed;
            } catch (NumberFormatException ex) {
                System.out.println("UploadPaths ignoring unparseable upload cap: " + rawMegabytes);
            }
        }
        return megabytes * 1024L * 1024L;
    }

    /** The byte offset a chunk starts at, computed in 64 bits so it cannot wrap past 2GiB. */
    public static long chunkOffset(int chunkNumber, int chunkSize) {
        return (long)(chunkNumber - 1) * (long)chunkSize;
    }

    /**
     * The most chunks one upload may declare (100k x 5MiB chunks is ~500GB). Before this bound
     * existed, FlowInfo.checkIfUploadFinished() counted with an int `i < count + 1`, which a
     * declared count near Integer.MAX_VALUE overflowed, so a single chunk was treated as a whole
     * upload. That loop is long-based now; the bound also keeps the per-chunk completion scan and
     * the declared geometry within reason.
     */
    public static final long MAX_CHUNKS = 100000L;

    /** Whether client-declared chunk geometry is usable. All three values are caller-supplied. */
    public static boolean chunkGeometryIsValid(int chunkNumber, int chunkSize, long totalSize) {
        if (chunkSize <= 0) return false;
        if (totalSize < 0) return false;
        if (chunkNumber < 1) return false;
        long totalChunks = declaredChunkCount(chunkSize, totalSize);
        if (totalChunks > MAX_CHUNKS) return false;
        return chunkNumber <= totalChunks;
    }

    /** Chunk count for a declared geometry, computed without overflowing near Long.MAX_VALUE. */
    public static long declaredChunkCount(int chunkSize, long totalSize) {
        if (chunkSize <= 0) return 0L;
        long whole = totalSize / chunkSize;
        if ((totalSize % chunkSize) != 0L) whole++;
        return Math.max(1L, whole);
    }

    /**
     * Whether the bytes actually received match what the declared geometry implies. Without this
     * a client can declare a tiny upload and send a large body, or declare a large one and send a
     * short chunk that leaves an unwritten hole in the file.
     */
    public static boolean chunkLengthIsValid(long actualLength, int chunkSize, int chunkNumber,
        long totalSize) {
        if (actualLength < 0) return false;
        if (!chunkGeometryIsValid(chunkNumber, chunkSize, totalSize)) return false;
        long expected = chunkSize;
        if (chunkNumber == declaredChunkCount(chunkSize, totalSize)) {
            long remainder = totalSize % chunkSize;
            expected = (remainder == 0L) ? Math.min(chunkSize, totalSize) : remainder;
        }
        return actualLength == expected;
    }
}
