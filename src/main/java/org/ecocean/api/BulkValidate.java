package org.ecocean.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.ecocean.Annotation;
import org.ecocean.CommonConfiguration;
import org.ecocean.ContextConfiguration;
import org.ecocean.IAJsonProperties;
import org.ecocean.Keyword;
import org.ecocean.LabeledKeyword;
import org.ecocean.LocationID;
import org.ecocean.Organization;
import org.ecocean.Project;
import org.ecocean.servlet.ReCAPTCHA;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.Setting;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdProperties;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.Util.MeasurementDesc;
import org.json.JSONArray;
import org.json.JSONObject;

public class BulkValidate extends ApiBase {

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        int statusCode = 500;
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("api.Bulk.doPost");
        myShepherd.beginDBTransaction();
        try {
            User currentUser = myShepherd.getUser(request);
            if ((currentUser == null) || !currentUser.isAdmin(myShepherd)) {
                response.setStatus(401);
                response.setHeader("Content-Type", "application/json");
                response.getWriter().write("{\"success\": false}");
                return;
            }

            JSONObject rtn = new JSONObject();
/*
            String uri = request.getRequestURI();
            String[] args = uri.substring(22).split("/");
            if (args.length < 2) throw new ServletException("Bad path");

*/

            response.setStatus(statusCode);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write(rtn.toString());

/*
        } catch (ServletException ex) {  // should just be thrown, not caught (below)
            throw ex;
*/
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (statusCode == 200) {
                myShepherd.commitDBTransaction();
            } else {
                myShepherd.rollbackDBTransaction();
            }
            myShepherd.closeDBTransaction();
        }
    }

}
