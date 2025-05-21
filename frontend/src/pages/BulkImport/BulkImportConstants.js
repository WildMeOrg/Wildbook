
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
  "Encounter.occurrenceID": "occurrence ID",
  "Encounter.occurrenceRemarks": "occurrence Remarks",
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

const NUMERIC_COLS = [
  "Encounter.count",
  "Encounter.weight",
  "Encounter.length",
];

const numericRule = {
  required: false,
  validate: (val) => {
    if (!val) return true;                 
    return /^-?\d+(\.\d+)?$/.test(val);   
  },
  message: "Must be a number",
};

export { specifiedColumns, removedColumns, tableHeaderMapping, columnsUseSelectCell, NUMERIC_COLS, numericRule };