package org.ecocean.api;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.ecocean.CommonConfiguration;
import org.ecocean.LocationID;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;

public class SiteSettings extends ApiBase {
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

        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        response.setStatus(200);
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(settings.toString());
    }
}
