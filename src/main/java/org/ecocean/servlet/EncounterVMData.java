package org.ecocean.servlet;

import org.ecocean.Encounter;
import org.ecocean.media.*;
import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import javax.jdo.Query;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import com.google.gson.Gson;

public class EncounterVMData extends HttpServlet {
    public static int MAX_MATCH = 5000; // most we should send back as matching candidates

    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = "context0";

        context = ServletUtilities.getContext(request);

        boolean locked = false, isOwner = true;
        HashMap rtn = new HashMap();
        boolean wantJson = true;
        String redirUrl = null;
        if (request.getUserPrincipal() == null) {
            rtn.put("error", "no access");
        } else if (request.getParameter("number") != null) {
            Shepherd myShepherd = new Shepherd(context);
            myShepherd.setAction("EncounterVMData.class");
            myShepherd.beginDBTransaction();
            try {
                Encounter enc = myShepherd.getEncounter(request.getParameter("number"));
                if (enc == null) {
                    rtn.put("error", "invalid Encounter number");
                } else if (request.getParameter("matchID") != null) {
                    wantJson = false;

                    // we may also be assigning the candidate encounter (if we are allowed)
                    Encounter candEnc = null;
                    if (request.getParameter("candidate_number") != null) {
                        candEnc = myShepherd.getEncounter(request.getParameter("candidate_number"));
                    }
                    if (ServletUtilities.isUserAuthorizedForEncounter(enc, request)) {
                        String matchID = ServletUtilities.cleanFileName(request.getParameter(
                            "matchID"));
                        // System.out.println("setting indiv id = " + matchID + " on enc id = " + enc.getCatalogNumber());
                        MarkedIndividual indiv = myShepherd.getMarkedIndividualQuiet(matchID);
                        if (indiv == null) { // must have sent a new one
                            indiv = new MarkedIndividual(matchID, enc);
                            myShepherd.getPM().makePersistent(indiv);

                            indiv.addComments("<p><em>" + request.getRemoteUser() + " on " +
                                (new java.util.Date()).toString() + "</em><br>" + "Created " +
                                matchID + ".</p>");
                            indiv.setDateTimeCreated(ServletUtilities.getDate());
                            myShepherd.updateDBTransaction();
                            // candEnc should only ever get assigned for *new indiv* hence this code here
                            if ((candEnc != null) &&
                                ServletUtilities.isUserAuthorizedForEncounter(candEnc, request)) {
                                candEnc.addComments("<p><em>" + request.getRemoteUser() + " on " +
                                    (new java.util.Date()).toString() + "</em><br>" + "Added to " +
                                    matchID + ".</p>");
                                candEnc.setMatchedBy("Visual Matcher");
                                indiv.addEncounter(candEnc);
                            }
                        } else {
                            enc.setIndividual(indiv);
                        }
                        enc.addComments("<p><em>" + request.getRemoteUser() + " on " +
                            (new java.util.Date()).toString() + "</em><br>" + "Added to " +
                            matchID + ".</p>");
                        enc.setMatchedBy("Visual Matcher");
                        myShepherd.commitDBTransaction();
                        redirUrl = "encounters/encounter.jsp?number=" + enc.getCatalogNumber();
                    } else {
                        rtn.put("error", "unauthorized");
                    }
                } else if (request.getParameter("candidates") != null) {
                    rtn.put("_wantCandidates", true);
                    ArrayList candidates = new ArrayList();
                    String filter = "select from org.ecocean.Encounter where catalogNumber != '" +
                        enc.getCatalogNumber() + "'";
                    filter += " && genus == '" + enc.getGenus() + "'";
                    filter += " && specificEpithet == '" + enc.getSpecificEpithet() + "'";
                    String[] fields = { "locationID", "sex", "patterningCode" };
                    for (String f : fields) {
                        String val = request.getParameter(f);
                        if (val != null) filter += " && " + f + " == '" + val + "'"; 
                    }
                    String mma = request.getParameter("mmaCompatible");
                    if ((mma != null) && !mma.equals("")) {
                        if (mma.equals("true")) {
                            filter += " && mmaCompatible == true";
                        } else {
                            filter += " && (mmaCompatible == false || mmaCompatible == null)";
                        }
                    }
                    System.out.println("candidate filter => " + filter);
                    long startTime = System.currentTimeMillis();
                    Query q = myShepherd.getPM().newQuery(filter);
                    ArrayList<Encounter> results = new ArrayList<Encounter>();
                    try {
                        Collection c = (Collection)q.execute();
                        results = new ArrayList<Encounter>(c);
                    } catch (Exception p) {
                        System.out.println("EncounterVMData exception caught!");
                        p.printStackTrace();
                    } finally {
                        q.closeAll();
                    }
                    int resultsSize = results.size();
                    System.out.println("EncounterVM query took " +
                        (System.currentTimeMillis() - startTime) +
                        " milliseconds. Result set was: " + resultsSize);
                    int numConsidered = 0;
                    for (int i = 0; ((i < resultsSize) && (candidates.size() < MAX_MATCH)); i++) {
                        System.out.println("     i=" + i);
                        Encounter cand = results.get(i);
                        numConsidered++;
                        HashMap e = new HashMap();
                        e.put("id", cand.getCatalogNumber());
                        e.put("dateInMilliseconds", cand.getDateInMilliseconds());
                        e.put("locationID", cand.getLocationID());
                        if (cand.getIndividual() != null) {
                            e.put("individualID",
                                ServletUtilities.handleNullString(ServletUtilities.handleNullString(
                                cand.getIndividual().getIndividualID())));
                            e.put("displayName",
                                ServletUtilities.handleNullString(ServletUtilities.handleNullString(
                                cand.getIndividual().getDisplayName(request, myShepherd))));
                        } else {
                            e.put("individualID", null);
                            e.put("displayName", null);
                        }
                        e.put("patterningCode", cand.getPatterningCode());
                        e.put("sex", cand.getSex());
                        e.put("mmaCompatible", cand.getMmaCompatible());

                        addImages(cand, e, myShepherd, request);
                        candidates.add(e);
                    }
                    boolean maxCandidatesReached = true;
                    if (numConsidered < MAX_MATCH) maxCandidatesReached = false;
                    rtn.put("maximumCandidatesReached", maxCandidatesReached);
                    if (!candidates.isEmpty()) rtn.put("candidates", candidates);
                    System.out.println("EncounterVM total execution time: " +
                        (System.currentTimeMillis() - startTime) + " milliseconds");
                } else {
                    addImages(enc, rtn, myShepherd, request);
                    rtn.put("id", enc.getCatalogNumber());
                    rtn.put("patterningCode", enc.getPatterningCode());
                    rtn.put("sex", enc.getSex());
                    rtn.put("locationID", enc.getLocationID());
                    if (enc.getIndividual() != null) {
                        rtn.put("displayName",
                            ServletUtilities.handleNullString(enc.getIndividual().getDisplayName(
                            request, myShepherd)));
                        rtn.put("individualID",
                            ServletUtilities.handleNullString(
                            enc.getIndividual().getIndividualID()));
                    } else {
                        rtn.put("displayName", null);
                        rtn.put("individualID", null);
                    }
                    rtn.put("dateInMilliseconds", enc.getDateInMilliseconds());
                    rtn.put("mmaCompatible", enc.getMmaCompatible());
                }
            } // end try
            catch (Exception e) {
                e.printStackTrace();
            } finally {
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
            }
        } else {
            rtn.put("error", "invalid Encounter number");
        }
        if (redirUrl != null) {
            response.sendRedirect(redirUrl);
            return;
        }
        PrintWriter out = response.getWriter();
        if (wantJson) {
            response.setContentType("text/json");
            out.println(new Gson().toJson(rtn));
        } else {
            response.setContentType("text/html");
            out.println(ServletUtilities.getHeader(request));
            if (rtn.get("error") != null) out.println(rtn.get("error").toString());
            if (rtn.get("message") != null) out.println(rtn.get("message").toString());
            out.println(ServletUtilities.getFooter(context));
        }
        out.close();
    }

    private void addImages(Encounter enc, HashMap m, Shepherd myShepherd,
        HttpServletRequest request) {
        if (enc == null) return;
        long startTime = System.currentTimeMillis();
        ArrayList mas = new ArrayList();
        for (MediaAsset ma : enc.getMedia()) {
            HashMap i = new HashMap();
            i.put("id", ma.getId());
            URL safe = ma.webURL();
            i.put("url", safe);
            i.put("thumbUrl", safe);
            // keywords are not actually displayed anywhere. uncomment if we ever use them here.
            // ma.put("keywords", ma.getKeywords());
            mas.add(i);
        }
        if (mas.size() > 0) m.put("images", mas);
        System.out.println("     add images for " + mas.size() + " took: " +
            (System.currentTimeMillis() - startTime));
    }
}
