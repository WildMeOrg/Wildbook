const specifiedColumns = [
  "Encounter.mediaAsset0",
  "Encounter.year",
  "Encounter.genus",
  "Encounter.decimalLatitude",
  "Encounter.locationID",
  "Encounter.country",
  "Encounter.occurrenceID",
  "MarkedIndividual.individualID",
  "Encounter.sex",
  "Encounter.lifeStage",
  "Encounter.livingStatus",
  "Encounter.behavior",
  "Encounter.submitterID",
  "Encounter.occurrenceRemarks",
  "Encounter.verbatimLocality",
  "Encounter.dateInMilliseconds",
  "Encounter.researcherComments",
  "Encounter.photographer0.emailAddress",
  "Encounter.informOther0.emailAddress",
  "TissueSample.sampleID",
  "SexAnalysis.sex",
];

const removedColumns = [
  "Encounter.month",
  "Encounter.day",
  "Encounter.hour",
  "Encounter.minutes",
  "Encounter.seconds",
  "Encounter.decimalLongitude",
  "Encounter.specificEpithet",
];

const tableHeaderMapping = {
  "Encounter.mediaAsset0": "Media Assets",
  "Encounter.genus": "Species",
  "MarkedIndividual.individualID": "Individual name",
  "Encounter.sightingID": "sighting ID",
  "Encounter.sightingRemarks": "sighting Remarks",
  "Encounter.locationID": "locationID",
  "Encounter.country": "country",
  "Encounter.decimalLatitude": "Lat, long (DD)",
  "Encounter.year": "date",
  "Encounter.sex": "sex",
  "Encounter.lifeStage": "life Stage",
  "Encounter.livingStatus": "living Status",
  "Encounter.behavior": "behavior",
  "Encounter.researcherComments": "researcher Comments",
  "Encounter.submitterID": "submitterID",
  "Encounter.photographer0.emailAddress": "photographer Email",
  "Encounter.informOther0.emailAddress": "informOther Email",
  "TissueSample.sampleID": "sample ID",
};

const columnsUseSelectCell = [
  "Encounter.genus",
  "Encounter.locationID",
  "Encounter.country",
  "Encounter.lifeStage",
  "Encounter.livingStatus",
  "Encounter.sex",
  "Encounter.behavior",
];

const stringRule = {
  required: false,
  validate: (val) => {
    if (!val) return true;
    // return /^[a-zA-Z0-9\s.,-]+$/.test(val);
    return typeof val === "string" || val instanceof String;
  },
  message: "Must be a string",
};

const intRule = {
  required: false,
  validate: (val) => {
    if (!val) return true;
    return /^-?\d+$/.test(val);
  },
  message: "Must be an integer",
};

const doubleRule = {
  required: false,
  validate: (val) => {
    if (!val) return true;
    return /^-?\d+(\.\d+)?$/.test(val);
  },
  message: "Must be a double",
};

const extraStringCols = [
  "Encounter.sightingID",
  "Encounter.sightingRemarks",
  "Encounter.verbatimLocality",
  "TissueSample.sampleID",
  "TissueSample.sampleID",
  "SexAnalysis.sex",
  "researcher Comments",
  "Encounter.sightingID",
  "Encounter.sightingRemarks",
  "Encounter.researcherComments",
  "MarkedIndividual.individualID",
  "Encounter.occurrenceID",
  "Encounter.location",
];

const allColumns_sighting = [
  "Encounter.mediaAsset0",
  "MarkedIndividual.individualID",
  "Encounter.sightingID",
  "EncountersightingRemarks",
  "Encounter.verbatimLocality",
  "Encounter.locationID",
  "Encounter.country",
  "Encounter.decimalLatitude",
  "Encounter.decimalLongitude",
  "Encounter.year",
  "Encounter.month",
  "Encounter.day",
  "Encounter.hour",
  "Encounter.minutes",
  "Encounter.dateInMilliseconds",
  "Encounter.genus",
  "Encounter.specificEpithet",
  "Encounter.sex",
  "Encounter.lifeStage",
  "Encounter.livingStatus",
  "Encounter.behavior",
  "Encounter.researcherComments",
  "Encounter.submitterID",
  "TissueSample.sampleID",
  "SexAnalysis.sex",
  // Pulled from backend
  "Encounter.alternateID",
  "Encounter.depth",
  "Encounter.distinguishingScar",
  "Encounter.elevation",
  "Encounter.groupRole",
  "Encounter.identificationRemarks",
  "Encounter.individualID",
  "Encounter.informOther",
  "Encounter.latitude",
  "Encounter.longitude",
  "Encounter.measurement",
  "Encounter.sightingID",
  "Encounter.sightingRemarks",
  "Encounter.otherCatalogNumbers",
  "Encounter.patterningCode",
  "Encounter.photographer",
  "Encounter.project",
  "Encounter.quality",
  "Encounter.state",
  "Encounter.submitter",
  "Encounter.submitterName",
  "Encounter.submitterOrganization",
  "MarkedIndividual.name",
  "MarkedIndividual.nickname",
  "MarkedIndividual.nickName",
  "Membership.role",
  "MicrosatelliteMarkersAnalysis.alleleNames",
  "MicrosatelliteMarkersAnalysis.alleles#",
  "MicrosatelliteMarkersAnalysis.analysisID",
  "MitochondrialDNAAnalysis.haplotype",
  "Sighting.bearing",
  "Sighting.bestGroupSizeEstimate",
  "Sighting.comments",
  "Sighting.dateInMilliseconds",
  "Sighting.day",
  "Sighting.decimalLatitude",
  "Sighting.decimalLongitude",
  "Sighting.distance",
  "Sighting.effortCode",
  "Sighting.fieldStudySite",
  "Sighting.fieldSurveyCode",
  "Sighting.groupBehavior",
  "Sighting.groupComposition",
  "Sighting.groupSize",
  "Sighting.hour",
  "Sighting.humanActivityNearby",
  "Sighting.individualCount",
  "Sighting.initialCue",
  "Sighting.maxGroupSizeEstimate",
  "Sighting.millis",
  "Sighting.minGroupSizeEstimate",
  "Sighting.minutes",
  "Sighting.month",
  "Sighting.numAdultFemales",
  "Sighting.numAdultMales",
  "Sighting.numAdults",
  "Sighting.numCalves",
  "Sighting.numJuveniles",
  "Sighting.numSubAdults",
  "Sighting.numSubFemales",
  "Sighting.numSubMales",
  "Sighting.observer",
  "Sighting.sightingID",
  "Sighting.seaState",
  "Sighting.seaSurfaceTemp",
  "Sighting.seaSurfaceTemperature",
  "Sighting.swellHeight",
  "Sighting.taxonomy#",
  "Sighting.terrain",
  "Sighting.transectBearing",
  "Sighting.transectName",
  "Sighting.vegetation",
  "Sighting.visibilityIndex",
  "Sighting.year",
  "SatelliteTag.serialNumber",
  "SexAnalysis.processingLabTaskID",
  "SocialUnit.socialUnitName",
  "Survey.comments",
  "Survey.id",
  "Survey.type",
  "SurveyTrack.vesselID",
  "Survey.vessel",
  "Taxonomy.commonName",
  "Taxonomy.scientificName",
  "TissueSample.tissueType",
  "Encounter.sightingRemarks_1",
];

const allColumns_occurrence = allColumns_sighting
  .filter((col) => {
    return col.includes("sighting") || col.includes("Sighting");
  })
  .map((col) => {
    return col
      .replace("Sighting", "Occurrence")
      .replace("sighting", "occurrence");
  });

const allColumns = allColumns_occurrence.concat(allColumns_sighting);

export {
  specifiedColumns,
  removedColumns,
  tableHeaderMapping,
  columnsUseSelectCell,
  doubleRule,
  intRule,
  stringRule,
  extraStringCols,
  allColumns,
};
