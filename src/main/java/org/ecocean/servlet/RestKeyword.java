package org.ecocean.servlet;

import org.ecocean.Annotation;
import org.ecocean.ia.IA;
import org.ecocean.identity.IBEISIA;
import org.ecocean.Keyword;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.Shepherd;
import org.ecocean.Taxonomy;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

// handles operations to sharks. possible operations include, create new, add encounter, remove encounter from
public class RestKeyword extends HttpServlet {
    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        JSONObject jin = ServletUtilities.jsonFromHttpServletRequest(request);
        JSONObject jout = new JSONObject("{\"success\": false}");

        if (request.getUserPrincipal() == null) {
            response.setStatus(401);
            jout.put("error", "access denied");
        } else if (jin.optJSONObject("onMediaAssets") != null) {
            JSONObject jadd = jin.getJSONObject("onMediaAssets");
            JSONArray aids = jadd.optJSONArray("assetIds");
            JSONArray remove = jadd.optJSONArray("remove");
            JSONArray add = jadd.optJSONArray("add");
            JSONArray newAdd = jadd.optJSONArray("newAdd");
            if (aids == null) {
                jout.put("error", "no required assetIds [] passed");
            } else if ((remove == null) && (add == null) && (newAdd == null)) {
                jout.put("error", "must have one of remove, add, or newAdd");
            } else {
                Shepherd myShepherd = new Shepherd(context);
                myShepherd.setAction("RestKeyword.class");
                try {
                    myShepherd.beginDBTransaction();
                    List<MediaAsset> mas = new ArrayList<MediaAsset>();
                    for (int i = 0; i < aids.length(); i++) {
                        MediaAsset ma = MediaAssetFactory.load(aids.optInt(i, -99), myShepherd);
                        if (ma != null) mas.add(ma);
                    }
                    if (mas.size() < 1) {
                        jout.put("error", "no MediaAssets to act upon");
                    } else {
                        List<Keyword> toAdd = new ArrayList<Keyword>();
                        if ((add != null) && (add.length() > 0)) {
                            for (int k = 0; k < add.length(); k++) {
                                Keyword kw = null;
                                try {
                                    kw = (Keyword)(myShepherd.getPM().getObjectById(
                                        myShepherd.getPM().newObjectIdInstance(Keyword.class,
                                        add.optString(k, null)), true));
                                } catch (Exception ex) {}
                                if (kw == null) continue;
                                if (!toAdd.contains(kw)) toAdd.add(kw);
                            }
                        }
                        if ((newAdd != null) && (newAdd.length() > 0)) {
                            JSONObject newj = new JSONObject();
                            for (int k = 0; k < newAdd.length(); k++) {
                                String name = newAdd.optString(k, null);
                                if (name == null) continue;
                                Keyword kw = null;
                                try {
                                    kw = myShepherd.getKeyword(name);
                                } catch (Exception ex) {}
                                if (kw != null) {
                                    if (!toAdd.contains(kw)) toAdd.add(kw);
                                } else {
                                    kw = new Keyword(name);
                                    myShepherd.getPM().makePersistent(kw);
                                    toAdd.add(kw);
                                    newj.put(kw.getIndexname(), kw.getDisplayName());
                                }
                                if (newj.length() > 0) jout.put("newKeywords", newj);
                                System.out.println("INFO: RestKeyword new keywords = " + newj);
                            }
                        }
                        List<String> toRemove = new ArrayList<String>();
                        if ((remove != null) && (remove.length() > 0)) {
                            for (int i = 0; i < remove.length(); i++) {
                                String id = remove.optString(i, null);
                                if ((id != null) && !toRemove.contains(id)) toRemove.add(id);
                            }
                        }
                        Taxonomy taxy = IBEISIA.taxonomyFromMediaAssets(context, mas, myShepherd); // keeps our logic consistent w/ IA stuff

                        System.out.println("INFO: RestKeyword mas = " + mas.toString());
                        System.out.println("INFO: RestKeyword toAdd = " + toAdd.toString());
                        System.out.println("INFO: RestKeyword toRemove = " + toRemove.toString());

                        JSONObject jassigned = new JSONObject();
                        for (MediaAsset ma : mas) {
                            ArrayList<Keyword> mine = ma.getKeywords();
                            System.out.println("mine -> " + mine);
                            if (mine == null) mine = new ArrayList<Keyword>();
                            ArrayList<Keyword> newList = new ArrayList<Keyword>();
                            // first add what should be added
                            for (int i = 0; i < toAdd.size(); i++) {
                                if (!mine.contains(toAdd.get(i))) newList.add(toAdd.get(i));
                                // Here we also want to set IA viewpoint
                                Keyword kw = toAdd.get(i);
                                String kwName = kw.getReadableName();
                                // We could simply use kwName as the viewpoint, but not sure if IA would play nicely with "Tail Fluke" for humpbacks
                                String viewpoint = getViewpoint(kwName);
                                if (viewpoint != null) {
                                    ArrayList<Annotation> anns = ma.getAnnotations();
                                    for (Annotation ann : anns) {
                                        String uuid = ann.getAcmId();
                                        System.out.println(
                                            "RestKeyword servlet! About to set viewpoint " +
                                            viewpoint + " on ann " + ann);
                                        if (uuid != null)
                                            IBEISIA.iaSetViewpointForAnnotUUID(uuid, viewpoint,
                                                context);
                                    }
                                }
                                // Done set IA viewpoint
                            }
                            // then add from mine except where should be removed
                            for (int i = 0; i < mine.size(); i++) {
                                if (!toRemove.contains(mine.get(i).getIndexname()))
                                    newList.add(mine.get(i));
                            }
                            System.out.println(ma + " ----------> " + newList);
                            JSONObject mj = new JSONObject();
                            for (Keyword k : newList) {
                                mj.put(k.getIndexname(), k.getDisplayName());
                            }
                            if (newList.size() < 1) {
                                ma.setKeywords(null);
                            } else {
                                ma.setKeywords(newList);
                            }
                            System.out.println("getKeywords() -> " + ma.getKeywords());
                            jassigned.put(Integer.toString(ma.getId()), mj);
                            MediaAssetFactory.save(ma, myShepherd);
                        }
                        jout.put("results", jassigned);
                        jout.put("success", true);
                        myShepherd.commitDBTransaction();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    myShepherd.rollbackDBTransaction();
                } finally {
                    myShepherd.closeDBTransaction();
                }
            }
        } else {
            jout.put("error", "unknown command");
        }
        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();
        out.println(jout.toString());
        out.flush();
        out.close();
    }

    // logic custom to Flukebook, currently only applicable for dolphins
    public static String getViewpoint(String kwName) {
        if (kwName == null) return null;
        String lower = kwName.toLowerCase();
        if (lower.contains("left")) return "left";
        if (lower.contains("right")) return "right";
        return null;
    }

    // This gets the corresponding IA viewpoint for a kwName, taxonomy (opt null), and context
    // the properties keys are of the format:
    // labelerModelTag_Scintificus_namus_Key_Word_Name=viewpointValueForIa
    public static String getViewpoint(String kwName, Taxonomy taxy, String context) {
        if (kwName == null) return null;
        // taxonomyStr is either "" or something like "_Tursiops_truncatus_"
        String taxonomyStr = (taxy == null) ? "" : "_" + taxy.getScientificName().replaceAll(" ",
            "_") + "_";
        String propKey = "labelerModelTag" + taxonomyStr + kwName.replaceAll(" ", "_");
        return IA.getProperty(context, propKey);
    }

    public static String getKwNameFromIaViewpoint(String iaViewpoint) {
        if (iaViewpoint == null) return null;
        String lower = iaViewpoint.toLowerCase();
        if (lower.contains("left")) return "Left Side";
        if (lower.contains("right")) return "Right Side";
        return null;
    }

    // This gets the corresponding kwName for an iaViewpoint, taxonomy (opt null), and context
    // the properties keys are of the format:
    // labelerModelTag_Scintificus_namus_iaViewpoint=kwName
    public static String getKwNameFromIaViewpoint(String iaViewpoint, Taxonomy taxy,
        String context) {
        if (iaViewpoint == null) return null;
        String lower = iaViewpoint.toLowerCase();
        // taxonomyStr is either "" or something like "_Tursiops_truncatus_"
        String taxonomyStr = (taxy == null) ? "" : "_" + taxy.getScientificName().replaceAll(" ",
            "_") + "_";
        String propKey = "labelerModelTag" + taxonomyStr + iaViewpoint.replaceAll(" ", "_");
        System.out.println("[INFO]: getKwNameFromIaViewpoint looking for propKey " + propKey);
        return IA.getProperty(context, propKey);
    }
}
