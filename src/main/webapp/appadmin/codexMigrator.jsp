<%@ page contentType="text/html; charset=utf-8" language="java"
import="org.ecocean.*,
org.ecocean.tag.MetalTag,
org.ecocean.social.*,
org.ecocean.servlet.ServletUtilities,
org.ecocean.social.Relationship,
org.ecocean.genetics.TissueSample,
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
org.ecocean.security.Collaboration,
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

private static void encAddMeasurement(Shepherd myShepherd, Encounter enc, JSONObject cfData, String cfKey, String mType, String mUnit) {
    if ((enc == null) || (cfData == null)) return;
    Double value = cfDouble(cfData, cfKey);
    if (value == null) return;
    Measurement meas = new Measurement(enc.getId(), mType, value, mUnit, null);
    enc.setMeasurement(meas, myShepherd);
Util.mark("on " + enc.getId() + ": measurement " + meas);
}

private static void encAddLabeledKeyword(Shepherd myShepherd, Encounter enc, JSONObject cfData, String cfKey, String label) {
    if ((enc == null) || (cfData == null) || (label == null)) return;
    if (enc.numAnnotations() < 1) return;
    String value = cfString(cfData, cfKey);
    if (stringEmpty(value)) return;
    LabeledKeyword kw = myShepherd.getOrCreateLabeledKeyword(label, value, false);
    for (MediaAsset ma : enc.getMedia()) {
        ma.addKeyword(kw);
Util.mark("on " + enc.getId() + ": " + kw + " on: " + ma);
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

private static Double cfDouble(JSONObject cfData, String key) {
    if (cfData == null) return null;
    if (!cfData.has(key)) return null;
    if (cfData.isNull(key)) return null;
    try {
        return cfData.getDouble(key);
    } catch (Exception ex) {}
    return null;
}

private static String cfArrayFlattenString(JSONObject cfData, String key) {
    if (cfData == null) return null;
    if (!cfData.has(key)) return null;
    if (cfData.isNull(key)) return null;
    JSONArray arr = null;
    try {
        arr = cfData.getJSONArray(key);
    } catch (Exception ex) {}
    // sometimes its just stored as a string (not array) SIGH
    if (arr == null) {
        try {
            String one = cfData.getString(key);
            if (stringEmpty(one)) return null;
            return one;
        } catch (Exception ex) {}
    }
    if ((arr == null) || (arr.length() < 1)) return null;
    List<String> all = new ArrayList<String>();
    for (int i = 0 ; i < arr.length() ; i++) {
        String val = arr.optString(i, null);
        if (val != null) all.add(val);
    }
    return String.join(", ", all);
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
    long startTime = System.currentTimeMillis();
    out.println("<h2>Encounters</h2><ol>");
    Map<String,String> txmap = taxonomyMap(conn);
    Statement st = conn.createStatement();
    Statement st2 = conn.createStatement();
    ResultSet res = st.executeQuery("SELECT encounter.*, complex_date_time.datetime, complex_date_time.timezone, complex_date_time.specificity FROM encounter JOIN complex_date_time ON (time_guid = complex_date_time.guid) ORDER BY guid");
    int ct = 0;

    Map<String, JSONObject> encCfData = new HashMap<String, JSONObject>();

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
            encCfData.put(enc.getId(), cfData);
            Double age = cfDouble(cfData, "e44351df-44f7-4f4f-9c4e-204be927114a");
            Double direction = cfDouble(cfData, "3c896984-11b6-4b18-bde6-6e5e3c6241c6");
            Double distance = cfDouble(cfData, "f322597e-dbc3-48f6-9abe-e405b7fe1d6c");

            String behavior = cfArrayFlattenString(cfData, "37b9877c-0f66-4613-ab6c-bc622f3c8c6b");
            String lifeStage = cfString(cfData, "48030bb6-2e6a-4e70-a3fc-07dce63e6c78");
            String livingStatus = cfString(cfData, "c6a10ee5-4921-4701-a996-4f104b160f03");
            String sampleId = cfString(cfData, "fe330c83-562b-4bac-8a98-3c957fd4c3d2");
            //String femaleRepro = cfString(cfData, "c26d13c2-359e-48a7-977c-5b7b5db6c81f");
            //String maleRepro = cfString(cfData, "0b1117cd-27f3-46e2-8884-d789bc9f4967");

            if (!stringEmpty(sampleId)) {
                TissueSample sample = new TissueSample(enc.getId(), sampleId);
                enc.addTissueSample(sample);
Util.mark("on " + enc.getId() + ": tissue sample " + sample);
            }

            if (!stringEmpty(behavior)) enc.setBehavior(behavior);
            if (!stringEmpty(lifeStage)) enc.setLifeStage(lifeStage);
            if (!stringEmpty(livingStatus)) enc.setLivingStatus(livingStatus);

            if (age != null) {
                Measurement meas = new Measurement(enc.getId(), "Age", age, "years", null);
                enc.setMeasurement(meas, myShepherd);
Util.mark("on " + enc.getId() + ": measurement " + meas);
            }
            if (direction != null) {
                Measurement meas = new Measurement(enc.getId(), "DirectionToGroup", direction, "degrees", null);
                enc.setMeasurement(meas, myShepherd);
Util.mark("on " + enc.getId() + ": measurement " + meas);
            }
            if (distance != null) {
                Measurement meas = new Measurement(enc.getId(), "DistanceToGroup", distance, "meters", null);
                enc.setMeasurement(meas, myShepherd);
Util.mark("on " + enc.getId() + ": measurement " + meas);
            }
 
            myShepherd.storeNewEncounter(enc, guid);

            String msg = "created encounter [" + ct + "] " + enc;
            out.println("<b>" + msg + "</b>");
            System.out.println("MARK: " + msg);
        }
        out.println("</li>");
    }
    out.println("</ol>");
Util.mark("TIME MID", startTime);

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
        MediaAsset ma = ann.getMediaAsset();

        String femaleRepro = null;
        String maleRepro = null;
        if (encCfData.get(encGuid) != null) {
            femaleRepro = cfString(encCfData.get(encGuid), "c26d13c2-359e-48a7-977c-5b7b5db6c81f");
            maleRepro = cfString(encCfData.get(encGuid), "0b1117cd-27f3-46e2-8884-d789bc9f4967");
Util.mark("on " + enc.getId() + " ===> " + femaleRepro + ", " + maleRepro);
        }
        if (!stringEmpty(femaleRepro)) {
            LabeledKeyword kw = myShepherd.getOrCreateLabeledKeyword("Female reproductive status", femaleRepro, false);
            ma.addKeyword(kw);
Util.mark("on " + enc.getId() + ": " + kw + " on: " + ma);
        }
        if (!stringEmpty(maleRepro)) {
            LabeledKeyword kw = myShepherd.getOrCreateLabeledKeyword("Male reproductive status", maleRepro, false);
            ma.addKeyword(kw);
Util.mark("on " + enc.getId() + ": " + kw + " on: " + ma);
        }
    }
    out.println("<p>joined " + ct + " enc/ann pairs</p>");
Util.mark("TIME COMPLETE", startTime);
}


private static void migrateOccurrences(JspWriter out, Shepherd myShepherd, Connection conn) throws SQLException, IOException {
    out.println("<h2>Occurrences</h2><ol>");
    Statement st = conn.createStatement();
    Statement st2 = conn.createStatement();
    ResultSet res = st.executeQuery("SELECT sighting.*, complex_date_time.datetime, complex_date_time.timezone, complex_date_time.specificity FROM sighting JOIN complex_date_time ON (time_guid = complex_date_time.guid) ORDER BY guid");
    int ct = 0;
    //Map<String, JSONObject> occCfData = new HashMap<String, JSONObject>();

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
            //occCfData.put(occ.getId(), cfData);
            String behavior = cfArrayFlattenString(cfData, "665cc563-4ec4-4f1e-afcb-5617e1768312");
            if (!stringEmpty(behavior)) occ.setGroupBehavior(behavior);
/*
            Map<String,String> cfMap = new HashMap<String,String>();
            cfMap.put("Seen in Artificial Nest", cfString(cfData, "34a8f03e-d282-4fef-b1ed-9eeebaaa887e"));
            cfMap.put("Observation Type", cfString(cfData, "736d8b8f-7abb-404f-9da8-0c1507185baa"));
            cfMap.put("Seen with Unknown Seal", cfString(cfData, "e9a00eab-7ea6-4777-afb3-79d95ebfbf4f"));
            cfMap.put("Photography Type", cfString(cfData, "cf7ed66f-e6c1-4cb1-aadf-0f141ca22316"));
            cfMap.put("Sighting Origin", cfString(cfData, "15b4525a-47e9-4673-ae42-f99ea55f810c"));
            cfMap.put("Seen with Unknown Pup", cfString(cfData, "d0f2cc9e-0845-4608-8754-3d1f70eec699"));
            String photogName = cfString(cfData, "305b50df-7f21-4d8d-aeb6-45ab1869f5ba");
            String photogEmail = cfString(cfData, "ecc6f017-057c-4821-b07a-f82cd60aa31d");
*/

            // we have to link encounters here due to customField needs :(
            //  this makes the joining code below kinda redundant but leaving it to catch stuff that missed
            ResultSet res2 = st2.executeQuery("SELECT guid FROM encounter WHERE sighting_guid='" + guid + "' ORDER BY guid");
            while (res2.next()) {
                Encounter enc = myShepherd.getEncounter(res2.getString("guid"));
                if (enc == null) continue;
                occ.addEncounter(enc);
                enc.setOccurrenceID(occ.getId());

                encAddMeasurement(myShepherd, enc, cfData, "660d3519-3adc-4e4c-80ab-41e217025936", "cloudCover", "percent");
                encAddMeasurement(myShepherd, enc, cfData, "213ba304-592c-4b5b-9b54-6998fcca28d3", "countThickBushes", null);
                encAddMeasurement(myShepherd, enc, cfData, "785ea4e1-acaf-4fc8-9448-7e981e47da52", "distanceNearestZebra", "meters");
                encAddMeasurement(myShepherd, enc, cfData, "899cfa95-1cb1-4db4-b5a7-f35427f40fd5", "individualCount", null);
                encAddMeasurement(myShepherd, enc, cfData, "9ae750a5-37dd-45be-85e7-a43072477d93", "numberAgonism", null);
                encAddMeasurement(myShepherd, enc, cfData, "f3611d23-6670-403e-8d72-96f2e5ecebb1", "numberDrinking", null);
                encAddMeasurement(myShepherd, enc, cfData, "3177d25e-cfd6-48a3-bdf2-5c67fcfc567f", "numberGeophagy", null);
                encAddMeasurement(myShepherd, enc, cfData, "eac2dff0-507c-4f9a-b76d-47e7dbf10209", "numberGrassSpecies", null);
                encAddMeasurement(myShepherd, enc, cfData, "800e6950-2806-4e1a-a48a-85a9140cfe4a", "numberGrazing", null);
                encAddMeasurement(myShepherd, enc, cfData, "2fb2ac8e-bdae-4527-a90a-61d7b3b32153", "numberGrooming", null);
                encAddMeasurement(myShepherd, enc, cfData, "b41a203a-4ba3-472c-b5fc-d5aef41b1955", "numberHealth", null);
                encAddMeasurement(myShepherd, enc, cfData, "558ccf92-d7e5-40f8-8cb1-281f7f32e891", "numberLying", null);
                encAddMeasurement(myShepherd, enc, cfData, "39832492-530d-4c0d-be0d-ad122b919ec9", "numberNotVisible", null);
                encAddMeasurement(myShepherd, enc, cfData, "13df8e88-39fd-491e-8a0f-42e623b16a3f", "numberNurseSuckle", null);
                encAddMeasurement(myShepherd, enc, cfData, "f90faaf0-8fcc-43e5-8aef-f33628ca4e12", "numberOtherSpecies1", null);
                encAddMeasurement(myShepherd, enc, cfData, "ce262053-19e3-482c-af37-f41c039b8cb5", "numberOtherSpecies2", null);
                encAddMeasurement(myShepherd, enc, cfData, "5ad52894-9979-4f90-bdce-17c29ad563f5", "numberOtherSpecies3", null);
                encAddMeasurement(myShepherd, enc, cfData, "768a5aee-766c-456a-94d8-cb6bfa95fede", "numberPlaying", null);
                encAddMeasurement(myShepherd, enc, cfData, "d1c6da62-659e-4eb4-9442-d5abb3e6c142", "numberResting", null);
                encAddMeasurement(myShepherd, enc, cfData, "6e60ce75-96d7-4acf-833f-d7fdb6ffdd7b", "numberRunning", null);
                encAddMeasurement(myShepherd, enc, cfData, "c55e00fd-04bd-4fbc-bba0-8e004977e85e", "numberSalting", null);
                encAddMeasurement(myShepherd, enc, cfData, "1ff5ff65-b3b3-4517-9c17-2125468343ca", "numberSexual", null);
                encAddMeasurement(myShepherd, enc, cfData, "05110f89-7162-4461-8b33-f252bf56188d", "numberSocializing", null);
                encAddMeasurement(myShepherd, enc, cfData, "8378401d-5a71-4e46-a787-220911be1feb", "numberStanding", null);
                encAddMeasurement(myShepherd, enc, cfData, "d57aa220-8691-4c3c-b598-c8485017088e", "numberVigilant", null);
                encAddMeasurement(myShepherd, enc, cfData, "ebe8bf2b-1496-493b-b97f-cf9cf4230e64", "numberWalking", null);

                encAddLabeledKeyword(myShepherd, enc, cfData, "ac8b14e4-e284-4cc3-b021-8de4a77977c0", "Bush type");
                encAddLabeledKeyword(myShepherd, enc, cfData, "d6adae96-b892-4e12-aaca-270b71c9d98c", "Grass height");
                encAddLabeledKeyword(myShepherd, enc, cfData, "817e749f-0468-4d26-bccb-f3c2e00351cc", "Grass color");
                encAddLabeledKeyword(myShepherd, enc, cfData, "0db01770-961d-4fca-8217-159704ab1aed", "Grass species 1");
                encAddLabeledKeyword(myShepherd, enc, cfData, "6fd91781-6100-4ccf-b16c-ea821fe1186a", "Grass species 2");
                encAddLabeledKeyword(myShepherd, enc, cfData, "95c6131a-281a-4883-b676-e313766a8c15", "Grass species 3");
                encAddLabeledKeyword(myShepherd, enc, cfData, "308bad8e-ab79-4c29-aa73-bbff34f6967f", "Habitat obscurity");
                encAddLabeledKeyword(myShepherd, enc, cfData, "f8854f1c-774d-4fb9-8e60-1d2e2e1f1fde", "Other species 1");
                encAddLabeledKeyword(myShepherd, enc, cfData, "861b6411-d9ee-44b5-8a4c-0e7dcbb3596f", "Other species 2");
                encAddLabeledKeyword(myShepherd, enc, cfData, "157db683-354b-4eca-937d-eaca7547b3da", "Other species 3");
                encAddLabeledKeyword(myShepherd, enc, cfData, "6392ae9f-0b35-44df-8b87-7bd2b733d83d", "Rain");
                encAddLabeledKeyword(myShepherd, enc, cfData, "27b96f1c-fe13-4aba-9fb5-6f99fee1ca10", "Soil");
                encAddLabeledKeyword(myShepherd, enc, cfData, "d983c468-4693-4603-8820-1c9c6034d254", "Sun");
                encAddLabeledKeyword(myShepherd, enc, cfData, "a2c3ca2c-8dac-4baf-a794-21132cf32ffc", "Wind");
            }

            // now we can do this, since it needs encs
            ////cfOccurrence(myShepherd, occ, cfMap);

            myShepherd.storeNewOccurrence(occ);

            String msg = "created occurrence [" + ct + "] " + occ;
            out.println("<b>" + msg + "</b>");
            System.out.println("MARK: " + msg);
        }
        out.println("</li>");
    }
    out.println("</ol>");

/*
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
*/

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

/*
            // same note as occurrences on needing encs attached first :(
            res2 = st2.executeQuery("SELECT guid FROM encounter WHERE individual_guid='" + guid + "' ORDER BY guid");
            while (res2.next()) {
                Encounter enc = myShepherd.getEncounter(res2.getString("guid"));
                if (enc == null) continue;
                if (!enc.hasMarkedIndividual(indiv)) {
                    enc.setIndividual(indiv);
                }
            }
*/

            // custom fields, oof
            //  these need to be hard-coded per migration
            JSONObject cfData = cleanJSONObject(res.getString("custom_fields"));

            String notes = cfString(cfData, "e1986d15-647b-4526-9b96-89b2c4d80ab1");
            if (!stringEmpty(notes)) indiv.addComments(notes);

            myShepherd.storeNewMarkedIndividual(indiv);

            String msg = "created indiv [" + ct + "] " + indiv;
            out.println("<b>" + msg + "</b>");
            System.out.println("MARK: " + msg);
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

/*
seal_codex=> select collaboration_guid, user_guid, read_approval_state, edit_approval_state from collaboration_user_associations limit 4;
          collaboration_guid          |              user_guid               | read_approval_state | edit_approval_state 
--------------------------------------+--------------------------------------+---------------------+---------------------
 5b2ab089-fa8e-447d-bcc7-467cfb26a7fe | 15ec5d1b-562a-491c-a0e0-9c1e0f1603c3 | approved            | approved
 0071c641-ba33-4b37-b467-2f0d5da9a5b8 | 315a9bca-d9d9-4f9b-bf40-a0210e9aff38 | approved            | not_initiated
 5b2ab089-fa8e-447d-bcc7-467cfb26a7fe | 46b609dc-7d0d-41fb-b73b-783ab52aa146 | approved            | approved

 approved    not_initiated
 revoked     approved
 pending


seal_codex=> select guid, initiator_guid, edit_initiator_guid from collaboration limit 20;
                 guid                 |            initiator_guid            |         edit_initiator_guid          
--------------------------------------+--------------------------------------+--------------------------------------
 ae4f5280-e16e-4951-874b-9a3723d92649 |                                      | 
 1ebc6a41-63a2-4bc8-9b59-cd8a182b45d1 |                                      | 
 fb959ecd-eec5-45ea-88b1-765298107811 |                                      | 
 222af3cd-fd97-4e54-8e6d-645370450ac5 |                                      | 
 79a2310a-32f3-45f5-946c-8d7111ee2243 |                                      | 
 6f343622-f058-4f72-9df0-7e415405a7c7 |                                      | 
 fb780ee5-01f1-4fe7-83bb-27ae4078f37f | 46b609dc-7d0d-41fb-b73b-783ab52aa146 | 
 1673aa79-8b62-43a6-bedf-1c2b55d90c9a | 58003dde-a6bb-47cf-8880-6b34d41633a9 | 
 38ebe266-5786-4660-b711-089043039adf | 46b609dc-7d0d-41fb-b73b-783ab52aa146 | 46b609dc-7d0d-41fb-b73b-783ab52aa146


 approved
 initialized
 edit_pending
 edit
 rejected


wildbook=> select * from "COLLABORATIONS" ;
                   ID                    | DATETIMECREATED |    STATE     |      USERNAME1       |         USERNAME2          |    EDITINITIATOR     
-----------------------------------------+-----------------+--------------+----------------------+----------------------------+----------------------
 hpastrp:laura jim                       |   1614126385095 | initialized  | hpastrp              | laura jim                  | hpastrp
 colinwkingen:public                     |   1614880080726 | approved     | colinwkingen         | public                     | colinwkingen
 E.Germanov:unud                         |   1615048167822 | initialized  | E.Germanov           | unud                       | E.Germanov
 E.Germanov:Indo Ocean Project           |   1648799301591 | edit         | Indo Ocean Project   | E.Germanov                 | E.Germanov


*/

private static void migrateCollaborations(JspWriter out, Shepherd myShepherd, Connection conn, HttpServletRequest request) throws SQLException, IOException {
    out.println("<h2>Collaborations</h2><ol>");
    Statement st = conn.createStatement();
    Statement st2 = conn.createStatement();
    ResultSet res = st.executeQuery("SELECT * FROM collaboration ORDER BY guid");
    int ct = 0;

    while (res.next()) {
        ct++;
        String guid = res.getString("guid");
        String initiatorGuid = res.getString("initiator_guid");
        User initUser = myShepherd.getUserByUUID(initiatorGuid);
        String editInitiatorGuid = res.getString("edit_initiator_guid");
        User editUser = myShepherd.getUserByUUID(editInitiatorGuid);
        out.println("<li>[" + guid + "] init=" + initUser + "; edit=" + editUser + "<ul>");
        String state = "initialized";
        ResultSet res2 = st2.executeQuery("SELECT * FROM collaboration_user_associations WHERE collaboration_guid='" + guid + "' ORDER BY updated");
        String[] reads = new String[2];
        String[] edits = new String[2];
        User[] users = new User[2];
        int offset = 0;
        while (res2.next()) {
            String userId = res2.getString("user_guid");
            User user = myShepherd.getUserByUUID(userId);
            if (user == null) {
                out.println("<li><b>UNKNOWN USER ID " + userId + "</b></li>");
                continue;
            }
            users[offset] = user;
            String readState = res2.getString("read_approval_state");
            reads[offset] = readState;
            String editState = res2.getString("edit_approval_state");
            edits[offset] = editState;
            out.println("<li><b>" + user + "</b>:<br />read=" + readState + "; edit=" + editState + "</li>");
            offset++;
        }
        if (reads[0].equals("approved") && reads[1].equals("approved")) state = "approved";
        if (edits[0].equals("approved") && edits[1].equals("approved")) state = "edit";
        if (reads[0].equals("revoked") || reads[1].equals("revoked")) state = "rejected";
        out.println("</ul>");

        Timestamp ts = res.getTimestamp("created");
        Collaboration collab = Collaboration.create(users[0].getUsername(), users[1].getUsername());
        if (ts != null) collab.setDateTimeCreated(ts.getTime());
        collab.setState(state);
        if (editUser != null) collab.setEditInitiator(editUser.getUsername());
        myShepherd.storeNewCollaboration(collab);
        out.println("<span style=\"color: blue\">STATE: " + state + " (" + ts + ")</span> => " + collab + "</li>");
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

migrateCollaborations(out, myShepherd, conn, request);

migrateMediaAssets(out, myShepherd, conn, request, new File(dataDir, assetGroupDir));

migrateAnnotations(out, myShepherd, conn);

migrateEncounters(out, myShepherd, conn);

migrateOccurrences(out, myShepherd, conn);

migrateMarkedIndividuals(out, myShepherd, conn);

migrateKeywords(out, myShepherd, conn);

migrateRelationships(out, myShepherd, conn);

fixAutogenNames(out, myShepherd, conn);

migrateSocialGroups(out, myShepherd, conn);

///migratePendingSightings(out, myShepherd, conn, request);

*/


myShepherd.commitDBTransaction();
myShepherd.closeDBTransaction();

%>



