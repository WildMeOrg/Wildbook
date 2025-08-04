package org.ecocean.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
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
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdProperties;
import org.ecocean.Setting;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.Util.MeasurementDesc;
import org.json.JSONArray;
import org.json.JSONObject;

public class SiteSettings extends ApiBase {
    public static String[] VALUES_SEX = { "unknown", "male", "female" };
    public static String[] VALUES_ENCOUNTER_STATES = { "unapproved", "approved", "unidentifiable" };

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        JSONObject settings = new JSONObject();
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("api.SiteSettings.doGet");
        myShepherd.beginDBTransaction();

        try {
            String langCode = ServletUtilities.getLanguageCode(request);
            User currentUser = myShepherd.getUser(request);
            // note: there is a CommonConfiguration property: htmlShortcutIcon=images/favicon.ico?v=2
            settings.put("siteFavicon", "/images/favicon.ico");
            settings.put("siteName", CommonConfiguration.getHTMLTitle(context));
            settings.put("siteDescription", CommonConfiguration.getHTMLDescription(context));
            settings.put("siteKeywords", CommonConfiguration.getHTMLKeywords(context));
            settings.put("siteAuthor", CommonConfiguration.getHTMLAuthor(context));
            settings.put("locationData", LocationID.getLocationIDStructure());

            settings.put("mapCenterLat", CommonConfiguration.getCenterLat(context));
            settings.put("mapCenterLon", CommonConfiguration.getCenterLong(context));
            settings.put("mapZoom", CommonConfiguration.getMapZoom(context));
            settings.put("googleMapsKey", CommonConfiguration.getGoogleMapsKey(context));

            Set<String> sciNames = new HashSet<String>();  // used later
            JSONArray txArr = new JSONArray();
            List<List<String> > nameArray = myShepherd.getAllTaxonomyCommonNames();
            if (Util.collectionSize(nameArray) > 1) {
                int nameArrayLen = nameArray.get(0).size();
                for (int i = 0; i < nameArrayLen; i++) {
                    JSONObject txj = new JSONObject();
                    txj.put("scientificName", nameArray.get(0).get(i));
                    sciNames.add(nameArray.get(0).get(i));
                    if (i < nameArray.get(1).size()) {
                        txj.put("commonName", nameArray.get(1).get(i));
                    } else {
                        txj.put("commonName", "");
                    }
                    txArr.put(txj);
                }
            }
            settings.put("siteTaxonomies", txArr);

            // apparently *nothing* actually uses the common configuration values!
            // settings.put("sex", CommonConfiguration.getIndexedPropertyValues("sex", context));
            settings.put("sex", VALUES_SEX);
            settings.put("lifeStage",
                CommonConfiguration.getIndexedPropertyValues("lifeStage", context));
            settings.put("livingStatus",
                CommonConfiguration.getIndexedPropertyValues("livingStatus", context));
            settings.put("country",
                CommonConfiguration.getIndexedPropertyValues("country", context));
            settings.put("annotationViewpoint", Annotation.getAllValidViewpointsSorted());
            settings.put("patterningCode",
                CommonConfiguration.getIndexedPropertyValues("patterningCode", context));
            settings.put("measurement",
                CommonConfiguration.getIndexedPropertyValues("measurement", context));

            // TODO: there was some discussion in slack about this being derived differently
            // NOTE: historically this list was generated via CommonConfiguration using
            // List<String> states = CommonConfiguration.getIndexedPropertyValues("encounterState",context)

            settings.put("encounterState", VALUES_ENCOUNTER_STATES);

            IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
            Object[] iac = iaConfig.getAllIAClassesWithParts().toArray();
            Arrays.sort(iac);
            settings.put("iaClass", iac);

            JSONObject iaForTx = new JSONObject();
            for (String sn : sciNames) {
                iaForTx.put(sn, iaConfig.getValidIAClassesIgnoreRedirects(new org.ecocean.Taxonomy(sn)));
            }
            settings.put("iaClassesForTaxonomy", iaForTx);

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

            JSONObject system = new JSONObject();
            system.put("wildbookVersion", ContextConfiguration.getVersion());
            settings.put("system", system);

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
            settings.put("maximumMediaSizeMegabytes",
                CommonConfiguration.getMaxMediaSizeInMegabytes(context));

            JSONArray loci = new JSONArray();
            for (String locus : myShepherd.getAllLoci()) {
                loci.put(locus);
            }
            settings.put("loci", loci);

            settings.put("showClassicSubmit",
                Util.booleanNotFalse(CommonConfiguration.getProperty("showClassicSubmit", context))
                );

            settings.put("showClassicEncounters",
                Util.booleanNotFalse(CommonConfiguration.getProperty("showClassicEncounters",
                    context))
                );

            Properties recaptchaProps = ShepherdProperties.getProperties("recaptcha.properties", "",
                context);
            if (recaptchaProps != null) {
                settings.put("reCAPTCHAEnterpriseSiteKey",
                    recaptchaProps.getProperty("enterpriseSiteKey"));
                settings.put("reCAPTCHASiteKey", recaptchaProps.getProperty("siteKey"));
            }
            Properties captchaProps = ShepherdProperties.getProperties("captcha.properties", "",
                context);
            if (captchaProps != null) {
                settings.put("procaptchaSiteKey", captchaProps.getProperty("procaptchaSiteKey"));
            }
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
            settings.put("isHuman", ReCAPTCHA.sessionIsHuman(request));

            // new Setting values, built from valid options
            Map<String, String[]> grp = Setting.getValidGroupsAndIds();
            // note: if group was already set above, it will be overwritten TODO should we check? is this bad? is it on us?
            for (String group : grp.keySet()) {
                JSONObject jg = new JSONObject();
                for (String id : grp.get(group)) {
                    Object val = myShepherd.getSettingValue(group, id);
                    if (val == null) {
                        jg.put(id, JSONObject.NULL);
                    } else {
                        jg.put(id, val);
                    }
                }
                settings.put(group, jg);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            myShepherd.rollbackAndClose();
        }
        response.setStatus(200);
        response.setHeader("Content-Type", "application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(settings.toString());
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        int statusCode = 500;
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("api.SiteSettings.doPost");
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
            String uri = request.getRequestURI();
            String[] args = uri.substring(22).split("/");
            if (args.length < 2) throw new ServletException("Bad path");
            if (!Setting.isValidGroupAndId(args[0], args[1])) {
                statusCode = 400;
                JSONObject error = new JSONObject();
                error.put("code", ApiException.ERROR_RETURN_CODE_INVALID);
                JSONArray errArr = new JSONArray();
                errArr.put(error);
                rtn.put("errors", errArr);
                rtn.put("debug", "invalid group [" + args[0] + "] or id [" + args[1] + "]");
            } else {
                try {
                    Setting setting = myShepherd.getOrCreateSetting(args[0], args[1]);
                    JSONObject payload = ServletUtilities.jsonFromHttpServletRequest(request);
                    setting.setValueFromPayload(payload);
                    myShepherd.storeSetting(setting);
                    statusCode = 200;
                    rtn.put("setting", setting.toJSONObject());
                } catch (Exception ex) {
                    statusCode = 400;
                    JSONObject error = new JSONObject();
                    error.put("code", ApiException.ERROR_RETURN_CODE_INVALID);
                    JSONArray errArr = new JSONArray();
                    errArr.put(error);
                    rtn.put("errors", errArr);
                    rtn.put("debug", ex.toString());
                }
            }
            response.setStatus(statusCode);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write(rtn.toString());
        } catch (ServletException ex) { // should just be thrown, not caught (below)
            throw ex;
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

    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        int statusCode = 500;
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("api.SiteSettings.doDelete");
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
            String uri = request.getRequestURI();
            String[] args = uri.substring(22).split("/");
            if (args.length < 2) throw new ServletException("Bad path");
            if (!Setting.isValidGroupAndId(args[0], args[1])) {
                statusCode = 400;
                JSONObject error = new JSONObject();
                error.put("code", ApiException.ERROR_RETURN_CODE_INVALID);
                JSONArray errArr = new JSONArray();
                errArr.put(error);
                rtn.put("errors", errArr);
                rtn.put("debug", "invalid group [" + args[0] + "] or id [" + args[1] + "]");
            } else {
                Setting setting = myShepherd.getSetting(args[0], args[1]);
                if (setting == null) {
                    statusCode = 404;
                } else {
                    myShepherd.deleteSetting(setting);
                    statusCode = 204;
                }
            }
            response.setStatus(statusCode);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write(rtn.toString());
        } catch (ServletException ex) { // should just be thrown, not caught (below)
            throw ex;
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (statusCode == 204) {
                myShepherd.commitDBTransaction();
            } else {
                myShepherd.rollbackDBTransaction();
            }
            myShepherd.closeDBTransaction();
        }
    }
}
