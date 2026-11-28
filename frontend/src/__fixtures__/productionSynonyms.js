// Production synonym groups, copied from
// src/main/java/org/ecocean/api/bulk/BulkValidator.java FIELD_NAME_SYNONYMS.
// Keep in step with that array.
export const PRODUCTION_SYNONYMS = [
  ["Encounter.id", "Encounter.catalogNumber"],
  ["Encounter.sightingID", "Sighting.sightingID"],
  ["Encounter.individualID", "MarkedIndividual.individualID"],
  [
    "Encounter.latitude",
    "Encounter.decimalLatitude",
    "Sighting.decimalLatitude",
  ],
  [
    "Encounter.longitude",
    "Encounter.decimalLongitude",
    "Sighting.decimalLongitude",
  ],
  [
    "Sighting.dateInMilliseconds",
    "Sighting.millis",
    "Encounter.dateInMilliseconds",
    "Encounter.year",
    "Sighting.year",
  ],
  ["Sighting.year", "Encounter.year"],
  ["Sighting.month", "Encounter.month"],
  ["Sighting.day", "Encounter.day"],
  ["Sighting.hour", "Encounter.hour"],
  ["Sighting.minutes", "Encounter.minutes"],
  ["MarkedIndividual.nickName", "MarkedIndividual.nickname"],
  ["Sighting.fieldSurveyCode", "Survey.id"],
  ["Sighting.seaSurfaceTemp", "Sighting.seaSurfaceTemperature"],
  ["Survey.vessel", "SurveyTrack.vesselID"],
  [
    "TissueSample.sampleID",
    "MicrosatelliteMarkersAnalysis.analysisID",
    "SexAnalysis.processingLabTaskID",
  ],
];
