// basic bulk import utilities
package org.ecocean.api.bulk;

import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

public class BulkImportUtil {

	public static Map<String, BulkValidatorException> validateRow(JSONObject row) {
		Map<String, BulkValidatorException> rtn = new HashMap<String, BulkValidatorException>();
		if (row == null) return rtn;
		for (String fieldName : row.keySet()) {
			try {
				new BulkValidator(fieldName, row.optString(fieldName, null));
			} catch (BulkValidatorException bve) {
				rtn.put(fieldName, bve);
			}
		}
		return rtn;
	}
}

