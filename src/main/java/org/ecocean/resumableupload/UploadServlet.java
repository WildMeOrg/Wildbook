package org.ecocean.resumableupload;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

// oh drat, we cant use servlet 3.0 multi-part magic.  gotta kick it oldschool with 2.5 apache stuff.  :/
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import org.ecocean.AccessControl;
import org.ecocean.CommonConfiguration;
import org.ecocean.servlet.ReCAPTCHA;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.Util;

public class UploadServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    /*
     * In ORDER to allow CORS to multiple domains you can set a list of valid domains here
     */
    private List<String> authorizedUrl = Arrays.asList("http://localhost", "http://example.com");

    // For user-facing web bulk upload.
    public static String getSubdirForUpload(Shepherd myShepherd, HttpServletRequest request) {
        String subdir = ServletUtilities.getParameterOrAttribute("subdir", request);

        if (subdir == null) {
            System.out.println("No subdir is set for upload; setting subdir to username");
            subdir = myShepherd.getUsername(request);
        }
        return subdir;
    }

    public static void setSubdirForUpload(String subdir, HttpServletRequest request) {
        if (subdir != null) {
            System.out.println("We got a subdir! I'm setting the session 'subdir' attribute to " +
                subdir);
            request.getSession().setAttribute("subdir", subdir);
        } else {
            System.out.println("We did not get a subdir!");
        }
    }

    public void doOptions(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST");
        if (request.getHeader("Access-Control-Request-Headers") != null)
            response.setHeader("Access-Control-Allow-Headers",
                request.getHeader("Access-Control-Request-Headers"));
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        if (!accessAllowed(request)) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().print("{\"success\": false}");
            response.getWriter().close();
            return;
        }
        System.out.println("UploadServlet.java. About to Print Params");
        ServletUtilities.printParams(request);
        System.out.println("(Those were the params)");
        if (!ServletFileUpload.isMultipartContent(request)) {
            writeJsonError(response, 400, "upload request must be multipart/form-data");
            return;
        }
        ServletFileUpload upload = new ServletFileUpload(new DiskFileItemFactory());
        // an unbounded parser lets one request fill the disk; these cap a single chunk POST
        long maxUploadBytes = CommonConfiguration.getUploadChunkMaxBytes(
            ServletUtilities.getContext(request));
        upload.setFileSizeMax(maxUploadBytes);
        upload.setSizeMax(maxUploadBytes);
        List<FileItem> multiparts = null;
        try {
            multiparts = upload.parseRequest(request);
        } catch (org.apache.commons.fileupload.FileUploadBase.SizeLimitExceededException |
            org.apache.commons.fileupload.FileUploadBase.FileSizeLimitExceededException ex) {
            System.out.println("UploadServlet refusing oversized upload: " + ex);
            writeJsonError(response, 413, "upload exceeds the configured maximum of " +
                maxUploadBytes + " bytes");
            return;
        } catch (org.apache.commons.fileupload.FileUploadException ex) {
            System.out.println("UploadServlet could not parse request: " + ex);
            writeJsonError(response, 400, "could not parse upload request");
            return;
        }
        try {
            boolean anonUser = AccessControl.isAnonymous(request);
            FileItem fileChunk = null;
            String recaptchaValue = null;
            for (FileItem item : multiparts) {
                if (item.isFormField()) {
                    if (item.getFieldName().equals("recaptchaValue"))
                        recaptchaValue = item.getString("UTF-8");
                } else {
                    fileChunk = item;
                    break; // we only do first one.  ?
                }
            }
            if (fileChunk == null)
                throw new UploadRefusedException("doPost could not find file chunk",
                        "no file chunk in request");
            System.out.println("Do Post");

            System.out.println(request.getRequestURL());

            PrintWriter out = response.getWriter();
            response.setContentType("application/json");
            response.setHeader("Cache-control", "no-cache, no-store");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "-1");

            response.setHeader("Access-Control-Allow-Origin", "*"); // allow us stuff from localhost
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Allow-Methods", "POST");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type");
            response.setHeader("Access-Control-Max-Age", "86400");

            int flowChunkNumber = getflowChunkNumber(multiparts);
            FlowInfo described = getFlowInfo(multiparts, request);
            System.out.println(described.flowFilePath);
            System.out.println("flowChunkNumber " + flowChunkNumber);

            if (!UploadPaths.chunkGeometryIsValid(flowChunkNumber, described.flowChunkSize,
                described.flowTotalSize))
                throw new UploadRefusedException("refusing chunk " + flowChunkNumber +
                        " of size " + described.flowChunkSize + " against declared total " +
                        described.flowTotalSize, "invalid chunk geometry");
            long content_length = fileChunk.getSize();
            if (!UploadPaths.chunkLengthIsValid(content_length, described.flowChunkSize,
                flowChunkNumber, described.flowTotalSize))
                throw new UploadRefusedException("refusing chunk " + flowChunkNumber + ": got " +
                        content_length + " bytes against declared chunkSize=" +
                        described.flowChunkSize,
                        "chunk length does not match declared geometry");
            // only now does this upload get bookkeeping; if another request already registered
            // the same identifier AND destination, it must have declared the same geometry
            FlowInfo info = FlowInfoStorage.getInstance().register(described);
            if ((info != described) && !info.sameGeometry(described))
                throw new UploadRefusedException("geometry changed mid-upload for " +
                        described.flowIdentifier, "upload parameters changed mid-upload");
            if ((info != described) && !info.sameDestination(described))
                throw new UploadRefusedException("staging collision for " +
                        described.flowIdentifier + ": " + info.finalFilePath + " vs " +
                        described.finalFilePath, "upload destination conflict");

            final FileItem chunkItem = fileChunk;
            final FlowInfo tracked = info;
            // held off from finalization: a duplicate chunk that lands after the upload has
            // been finalized is acknowledged here, never allowed to recreate the .temp
            boolean written = tracked.writeUnlessFinalized(() -> {
                // NOFOLLOW_LINKS: the containment check above proves the PATH is inside the upload root,
                // but a symlink sitting at that path would still redirect the write. Refusing to follow
                // one at open time is what actually closes that, and it is atomic.
                try (FileChannel channel = FileChannel.open(Paths.get(tracked.flowFilePath),
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                    InputStream is = chunkItem.getInputStream()) {
                    // Seek to position (64-bit: an int multiply here wraps past 2GiB)
                    channel.position(UploadPaths.chunkOffset(flowChunkNumber, tracked.flowChunkSize));
                    long readed = 0;
                    byte[] bytes = new byte[1024 * 100];
                    while (readed < content_length) {
                        int r = is.read(bytes);
                        if (r < 0) {
                            break;
                        }
                        // FileChannel.write is not obliged to consume the whole buffer
                        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(bytes, 0, r);
                        while (buf.hasRemaining()) {
                            channel.write(buf);
                        }
                        readed += r;
                    }
                    if (readed != content_length)
                        throw new UploadRefusedException("short chunk: wrote " + readed + " of " +
                                content_length, "upload chunk was truncated");
                }
                // mark only once the bytes are written and the channel is closed
                tracked.uploadedChunks.add(new FlowInfo.flowChunkNumber(flowChunkNumber));
            });
            if (!written) {
                out.print("{\"success\": true, \"uploadComplete\": true}");
                out.close();
                return;
            }
            String archivoFinal = info.checkIfUploadFinished();
            if (archivoFinal != null) { // Check if all chunks uploaded, and change filename
                FlowInfoStorage.getInstance().remove(info);
                response.getWriter().print("{\"success\": true, \"uploadComplete\": true}");
            } else {
                response.getWriter().print(
                    "{\"success\": true, \"uploadComplete\": false, \"chunkNumber\": " +
                    flowChunkNumber + "}");
            }
            // out.println(myObj.toString());

            out.close();
        } catch (UploadRefusedException ex) {
            // detail stays in the log; the client gets the reason without server paths
            System.out.println("UploadServlet refusing request: " + ex.getMessage());
            writeJsonError(response, 400, ex.getClientMessage());
        } finally {
            // commons-fileupload spools parts over its threshold to disk; without this they are
            // left behind on every request, including the ones we refuse
            for (FileItem item : multiparts) {
                try { item.delete(); } catch (Exception ignored) {}
            }
        }
    }

    /** A refusal that should reach the client as a 400 rather than a container error page. */
    public static class UploadRefusedException extends IOException {
        private final String clientMessage;

        public UploadRefusedException(String logMessage, String clientMessage) {
            super(logMessage);
            this.clientMessage = clientMessage;
        }

        /** Safe to return to the caller: never contains a server path. */
        public String getClientMessage() { return clientMessage; }
    }

    private static void writeJsonError(HttpServletResponse response, int status, String message)
    throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().print("{\"success\": false, \"error\": " +
            org.json.JSONObject.quote(message) + "}");
        response.getWriter().close();
    }

/* TODO: verifiy doGet works. skip testChunk with testChunks: false essentially, i doubt GET will be multipart -- so we need to also
   support that, esp getflowChunkNumber() and getFlowInfo() ... :(
 */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        if (!accessAllowed(request)) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().print("{\"success\": false}");
            response.getWriter().close();
            return;
        }
        if (!ServletFileUpload.isMultipartContent(request)) {
            writeJsonError(response, 400, "upload request must be multipart/form-data");
            return;
        }
        ServletFileUpload upload = new ServletFileUpload(new DiskFileItemFactory());
        // an unbounded parser lets one request fill the disk; these cap a single chunk POST
        long maxUploadBytes = CommonConfiguration.getUploadChunkMaxBytes(
            ServletUtilities.getContext(request));
        upload.setFileSizeMax(maxUploadBytes);
        upload.setSizeMax(maxUploadBytes);
        // upload.setHeaderEncoding("UTF-8");
        List<FileItem> multiparts = null;
        try {
            multiparts = upload.parseRequest(request);
        } catch (org.apache.commons.fileupload.FileUploadBase.SizeLimitExceededException |
            org.apache.commons.fileupload.FileUploadBase.FileSizeLimitExceededException ex) {
            System.out.println("UploadServlet refusing oversized request: " + ex);
            writeJsonError(response, 413, "upload exceeds the configured maximum of " +
                maxUploadBytes + " bytes");
            return;
        } catch (org.apache.commons.fileupload.FileUploadException ex) {
            System.out.println("UploadServlet could not parse request: " + ex);
            writeJsonError(response, 400, "could not parse upload request");
            return;
        }
        try {
            int flowChunkNumber = getflowChunkNumber(multiparts);
            System.out.println("GET fcn = " + flowChunkNumber);
            System.out.println("Do Get begun on request (about to print params)");
            ServletUtilities.printParams(request);

            System.out.println(request.getRequestURL());
            PrintWriter out = response.getWriter();
            response.setContentType("application/json");
            response.setHeader("Cache-control", "no-cache, no-store");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "-1");

            response.setHeader("Access-Control-Allow-Origin", "*"); // allow us stuff from localhost
            response.setHeader("Access-Control-Allow-Methods", "GET");
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type");
            response.setHeader("Access-Control-Max-Age", "86400");

            FlowInfo described = getFlowInfo(multiparts, request);
            System.out.println(described.flowFilePath);
            System.out.println("flowChunkNumber " + flowChunkNumber);

            // a status probe must never create bookkeeping of its own
            FlowInfo info = FlowInfoStorage.getInstance().lookup(described.flowIdentifier,
                described.flowFilePath);
            Object fcn = new FlowInfo.flowChunkNumber(flowChunkNumber);
            if ((info != null) && info.uploadedChunks.contains(fcn)) {
                System.out.println("Do Get arriba");
                response.getWriter().print("Uploaded."); // This Chunk has been Uploaded.
            } else {
                System.out.println("Do Get something is wrong");
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
            out.close();
        } catch (UploadRefusedException ex) {
            System.out.println("UploadServlet refusing request: " + ex.getMessage());
            writeJsonError(response, 400, ex.getClientMessage());
        } finally {
            for (FileItem item : multiparts) {
                try { item.delete(); } catch (Exception ignored) {}
            }
        }
    }

    private int getflowChunkNumber(List<FileItem> parts) {
        for (FileItem item : parts) {
            if (!item.isFormField()) continue;
            if (item.getFieldName().equals("flowChunkNumber"))
                return HttpUtils.toInt(item.getString(), -1);
        }
        return -1;
    }

    public static String getUploadDir(HttpServletRequest request)
    throws IOException {
        return getUploadDir(request, null);
    }

    public static String getUploadDir(HttpServletRequest request,
        Map<String, String> values)
    throws IOException {
        ServletUtilities.printParams(request);
        String subDir = ServletUtilities.getParameterOrAttributeOrSessionAttribute("subdir",
            request);
        String submissionId = null;
        boolean skipCreation = false;
        if (values != null) {
            submissionId = values.get("submissionId");
            skipCreation = Util.booleanNotFalse(values.get("skipCreation"));
        }
        if (Util.isUUID(submissionId)) {
/*  for now we cannot use username due to the fact that a user can upload while anon, and login later! :(
            String context = ServletUtilities.getContext(request);
            Shepherd myShepherd = new Shepherd(context);
            myShepherd.setAction("UploadServlet.getUploadDir");
            String username = myShepherd.getUsername(request);
            myShepherd.rollbackAndClose();
            if (username == null) {
                subDir = "_anonymous/submission/" + submissionId;
            } else {
                subDir = username + "/submission/" + submissionId;
            }
 */
            subDir = UploadPaths.ANONYMOUS_SUBMISSION_PREFIX + submissionId;
        }
        System.out.println("UploadServlet got subdir " + subDir);
        String safeSubDir = UploadPaths.uploadSubdir(subDir, submissionId);
        if (safeSubDir == null)
            throw new UploadRefusedException("unsafe subdir rejected: " + subDir,
                    "invalid upload destination");
        File uploadRoot = new File(
            CommonConfiguration.getUploadTmpDir(ServletUtilities.getContext(request)));
        File resolved = UploadPaths.resolveDirWithin(uploadRoot, safeSubDir);
        if (resolved == null)
            throw new UploadRefusedException("upload dir escapes upload root: " + safeSubDir,
                    "invalid upload destination");
        String fullDir = resolved.getPath();
        System.out.println("UploadServlet got uploadDir fullDir = " + fullDir);
        if (!skipCreation) ensureDirectoryExists(fullDir);
        return fullDir;
    }

    private static void ensureDirectoryExists(String fullPath) {
        File directory = new File(fullPath);

        if (!directory.isDirectory()) directory.mkdirs();
    }

    private FlowInfo getFlowInfo(List<FileItem> parts, HttpServletRequest request)
    throws ServletException, IOException {
        int FlowChunkSize = -1;
        long FlowTotalSize = -1;
        String FlowIdentifier = null;
        String FlowFilename = null;
        String FlowRelativePath = null;
        Map<String, String> values = new HashMap<String, String>();

        for (FileItem item : parts) {
            if (!item.isFormField()) continue;
            // System.out.println(item.getFieldName() + " -> " + item);
            // System.out.println(item.getFieldName() + " -> " + item.getString());
            values.put(item.getFieldName(), item.getString());
            switch (item.getFieldName()) {
            case "flowChunkSize":
                FlowChunkSize = HttpUtils.toInt(item.getString(), -1);
                break;
            case "flowTotalSize":
                FlowTotalSize = HttpUtils.toLong(item.getString(), -1);
                break;
            case "flowIdentifier":
                FlowIdentifier = item.getString("UTF-8");
                break;
            case "flowFilename":
                FlowFilename = item.getString("UTF-8");
                break;
            case "flowRelativePath":
                FlowRelativePath = item.getString("UTF-8");
                break;
            }
        }
        String base_dir = getUploadDir(request, values);
        System.out.println("[INFO] UploadServlet got base_dir=" + base_dir);

        // Here we add a ".temp" to every upload file to indicate NON-FINISHED
        // System.out.println("aaaa ==> " + FlowFilename);
        // validate the path that is actually opened: ".temp" is appended before the file is
        // created, so checking the bare name and then suffixing it leaves the real target unchecked
        String stagingName = UploadPaths.basename(FlowFilename);
        File finalTarget = (stagingName == null) ? null
            : UploadPaths.resolveWithin(new File(base_dir), stagingName);
        File target = (stagingName == null) ? null
            : UploadPaths.resolveWithin(new File(base_dir),
            stagingName + UploadPaths.STAGING_SUFFIX);
        if ((target == null) || (finalTarget == null))
            throw new UploadRefusedException("unsafe flowFilename rejected: " + FlowFilename,
                    "invalid filename");
        // the staging path is canonical; if it no longer ends in the staging suffix then an
        // existing symlink resolved it somewhere else, and finalization would move the wrong file
        if (!target.getPath().endsWith(UploadPaths.STAGING_SUFFIX))
            throw new UploadRefusedException("staging path resolved away from its suffix: " +
                    target.getPath(), "invalid filename");
        String FlowFilePath = target.getPath();
        // System.out.println("FlowFilePath ---> " + FlowFilePath);

/*
        System.out.println("FlowChunkSize: " + FlowChunkSize);
        System.out.println("FlowTotalSize: " + FlowTotalSize);
        System.out.println("FlowIdentifier: " + FlowIdentifier);
        System.out.println("FlowFilename: " + FlowFilename);
        System.out.println("FlowRelativePath: " + FlowRelativePath);
        System.out.println("FlowFilePath: " + FlowFilePath);
 */

        // hacky, but gets us userFilename
        request.getSession().setAttribute("userFilename:" + FlowFilename, FlowRelativePath);

        // A description of this request's upload, NOT yet registered: doPost registers it only
        // after the chunk passes geometry and length validation, so a malformed request leaves no
        // bookkeeping behind for a later legitimate upload to collide with. (A chunk whose stream
        // turns out short is refused after registration; its entry is the legitimate upload's.)
        FlowInfo info = new FlowInfo();
        info.flowChunkSize = FlowChunkSize;
        info.flowTotalSize = FlowTotalSize;
        info.flowIdentifier = FlowIdentifier;
        info.flowFilename = FlowFilename;
        info.flowRelativePath = FlowRelativePath;
        info.flowFilePath = FlowFilePath;
        info.finalFilePath = finalTarget.getPath();
        if (!info.valid())
            throw new UploadRefusedException("invalid flow params for " + FlowIdentifier,
                    "invalid upload parameters");
        return info;
    }

    // a simple wrapper, in case we want to change the logic here
    private boolean accessAllowed(HttpServletRequest request) {
        // note: this will return true for logged-in user (indifferent to captcha)
        return ReCAPTCHA.sessionIsHuman(request);
    }
}
