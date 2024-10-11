package org.ecocean.api;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONObject;

import org.ecocean.Base;
import org.ecocean.Encounter;
import org.ecocean.ia.Task;
import org.ecocean.media.AssetStore;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.MarkedIndividual;
import org.ecocean.Occurrence;
import org.ecocean.Project;
import org.ecocean.resumableupload.UploadServlet;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.servlet.ReCAPTCHA;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.Shepherd;
import org.ecocean.User;
import org.ecocean.Util;

// note: this is for use on any Base object (MarkedIndividual, Encounter, Occurrence)
public class BaseObject extends ApiBase {
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String uri = request.getRequestURI();
        String[] args = uri.substring(8).split("/");
        if (args.length < 1) throw new ServletException("Bad path");
        // System.out.println("args => " + java.util.Arrays.toString(args));

        JSONObject payload = null;
        String requestMethod = request.getMethod();
        if (requestMethod.equals("POST")) {
            payload = ServletUtilities.jsonFromHttpServletRequest(request);
        }

        if (!ReCAPTCHA.sessionIsHuman(request)) {
            response.setStatus(401);
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write("{\"success\": false}");
            return;
        }

/*
        if (!(args[0].equals("encounters") || args[0].equals("individuals") || args[0].equals("occurrences")))
            throw new ServletException("Bad class");
*/
        payload.put("_class", args[0]);

        JSONObject rtn = null;
        if (requestMethod.equals("POST")) {
            rtn = processPost(request, args, payload);
        } else if (requestMethod.equals("GET")) {
            rtn = processGet(request, args);
        } else {
            throw new ServletException("Invalid method");
        }
        int statusCode = rtn.optInt("statusCode", 500);

        response.setStatus(statusCode);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(rtn.toString());
    }

    protected JSONObject processPost(HttpServletRequest request, String[] args, JSONObject payload)
    throws ServletException, IOException {
        if (payload == null) throw new ServletException("empty payload");
        JSONObject rtn = new JSONObject();
        rtn.put("success", false);
        List<File> files = findFiles(request, payload);
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("api.BaseObject.processPost");
        myShepherd.beginDBTransaction();
        User currentUser = myShepherd.getUser(request);

        Base obj = null;
        try {
            String cls = payload.optString("_class");
            switch (cls) {
            case "encounters":
                // we need to know the id ahead of time for the file strucure
                String encId = Util.generateUUID();
                payload.put("_id", encId);
                Map<File, MediaAsset> mas = makeMediaAssets(encId, files, myShepherd);
                JSONArray invalidFilesArr = new JSONArray();
                List<MediaAsset> validMAs = new ArrayList<MediaAsset>();
                for (File file : mas.keySet()) {
                    MediaAsset ma = mas.get(file);
                    if (ma == null) {
                        JSONObject el = new JSONObject();
                        el.put("filename", file.getName());
                        invalidFilesArr.put(el);
                    } else {
                        validMAs.add(ma);
                    }
                }
                rtn.put("invalidFiles", invalidFilesArr);
                if ((validMAs.size() < 1) && (currentUser == null)) {
                    JSONObject error = new JSONObject();
                    error.put("fieldName", "assetFilenames");
                    error.put("code", ApiException.ERROR_RETURN_CODE_REQUIRED);
                    throw new ApiException("anonymous submission requires valid files", error);
                }

                obj = Encounter.createFromApi(payload, files);
                Encounter enc = (Encounter)obj;
                myShepherd.getPM().makePersistent(enc);

                JSONArray assetsArr = new JSONArray();
                List<Integer> maIds = new ArrayList<Integer>();
                for (MediaAsset ma : validMAs) {
                    MediaAssetFactory.save(ma, myShepherd);
////// attach to enc!!!
                    JSONObject maj = new JSONObject();
                    maj.put("filename", ma.getFilename());
                    maj.put("id", ma.getId());
                    maj.put("uuid", ma.getUUID());
                    maj.put("url", ma.safeURL(myShepherd));
System.out.println(">>>>>> ma=" + maj);
                    assetsArr.put(maj);
                    maIds.add(ma.getId());
                }
                rtn.put("assets", assetsArr);
                //MediaAsset.updateStandardChildrenBackground(context, maIds);
                // these are needed for display in results
                rtn.put("locationId", enc.getLocationID());
                rtn.put("submissionDate", enc.getDWCDateAdded());
                rtn.put("statusCode", 200);
                break;
            case "occurrences":
                obj = Occurrence.createFromApi(payload, files);
                break;
            case "individuals":
                obj = MarkedIndividual.createFromApi(payload, files);
                break;
            default:
                throw new ApiException("bad class");
            }
            rtn.put("id", obj.getId());
            rtn.put("class", cls);
            rtn.put("success", true);

        } catch (ApiException apiEx) {
            System.out.println("BaseObject.processPost() returning 400 due to " + apiEx + " [errors=" + apiEx.getErrors() + "] on payload " + payload);
            rtn.put("statusCode", 400);
            rtn.put("errors", apiEx.getErrors());
        }

        if ((obj != null) && (rtn.optInt("statusCode", 0) == 200)) {
            System.out.println("BaseObject.processPost() success (200) creating " + obj + " from payload " + payload);
            myShepherd.commitDBTransaction();
        } else {
            myShepherd.rollbackDBTransaction();
        }
        myShepherd.closeDBTransaction();
        return rtn;
    }

    protected JSONObject processGet(HttpServletRequest request, String[] args)
    throws ServletException, IOException {
        JSONObject rtn = new JSONObject();
        return rtn;
    }

    private List<File> findFiles(HttpServletRequest request, JSONObject payload)
    throws IOException {
        List<File> files = new ArrayList<File>();
        if (payload == null) return files;
        String submissionId = payload.optString("submissionId", null);
        if (submissionId == null) {
            System.out.println("WARNING: submissionId required; no files possible");
            return files;
        }

        Map<String, String> values = new HashMap<String, String>();
        values.put("submissionId", submissionId);
        File uploadDir = new File(UploadServlet.getUploadDir(request, values));
        System.out.println("findFiles() uploadDir=" + uploadDir);
        if (!uploadDir.exists()) throw new IOException("uploadDir for submissionId=" + submissionId + " does not exist");

        List<String> filenames = new ArrayList<String>();
        JSONArray fnArr = payload.optJSONArray("assetFilenames");
        if (fnArr != null) {
            for (int i = 0 ; i < fnArr.length() ; i++) {
                String fn = fnArr.optString(i, null);
                if (fn != null) filenames.add(fn);
            }
        } else {
            for (File f : uploadDir.listFiles()) {
                filenames.add(f.getName());
            }
        }
        if (filenames.size() < 1) return files;
        for (String fname : filenames) {
            File file = new File(uploadDir, fname);
            if (!file.exists()) throw new IOException(file + " does not exist in uploadDir");
            files.add(file);
        }
        System.out.println("findFiles(): files=" + files);
        return files;
    }

    private Map<File, MediaAsset> makeMediaAssets(String encounterId, List<File> files, Shepherd myShepherd)
    throws ApiException {
        Map<File, MediaAsset> results = new HashMap<File, MediaAsset>();
        AssetStore astore = AssetStore.getDefault(myShepherd);
        for (File file : files) {
            if (!AssetStore.isValidImage(file)) {
                System.out.println("BaseObject.makeMediaAssets() failed isValidImage() on " + file);
                results.put(file, null);
                continue;
            }
            String sanitizedItemName = ServletUtilities.cleanFileName(file.getName());
            JSONObject sp = astore.createParameters(new File(Encounter.subdir(encounterId) + File.separator + sanitizedItemName));
            sp.put("userFilename", file.getName());
            System.out.println("makeMediaAssets(): file=" + file + " => " + sp);
            MediaAsset ma = new MediaAsset(astore, sp);
            ma.addLabel("_original");
System.out.println(">>>>>>>>>> ma => " + ma);
            try {
                ma.copyIn(file);
                ma.validateSourceImage();
                ma.updateMetadata();
                results.put(file, ma);
            } catch (IOException ioe) {
                System.out.println("BaseObject.makeMediaAssets() failed on " + file + ": " + ioe);
                ioe.printStackTrace();
                results.put(file, null);
            }
        }
        return results;
    }

/*
    private void makeMediaAssetsFromJavaFileItemObject(FileItem item, String encID,
        AssetStore astore, Encounter enc, ArrayList<Annotation> newAnnotations, String genus,
        String specificEpithet) {
        String sanitizedItemName = ServletUtilities.cleanFileName(item.getName());
        JSONObject sp = astore.createParameters(new File(enc.subdir() + File.separator +
            sanitizedItemName));

        sp.put("userFilename", item.getName());
        sp.put("key", Util.hashDirectories(encID) + "/" + sanitizedItemName);
        MediaAsset ma = new MediaAsset(astore, sp);
        File tmpFile = ma.localPath().toFile(); // conveniently(?) our local version to save ma.cacheLocal() from having to do anything?
        File tmpDir = tmpFile.getParentFile();
        if (!tmpDir.exists()) tmpDir.mkdirs();
// System.out.println("attempting to write uploaded file to " + tmpFile);
        try {
            item.write(tmpFile);
        } catch (Exception ex) {
            System.out.println("Could not write " + tmpFile + ": " + ex.toString());
        }
        if (tmpFile.exists()) {
            try {
                ma.addLabel("_original");
                ma.copyIn(tmpFile);
                ma.validateSourceImage();
                ma.updateMetadata();
                newAnnotations.add(new Annotation(Util.taxonomyString(genus, specificEpithet), ma));
            } catch (IOException ioe) {
                System.out.println("Hit an IOException trying to transform file " + item.getName() +
                    " into a MediaAsset in EncounterFom.class.");
                ioe.printStackTrace();
            }
        } else {
            System.out.println("failed to write file " + tmpFile);
        }
    }
*/


}
