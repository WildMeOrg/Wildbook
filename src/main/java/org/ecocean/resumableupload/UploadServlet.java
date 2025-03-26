package org.ecocean.resumableupload;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
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

import org.ecocean.resumableupload.FlowInfo;
import org.ecocean.resumableupload.FlowInfoStorage;
import org.ecocean.resumableupload.HttpUtils;

import org.ecocean.AccessControl;
import org.ecocean.CommonConfiguration;
import org.ecocean.servlet.ReCAPTCHA;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.Shepherd;
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
        if (!ServletFileUpload.isMultipartContent(request))
            throw new IOException("doPost is not multipart");
        ServletFileUpload upload = new ServletFileUpload(new DiskFileItemFactory());
        List<FileItem> multiparts = null;
        try {
            multiparts = upload.parseRequest(request);
        } catch (org.apache.commons.fileupload.FileUploadException ex) {
            throw new IOException("error parsing request: " + ex.toString());
        }
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
        if (fileChunk == null) throw new IOException("doPost could not find file chunk");
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
        FlowInfo info = getFlowInfo(multiparts, request);
        System.out.println(info.flowFilePath);
        System.out.println("flowChunkNumber " + flowChunkNumber);

        RandomAccessFile raf = new RandomAccessFile(info.flowFilePath, "rw");

        // Seek to position
        raf.seek((flowChunkNumber - 1) * info.flowChunkSize);

        // Save to file
        InputStream is = fileChunk.getInputStream();
        long readed = 0;
        long content_length = fileChunk.getSize();
        byte[] bytes = new byte[1024 * 100];
        while (readed < content_length) {
            int r = is.read(bytes);
            if (r < 0) {
                break;
            }
            raf.write(bytes, 0, r);
            readed += r;
        }
        raf.close();

        // Mark as uploaded.
        info.uploadedChunks.add(new FlowInfo.flowChunkNumber(flowChunkNumber));
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
        if (!ServletFileUpload.isMultipartContent(request))
            throw new IOException("doGet is not multipart");
        ServletFileUpload upload = new ServletFileUpload(new DiskFileItemFactory());
        // upload.setHeaderEncoding("UTF-8");
        List<FileItem> multiparts = null;
        try {
            multiparts = upload.parseRequest(request);
        } catch (org.apache.commons.fileupload.FileUploadException ex) {
            throw new IOException("error parsing request: " + ex.toString());
        }
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

        FlowInfo info = getFlowInfo(multiparts, request);
        System.out.println(info.flowFilePath);
        System.out.println("flowChunkNumber " + flowChunkNumber);

        Object fcn = new FlowInfo.flowChunkNumber(flowChunkNumber);
        if (info.uploadedChunks.contains(fcn)) {
            System.out.println("Do Get arriba");
            response.getWriter().print("Uploaded."); // This Chunk has been Uploaded.
        } else {
            System.out.println("Do Get something is wrong");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
        out.close();
    }

    private int getflowChunkNumber(List<FileItem> parts) {
        for (FileItem item : parts) {
            if (!item.isFormField()) continue;
            if (item.getFieldName().equals("flowChunkNumber"))
                return HttpUtils.toInt(item.getString(), -1);
        }
        return -1;
    }

    public static String getUploadDir(HttpServletRequest request) {
        return getUploadDir(request, null);
    }

    public static String getUploadDir(HttpServletRequest request, Map<String, String> values) {
        ServletUtilities.printParams(request);
        String subDir = ServletUtilities.getParameterOrAttributeOrSessionAttribute("subdir",
            request);
        String submissionId = null;
        if (values != null) submissionId = values.get("submissionId");
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
            subDir = "_anonymous/submission/" + submissionId;
        }
        System.out.println("UploadServlet got subdir " + subDir);
        if (subDir == null) { subDir = ""; } else { subDir = "/" + subDir; }
        String fullDir = CommonConfiguration.getUploadTmpDir(ServletUtilities.getContext(request)) +
            subDir;
        System.out.println("UploadServlet got uploadDir fullDir = " + fullDir);
        ensureDirectoryExists(fullDir);
        return fullDir;
    }

    private static void ensureDirectoryExists(String fullPath) {
        File directory = new File(fullPath);

        if (!directory.isDirectory()) directory.mkdirs();
    }

    private FlowInfo getFlowInfo(List<FileItem> parts, HttpServletRequest request)
    throws ServletException {
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
                FlowIdentifier = item.getString();
                break;
            case "flowFilename":
                FlowFilename = item.getString();
                break;
            case "flowRelativePath":
                FlowRelativePath = item.getString();
                break;
            }
        }
        String base_dir = getUploadDir(request, values);
        System.out.println("[INFO] UploadServlet got base_dir=" + base_dir);

        // Here we add a ".temp" to every upload file to indicate NON-FINISHED
        // System.out.println("aaaa ==> " + FlowFilename);
        String FlowFilePath = new File(base_dir, FlowFilename).getAbsolutePath() + ".temp";
        // System.out.println("FlowFilePath ---> " + FlowFilePath);
        FlowInfoStorage storage = FlowInfoStorage.getInstance();

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

        FlowInfo info = storage.get(FlowChunkSize, FlowTotalSize, FlowIdentifier, FlowFilename,
            FlowRelativePath, FlowFilePath);
        if (!info.valid()) {
            storage.remove(info);
            throw new ServletException("Invalid request params.");
        }
        return info;
    }

    // a simple wrapper, in case we want to change the logic here
    private boolean accessAllowed(HttpServletRequest request) {
        // note: this will return true for logged-in user (indifferent to captcha)
        return ReCAPTCHA.sessionIsHuman(request);
    }
}
