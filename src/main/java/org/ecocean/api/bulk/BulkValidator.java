// validates field + value(s)
package org.ecocean.api.bulk;

import java.time.Year;
import java.util.Arrays;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import org.json.JSONObject;

import org.ecocean.api.ApiException;
import org.ecocean.api.SiteSettings;
import org.ecocean.CommonConfiguration;
import org.ecocean.LocationID;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.Util;

public class BulkValidator {
    public static final Set<String> FIELD_NAMES = new HashSet<>(Arrays.asList(
        "Encounter.alternateID", "Encounter.behavior", "Encounter.catalogNumber",
        "Encounter.country", "Encounter.dateInMilliseconds", "Encounter.day",
        "Encounter.decimalLatitude", "Encounter.decimalLongitude", "Encounter.depth",
        "Encounter.distinguishingScar", "Encounter.elevation", "Encounter.genus",
        "Encounter.groupRole", "Encounter.hour", "Encounter.id", "Encounter.identificationRemarks",
        "Encounter.individualID", "Encounter.latitude", "Encounter.lifeStage",
        "Encounter.livingStatus", "Encounter.locationID", "Encounter.longitude",
        "Encounter.minutes", "Encounter.month", "Encounter.sightingID", "Encounter.sightingRemarks",
        "Encounter.otherCatalogNumbers", "Encounter.patterningCode", "Encounter.photographer",
        "Encounter.project", "Encounter.researcherComments", "Encounter.sex",
        "Encounter.specificEpithet", "Encounter.state", "Encounter.submitterID",
        "Encounter.submitterName", "Encounter.submitterOrganization", "Encounter.verbatimLocality",
        "Encounter.year", "MarkedIndividual.individualID", "MarkedIndividual.name",
        "MarkedIndividual.nickName", "MarkedIndividual.nickname", "Membership.role",
        "MicrosatelliteMarkersAnalysis.alleleNames", "MicrosatelliteMarkersAnalysis.analysisID",
        "MitochondrialDNAAnalysis.haplotype", "Sighting.bearing", "Sighting.bestGroupSizeEstimate",
        "Sighting.comments", "Sighting.dateInMilliseconds", "Sighting.day",
        "Sighting.decimalLatitude", "Sighting.decimalLongitude", "Sighting.distance",
        "Sighting.effortCode", "Sighting.fieldStudySite", "Sighting.fieldSurveyCode",
        "Sighting.groupBehavior", "Sighting.groupComposition", "Sighting.hour",
        "Sighting.humanActivityNearby", "Sighting.individualCount", "Sighting.initialCue",
        "Sighting.maxGroupSizeEstimate", "Sighting.millis", "Sighting.minGroupSizeEstimate",
        "Sighting.minutes", "Sighting.month", "Sighting.numAdults", "Sighting.numCalves",
        "Sighting.numJuveniles", "Sighting.observer", "Sighting.sightingID", "Sighting.seaState",
        "Sighting.groupSize", "Sighting.numSubAdults", "Sighting.numAdultMales",
        "Sighting.numAdultFemales", "Sighting.numSubFemales", "Sighting.numSubMales",
        "Sighting.seaSurfaceTemp", "Sighting.seaSurfaceTemperature", "Sighting.swellHeight",
        "Sighting.terrain", "Sighting.transectBearing", "Sighting.transectName",
        "Sighting.vegetation", "Sighting.visibilityIndex", "Sighting.year",
        "SatelliteTag.serialNumber", "SexAnalysis.processingLabTaskID", "SexAnalysis.sex",
        "SocialUnit.socialUnitName", "Survey.comments", "Survey.id", "Survey.type", "Survey.vessel",
        "SurveyTrack.vesselID", "Taxonomy.commonName", "Taxonomy.scientificName",
        "TissueSample.sampleID", "TissueSample.tissueType"));

    public static final Set<String> FIELD_NAMES_INDEXABLE = new HashSet<>(Arrays.asList(
        "Encounter.keyword#", "Encounter.mediaAsset#", "Encounter.mediaAsset#.keywords",
        "Encounter.project#.projectIdPrefix", "Encounter.project#.researchProjectName",
        "Encounter.project#.ownerUsername", "Encounter.quality#",
        "Encounter.submitter#.affiliation", "Encounter.submitter#.emailAddress",
        "Encounter.submitter#.fullName", "Encounter.informOther#.emailAddress",
        "Encounter.photographer#.emailAddress", "MicrosatelliteMarkersAnalysis.alleles#",
        "MarkedIndividual.name#.label", "MarkedIndividual.name#.value", "Sighting.taxonomy#"));

    public static final Set<String> FIELD_NAMES_REQUIRED = new HashSet<>(Arrays.asList(
        "Encounter.genus", "Encounter.specificEpithet", "Encounter.year"));

    // this is for frontend, and it contains "minimally supported" fields: those which will NOT be
    // validated, but just accepted as-is and set on appropriate object
    public static final Set<String> MINIMAL_FIELD_NAMES_STRING = new HashSet<>(Arrays.asList(
        "Encounter.alternateID", "Encounter.distinguishingScar", "Encounter.groupRole",
        "Encounter.identificationRemarks", "Encounter.individualID", "Encounter.sightingID",
        "Encounter.sightingRemarks", "Encounter.otherCatalogNumbers", "Encounter.patterningCode",
        "Encounter.state", "Encounter.submitterName", "Encounter.submitterOrganization",
        "MarkedIndividual.name", "MarkedIndividual.nickname", "MarkedIndividual.nickName",
        "Membership.role", "MicrosatelliteMarkersAnalysis.alleleNames",
        "MicrosatelliteMarkersAnalysis.analysisID", "MitochondrialDNAAnalysis.haplotype",
        "Sighting.comments", "Sighting.fieldStudySite", "Sighting.groupBehavior",
        "Sighting.groupComposition", "Sighting.humanActivityNearby", "Sighting.initialCue",
        "Sighting.observer", "Sighting.sightingID", "Sighting.terrain", "Sighting.transectName",
        "Sighting.vegetation", "SatelliteTag.serialNumber", "SexAnalysis.processingLabTaskID",
        "SocialUnit.socialUnitName", "Survey.comments", "Survey.id", "Survey.type",
        "SurveyTrack.vesselID", "Survey.vessel", "Taxonomy.commonName", "Taxonomy.scientificName",
        "TissueSample.tissueType"));
    public static final Set<String> MINIMAL_FIELD_NAMES_INT = new HashSet<>(Arrays.asList(
        "Sighting.fieldSurveyCode", "Sighting.groupSize", "Sighting.individualCount",
        "Sighting.maxGroupSizeEstimate", "Sighting.minGroupSizeEstimate",
        "Sighting.numAdultFemales", "Sighting.numAdultMales", "Sighting.numAdults",
        "Sighting.numCalves", "Sighting.numJuveniles", "Sighting.numSubAdults",
        "Sighting.numSubFemales", "Sighting.numSubMales", "Sighting.seaState",
        "Sighting.visibilityIndex"));
    public static final Set<String> MINIMAL_FIELD_NAMES_DOUBLE = new HashSet<>(Arrays.asList(
        "Encounter.depth", "Encounter.elevation", "Sighting.bearing",
        "Sighting.bestGroupSizeEstimate", "Sighting.distance", "Sighting.effortCode",
        "Sighting.seaSurfaceTemperature", "Sighting.swellHeight", "Sighting.transectBearing"));

    private String fieldName = null;
    private Object value = null;
    private int indexInt = -3;
    private String indexPrefix = null;

    // public BulkValidator(String fieldName, JSONObject jvalue) throws BulkValidatorException {

    public BulkValidator(String fieldNamePassed, Object valuePassed, Shepherd myShepherd)
    throws BulkValidatorException {
        indexInt = indexIntValue(fieldNamePassed); // bonus: this throws exception if valid fieldName
        if (indexInt >= 0) indexPrefix = indexPrefixValue(fieldNamePassed);
        fieldName = fieldNamePassed;
        value = validateValue(fieldNamePassed,
            (valuePassed == JSONObject.NULL ? null : valuePassed), myShepherd);
    }

    public boolean isIndexed() {
        return (indexInt >= 0);
    }

    public int getIndexInt() {
        return indexInt;
    }

    public String getIndexPrefix() {
        return indexPrefix;
    }

    public boolean indexPrefixEquals(String val) {
        if (indexPrefix == null) return false;
        return indexPrefix.equals(val);
    }

    public String getFieldName() {
        return fieldName;
    }

    public Object getValue() {
        return value;
    }

    public boolean valueIsNull() {
        return (value == null);
    }

    public String getValueString() {
        if (value == null) return null;
        return value.toString();
    }

    public Double getValueDouble() {
        if (value == null) return null;
        if (value instanceof Double) return (Double)value;
        System.out.println("not a Double: " + value);
        return null;
    }

    public Integer getValueInteger() {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer)value;
        System.out.println("not an Integer: " + value);
        return null;
    }

    public Long getValueLong() {
        if (value == null) return null;
        if (value instanceof Long) return (Long)value;
        if (value instanceof Integer) {
            Integer i = (Integer)value;
            return i.longValue();
        }
        System.out.println("not a Long: " + value);
        return null;
    }

    public static boolean isValidFieldName(String fieldName) {
        if (fieldName == null) return false;
        if (FIELD_NAMES.contains(fieldName)) return true;
        if (getRawIndexableFieldName(fieldName) != null) return true;
        if (isMeasurementFieldName(fieldName)) return true;
        return false;
    }

    // returns -1 if valid field but not indexable, -2 if could not parse where int should be
    public static int indexIntValue(String fieldName)
    throws BulkValidatorException {
        if (!isValidFieldName(fieldName))
            throw new BulkValidatorException("invalid fieldName: " + fieldName,
                    ApiException.ERROR_RETURN_CODE_INVALID,
                    BulkValidatorException.TYPE_UNKNOWN_FIELDNAME);
        String raw = getRawIndexableFieldName(fieldName);
        if (raw == null) return -1;
        Pattern p = Pattern.compile(rawToRegex(raw));
        Matcher m = p.matcher(fieldName);
        if (!m.find()) return -2;
        try {
            return Integer.parseInt(m.group(1));
        } catch (Exception ex) {}
        return -3;
    }

    // will return null if valid fieldName, but not indexable
    public static String indexPrefixValue(String fieldName)
    throws BulkValidatorException {
        if (!isValidFieldName(fieldName))
            throw new BulkValidatorException("invalid fieldName: " + fieldName,
                    ApiException.ERROR_RETURN_CODE_INVALID,
                    BulkValidatorException.TYPE_UNKNOWN_FIELDNAME);
        String raw = getRawIndexableFieldName(fieldName);
        if (raw == null) return null;
        return raw.replace("#", "");
    }

    private static String rawToRegex(String prefix) {
        String regex = "^" + prefix.replace(".", "\\.") + "$";

        return regex.replace("#", "(\\d+)");
    }

    private static String getRawIndexableFieldName(String fieldName) {
        for (String ifn : FIELD_NAMES_INDEXABLE) {
            if (fieldName.matches(rawToRegex(ifn))) return ifn;
        }
        return null;
    }

    public static Object validateValue(String fieldName, Object value, Shepherd myShepherd)
    throws BulkValidatorException {
        if (!isValidFieldName(fieldName))
            throw new BulkValidatorException("invalid fieldName: " + fieldName,
                    ApiException.ERROR_RETURN_CODE_INVALID,
                    BulkValidatorException.TYPE_UNKNOWN_FIELDNAME);
        switch (fieldName) {
        case "Encounter.id":
        case "Encounter.catalogNumber":
            if (value == null) return null;
            if (!Util.isUUID(value.toString()))
                throw new BulkValidatorException("must be proper UUID",
                        ApiException.ERROR_RETURN_CODE_INVALID);
            return value.toString();

        case "Encounter.year":
        case "Sighting.year":
            Integer intVal = tryInteger(value);
            if (intVal == null) return null;
            if (intVal < 1000)
                throw new BulkValidatorException("year value too small",
                        ApiException.ERROR_RETURN_CODE_INVALID);
            if (intVal > Year.now().getValue())
                throw new BulkValidatorException("year cannot be in future",
                        ApiException.ERROR_RETURN_CODE_INVALID);
            return intVal;

        case "Encounter.month":
        case "Sighting.month":
            intVal = tryInteger(value);
            if (intVal == null) return null;
            if (intVal < 1)
                throw new BulkValidatorException("month value too small",
                        ApiException.ERROR_RETURN_CODE_INVALID);
            if (intVal > 12)
                throw new BulkValidatorException("month value too large",
                        ApiException.ERROR_RETURN_CODE_INVALID);
            return intVal;

        case "Encounter.day":
        case "Sighting.day":
            intVal = tryInteger(value);
            if (intVal == null) return null;
            if (intVal < 1)
                throw new BulkValidatorException("day value too small",
                        ApiException.ERROR_RETURN_CODE_INVALID);
            if (intVal > 31)
                throw new BulkValidatorException("day value too large",
                        ApiException.ERROR_RETURN_CODE_INVALID);
            // note: to validate upper bound based on month, this must be done through BulkImportUtil.validateRow()
            return intVal;

        case "Encounter.hour":
        case "Sighting.hour":
            intVal = tryInteger(value);
            if (intVal == null) return null;
            if (intVal < 0)
                throw new BulkValidatorException("hour value too small",
                        ApiException.ERROR_RETURN_CODE_INVALID);
            if (intVal > 23)
                throw new BulkValidatorException("hour value too large",
                        ApiException.ERROR_RETURN_CODE_INVALID);
            return intVal;

        case "Encounter.minutes":
        case "Sighting.minutes":
            intVal = tryInteger(value);
            if (intVal == null) return null;
            if (intVal < 0)
                throw new BulkValidatorException("minutes value too small",
                        ApiException.ERROR_RETURN_CODE_INVALID);
            if (intVal > 59)
                throw new BulkValidatorException("minutes value too large",
                        ApiException.ERROR_RETURN_CODE_INVALID);
            return intVal;

        case "Encounter.decimalLatitude":
        case "Encounter.latitude":
        case "Sighting.decimalLatitude":
            Double doubleVal = tryDouble(value);
            if (doubleVal == null) return null;
            if (!Util.isValidDecimalLatitude(doubleVal))
                throw new BulkValidatorException("invalid " + fieldName + " value: " + doubleVal,
                        ApiException.ERROR_RETURN_CODE_INVALID);
            return doubleVal;

        case "Encounter.decimalLongitude":
        case "Encounter.longitude":
        case "Sighting.decimalLongitude":
            doubleVal = tryDouble(value);
            if (doubleVal == null) return null;
            if (!Util.isValidDecimalLongitude(doubleVal))
                throw new BulkValidatorException("invalid " + fieldName + " value: " + doubleVal,
                        ApiException.ERROR_RETURN_CODE_INVALID);
            return doubleVal;

        case "Encounter.locationID":
            if ((value != null) && !LocationID.isValidLocationID(value.toString()))
                throw new BulkValidatorException("invalid location value: " + value,
                        ApiException.ERROR_RETURN_CODE_INVALID);
            return value;

        case "Encounter.sex":
            if ((value != null) && !Arrays.asList(SiteSettings.VALUES_SEX).contains(value))
                throw new BulkValidatorException("invalid sex value: " + value,
                        ApiException.ERROR_RETURN_CODE_INVALID);
            return value;

        case "Encounter.state":
            if ((value != null) &&
                !Arrays.asList(SiteSettings.VALUES_ENCOUNTER_STATES).contains(value))
                throw new BulkValidatorException("invalid state value: " + value,
                        ApiException.ERROR_RETURN_CODE_INVALID);
            return value;

        case "Encounter.lifeStage":
            if ((value != null) && !CommonConfiguration.getIndexedPropertyValues("lifeStage",
                myShepherd.getContext()).contains(value))
                throw new BulkValidatorException("invalid lifeStage value: " + value,
                        ApiException.ERROR_RETURN_CODE_INVALID);
            return value;

        case "Encounter.livingStatus":
            if ((value != null) && !CommonConfiguration.getIndexedPropertyValues("livingStatus",
                myShepherd.getContext()).contains(value))
                throw new BulkValidatorException("invalid livingStatus value: " + value,
                        ApiException.ERROR_RETURN_CODE_INVALID);
            return value;

        case "Encounter.country":
            if ((value != null) && !CommonConfiguration.getIndexedPropertyValues("country",
                myShepherd.getContext()).contains(value))
                throw new BulkValidatorException("invalid country value: " + value,
                        ApiException.ERROR_RETURN_CODE_INVALID);
            return value;

        case "Encounter.submitterID":
            if ("public".equals(value)) return value;
            if ((value != null) && (myShepherd.getUser(value.toString()) == null))
                throw new BulkValidatorException("invalid username: " + value,
                        ApiException.ERROR_RETURN_CODE_INVALID);
            return value;

        // just generic (positive) ints
        case "Sighting.individualCount":
        case "Sighting.maxGroupSizeEstimate":
        case "Sighting.minGroupSizeEstimate":
            if (value == null) return null;
            intVal = tryInteger(value);
            if (intVal < 0)
                throw new BulkValidatorException("integer must be 0 or larger",
                        ApiException.ERROR_RETURN_CODE_INVALID);
            return intVal;

        case "Sighting.seaSurfaceTemp":
        case "Sighting.seaSurfaceTemperature":
        case "Sighting.swellHeight":
        case "Sighting.transectBearing":
        case "Sighting.bearing":
        case "Sighting.bestGroupSizeEstimate":
        case "Sighting.distance":
        case "Encounter.depth":
        case "Encounter.elevation":
            return tryDouble(value);
        }
        // now we validated prefixed ones
        String prefix = indexPrefixValue(fieldName);
        if (prefix != null)
            switch (prefix) {
            case "Encounter.submitter.emailAddress":
            case "Encounter.photographer.emailAddress":
            case "Encounter.informOther.emailAddress":
                if (value == null) return null;
                if (!Util.isValidEmailAddress(value.toString()))
                    throw new BulkValidatorException("invalid email address",
                            ApiException.ERROR_RETURN_CODE_INVALID);
                return value;

            case "Sighting.taxonomy":
                if (value == null) return null;
                throw new BulkValidatorException("net yet supporting validating Sighting.taxonomy",
                        ApiException.ERROR_RETURN_CODE_INVALID);
            }
        // now we validate, um, weird ones
        int offset = BulkImportUtil.findMeasurementOffset(fieldName);
        if (offset >= 0) return tryDouble(value);
        offset = BulkImportUtil.findMeasurementSamplingProtocolOffset(fieldName);
        if (offset >= 0) return value;
        // probably should never get to this point, so worth noting
        System.out.println("INFO: validateValue() fell through with fieldName=" + fieldName +
            " and value=" + value);
        return value;
        // throw new BulkValidatorException("unknown error on fieldName validation: " + fieldName, ApiException.ERROR_RETURN_CODE_UNKNOWN);
    }

    private static Integer tryInteger(Object value)
    throws BulkValidatorException {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer)value;
        try {
            return Integer.valueOf(value.toString());
        } catch (Exception ex) {
            throw new BulkValidatorException("error parsing integer: " + ex,
                    ApiException.ERROR_RETURN_CODE_INVALID);
        }
    }

    private static Double tryDouble(Object value)
    throws BulkValidatorException {
        if (value == null) return null;
        if (value instanceof Double) return (Double)value;
        try {
            return Double.valueOf(value.toString());
        } catch (Exception ex) {
            throw new BulkValidatorException("error parsing double: " + ex,
                    ApiException.ERROR_RETURN_CODE_INVALID);
        }
    }

    public String toString() {
        String str = fieldName;

        if (indexPrefix != null) {
            str += "[" + indexPrefix + ":" + indexInt + "]";
        }
        str += " value=(" + value + ")";
        return str;
    }

    public static JSONObject minimalFieldsJson() {
        JSONObject mf = new JSONObject();

        for (String f : MINIMAL_FIELD_NAMES_STRING) {
            mf.put(f, "string");
        }
        for (String f : MINIMAL_FIELD_NAMES_INT) {
            mf.put(f, "int");
        }
        for (String f : MINIMAL_FIELD_NAMES_DOUBLE) {
            mf.put(f, "double");
        }
        // for now we are treating measurements as minimalFields (double)
        int i = 0;
        for (String mname : BulkImportUtil.getMeasurementValues()) {
            mf.put("Encounter.measurement" + i, "double");
            mf.put("Encounter." + mname, "double");
            mf.put("Encounter.measurement." + mname, "double");
            mf.put("Encounter.measurement" + i + ".samplingProtocol", "string");
            mf.put("Encounter." + mname + ".samplingProtocol", "string");
            mf.put("Encounter.measurement." + mname + ".samplingProtocol", "string");
            i++;
        }
        return mf;
    }

    // apparently measurements can be any of the following. :|  sigh
    // - Encounter.measurement0  [for valid int]
    // - Encounter.mname  [where "mname" is actual name of measurement]
    // - Encounter.measurement.mname
    // any of these can also be followed by ".samplingProtocol"
    public static boolean isMeasurementFieldName(String fieldName) {
        int offset = BulkImportUtil.findMeasurementOffset(fieldName);

        if (offset >= 0) return true;
        offset = BulkImportUtil.findMeasurementSamplingProtocolOffset(fieldName);
        if (offset >= 0) return true;
        return false;
    }
}
