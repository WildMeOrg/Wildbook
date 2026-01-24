const SECTION_FIELD_PATHS = {
  date: ["date", "dateValues", "verbatimEventDate"],
  identify: [
    "individualDisplayName",
    "otherCatalogNumbers",
    "identificationRemarks",
    "occurrenceId",
    "sightingId",
    "individualId",
  ],
  metadata: ["id", "submitterID", "state", "observationComments"],
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
    dateValues: "INVALID_DATE",
  },
  location: {
    latitude: "INVALID_LAT_LON",
    longitude: "INVALID_LAT_LON",
  },
  measurements: {
    measurements: "INVALID_MEASUREMENTS",
  },
};

export { SECTION_FIELD_PATHS, LOCAL_FIELD_ERRORS };
