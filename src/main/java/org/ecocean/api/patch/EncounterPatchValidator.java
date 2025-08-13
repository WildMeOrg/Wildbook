package org.ecocean.api.patch;

import org.ecocean.api.ApiException;
import org.ecocean.api.bulk.BulkValidator;
import org.ecocean.api.bulk.BulkValidatorException;
import org.ecocean.shepherd.core.Shepherd;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONObject;

public class EncounterPatchValidator extends BulkValidator {
    public static final Set<String> VALID_OPS = new HashSet<>(Arrays.asList("add", "remove",
        "replace", "move", "copy"));

    public static String TYPE_INVALID_OP = "INVALID_OP";
    public static String TYPE_INVALID_PATH = "INVALID_PATH";

    public EncounterPatchValidator(String fieldName, Object value, Shepherd myShepherd)
    throws BulkValidatorException {
        // FIXME
        super(fieldName, value, myShepherd);
    }

    public static EncounterPatchValidator createFromPatch(JSONObject patch, Shepherd myShepherd)
    throws BulkValidatorException {
        if (patch == null)
            throw new BulkValidatorException("null patch json",
                    ApiException.ERROR_RETURN_CODE_REQUIRED,
                    BulkValidatorException.TYPE_REQUIRED_VALUE);
        String op = patch.optString("op");
        if (!isValidOp(op))
            throw new BulkValidatorException("invalid op: " + op,
                    ApiException.ERROR_RETURN_CODE_INVALID, TYPE_INVALID_OP);
        String path = patch.optString("path");
        Object value = patch.opt("value");
        return new EncounterPatchValidator(path, value, myShepherd);
    }

    public static boolean isValidOp(String op) {
        return VALID_OPS.contains(op);
    }
}
