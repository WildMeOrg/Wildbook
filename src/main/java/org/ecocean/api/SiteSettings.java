package org.ecocean.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.ecocean.Annotation;
import org.ecocean.CommonConfiguration;
import org.ecocean.IAJsonProperties;
import org.ecocean.Keyword;
import org.ecocean.LabeledKeyword;
import org.ecocean.LocationID;
import org.ecocean.Organization;
import org.ecocean.Project;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.Shepherd;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.Util.MeasurementDesc;
import org.json.JSONArray;
import org.json.JSONObject;

public class SiteSettings extends ApiBase {
    public static String[] VALUES_SEX = { "unknown", "male", "female" };
    public static String[] VALUES_ENCOUNTER_STATES = { "unapproved", "approved", "unidentifiable"};

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("api.SiteSettings");
        myShepherd.beginDBTransaction();

        String langCode = ServletUtilities.getLanguageCode(request);
        User currentUser = myShepherd.getUser(request);
        JSONObject settings = new JSONObject();
        // note: there is a CommonConfiguration property: htmlShortcutIcon=images/favicon.ico?v=2
        settings.put("siteFavicon", "/images/favicon.ico");
        settings.put("siteName", CommonConfiguration.getHTMLTitle(context));
        settings.put("locationData", LocationID.getLocationIDStructure());

        JSONArray txArr = new JSONArray();
        for (String sciName : myShepherd.getAllTaxonomyNames()) {
            JSONObject txj = new JSONObject();
            txj.put("scientificName", sciName.replaceAll("_", " "));
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
        settings.put("patterningCode",
            CommonConfiguration.getIndexedPropertyValues("patterningCode", context));
        settings.put("measurement",
            CommonConfiguration.getIndexedPropertyValues("measurement", context));

        // TODO: there was some discussion in slack about this being derived differently
        // NOTE: historically this list was generated via CommonConfiguration using 
        //       List<String> states = CommonConfiguration.getIndexedPropertyValues("encounterState",context)
        settings.put("encounterState", VALUES_ENCOUNTER_STATES);

        IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
        Object[] iac = iaConfig.getAllIAClasses().toArray();
        Arrays.sort(iac);
        settings.put("iaClass", iac);

        List<String> behavs = myShepherd.getAllBehaviors();
        behavs.remove(null);
        Object[] barr = behavs.toArray();
        Arrays.sort(barr);
        settings.put("behavior", behavs);

        List<String> kws = new ArrayList<String>();
        // this seems like less desirable method: getAllKeywordsNoLabeledKeywords()
        for (Keyword kw : myShepherd.getSortedKeywordList()) {
            if (!kws.contains(kw.getDisplayName())) kws.add(kw.getDisplayName());
        }
        Object[] sortArray = kws.toArray();
        Arrays.sort(sortArray);
        settings.put("keyword", sortArray);

        JSONObject lkeyword = new JSONObject();
        for (LabeledKeyword lkw : myShepherd.getAllLabeledKeywords()) {
            if (!lkeyword.has(lkw.getLabel())) lkeyword.put(lkw.getLabel(), new JSONArray());
            lkeyword.getJSONArray(lkw.getLabel()).put(lkw.getValue());
        }
        settings.put("labeledKeyword", lkeyword);

        JSONObject orgs = new JSONObject();
        for (Organization org : myShepherd.getAllOrganizations()) {
            orgs.put(org.getId(), org.getName());
        }
        settings.put("organizations", orgs);

        sortArray = myShepherd.getAllSocialUnitNames().toArray();
        Arrays.sort(sortArray);
        settings.put("socialUnitName", sortArray);
        settings.put("socialUnitRole", myShepherd.getAllMembershipRoles());
        settings.put("relationshipRole",
            CommonConfiguration.getIndexedPropertyValues("relationshipRole", context));

        boolean enabled = CommonConfiguration.showMetalTags(context);
        settings.put("metalTagsEnabled", enabled);
        if (enabled)
            settings.put("metalTagLocation",
                CommonConfiguration.getIndexedPropertyValues("metalTagLocation", context));
        enabled = CommonConfiguration.showSatelliteTag(context);
        settings.put("satelliteTagEnabled", enabled);
        if (enabled)
            settings.put("satelliteTagName",
                CommonConfiguration.getIndexedPropertyValues("satelliteTagName", context));
        settings.put("acousticTagEnabled", CommonConfiguration.showAcousticTag(context));

        List<String> ved = myShepherd.getAllVerbatimEventDates();
        ved.remove(null); // sloppy
        settings.put("verbatimEventDate", ved);

        settings.put("haplotype", myShepherd.getAllHaplotypes());
        settings.put("geneticSex", myShepherd.getAllGeneticSexes());
        JSONObject biomeas = new JSONObject();
        for (MeasurementDesc mdesc : Util.findBiologicalMeasurementDescs(langCode, context)) {
            biomeas.put(mdesc.getType(), mdesc.getLabel());
        }
        settings.put("bioMeasurement", biomeas);
        settings.put("showMeasurements", CommonConfiguration.showMeasurements(context));

        JSONArray loci = new JSONArray();
        for (String locus : myShepherd.getAllLoci()) {
            loci.put(locus);
        }
        settings.put("loci", loci);
        // these are sensitive settings, that anon users should not get (e.g. user lists)
        if (currentUser != null) {
            JSONArray jarr = new JSONArray();
            for (User user : myShepherd.getAllUsers("fullName")) {
                JSONObject ju = new JSONObject();
                ju.put("id", user.getId());
                ju.put("username", user.getUsername());
                // ju.put("fullName", user.getDisplayName()); // hidden for security reasons?
                jarr.put(ju);
            }
            settings.put("users", jarr);

            JSONObject jp = new JSONObject();
            ArrayList<Project> projs = myShepherd.getProjectsForUser(currentUser);
            if (projs != null) {
                for (Project proj : projs) {
                    jp.put(proj.getId(), proj.getResearchProjectName());
                }
            }
            settings.put("projectsForUser", jp);
        }
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        response.setStatus(200);
        response.setHeader("Content-Type", "application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(settings.toString());
    }
}
