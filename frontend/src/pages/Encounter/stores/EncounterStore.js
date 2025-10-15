import { makeAutoObservable } from "mobx";
import axios from "axios";
import convertToTreeDataWithName from "../../../utils/convertToTreeDataWithName";
import { debounce } from "lodash";
import { toJS } from "mobx";
import dayjs from "dayjs";
import Flow from "@flowjs/flow.js";
import customParseFormat from "dayjs/plugin/customParseFormat";
import { v4 as uuidv4 } from "uuid";
import ModalStore from "./ModalStore";
import ErrorStore from "./ErrorStore";
import { SECTION_FIELD_PATHS } from "../constants";
import { validateFieldValue, getValueAtPath, setValueAtPath, deleteValueAtPath, expandOperations } from "./helperFunctions";
import NewMatchStore from "./NewMatchStore";
import ImageModalStore from "./ImageModalStore";
dayjs.extend(customParseFormat);

class EncounterStore {
  _encounterData = null;

  _siteSettingsData = null;

  modals;
  errors;
  newMatch;
  imageModal;

  _overviewActive = true;
  _editDateCard = false;
  _editIdentifyCard = false;
  _editMetadataCard = false;
  _editLocationCard = false;
  _editAttributesCard = false;

  _lat = null;
  _lon = null;

  // _showAnnotations = true;

  _newPersonName = '';
  _newPersonEmail = '';
  _newPersonRole = '';

  _individualSearchInput = "";
  _searchingIndividuals = false;
  _individualSearchResults = [];
  _searchingSightings = false;
  _sightingSearchResults = [];
  _sightingSearchInput = "";

  _taxonomyOptions = [];
  _livingStatusOptions = [];
  _sexOptions = [];
  _lifeStageOptions = [];
  _behaviorOptions = [];
  _groupRoleOptions = [];
  _patterningCodeOptions = [];
  _locationIdOptions = [];
  _identificationRemarksOptions = [];

  _metalTagLocation = [];
  _metalTagValues = [];
  _acousticTagValues = {};
  _satelliteTagValues = {};
  _measurementValues = [];
  _measurementTypes = [];
  _measurementUnits = [];

  _selectedImageIndex = 0;
  _encounterAnnotations = null;
  _selectedAnnotationId = null;

  flow = null;
  imageSubmissionId = null;
  uploadProgress = 0;

  _matchResultClickable = false;

  _selectedMatchLocation = "";
  _owner = "";

  _measurementsAndTrackingSection = true;
  _editTracking = false;
  _editMeasurements = false;

  _biologicalSamplesSection = false;
  _editBiologicalSamples = false;

  _projectsSection = false;
  _selectedProjects = null;

  _sectionDrafts = new Map(
    Object.keys(SECTION_FIELD_PATHS).map((name) => [name, {}]),
  );

  constructor() {
    this.modals = new ModalStore(this);
    this.errors = new ErrorStore(this);
    this.newMatch = new NewMatchStore(this);
    this.imageModal = new ImageModalStore(this);

    makeAutoObservable(this, {
      flow: false,
      modals: false,
      errors: false,
      newMatch: false,
      imageModal: false,
    }, { autoBind: true });
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
    this._measurementValues = (newEncounterData?.measurements ?? [])
      .filter(m => m?.type)
      .map(m => ({
        type: m.type,
        units: m.units || this.unitByType[m.type] || "",
        value: m.value ?? "",
        samplingProtocol: m.samplingProtocol ?? "",
      }));
    this.resetAllDrafts();
  }

  resetMeasurementValues() {
    this._measurementValues = (this.encounterData?.measurements ?? [])
      .filter(m => m?.type)
      .map(m => ({
        type: m.type,
        units: m.units || this.unitByType[m.type] || "",
        value: m.value ?? "",
        samplingProtocol: m.samplingProtocol ?? "",
      }));
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
      this.modals.setOpenAddPeopleModal(false);
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
      this.modals.setOpenContactInfoModal(false);
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
    this.errors.setFieldError("location", "latitude", null);
    this._lat = newLat;
    this.errors.setFieldError("location", "latitude",
      validateFieldValue("location", "latitude", newLat, { lat: newLat, lon: this._lon })
    );
    this.errors.setFieldError("location", "longitude",
      validateFieldValue("location", "longitude", this._lon, { lat: newLat, lon: this._lon })
    );
  }

  get lon() {
    return this._lon;
  }
  setLon(newLon) {
    this.errors.setFieldError("location", "longitude", null);
    this._lon = newLon;
    this.errors.setFieldError("location", "longitude",
      validateFieldValue("location", "longitude", newLon, { lat: this._lat, lon: newLon })
    );
    this.errors.setFieldError("location", "latitude",
      validateFieldValue("location", "latitude", this._lat, { lat: this._lat, lon: newLon })
    );
  }

  // get showAnnotations() {
  //   return this._showAnnotations;
  // }
  // setShowAnnotations(show) {
  //   this._showAnnotations = show;
  // }

  // get tags() {
  //   return this._encounterData?.mediaAssets?.[this._selectedImageIndex]?.keywords || [];
  // }
  // setTags(newTags) {
  //   this._tags = newTags;
  // }

  // get addTagsFieldOpen() {
  //   return this._addTagsFieldOpen;
  // }
  // setAddTagsFieldOpen(add) {
  //   this._addTagsFieldOpen = add;
  // }

  // get availableKeywords() {
  //   return this._siteSettingsData?.keyword || [];
  // }

  // get availableKeywordsId() {
  //   return this._siteSettingsData?.keywordId || [];
  // }

  // get selectedKeyword() {
  //   return this._selectedKeyword;
  // }
  // setSelectedKeyword(keyword) {
  //   this._selectedKeyword = keyword;
  // }

  // get availabelLabeledKeywords() {
  //   return Object.keys(this._siteSettingsData?.labeledKeyword) || [];
  // }

  // get labeledKeywordAllowedValues() {
  //   return this._siteSettingsData?.labeledKeywordAllowedValues[this.selectedLabeledKeyword] || [];
  // }

  // get selectedLabeledKeyword() {
  //   return this._selectedLabeledKeyword;
  // }
  // setSelectedLabeledKeyword(keyword) {
  //   this._selectedLabeledKeyword = keyword;
  // }

  // get selectedAllowedValues() {
  //   return this._selectedAllowedValues;
  // }

  // setSelectedAllowedValues(allowedValues) {
  //   this._selectedAllowedValues = allowedValues;
  // }

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

  get unitByType() {
    return Object.fromEntries(
      (this.measurementTypes || []).map((t, i) => [t, this.measurementUnits?.[i] ?? ""])
    );
  }

  get measurementValues() {
    return this._measurementValues;
  }

  getMeasurement(type) {
    const found = this.measurementValues.find(m => m.type === type);
    if (found) return found;
    return {
      type,
      units: this.unitByType[type] ?? "",
      value: "",
      samplingProtocol: "",
    };
  }

  _upsertMeasurement(type, partial) {
    const idx = this.measurementValues.findIndex(m => m.type === type);
    const units = this.unitByType[type] ?? "";
    if (idx === -1) {
      this.measurementValues.push({
        type,
        units,
        value: "",
        samplingProtocol: "",
        ...partial,
      });
    } else {
      this.measurementValues[idx] = {
        ...this.measurementValues[idx],
        units,
        ...partial,
      };
    }
  }

  setMeasurementValue(type, value) {
    this._upsertMeasurement(type, { value });
  }

  setMeasurementSamplingProtocol(type, samplingProtocol) {
    this._upsertMeasurement(type, { samplingProtocol });
  }

  get measurementTypes() {
    return this.siteSettingsData?.measurement || [];
  }

  get measurementUnits() {
    return this._siteSettingsData?.measurementUnits || [];
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
  get showMeasurements() {
    return this.siteSettingsData?.showMeasurements;
  }

  get satelliteTagNameOptions() {
    return this._siteSettingsData?.satelliteTagName.map((name) => ({
      value: name,
      label: name,
    })) || [];
  }

  getFieldValue(sectionName, fieldPath) {
    const draftForSection = this._sectionDrafts.get(sectionName) || {};
    if (Object.prototype.hasOwnProperty.call(draftForSection, fieldPath)) {
      return draftForSection[fieldPath];
    }
    return getValueAtPath(this._encounterData, fieldPath);
  }

  setFieldValue(sectionName, fieldPath, newValue) {
    this.errors.setFieldError(sectionName, fieldPath, null);
    const draftForSection = { ...(this._sectionDrafts.get(sectionName) || {}) };
    draftForSection[fieldPath] = newValue;
    this._sectionDrafts.set(sectionName, draftForSection);

    const error = validateFieldValue(sectionName, fieldPath, newValue);
    if (error) {
      this.errors.setFieldError(sectionName, fieldPath, error);
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
  async patchMeasurements() {    
    this.measurementValues.map(async (measurement) => {
      if(measurement.value === "" || measurement.value == null) {
        this.errors.setFieldError('measurement', measurement.type, 'value cannot be empty');
        return;
      }
      const payload = {
        op: "replace",
        path: "measurements",
        value: {
          type: measurement.type,
          units: measurement.units,
          value: measurement.value,
          samplingProtocol: measurement.samplingProtocol,
        },
      };
      const result = await axios.patch(`/api/v3/encounters/${this.encounterData.id}`, [payload], {
        headers: { "Content-Type": "application/json" },
      });
    })

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
        this.errors.uploadErrors = [];
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

  // removeAnnotation(annotationId) {
  //   return axios.patch(
  //     `/api/v3/encounters/${this._encounterData.id}`,
  //     [
  //       {
  //         op: "remove",
  //         path: "annotations",
  //         value: annotationId,
  //       },
  //     ],
  //     {
  //       headers: { "Content-Type": "application/json" },
  //     }
  //   )
  // }

  // deleteImage(encounterId, mediaAssetId) {
  //   return axios.post(
  //     "/MediaAssetAttach",
  //     {
  //       detach: "true",
  //       EncounterID: this._encounterData.id,
  //       MediaAssetID: this._encounterData.mediaAssets[this._selectedImageIndex].id,
  //     },
  //     {
  //       headers: { "Content-Type": "application/json" },
  //     }
  //   );
  // }

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
    this.errors.clearErrors();
    const operations = this.buildPatchOperations(sectionName);
    if (operations.length === 0) {
      this.resetSectionDraft(sectionName);
      return;
    }

    const expanded = expandOperations(operations);
    if (expanded.length === 0) {
      this.resetSectionDraft(sectionName);
      return;
    }

    // const result = await axios.patch(`/api/v3/encounters/${encounterId}`, expanded);
    // // this.applyPatchOperationsLocally(operations);
    // this.resetSectionDraft(sectionName);
    try {
      const result = await axios.patch(`/api/v3/encounters/${encounterId}`, expanded);
      this.errors.clearSectionErrors(sectionName);
      this.resetSectionDraft(sectionName);
      return result;
    } catch (error) {
      if (error.response?.data) {
        this.errors.setErrors(sectionName, error.response.data);
      } else {
        this.errors.setErrors('general', error.message || 'An error occurred while saving');
      }
      throw error;
    }
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
    this.errors.clearErrors();
    try {
      await this.saveSection(sectionName, encounterId);
      await this.refreshEncounterData();
      return true;
    } catch (error) {
      console.error(`Failed to save section ${sectionName}:`, error);
      throw error;
    }
  }
}

export default EncounterStore;
