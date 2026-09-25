package org.ecocean.api.submission;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.ecocean.CommonConfiguration;
import org.ecocean.resumableupload.UploadPaths;
import org.ecocean.servlet.ServletUtilities;
import org.json.*;

/** Private immutable staging. No client-controlled storage paths are accepted. */
public class SubmissionFiles {
    public static final long MAX_DRAFT_BYTES = 200L * 1024 * 1024;
    public static final int MAX_FILES = 200;
    public static final long MAX_PIXELS = 24_000_000;
    private final Path root;
    public SubmissionFiles(String context, javax.servlet.ServletContext servlet) {
        this(configuredRoot(context, servlet));
    }
    public SubmissionFiles(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }
    private static Path configuredRoot(String context, javax.servlet.ServletContext servlet) {
        String value = CommonConfiguration.getApiAccessProperty("submissions.stagingDirectory", context);
        if (value == null || !Path.of(value).isAbsolute())
            throw new SubmissionException(503, "CAPABILITY_UNAVAILABLE", "Private staging directory is not configured");
        try {
            Path root = Path.of(value).toRealPath();
            Path legacy = new File(CommonConfiguration.getUploadTmpDir(context)).getCanonicalFile().toPath();
            if (root.startsWith(legacy) || legacy.startsWith(root))
                throw new SubmissionException(503, "CAPABILITY_UNAVAILABLE", "Private staging must be separate from legacy uploads");
            String web = servlet.getRealPath("/");
            if (web == null) throw new SubmissionException(503, "CAPABILITY_UNAVAILABLE", "Cannot verify private staging against web root");
            rejectOverlap(root, Path.of(web).toFile().getCanonicalFile().toPath().getParent());
            String imports = CommonConfiguration.getImportDir(context);
            if (imports != null) rejectOverlap(root, new File(imports).getCanonicalFile().toPath());
            org.ecocean.shepherd.core.Shepherd sh = new org.ecocean.shepherd.core.Shepherd(context);
            try {
                sh.beginDBTransaction();
                java.util.List<org.ecocean.media.AssetStore> stores = org.ecocean.media.AssetStoreFactory.getStores(sh);
                if (stores == null) throw new SubmissionException(503, "CAPABILITY_UNAVAILABLE", "Cannot verify asset-store boundaries");
                for (org.ecocean.media.AssetStore store : stores) if (store instanceof org.ecocean.media.LocalAssetStore)
                    rejectOverlap(root, ((org.ecocean.media.LocalAssetStore)store).root().toFile().getCanonicalFile().toPath());
            } finally { sh.rollbackAndClose(); }
            return root;
        } catch (IOException ex) { throw new SubmissionException(503, "CAPABILITY_UNAVAILABLE", "Private staging directory unavailable"); }
    }
    public static void rejectOverlap(Path root, Path served) {
        if (served == null || root.startsWith(served) || served.startsWith(root))
            throw new SubmissionException(503, "CAPABILITY_UNAVAILABLE", "Staging must be outside served and import directories");
    }
    public static long maxFileBytes(String context) {
        return Math.min(MAX_DRAFT_BYTES, Math.max(1L, CommonConfiguration.getMaxMediaSizeInMegabytes(context)) * 1024 * 1024);
    }
    public static void checkName(String name) {
        if (!UploadPaths.isSingleComponentName(name) || name.length() > 128 || !name.matches("[A-Za-z0-9][A-Za-z0-9_.-]*")
                || !name.equals(ServletUtilities.cleanFileName(name)))
            throw new SubmissionException(400, "BAD_REQUEST", "Use a filename of at most 128 ASCII letters, digits, dots, underscores or hyphens, starting with a letter or digit");
    }
    public JSONObject receive(HttpServletRequest request, long limit) throws Exception {
        try { return receiveMultipart(request, limit); }
        catch (FileUploadBase.FileUploadIOException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof FileUploadBase.SizeLimitExceededException || cause instanceof FileUploadBase.FileSizeLimitExceededException) throw new SubmissionException(413, "LIMIT_EXCEEDED", "Multipart size limit exceeded");
            throw new SubmissionException(400, "BAD_REQUEST", "Malformed multipart upload");
        } catch (FileUploadBase.SizeLimitExceededException | FileUploadBase.FileSizeLimitExceededException ex) { throw new SubmissionException(413, "LIMIT_EXCEEDED", "Multipart size limit exceeded"); }
        catch (MultipartStream.MalformedStreamException ex) { throw new SubmissionException(400, "BAD_REQUEST", "Incomplete multipart body"); }
        catch (FileUploadException | InvalidFileNameException ex) { throw new SubmissionException(400, "BAD_REQUEST", "Malformed multipart upload"); }
    }
    private JSONObject receiveMultipart(HttpServletRequest request, long limit) throws Exception {
        if (!ServletFileUpload.isMultipartContent(request))
            throw new SubmissionException(400, "BAD_REQUEST", "multipart/form-data with one file required");
        ServletFileUpload upload = new ServletFileUpload();
        upload.setSizeMax(limit + 65536); upload.setFileSizeMax(limit); upload.setHeaderEncoding("UTF-8");
        FileItemIterator items = upload.getItemIterator(request);
        if (!items.hasNext()) throw new SubmissionException(400, "BAD_REQUEST", "File required");
        FileItemStream item = items.next();
        if (item.isFormField() || !"file".equals(item.getFieldName()))
            throw new SubmissionException(400, "BAD_REQUEST", "Exactly one file part named file required");
        JSONObject entry = null;
        try {
            try (InputStream input = item.openStream()) { entry = write(item.getName(), input, limit); }
            if (items.hasNext()) throw new SubmissionException(400, "BAD_REQUEST", "Exactly one file part required");
            return entry;
        } catch (Exception ex) { if (entry != null) try { remove(entry); } catch (IOException cleanup) { System.err.println("Submission temporary blob cleanup failed"); } throw ex; }
    }
    public JSONObject write(String name, InputStream input, long limit) throws Exception {
        checkName(name);
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
            throw new SubmissionException(503, "CAPABILITY_UNAVAILABLE", "Private staging unavailable");
        boolean posix = Files.getFileStore(root).supportsFileAttributeView("posix");
        Path candidate = root.resolve(UUID.randomUUID().toString());
        Path dir = posix ? Files.createDirectory(candidate, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
            java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"))) : Files.createDirectory(candidate);
        Path path = dir.resolve(name);
        boolean complete = false;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long count = 0; long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(2);
            try (OutputStream out = posix ? java.nio.channels.Channels.newOutputStream(Files.newByteChannel(path,
                    java.util.EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW),
                    java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))))
                    : Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
                byte[] buffer = new byte[8192]; int n;
                while ((n = input.read(buffer)) != -1) {
                    if (System.nanoTime() > deadline) throw new SubmissionException(408, "BAD_REQUEST", "Upload time limit exceeded");
                    count += n;
                    if (count > limit) throw new SubmissionException(413, "LIMIT_EXCEEDED", "File or remaining draft byte limit exceeded");
                    digest.update(buffer, 0, n); out.write(buffer, 0, n);
                }
            }
            String mediaType = inspect(path);
            String lower = name.toLowerCase(Locale.ROOT);
            if (!(mediaType.equals("image/png") ? lower.endsWith(".png") : (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))))
                throw new SubmissionException(422, "VALIDATION_INVALID", "Filename extension must match image content");
            StringBuilder sha = new StringBuilder(); for (byte b : digest.digest()) sha.append(String.format("%02x", b & 255));
            JSONObject result = new JSONObject().put("name", name).put("sizeBytes", count).put("sha256", sha.toString())
                .put("state", "complete").put("mediaType", mediaType).put("blob", dir.getFileName().toString());
            complete = true; return result;
        } finally { if (!complete) try { Files.deleteIfExists(path); Files.deleteIfExists(dir); } catch (IOException cleanup) { System.err.println("Submission temporary blob cleanup failed"); } }
    }
    public Path path(JSONObject entry) throws IOException {
        String blob = entry.getString("blob"); String name = entry.getString("name"); checkName(name);
        if (!org.ecocean.Util.isUUID(blob)) throw new IOException("Invalid blob identifier");
        File dir = UploadPaths.resolveDirWithin(root.toFile(), blob);
        if (dir == null || Files.isSymbolicLink(root.resolve(blob))) throw new IOException("Invalid staging path");
        File file = UploadPaths.resolveWithin(dir, name);
        if (file == null || Files.isSymbolicLink(dir.toPath().resolve(name))) throw new IOException("Invalid staging file");
        return file.toPath();
    }
    public void verify(JSONObject entry) throws Exception {
        Path path = path(entry);
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); long count = 0;
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) != -1) { count += n; if (count > MAX_DRAFT_BYTES) throw new IOException("Oversize staged file"); digest.update(buf, 0, n); }
        }
        StringBuilder sha = new StringBuilder(); for (byte b : digest.digest()) sha.append(String.format("%02x", b & 255));
        if (count != entry.getLong("sizeBytes") || !sha.toString().equals(entry.getString("sha256"))) throw new IOException("Staged file changed");
        inspect(path);
    }
    public static String inspect(Path path) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) throw new IOException("Cannot read image");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new SubmissionException(422, "VALIDATION_INVALID", "Unrecognized image");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("jpeg", "jpg", "png").contains(format)) throw new SubmissionException(422, "VALIDATION_INVALID", "Only JPEG and PNG are supported");
                reader.setInput(input, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w > 16000 || h > 16000 || (long)w * h > MAX_PIXELS)
                    throw new SubmissionException(413, "LIMIT_EXCEEDED", "Image exceeds dimension or pixel limit");
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(4, 4, 0, 0);
                java.awt.image.BufferedImage decoded = reader.read(0, param);
                if (decoded == null) throw new IOException("Cannot decode image"); decoded.flush();
                return format.equals("png") ? "image/png" : "image/jpeg";
            } finally { reader.dispose(); }
        } catch (IOException ex) { throw new SubmissionException(422, "VALIDATION_INVALID", "Image is truncated or cannot be decoded"); }
    }
    public void remove(JSONObject entry) throws IOException {
        Path path = path(entry); Files.deleteIfExists(path); Files.deleteIfExists(path.getParent());
    }
    public static JSONArray publicFiles(JSONArray files) {
        JSONArray result = new JSONArray();
        for (int i = 0; i < files.length(); i++) { JSONObject file = new JSONObject(files.getJSONObject(i).toString()); file.remove("blob"); result.put(file); }
        return result;
    }
    public void cleanup(Set<String> retained) throws IOException {
        long cutoff = System.currentTimeMillis() - SubmissionPolicy.DRAFT_TTL_MILLIS; int removed = 0;
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(root)) {
            for (Path dir : dirs) {
                if (removed >= 5000) break;
                try {
                    String blob = dir.getFileName().toString();
                    if (!org.ecocean.Util.isUUID(blob) || retained.contains(blob) || Files.isSymbolicLink(dir)
                            || !Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS) || Files.getLastModifiedTime(dir).toMillis() >= cutoff) continue;
                    java.util.List<Path> files = new ArrayList<>(); boolean safe = true;
                    try (DirectoryStream<Path> children = Files.newDirectoryStream(dir)) {
                        for (Path file : children) {
                            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.getLastModifiedTime(file).toMillis() >= cutoff) { safe = false; break; }
                            files.add(file);
                        }
                    }
                    if (!safe) continue;
                    for (Path file : files) Files.deleteIfExists(file);
                    Files.deleteIfExists(dir); removed++;
                } catch (IOException ex) { System.err.println("Submission blob cleanup deferred for one directory"); }
            }
        }
    }
}
