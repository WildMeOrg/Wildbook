// basic bulk import utilities
package org.ecocean.api.bulk;

import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import org.ecocean.api.ApiException;
import javax.jdo.Query;
import org.ecocean.Base;
import org.ecocean.Encounter;
import org.ecocean.CommonConfiguration;
import org.ecocean.IAJsonProperties;
import org.ecocean.Taxonomy;
import org.ecocean.OpenSearch;
import org.ecocean.Project;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.Util;
import org.json.JSONArray;
import org.json.JSONObject;

public class BulkImportUtil {
    // cache these so we dont have to keep reading them from commonConfig
    public static List<String> measurementValues = null;
    public static List<String> measurementUnits = null;
    public static Map<String, Set<String> > labeledKeywordMap = null;

    public static Map<String, Object> validateRow(Set<String> fieldNames, JSONArray rowValues,
        Shepherd myShepherd) {
        return validateRow(fieldNames, rowValues, myShepherd, false);
    }

    // bulk-import boxes (decision 3): identificationSkipped widens the accepted box classes from
    // the species' matchable list to every stored class
    public static Map<String, Object> validateRow(Set<String> fieldNames, JSONArray rowValues,
        Shepherd myShepherd, boolean identificationSkipped) {
        if ((fieldNames == null) || (rowValues == null)) return new HashMap<String, Object>();
        return validateRow(rowFromPositional(fieldNames, rowValues), myShepherd,
                identificationSkipped);
    }

    // a positional row as an object row keyed by fieldNames; missing trailing values are JSON null
    public static JSONObject rowFromPositional(Set<String> fieldNames, JSONArray rowValues) {
        JSONObject row = new JSONObject();
        int i = 0;
        for (String fieldName : fieldNames) {
            if (i < rowValues.length()) {
                row.put(fieldName, rowValues.get(i));
            } else {
                row.put(fieldName, JSONObject.NULL);
            }
            i++;
        }
        return row;
    }

    public static Map<String, Object> validateRow(JSONObject row, Shepherd myShepherd) {
        return validateRow(row, myShepherd, false);
    }

    public static Map<String, Object> validateRow(JSONObject row, Shepherd myShepherd,
        boolean identificationSkipped) {
        Map<String, Object> rtn = validateFields(row, myShepherd);

        if (row == null) return rtn;
        validateDependent(rtn, row.keySet(), myShepherd, identificationSkipped);
        return rtn;
    }

    // the per-field half of row validation: synonym columns and each field's own value check.
    // NOTE removes synonym columns from row (row.keySet() is a live view), as validateRow always has
    public static Map<String, Object> validateFields(JSONObject row, Shepherd myShepherd) {
        Map<String, Object> rtn = new HashMap<String, Object>();

        if (row == null) return rtn;
        Set<String> fieldNames = row.keySet();
        List<List<String> > syn = BulkValidator.findSynonyms(fieldNames);
        // we set errors for fields which are duplicates, and remove from fieldNames (for below)
        if (syn != null) {
            for (List<String> syns : syn) {
                BulkValidatorException bv = synonymException(syns, null);
                for (String dup : syns) {
                    rtn.put(dup, bv);
                    fieldNames.remove(dup);
                }
            }
        }
        for (String fieldName : fieldNames) {
            try {
                // FIXME -- how do we handle get() and type returned? TBD
                if (row.isNull(fieldName)) { // want to pass java null here instead of org.json.NULL
                    rtn.put(fieldName, new BulkValidator(fieldName, null, myShepherd));
                } else {
                    rtn.put(fieldName,
                        new BulkValidator(fieldName, row.get(fieldName), myShepherd));
                }
                // lets just put *any* exception for now?
                // } catch (BulkValidatorException ex) {
            } catch (Exception ex) {
                rtn.put(fieldName, ex);
            }
        }
        return rtn;
    }

    // the error for synonym columns found together on one row; fieldName may be null
    public static BulkValidatorException synonymException(List<String> syns, String fieldName) {
        return new BulkValidatorException("synonym columns: " + String.join(", ", syns),
                ApiException.ERROR_RETURN_CODE_INVALID, BulkValidatorException.TYPE_INVALID_SYNONYM,
                fieldName);
    }

    // the cross-field half of row validation, run over validateFields() results. A dependent
    // error never overwrites an error the field already has (putIfClean).
    public static void validateDependent(Map<String, Object> rtn, Set<String> fieldNames,
        Shepherd myShepherd, boolean identificationSkipped) {
        // try to make sure we set date *somehow*; labeled keyword columns never count
        String hasDateMillis = null;
        String hasDateYear = null;
        for (String fieldName : fieldNames) {
            if (getValue(rtn, fieldName) == null) continue;
            if (BulkValidator.DATE_MILLIS_FIELDS.contains(fieldName)) hasDateMillis = fieldName;
            if (fieldName.equals("Encounter.year") || fieldName.equals("Sighting.year"))
                hasDateYear = fieldName;
        }
        // now we do inter-dependent validations
        checkYMD(rtn, "Encounter.year", "Encounter.month", "Encounter.day");
        checkYMD(rtn, "Sighting.year", "Sighting.month", "Sighting.day");
        // (only) one of these is required, so cannot add both to FIELD_NAMES_REQUIRED
        // we set Encounter.year as the field we complain about (replacing an invalid year value:
        // a year that did not validate is the same as no date)
        if ((hasDateMillis == null) && (hasDateYear == null))
            rtn.put("Encounter.year",
                new BulkValidatorException("required value (year or millis)",
                ApiException.ERROR_RETURN_CODE_REQUIRED,
                BulkValidatorException.TYPE_REQUIRED_VALUE));
        // case where we have both, we complain about the fields we actually had set
        if ((hasDateMillis != null) && (hasDateYear != null)) {
            putIfClean(rtn, hasDateMillis,
                new BulkValidatorException("cannot have both date year and millis",
                ApiException.ERROR_RETURN_CODE_INVALID));
            putIfClean(rtn, hasDateYear,
                new BulkValidatorException("cannot have both date year and millis",
                ApiException.ERROR_RETURN_CODE_INVALID));
        }
        for (String reqFieldName : BulkValidator.FIELD_NAMES_REQUIRED) {
            if (!rtn.containsKey(reqFieldName)) {
                putIfClean(rtn, reqFieldName,
                    new BulkValidatorException("required value",
                    ApiException.ERROR_RETURN_CODE_REQUIRED,
                    BulkValidatorException.TYPE_REQUIRED_VALUE));
            } else if (rtn.get(reqFieldName) instanceof BulkValidator) {
                BulkValidator bv = (BulkValidator)rtn.get(reqFieldName);
                // has a bv, but value cannot be null
                if (bv.getValue() == null) {
                    putIfClean(rtn, reqFieldName,
                        new BulkValidatorException("required value",
                        ApiException.ERROR_RETURN_CODE_REQUIRED,
                        BulkValidatorException.TYPE_REQUIRED_VALUE));
                }
            }
        }
        // why do we have THREE different ways for users to provide same fields? :(
        checkLatLon(rtn, "Encounter.latitude", "Encounter.longitude");
        checkLatLon(rtn, "Encounter.decimalLatitude", "Encounter.decimalLongitude");
        checkLatLon(rtn, "Sighting.decimalLatitude", "Sighting.decimalLongitude");

        Object taxG = getValue(rtn, "Encounter.genus");
        Object taxS = getValue(rtn, "Encounter.specificEpithet");
        if ((taxG != null) && (taxS != null)) {
            String sciName = Util.taxonomyString(taxG.toString(), taxS.toString());
            if (!myShepherd.isValidTaxonomyName(sciName)) {
                putIfClean(rtn, "Encounter.genus",
                    new BulkValidatorException("invalid taxonomy value",
                    ApiException.ERROR_RETURN_CODE_INVALID));
                putIfClean(rtn, "Encounter.specificEpithet",
                    new BulkValidatorException("invalid taxonomy value",
                    ApiException.ERROR_RETURN_CODE_INVALID));
            }
        }

        // 1314 introduces catching incomplete project name/prefix combos
        List<String> projectPrefixes = findIndexedFieldNames(fieldNames, "Encounter.project.projectIdPrefix");
        List<String> projectNames = findIndexedFieldNames(fieldNames, "Encounter.project.researchProjectName");
        // we use the bigger of these
        int pmax = Integer.max(projectPrefixes.size(), projectNames.size());
        for (int i = 0; i < pmax; i++) {
            String pnameKey = "Encounter.project" + i + ".researchProjectName";
            String pprefixKey = "Encounter.project" + i + ".projectIdPrefix";
            Object pname = getValue(rtn, pnameKey);
            Object pprefix = getValue(rtn, pprefixKey);
            if ((pname == null) && (pprefix == null)) continue;
            if ((pname != null) && (pprefix != null)) continue;
            if (pname == null)
                putIfClean(rtn, pnameKey,
                    new BulkValidatorException("must have researchProjectName if given projectIdPrefix",
                    ApiException.ERROR_RETURN_CODE_REQUIRED));
            if (pprefix == null)
                putIfClean(rtn, pprefixKey,
                    new BulkValidatorException("must have projectIdPrefix if given researchProjectName",
                    ApiException.ERROR_RETURN_CODE_REQUIRED));
        }

        // bulk-import boxes (spec 4.2 row layer): per-slot pairing, class, duplicate photos
        checkBoxSlots(rtn, fieldNames, identificationSkipped);
    }

    /**
     * Bulk-import boxes (spec 4.2, decisions 2 and 3), per photo slot N of this row: (1) any of
     * .iaClass/.viewpoint/.theta/.bboxSource without a .bbox column or value is REQUIRED on that
     * field (never on a .bbox column the sheet does not have: the review table shows errors by
     * column, and a fabricated .bbox key would switch the importer to exact-slot mode); (2) a
     * .bbox on a slot with no photo (absent or blank cell) is INVALID on .bbox; (3) a .bbox
     * without a .iaClass is REQUIRED on .iaClass when that column exists, else on .bbox; (4) when
     * the taxonomy validated, a class outside the species' matchable list (every stored class when
     * identification is skipped) is INVALID on .iaClass, naming the accepted classes; (5) the same
     * photo in two slots of the row, when any of those slots is boxed, is INVALID_DUPLICATE on the
     * later slot. A rejected .bbox value counts as a boxed slot and gets no pairing errors piled on;
     * an exception already on a field is never overwritten. The class lists are read only when a
     * boxed slot needs them.
     */
    private static void checkBoxSlots(Map<String, Object> rtn, Set<String> fieldNames,
        boolean identificationSkipped) {
        List<String> bareSlots = findIndexedFieldNames(fieldNames, "Encounter.mediaAsset");
        Map<String, List<String> > boxFields = boxFieldNames(fieldNames);
        int numSlots = bareSlots.size();
        for (List<String> names : boxFields.values()) numSlots = Math.max(numSlots, names.size());
        if (numSlots == 0) return;
        Object taxG = getValue(rtn, "Encounter.genus");
        Object taxS = getValue(rtn, "Encounter.specificEpithet");
        String sciName = ((taxG != null) && (taxS != null)) ?
            Util.taxonomyString(taxG.toString(), taxS.toString()) : null;
        List<String> allowed = null; // resolved lazily, only when a boxed slot has a class to check
        Map<String, int[]> seenPhotos = new HashMap<String, int[]>(); // filename -> {first slot, any boxed}
        for (int n = 0; n < numSlots; n++) {
            String bboxName = boxFieldName(boxFields, "bbox", n);
            String classNm = boxFieldName(boxFields, "iaClass", n);
            String bareName = (n < bareSlots.size()) ? bareSlots.get(n) : null;
            String photo = null;
            if (bareName != null) {
                Object bare = getValue(rtn, bareName);
                if ((bare != null) && !bare.toString().trim().isEmpty()) photo = bare.toString();
            }
            Object bbox = getValue(rtn, bboxName);
            boolean boxed = (bbox != null) || isException(rtn, bboxName);
            if (bbox == null) {
                if (!boxed) {
                    for (String suffix : BulkValidator.BOX_SUFFIXES) {
                        if (suffix.equals("bbox")) continue;
                        String name = boxFieldName(boxFields, suffix, n);
                        if (getValue(rtn, name) != null)
                            putIfClean(rtn, name,
                                new BulkValidatorException(name + " needs a box in " + bboxName,
                                ApiException.ERROR_RETURN_CODE_REQUIRED,
                                BulkValidatorException.TYPE_REQUIRED_VALUE, name));
                    }
                }
            } else {
                if (photo == null) {
                    putIfClean(rtn, bboxName,
                        new BulkValidatorException("box without a photo in this slot",
                        ApiException.ERROR_RETURN_CODE_INVALID,
                        BulkValidatorException.TYPE_INVALID_VALUE, bboxName));
                }
                Object cls = getValue(rtn, classNm);
                if (cls == null) {
                    String where = rtn.containsKey(classNm) ? classNm : bboxName;
                    putIfClean(rtn, where,
                        new BulkValidatorException("a box needs an iaClass in " + classNm,
                        ApiException.ERROR_RETURN_CODE_REQUIRED,
                        BulkValidatorException.TYPE_REQUIRED_VALUE, where));
                } else if (sciName != null) {
                    if (allowed == null) {
                        Taxonomy taxy = new Taxonomy(sciName);
                        IAJsonProperties iac = IAJsonProperties.iaConfig();
                        allowed = identificationSkipped ? iac.getCanonicalIAClasses(taxy) :
                            iac.getMatchableIAClasses(taxy);
                    }
                    if (!allowed.contains(cls.toString())) {
                        String msg = allowed.isEmpty() ? ("no matchable classes for " + sciName) :
                            ("valid classes for " + sciName + ": " + String.join(", ", allowed));
                        putIfClean(rtn, classNm,
                            new BulkValidatorException("invalid iaClass '" + cls + "'; " + msg,
                            ApiException.ERROR_RETURN_CODE_INVALID,
                            BulkValidatorException.TYPE_INVALID_VALUE, classNm));
                    }
                }
            }
            if (photo == null) continue;
            int[] seen = seenPhotos.get(photo);
            if (seen == null) {
                seenPhotos.put(photo, new int[] { n, boxed ? 1 : 0 });
            } else {
                if ((seen[1] == 1) || boxed) {
                    putIfClean(rtn, bareName,
                        new BulkValidatorException("photo " + photo + " is already in slot " +
                        seen[0] + " of this row; a second animal on the same photo needs its own row",
                        ApiException.ERROR_RETURN_CODE_INVALID,
                        BulkValidatorException.TYPE_INVALID_DUPLICATE, bareName));
                }
                if (boxed) seen[1] = 1;
            }
        }
    }

    /**
     * A row's box columns by suffix (BulkValidator.BOX_SUFFIXES), each list indexed by slot and
     * holding the column's real name. An index may be written zero-padded
     * ("Encounter.mediaAsset01.bbox"), so a slot's column is always found by its number here and
     * never by rebuilding the name.
     */
    public static Map<String, List<String> > boxFieldNames(Set<String> fieldNames) {
        Map<String, List<String> > rtn = new HashMap<String, List<String> >();
        for (String suffix : BulkValidator.BOX_SUFFIXES) {
            rtn.put(suffix, findIndexedFieldNames(fieldNames, "Encounter.mediaAsset." + suffix));
        }
        return rtn;
    }

    /** The real name of a slot's box column; the canonical name when the row has no such column. */
    public static String boxFieldName(Map<String, List<String> > boxFields, String suffix,
        int slot) {
        List<String> names = boxFields.get(suffix);
        if ((names != null) && (slot < names.size()) && (names.get(slot) != null))
            return names.get(slot);
        return "Encounter.mediaAsset" + slot + "." + suffix;
    }

    /** The slot a box column belongs to ("Encounter.mediaAsset01.theta", "theta" is 1); -1 for any other field. */
    public static int boxSlot(String fieldName, String suffix) {
        try {
            if (!("Encounter.mediaAsset." + suffix).equals(BulkValidator.indexPrefixValue(fieldName)))
                return -1;
            return BulkValidator.indexIntValue(fieldName);
        } catch (BulkValidatorException ex) {
            return -1;
        }
    }

    /**
     * Two columns that are the same numbered column under different spellings
     * ("Encounter.mediaAsset01" and "Encounter.mediaAsset1"): only one of them could be read, so
     * the pair is reported instead. Returns the first such pair, or null.
     */
    public static String[] findIndexAliases(Set<String> fieldNames) {
        Map<String, String> seen = new HashMap<String, String>();
        for (String fn : fieldNames) {
            Slot slot = slotOf(fn);
            if (slot == null) continue;
            String other = seen.put(slot.prefix + "#" + slot.index, fn);
            if (other != null) return new String[] { other, fn };
        }
        return null;
    }

    /** A numbered column's index prefix and index, however the index is spelled. */
    public static final class Slot {
        public final String prefix;
        public final int index;

        Slot(String prefix, int index) {
            this.prefix = prefix;
            this.index = index;
        }
    }

    /**
     * The numbered column fn is ("Encounter.mediaAsset01.bbox" is Encounter.mediaAsset.bbox, 1);
     * a slot's labeled keyword ("Encounter.mediaAsset3.<label>") is Encounter.mediaAsset.<label>
     * at its offset. Null for an unknown name, an index above MAX_INDEX, or a field that is not
     * numbered.
     */
    public static Slot slotOf(String fn) {
        try {
            String prefix = BulkValidator.indexPrefixValue(fn);
            int index = (prefix == null) ? -1 : BulkValidator.indexIntValue(fn);
            if (index < 0) {   // not a numbered column, unless it is a slot's labeled keyword
                prefix = getLabeledKeywordLabel(fn);
                index = (prefix == null) ? -1 : getLabeledKeywordOffset(fn);
                if (index < 0) return null;
                prefix = "Encounter.mediaAsset." + prefix;
            }
            return new Slot(prefix, index);
        } catch (BulkValidatorException ex) {
            return null;
        }
    }

    // true when any of the columns is an Encounter.mediaAsset{N}.bbox box column
    public static boolean hasBboxColumn(java.util.Collection<String> fieldNames) {
        if (fieldNames == null) return false;
        // findIndexedFieldNames swallows the unknown-field exception a tolerated column raises
        for (String name : findIndexedFieldNames(new HashSet<String>(fieldNames),
            "Encounter.mediaAsset.bbox")) {
            if (name != null) return true;
        }
        return false;
    }

    // sets a validation exception unless the field already holds one
    public static void putIfClean(Map<String, Object> map, String fieldName,
        BulkValidatorException ex) {
        if (isException(map, fieldName)) return;
        map.put(fieldName, ex);
    }

    private static void checkYMD(Map<String, Object> map, String ykey, String mkey, String dkey) {
        Object dateY = getValue(map, ykey);
        Object dateM = getValue(map, mkey);
        Object dateD = getValue(map, dkey);

        if ((dateY != null) && (dateM != null) && (dateD != null) &&
            ((Integer)dateY > Year.MAX_VALUE)) {
            // java.time cannot hold it: rejected here, on the year, rather than thrown below
            putIfClean(map, ykey, new BulkValidatorException("year value too large",
                ApiException.ERROR_RETURN_CODE_INVALID, BulkValidatorException.TYPE_INVALID_VALUE,
                ykey));
        } else if ((dateY != null) && (dateM != null) && (dateD != null)) {
            YearMonth yearMonth = YearMonth.of((Integer)dateY, (Integer)dateM);
            if (!yearMonth.isValidDay((Integer)dateD))
                putIfClean(map, dkey,
                    new BulkValidatorException("day is out of range for month",
                    ApiException.ERROR_RETURN_CODE_INVALID));
        }
        if ((dateM == null) && (dateD != null))
            putIfClean(map, mkey,
                new BulkValidatorException("must supply a valid month along with day",
                ApiException.ERROR_RETURN_CODE_REQUIRED,
                BulkValidatorException.TYPE_REQUIRED_VALUE));
        if (Util.dateIsInFuture((Integer)dateY, (Integer)dateM, (Integer)dateD))
            putIfClean(map, ykey,
                new BulkValidatorException("date is in the future",
                ApiException.ERROR_RETURN_CODE_INVALID));
    }

    private static void checkLatLon(Map<String, Object> map, String latKey, String lonKey) {
        // exceptions for one of this is fine; use that
        if (isException(map, latKey) || isException(map, lonKey)) return;
        Object dlat = getValue(map, latKey);
        Object dlon = getValue(map, lonKey);
        if ((dlat == null) && (dlon != null))
            map.put(latKey,
                new BulkValidatorException("must supply both latitude and longitude",
                ApiException.ERROR_RETURN_CODE_REQUIRED,
                BulkValidatorException.TYPE_REQUIRED_VALUE));
        if ((dlat != null) && (dlon == null))
            map.put(lonKey,
                new BulkValidatorException("must supply both latitude and longitude",
                ApiException.ERROR_RETURN_CODE_REQUIRED,
                BulkValidatorException.TYPE_REQUIRED_VALUE));
    }

    // this is just a helper function for validateRow
    private static Object getValue(Map<String, Object> map, String fieldName) {
        if (!map.containsKey(fieldName)) return null;
        if (map.get(fieldName) instanceof Exception) return null;
        BulkValidator bv = (BulkValidator)map.get(fieldName);
        return bv.getValue();
    }

    private static boolean isException(Map<String, Object> map, String fieldName) {
        if (!map.containsKey(fieldName)) return false;
        return (map.get(fieldName) instanceof Exception);
    }

    // pass a list of fieldnames and something a prefix for an indexed filename (e.g. "Encounter.mediaAsset"
    // and get back a list of the fieldnames that share that prefix
    // note list will be in numerical order but have nulls where fieldnames were missing
    public static List<String> findIndexedFieldNames(Set<String> fieldNames,
        String fieldNamePrefix) {
        List<String> rtn = new ArrayList<String>();

        for (String fn : fieldNames) {
            int index = -999;
            String ipv = null;
            try {
                ipv = BulkValidator.indexPrefixValue(fn);
                if ((ipv == null) || !ipv.equals(fieldNamePrefix)) continue;
                index = BulkValidator.indexIntValue(fn);
            } catch (BulkValidatorException bve) {}
            if (index < 0) continue;
            while (rtn.size() <= index) rtn.add(null);
            rtn.set(index, fn);
        }
        return rtn;
    }

    /**
     * The existing project a bulk-import project prefix names, looked up as BulkImporter does: the
     * exact prefix first, then a project whose prefix starts with it (case-insensitive). Null when
     * neither finds one, in which case the importer would have to create the project.
     */
    static Project findExistingProject(String prefix, Shepherd sh) {
        Project proj = sh.getProjectByProjectIdPrefix(prefix);
        if (proj == null) proj = sh.getProjectByProjectIdPrefixPrefix(prefix);
        return proj;
    }

    static final int ID_LOOKUP_CHUNK = 1000;

    /**
     * The given lowercase encounter IDs that already exist in the database, compared
     * case-insensitively against the stored catalogNumber and returned lowercase. Queried in
     * chunks of 1000. Throws when the database cannot be read (the caller reports it).
     * The lookup runs in its own short-lived Shepherd and transaction for requestShepherd's
     * context, so a failed query (on Postgres, one that aborts its transaction) cannot leave the
     * request's transaction unusable; requestShepherd itself is never queried.
     */
    public static Set<String> encounterIdsInUse(Collection<String> lowercaseIds,
        Shepherd requestShepherd)
    throws Exception {
        Shepherd idShepherd = new Shepherd(requestShepherd.getContext());
        idShepherd.setAction("BulkImportUtil.encounterIdsInUse");
        try {
            idShepherd.beginDBTransaction();
            return queryEncounterIdsInUse(lowercaseIds, idShepherd);
        } finally {
            idShepherd.rollbackAndClose();
        }
    }

    // encounterIdsInUse's query, on the given Shepherd's open transaction
    static Set<String> queryEncounterIdsInUse(Collection<String> lowercaseIds, Shepherd sh)
    throws Exception {
        Set<String> found = new HashSet<String>();
        List<String> all = new ArrayList<String>(new LinkedHashSet<String>(lowercaseIds));
        for (int i = 0; i < all.size(); i += ID_LOOKUP_CHUNK) {
            List<String> chunk = new ArrayList<String>(all.subList(i,
                Math.min(all.size(), i + ID_LOOKUP_CHUNK)));
            Query query = sh.getPM().newQuery(Encounter.class);
            try {
                query.setFilter(":ids.contains(this.catalogNumber.toLowerCase())");
                query.setResult("catalogNumber");
                for (Object o : (Collection)query.execute(chunk)) {
                    if (o != null) found.add(o.toString().toLowerCase());
                }
            } finally {
                query.closeAll();
            }
        }
        return found;
    }

    // possibly could be moved to OpenSearch or something, but lets test in bulk context first
    // this (intentionally) does not use IndexManager queues as we assume these are newly created
    // and dont need to be done deeply
    public static void bulkOpensearchIndex(final List<Base> objs) {
        if (Util.collectionIsEmptyOrNull(objs)) return;
        Integer numThreads = (Integer)OpenSearch.getConfigurationValue("indexingNumAllowedThreads",
            4);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        Runnable rn = new Runnable() {
            public void run() {
                Shepherd bgShepherd = new Shepherd("context0");
                bgShepherd.setAction("bulkOpensearchIndex");
                bgShepherd.beginDBTransaction();
                int ct = 0;
                for (Base obj : objs) {
                    ct++;
                    String id = obj.getId();
                    // we cant use the obj directly, as its shepherd likely closed,
                    // so we hackily reload under our shepherd
                    try {
                        Class myClass = obj.getClass();
                        Base base = (Base)bgShepherd.getPM().getObjectById(myClass, id);
                        System.out.println("bulkOpensearchIndex[" + ct + "/" + objs.size() + "]: " +
                            base);
                        base.opensearchIndex();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                bgShepherd.rollbackAndClose();
            }
        };
        executor.execute(rn);
        // let the submitted task finish, then terminate the worker thread;
        // without this every call leaks an idle non-daemon thread for the JVM lifetime
        executor.shutdown();
    }

    public static String bulkImportArchiveFilepath(String id, String suffix) {
        return "/tmp/bulkImportArchive_" + id + "_" + suffix + "_" + System.currentTimeMillis() +
                   ".json";
    }

    public static List<String> getMeasurementValues() {
        if (measurementValues != null) return measurementValues;
        measurementValues = new ArrayList<String>();
        try {
            measurementValues = (List<String>)CommonConfiguration.getIndexedPropertyValues(
                "measurement", "context0");
        } catch (Exception ex) {}
        return measurementValues;
    }

    public static List<String> getMeasurementUnits() {
        if (measurementUnits != null) return measurementUnits;
        measurementUnits = new ArrayList<String>();
        try {
            measurementUnits = (List<String>)CommonConfiguration.getIndexedPropertyValues(
                "measurementUnits", "context0");
        } catch (Exception ex) {}
        return measurementUnits;
    }

    public static int findMeasurementOffset(String fieldName) {
        if (fieldName == null) return -1;
        int offset = 0;
        for (String mval : getMeasurementValues()) {
            if (fieldName.equals("Encounter.measurement" + offset)) return offset;
            if (fieldName.equals("Encounter." + mval)) return offset;
            if (fieldName.equals("Encounter.measurement." + mval)) return offset;
            offset++;
        }
        return -1;
    }

    public static int findMeasurementSamplingProtocolOffset(String fieldName) {
        if (fieldName == null) return -1;
        int offset = 0;
        for (String mval : getMeasurementValues()) {
            if (fieldName.equals("Encounter.measurement" + offset + ".samplingProtocol"))
                return offset;
            if (fieldName.equals("Encounter." + mval + ".samplingProtocol")) return offset;
            if (fieldName.equals("Encounter.measurement." + mval + ".samplingProtocol"))
                return offset;
            offset++;
        }
        return -1;
    }

    // like findIndexedFieldNames() but for measurement chaos
    public static List<String> findMeasurementFieldNames(Set<String> fieldNames) {
        List<String> rtn = new ArrayList<String>();

        for (String fn : fieldNames) {
            int offset = findMeasurementOffset(fn);
            if (offset < 0) continue;
            while (rtn.size() <= offset) rtn.add(null);
            rtn.set(offset, fn);
        }
        return rtn;
    }

    public static List<String> findMeasurementSamplingProtocolFieldNames(Set<String> fieldNames) {
        List<String> rtn = new ArrayList<String>();

        for (String fn : fieldNames) {
            int offset = findMeasurementSamplingProtocolOffset(fn);
            if (offset < 0) continue;
            while (rtn.size() <= offset) rtn.add(null);
            rtn.set(offset, fn);
        }
        return rtn;
    }

    public static Map<String, Set<String> > getLabeledKeywordMap() {
        if (labeledKeywordMap != null) return labeledKeywordMap;
        labeledKeywordMap = new LinkedHashMap<String, Set<String> >();
        try {
            for (String label : CommonConfiguration.getIndexedPropertyValues("kwLabel",
                "context0")) {
                // bulk-import boxes (spec 4.1): the box column suffixes are reserved
                if (BulkValidator.BOX_SUFFIXES.contains(label)) {
                    System.out.println("WARN: BulkImportUtil labeled keyword '" + label +
                        "' is a reserved bulk-import suffix; ignored");
                    continue;
                }
                labeledKeywordMap.put(label,
                    new LinkedHashSet<String>(CommonConfiguration.getIndexedPropertyValues(label,
                        "context0")));
            }
        } catch (Exception ex) {}
        return labeledKeywordMap;
    }

    public static boolean isValidLabeledKeywordValue(String label, String value) {
        if (!getLabeledKeywordMap().containsKey(label)) return false;
        return getLabeledKeywordMap().get(label).contains(value);
    }

    public static String getLabeledKeywordLabel(String fieldName) {
        if ((fieldName == null) || !fieldName.startsWith("Encounter.mediaAsset")) return null;
        String regexPrefix = "^Encounter\\.mediaAsset(\\d+)\\.";
        for (String label : getLabeledKeywordMap().keySet()) {
            Pattern p = Pattern.compile(regexPrefix + label + "$");
            Matcher m = p.matcher(fieldName);
            if (m.find()) return label;
        }
        return null;
    }

    public static int getLabeledKeywordOffset(String fieldName) {
        if ((fieldName == null) || !fieldName.startsWith("Encounter.mediaAsset")) return -1;
        String regexPrefix = "^Encounter\\.mediaAsset(\\d+)\\.";
        for (String label : getLabeledKeywordMap().keySet()) {
            Pattern p = Pattern.compile(regexPrefix + label + "$");
            Matcher m = p.matcher(fieldName);
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (Exception ex) {}
                return -3;
            }
        }
        return -2;
    }
}
