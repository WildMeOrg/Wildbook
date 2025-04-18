// validates field + value(s)
package org.ecocean.api.bulk;

import org.json.JSONObject;


public class BulkValidator {
	public static String[] FIELD_NAMES = {
		"Encounter.day", "Encounter.month", "Encounter.year"
	};
	public static String[] FIELD_NAMES_ITERABLE = {
		"Encounter.mediaAsset"
	};

	public BulkValidator(String fieldName, JSONObject jvalue) throws BulkValidatorException {
	}
}

