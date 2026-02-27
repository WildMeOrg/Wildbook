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
import { toast } from "react-toastify";
import {
  validateFieldValue,
  getValueAtPath,
  setValueAtPath,
  deleteValueAtPath,
  expandOperations,
  setEncounterState,
} from "./helperFunctions";
import NewMatchStore from "./NewMatchStore";
import ImageModalStore from "./ImageModalStore";
import { Toast } from "react-bootstrap";
dayjs.extend(customParseFormat);

class EncounterStore {
  _encounterData = null;

  _siteSettingsData = null;
  _siteSettingsLoading = true;

  _access = "read";

  _intl = null;

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

  _newPersonName = "";
  _newPersonEmail = "";
  _newPersonRole = "";

  _individualSearchInput = "";
  _searchingIndividuals = false;
  _individualSearchResults = [];
  _searchingSightings = false;
  _sightingSearchResults = [];
  _sightingSearchInput = "";

  _individualOptions = [];
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
  _isUploading = false;
  _uploadToastId = null;

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

    makeAutoObservable(
      this,
      {
        flow: false,
        modals: false,
        errors: false,
        newMatch: false,
        imageModal: false,
      },
      { autoBind: true },
    );
  }

  setIntl(intl) {
    this._intl = intl;
  }

  get encounterData() {
    return this._encounterData;
  }
  setEncounterData(newEncounterData) {
    this._encounterData = newEncounterData;
    this._lat = newEncounterData?.locationGeoPoint?.lat ?? null;
    this._lon = newEncounterData?.locationGeoPoint?.lon ?? null;
    this._metalTagValues = newEncounterData?.metalTags || [];
    this._acousticTagValues = newEncounterData?.acousticTag || {};
    this._satelliteTagValues = newEncounterData?.satelliteTag || {};
    this._measurementValues = (newEncounterData?.measurements ?? [])
      .filter((m) => m?.type)
      .map((m) => ({
        type: m.type,
        units: m.units || this.unitByType[m.type] || "",
        value: m.value ?? "",
        samplingProtocol: m.samplingProtocol ?? "",
      }));
    this.resetAllDrafts();
  }

  get access() {
    return this._access;
  }
  setAccess(newAccess) {
    this._access = newAccess;
  }

  async requestCollaboration({ message }) {
    try {
      await axios.get("/Collaborate", {
        params: {
          json: 1,
          username: this._encounterData.assignedUsername,
          message,
        },
      });
    } catch (e) {
      console.error("Failed to send request:", e);
    }
  }

  resetMeasurementValues() {
    this._measurementValues = (this.encounterData?.measurements ?? [])
      .filter((m) => m?.type)
      .map((m) => ({
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
      await this.searchIndividualsByNameAndId(inputValue);
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
    this._selectedMatchLocation = location;
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

  get individualOptions() {
    return this._individualOptions || [];
  }

  setIndividualOptions(options) {
    this._individualOptions = options;
  }

  async changeEncounterState(nextState) {
    if (!nextState || nextState === "loading") return;
    if (!this._encounterData?.id) return;
    this.errors.setFieldError("header", "state", null);
    try {
      await setEncounterState(nextState, this._encounterData.id);
      await this.refreshEncounterData();
    } catch (error) {
      const data = error?.response?.data;
      const msg = this._intl.formatMessage({
        id: "ENCOUNTER_UPDATE_STATE_ERROR",
        defaultMessage: "Failed to update state",
      });
      const serverMsg =
        Array.isArray(data?.errors) && data.errors.length
          ? data.errors
              .map((e) => e.details || e.code || "INVALID")
              .filter(Boolean)
              .join("; ")
          : null;

      this.errors.setFieldError("header", "state", serverMsg || msg);
      toast.error(msg);
    }
  }

  async addNewPerson() {
    try {
      const result = await axios.patch(
        `/api/v3/encounters/${this._encounterData.id}`,
        [{ op: "add", path: this._newPersonRole, value: this._newPersonEmail }],
      );
      if (result.status === 200) {
        this.modals.setOpenAddPeopleModal(false);
        const message = this._intl.formatMessage({
          id: "ENCOUNTER_ADD_PERSON_SUCCESS",
          defaultMessage: "Person added successfully!",
        });
        toast.success(message);
        this._newPersonName = "";
        this._newPersonEmail = "";
        this._newPersonRole = "";
      }
    } catch (error) {
      const message = this._intl.formatMessage({
        id: "ENCOUNTER_ADD_PERSON_ERROR",
        defaultMessage: "Failed to add person",
      });
      toast.error(message);
      throw error;
    }
  }

  async removeContact(type, uuid) {
    if (!type || !uuid) return;
    try {
      const data = await axios.patch(
        `/api/v3/encounters/${this._encounterData.id}`,
        [{ op: "remove", path: type, value: uuid }],
      );
      if (data.status === 200) {
        this._encounterData[type] = this._encounterData[type].filter(
          (item) => item.id !== uuid,
        );
        this.modals.setOpenContactInfoModal(false);
        const message = this._intl.formatMessage({
          id: "CONTACT_REMOVE_SUCCESS",
        });
        toast.success(message);
      }
    } catch (error) {
      const message = this._intl.formatMessage({
        id: "CONTACT_REMOVE_FAILURE",
      });
      toast.error(message);
      throw error;
    }
  }

  async addEncounterToProject() {
    if (!this._selectedProjects) {
      const message = this._intl.formatMessage({
        id: "NO_PROJECT_SELECTED",
      });
      Toast.error(message);
      return;
    }
    const payload = {
      projects: toJS(
        this._selectedProjects.map((project) => ({
          id: project.id,
          encountersToAdd: [this._encounterData.id],
        })),
      ),
    };

    try {
      const result = await axios.post("/ProjectUpdate", payload, {
        headers: { "Content-Type": "application/json" },
      });
      if (result.status === 200) {
        await this.refreshEncounterData();
        const message = this._intl.formatMessage({
          id: "ENCOUNTER_ADDED_TO_PROJECT",
        });
        toast.success(message);
      }
    } catch (error) {
      const message = this._intl.formatMessage({
        id: "ENCOUNTER_ADD_PROJECT_ERROR",
      });
      toast.error(message);
      throw error;
    }
  }

  async removeProjectFromEncounter(projectId) {
    const payload = {
      projects: [
        {
          id: projectId,
          encountersToRemove: [this._encounterData.id],
        },
      ],
    };
    try {
      const result = await axios.post("/ProjectUpdate", payload, {
        headers: { "Content-Type": "application/json" },
      });
      if (result.status === 200) {
        await this.refreshEncounterData();
        const message = this._intl.formatMessage({
          id: "ENCOUNTER_REMOVED_FROM_PROJECT",
        });
        toast.success(message);
      }
    } catch (error) {
      const message = this._intl.formatMessage({
        id: "ENCOUNTER_REMOVE_FROM_PROJECT_ERROR",
      });
      toast.error(message);
      throw error;
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
  setSelectedProjects(projectIds) {
    this._selectedProjects = projectIds;
  }

  get selectedImageIndex() {
    return this._selectedImageIndex;
  }
  setSelectedImageIndex(index) {
    this._selectedImageIndex = index;
  }

  get encounterAnnotations() {
    return (
      this.encounterData?.mediaAssets?.[
        this._selectedImageIndex
      ]?.annotations?.filter(
        (data) => data.encounterId === this.encounterData.id,
      ) || []
    );
  }

  get selectedAnnotationId() {
    return this._selectedAnnotationId;
  }
  setSelectedAnnotationId(annotationId) {
    this._selectedAnnotationId = annotationId;
  }

  get isUploading() {
    return this._isUploading;
  }

  get matchResultClickable() {
    const selectedAnnotation =
      this.encounterAnnotations?.find(
        (annotation) => annotation.id === this.selectedAnnotationId,
      ) || [];
    const iaTaskId = !!selectedAnnotation?.iaTaskId;
    const skipId = !!selectedAnnotation?.iaTaskParameters?.skipIdent;
    const identActive = iaTaskId && !skipId;
    const detectionComplete =
      this.encounterData?.mediaAssets?.[this._selectedImageIndex]
        ?.detectionStatus === "complete";
    const identificationStatus =
      selectedAnnotation?.identificationStatus === "complete" ||
      selectedAnnotation?.identificationStatus === "pending";

    return identActive && (detectionComplete || identificationStatus);
  }

  get lat() {
    return this._lat;
  }
  setLat(newLat) {
    this.errors.setFieldError("location", "latitude", null);
    this._lat = newLat;
    this.errors.setFieldError(
      "location",
      "latitude",
      validateFieldValue("location", "latitude", newLat, {
        lat: newLat,
        lon: this._lon,
      }),
    );
    this.errors.setFieldError(
      "location",
      "longitude",
      validateFieldValue("location", "longitude", this._lon, {
        lat: newLat,
        lon: this._lon,
      }),
    );
  }

  get lon() {
    return this._lon;
  }
  setLon(newLon) {
    this.errors.setFieldError("location", "longitude", null);
    this._lon = newLon;
    this.errors.setFieldError(
      "location",
      "longitude",
      validateFieldValue("location", "longitude", newLon, {
        lat: this._lat,
        lon: newLon,
      }),
    );
    this.errors.setFieldError(
      "location",
      "latitude",
      validateFieldValue("location", "latitude", this._lat, {
        lat: this._lat,
        lon: newLon,
      }),
    );
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
      this._identificationRemarksOptions =
        this._siteSettingsData.identificationRemarks.map((data) => ({
          value: data,
          label: data,
        }));
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
      (this.measurementTypes || []).map((t, i) => [
        t,
        this.measurementUnits?.[i] ?? "",
      ]),
    );
  }

  get measurementValues() {
    return this._measurementValues;
  }

  getMeasurement(type) {
    const found = this.measurementValues.find((m) => m.type === type);
    if (found) return found;
    return {
      type,
      units: this.unitByType[type] ?? "",
      value: "",
      samplingProtocol: "",
    };
  }

  _upsertMeasurement(type, partial) {
    const idx = this.measurementValues.findIndex((m) => m.type === type);
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
    return (
      this._siteSettingsData?.satelliteTagName.map((name) => ({
        value: name,
        label: name,
      })) || []
    );
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
        siteSettingsData.behaviorOptions?.[this._encounterData?.species] || []
      ).map((data) => ({
        value: data,
        label: data,
      })),
      ...(siteSettingsData.behaviorOptions?.[""] || []).map((data) => ({
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

  get siteSettingsLoading() {
    return this._siteSettingsLoading;
  }

  setSiteSettingsLoading(siteSettingsLoading) {
    this._siteSettingsLoading = siteSettingsLoading;
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

    currentMetalTags.forEach((currentTag) => {
      const originalTag = originalMetalTags.find(
        (tag) => tag.location === currentTag.location,
      );

      if (!originalTag || originalTag.number !== currentTag.number) {
        ops.push({
          op: "replace",
          path: "metalTags",
          value: { location: currentTag.location, number: currentTag.number },
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
          ...(currentAcoustic.serialNumber && {
            serialNumber: currentAcoustic.serialNumber,
          }),
          ...(currentAcoustic.idNumber && {
            idNumber: currentAcoustic.idNumber,
          }),
        },
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
          ...(currentSatellite.serialNumber && {
            serialNumber: currentSatellite.serialNumber,
          }),
          ...(currentSatellite.argosPttNumber && {
            argosPttNumber: currentSatellite.argosPttNumber,
          }),
        },
      });
    }
    return ops;
  }

  async patchTracking() {
    const ops = this.buildTrackingPatchPayload();
    if (!ops.length) return;
    try {
      const resp = await axios.patch(
        `/api/v3/encounters/${this.encounterData.id}`,
        ops,
      );
      if (resp.status === 200) {
        await this.refreshEncounterData();
        this.setEditTracking?.(false);
        const message = this._intl.formatMessage({
          id: "TRACKING_DATA_SAVED",
        });
        toast.success(message);
      }
    } catch (error) {
      const message = this._intl.formatMessage({
        id: "TRACKING_DATA_SAVE_ERROR",
      });
      toast.error(message);
      this.errors.setFieldError("tracking", "general", message);
      throw error;
    }
  }

  async patchMeasurements() {
    const tasks = [];
    let hasErrors = false;

    for (const m of this.measurementValues) {
      const isEmpty = m.value == null || m.value === "";

      const payload = isEmpty
        ? {
            op: "remove",
            path: "measurements",
            value: m.type,
          }
        : {
            op: "replace",
            path: "measurements",
            value: {
              type: m.type,
              units: m.units,
              value: m.value,
              samplingProtocol: m.samplingProtocol,
            },
          };

      tasks.push(
        axios
          .patch(`/api/v3/encounters/${this.encounterData.id}`, [payload], {
            headers: { "Content-Type": "application/json" },
          })
          .catch((err) => {
            hasErrors = true;
            this.errors.setFieldError(
              "measurement",
              m.type,
              isEmpty
                ? "Failed to remove measurement"
                : "Failed to save measurement",
            );
            throw err;
          }),
      );
    }

    if (tasks.length > 0) {
      await Promise.allSettled(tasks);
    }

    if (hasErrors) {
      const message = this._intl.formatMessage({
        id: "MEASUREMENTS_SAVE_ERROR",
      });
      toast.error(message);
    } else if (tasks.length > 0) {
      const message = this._intl.formatMessage({
        id: "MEASUREMENTS_SAVE_SUCCESS",
      });
      toast.success(message);
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

    const supported = new Set([
      "image/jpeg",
      "image/jpg",
      "image/png",
      "image/bmp",
    ]);
    const maxBytes = maxSizeMB * 1024 * 1024;

    flow.on("fileAdded", (file) => {
      const typeOk = supported.has(file?.file?.type || "");
      const sizeOk = file.size <= maxBytes;
      return typeOk && sizeOk;
    });

    flow.on("filesSubmitted", () => {
      this._isUploading = true;
      const message = this._intl.formatMessage({
        id: "UPLOADING_IMAGE",
      });
      this._uploadToastId = toast.loading(message);
      flow.upload();
    });

    flow.on("progress", () => {
      const progress = Math.floor(flow.progress() * 100);
      this.uploadProgress = progress;

      if (this._uploadToastId) {
        toast.update(this._uploadToastId, {
          render: `Uploading... ${progress}%`,
          isLoading: true,
        });
      }
    });

    flow.on("fileSuccess", async (file) => {
      try {
        const op = {
          op: "add",
          path: "assets",
          value: {
            submissionId: this.imageSubmissionId,
            filename: file?.file?.name || file?.name || "upload.jpg",
          },
        };
        const result = await axios.patch(
          `/api/v3/encounters/${this._encounterData.id}`,
          [op],
          {
            headers: { "Content-Type": "application/json" },
          },
        );
        if (result.status === 200) {
          this.refreshEncounterData();
          if (this._uploadToastId) {
            toast.update(this._uploadToastId, {
              render: "Image uploaded successfully!",
              type: "success",
              isLoading: false,
              autoClose: 3000,
            });
          }
        }
      } catch (e) {
        if (this._uploadToastId) {
          toast.update(this._uploadToastId, {
            render: "Failed to upload image",
            type: "error",
            isLoading: false,
            autoClose: 3000,
          });
        }
        throw e;
      } finally {
        this._isUploading = false;
        this.uploadProgress = 0;
        this.errors.uploadErrors = [];
        this._uploadToastId = null;
      }
    });

    flow.on("fileError", (_file) => {
      if (this._uploadToastId) {
        toast.update(this._uploadToastId, {
          render: "Upload failed",
          type: "error",
          isLoading: false,
          autoClose: 3000,
        });
      }
      this._isUploading = false;
      this._uploadToastId = null;
      this.uploadProgress = 0;
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

  async searchIndividualsByNameAndId(inputValue) {
    this._searchingIndividuals = true;

    try {
      const searchQuery = {
        query: {
          bool: {
            filter: [
              ...(this._encounterData?.taxonomy
                ? [
                    {
                      match: {
                        taxonomy: this._encounterData.taxonomy,
                      },
                    },
                  ]
                : []),
            ],
            should: [
              {
                wildcard: {
                  names: {
                    value: `*${inputValue}*`,
                    case_insensitive: true,
                  },
                },
              },
              {
                wildcard: {
                  id: {
                    value: `*${inputValue}*`,
                    case_insensitive: true,
                  },
                },
              },
            ],
            minimum_should_match: 1,
          },
        },
      };

      const resp = await axios.post(
        "/api/v3/search/individual?size=20&from=0",
        searchQuery,
      );
      this._individualSearchResults = resp?.data?.hits ?? [];
      return resp;
    } catch (error) {
      this._individualSearchResults = [];
      throw error;
    } finally {
      this._searchingIndividuals = false;
    }
  }

  async searchSightingsById(inputValue) {
    this._searchingSightings = true;
    try {
      const searchQuery = {
        query: {
          bool: {
            filter: [
              {
                wildcard: {
                  id: {
                    value: `*${inputValue}*`,
                    case_insensitive: true,
                  },
                },
              },
            ],
          },
        },
      };
      const response = await axios.post(
        "/api/v3/search/occurrence?size=20&from=0",
        searchQuery,
      );
      this._sightingSearchResults = response?.data?.hits ?? [];
      return response;
    } catch (error) {
      this._sightingSearchResults = [];
      throw error;
    } finally {
      this._searchingSightings = false;
    }
  }

  clearSightingSearchResults() {
    this._sightingSearchResults = [];
  }

  async removeIndividualFromEncounter() {
    if (!this.encounterData?.individualId) return;
    const ops = [
      {
        op: "remove",
        path: "individualId",
        value: this.encounterData?.individualId,
      },
    ];
    try {
      const result = await axios.patch(
        `/api/v3/encounters/${this._encounterData.id}`,
        ops,
      );
      if (result.status === 200) {
        await this.refreshEncounterData();
        toast.success("Individual removed successfully!");
      }
    } catch (error) {
      toast.error("Failed to remove individual from encounter");
      throw error;
    }
  }

  async removeOccurrenceIdFromEncounter() {
    if (!this.encounterData?.occurrenceId) return;
    const ops = [
      {
        op: "remove",
        path: "occurrenceId",
        value: this.encounterData?.occurrenceId,
      },
    ];
    try {
      const result = await axios.patch(
        `/api/v3/encounters/${this._encounterData.id}`,
        ops,
      );
      if (result.status === 200) {
        await this.refreshEncounterData();
        toast.success("Occurrence ID removed successfully!");
      }
    } catch (error) {
      toast.error("Failed to remove occurrence ID from encounter");
      throw error;
    }
  }

  async saveSection(sectionName, encounterId) {
    this.errors.clearErrors();
    const operations = this.buildPatchOperations(sectionName);
    if (operations.length === 0) {
      this.resetSectionDraft(sectionName);
      return;
    }

    const expanded = expandOperations(operations, this.individualOptions);
    if (expanded.length === 0) {
      this.resetSectionDraft(sectionName);
      return;
    }

    try {
      const result = await axios.patch(
        `/api/v3/encounters/${encounterId}`,
        expanded,
      );
      this.errors.clearSectionErrors(sectionName);
      this.resetSectionDraft(sectionName);
      toast.success("Changes saved successfully!");
      return result;
    } catch (error) {
      if (error.response?.data) {
        this.errors.setErrors(sectionName, error.response.data);
      } else {
        this.errors.setErrors(
          "general",
          error.message || "An error occurred while saving",
        );
      }
      toast.error("Failed to save changes");
      throw error;
    }
  }

  async refreshEncounterData() {
    if (!this._encounterData?.id) {
      console.warn("No encounter ID available for refresh");
      return;
    }

    try {
      const response = await axios.get(
        `/api/v3/encounters/${this._encounterData.id}`,
      );
      if (response.status === 200 && response.data) {
        const currentImageIndex = this._selectedImageIndex;
        this.setEncounterData(response.data);
        if (currentImageIndex < (response.data.mediaAssets?.length || 0)) {
          this.setSelectedImageIndex(currentImageIndex);
        }
        return response.data;
      }
    } catch (error) {
      toast.error("Failed to refresh encounter data");
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
      toast.error("Failed to save data");
      throw error;
    }
  }
}

export default EncounterStore;
