package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.ecocean.scheduled.ScheduledIndividualMerge;
import org.ecocean.Shepherd;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class UserGetNotifications extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override public void init(final ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    @Override public void doPost(final HttpServletRequest request,
        final HttpServletResponse response)
    throws ServletException, IOException {
        doGet(request, response);
    }

    @Override public void doGet(final HttpServletRequest request,
        final HttpServletResponse response)
    throws ServletException, IOException {
        // trying to make this easily extendable to other kinds of notification

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");

        JSONObject res = new JSONObject();
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("UserGetNotifications.java");
        myShepherd.beginDBTransaction();

        System.out.println("==> In UserGetNotifications servlet!");

        try {
            res.put("success", "false");
            JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);
            String username = j.optString("username", null);
            if (username == null || myShepherd.getUser(username) == null) {
                username = request.getUserPrincipal().getName();
            }
            System.out.println("Finding notifications for username = " + username);

            JSONArray notificationArr = new JSONArray();
            if (username != null && !"".equals(username)) {
                notificationArr = addAllScheduledIndividualMergeNotifications(notificationArr,
                    myShepherd, username, request);
            }
            res.put("notifications", notificationArr);

            PrintWriter out = response.getWriter();
            res.put("success", "true");
            out.println(res);
            out.close();
            myShepherd.closeDBTransaction();
            response.setStatus(HttpServletResponse.SC_OK);
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

    private JSONArray addAllScheduledIndividualMergeNotifications(JSONArray notificationArr,
        Shepherd myShepherd, String username, HttpServletRequest request) {
        ArrayList<ScheduledIndividualMerge> pendingMerges =
            myShepherd.getAllIncompleteScheduledIndividualMerges();

        System.out.println("all incomplete merges: " + pendingMerges.size());
        for (ScheduledIndividualMerge pendingMerge : pendingMerges) {
            if (pendingMerge.isUserParticipent(username)) {
                System.out.println("Is pending merge ignored by user? : " +
                    pendingMerge.ignoredByUser(username));
                if (pendingMerge.isDenied() && !pendingMerge.ignoredByUser(username)) {
                    notificationArr.put(individualMergeDeniedNotification(pendingMerge, username,
                        myShepherd, request));
                } else if (!pendingMerge.ignoredByUser(username)) {
                    notificationArr.put(individualMergePendingNotification(pendingMerge, username,
                        myShepherd, request));
                }
            }
        }
        ArrayList<ScheduledIndividualMerge> completeMergesOwnedByUser =
            myShepherd.getAllCompleteScheduledIndividualMergesForUsername(username);
        System.out.println("all complete merges owned by user: " +
            completeMergesOwnedByUser.size());
        for (ScheduledIndividualMerge completeMerge : completeMergesOwnedByUser) {
            System.out.println("Is merge ignored by user? : " +
                completeMerge.ignoredByUser(username));
            if (!completeMerge.ignoredByUser(username)) {
                notificationArr.put(individualMergeCompleteNotification(completeMerge, username,
                    myShepherd, request));
            }
        }
        return notificationArr;
    }

    private JSONObject individualMergeDeniedNotification(ScheduledIndividualMerge merge,
        String username, Shepherd myShepherd, HttpServletRequest request) {
        JSONObject note = getBasicMergeNotificationJSON(merge, username, myShepherd, request);

        note.put("notificationType", "mergeDenied");
        note.put("deniedBy", merge.getUsernameThatDeniedMerge());
        note.put("secondaryIndividualId", merge.getSecondaryIndividual().getId());
        note.put("secondaryIndividualName",
            merge.getSecondaryIndividual().getDisplayName(request, myShepherd));
        return note;
    }

    private JSONObject individualMergePendingNotification(ScheduledIndividualMerge merge,
        String username, Shepherd myShepherd, HttpServletRequest request) {
        JSONObject note = getBasicMergeNotificationJSON(merge, username, myShepherd, request);

        note.put("notificationType", "mergePending");
        note.put("secondaryIndividualId", merge.getSecondaryIndividual().getId());
        note.put("secondaryIndividualName",
            merge.getSecondaryIndividual().getDisplayName(request, myShepherd));
        return note;
    }

    private JSONObject individualMergeCompleteNotification(ScheduledIndividualMerge merge,
        String username, Shepherd myShepherd, HttpServletRequest request) {
        JSONObject note = getBasicMergeNotificationJSON(merge, username, myShepherd, request);

        note.put("notificationType", "mergeComplete");
        return note;
    }

    private JSONObject getBasicMergeNotificationJSON(ScheduledIndividualMerge merge,
        String username, Shepherd myShepherd, HttpServletRequest request) {
        // stuff commmon to all merge notifications
        JSONObject note = new JSONObject();

        note.put("taskId", merge.getId());
        note.put("initiator", merge.getInitiatorName());
        if (merge.getInitiatorName().equals(username)) {
            note.put("ownedByMe", "true");
        } else {
            note.put("ownedByMe", "false");
        }
        note.put("primaryIndividualId", merge.getPrimaryIndividual().getId());
        note.put("primaryIndividualName",
            merge.getPrimaryIndividual().getDisplayName(request, myShepherd));
        note.put("mergeExecutionDate", merge.getTaskScheduledExecutionDateString());

        return note;
    }
}
