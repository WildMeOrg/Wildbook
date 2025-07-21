const allRequiredColumns = [
  "Encounter.year",
  "Encounter.genus",
  // "Encounter.specificEpithet",
  "Encounter.locationID",
];

const specifiedColumns = [
  "Encounter.mediaAsset0",
  "Encounter.year",
  "Sighting.year",
  "Encounter.genus",
  "Encounter.decimalLatitude",
  "Encounter.latitude",
  "Sighting.decimalLatitude",
  "Encounter.locationID",
  "Encounter.submitterID",
  "Encounter.country",
  "Encounter.sightingID",
  "MarkedIndividual.individualID",
  "Encounter.sex",
  "Encounter.lifeStage",
  "Encounter.livingStatus",
  "Encounter.behavior",
  "Encounter.sightingRemarks",
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
  "Sighting.decimalLongitude",
  "Occurrence.decimalLongitude",
  "Encounter.longitude",
  "Sighting.month",
  "Sighting.day",
  "Sighting.hour",
  "Sighting.minutes",
  "Sighting.seconds",
];

const tableHeaderMapping = {
  "Encounter.mediaAsset0": "Media Assets",
  "Encounter.genus": "Species*",
  // "MarkedIndividual.individualID": "Individual name",
  // "Encounter.sightingID": "sighting ID",
  // "Encounter.sightingRemarks": "sighting Remarks",
  "Encounter.locationID": "locationID*+",
  // "Encounter.country": "country",
  "Encounter.decimalLatitude": "Encounter.decimalLatitude Lat, long (DD)",
  "Encounter.latitude": "Encounter.latitude Lat, long (DD)",
  "Sighting.decimalLatitude": "Sighting.decimalLatitude Lat, long (DD)",
  "Encounter.year": "Encounter date*",
  "Sighting.year": "Sighting date",
  // "Encounter.sex": "sex",
  // "Encounter.lifeStage": "life Stage",
  // "Encounter.livingStatus": "living Status",
  // "Encounter.behavior": "behavior",
  // "Encounter.researcherComments": "researcher Comments",
  "Encounter.submitterID": "submitterID+",
  // "Encounter.photographer0.emailAddress": "photographer Email",
  // "Encounter.informOther0.emailAddress": "informOther Email",
  // "TissueSample.sampleID": "sample ID",
};

const columnsUseSelectCell = [
  "Encounter.genus",
  "Encounter.locationID",
  "Encounter.country",
  "Encounter.lifeStage",
  "Encounter.livingStatus",
  "Encounter.sex",
  // "Encounter.behavior",
  "Encounter.state",
];

const stringRule = {
  required: false,
  validate: (val) => {
    if (!val) return true;
    return typeof val === "string" || val instanceof String;
  },
  message: "BULKIMPORT_ERROR_INVALID_INVALIDSTRING",
};

const intRule = {
  required: false,
  validate: (val) => {
    if (!val) return true;
    return /^-?\d+$/.test(val);
  },
  message: "BULKIMPORT_ERROR_INVALID_INVALIDINTEGER",
};

const doubleRule = {
  required: false,
  validate: (val) => {
    if (!val) return true;
    return /^-?\d+(\.\d+)?$/.test(val);
  },
  message: "BULKIMPORT_ERROR_INVALID_INVALIDNUMBER",
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
  "Encounter.sightingID",
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

const latlongRule = {
  required: false,
  validate: (val) => {
    if (!val) {
      return true;
    }

    const re = /^\s*([-+]?\d+(\.\d+)?)\s*,\s*([-+]?\d+(\.\d+)?)\s*$/;
    const m = re.exec(val);
    if (!m) {
      return false;
    }

    const lat = parseFloat(m[1]);
    const lon = parseFloat(m[3]);

    if (Number.isNaN(lat) || Number.isNaN(lon)) {
      return false;
    }
    if (lat < -90 || lat > 90) {
      return false;
    }
    if (lon < -180 || lon > 180) {
      return false;
    }

    return true;
  },
  message: "BULKIMPORT_ERROR_INVALID_INVALIDLATLONG",
};

const parseEncounterDateString = (field, val, raw) => {
  if (!val) return;

  const set = (suffix, value) => {
    raw[`${field}.${suffix}`] = value;
  };

  if (/^\d{4}$/.test(val)) {
    set("year", Number(val));
    set("month", "");
    set("day", "");
    set("hour", "");
    set("minutes", "");
  } else if (/^\d{4}-\d{2}$/.test(val)) {
    const [y, m] = val.split("-").map(Number);
    set("year", y);
    set("month", m);
    set("day", "");
    set("hour", "");
    set("minutes", "");
  } else if (/^\d{4}-\d{2}-\d{2}$/.test(val)) {
    const [y, m, d] = val.split("-").map(Number);
    set("year", y);
    set("month", m);
    set("day", d);
    set("hour", "");
    set("minutes", "");
  } else if (/^\d{4}-\d{2}-\d{2}T\d{2}$/.test(val)) {
    const [datePart, hour] = val.split("T");
    const [y, m, d] = datePart.split("-").map(Number);
    set("year", y);
    set("month", m);
    set("day", d);
    set("hour", Number(hour));
    set("minutes", "");
  } else {
    const dt = new Date(val);
    if (!isNaN(dt)) {
      set("year", dt.getFullYear());
      set("month", dt.getMonth() + 1);
      set("day", dt.getDate());
      set("hour", dt.getHours());
      set("minutes", dt.getMinutes());
    }
  }
};

export {
  allRequiredColumns,
  specifiedColumns,
  removedColumns,
  tableHeaderMapping,
  columnsUseSelectCell,
  doubleRule,
  intRule,
  stringRule,
  extraStringCols,
  allColumns,
  latlongRule,
  parseEncounterDateString,
};
