// validates field + value(s)
package org.ecocean.api.bulk;

import org.json.JSONObject;
import java.util.Set;


public class BulkValidator {
	public static final Set<String> FIELD_NAMES = Set.of(
		"Encounter.alternateID",
		"Encounter.behavior",
		"Encounter.country",
		"Encounter.dateInMilliseconds",
		"Encounter.day",
		"Encounter.decimalLatitiude",
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
        );

	public static final Set<String> FIELD_NAMES_INDEXABLE = Set.of(
		"Encounter.keyword",
		"Encounter.mediaAsset",
		"MicrosatelliteMarkersAnalysis.alleles",
		"Occurrence.taxonomy"
	);

	//public BulkValidator(String fieldName, JSONObject jvalue) throws BulkValidatorException {

	public BulkValidator(String fieldName, Object jvalue) throws BulkValidatorException {
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
            if (!isValidFieldName(fieldName)) throw new BulkValidatorException();
            for (String prefix : FIELD_NAMES_INDEXABLE) {
                String dotFix = prefix.replace(".", "\\.");
                if (fieldName.matches("^" + dotFix + "\\d+$")) return prefix;
            }
            return null;
        }
}

