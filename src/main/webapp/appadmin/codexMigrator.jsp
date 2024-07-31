<%@ page contentType="text/html; charset=utf-8" language="java"
import="org.ecocean.*,
org.ecocean.tag.MetalTag,
org.ecocean.social.*,
org.ecocean.servlet.ServletUtilities,
org.ecocean.social.Relationship,
org.joda.time.DateTime,
java.io.IOException,
javax.servlet.jsp.JspWriter,
javax.servlet.http.HttpServletRequest,
java.nio.file.Files,
java.sql.Timestamp,
java.sql.*,
java.io.File,
java.util.ArrayList,
java.util.List,
java.util.Map,
java.util.HashMap,
java.util.Collection,
java.util.Set,
java.util.HashSet,
java.util.Properties,
java.util.TimeZone,
javax.jdo.Query,
org.json.JSONObject,
org.json.JSONArray,
org.ecocean.media.*
    "
%>

<%!

private static String TMP_DIR = "/tmp/migrate";

private static Encounter encounterFromPending(JSONObject edata) {
    Encounter enc = null;
    return enc;
}

private static boolean stringEmpty(String str) {
    return ((str == null) || str.equals(""));
}

private static void cfOccurrence(Shepherd myShepherd, Occurrence occ, Map<String,String> cfMap) {
    for (String key : cfMap.keySet()) {
        String value = cfMap.get(key);
        if (stringEmpty(value)) continue;
        String label = key.replaceAll(" ", "_");
System.out.println(">>>>>> " + label + " => " + value + " on " + occ);
        occ.addComments("<p><b>" + key + ":</b> " + value + "</p>");
        if (occ.getNumberEncounters() < 1) continue;
        LabeledKeyword kw = myShepherd.getOrCreateLabeledKeyword(label, value, false);
        for (Encounter enc : occ.getEncounters()) {
            for (MediaAsset ma : enc.getMedia()) {
                ma.addKeyword(kw);
System.out.println(">>>>>> ++++++ " + kw + " on " + ma);
            }
        }
    }
}

private static String cleanJsonString(String json) {
    if (json == null) return null;
    json = json.replaceAll("\\\\", "");
    json = json.substring(1, json.length() - 1);
    return json;
}

private static JSONObject cleanJSONObject(String data) {
    String clean = cleanJsonString(data);
    if (clean == null) return null;
    try {
        return new JSONObject(clean);
    } catch (Exception ex) {}
    return null;
}

private static JSONArray cleanJSONArray(String data) {
    String clean = cleanJsonString(data);
    if (clean == null) return null;
    try {
        return new JSONArray(clean);
    } catch (Exception ex) {}
    return null;
}

private static Object siteSetting(String key, Connection conn) throws SQLException, IOException {
    Statement st = conn.createStatement();
    ResultSet res = st.executeQuery("SELECT * FROM site_setting WHERE key='" + key + "'");
    if (!res.next()) return null;
    String data = cleanJsonString(res.getString("data"));
    if (data == null) return null;
    try {
        return new JSONObject(data);
    } catch (Exception ex) {}
    try {
        return new JSONArray(data);
    } catch (Exception ex) {}
    return data;
}

private static JSONObject cfDefinitions(String cls, Connection conn) throws SQLException, IOException {
    Statement st = conn.createStatement();
    ResultSet res = st.executeQuery("SELECT data FROM site_setting WHERE key='site.custom.customFields." + cls + "'");
    if (!res.next()) return null;
    JSONObject j = cleanJSONObject(res.getString("data"));
    if (j == null) return null;
    JSONArray darr = j.optJSONArray("definitions");
    if (darr == null) return null;
    JSONObject rtn = new JSONObject();
    for (int i = 0 ; i < darr.length() ; i++) {
        JSONObject defn = darr.optJSONObject(i);
        if (defn == null) continue;
        String id = defn.optString("id", null);
        if (id == null) continue;
        rtn.put(id, defn);
    }
    return rtn;
}

private static String cfString(JSONObject cfData, String key) {
    if (cfData == null) return null;
    String value = cfData.optString(key, null);
    if ("".equals(value)) return null;
    return value;
}

private static Integer cfInteger(JSONObject cfData, String key) {
    if (cfData == null) return null;
    if (!cfData.has(key)) return null;
    if (cfData.isNull(key)) return null;
    try {
        return cfData.getInt(key);
    } catch (Exception ex) {}
    return null;
}

private static Map<String,String> relationshipMeta(Connection conn) throws SQLException, IOException {
    Object ss = siteSetting("relationship_type_roles", conn);
    Map<String,String> rtn = new HashMap<String,String>();
    if (ss == null) return rtn;
    JSONObject rel = (JSONObject)ss;
    for (String key : (Set<String>)rel.keySet()) {
        JSONObject data = rel.optJSONObject(key);
        if (data == null) continue;
        String label = data.optString("label", null);
        if (label != null) rtn.put(key, label);
        JSONArray roles = data.optJSONArray("roles");
        if (roles == null) continue;
        for (int i = 0 ; i < roles.length() ; i++) {
            JSONObject role = roles.optJSONObject(i);
            if (role == null) continue;
            String rid = role.optString("guid", null);
            String rlabel = role.optString("label", null);
            if ((rid != null) && (rlabel != null)) rtn.put(rid, rlabel);
        }
    }
    return rtn;
}

private static Map<String,String> socialGroupMeta(Connection conn) throws SQLException, IOException {
    Object ss = siteSetting("social_group_roles", conn);
    Map<String,String> rtn = new HashMap<String,String>();
    if (ss == null) return rtn;
    JSONArray sgArr = (JSONArray)ss;
    for (int i = 0 ; i < sgArr.length() ; i++) {
        JSONObject sg = sgArr.optJSONObject(i);
        if (sg == null) continue;
        String guid = sg.optString("guid", null);
        String label = sg.optString("label", null);
        if ((guid == null) || (label == null)) continue;
        rtn.put(guid, label);
    }
    return rtn;
}

private static Map<String,String> taxonomyMap(Connection conn) throws SQLException, IOException {
    Object ss = siteSetting("site.species", conn);
    Map<String,String> rtn = new HashMap<String,String>();
    if (ss == null) return rtn;
    JSONArray arr = (JSONArray)ss;
    for (int i = 0 ; i < arr.length() ; i++) {
        JSONObject tx = arr.getJSONObject(i);
        rtn.put(tx.optString("id", "_FAIL"), tx.optString("scientificName", "_FAIL"));
    }
    return rtn;
}

private static JSONObject locationJson(Connection conn) throws SQLException, IOException {
    Object ss = siteSetting("site.custom.regions", conn);
    if (ss == null) return null;
    try {
        return (JSONObject)ss;
    } catch (Exception ex) {}
    return null;
}

private static Map<String,String> locationMap(Connection conn) throws SQLException, IOException {
    Map<String,String> map = new HashMap<String,String>();
    return locationMap(locationJson(conn), map);
}

private static Map<String,String> locationMap(JSONObject data, Map<String,String> map) {
    if (data == null) return map;
    String id = data.optString("id", null);
    String name = data.optString("name", id);
    if (id != null) map.put(id, name);
    JSONArray sub = data.optJSONArray("locationID");
    if (sub != null) for (int i = 0 ; i < sub.length() ; i++) {
        JSONObject subObj = sub.optJSONObject(i);
        if (subObj == null) continue;
        locationMap(subObj, map);
    }
    return map;
}


private static int batchMax() {
    return 00;
}

private static void migrateUsers(JspWriter out, Shepherd myShepherd, Connection conn) throws SQLException, IOException {
    out.println("<h2>Users</h2><ol>");
    Statement st = conn.createStatement();
    ResultSet res = st.executeQuery("SELECT * FROM \"user\"");
    int ct = 0;
    while (res.next()) {
        String guid = res.getString("guid");
        int staticRoles = res.getInt("static_roles");
        out.println("<li>" + guid + ": ");
        Set<String> roles = new HashSet<String>();
        if ((staticRoles & 0x04000) > 0) {
            roles.add("admin");
            roles.add("orgAdmin");
            roles.add("researcher");
            roles.add("rest");
            roles.add("machinelearning");
        }
        if ((staticRoles & 0x80000) > 0) roles.add("orgAdmin");
        //if ((staticRoles & 0x10000) > 0)  exporter == "N/A"
        if ((staticRoles & 0x20000) > 0) roles.add("researcher");
        if ((staticRoles & 0x40000) > 0) {
            roles.add("rest");
            roles.add("machinelearning");
        }
        User user = myShepherd.getUserByUUID(guid);
        if (user != null) {
            out.println("<i>user exists; skipping</i>");
        } else {
            user = new User(guid);
            String username = res.getString("email");
            user.setUsername(username);
            user.setAffiliation(res.getString("affiliation"));
            // location seems to often have value in codex
            user.setUserURL(res.getString("website"));
            user.setEmailAddress(res.getString("email"));
            user.setFullName(res.getString("full_name"));
            myShepherd.getPM().makePersistent(user);

            for (String roleName : roles) {
                Role role = new Role();
                role.setRolename(roleName);
                role.setUsername(username);
                role.setContext("context0");
                myShepherd.getPM().makePersistent(role);
            }

            // TODO Organizations

            String msg = "created user [" + ct + "] [" + String.join(",", roles) + "] " + user;
            out.println("<b>" + msg + "</b>");
            System.out.println(msg);
        }
        out.println("</li>");
        ct++;
    }
    out.println("</ol>");
}


private static void migrateMediaAssets(JspWriter out, Shepherd myShepherd, Connection conn, HttpServletRequest request, File assetGroupDir) throws SQLException, IOException {
    out.println("<h2>MediaAssets</h2><ol>");
    Statement st = conn.createStatement();
    ResultSet res = st.executeQuery("SELECT * FROM asset ORDER BY git_store_guid, guid");
    AssetStore targetStore = AssetStore.getDefault(myShepherd);
    List<Integer> maIds = new ArrayList<Integer>();
    int ct = 0;

    while (res.next()) {
        ct++;
        if (ct > batchMax()) break;
        String guid = res.getString("guid");
        out.println("<li>" + guid + ": ");
        MediaAsset ma = MediaAssetFactory.loadByUuid(guid, myShepherd);
        if (ma != null) {
            ct--;
            out.println("<i>asset exists; skipping</i>");
        } else {
            String ext = "unknown";
            String mimeType = res.getString("mime_type");
            if ((mimeType != null) && mimeType.contains("/")) ext = mimeType.split("\\/")[1];
            if ("jpeg".equals(ext)) ext = "jpg";
            String assetGroupGuid = res.getString("git_store_guid");
            File sourceFile = new File(assetGroupDir, assetGroupGuid + "/_assets/" + guid + "." + ext);
            String userFilename = res.getString("path");
            if (stringEmpty(userFilename)) userFilename = guid + "." + ext;
            //out.println(sourceFile.toString());
            if (!sourceFile.exists()) {
                out.println("<b>" + sourceFile + " does not exist</b>");
                if (ext.equals("unknown")) continue;
                break;
            }
            File tmpFile = new File(TMP_DIR, guid + "." + ext);
            Files.copy(sourceFile.toPath(), tmpFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            String grouping = Util.hashDirectories(assetGroupGuid, File.separator);
            JSONObject params = targetStore.createParameters(tmpFile, grouping);
            params.put("userFilename", userFilename);
            params.put("_codexMigration", System.currentTimeMillis());
            ma = targetStore.create(params);
            try {
                ma.copyIn(sourceFile);
            } catch (Exception ex) {
                out.println("<b>failed to copyIn " + sourceFile + " => " + ex.toString() + "</b>");
                break;
            }
            ma.setUUID(guid);
            // TODO revision from codex?
            ma.setAcmId(res.getString("content_guid"));
            ma.updateMetadata();
            ma.addLabel("_original");
            ma.setAccessControl(request);
            MediaAssetFactory.save(ma, myShepherd);
            maIds.add(ma.getId());
            String msg = "created [" + ct + "] " + ma;
            out.println("<b>" + msg + "</b>");
            System.out.println(msg);
        }
        out.println("</li>");
    }
    myShepherd.commitDBTransaction(); // necessary for backgrounding
    myShepherd.beginDBTransaction();
    MediaAsset.updateStandardChildrenBackground(ServletUtilities.getContext(request), maIds);
    out.println("</ol>");
}


private static void migrateAnnotations(JspWriter out, Shepherd myShepherd, Connection conn) throws SQLException, IOException {
    out.println("<h2>Annotations</h2><ol>");
    Statement st = conn.createStatement();
    ResultSet res = st.executeQuery("SELECT * FROM annotation ORDER BY guid");
    FeatureType.initAll(myShepherd);
    int ct = 0;

    while (res.next()) {
        ct++;
        if (ct > batchMax()) break;
        String guid = res.getString("guid");
        Annotation ann = myShepherd.getAnnotation(guid);
        if (ann != null) {
            ct--;
            out.println("<li>" + guid + ": <i>annotation exists; skipping</i>");
        } else {
            String maId = res.getString("asset_guid");
            MediaAsset ma = MediaAssetFactory.loadByUuid(maId, myShepherd);
            if (ma == null) {
                //out.println("<b>" + guid + ": failed due to missing MediaAsset id=" + maId + "</b>");
                //System.out.println(guid + " failed due to missing MediaAsset id=" + maId);
                ct--;
                continue;
            }
            out.println("<li>" + guid + ": ");
            JSONObject bounds = cleanJSONObject(res.getString("bounds"));
            if (bounds == null) bounds = new JSONObject();
            Feature feat = null;
            JSONArray rect = bounds.optJSONArray("rect");
            if (rect != null) {
                JSONObject params = new JSONObject();
                params.put("theta", bounds.optDouble("theta", 0d));
                params.put("x", rect.optInt(0, 0));
                params.put("y", rect.optInt(1, 0));
                params.put("width", rect.optInt(2, 0));
                params.put("height", rect.optInt(3, 0));
                feat = new Feature("org.ecocean.boundingBox", params);
            } else {
                System.out.println("%%% failed on bbox for bounds=" + bounds);
                feat = new Feature();
            }
            ma.addFeature(feat);
            // 99.9% sure species doesnt matter any more, so setting as null
            ann = new Annotation(null, feat, res.getString("ia_class"));
            ann.setId(guid);
            // TODO id status?
            ann.setAcmId(res.getString("content_guid"));
            ann.setViewpoint(res.getString("viewpoint"));
            ann.setMatchAgainst(true);
            myShepherd.getPM().makePersistent(ann);

            String msg = "created annot [" + ct + "] " + ann;
            out.println("<b>" + msg + "</b>");
            System.out.println(msg);
        }
        out.println("</li>");
    }
    out.println("</ol>");
}

private static void migrateEncounters(JspWriter out, Shepherd myShepherd, Connection conn) throws SQLException, IOException {
    out.println("<h2>Encounters</h2><ol>");
    Map<String,String> txmap = taxonomyMap(conn);
    Statement st = conn.createStatement();
    Statement st2 = conn.createStatement();
    ResultSet res = st.executeQuery("SELECT encounter.*, complex_date_time.datetime, complex_date_time.timezone, complex_date_time.specificity FROM encounter JOIN complex_date_time ON (time_guid = complex_date_time.guid) ORDER BY guid");
    int ct = 0;

    while (res.next()) {
        ct++;
        if (ct > batchMax()) break;
        String guid = res.getString("guid");
        out.println("<li>" + guid + ": ");
        Encounter enc = myShepherd.getEncounter(guid);
        if (enc != null) {
            ct--;
            out.println("<i>encounter exists; skipping</i>");
        } else {
            enc = new Encounter();
            enc.setId(guid);
            Timestamp ts = res.getTimestamp("created");
            if (ts != null) enc.setDWCDateAdded(ts.toString());
            ts = res.getTimestamp("updated");
            if (ts != null) enc.setDWCDateLastModified(ts.toString());
            String txguid = res.getString("taxonomy_guid");
            if (txguid != null) enc.setTaxonomyFromString(txmap.get(txguid));
            User owner = myShepherd.getUserByUUID(res.getString("owner_guid"));
            if (owner != null) {
                enc.addSubmitter(owner);
                enc.setSubmitterID(owner.getUsername());
            }
            Double d = res.getDouble("decimal_latitude");
            if (res.wasNull()) d = null;
            enc.setDecimalLatitude(d);
            d = res.getDouble("decimal_longitude");
            if (res.wasNull()) d = null;
            enc.setDecimalLongitude(d);
            enc.setSex(res.getString("sex"));
            enc.setVerbatimLocality(res.getString("verbatim_locality"));
            enc.setLocationID(res.getString("location_guid"));

            // date/time madness
            ts = res.getTimestamp("datetime");
System.out.println("TIME: ts=" + ts);
            String tz = res.getString("timezone");
            if (tz != null) TimeZone.setDefault(TimeZone.getTimeZone(tz));
            String spec = res.getString("specificity");
            Timestamp adjusted = ts;
            enc.setYear(adjusted.getYear() + 1900);
            if (!"year".equals(spec)) {
                enc.setMonth(adjusted.getMonth() + 1);
                if (!"month".equals(spec)) {
                    enc.setDay(adjusted.getDate());
                    if (!"day".equals(spec)) {
                        enc.setHour(adjusted.getHours());
                        enc.setMinutes(Integer.toString(adjusted.getMinutes())); // ygbkm
                    }
                }
            }

            // custom fields, oof
            //  these need to be hard-coded per migration
            JSONObject cfData = cleanJSONObject(res.getString("custom_fields"));
            String lifeStage = cfString(cfData, "344792fc-7910-45cd-867b-cb9c927677e1");
            String livingStatus = cfString(cfData, "b9eb55f4-ebc6-47b7-9991-9339084c8639");
            String occRemarks = cfString(cfData, "0d9a3764-f872-4320-ba03-bde268ce1513");
            String researcherComments = cfString(cfData, "b230a670-ee2e-44c4-89a1-6b1dffe2cda3");
            String unidentIndiv = cfString(cfData, "0f48fdc5-6a5e-4a01-aeff-2f1bebf4864d");
            enc.setLifeStage(lifeStage);
            enc.setLivingStatus(livingStatus);
            enc.setOccurrenceRemarks(occRemarks);
            enc.addComments(researcherComments);
            if (unidentIndiv != null) enc.setDynamicProperty("unidentified_individual", unidentIndiv);

            myShepherd.storeNewEncounter(enc, guid);

            String msg = "created encounter [" + ct + "] " + enc;
            out.println("<b>" + msg + "</b>");
            System.out.println(msg);
        }
        out.println("</li>");
    }
    out.println("</ol>");

    // annotation joins after
    ct = 0;
    res = st.executeQuery("SELECT guid, encounter_guid FROM annotation ORDER BY encounter_guid, guid");
    while (res.next()) {
        String annGuid = res.getString("guid");
        String encGuid = res.getString("encounter_guid");
        Encounter enc = myShepherd.getEncounter(encGuid);
        Annotation ann = myShepherd.getAnnotation(annGuid);
        if ((enc == null) || (ann == null)) {
            System.out.println("migrateEncounters: cannot join due to null; enc=" + enc + "; ann=" + ann);
            continue;
        }
        ct++;
        enc.addAnnotation(ann);
    }
    out.println("<p>joined " + ct + " enc/ann pairs</p>");
}


private static void migrateOccurrences(JspWriter out, Shepherd myShepherd, Connection conn) throws SQLException, IOException {
    out.println("<h2>Occurrences</h2><ol>");
    Statement st = conn.createStatement();
    Statement st2 = conn.createStatement();
    ResultSet res = st.executeQuery("SELECT sighting.*, complex_date_time.datetime, complex_date_time.timezone, complex_date_time.specificity FROM sighting JOIN complex_date_time ON (time_guid = complex_date_time.guid) ORDER BY guid");
    int ct = 0;

    while (res.next()) {
        ct++;
        if (ct > batchMax()) break;
        String guid = res.getString("guid");
        out.println("<li>" + guid + ": ");
        Occurrence occ = myShepherd.getOccurrence(guid);
        if (occ != null) {
            ct--;
            out.println("<i>occurrence exists; skipping</i>");
        } else {
            occ = new Occurrence();
            occ.setId(guid);
            Timestamp ts = res.getTimestamp("created");
            if (ts != null) occ.setDateTimeCreated(ts.toString());
            ts = res.getTimestamp("updated");
            if (ts != null) occ.setDWCDateLastModified(ts.toString());

            Double d = res.getDouble("decimal_latitude");
            if (res.wasNull()) d = null;
            occ.setDecimalLatitude(d);
            d = res.getDouble("decimal_longitude");
            if (res.wasNull()) d = null;
            occ.setDecimalLongitude(d);
            occ.setComments(res.getString("comments"));
            //occ.setVerbatimLocality(res.getString("verbatim_locality"));
            //occ.setLocationID(res.getString("location_guid"));

            // date/time madness
            ts = res.getTimestamp("datetime");
            String tz = res.getString("timezone");
            if (tz != null) TimeZone.setDefault(TimeZone.getTimeZone(tz));
            //String spec = res.getString("specificity");
            // we only store a long for this on occurrences
            if (ts != null) occ.setDateTimeLong(ts.getTime());

            // custom fields, oof
            //  these need to be hard-coded per migration
            JSONObject cfData = cleanJSONObject(res.getString("custom_fields"));
            Map<String,String> cfMap = new HashMap<String,String>();
            cfMap.put("Seen in Artificial Nest", cfString(cfData, "34a8f03e-d282-4fef-b1ed-9eeebaaa887e"));
            cfMap.put("Observation Type", cfString(cfData, "736d8b8f-7abb-404f-9da8-0c1507185baa"));
            cfMap.put("Seen with Unknown Seal", cfString(cfData, "e9a00eab-7ea6-4777-afb3-79d95ebfbf4f"));
            cfMap.put("Photography Type", cfString(cfData, "cf7ed66f-e6c1-4cb1-aadf-0f141ca22316"));
            cfMap.put("Sighting Origin", cfString(cfData, "15b4525a-47e9-4673-ae42-f99ea55f810c"));
            cfMap.put("Seen with Unknown Pup", cfString(cfData, "d0f2cc9e-0845-4608-8754-3d1f70eec699"));
            String photogName = cfString(cfData, "305b50df-7f21-4d8d-aeb6-45ab1869f5ba");
            String photogEmail = cfString(cfData, "ecc6f017-057c-4821-b07a-f82cd60aa31d");

            // we have to link encounters here due to customField needs :(
            //  this makes the joining code below kinda redundant but leaving it to catch stuff that missed
            ResultSet res2 = st2.executeQuery("SELECT guid FROM encounter WHERE sighting_guid='" + guid + "' ORDER BY guid");
            while (res2.next()) {
                Encounter enc = myShepherd.getEncounter(res2.getString("guid"));
                if (enc == null) continue;
                occ.addEncounter(enc);
                enc.setOccurrenceID(occ.getId());
                if (!stringEmpty(photogName)) enc.setPhotographerName(photogName);
                if (!stringEmpty(photogEmail)) enc.setPhotographerEmail(photogEmail);
            }

            // now we can do this, since it needs encs
            cfOccurrence(myShepherd, occ, cfMap);

            myShepherd.storeNewOccurrence(occ);

            String msg = "created occurrence [" + ct + "] " + occ;
            out.println("<b>" + msg + "</b>");
            System.out.println(msg);
        }
        out.println("</li>");
    }
    out.println("</ol>");

    ct = 0;
    res = st.executeQuery("SELECT guid, sighting_guid FROM encounter ORDER BY sighting_guid");
    while (res.next()) {
        String encGuid = res.getString("guid");
        String occGuid = res.getString("sighting_guid");
        Encounter enc = myShepherd.getEncounter(encGuid);
        Occurrence occ = myShepherd.getOccurrence(occGuid);
        if ((enc == null) || (occ == null)) {
            System.out.println("migrateOccurrences: cannot join due to null; enc=" + enc + "; occ=" + occ);
            continue;
        }
        if (occ.addEncounter(enc)) {
            enc.setOccurrenceID(occ.getId());
            ct++;
        }
    }
    out.println("<p>joined " + ct + " occ/enc pairs</p>");

}

private static void migrateMarkedIndividuals(JspWriter out, Shepherd myShepherd, Connection conn) throws SQLException, IOException {
    out.println("<h2>MarkedIndividuals</h2><ol>");
    Map<String,String> txmap = taxonomyMap(conn);
    Statement st = conn.createStatement();
    Statement st2 = conn.createStatement();
    ResultSet res = st.executeQuery("SELECT * FROM individual ORDER BY guid");
    int ct = 0;

    while (res.next()) {
        ct++;
        if (ct > batchMax()) break;
        String guid = res.getString("guid");
        out.println("<li>" + guid + ": ");
        MarkedIndividual indiv = myShepherd.getMarkedIndividual(guid);
        if (indiv != null) {
            ct--;
            out.println("<i>indiv exists; skipping</i>");
        } else {
            indiv = new MarkedIndividual();
            indiv.setId(guid);
            Timestamp ts = res.getTimestamp("created");
            if (ts != null) indiv.setDateTimeCreated(ts.toString());
            //ts = res.getTimestamp("updated");
            //if (ts != null) occ.setDWCDateLastModified(ts.toString());
            String txguid = res.getString("taxonomy_guid");
            if (txguid != null) indiv.setTaxonomyString(txmap.get(txguid));
            ts = res.getTimestamp("time_of_birth");
            if (ts != null) indiv.setTimeOfBirth(ts.getTime());
            ts = res.getTimestamp("time_of_death");
            if (ts != null) indiv.setTimeOfDeath(ts.getTime());
            indiv.setSex(res.getString("sex"));
            indiv.setComments(res.getString("comments"));

            ResultSet res2 = st2.executeQuery("SELECT * FROM name WHERE individual_guid='" + guid + "'");
            while (res2.next()) {
                String nameContext = res2.getString("context");
                String nameValue = res2.getString("value");
                if ("FirstName".equals(nameContext)) {
                    indiv.addName(nameValue);
                } else {
                    indiv.addName(nameContext, nameValue);
                }
            }

            // same note as occurrences on needing encs attached first :(
            res2 = st2.executeQuery("SELECT guid FROM encounter WHERE individual_guid='" + guid + "' ORDER BY guid");
            while (res2.next()) {
                Encounter enc = myShepherd.getEncounter(res2.getString("guid"));
                if (enc == null) continue;
                if (!enc.hasMarkedIndividual(indiv)) {
                    enc.setIndividual(indiv);
                }
            }

            // custom fields, oof
            //  these need to be hard-coded per migration
            JSONObject cfData = cleanJSONObject(res.getString("custom_fields"));

            String dateOfBirth = cfString(cfData, "87d08929-2133-4053-911a-8740f7fa8dd5");
            if (!stringEmpty(dateOfBirth)) try {
                indiv.setTimeOfBirth(Util.getVersionFromModified(dateOfBirth));
            } catch (Exception ex) {}
            String dateOfDeath = cfString(cfData, "ed537aa9-5d68-45e5-9236-f701d95a8bdd");
            if (!stringEmpty(dateOfDeath)) try {
                indiv.setTimeOfDeath(Util.getVersionFromModified(dateOfDeath));
            } catch (Exception ex) {}
            String notes = cfString(cfData, "8ac7286d-3290-41d3-8497-17b3f7aa5184");
            if (!stringEmpty(notes)) indiv.addComments(notes);

            // these require more complex stuff
            String withPup = cfString(cfData, "6428357e-8965-45f6-8f53-d17df08c4316");
            String lifeStatus = cfString(cfData, "854a9755-1909-464b-b024-7608045309a7");
            String flipperTag = cfString(cfData, "7bb54bb8-f148-47b5-91b3-286b8851e461");
            String entanglement = cfString(cfData, "e9ecaaac-54c9-4c94-bf2e-0989f467c1d1");

            if (!stringEmpty(withPup)) {
                indiv.addComments("<p><b>With Pup:</b> " + withPup + "</p>");
                for (Encounter enc : indiv.getEncounters()) {
System.out.println(">>>>> ??? " + withPup + " on " + enc);
                    enc.setDynamicProperty("with_pup", withPup);
                }
            }
            if (!stringEmpty(flipperTag)) {
                indiv.addComments("<p><b>Flipper Tag:</b> " + flipperTag + "</p>");
                MetalTag tag = new MetalTag(flipperTag, "flipper");
                for (Encounter enc : indiv.getEncounters()) {
System.out.println(">>>>> ??? " + tag + " on " + enc);
                    enc.addMetalTag(tag);
                }
            }
            if (!stringEmpty(lifeStatus)) {
                indiv.addComments("<p><b>Life Status:</b> " + lifeStatus + "</p>");
                Encounter[] recent = indiv.getDateSortedEncounters(true, 1);
                if ((recent != null) && (recent.length > 0)) recent[0].setLifeStage(lifeStatus);
System.out.println(">>>>> ??? " + lifeStatus + " on " + indiv);
            }
            if (!stringEmpty(entanglement)) {
                indiv.addComments("<p><b>Entanglement:</b> " + entanglement + "</p>");
                LabeledKeyword kw = myShepherd.getOrCreateLabeledKeyword("Entanglement", entanglement, false);
                for (Encounter enc : indiv.getEncounters()) {
                    for (MediaAsset ma : enc.getMedia()) {
System.out.println(">>>>> ??? " + kw + " on " + enc);
                        ma.addKeyword(kw);
                    }
                }
            }

            myShepherd.storeNewMarkedIndividual(indiv);

            String msg = "created indiv [" + ct + "] " + indiv;
            out.println("<b>" + msg + "</b>");
            System.out.println(msg);
        }
        out.println("</li>");
    }
    out.println("</ol>");

    ct = 0;
    res = st.executeQuery("SELECT guid, individual_guid FROM encounter WHERE individual_guid IS NOT NULL ORDER BY individual_guid");
    while (res.next()) {
        String encGuid = res.getString("guid");
        String indivGuid = res.getString("individual_guid");
        Encounter enc = myShepherd.getEncounter(encGuid);
        MarkedIndividual indiv = myShepherd.getMarkedIndividual(indivGuid);
        if ((enc == null) || (indiv == null)) {
            System.out.println("migrateMarkedIndividuals: cannot join due to null; enc=" + enc + "; indiv=" + indiv);
            continue;
        }
        ct++;
        enc.setIndividual(indiv);
    }
    out.println("<p>joined " + ct + " enc/indiv pairs</p>");

}


private static void migrateKeywords(JspWriter out, Shepherd myShepherd, Connection conn) throws SQLException, IOException {
    out.println("<h2>Keywords</h2><ol>");
    Statement st = conn.createStatement();
    ResultSet res = st.executeQuery("SELECT keyword.guid, value, STRING_AGG(annotation_guid::text, ',') AS annot_guids FROM keyword JOIN annotation_keywords ON (keyword.guid = keyword_guid) GROUP BY keyword.guid");
    int ct = 0;

    while (res.next()) {
        ct++;
        String guid = res.getString("guid");
        String value = res.getString("value");
        if (stringEmpty(value)) continue;
        String annIdList = res.getString("annot_guids");
        if (stringEmpty(annIdList)) continue;
        String[] annIds = annIdList.split(",");
        if (annIds.length < 1) continue;
        Keyword kw = myShepherd.getOrCreateKeyword(value);
        out.println("<li>" + guid + " " + kw + ": <ul>");
        for (String annId : annIds) {
            Annotation ann = myShepherd.getAnnotation(annId);
            if (ann == null) {
                out.println("<li><i>cannot load Annot " + annId + "</i></li>");
                continue;
            }
            MediaAsset ma = ann.getMediaAsset();
            if (ma == null) {
                out.println("<li><i>cannot load MediaAsset on " + ann + "</i></li>");
                continue;
            }
            if (ma.hasKeyword(kw)) {
                out.println("<li><i>" + ma + " already has " + kw + "</i></li>");
                continue;
            }
            ma.addKeyword(kw);
            out.println("<li><b>Added " + kw + " to " + ma + "</b></li>");
        }
        out.println("</ul></li>");
    }
    out.println("</ol>");
}

private static void migrateRelationships(JspWriter out, Shepherd myShepherd, Connection conn) throws SQLException, IOException {
    out.println("<h2>Relationships</h2><ol>");
    Map<String,String> relMeta = relationshipMeta(conn);
    Statement st = conn.createStatement();
    Statement st2 = conn.createStatement();
    ResultSet res = st.executeQuery("SELECT * FROM relationship");
    int ct = 0;

    while (res.next()) {
        ct++;
        String guid = res.getString("guid");
        String typeGuid = res.getString("type_guid");
        out.println("<li>" + guid + " [" + typeGuid + "]: ");
        String typeLabel = relMeta.get(typeGuid);
        if (typeLabel == null) {
            out.println("<i>unknown label</i></li>");
            continue;
        }
        out.println(" typeLabel=" + typeLabel);
        ResultSet res2 = st2.executeQuery("SELECT * FROM relationship_individual_member WHERE relationship_guid='" + guid + "'");
        List<MarkedIndividual> indivs = new ArrayList<MarkedIndividual>();
        List<String> roles = new ArrayList<String>();
        out.println("<ul>");
        while (res2.next()) {
            String indivGuid = res2.getString("individual_guid");
            String roleGuid = res2.getString("individual_role_guid");
            MarkedIndividual indiv = myShepherd.getMarkedIndividual(indivGuid);
            String roleLabel = relMeta.get(roleGuid);
            if ((indiv == null) || (roleLabel == null)) {
                out.println("<li><i>failed on indivGuid=" + indivGuid + ", roleGuid=" + roleGuid + "[" + roleLabel + "]</i></li>");
            } else {
                out.println("<li><b>" + roleGuid + "[" + roleLabel + "]</b> on " + indiv + "</li>");
                indivs.add(indiv);
                roles.add(roleLabel);
            }
        }
        out.println("</ul>");
        if (indivs.size() != 2) {
            out.println("<i>invalid indivs.size=" + indivs.size() + "</i></li>");
            continue;
        }

        Relationship rel = myShepherd.getRelationship(typeLabel, indivs.get(0).getId(), indivs.get(1).getId(), roles.get(0), roles.get(1));
        if (rel != null) {
            out.println("<i>rel already exists: <b>" + rel + "</b></i></li>");
            continue;
        }
        rel = new Relationship(typeLabel, indivs.get(0), indivs.get(1));
        myShepherd.getPM().makePersistent(rel);
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
        rel.setMarkedIndividualRole1(roles.get(0));
        rel.setMarkedIndividualRole2(roles.get(1));
        out.println("<b>created " + rel + " on " + indivs.get(0) + " and " + indivs.get(1) + "</b>");
        out.println("</li>");
    }
    out.println("</ol>");
}

private static void migrateSocialGroups(JspWriter out, Shepherd myShepherd, Connection conn) throws SQLException, IOException {
    out.println("<h2>SocialGroups</h2><ol>");
    Map<String,String> sgMeta = socialGroupMeta(conn);
    Statement st = conn.createStatement();
    ResultSet res = st.executeQuery("SELECT group_guid, name, individual_guid, roles FROM social_group_individual_membership JOIN social_group ON (group_guid=social_group.guid);");
    int ct = 0;

    Map<String,SocialUnit> units = new HashMap<String,SocialUnit>();
    while (res.next()) {
        ct++;
        String suName = res.getString("name");
        SocialUnit su = units.get(suName);
        if (su == null) su = myShepherd.getSocialUnit(suName);
        if (su == null) su = new SocialUnit(suName);
        units.put(suName, su);
        out.println("<li><b>" + su + "</b>: ");

        String indivID = res.getString("individual_guid");
        MarkedIndividual indiv = myShepherd.getMarkedIndividual(indivID);
        if (indiv == null) {
            out.println("<i>failed to load indivID=" + indivID + "</i></li>");
            continue;
        }
        out.println("[" + indiv + "]; ");
        // even tho the code before will import multiple roles per individual, we are
        //  going to bail on there already *existing* one meaning this has been done
        //  ymmv ???
        Membership exists = su.getMembershipForMarkedIndividual(indiv);
        if (exists != null) {
            out.println("<i>a membership exists for " + indiv + "; skipping</i></li>");
            continue;
        }
        myShepherd.getPM().makePersistent(su);

        JSONArray rolesArr = cleanJSONArray(res.getString("roles"));
        if ((rolesArr == null) || (rolesArr.length() == 0)) {
            // guess we make it a null role and add it???
            Membership membership = new Membership(indiv);
            membership.setRole(null);
            myShepherd.getPM().makePersistent(membership);
            su.addMember(membership);
            out.println("[null role]");
        } else {
            for (int i = 0 ; i < rolesArr.length() ; i++) {
                String role = rolesArr.optString(i, null);
                if (role == null) continue;
                String roleName = sgMeta.get(role);
                if (roleName == null) {
                    out.println("<i>[failed to find roleName for role=" + role + "; skipping]</i>");
                    continue;
                }
                Membership membership = new Membership(indiv);
                membership.setRole(roleName);
                myShepherd.getPM().makePersistent(membership);
                su.addMember(membership);
                out.println("[role=" + role + "; roleName=" + roleName + "]");
            }
        }

        out.println("</li>");
    }
    out.println("</ol>");
}

private static void migratePendingSightings(JspWriter out, Shepherd myShepherd, Connection conn, HttpServletRequest request) throws SQLException, IOException {
    out.println("<h2>PendingSightings</h2><ol>");
    Statement st = conn.createStatement();
    ResultSet res = st.executeQuery("SELECT * FROM asset_group_sighting WHERE stage != 'processed' ORDER BY guid");
    int ct = 0;

    while (res.next()) {
        ct++;
        String guid = res.getString("guid");
        out.println("<li>" + guid + ": ");
        Occurrence occ = myShepherd.getOccurrence(guid);
        if (occ != null) {
            ct--;
            out.println("<i>occurrence exists; skipping</i>");
        } else {
            JSONObject config = cleanJSONObject(res.getString("config"));
            if (config == null) {
                out.println("<i>null config; failing</i></li>");
                continue;
            }
            out.println("<xmp>" + config.toString(4) + "</xmp>");

            JSONObject sdata = config.optJSONObject("sighting");
            if (sdata == null) {
                out.println("<i>null sighitng data in config; failing</i></li>");
                continue;
            }
            JSONArray earr = sdata.optJSONArray("encounters");
            if ((earr == null) || (earr.length() < 1)) {
                out.println("<i>null or empty encounters array in config; failing</i></li>");
                continue;
            }

            occ = new Occurrence();
            occ.setId(guid);
            // TODO something with asset_group_guid ?

            occ.setEncounters(new ArrayList<Encounter>()); //grrr
            for (int i = 0 ; i < earr.length() ; i++) {
                Encounter enc = encounterFromPending(earr.optJSONObject(i));
                if (enc == null) {
                    out.println("<i>failed to create enc " + i + "</i></li>");
                    continue;
                }
                occ.addEncounter(enc);
                enc.setOccurrenceID(occ.getId());
            }

            Timestamp ts = res.getTimestamp("created");
            if (ts != null) occ.setDateTimeCreated(ts.toString());
            ts = res.getTimestamp("updated");
            if (ts != null) occ.setDWCDateLastModified(ts.toString());

            Double d = null;
            if (sdata.has("decimal_latitude") && !sdata.isNull("decimal_latitude")) d = sdata.getDouble("decimal_latitude");
            occ.setDecimalLatitude(d);
            d = null;
            if (sdata.has("decimal_longitude") && !sdata.isNull("decimal_longitude")) d = sdata.getDouble("decimal_longitude");
            occ.setDecimalLongitude(d);
            occ.setComments(sdata.optString("comments", null));
            //occ.setVerbatimLocality(res.getString("verbatim_locality"));
            //occ.setLocationID(res.getString("location_guid"));

            // date/time madness
            String time = sdata.optString("time", null);
            if (time == null) {
                out.println("<i>null time config; failing</i></li>");
                continue;
            }
            //String spec = res.getString("specificity"); n/a
            // we only store a long for this on occurrences
            occ.setDateTimeLong(new DateTime(time).getMillis());

            // custom fields, oof
            //  these need to be hard-coded per migration
            JSONObject cfData = sdata.optJSONObject("customFields");
            Map<String,String> cfMap = new HashMap<String,String>();
            cfMap.put("Seen in Artificial Nest", cfString(cfData, "34a8f03e-d282-4fef-b1ed-9eeebaaa887e"));
            cfMap.put("Observation Type", cfString(cfData, "736d8b8f-7abb-404f-9da8-0c1507185baa"));
            cfMap.put("Seen with Unknown Seal", cfString(cfData, "e9a00eab-7ea6-4777-afb3-79d95ebfbf4f"));
            cfMap.put("Photography Type", cfString(cfData, "cf7ed66f-e6c1-4cb1-aadf-0f141ca22316"));
            cfMap.put("Sighting Origin", cfString(cfData, "15b4525a-47e9-4673-ae42-f99ea55f810c"));
            cfMap.put("Seen with Unknown Pup", cfString(cfData, "d0f2cc9e-0845-4608-8754-3d1f70eec699"));
            String photogName = cfString(cfData, "305b50df-7f21-4d8d-aeb6-45ab1869f5ba");
            String photogEmail = cfString(cfData, "ecc6f017-057c-4821-b07a-f82cd60aa31d");

            if (occ.getNumberEncounters() > 0) for (Encounter enc : occ.getEncounters()) {
                if (!stringEmpty(photogName)) enc.setPhotographerName(photogName);
                if (!stringEmpty(photogEmail)) enc.setPhotographerEmail(photogEmail);
                try {
                    out.println("<xmp style=\"font-size: 0.8em\">" + enc.uiJson(request).toString(4) + "</xmp><hr />");
                } catch (Exception ex) {}
            }

            // now we can do this, since it needs encs
            cfOccurrence(myShepherd, occ, cfMap);

/*
            myShepherd.storeNewOccurrence(occ);

            String msg = "created occurrence [" + ct + "] " + occ;
            out.println("<b>" + msg + "</b>");
            System.out.println(msg);
*/


            out.println("<xmp>" + occ.getJSONSummary().toString(4) + "</xmp>");
        }

        out.println("</li>");
    }
    out.println("</ol>");
}


private static void fixAutogenNames(JspWriter out, Shepherd myShepherd, Connection conn) throws SQLException, IOException {
    Object ss = siteSetting("autogenerated_names", conn);
    if (ss == null) return;
    JSONObject autogenMeta = (JSONObject)ss;

    out.println("<h2>Autogenerated Names</h2><ol>");
    //Statement st = conn.createStatement();
    Query query = myShepherd.getPM().newQuery("SELECT FROM org.ecocean.MultiValue WHERE valuesAsString.matches('.*autogen-.*')");
    Collection c = (Collection)(query.execute());
    List<MultiValue> all = new ArrayList<MultiValue>(c);
    query.closeAll();
    for (MultiValue mv : all) {
        JSONObject val = mv.getValues();
        Map<String,String> replace = new HashMap<String,String>();
        for (String key : (Set<String>)val.keySet()) {
            if (key.startsWith("autogen-")) {
                JSONArray names = val.optJSONArray(key);
                if ((names == null) || (names.length() < 1)) continue;
                String suffix = names.optString(0, null);
                if (suffix == null) continue;
                String txId = key.substring(8);
                JSONObject meta = autogenMeta.optJSONObject(txId);
                if (meta == null) continue;
                String prefix = meta.optString("prefix", null);
                if (prefix == null) continue;
                replace.put(key, prefix + "-" + suffix);
            }
        }
        for (String kill : replace.keySet()) {
            val.remove(kill);
            JSONArray newNames = new JSONArray();
            newNames.put(replace.get(kill));
            val.put("autogenerated", newNames);
        }
        out.println("<li>" + val + "</li>");
        mv.setValues(val);
        myShepherd.getPM().makePersistent(mv);
    }
    out.println("</ol>");
}

%>


<%

File tmp = new File(TMP_DIR);
if (!tmp.exists()) tmp.mkdir();

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.beginDBTransaction();

Properties props = ShepherdProperties.getProperties("codexMigration.properties", "", context);
String dbUrl = props.getProperty("codexDbUrl", context);
String dbUsername = props.getProperty("codexDbUsername", context);
String dbPassword = props.getProperty("codexDbPassword", context);

// this is under data-dir
String assetGroupDir = props.getProperty("assetGroupDir", context);
File dataDir = CommonConfiguration.getDataDirectory(getServletContext(), context);


Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);

JSONObject locJson = locationJson(conn);
// hard-coded file path, but life is rough
if (locJson != null) Util.writeToFile(locJson.toString(4), "/usr/local/tomcat/webapps/wildbook_data_dir/WEB-INF/classes/bundles/locationID.json");


/*
migrateUsers(out, myShepherd, conn);

migrateMediaAssets(out, myShepherd, conn, request, new File(dataDir, assetGroupDir));

migrateAnnotations(out, myShepherd, conn);

migrateEncounters(out, myShepherd, conn);

migrateOccurrences(out, myShepherd, conn);

migrateMarkedIndividuals(out, myShepherd, conn);

migrateKeywords(out, myShepherd, conn);

migrateRelationships(out, myShepherd, conn);

fixAutogenNames(out, myShepherd, conn);

migrateSocialGroups(out, myShepherd, conn);
*/

migratePendingSightings(out, myShepherd, conn, request);


myShepherd.commitDBTransaction();
myShepherd.closeDBTransaction();

%>



