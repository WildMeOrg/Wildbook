import { makeAutoObservable } from "mobx";
import axios from "axios";
import convertToTreeDataWithName from "../../utils/converToTreeData";
import { debounce } from "lodash";
import { toJS } from "mobx";
import dayjs from "dayjs";
import Flow from "@flowjs/flow.js";
import customParseFormat from "dayjs/plugin/customParseFormat";
import { v4 as uuidv4 } from "uuid";
dayjs.extend(customParseFormat);

const SECTION_FIELD_PATHS = {
  date: ["date", "verbatimEventDate"],
  identify: [
    "individualDisplayName",
    "otherCatalogNumbers",
    "identificationRemarks",
    "sightingId",
    "individualID",
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
};

function validateFieldValue(sectionName, fieldPath, value, ctx = {}) {
  const errors = LOCAL_FIELD_ERRORS[sectionName] || {};
  const errorMessage = errors[fieldPath];
  if (!errorMessage) return null;

  if (sectionName === "date") {
    if (value == null || value === "") return null;
    const FORMATS = [
      "YYYY",
      "YYYY-MM",
      "YYYY-MM-DD",
      "YYYY-MM-DDTHH",
      "YYYY-MM-DDTHH:mm",
      "YYYY-MM-DDTHH:mm[Z]",
    ];
    let parsed;
    if (value instanceof Date) {
      parsed = dayjs(value);
    } else {
      parsed = dayjs(String(value).trim(), FORMATS, true);
    }

    if (!parsed.isValid() || parsed.isAfter(dayjs())) {
      return errorMessage;
    }
    return null;
  }

  if (sectionName === "location") {
    const isEmpty = (v) => v === "" || v == null;

    const lat = fieldPath === "latitude" ? value : ctx.lat;
    const lon = fieldPath === "longitude" ? value : ctx.lon;

    if (isEmpty(lat) && isEmpty(lon)) return null;

    if (fieldPath === "latitude") {
      if (isEmpty(value)) return errorMessage;
      const n = Number(value);
      if (!Number.isFinite(n) || n < -90 || n > 90) return errorMessage;
      return null;
    }

    if (fieldPath === "longitude") {
      if (isEmpty(value)) return errorMessage;
      const n = Number(value);
      if (!Number.isFinite(n) || n < -180 || n > 180) return errorMessage;
      return null;
    }
  }

  return null;
}


function splitPathIntoSegments(fieldPath) {
  return fieldPath.replace(/\[(\d+)\]/g, ".$1").split(".");
}

function getValueAtPath(targetObject, fieldPath) {
  const pathSegments = splitPathIntoSegments(fieldPath);
  return pathSegments.reduce((currentValue, pathSegment) => {
    if (currentValue == null) return undefined;
    return currentValue[pathSegment];
  }, targetObject);
}

function setValueAtPath(targetObject, fieldPath, newValue) {
  const pathSegments = splitPathIntoSegments(fieldPath);
  let cursor = targetObject;
  for (let i = 0; i < pathSegments.length - 1; i++) {
    const segment = pathSegments[i];
    const nextSegment = pathSegments[i + 1];
    const nextIsArrayIndex = /^\d+$/.test(nextSegment);
    if (cursor[segment] == null || typeof cursor[segment] !== "object") {
      cursor[segment] = nextIsArrayIndex ? [] : {};
    }
    cursor = cursor[segment];
  }
  const lastSegment = pathSegments[pathSegments.length - 1];
  cursor[lastSegment] = newValue;
}

function deleteValueAtPath(targetObject, fieldPath) {
  const pathSegments = splitPathIntoSegments(fieldPath);
  let cursor = targetObject;
  for (let i = 0; i < pathSegments.length - 1; i++) {
    const segment = pathSegments[i];
    if (cursor[segment] == null) return;
    cursor = cursor[segment];
  }
  const lastSegment = pathSegments[pathSegments.length - 1];
  const isArrayIndex = /^\d+$/.test(lastSegment);
  if (Array.isArray(cursor) && isArrayIndex) {
    cursor.splice(Number(lastSegment), 1);
  } else {
    delete cursor[lastSegment];
  }
}

class EncounterStore {
  _encounterData = null;

  _siteSettingsData = null;

  _overviewActive = true;
  _editDateCard = false;
  _editIdentifyCard = false;
  _editMetadataCard = false;
  _editLocationCard = false;
  _editAttributesCard = false;

  _fieldErrors = new Map();

  _lat = null;
  _lon = null;

  _showAnnotations = true;

  _openContactInfoModal = false;
  _openEncounterHistoryModal = false;
  _openAddPeopleModal = false;
  _openMatchCriteriaModal = false;

  _newPersonName = '';
  _newPersonEmail = '';
  _newPersonRole = '';

  _individualSearchInput = "";
  _searchingIndividuals = false;
  _individualSearchResults = [];
  _searchingSightings = false;
  _sightingSearchResults = [];
  _sightingSearchInput = "";

  _tags = [];
  _addTagsFieldOpen = false;
  _availableKeywords = [];
  _availableKeywordsId = [];
  _selectedKeyword = null;
  _availabelLabeledKeywords = [];
  _labeledKeywordAllowedValues = [];
  _selectedLabeledKeyword = null;
  _selectedAllowedValues = [];

  _taxonomyOptions = [];
  _livingStatusOptions = [];
  _sexOptions = [];
  _lifeStageOptions = [];
  _behaviorOptions = [];
  _groupRoleOptions = [];
  _patterningCodeOptions = [];
  _locationIdOptions = [];
  _identificationRemarksOptions = [];
  // Algorithm options for matching
  _algorithmOptions = [
    { label: "MiewID Matcher", value: "MiewID Matcher" },
    { label: "HotSpotter Pattern Matcher", value: "HotSpotter Pattern Matcher" },
  ];
  _metalTagLocation = [];
  _metalTagValues = [];
  _acousticTagValues = {};
  _satelliteTagValues = {};

  _selectedImageIndex = 0;
  _encounterAnnotations = null;
  _selectedAnnotationId = null;

  flow = null;
  imageSubmissionId = null;
  uploadProgress = 0;
  uploadErrors = [];

  _matchResultClickable = false;

  _selectedMatchLocation = "";
  _owner = "";

  _measurementsAndTrackingSection = true;
  _editTracking = false;
  _editMeasurements = false;
  _acousticTagsEnabled = false;
  _satelliteTagsEnabled = false;

  _biologicalSamplesSection = false;
  _editBiologicalSamples = false;

  _projectsSection = false;
  _selectedProjects = null;

  _sectionDrafts = new Map(
    Object.keys(SECTION_FIELD_PATHS).map((name) => [name, {}]),
  );

  constructor() {
    makeAutoObservable(this, { flow: false }, { autoBind: true });
  }

  get encounterData() {
    return this._encounterData;
  }
  setEncounterData(newEncounterData) {
    this._encounterData = newEncounterData;
    this._lat = newEncounterData?.locationGeoPoint?.lat || null;
    this._lon = newEncounterData?.locationGeoPoint?.lon || null;
    this._metalTagValues = newEncounterData?.metalTags || [];
    this._acousticTagValues = newEncounterData?.acousticTag || {};
    this._satelliteTagValues = newEncounterData?.satelliteTag || {};
    this.resetAllDrafts();
  }

  get fieldErrors() {
    return this._fieldErrors;
  }

  getFieldError(sectionName, fieldPath) {
    return this._fieldErrors.get(`${sectionName}.${fieldPath}`) || null;
  }

  setFieldError(sectionName, fieldPath, errorMsg) {
    const key = `${sectionName}.${fieldPath}`;
    if (errorMsg) {
      this._fieldErrors.set(key, errorMsg);
    } else {
      this._fieldErrors.delete(key);
    }
  }

  get overviewActive() {
    return this._overviewActive;
  }
  setOverviewActive(isActive) {
    this._overviewActive = isActive;
  }

  get editDateCard() {
    return this._editDateCard;
  }
  setEditDateCard(isEditing) {
    this._editDateCard = isEditing;
  }

  get editIdentifyCard() {
    return this._editIdentifyCard;
  }
  setEditIdentifyCard(isEditing) {
    this._editIdentifyCard = isEditing;
  }

  get editMetadataCard() {
    return this._editMetadataCard;
  }
  setEditMetadataCard(isEditing) {
    this._editMetadataCard = isEditing;
  }

  get editLocationCard() {
    return this._editLocationCard;
  }
  setEditLocationCard(isEditing) {
    this._editLocationCard = isEditing;
  }

  get editAttributesCard() {
    return this._editAttributesCard;
  }
  setEditAttributesCard(isEditing) {
    this._editAttributesCard = isEditing;
  }

  get searchingIndividuals() {
    return this._searchingIndividuals;
  }

  get individualSearchResults() {
    return this._individualSearchResults;
  }

  get searchingSightings() {
    return this._searchingSightings;
  }

  get sightingSearchResults() {
    return this._sightingSearchResults;
  }

  get individualSearchInput() {
    return this._individualSearchInput;
  }

  get sightingSearchInput() {
    return this._sightingSearchInput;
  }

  setIndividualSearchInput(input) {
    this._individualSearchInput = input;
    this.debouncedSearchIndividuals(input);
  }

  setSightingSearchInput(input) {
    this._sightingSearchInput = input;
    this.debouncedSearchSightings(input);
  }

  debouncedSearchIndividuals = debounce(async (inputValue) => {
    if (inputValue && inputValue.length >= 2) {
      await this.searchIndividualsByName(inputValue);
    } else {
      this.clearIndividualSearchResults();
    }
  }, 300);

  debouncedSearchSightings = debounce(async (inputValue) => {
    if (inputValue && inputValue.length >= 2) {
      await this.searchSightingsById(inputValue);
    } else {
      this.clearSightingSearchResults();
    }
  }, 300);

  clearIndividualSearchResults() {
    this._individualSearchResults = [];
  }

  get newPersonName() {
    return this._newPersonName;
  }
  setNewPersonName(name) {
    this._newPersonName = name;
  }

  get newPersonEmail() {
    return this._newPersonEmail;
  }
  setNewPersonEmail(email) {
    this._newPersonEmail = email;
  }

  get newPersonRole() {
    return this._newPersonRole;
  }
  setNewPersonRole(role) {
    this._newPersonRole = role;
  }

  get openAddPeopleModal() {
    return this._openAddPeopleModal;
  }
  setOpenAddPeopleModal(isOpen) {
    this._openAddPeopleModal = isOpen;
  }

  get openMatchCriteriaModal() {
    return this._openMatchCriteriaModal;
  }
  setOpenMatchCriteriaModal(isOpen) {
    this._openMatchCriteriaModal = isOpen;
  }

  get selectedLocation() {
    return this._selectedMatchLocation;
  }
  setSelectedLocation(location) {
    this._selectedMatchLocation = location
  }

  get owner() {
    return this._owner;
  }
  setMyData(owner) {
    this._owner = owner;
  }

  get selectedAlgorithm() {
    return this._selectedAlgorithm;
  }
  setselectedAlgorithm(algorithm) {
    this._selectedAlgorithm = algorithm;
  }

  async addNewPerson() {
    const result = await axios.patch(`/api/v3/encounters/${this._encounterData.id}`, [
      { op: "add", path: this._newPersonRole, value: this._newPersonEmail },
    ]);
    if (result.status === 200) {
      this.setOpenAddPeopleModal(false);
      this._newPersonName = '';
      this._newPersonEmail = '';
      this._newPersonRole = '';
    } else {
      console.error("Failed to add new person:", result);
    }
  }

  async removeContact(type, uuid) {
    const data = await axios.patch(`/api/v3/encounters/${this._encounterData.id}`, [
      { op: "remove", path: type, value: uuid }]);
    if (data.status === 200) {
      this._encounterData[type] = this._encounterData[type].filter(item => item.id !== uuid);
      this.setOpenContactInfoModal(false);
    } else {
      console.error("Failed to remove contact:", data);
    }
  }

  async addEncounterToProject() {
    if (!this._selectedProjects) {
      console.error("No project selected to add the encounter to.");
      return;
    }
    const payload = {
      projects: toJS(this._selectedProjects.map(project => ({
        id: project.id,
        encountersToAdd: [this._encounterData.id],
      })))
    };

    const result = await axios.post("/ProjectUpdate", payload, {
      headers: { "Content-Type": "application/json" },
    })
    if (result.status === 200) {
      this.refreshEncounterData();
    }
    else {
      console.error("Failed to add encounter to project:", result);
    }
  }

  async removeProjectFromEncounter(projectId) {
    const payload = {
      projects: [
        {
          id: projectId,
          encountersToRemove: [this._encounterData.id],
        }
      ]
    }
    const result = await axios.post("/ProjectUpdate", payload, {
      headers: { "Content-Type": "application/json" },
    })
    if (result.status === 200) {
      this.refreshEncounterData();
    }
    else {
      console.error("Failed to add encounter to project:", result);
    }
  }

  get measurementsAndTrackingSection() {
    return this._measurementsAndTrackingSection;
  }
  setMeasurementsAndTrackingSection(isEnabled) {
    this._measurementsAndTrackingSection = isEnabled;
  }

  get editTracking() {
    return this._editTracking;
  }
  setEditTracking(isEditing) {
    this._editTracking = isEditing;
  }

  get editMeasurements() {
    return this._editMeasurements;
  }
  setEditMeasurements(isEditing) {
    this._editMeasurements = isEditing;
  }

  get biologicalSamplesSection() {
    return this._biologicalSamplesSection;
  }
  setBiologicalSamplesSection(isEnabled) {
    this._biologicalSamplesSection = isEnabled;
  }

  get editBiologicalSamples() {
    return this._editBiologicalSamples;
  }
  setEditBiologicalSamples(isEditing) {
    this._editBiologicalSamples = isEditing;
  }

  get projectsSection() {
    return this._projectsSection;
  }
  setProjectsSection(isEnabled) {
    this._projectsSection = isEnabled;
  }

  get selectedProjects() {
    return this._selectedProjects;
  }
  setSelectedProjects(projectId) {
    this._selectedProjects = projectId;
  }

  get selectedImageIndex() {
    return this._selectedImageIndex;
  }
  setSelectedImageIndex(index) {
    this._selectedImageIndex = index;
  }

  get encounterAnnotations() {
    return this.encounterData?.mediaAssets?.[this._selectedImageIndex]?.annotations?.filter(data => data.encounterId === this.encounterData.id) || [];
  }

  get selectedAnnotationId() {
    return this._selectedAnnotationId;
  }
  setSelectedAnnotationId(annotationId) {
    this._selectedAnnotationId = annotationId;
  }

  get matchResultClickable() {
    const selectedAnnotation = this.encounterAnnotations?.find(
      (annotation) => annotation.id === this.selectedAnnotationId
    ) || [];
    const iaTaskId = !!selectedAnnotation?.iaTaskId;
    const skipId = !!selectedAnnotation?.iaTaskParameters?.skipIdent;
    const identActive = iaTaskId && !skipId;
    const detectionComplete = this.encounterData?.mediaAssets?.[this._selectedImageIndex]?.detectionStatus === "complete";
    const identificationStatus = selectedAnnotation?.identificationStatus === "complete" || selectedAnnotation?.identificationStatus === "pending";

    return identActive && (detectionComplete || identificationStatus);
  }

  get lat() {
    return this._lat;
  }
  setLat(newLat) {
    this.setFieldError("location", "latitude", null);
    this._lat = newLat;
    this.setFieldError("location", "latitude",
      validateFieldValue("location", "latitude", newLat, { lat: newLat, lon: this._lon })
    );
    this.setFieldError("location", "longitude",
      validateFieldValue("location", "longitude", this._lon, { lat: newLat, lon: this._lon })
    );
  }

  get lon() {
    return this._lon;
  }
  setLon(newLon) {
    this.setFieldError("location", "longitude", null);
    this._lon = newLon;
    this.setFieldError("location", "longitude",
      validateFieldValue("location", "longitude", newLon, { lat: this._lat, lon: newLon })
    );
    this.setFieldError("location", "latitude",
      validateFieldValue("location", "latitude", this._lat, { lat: this._lat, lon: newLon })
    );
  }

  get showAnnotations() {
    return this._showAnnotations;
  }
  setShowAnnotations(show) {
    this._showAnnotations = show;
  }

  get openContactInfoModal() {
    return this._openContactInfoModal;
  }
  setOpenContactInfoModal(isOpen) {
    this._openContactInfoModal = isOpen;
  }

  get openEncounterHistoryModal() {
    return this._openEncounterHistoryModal;
  }
  setOpenEncounterHistoryModal(isOpen) {
    this._openEncounterHistoryModal = isOpen;
  }

  get tags() {
    return this._encounterData?.mediaAssets?.[this._selectedImageIndex]?.keywords || [];
  }
  setTags(newTags) {
    this._tags = newTags;
  }

  get addTagsFieldOpen() {
    return this._addTagsFieldOpen;
  }
  setAddTagsFieldOpen(add) {
    this._addTagsFieldOpen = add;
  }

  get availableKeywords() {
    return this._siteSettingsData?.keyword || [];
  }

  get availableKeywordsId() {
    return this._siteSettingsData?.keywordId || [];
  }

  get selectedKeyword() {
    return this._selectedKeyword;
  }
  setSelectedKeyword(keyword) {
    this._selectedKeyword = keyword;
  }

  get availabelLabeledKeywords() {
    return Object.keys(this._siteSettingsData?.labeledKeyword) || [];
  }

  get labeledKeywordAllowedValues() {
    return this._siteSettingsData?.labeledKeywordAllowedValues[this.selectedLabeledKeyword] || [];
  }

  get selectedLabeledKeyword() {
    return this._selectedLabeledKeyword;
  }
  setSelectedLabeledKeyword(keyword) {
    this._selectedLabeledKeyword = keyword;
  }

  get selectedAllowedValues() {
    return this._selectedAllowedValues;
  }

  setSelectedAllowedValues(allowedValues) {
    this._selectedAllowedValues = allowedValues;
  }


  get taxonomyOptions() {
    return this._taxonomyOptions;
  }
  get livingStatusOptions() {
    return this._livingStatusOptions;
  }
  get sexOptions() {
    return this._sexOptions;
  }
  get lifeStageOptions() {
    return this._lifeStageOptions;
  }
  get behaviorOptions() {
    return this._behaviorOptions;
  }
  get groupRoleOptions() {
    return this._groupRoleOptions;
  }
  get patterningCodeOptions() {
    return this._patterningCodeOptions;
  }
  get locationIdOptions() {
    if (this._siteSettingsData?.locationData?.locationID) {
      this._locationIdOptions = convertToTreeDataWithName(
        this._siteSettingsData.locationData.locationID,
      );
      return this._locationIdOptions;
    }
    return [];
  }

  get identificationRemarksOptions() {
    if (this._siteSettingsData?.identificationRemarks) {
      this._identificationRemarksOptions = this._siteSettingsData.identificationRemarks.map(
        (data) => ({
          value: data,
          label: data,
        }),
      );
      return this._identificationRemarksOptions;
    }
    return [];
  }

  get metalTagLocation() {
    return this._siteSettingsData?.metalTagLocation || [];
  }

  get metalTagValues() {
    return this._metalTagValues;
  }
  setMetalTagValues(newValues) {
    this._metalTagValues = newValues;
  }

  get acousticTagValues() {
    return this._acousticTagValues;
  }
  setAcousticTagValues(newValues) {
    this._acousticTagValues = { ...this._acousticTagValues, ...newValues };
  }

  get satelliteTagValues() {
    return this._satelliteTagValues;
  }
  setSatelliteTagValues(newValues) {
    this._satelliteTagValues = { ...this._satelliteTagValues, ...newValues };
  }

  get metalTagsEnabled() {
    return this.siteSettingsData?.metalTagsEnabled || false;
  }
  get acousticTagEnabled() {
    return this.siteSettingsData?.acousticTagEnabled || false;
  }
  get satelliteTagEnabled() {
    return this.siteSettingsData?.satelliteTagEnabled || false;
  }

  get satelliteTagNameOptions() {
    return this._siteSettingsData?.satelliteTagName.map((name) => ({
      value: name,
      label: name,
    })) || [];
  }

  get algorithmOptions() {
    return this._algorithmOptions;
  }
  setAlgorithmOptions(algorithmOptions) {
    this._algorithmOptions = algorithmOptions;
  }

  getFieldValue(sectionName, fieldPath) {
    const draftForSection = this._sectionDrafts.get(sectionName) || {};
    if (Object.prototype.hasOwnProperty.call(draftForSection, fieldPath)) {
      return draftForSection[fieldPath];
    }
    return getValueAtPath(this._encounterData, fieldPath);
  }

  setFieldValue(sectionName, fieldPath, newValue) {
    this.setFieldError(sectionName, fieldPath, null);
    const draftForSection = { ...(this._sectionDrafts.get(sectionName) || {}) };
    draftForSection[fieldPath] = newValue;
    this._sectionDrafts.set(sectionName, draftForSection);

    const error = validateFieldValue(sectionName, fieldPath, newValue);
    console.log("error", JSON.stringify(error));
    if (error) {
      this.setFieldError(sectionName, fieldPath, error);
    }
  }

  get siteSettingsData() {
    return this._siteSettingsData;
  }

  setSiteSettings(siteSettingsData) {
    this._siteSettingsData = siteSettingsData;
    this._taxonomyOptions = siteSettingsData.siteTaxonomies?.map(
      (taxonomy) => ({
        value: taxonomy.scientificName,
        label: taxonomy.scientificName,
      }),
    );
    this._livingStatusOptions = siteSettingsData.livingStatus?.map(
      (status) => ({
        value: status,
        label: status,
      }),
    );
    this._sexOptions = siteSettingsData.sex?.map((data) => ({
      value: data,
      label: data,
    }));
    this._lifeStageOptions = siteSettingsData.lifeStage?.map((data) => ({
      value: data,
      label: data,
    }));
    const allOptions = [
      ...(
        siteSettingsData.behaviorOptions[this._encounterData?.species] || []
      ).map((data) => ({
        value: data,
        label: data,
      })),
      ...(siteSettingsData.behaviorOptions[""] || []).map((data) => ({
        value: data,
        label: data,
      })),
      ...(siteSettingsData?.behavior ?? []).map((data) => ({
        value: data,
        label: data,
      })),
    ];
    this._behaviorOptions = [
      ...new Map(allOptions.map((item) => [item.value, item])).values(),
    ];
    this._groupRoleOptions = siteSettingsData.groupRoles?.map((data) => ({
      value: data,
      label: data,
    }));
    this._patterningCodeOptions = siteSettingsData.patterningCode?.map(
      (data) => ({
        value: data,
        label: data,
      }),
    );
  }

  resetSectionDraft(sectionName) {
    this._sectionDrafts.set(sectionName, {});
  }

  resetAllDrafts() {
    Object.keys(SECTION_FIELD_PATHS).forEach((name) =>
      this._sectionDrafts.set(name, {}),
    );
  }

  buildPatchOperations(sectionName) {
    const fieldPaths = SECTION_FIELD_PATHS[sectionName] || [];
    const draftForSection = this._sectionDrafts.get(sectionName) || {};
    const operations = [];

    for (const fieldPath of fieldPaths) {
      const oldValue = getValueAtPath(this._encounterData, fieldPath);
      const hasUserEdited = Object.prototype.hasOwnProperty.call(
        draftForSection,
        fieldPath,
      );
      const newValue = hasUserEdited ? draftForSection[fieldPath] : oldValue;

      const isSame = JSON.stringify(newValue) === JSON.stringify(oldValue);
      if (isSame) continue;

      const isEmptyNewValue = newValue === "" || newValue == null;

      if (isEmptyNewValue) {
        if (oldValue !== undefined)
          operations.push({ op: "remove", path: fieldPath });
      } else if (oldValue === undefined) {
        operations.push({ op: "add", path: fieldPath, value: newValue });
      } else {
        operations.push({ op: "replace", path: fieldPath, value: newValue });
      }
    }
    return operations;
  }

  buildTrackingPatchPayload() {
    const ops = [];

    const originalMetalTags = this._encounterData?.metalTags || [];
    const currentMetalTags = this._metalTagValues || [];

    currentMetalTags.forEach(currentTag => {
      const originalTag = originalMetalTags.find(
        tag => tag.location === currentTag.location
      );

      if (!originalTag || originalTag.number !== currentTag.number) {
        ops.push({
          op: "replace",
          path: "metalTags",
          value: { location: currentTag.location, number: currentTag.number }
        });
      }
    });

    const originalAcoustic = this._encounterData?.acousticTag || {};
    const currentAcoustic = this._acousticTagValues || {};

    const hasAcousticChanges =
      currentAcoustic.serialNumber !== originalAcoustic.serialNumber ||
      currentAcoustic.idNumber !== originalAcoustic.idNumber;

    if (hasAcousticChanges) {
      ops.push({
        op: "replace",
        path: "acousticTag",
        value: {
          ...(currentAcoustic.serialNumber && { serialNumber: currentAcoustic.serialNumber }),
          ...(currentAcoustic.idNumber && { idNumber: currentAcoustic.idNumber })
        }
      });
    }

    const originalSatellite = this._encounterData?.satelliteTag || {};
    const currentSatellite = this._satelliteTagValues || {};

    const hasSatelliteChanges =
      currentSatellite.name !== originalSatellite.name ||
      currentSatellite.serialNumber !== originalSatellite.serialNumber ||
      currentSatellite.argosPttNumber !== originalSatellite.argosPttNumber;

    if (hasSatelliteChanges) {

      ops.push({
        op: "replace",
        path: "satelliteTag",
        value: {
          ...(currentSatellite.name && { name: currentSatellite.name }),
          ...(currentSatellite.serialNumber && { serialNumber: currentSatellite.serialNumber }),
          ...(currentSatellite.argosPttNumber && { argosPttNumber: currentSatellite.argosPttNumber })
        }
      });
    }
    return ops;
  }

  async patchTracking() {
    const ops = this.buildTrackingPatchPayload();
    if (!ops.length) return;
    const resp = await axios.patch(`/api/v3/encounters/${this.encounterData.id}`, ops);
    if (resp.status === 200) {
      await this.refreshEncounterData();
      this.setEditTracking?.(false);
    } else {
      console.error("patchTracking failed:", resp);
    }
  }

  initializeFlow(inputEl, maxSizeMB = 10) {
    if (this.flow) {
      if (inputEl) this.flow.assignBrowse(inputEl);
      return;
    }

    if (!this.imageSubmissionId) {
      this.imageSubmissionId = uuidv4();
    }

    const flow = new Flow({
      target: "/ResumableUpload",
      forceChunkSize: true,
      testChunks: false,
      query: () => ({ submissionId: this.imageSubmissionId }),
    });

    if (inputEl) flow.assignBrowse(inputEl);

    const supported = new Set(["image/jpeg", "image/jpg", "image/png", "image/bmp"]);
    const maxBytes = maxSizeMB * 1024 * 1024;

    flow.on("fileAdded", (file) => {
      const typeOk = supported.has(file?.file?.type || "");
      const sizeOk = file.size <= maxBytes;
      return typeOk && sizeOk;
    });

    flow.on("filesSubmitted", () => {
      flow.upload();
    });

    flow.on("fileSuccess", async (file, message) => {
      try {
        const op = {
          op: "add",
          path: "assets",
          value: {
            submissionId: this.imageSubmissionId,
            filename: file?.file?.name || file?.name || "upload.jpg",
          },
        };
        await axios.patch(`/api/v3/encounters/${this._encounterData.id}`, [op], {
          headers: { "Content-Type": "application/json" },
        });
        alert("Image uploaded successfully, page will be reloaded!");
        window.location.reload();
      } catch (e) {
        console.error("PATCH add assets failed:", e);
      } finally {
        this.uploadProgress = 0;
        this.uploadErrors = [];
      }
    });

    flow.on("fileError", (_file, msg) => {
      console.error("Upload failed:", msg);
    });

    this.flow = flow;
  }

  triggerUploadImage() {
    if (this.flow && this.flow.files && this.flow.files.length > 0) {
      this.flow.upload();
    }
  }

  applyPatchOperationsLocally(operations) {
    if (!Array.isArray(operations) || operations.length === 0) return;
    if (!this._encounterData) return;
    const nextEncounter = JSON.parse(JSON.stringify(this._encounterData));
    for (const operation of operations) {
      const { op, path, value } = operation;
      if (op === "remove") {
        deleteValueAtPath(nextEncounter, path);
      } else {
        setValueAtPath(nextEncounter, path, value);
      }
    }
    this._encounterData = nextEncounter;

  }

  parseYMDHM(val) {
    if (val == null) return null;

    if (val instanceof Date && !isNaN(val)) {
      return {
        year: String(val.getFullYear()).padStart(4, "0"),
        month: String(val.getMonth() + 1).padStart(2, "0"),
        day: String(val.getDate()).padStart(2, "0"),
        hour: String(val.getHours()).padStart(2, "0"),
        minutes: String(val.getMinutes()).padStart(2, "0"),
      };
    }

    const s = String(val).trim();

    const re =
      /^(\d{4})(?:-(\d{2}))?(?:-(\d{2}))?(?:[T\s](\d{2}):(\d{2}))?(?:Z|[+-]\d{2}:\d{2})?$/;
    const m = re.exec(s);
    if (!m) return null;

    const [, Y, M, D, H, Min] = m;
    return {
      year: Y,
      month: M ?? "",
      day: D ?? "",
      hour: H ?? "",
      minutes: Min ?? "",
    };
  }


  removeAnnotation(annotationId) {
    return axios.post(
      "/EncounterRemoveAnnotation",
      {
        annotation: String(annotationId),
        detach: "true",
        number: String(this._encounterData.id),
      },
      {
        headers: { "Content-Type": "application/json" },
      }
    );
  }

  deleteImage() {
    return axios.post(
      "/MediaAssetAttach",
      {
        detach: "true",
        EncounterID: this._encounterData.id,
        MediaAssetID: this._encounterData.mediaAssets[this._selectedImageIndex].id,
      },
      {
        headers: { "Content-Type": "application/json" },
      }
    );
  }

  expandOperations(operations) {
    const base = operations.slice();
    const out = [];

    for (const op of base) {
      if (op.path === "date") {
        const p = this.parseYMDHM(op.value);
        if (!p) continue;
        out.push({ op: "replace", path: "year", value: String(p.year) });
        out.push({
          op: "replace",
          path: "month",
          value: !!p.month ? String(p.month) : null,
        });
        out.push({
          op: "replace",
          path: "day",
          value: !!p.day ? String(p.day) : null,
        });
        out.push({
          op: "replace",
          path: "hour",
          value: !!p.hour ? String(p.hour) : null,
        });
        out.push({
          op: "replace",
          path: "minutes",
          value: !!p.minutes ? String(p.minutes) : null,
        });
        continue;
      }

      if (op.path === "locationGeoPoint" && op.value) {
        const v = op.value || {};
        const lat = v.latitude ?? v.lat;
        const lon = v.longitude ?? v.lng ?? v.lon;
        if (lat != null)
          out.push({ op: "replace", path: "decimalLatitude", value: lat });
        if (lon != null)
          out.push({ op: "replace", path: "decimalLongitude", value: lon });
        continue;
      }

      if (op.path === "taxonomy" && op.value) {
        const s = String(op.value).trim();
        const [genus = "", specificEpithet = ""] = s.split(/\s+/, 2);
        out.push({ op: "replace", path: "genus", value: genus });
        out.push({
          op: "replace",
          path: "specificEpithet",
          value: specificEpithet,
        });
        continue;
      }

      if (op.path === "individualID" && op.value) {
        out.push({
          op: "replace",
          path: "individualId",
          value: op.value,
        });
        continue;
      }

      out.push(op);
    }

    return out;
  }

  async searchIndividualsByName(inputValue) {

    this._searchingIndividuals = true;

    try {
      const searchQuery = {
        query: {
          bool: {
            filter: [
              ...(this._encounterData?.taxonomy ? [{
                match: {
                  taxonomy: this._encounterData.taxonomy
                }
              }] : []),
              {
                wildcard: {
                  names: {
                    value: `*${inputValue}*`,
                    case_insensitive: true
                  }
                }
              }
            ]
          }
        },
      };

      const resp = axios.post('/api/v3/search/individual?size=20&from=0', searchQuery);
      return resp;

    } catch (error) {
      console.error('Failed to search individuals:', error);
      this._individualSearchResults = [];
    } finally {
      this._searchingIndividuals = false;
    }
  }

  async searchSightingsById(inputValue) {
    this._searchingIndividuals = true;

    try {
      const searchQuery = {
        query: {
          bool: {
            filter: [
              {
                wildcard: {
                  id: {
                    value: `*${inputValue}*`,
                    case_insensitive: true
                  }
                }
              }
            ]
          }
        },
      };

      const response = await axios.post('/api/v3/search/occurrence?size=20&from=0', searchQuery);
      return response;

    } catch (error) {
      console.error('Failed to search sightings:', error);
      this._sightingSearchResults = [];
    } finally {
      this._searchingSightings = false;
    }
    return [];
  }

  clearSightingSearchResults() {
    this._sightingSearchResults = [];
  }

  async saveSection(sectionName, encounterId) {
    const operations = this.buildPatchOperations(sectionName);
    if (operations.length === 0) {
      this.resetSectionDraft(sectionName);
      return;
    }

    const expanded = this.expandOperations(operations);

    const result = await axios.patch(`/api/v3/encounters/${encounterId}`, expanded);
    // this.applyPatchOperationsLocally(operations);
    this.resetSectionDraft(sectionName);
  }

  async refreshEncounterData() {
    if (!this._encounterData?.id) {
      console.warn('No encounter ID available for refresh');
      return;
    }

    try {
      const response = await axios.get(`/api/v3/encounters/${this._encounterData.id}`);
      if (response.status === 200 && response.data) {
        const currentImageIndex = this._selectedImageIndex;

        this.setEncounterData(response.data);

        if (currentImageIndex < (response.data.mediaAssets?.length || 0)) {
          this.setSelectedImageIndex(currentImageIndex);
        }

        return response.data;
      }
    } catch (error) {
      console.error('Failed to refresh encounter data:', error);
      throw error;
    }
  }

  async saveSectionAndRefresh(sectionName, encounterId) {
    try {
      await this.saveSection(sectionName, encounterId);

      await this.refreshEncounterData();

      return true;
    } catch (error) {
      console.error(`Failed to save section ${sectionName}:`, error);
      throw error;
    }
  }

  async setEncounterState(newState) {
    const operations = [{ op: "replace", path: "state", value: newState }];
    this.applyPatchOperationsLocally(operations);
    await axios.patch(
      `/api/v3/encounters/${this._encounterData?.id}`,
      operations,
    );
  }
}

export default EncounterStore;
