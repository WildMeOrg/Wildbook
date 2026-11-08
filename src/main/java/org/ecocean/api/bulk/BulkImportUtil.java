// basic bulk import utilities
package org.ecocean.api.bulk;

import java.time.YearMonth;
import java.util.ArrayList;
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
import org.ecocean.Base;
import org.ecocean.CommonConfiguration;
import org.ecocean.IAJsonProperties;
import org.ecocean.Taxonomy;
import org.ecocean.OpenSearch;
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
        return validateRow(row, myShepherd, identificationSkipped);
    }

    public static Map<String, Object> validateRow(JSONObject row, Shepherd myShepherd) {
        return validateRow(row, myShepherd, false);
    }

    public static Map<String, Object> validateRow(JSONObject row, Shepherd myShepherd,
        boolean identificationSkipped) {
        Map<String, Object> rtn = new HashMap<String, Object>();

        if (row == null) return rtn;
        Set<String> fieldNames = row.keySet();
        List<List<String> > syn = BulkValidator.findSynonyms(fieldNames);
        // we set errors for fields which are duplicates, and remove from fieldNames (for below)
        if (syn != null) {
            for (List<String> syns : syn) {
                BulkValidatorException bv = new BulkValidatorException("synonym columns: " +
                    String.join(", ", syns), ApiException.ERROR_RETURN_CODE_INVALID,
                    BulkValidatorException.TYPE_INVALID_SYNONYM);
                for (String dup : syns) {
                    rtn.put(dup, bv);
                    fieldNames.remove(dup);
                }
            }
        }
        // try to make sure we set date *somehow*
        String hasDateMillis = null;
        String hasDateYear = null;
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
            if (fieldName.toLowerCase().contains("millis") && (getValue(rtn, fieldName) != null))
                hasDateMillis = fieldName;
            if (fieldName.endsWith(".year") && (getValue(rtn, fieldName) != null))
                hasDateYear = fieldName;
        }
        // now we do inter-dependent validations
        checkYMD(rtn, "Encounter.year", "Encounter.month", "Encounter.day");
        checkYMD(rtn, "Sighting.year", "Sighting.month", "Sighting.day");
        // (only) one of these is required, so cannot add both to FIELD_NAMES_REQUIRED
        // we set Encounter.year as the field we complain about
        if ((hasDateMillis == null) && (hasDateYear == null))
            rtn.put("Encounter.year",
                new BulkValidatorException("required value (year or millis)",
                ApiException.ERROR_RETURN_CODE_REQUIRED,
                BulkValidatorException.TYPE_REQUIRED_VALUE));
        // case where we have both, we complain about the fields we actually had set
        if ((hasDateMillis != null) && (hasDateYear != null)) {
            rtn.put(hasDateMillis,
                new BulkValidatorException("cannot have both date year and millis",
                ApiException.ERROR_RETURN_CODE_INVALID));
            rtn.put(hasDateYear,
                new BulkValidatorException("cannot have both date year and millis",
                ApiException.ERROR_RETURN_CODE_INVALID));
        }
        for (String reqFieldName : BulkValidator.FIELD_NAMES_REQUIRED) {
            if (!rtn.containsKey(reqFieldName)) {
                rtn.put(reqFieldName,
                    new BulkValidatorException("required value",
                    ApiException.ERROR_RETURN_CODE_REQUIRED,
                    BulkValidatorException.TYPE_REQUIRED_VALUE));
            } else if (rtn.get(reqFieldName) instanceof BulkValidator) {
                BulkValidator bv = (BulkValidator)rtn.get(reqFieldName);
                // has a bv, but value cannot be null
                if (bv.getValue() == null) {
                    rtn.put(reqFieldName,
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
                rtn.put("Encounter.genus",
                    new BulkValidatorException("invalid taxonomy value",
                    ApiException.ERROR_RETURN_CODE_INVALID));
                rtn.put("Encounter.specificEpithet",
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
                rtn.put(pnameKey,
                    new BulkValidatorException("must have researchProjectName if given projectIdPrefix",
                    ApiException.ERROR_RETURN_CODE_REQUIRED));
            if (pprefix == null)
                rtn.put(pprefixKey,
                    new BulkValidatorException("must have projectIdPrefix if given researchProjectName",
                    ApiException.ERROR_RETURN_CODE_REQUIRED));
        }

        // bulk-import boxes (spec 4.2 row layer): per-slot pairing, class, duplicate photos
        checkBoxSlots(rtn, fieldNames, identificationSkipped);

        return rtn;
    }

    /**
     * Bulk-import boxes (spec 4.2, decisions 2 and 3), per photo slot N of this row: (1) any of
     * .iaClass/.viewpoint/.theta/.bboxSource without a .bbox is REQUIRED on .bbox; (2) a .bbox on
     * a slot with no photo (absent or blank cell) is INVALID on .bbox; (3) a .bbox without a
     * .iaClass is REQUIRED on .iaClass; (4) when the taxonomy validated, a class outside the
     * species' matchable list (every stored class when identification is skipped) is INVALID on
     * .iaClass, naming the accepted classes; (5) the same photo in two slots of the row, when
     * either slot is boxed, is INVALID_DUPLICATE on the later slot. An exception already on a
     * field is never overwritten.
     */
    private static void checkBoxSlots(Map<String, Object> rtn, Set<String> fieldNames,
        boolean identificationSkipped) {
        List<String> bareSlots = findIndexedFieldNames(fieldNames, "Encounter.mediaAsset");
        int numSlots = bareSlots.size();
        for (String suffix : BulkValidator.BOX_SUFFIXES) {
            numSlots = Math.max(numSlots,
                findIndexedFieldNames(fieldNames, "Encounter.mediaAsset." + suffix).size());
        }
        if (numSlots == 0) return;
        // the accepted classes, only when the row's taxonomy validated (rule 4)
        List<String> allowed = null;
        String sciName = null;
        Object taxG = getValue(rtn, "Encounter.genus");
        Object taxS = getValue(rtn, "Encounter.specificEpithet");
        if ((taxG != null) && (taxS != null)) {
            sciName = Util.taxonomyString(taxG.toString(), taxS.toString());
            Taxonomy taxy = new Taxonomy(sciName);
            IAJsonProperties iac = IAJsonProperties.iaConfig();
            allowed = identificationSkipped ? iac.getCanonicalIAClasses(taxy) :
                iac.getMatchableIAClasses(taxy);
        }
        Map<String, Integer> firstSlotOfPhoto = new HashMap<String, Integer>();
        boolean[] boxed = new boolean[numSlots];
        for (int n = 0; n < numSlots; n++) {
            String prefix = "Encounter.mediaAsset" + n;
            String bboxName = prefix + ".bbox";
            String classNm = prefix + ".iaClass";
            String bareName = (n < bareSlots.size()) ? bareSlots.get(n) : null;
            String photo = null;
            if (bareName != null) {
                Object bare = getValue(rtn, bareName);
                if ((bare != null) && !bare.toString().trim().isEmpty()) photo = bare.toString();
            }
            Object bbox = getValue(rtn, bboxName);
            if (bbox == null) {
                for (String suffix : BulkValidator.BOX_SUFFIXES) {
                    if (suffix.equals("bbox")) continue;
                    if (getValue(rtn, prefix + "." + suffix) != null) {
                        putIfClean(rtn, bboxName,
                            new BulkValidatorException("box columns need a box in " + bboxName,
                            ApiException.ERROR_RETURN_CODE_REQUIRED,
                            BulkValidatorException.TYPE_REQUIRED_VALUE, bboxName));
                        break;
                    }
                }
            } else {
                boxed[n] = true;
                if (photo == null) {
                    putIfClean(rtn, bboxName,
                        new BulkValidatorException("box without a photo in this slot",
                        ApiException.ERROR_RETURN_CODE_INVALID,
                        BulkValidatorException.TYPE_INVALID_VALUE, bboxName));
                }
                Object cls = getValue(rtn, classNm);
                if (cls == null) {
                    putIfClean(rtn, classNm,
                        new BulkValidatorException("a box needs an iaClass",
                        ApiException.ERROR_RETURN_CODE_REQUIRED,
                        BulkValidatorException.TYPE_REQUIRED_VALUE, classNm));
                } else if ((allowed != null) && !allowed.contains(cls.toString())) {
                    String msg = allowed.isEmpty() ? ("no matchable classes for " + sciName) :
                        ("valid classes for " + sciName + ": " + String.join(", ", allowed));
                    putIfClean(rtn, classNm,
                        new BulkValidatorException("invalid iaClass '" + cls + "'; " + msg,
                        ApiException.ERROR_RETURN_CODE_INVALID,
                        BulkValidatorException.TYPE_INVALID_VALUE, classNm));
                }
            }
            if (photo == null) continue;
            Integer first = firstSlotOfPhoto.get(photo);
            if (first == null) {
                firstSlotOfPhoto.put(photo, n);
            } else if (boxed[first] || boxed[n]) {
                putIfClean(rtn, bareName,
                    new BulkValidatorException("photo " + photo + " is already in slot " + first +
                    " of this row; a second animal on the same photo needs its own row",
                    ApiException.ERROR_RETURN_CODE_INVALID,
                    BulkValidatorException.TYPE_INVALID_DUPLICATE, bareName));
            }
        }
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

        if ((dateY != null) && (dateM != null) && (dateD != null)) {
            YearMonth yearMonth = YearMonth.of((Integer)dateY, (Integer)dateM);
            if (!yearMonth.isValidDay((Integer)dateD))
                map.put(dkey,
                    new BulkValidatorException("day is out of range for month",
                    ApiException.ERROR_RETURN_CODE_INVALID));
        }
        if ((dateM == null) && (dateD != null))
            map.put(mkey,
                new BulkValidatorException("must supply a valid month along with day",
                ApiException.ERROR_RETURN_CODE_REQUIRED,
                BulkValidatorException.TYPE_REQUIRED_VALUE));
        if (Util.dateIsInFuture((Integer)dateY, (Integer)dateM, (Integer)dateD))
            map.put(ykey,
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
