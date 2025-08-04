package org.ecocean.api;

import java.io.IOException;
import java.net.URL;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.json.JSONArray;
import org.json.JSONObject;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.media.Feature;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.User;

// note: this is for use on any non-Base object
// see api/BaseObject if object extends Base.java
public class GenericObject extends ApiBase {
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("api.GenericObject.doGet");
        myShepherd.beginDBTransaction();

        String uri = request.getRequestURI();
        String[] args = uri.substring(8).split("/");
        if (args.length < 1) throw new ServletException("bad path");

        JSONObject rtn = new JSONObject();
        rtn.put("success", false);
        try {
            User currentUser = myShepherd.getUser(request);
            switch (args[0]) {
            case "media-assets":
                if (currentUser == null) {
                        rtn.put("statusCode", 401);
                        rtn.put("error", "access denied");
                } else {
                    MediaAsset ma = null;
                    URL url = null;
                    try {
                        ma = MediaAssetFactory.load(Integer.parseInt(args[1]), myShepherd);
                        if (ma != null) url = ma.safeURL(myShepherd, request);
                    } catch (Exception ex) {
                        throw new ApiException(ex.toString());
                    }
                    if (ma == null) {
                        rtn.put("statusCode", 404);
                        rtn.put("error", "not found");
                    } else {
                        rtn.put("success", true);
                        rtn.put("statusCode", 200);
                        rtn.put("url", url.toString());
                        rtn.put("width", ma.getWidth());
                        rtn.put("height", ma.getHeight());
                        JSONArray janns = new JSONArray();
                        for (Annotation ann : ma.getAnnotations()) {
                            JSONObject jann = new JSONObject();
                            if (ann.getFeatures() != null) {
                                for (Feature ft : ann.getFeatures()) {
                                    if (ft.isUnity()) {
                                        jann.put("trivial", true);
                                        jann.put("x", 0);
                                        jann.put("y", 0);
                                        jann.put("width", (int)ma.getWidth());
                                        jann.put("height", (int)ma.getHeight());
                                    } else {
                                        // basically if we have more than one feature, only one wins
                                        if (ft.getParameters() != null) jann = ft.getParameters();
                                    }
                                }
                            }
                            Encounter enc = ann.findEncounter(myShepherd);
                            if (enc != null) {
                                jann.put("encounterId", enc.getId());
                                jann.put("encounterTaxonomy", enc.getTaxonomyString());
                            }
                            jann.put("id", ann.getId());
                            janns.put(jann);
                        }
                        rtn.put("annotations", janns);
                    }
                }
                break;
            default:
                throw new ApiException("bad class");
            }

        } catch (ApiException apiEx) {
            rtn.put("statusCode", 400);
            rtn.put("errors", apiEx.getErrors());
            rtn.put("debug", apiEx.toString());
        } finally {
            myShepherd.rollbackAndClose();
        }

        response.setStatus(rtn.optInt("statusCode", 500));
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(rtn.toString());
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        throw new ServletException("not yet supported");
    }

}
