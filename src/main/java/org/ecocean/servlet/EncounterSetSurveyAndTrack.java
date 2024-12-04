package org.ecocean.servlet;

import org.ecocean.Encounter;
import org.ecocean.media.MediaAsset;
import org.ecocean.movement.SurveyTrack;
import org.ecocean.Occurrence;
import org.ecocean.Shepherd;
import org.ecocean.Survey;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

public class EncounterSetSurveyAndTrack extends HttpServlet {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

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
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("EncounterSetSurveyAndTrack.class");
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        String encID = null;
        String surveyID = null;
        String surveyTrackID = null;

        try {
            if (request.getParameter("encID") != null) {
                encID = request.getParameter("encID");
            }
            if (request.getParameter("surveyID") != null) {
                surveyID = request.getParameter("surveyID");
            }
            if (request.getParameter("surveyTrackID") != null) {
                surveyTrackID = request.getParameter("surveyTrackID");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(
                "Error grabbing parameters for change in Survey or ID for this Encounter!");
        }
        System.out.println("Hit survey association servlet! EncID: " + encID + " surveyID: " +
            surveyID + " surveyTrackID: " + surveyTrackID);

        myShepherd.beginDBTransaction();
        if (encID != null && myShepherd.isEncounter(encID)) {
            Encounter thisEnc = myShepherd.getEncounter(encID);
            Survey sv = null;
            if (surveyID != null && myShepherd.isSurvey(surveyID)) {
                sv = myShepherd.getSurvey(surveyID);
                try {
                    thisEnc.setSurveyID(surveyID);
                } catch (Exception le) {
                    locked = true;
                    myShepherd.rollbackDBTransaction();
                    myShepherd.closeDBTransaction();
                    System.out.println("Failed to add survey to encounter.");
                }
                if (thisEnc.getMedia() != null) {
                    ArrayList<MediaAsset> assets = thisEnc.getMedia();
                    for (MediaAsset asset : assets) {
                        asset.setCorrespondingSurveyID(surveyID);
                        if (surveyTrackID != null) {
                            asset.setCorrespondingSurveyTrackID(surveyTrackID);
                        }
                    }
                }
            }
            if (surveyTrackID != null && myShepherd.isSurveyTrack(surveyTrackID)) {
                try {
                    SurveyTrack st = myShepherd.getSurveyTrack(surveyTrackID);
                    Occurrence occ = myShepherd.getOccurrence(thisEnc.getOccurrenceID());
                    ArrayList<Occurrence> occs = st.getAllOccurrences();
                    if (!occs.contains(occ)) {
                        st.addOccurrence(occ);
                    }
                    thisEnc.setSurveyTrackID(surveyTrackID);
                    System.out.println("Do we already have this occ on the survey track? " +
                        occs.contains(occ));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (!locked) {
                myShepherd.commitDBTransaction();
                myShepherd.closeDBTransaction();
                out.println("The Survey/Track ID's for encounter are now Survey: " + surveyID +
                    " and Track: " + surveyTrackID);
                response.setStatus(HttpServletResponse.SC_OK);
            }
        } else {
            myShepherd.rollbackDBTransaction();
            out.println("<strong>Error:</strong> The survey specified does not exist.");
            System.out.println("Enc ID : " + encID + " SurveyID : " + surveyID +
                " SurveyTrackID : " + surveyTrackID);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}
