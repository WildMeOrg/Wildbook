package ec.com.mapache.ngflow.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

//oh drat, we cant use servlet 3.0 multi-part magic.  gotta kick it oldschool with 2.5 apache stuff.  :/
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import ec.com.mapache.ngflow.upload.FlowInfo;
import ec.com.mapache.ngflow.upload.FlowInfoStorage;
import ec.com.mapache.ngflow.upload.HttpUtils;

import org.ecocean.CommonConfiguration;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.servlet.ReCAPTCHA;
import org.ecocean.AccessControl;

/**
 *
 * This is a servlet demo, for using Flow.js to upload files.
 *
 * by fanxu123
 */
public class UploadServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;


	/*
	 * In ORDER to allow CORS  to multiple domains you can set a list of valid domains here
	 */
	private List<String> authorizedUrl = Arrays.asList("http://localhost", "http://example.com");


	public void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
			response.setHeader("Access-Control-Allow-Origin", "*");
			response.setHeader("Access-Control-Allow-Methods", "GET, POST");
			if (request.getHeader("Access-Control-Request-Headers") != null) response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"));
			//response.setContentType("text/plain");
	}


	protected void doPost(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {




            if (!ServletFileUpload.isMultipartContent(request)) throw new IOException("doPost is not multipart");
            ServletFileUpload upload = new ServletFileUpload(new DiskFileItemFactory());
            //upload.setHeaderEncoding("UTF-8");
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
                    if (item.getFieldName().equals("recaptchaValue")) recaptchaValue = item.getString("UTF-8");
                } else {
                    fileChunk = item;
                    break;  //we only do first one.  ?
                }
            }
            if (!ReCAPTCHA.sessionIsHuman(request)) throw new IOException("failed sessionIsHuman()");
            if (fileChunk == null) throw new IOException("doPost could not find file chunk");

		System.out.println("Do Post");

		System.out.println(request.getRequestURL());

		PrintWriter out = response.getWriter();
		response.setContentType("application/json");
		response.setHeader("Cache-control", "no-cache, no-store");
		response.setHeader("Pragma", "no-cache");
		response.setHeader("Expires", "-1");

		response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost
		//response.setHeader("Access-Control-Allow-Origin", getOriginDomain(request));
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
		//InputStream is = request.getInputStream();
		InputStream is = fileChunk.getInputStream();
		long readed = 0;
		//long content_length = request.getContentLength();
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
		if (archivoFinal != null) { // Check if all chunks uploaded, and
			// change filename
			FlowInfoStorage.getInstance().remove(info);
			response.getWriter().print("{\"success\": true, \"uploadComplete\": true}");

		} else {
			response.getWriter().print("{\"success\": true, \"uploadComplete\": false, \"chunkNumber\": " + flowChunkNumber + "}");
		}
		// out.println(myObj.toString());

		out.close();
	}


/*  UGH TODO i think doGet is broken, so best skip testChunk with testChunks: false
    essentially, i doubt GET will be multipart -- so we need to also support that, esp getflowChunkNumber() and getFlowInfo() ... :(
*/
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {

            if (!ServletFileUpload.isMultipartContent(request)) throw new IOException("doGet is not multipart");
            ServletFileUpload upload = new ServletFileUpload(new DiskFileItemFactory());
            //upload.setHeaderEncoding("UTF-8");
            List<FileItem> multiparts = null;
            try {
                multiparts = upload.parseRequest(request);
            } catch (org.apache.commons.fileupload.FileUploadException ex) {
                throw new IOException("error parsing request: " + ex.toString());
            }

		int flowChunkNumber = getflowChunkNumber(multiparts);
System.out.println("GET fcn = " + flowChunkNumber);
		System.out.println("Do Get");


		System.out.println(request.getRequestURL());
		PrintWriter out = response.getWriter();
		response.setContentType("application/json");
		response.setHeader("Cache-control", "no-cache, no-store");
		response.setHeader("Pragma", "no-cache");
		response.setHeader("Expires", "-1");

		//response.setHeader("Access-Control-Allow-Origin", getOriginDomain(request));
		response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost
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
			response.getWriter().print("Uploaded."); // This Chunk has been
														// Uploaded.
		} else {
			System.out.println("Do Get something is wrong");
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		}

		out.close();
	}

	private int getflowChunkNumber(List<FileItem> parts) {
            for (FileItem item : parts) {
                if (!item.isFormField()) continue;
                if (item.getFieldName().equals("flowChunkNumber")) return HttpUtils.toInt(item.getString(), -1);
            }
            return -1;
        }


	private String getOriginDomain(HttpServletRequest request) {
            return "pod.scribble.com";
        }

	private String getUploadDir(HttpServletRequest request) {
            return CommonConfiguration.getUploadTmpDir(ServletUtilities.getContext(request));
        }

	private FlowInfo getFlowInfo(List<FileItem> parts, HttpServletRequest request) throws ServletException {
            int FlowChunkSize = -1;
            long FlowTotalSize = -1;
            String FlowIdentifier = null;
            String FlowFilename = null;
            String FlowRelativePath = null;

            for (FileItem item : parts) {
                if (!item.isFormField()) continue;
System.out.println(item);
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

                String base_dir = getUploadDir(request);

		// Here we add a ".temp" to every upload file to indicate NON-FINISHED
System.out.println("aaaa ==> " + FlowFilename);
		String FlowFilePath = new File(base_dir, FlowFilename).getAbsolutePath() + ".temp";
System.out.println("FlowFilePath ---> " + FlowFilePath);

		FlowInfoStorage storage = FlowInfoStorage.getInstance();

System.out.println("FlowChunkSize: " + FlowChunkSize);
System.out.println("FlowTotalSize: " + FlowTotalSize);
System.out.println("FlowIdentifier: " + FlowIdentifier);
System.out.println("FlowFilename: " + FlowFilename);
System.out.println("FlowRelativePath: " + FlowRelativePath);
System.out.println("FlowFilePath: " + FlowFilePath);

		FlowInfo info = storage.get(FlowChunkSize, FlowTotalSize,
				FlowIdentifier, FlowFilename, FlowRelativePath, FlowFilePath);
		if (!info.valid()) {
			storage.remove(info);
			throw new ServletException("Invalid request params.");
		}
		return info;
	}


}
