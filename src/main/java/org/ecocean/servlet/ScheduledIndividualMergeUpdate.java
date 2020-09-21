package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.Shepherd;
import org.ecocean.scheduled.ScheduledIndividualMerge;
import org.json.JSONObject;
import org.json.JSONException;


public class ScheduledIndividualMergeUpdate extends HttpServlet {
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

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        
        JSONObject res = new JSONObject();

        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("ScheduledIndividualMergeUpdate.java");
        myShepherd.beginDBTransaction();

        System.out.println("==> In ScheduledIndividualMergeUpdate servlet!");

        try {
            res.put("success", "false");

            JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);

            String mergeId = j.optString("mergeId", null);
            String action = j.optString("action", null);
            String username = j.optString("username", null);
            if (username==null||myShepherd.getUser(username)==null) {
                username = request.getUserPrincipal().getName();
            }

            if (username!=null&&!"".equals(username)&&action!=null&&!"".equals(action)&&mergeId!=null&&!"".equals(mergeId)) {
                ScheduledIndividualMerge merge = (ScheduledIndividualMerge) myShepherd.getWildbookScheduledTask(mergeId);
                System.out.println("Have all required info...");
                System.out.println("Merge real? "+merge.getId());
                if (merge!=null&&merge.isUserParticipent(username)) {
                    System.out.println("user is participant? "+merge.isUserParticipent(username));
                    if ("deny".equals(action)) {
                        merge.setTaskDeniedStateForUser(username, true);
                        myShepherd.updateDBTransaction();
                        System.out.println("Set ScheduledIndividual merge "+mergeId+" to DENIED for user "+username+".");
                        res.put("success", "true");
                    } else if ("ignore".equals(action)) {
                        merge.setTaskIgnoredStateForUser(username, true);
                        myShepherd.updateDBTransaction();
                        System.out.println("Set ScheduledIndividual merge "+mergeId+" to IGNORE for user "+username+".");
                        res.put("success", "true");
                    }   
                }
            } else {
                String err = "You must have a user, mergeId and action defined to modify a ScheduledIndividualMerge.";
                System.out.println(err);
                addErrorMessage(res, err);
            }

            PrintWriter out = response.getWriter();
            out.println(res);
            out.close();
            myShepherd.closeDBTransaction();
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

}