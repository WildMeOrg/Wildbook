package org.ecocean.api;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import org.ecocean.resumableupload.UploadServlet;
import org.ecocean.Util;

public class UploadedFiles {

    public static List<File> findFiles(HttpServletRequest request, String submissionId)
    throws IOException {
        return findFiles(request, submissionId, null);
    }

    public static List<File> findFiles(HttpServletRequest request, String submissionId, Set<String> filenames)
    throws IOException {
        List<File> files = new ArrayList<File>();
        if (!Util.isUUID(submissionId)) {
            System.out.println("WARNING: valid submissionId required; no files possible");
            return files;
        }
        Map<String, String> values = new HashMap<String, String>();
        values.put("submissionId", submissionId);
        File uploadDir = new File(UploadServlet.getUploadDir(request, values));
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

}
