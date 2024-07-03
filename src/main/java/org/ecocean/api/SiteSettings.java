package org.ecocean.api;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.ecocean.Annotation;
import org.ecocean.CommonConfiguration;
import org.ecocean.IAJsonProperties;
import org.ecocean.LocationID;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;

public class SiteSettings extends ApiBase {
    public static String[] VALUES_SEX = { "unknown", "male", "female" };

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("api.SiteSettings");
        myShepherd.beginDBTransaction();

        JSONObject settings = new JSONObject();
        // note: there is a CommonConfiguration property: htmlShortcutIcon=images/favicon.ico?v=2
        settings.put("siteFavicon", "/images/favicon.ico");
        settings.put("siteName", CommonConfiguration.getHTMLTitle(context));
        settings.put("locationData", LocationID.getLocationIDStructure());

        JSONArray txArr = new JSONArray();
        for (String sciName : myShepherd.getAllTaxonomyNames()) {
            JSONObject txj = new JSONObject();
            txj.put("scientificName", sciName);
            txArr.put(txj);
        }
        settings.put("siteTaxonomies", txArr);

        // apparently *nothing* actually uses the common configuration values!
        // settings.put("sex", CommonConfiguration.getIndexedPropertyValues("sex", context));
        settings.put("sex", VALUES_SEX);
        settings.put("lifeStage",
            CommonConfiguration.getIndexedPropertyValues("lifeStage", context));
        settings.put("livingStatus",
            CommonConfiguration.getIndexedPropertyValues("livingStatus", context));
        settings.put("country", CommonConfiguration.getIndexedPropertyValues("country", context));
        settings.put("annotationViewpoint", Annotation.getAllValidViewpointsSorted());

        IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
        Object[] iac = iaConfig.getAllIAClasses().toArray();
        Arrays.sort(iac);
        settings.put("iaClass", iac);

        List<String> behavs = myShepherd.getAllBehaviors();
        behavs.remove(null);
        Object[] barr = behavs.toArray();
        Arrays.sort(barr);
        settings.put("behavior", behavs);

        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        response.setStatus(200);
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(settings.toString());
    }
}
