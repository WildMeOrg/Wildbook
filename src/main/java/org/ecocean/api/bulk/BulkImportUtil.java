// basic bulk import utilities
package org.ecocean.api.bulk;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.ecocean.api.ApiException;

public class BulkImportUtil {

    public static Map<String, Object> validateRow(JSONObject row) {
        Map<String, Object> rtn = new HashMap<String, Object>();
        if (row == null) return rtn;
        for (String fieldName : row.keySet()) {
            try {
                // FIXME -- how do we handle get() and type returned? TBD
                rtn.put(fieldName, new BulkValidator(fieldName, row.get(fieldName)));
            // lets just put *any* exception for now?
            //} catch (BulkValidatorException ex) {
            } catch (Exception ex) {
                rtn.put(fieldName, ex);
            }
        }

        // now we do inter-dependent validations
        Object dateY = getValue(rtn, "Encounter.year");
        Object dateM = getValue(rtn, "Encounter.month");
        Object dateD = getValue(rtn, "Encounter.day");
        if ((dateY != null) && (dateM != null) && (dateD != null)) {
            YearMonth yearMonth = YearMonth.of((Integer)dateY, (Integer)dateM);
            if (!yearMonth.isValidDay((Integer)dateD)) rtn.put("Encounter.day", new BulkValidatorException("day is out of range for month", ApiException.ERROR_RETURN_CODE_INVALID));
        }

        dateY = getValue(rtn, "Occurrence.year");
        dateM = getValue(rtn, "Occurrence.month");
        dateD = getValue(rtn, "Occurrence.day");
        if ((dateY != null) && (dateM != null) && (dateD != null)) {
            YearMonth yearMonth = YearMonth.of((Integer)dateY, (Integer)dateM);
            if (!yearMonth.isValidDay((Integer)dateD)) rtn.put("Occurrence.day", new BulkValidatorException("day is out of range for month", ApiException.ERROR_RETURN_CODE_INVALID));
        }

        Object dlat = getValue(rtn, "Encounter.decimalLatitude");
        Object dlon = getValue(rtn, "Encounter.decimalLongitude");
        if ((dlat == null) && (dlon != null)) rtn.put("Encounter.decimalLatitude", new BulkValidatorException("must supply both latitude and longitude", ApiException.ERROR_RETURN_CODE_INVALID));
        if ((dlat != null) && (dlon == null)) rtn.put("Encounter.decimalLongitude", new BulkValidatorException("must supply both latitude and longitude", ApiException.ERROR_RETURN_CODE_INVALID));

        return rtn;
    }

    // this is just a helper function for validateRow
    private static Object getValue(Map<String, Object> map, String fieldName) {
        if (!map.containsKey(fieldName)) return null;
        if (map.get(fieldName) instanceof Exception) return null;
        BulkValidator bv = (BulkValidator)map.get(fieldName);
        return bv.getValue();
    }
}

