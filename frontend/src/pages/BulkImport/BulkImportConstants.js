
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
  "Encounter.decimalLongitude",
  "Encounter.specificEpithet",
];

const tableHeaderMapping = {
  "Encounter.mediaAsset0": "Media Assets",
  "Encounter.genus": "Species",
  "MarkedIndividual.individualID": "Individual name",
  "Encounter.sightingID": "sighting ID",
  "Encounter.sightingRemarks": "sighting Remarks",
  "Encounter.locationID": "location",
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

const columnsUseSelectCell = ["Encounter.genus", "Encounter.locationID", "Encounter.country", "Encounter.lifeStage", "Encounter.livingStatus", "Encounter.sex", "Encounter.behavior"];

const stringRule = {
  required: false,
  validate: (val) => {
    if (!val) return true;                 
    return /^[a-zA-Z0-9\s.,-]+$/.test(val); 
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
];

const specializedColumns = [
  "Encounter.mediaAsset0",
  "Encounter.year",
  "Encounter.genus",
  "Encounter.decimalLatitude",
  "Encounter.locationID",
  "Encounter.country",
  "Encounter.livingStatus",
  "Encounter.lifeStage",
  "Encounter.sex",
  "Encounter.behavior",
  "Encounter.occurrenceRemarks",
  "Encounter.photographer0.emailAddress",
  "Encounter.informOther0.emailAddress",  
  "Encounter.submitterID",
  "Encounter.dateInMilliseconds",
];



export { specifiedColumns, removedColumns, tableHeaderMapping, columnsUseSelectCell, doubleRule, intRule, stringRule, extraStringCols, specializedColumns };