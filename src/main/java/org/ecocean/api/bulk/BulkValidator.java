// validates field + value(s)
package org.ecocean.api.bulk;

import org.json.JSONObject;
import java.time.Year;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.ecocean.api.ApiException;
import org.ecocean.Util;


public class BulkValidator {
	public static final Set<String> FIELD_NAMES = new HashSet<>(Arrays.asList(
		"Encounter.alternateID",
		"Encounter.behavior",
		"Encounter.country",
		"Encounter.dateInMilliseconds",
		"Encounter.day",
		"Encounter.decimalLatitude",
		"Encounter.decimalLongitude",
		"Encounter.depth",
		"Encounter.distinguishingScar",
		"Encounter.elevation",
		"Encounter.genus",
		"Encounter.groupRole",
		"Encounter.hour",
		"Encounter.identificationRemarks",
		"Encounter.individualID",
		"Encounter.informOther",
		"Encounter.latitude",
		"Encounter.lifeStage",
		"Encounter.livingStatus",
		"Encounter.locationID",
		"Encounter.longitude",
		"Encounter.measurement",
		"Encounter.minutes",
		"Encounter.month",
		"Encounter.occurrenceID",
		"Encounter.occurrenceRemarks",
		"Encounter.otherCatalogNumbers",
		"Encounter.patterningCode",
		"Encounter.photographer",
		"Encounter.project",
		"Encounter.quality",
		"Encounter.researcherComments",
		"Encounter.sex",
		"Encounter.specificEpithet",
		"Encounter.state",
		"Encounter.submitter",
		"Encounter.submitterID",
		"Encounter.submitterName",
		"Encounter.submitterOrganization",
		"Encounter.verbatimLocality",
		"Encounter.year",
		"MarkedIndividual.individualID",
		"MarkedIndividual.name",
		"MarkedIndividual.nickName",
		"MarkedIndividual.nickname",
		"Membership.role",
		"MicrosatelliteMarkersAnalysis.alleleNames",
		"MicrosatelliteMarkersAnalysis.analysisID",
		"MitochondrialDNAAnalysis.haplotype",
		"Occurrence.bearing",
		"Occurrence.bestGroupSizeEstimate",
		"Occurrence.comments",
		"Occurrence.dateInMilliseconds",
		"Occurrence.day",
		"Occurrence.decimalLatitude",
		"Occurrence.decimalLongitude",
		"Occurrence.distance",
		"Occurrence.effortCode",
		"Occurrence.fieldStudySite",
		"Occurrence.fieldSurveyCode",
		"Occurrence.groupBehavior",
		"Occurrence.groupComposition",
		"Occurrence.hour",
		"Occurrence.humanActivityNearby",
		"Occurrence.individualCount",
		"Occurrence.initialCue",
		"Occurrence.maxGroupSizeEstimate",
		"Occurrence.millis",
		"Occurrence.minGroupSizeEstimate",
		"Occurrence.minutes",
		"Occurrence.month",
		"Occurrence.numAdults",
		"Occurrence.numCalves",
		"Occurrence.numJuveniles",
		"Occurrence.observer",
		"Occurrence.occurrenceID",
		"Occurrence.seaState",
		"Occurrence.seaSurfaceTemp",
		"Occurrence.seaSurfaceTemperature",
		"Occurrence.swellHeight",
		"Occurrence.transectBearing",
		"Occurrence.transectName",
		"Occurrence.visibilityIndex",
		"Occurrence.year",
		"SatelliteTag.serialNumber",
		"SexAnalysis.processingLabTaskID",
		"SexAnalysis.sex",
		"SocialUnit.socialUnitName",
		"Survey.comments",
		"Survey.id",
		"Survey.vessel",
		"SurveyTrack.vesselID",
		"Taxonomy.commonName",
		"Taxonomy.scientificName",
		"TissueSample.sampleID",
		"TissueSample.tissueType"
        ));

	public static final Set<String> FIELD_NAMES_INDEXABLE = new HashSet<>(Arrays.asList(
		"Encounter.keyword",
		"Encounter.mediaAsset",
		"MicrosatelliteMarkersAnalysis.alleles",
		"Occurrence.taxonomy"
	));

	public static final Set<String> FIELD_NAMES_REQUIRED = new HashSet<>(Arrays.asList(
		"Encounter.genus",
		"Encounter.specificEpithet",
		"Encounter.year"
	));

        private String fieldName = null;
        private Object value = null;
        private int indexInt = -3;
        private String indexPrefix = null;

	//public BulkValidator(String fieldName, JSONObject jvalue) throws BulkValidatorException {

	public BulkValidator(String fieldNamePassed, Object valuePassed) throws BulkValidatorException {
            indexInt = indexIntValue(fieldNamePassed); // bonus: this throws exception if valid fieldName
            if (indexInt >= 0) indexPrefix = indexPrefixValue(fieldNamePassed);
            fieldName = fieldNamePassed;
            value = validateValue(fieldNamePassed, valuePassed);
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

        public Object getValue() {
            return value;
        }

        public String getFieldName() {
            return fieldName;
        }

        public static boolean isValidFieldName(String fieldName) {
            if (fieldName == null) return false;
            if (FIELD_NAMES.contains(fieldName)) return true;
            for (String prefix : FIELD_NAMES_INDEXABLE) {
                String dotFix = prefix.replace(".", "\\.");
                if (fieldName.matches("^" + dotFix + "\\d+$")) return true;
            }
            return false;
        }

        // returns -1 if valid field but not indexable, -2 if could not parse where int should be
        public static int indexIntValue(String fieldName) throws BulkValidatorException {
            String prefix = indexPrefixValue(fieldName);
            if (prefix == null) return -1;
            try {
                return Integer.parseInt(fieldName.substring(prefix.length()));
            } catch (Exception ex) {}
            return -2;
        }

        // will return null if valid fieldName, but not indexable
        public static String indexPrefixValue(String fieldName) throws BulkValidatorException {
            if (!isValidFieldName(fieldName)) throw new BulkValidatorException("invalid fieldName: " + fieldName, ApiException.ERROR_RETURN_CODE_INVALID);
            for (String prefix : FIELD_NAMES_INDEXABLE) {
                String dotFix = prefix.replace(".", "\\.");
                if (fieldName.matches("^" + dotFix + "\\d+$")) return prefix;
            }
            return null;
        }

        public static Object validateValue(String fieldName, Object value) throws BulkValidatorException {
            if (!isValidFieldName(fieldName)) throw new BulkValidatorException("invalid fieldName: " + fieldName, ApiException.ERROR_RETURN_CODE_INVALID);
            switch (fieldName) {
            case "Encounter.year":
            case "Occurrence.year":
                Integer intVal = tryInteger(value);
                if (intVal < 1000) throw new BulkValidatorException("year value too small", ApiException.ERROR_RETURN_CODE_INVALID);
                if (intVal > Year.now().getValue()) throw new BulkValidatorException("year cannot be in future", ApiException.ERROR_RETURN_CODE_INVALID);
                return intVal;

            case "Encounter.month":
            case "Occurrence.month":
                intVal = tryInteger(value);
                if (intVal < 1) throw new BulkValidatorException("month value too small", ApiException.ERROR_RETURN_CODE_INVALID);
                if (intVal > 12) throw new BulkValidatorException("month value too large", ApiException.ERROR_RETURN_CODE_INVALID);
                return intVal;

            case "Encounter.day":
            case "Occurrence.day":
                intVal = tryInteger(value);
                if (intVal < 1) throw new BulkValidatorException("day value too small", ApiException.ERROR_RETURN_CODE_INVALID);
                if (intVal > 31) throw new BulkValidatorException("month value too large", ApiException.ERROR_RETURN_CODE_INVALID);
                // note: to validate upper bound based on month, this must be done through BulkImportUtil.validateRow()
                return intVal;

            case "Encounter.decimalLatitude":
            case "Occurrence.decimalLatitude":
                Double doubleVal = tryDouble(value);
                if (!Util.isValidDecimalLatitude(doubleVal)) throw new BulkValidatorException("invalid decimalLatitude value: " + doubleVal, ApiException.ERROR_RETURN_CODE_INVALID);
                return doubleVal;

            case "Encounter.decimalLongitude":
            case "Occurrence.decimalLongitude":
                doubleVal = tryDouble(value);
                if (!Util.isValidDecimalLongitude(doubleVal)) throw new BulkValidatorException("invalid decimalLongitude value: " + doubleVal, ApiException.ERROR_RETURN_CODE_INVALID);
                return doubleVal;
            }
            throw new BulkValidatorException("unknown error on fieldName validation: " + fieldName, ApiException.ERROR_RETURN_CODE_UNKNOWN);
        }

        private static Integer tryInteger(Object value) throws BulkValidatorException {
            if (value == null) return null;
            if (value instanceof Integer) return (Integer)value;
            try {
                return Integer.valueOf(value.toString());
            } catch (Exception ex) {
                throw new BulkValidatorException("error parsing integer: " + ex, ApiException.ERROR_RETURN_CODE_INVALID);
            }
        }

        private static Double tryDouble(Object value) throws BulkValidatorException {
            if (value == null) return null;
            if (value instanceof Double) return (Double)value;
            try {
                return Double.valueOf(value.toString());
            } catch (Exception ex) {
                throw new BulkValidatorException("error parsing double: " + ex, ApiException.ERROR_RETURN_CODE_INVALID);
            }
        }
}

