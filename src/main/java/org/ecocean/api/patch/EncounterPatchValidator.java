package org.ecocean.api.patch;

import org.ecocean.api.ApiException;
import org.ecocean.api.bulk.BulkValidator;
import org.ecocean.api.bulk.BulkValidatorException;
import org.ecocean.Encounter;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.Util;

import java.time.YearMonth;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONObject;

public class EncounterPatchValidator {
    public static final Set<String> VALID_OPS = new HashSet<>(Arrays.asList("add", "remove",
        "replace", "move", "copy"));

    public static String ERROR_INVALID_OP = "INVALID_OP";
    public static String ERROR_INVALID_PATH = "INVALID_PATH";

    public static JSONObject applyPatch(Encounter enc, JSONObject patch, Shepherd myShepherd)
    throws ApiException {
        if (patch == null)
            throw new ApiException("null patch json", ApiException.ERROR_RETURN_CODE_REQUIRED);
        String op = patch.optString("op");
        if (!isValidOp(op))
            throw new ApiException("invalid op: " + op, ERROR_INVALID_OP);
        JSONObject rtn = new JSONObject();
        rtn.put("_patch", patch);
        String path = patch.optString("path");
        Object value = patch.opt("value");
        // System.out.println(">>>>>>>>>>>>> PATCH obj=" + patch.toString(8));
        // add and replace are interchangeable for some things, so lets reuse
        // existing code for these when we can
        if (op.equals("add") || op.equals("replace")) {
            // this will throw ApiException if any of the POST (new Encounter) fields
            // are present and invalid values
            JSONObject tmp = new JSONObject(); // what we have to pass
            tmp.put(path, value);
            Encounter.validateFieldValue(path, tmp);
            // if we can piggyback on validation via BulkImporter, we do
            String bulkFieldName = "Encounter." + path;
            if (BulkValidator.isValidFieldName(bulkFieldName)) {
                // this will throw an exception if no bueno
                BulkValidator bv = new BulkValidator(bulkFieldName, value, myShepherd);
                System.out.println("**** BV!! **** " + bv);
                value = bv.getValue();
            } else {
// FIXME need to validate path more generally, maybe via applyPatchOp() throwing exception???
                //
            }
            // if we get through to here, value should be cleared to do actual patch
            // but this will throw exception if bad path
            enc.applyPatchOp(path, value, op);
        } else if (op.equals("remove")) {
            // TODO need to check required, etc.
            enc.applyPatchOp(path, null, op);
        } else { // other ops
        }
        // no exceptions means we had success
        String errorMsg = rtn.optString("error", null);
        if (errorMsg != null)
            throw new ApiException(errorMsg, ApiException.ERROR_RETURN_CODE_INVALID);
        return rtn;
    }

    // checks internal consistency of Encounter, such as missing one of lat/lon etc.
    public static void finalValidation(Encounter enc, Shepherd myShepherd)
    throws ApiException {
        // first check that we didnt end up with only half lat/lon set
        if (((enc.getDecimalLatitude() == null) && (enc.getDecimalLongitude() != null)) ||
            ((enc.getDecimalLatitude() != null) && (enc.getDecimalLongitude() == null)))
            throw new ApiException("must have both decimalLatitude and decimalLongitude values set",
                    ApiException.ERROR_RETURN_CODE_REQUIRED);
        // now make sure we didnt tweak one of y/m/d value and create invalid date
        if ((enc.getYear() > 0) && (enc.getMonth() > 0) && (enc.getDay() > 0)) {
            YearMonth yearMonth = YearMonth.of(enc.getYear(), enc.getMonth());
            if (!yearMonth.isValidDay(enc.getDay()))
                throw new ApiException("day is out of range for month",
                        ApiException.ERROR_RETURN_CODE_INVALID);
            if (Util.dateIsInFuture(enc.getYear(), enc.getMonth(), enc.getDay()))
                throw new ApiException("date cannot be set in future",
                        ApiException.ERROR_RETURN_CODE_INVALID);
        }
        if ((enc.getMonth() < 1) && (enc.getDay() > 0))
            throw new ApiException("cannot have day set but no month set",
                    ApiException.ERROR_RETURN_CODE_INVALID);
        // make sure we didnt set one of genus/specificEpithet and create invalid taxonomy
        String sciName = enc.getTaxonomyString();
        if ((sciName != null) && !myShepherd.isValidTaxonomyName(sciName))
            throw new ApiException("genus and specificEpithet are an invalid taxonomy: " + sciName,
                    ApiException.ERROR_RETURN_CODE_INVALID);
    }

    public static boolean isValidOp(String op) {
        return VALID_OPS.contains(op);
    }
}
