package org.ecocean.servlet;

import org.ecocean.*;
import org.ecocean.ia.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import org.ecocean.media.*;
import org.ecocean.resumableupload.UploadServlet;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import java.io.*;

public class MediaAssetCreate extends HttpServlet {
    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doOptions(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        ServletUtilities.doOptions(request, response);
    }

    // this is a little hacky, but it is a way for the browser/client to request a MediaAssetSet with which to associate MediaAssets
    // i guess we should *enforce* (require) this to have some sort of sanity around preventing backdoors to overwriting MediaAssets or whatever
    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*"); // allow us stuff from localhost
        if (request.getParameter("requestMediaAssetSet") == null)
            throw new IOException("invalid GET parameters");
        String context = "context0";
        context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("MediaAssetCreate.class");

        // note: a null status will be considered throw-away, cuz we no doubt will get aborted uploads etc.
        MediaAssetSet maSet = new MediaAssetSet();
        myShepherd.beginDBTransaction();
        myShepherd.getPM().makePersistent(maSet);
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();

        response.setContentType("text/plain");
        JSONObject res = new JSONObject();
        res.put("mediaAssetSetId", maSet.getId());

        PrintWriter out = response.getWriter();
        out.println(res.toString());
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*"); // allow us stuff from localhost
        String context = "context0";
        // context=ServletUtilities.getContext(request);

        // set up for response
        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();
        JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);
        if (!ReCAPTCHA.sessionIsHuman(request)) {
            System.out.println("WARNING: MediaAssetCreate failed sessionIsHuman()");
            response.setStatus(403);
            out.println("ERROR");
            out.close();
            return;
        }
        JSONObject res = new JSONObject();
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("MediaAssetCreate.class_nonum");
        myShepherd.beginDBTransaction();
        try {
            res = createMediaAssets(j.optJSONArray("MediaAssetCreate"), myShepherd, request);
            myShepherd.commitDBTransaction();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            myShepherd.rollbackDBTransaction();
        } finally {
            myShepherd.closeDBTransaction();
        }
        // this does children assets in background
        JSONArray ids = res.optJSONArray("allMediaAssetIds");
        List<Integer> idInts = new ArrayList<Integer>();
        for (int i = 0; i < ids.length(); i++) {
            int idInt = ids.optInt(i, -2);
            if (idInt > 0) idInts.add(idInt);
        }
        MediaAsset.updateStandardChildrenBackground(context, idInts);
        // this has to be after commit (so queue can find them from different thread), so we do a little work here
        if (!j.optBoolean("skipIA", false) && (idInts.size() > 0)) {
            myShepherd = new Shepherd(context);
            myShepherd.setAction("MediaAssetCreate.class_IA.intake");
            myShepherd.beginDBTransaction();
            try {
                List<MediaAsset> allMAs = new ArrayList<MediaAsset>();
                for (Integer id : idInts) {
                    if (id < 0) continue;
                    MediaAsset ma = MediaAssetFactory.load(id, myShepherd);
                    if (ma != null) allMAs.add(ma);
                }
                if (allMAs.size() > 0) {
                    System.out.println("Starting IA.intakeMediaAssets");
                    final Task parentTask = new Task();
                    String locationID = "";
                    if (j.optString("locationID", null) != null && !j.optString("locationID",
                        null).equals("null")) {
                        locationID = j.getString("locationID");
                        JSONObject tp = new JSONObject();
                        JSONObject mf = new JSONObject();
                        mf.put("locationId", locationID);
                        tp.put("matchingSetFilter", mf);
                        parentTask.setParameters(tp);
                    }
                    Task task = null;
                    Taxonomy taxy = null;
                    if (j.optString("taxonomy") != null && !j.optString("taxonomy",
                        null).equals("null")) {
                        taxy = new Taxonomy(j.getString("taxonomy"));
                    }
                    if (taxy != null) {
                        task = IA.intakeMediaAssetsOneSpecies(myShepherd, allMAs, taxy, parentTask);
                    } else {
                        task = IA.intakeMediaAssets(myShepherd, allMAs);
                    }
                    System.out.println("Out of IA.intakeMediaAssets");
                    myShepherd.storeNewTask(task);
                    res.put("IATaskId", task.getId());
                }
                myShepherd.commitDBTransaction();
            } catch (Exception e) {
                e.printStackTrace();
                myShepherd.rollbackDBTransaction();
            } finally {
                myShepherd.closeDBTransaction();
            }
        }
        out.println(res.toString());
        out.close();
    }

    private JSONObject createMediaAssets(JSONArray jarr, Shepherd myShepherd,
        HttpServletRequest request)
    throws IOException {
        String context = myShepherd.getContext();
        JSONObject rtn = new JSONObject();

        if (jarr == null) return rtn;
/*
    NOTE: for now we dont allow user to set AssetStore, so we have some "hard-coded" ways to deal with:
    - local (via CommonConfiguration tmp dir and "filename" value)
    - URL
 */
        String uploadTmpDir = UploadServlet.getUploadDir(request);
        AssetStore targetStore = AssetStore.getDefault(myShepherd); // see below about disabled user-provided stores
        HashMap<String, MediaAssetSet> sets = new HashMap<String, MediaAssetSet>();
        ArrayList<MediaAsset> haveNoSet = new ArrayList<MediaAsset>();
        URLAssetStore urlStore = URLAssetStore.find(myShepherd); // this is only needed if we get passed params for url
        JSONArray attachRtn = new JSONArray();
        for (int i = 0; i < jarr.length(); i++) {
            JSONObject st = jarr.optJSONObject(i);
            if (st == null) continue;

            String setId = st.optString("setId", null);
            // attempt to validate setId (if we have one)
            if ((setId != null) && (sets.get(setId) == null)) {
                MediaAssetSet s = null;
                try {
                    s = ((MediaAssetSet)(myShepherd.getPM().getObjectById(
                        myShepherd.getPM().newObjectIdInstance(MediaAssetSet.class, setId), true)));
                } catch (Exception ex) {} // usually(?) not found :)
                if (s == null) {
                    System.out.println(
                        "WARNING createMediaAssets() could not find MediaAssetSet id=" + setId +
                        "; skipping");                                                                                          // invalid, so fail!
                    continue;
                } else {
                    sets.put(setId, s);
                }
            }
            JSONArray assets = st.optJSONArray("assets");
            if ((assets == null) || (assets.length() < 1)) {
                System.out.println(
                    "WARNING: createMediaAssets() - assets array missing or empty for i=" + i);
                continue;
            }
            List<MediaAsset> mas = new ArrayList<MediaAsset>();
            for (int j = 0; j < assets.length(); j++) {
                boolean success = true;
                MediaAsset targetMA = null;
                JSONObject params = assets.optJSONObject(j);
                if (params == null) continue;
                String fname = params.optString("filename", null);
                String userFilename = params.optString("userFilename", fname);
                String url = params.optString("url", null);
                String accessKey = params.optString("accessKey", null); // kinda specialty use to validate certain anon-uploaded cases (e.g. match.jsp)
                if (fname != null) { // this is local
                    if (fname.indexOf("..") > -1) continue; // no hax0ring plz
                    File inFile = new File(uploadTmpDir, fname);
                    params = targetStore.createParameters(inFile);
                    params.put("userFilename", userFilename);
                    if (accessKey != null) params.put("accessKey", accessKey);
                    targetMA = targetStore.create(params);
                    try {
                        targetMA.copyIn(inFile);
                    } catch (Exception ex) {
                        System.out.println("WARNING: MediaAssetCreate failed to copyIn " + inFile +
                            " to " + targetMA + ": " + ex.toString());
                        success = false;
                    }
                } else if (url != null) {
                    if (urlStore == null) {
                        System.out.println(
                            "WARNING: MediaAssetCreate found no URLAssetStore; skipping url param "
                            + url);
                    } else {
                        targetMA = urlStore.create(params);
                    }
                }
                if (success) {
                    targetMA.validateSourceImage();
                    targetMA.updateMetadata();
                    targetMA.addLabel("_original");
                    targetMA.setAccessControl(request);
                    MediaAssetFactory.save(targetMA, myShepherd);
                    if (setId != null) {
                        System.out.println("MediaAssetSet " + setId + " created " + targetMA);
                        sets.get(setId).addMediaAsset(targetMA);
                        sets.get(setId).setStatus("active");
                    } else {
                        System.out.println("no MediaAssetSet; created " + targetMA);
                        haveNoSet.add(targetMA);
                    }
                    mas.add(targetMA);
                }
            }
            // this duplicates some of MediaAssetAttach, but lets us get done in one API call
            // TODO: sanity-check *ownership* of the encounter
            JSONObject attEnc = st.optJSONObject("attachToEncounter");
            JSONObject attOcc = st.optJSONObject("attachToOccurrence");
            if (attEnc != null) {
                JSONObject artn = new JSONObject();
                Encounter enc = myShepherd.getEncounter(attEnc.optString("id", "__FAIL__"));
                if (enc != null) {
                    artn.put("id", enc.getCatalogNumber());
                    artn.put("type", "Encounter");
                    artn.put("assets", new JSONArray());
                    for (MediaAsset ema : mas) {
                        if (enc.hasTopLevelMediaAsset(ema.getIdInt())) continue;
                        enc.addMediaAsset(ema);
                        artn.getJSONArray("assets").put(ema.getIdInt());
                    }
                    System.out.println("MediaAssetCreate.attachToEncounter added " +
                        artn.getJSONArray("assets").length() + " assets to Enc " +
                        enc.getCatalogNumber());
                }
                attachRtn.put(artn);
            } else if (attOcc != null) { // this requires a little extra, to make the enc, minimum is taxonomy
                String tax = attOcc.optString("taxonomy", null);
                JSONObject artn = new JSONObject();
                Occurrence occ = myShepherd.getOccurrence(attOcc.optString("id", "__FAIL__"));
                if ((tax == null) || (occ == null)) {
                    System.out.println(
                        "MediaAssetCreate.attachToOccurrence ERROR had invalid .taxonomy or bad id; skipping "
                        + attOcc);
                } else {
                    artn.put("id", occ.getOccurrenceID());
                    artn.put("assets", new JSONArray());
                    artn.put("type", "Occurrence");
                    ArrayList<Annotation> anns = new ArrayList<Annotation>();
                    for (MediaAsset ema : mas) {
                        Annotation ann = new Annotation(tax, ema);
                        anns.add(ann);
                        artn.getJSONArray("assets").put(ema.getId());
                    }
                    Encounter enc = new Encounter(anns);
                    enc.setTaxonomyFromString(tax);
                    enc.addSubmitter(AccessControl.getUser(request, myShepherd));
                    enc.addComments("<p>created by MediaAssetCreate attaching to Occurrence " +
                        occ.getOccurrenceID() + "</p>");
                    occ.addEncounter(enc);
                    artn.put("encounterId", enc.getCatalogNumber());
                    myShepherd.getPM().makePersistent(enc);
                    System.out.println("MediaAssetCreate.attachToOccurrence added Enc " +
                        enc.getCatalogNumber() + " to Occ " + occ.getOccurrenceID());
                }
                attachRtn.put(artn);
            }
        }
        if (attachRtn.length() > 0) rtn.put("attached", attachRtn);
        JSONObject js = new JSONObject();
        JSONArray allMAIds = new JSONArray();
        for (MediaAssetSet s : sets.values()) {
            JSONArray jmas = new JSONArray();
            if ((s.getMediaAssets() != null) && (s.getMediaAssets().size() > 0)) {
                for (MediaAsset ma : s.getMediaAssets()) {
                    JSONObject jma = new JSONObject();
                    try {
                        jma = Util.toggleJSONObject(ma.sanitizeJson(request,
                            new org.datanucleus.api.rest.orgjson.JSONObject(), true));
                    } catch (Exception ex) {
                        System.out.println("WARNING: failed sanitizeJson on " + ma + ": " +
                            ex.toString());
                    }
                    jma.put("_debug", ma.toString());
                    jma.put("_params", ma.getParameters().toString());
                    jmas.put(jma);
                    allMAIds.put(ma.getId());
                }
            }
            if (jmas.length() > 0) js.put(s.getId(), jmas);
        }
        rtn.put("sets", js);
        if (haveNoSet.size() > 0) {
            JSONArray jmas = new JSONArray();
            for (MediaAsset ma : haveNoSet) {
                JSONObject jma = new JSONObject();
                try {
                    jma = Util.toggleJSONObject(ma.sanitizeJson(request,
                        new org.datanucleus.api.rest.orgjson.JSONObject(), true));
                } catch (Exception ex) {
                    System.out.println("WARNING: failed sanitizeJson on " + ma + ": " +
                        ex.toString());
                }
                // "url" does not get set in sanitizeJson cuz it uses a new Shepherd object, sigh.  so:
                URL u = ma.safeURL(myShepherd);
                if (u != null) jma.put("url", u.toString());
                jma.put("_debug", ma.toString());
                jma.put("_params", ma.getParameters().toString());
                jmas.put(jma);
                allMAIds.put(ma.getId());
            }
            rtn.put("withoutSet", jmas);
        }
        rtn.put("allMediaAssetIds", allMAIds);
        rtn.put("success", true);
        return rtn;
    }
}
