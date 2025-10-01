package org.ecocean.api.patch;

import org.ecocean.api.ApiException;
import org.ecocean.api.bulk.BulkValidator;
import org.ecocean.api.bulk.BulkValidatorException;
import org.ecocean.api.UploadedFiles;
import org.ecocean.Encounter;
import org.ecocean.media.MediaAsset;
import org.ecocean.MarkedIndividual;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.tag.*;
import org.ecocean.User;
import org.ecocean.Util;

import java.io.File;
import java.io.IOException;
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

    // "remove" op not allowed on these
    public static final Set<String> PATHS_REQUIRED = new HashSet<>(Arrays.asList("genus",
        "specificEpithet", "year", "submitterID"));
    // remove needs a value (id) on these
    public static final Set<String> PATHS_REMOVE_NEEDS_USER_VALUE = new HashSet<>(Arrays.asList(
        "photographers", "informOthers", "submitters"));

    public static JSONObject applyPatch(Encounter enc, JSONObject patch, Shepherd myShepherd)
    throws ApiException {
        if (enc == null)
            throw new ApiException("null encounter", ApiException.ERROR_RETURN_CODE_REQUIRED);
        if (patch == null)
            throw new ApiException("null patch json", ApiException.ERROR_RETURN_CODE_REQUIRED);
        String op = patch.optString("op");
        if (!isValidOp(op))
            throw new ApiException("invalid op: " + op, ERROR_INVALID_OP);
        JSONObject rtn = new JSONObject();
        rtn.put("_patch", patch);
        String path = patch.optString("path", null);
        if (path == null)
            throw new ApiException("empty path", ERROR_INVALID_PATH);
        Object value = patch.opt("value");
        if (patch.isNull("value")) value = null;
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
            }
            if (path.equals("individualId") && (value != null)) {
                value = getOrCreateMarkedIndividual(value.toString(), myShepherd);
                System.out.println("applyPatch() path=individualId using " + value);
            }
            if (path.equals("assets") && (value != null)) {
                if (value instanceof JSONObject) {
                    value = createMediaAsset((JSONObject)value, enc.getId(), myShepherd);
                    System.out.println("applyPatch() path=assets using " + value);
                } else {
                    throw new ApiException("must pass json object as value: " + value,
                            ApiException.ERROR_RETURN_CODE_INVALID);
                }
            }
            if (path.equals("acousticTag")) {
                JSONObject jval = testJsonValue(value, new String[] { "idNumber", "serialNumber" });
                if (jval != null)
                    value = new AcousticTag(jval.optString("serialNumber", null),
                        jval.optString("idNumber", null));
            }
            if (path.equals("satelliteTag")) {
                JSONObject jval = testJsonValue(value,
                    new String[] { "argosPttNumber", "serialNumber", "name" });
                if (jval != null)
                    value = new SatelliteTag(jval.optString("name", null),
                        jval.optString("serialNumber", null),
                        jval.optString("argosPttNumber", null));
            }
            // since metalTags is a list we can only add, and null is pointless
            if (path.equals("metalTags") && (value != null)) {
                if (op.equals("replace"))
                    throw new ApiException(path + " cannot use op=replace",
                            ApiException.ERROR_RETURN_CODE_INVALID);
                JSONObject jval = testJsonValue(value, new String[] { "location", "number" });
                if (jval != null)
                    value = new MetalTag(jval.optString("number", null),
                        jval.optString("location", null));
            }
            if (PATHS_REMOVE_NEEDS_USER_VALUE.contains(path)) {
                if (op.equals("replace"))
                    throw new ApiException(path + " cannot use op=replace",
                            ApiException.ERROR_RETURN_CODE_INVALID);
                if (value == null)
                    throw new ApiException(path + " requires a value to add to list",
                            ApiException.ERROR_RETURN_CODE_REQUIRED);
                String userValue = value.toString();
                if (Util.isUUID(userValue)) {
                    User addUser = myShepherd.getUserByUUID(userValue);
                    if (addUser == null)
                        throw new ApiException(path + " no user id=" + userValue,
                                ApiException.ERROR_RETURN_CODE_INVALID);
                    value = addUser;
                } else if (Util.isValidEmailAddress(userValue)) {
                    User addUser = myShepherd.getOrCreateUserByEmailAddress(userValue, null);
                    value = addUser;
                    System.out.println("applyPatch() path=" + path + " using " + value);
                } else {
                    throw new ApiException(path + " has unusable value=" + userValue,
                            ApiException.ERROR_RETURN_CODE_INVALID);
                }
            }
            // TODO future enhancement: op=remove path=annotations/ANNOT_ID
            // so would need to validate ANNOT_ID here
            // see also: enc.applyPatchOp()

            // if we get through to here, value should be cleared to do actual patch
            // but this will throw exception if bad path
            enc.applyPatchOp(path, value, op);
        } else if (op.equals("remove")) {
            if (PATHS_REQUIRED.contains(path))
                throw new ApiException(path + " is a required value, cannot remove",
                        ApiException.ERROR_RETURN_CODE_REQUIRED);
            if (PATHS_REMOVE_NEEDS_USER_VALUE.contains(path)) {
                if (value == null)
                    throw new ApiException(path + " requires a value to remove from list",
                            ApiException.ERROR_RETURN_CODE_REQUIRED);
                value = myShepherd.getUserByUUID(value.toString());
                if (value == null)
                    throw new ApiException(path + " value is invalid user id",
                            ApiException.ERROR_RETURN_CODE_INVALID);
                enc.applyPatchOp(path, value, op);
            } else if (path.equals("metalTags")) {
                if (value == null)
                    throw new ApiException(path + " requires a value to remove from list",
                            ApiException.ERROR_RETURN_CODE_REQUIRED);
                JSONObject jval = testJsonValue(value, new String[] { "location", "number" });
                enc.applyPatchOp(path, jval, op);
            } else {
                enc.applyPatchOp(path, null, op);
            }
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

    private static JSONObject testJsonValue(Object value, String[] validFields)
    throws ApiException {
        if (value == null) return null; // null is valid
        if (!(value instanceof JSONObject))
            throw new ApiException("must pass a json object",
                    ApiException.ERROR_RETURN_CODE_INVALID);
        JSONObject jval = (JSONObject)value;
        for (String key : jval.keySet()) {
            if (!Arrays.asList(validFields).contains(key))
                throw new ApiException(key + " is an invalid key",
                        ApiException.ERROR_RETURN_CODE_INVALID);
        }
        return jval;
    }

    private static MarkedIndividual getOrCreateMarkedIndividual(String idOrName,
        Shepherd myShepherd) {
        MarkedIndividual indiv = myShepherd.getMarkedIndividual(idOrName);

        if (indiv != null) return indiv;
        indiv = new MarkedIndividual(); // will get assigned id
        indiv.addName(idOrName);
        // other properties like taxonomy set during actual patchOp
        myShepherd.getPM().makePersistent(indiv);
        return indiv;
    }

    private static MediaAsset createMediaAsset(JSONObject data, String targetSubdir,
        Shepherd myShepherd)
    throws ApiException {
        if (data == null)
            throw new ApiException("null asset data", ApiException.ERROR_RETURN_CODE_REQUIRED);
        File file = null;
        try {
            file = UploadedFiles.getFile(data.optString("submissionId", null),
                data.optString("filename", null));
        } catch (IOException ex) {
            throw new ApiException(ex.getMessage(), ApiException.ERROR_RETURN_CODE_INVALID);
        }
        MediaAsset ma = UploadedFiles.makeMediaAsset(targetSubdir, file, myShepherd);
        myShepherd.getPM().makePersistent(ma);
        return ma;
    }
}
