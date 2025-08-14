package org.ecocean.api.patch;

import org.ecocean.api.ApiException;
import org.ecocean.api.bulk.BulkValidator;
import org.ecocean.api.bulk.BulkValidatorException;
import org.ecocean.shepherd.core.Shepherd;

import org.ecocean.Encounter;

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
        System.out.println(">>>>>>>>>>>>> PATCH obj=" + patch.toString(8));
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
            } else {
                //
            }
        } else { // other ops
        }
        // FIXME temporary fall-through fail
        rtn.put("error", "fell through");

        String errorMsg = rtn.optString("error", null);
        if (errorMsg != null)
            throw new ApiException(errorMsg, ApiException.ERROR_RETURN_CODE_INVALID);
        return rtn;
    }

    public static boolean isValidOp(String op) {
        return VALID_OPS.contains(op);
    }
}
