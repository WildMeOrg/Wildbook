// basic bulk import utilities
package org.ecocean.api.bulk;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.ecocean.api.ApiException;
import org.ecocean.Base;
import org.ecocean.OpenSearch;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.Util;
import org.json.JSONArray;
import org.json.JSONObject;

public class BulkImportUtil {
    public static Map<String, Object> validateRow(Set<String> fieldNames, JSONArray rowValues,
        Shepherd myShepherd) {
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
        return validateRow(row, myShepherd);
    }

    public static Map<String, Object> validateRow(JSONObject row, Shepherd myShepherd) {
        Map<String, Object> rtn = new HashMap<String, Object>();

        if (row == null) return rtn;
        for (String fieldName : row.keySet()) {
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
        // now we do inter-dependent validations
        checkYMD(rtn, "Encounter.year", "Encounter.month", "Encounter.day");
        checkYMD(rtn, "Sighting.year", "Sighting.month", "Sighting.day");
        for (String reqFieldName : BulkValidator.FIELD_NAMES_REQUIRED) {
            if (!rtn.containsKey(reqFieldName)) {
                rtn.put(reqFieldName,
                    new BulkValidatorException("required value",
                    ApiException.ERROR_RETURN_CODE_REQUIRED));
            } else if (rtn.get(reqFieldName) instanceof BulkValidator) {
                BulkValidator bv = (BulkValidator)rtn.get(reqFieldName);
                // has a bv, but value cannot be null
                if (bv.getValue() == null) {
                    rtn.put(reqFieldName,
                        new BulkValidatorException("required value",
                        ApiException.ERROR_RETURN_CODE_REQUIRED));
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
        return rtn;
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
                ApiException.ERROR_RETURN_CODE_REQUIRED));
    }

    private static void checkLatLon(Map<String, Object> map, String latKey, String lonKey) {
        Object dlat = getValue(map, latKey);
        Object dlon = getValue(map, lonKey);

        if ((dlat == null) && (dlon != null))
            map.put(latKey,
                new BulkValidatorException("must supply both latitude and longitude",
                ApiException.ERROR_RETURN_CODE_REQUIRED));
        if ((dlat != null) && (dlon == null))
            map.put(lonKey,
                new BulkValidatorException("must supply both latitude and longitude",
                ApiException.ERROR_RETURN_CODE_REQUIRED));
    }

    // this is just a helper function for validateRow
    private static Object getValue(Map<String, Object> map, String fieldName) {
        if (!map.containsKey(fieldName)) return null;
        if (map.get(fieldName) instanceof Exception) return null;
        BulkValidator bv = (BulkValidator)map.get(fieldName);
        return bv.getValue();
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
        if (objs == null) return;
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
    }
}
