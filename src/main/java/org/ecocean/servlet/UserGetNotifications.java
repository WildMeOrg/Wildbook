package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.Shepherd;
import org.ecocean.scheduled.WildbookScheduledIndividualMerge;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;


public class UserGetNotifications extends HttpServlet {
    private static final long serialVersionUID = 1L;
    
    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
    }
    
    @Override
    public void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        doGet(request,response);
    }

    @Override
    public void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        
        //trying to make this easily extendable to other kinds of notification

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        
        JSONObject res = new JSONObject();

        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("UserGetNotifications.java");
        myShepherd.beginDBTransaction();

        try {
            res.put("success", "false");
            JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);

            String username = j.optString("username", null);

            JSONArray notificationArr = new JSONArray();

            if (username!=null&&!"".equals(username)) {

                notificationArr = addAllScheduledIndividualMergeNotifications(notificationArr, myShepherd, username);

            }

            res.put("notifications", notificationArr);

            PrintWriter out = response.getWriter();
            out.println(res);
            out.close();
            myShepherd.closeDBTransaction();
            res.put("success", "true");
        } catch (NullPointerException npe) {
            npe.printStackTrace();
            addErrorMessage(res, "NullPointerException npe");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            myShepherd.rollbackAndClose();
        } catch (JSONException je) {
            je.printStackTrace();
            addErrorMessage(res, "JSONException je");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            myShepherd.rollbackAndClose();
        } catch (Exception e) {
            e.printStackTrace();
            addErrorMessage(res, "Exception e");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            myShepherd.rollbackAndClose();
        }

    }

    private void addErrorMessage(JSONObject res, String error) {
        res.put("error", error);
    }

    private JSONArray addAllScheduledIndividualMergeNotifications(JSONArray notificationArr, Shepherd myShepherd, String username) {
        ArrayList<WildbookScheduledIndividualMerge> pendingMerges = myShepherd.getAllIncompleteWildbookScheduledIndividualMerges();
        System.out.println("all incomplete merges: "+pendingMerges.size());
        for (WildbookScheduledIndividualMerge pendingMerge : pendingMerges) {
            if (pendingMerge.isUserParticipent(username)) {
                if (pendingMerge.isDenied()) {
                    notificationArr.put(individualMergeDeniedNotification(pendingMerge));
                } else if (!pendingMerge.ignoredByUser(username)) {
                    notificationArr.put(individualMergePendingNotification(pendingMerge));
                }
            }
        }
        ArrayList<WildbookScheduledIndividualMerge> completeMergesOwnedByUser = myShepherd.getAllCompleteWildbookScheduledIndividualMergesForUsername(username);
        System.out.println("all complete merges owned by user: "+completeMergesOwnedByUser.size());
        for (WildbookScheduledIndividualMerge completeMerge : completeMergesOwnedByUser) {
            if (!completeMerge.ignoredByUser(username)) {
                notificationArr.put(individualMergeCompleteNotification(completeMerge));
            }

        }
        return notificationArr;
    }

    private JSONObject individualMergeDeniedNotification(WildbookScheduledIndividualMerge merge) {
        //TODO
        return null;
    }

    private JSONObject individualMergePendingNotification(WildbookScheduledIndividualMerge merge) {
        //TODO
        return null;
    }

    private JSONObject individualMergeCompleteNotification(WildbookScheduledIndividualMerge merge) {
        //TODO
        return null;
    }

}