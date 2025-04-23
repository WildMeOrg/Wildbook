// basic bulk import utilities
package org.ecocean.api.bulk;

import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

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
        return rtn;
    }
}

