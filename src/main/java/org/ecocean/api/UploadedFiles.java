package org.ecocean.api;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import org.ecocean.Encounter;
import org.ecocean.media.AssetStore;
import org.ecocean.media.MediaAsset;
import org.ecocean.resumableupload.UploadServlet;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.Util;

public class UploadedFiles {
    public static List<File> findFiles(HttpServletRequest request, String submissionId)
    throws IOException {
        return findFiles(request, submissionId, null);
    }

    public static List<File> findFiles(HttpServletRequest request, String submissionId,
        Set<String> filenames)
    throws IOException {
        List<File> files = new ArrayList<File>();

        if (!Util.isUUID(submissionId)) {
            System.out.println("WARNING: valid submissionId required; no files possible");
            return files;
        }
        File uploadDir = getUploadDir(request, submissionId);
        System.out.println("findFiles() uploadDir=" + uploadDir);
        if (!uploadDir.exists())
            throw new IOException("uploadDir for submissionId=" + submissionId + " does not exist");
        if (filenames == null) {
            filenames = new HashSet<String>();
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

    // this default behavior WILL CREATE the dir if it does not exist
    // use skipCreation=true below, otherwise
    public static File getUploadDir(HttpServletRequest request, String submissionId)
    throws IOException {
        return getUploadDir(request, submissionId, false);
    }

    public static File getUploadDir(HttpServletRequest request, String submissionId,
        boolean skipCreation)
    throws IOException {
        Map<String, String> values = new HashMap<String, String>();

        values.put("submissionId", submissionId);
        values.put("skipCreation", Boolean.toString(skipCreation));
        return new File(UploadServlet.getUploadDir(request, values));
    }

/*
    private Map<File, MediaAsset> makeMediaAssets(String encounterId, List<File> files,
        Shepherd myShepherd)
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
            JSONObject sp = astore.createParameters(new File(Encounter.subdir(encounterId) +
                File.separator + sanitizedItemName));
            sp.put("userFilename", file.getName());
            System.out.println("makeMediaAssets(): file=" + file + " => " + sp);
            MediaAsset ma = new MediaAsset(astore, sp);
            ma.addLabel("_original");
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
 */
    public static MediaAsset makeMediaAsset(File file, Shepherd myShepherd)
    throws ApiException {
        return makeMediaAsset(Util.generateUUID(), file, myShepherd);
    }

    // note: this sets auto-indexing false on the MediaAsset, so alter after if desired
    public static MediaAsset makeMediaAsset(String dirId, File file, Shepherd myShepherd)
    throws ApiException {
        JSONObject error = new JSONObject();

        error.put("code", ApiException.ERROR_RETURN_CODE_INVALID);
        error.put("filename", file.getName());
        AssetStore astore = AssetStore.getDefault(myShepherd);
        if (!AssetStore.isValidImage(file)) {
            System.out.println("UploadedFiles.makeMediaAsset() failed isValidImage() on " + file);
            throw new ApiException(file.getName() + " is not a valid image file", error);
        }
        String sanitizedItemName = ServletUtilities.cleanFileName(file.getName());
        JSONObject sp = astore.createParameters(new File(Encounter.subdir(dirId) + File.separator +
            sanitizedItemName));
        sp.put("userFilename", file.getName());
        System.out.println("UploadedFiles.makeMediaAsset(): file=" + file + " => " + sp);
        MediaAsset ma = new MediaAsset(astore, sp);
        ma.setSkipAutoIndexing(true);
        ma.addLabel("_original");
        boolean valid = false;
        try {
            ma.copyIn(file);
            valid = ma.validateSourceImage();
            ma.updateMetadata();
        } catch (IOException ioe) {
            System.out.println("UploadedFiles.makeMediaAsset() failed on " + file + ": " + ioe);
            ioe.printStackTrace();
            error.put("code", ApiException.ERROR_RETURN_CODE_UNKNOWN);
            throw new ApiException(file.getName() + " MediaAsset creation threw: " + ioe);
        }
        if (!valid)
            throw new ApiException(file.getName() +
                    " MediaAsset creation failed validateSourceImage()");
        return ma;
    }
}
