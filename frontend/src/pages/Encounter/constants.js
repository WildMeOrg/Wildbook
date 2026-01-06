
const SECTION_FIELD_PATHS = {
  date: ["date", "verbatimEventDate"],
  identify: [
    "individualDisplayName",
    "otherCatalogNumbers",
    "identificationRemarks",
    "occurrenceId",
    "sightingId",
    "individualId",
  ],
  metadata: [
    "id",
    "submitterID",
    "state",
    "observationComments",
  ],
  location: [
    "verbatimLocality",
    "locationId",
    "locationName",
    "country",
    "locationGeoPoint",
  ],
  attributes: [
    "taxonomy",
    "livingStatus",
    "sex",
    "distinguishingScar",
    "behavior",
    "groupRole",
    "patterningCode",
    "lifeStage",
    "occurrenceRemarks",
  ],
};

const LOCAL_FIELD_ERRORS = {
  date: {
    date: "invalid date"
  },
  location: {
    latitude: "please enter invalid latitude and longitude",
    longitude: "please enter invalid latitude and longitude",
  },
  measurements: {
    measurements: "please enter valid values",
  }
};

export { SECTION_FIELD_PATHS, LOCAL_FIELD_ERRORS };