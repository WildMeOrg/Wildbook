import { makeAutoObservable } from "mobx";
import axios from "axios";
import convertToTreeDataWithName from "../../utils/converToTreeData";

const SECTION_FIELD_PATHS = {
  date: ["date", "verbatimEventDate"],
  identify: [
    "individualDisplayName",
    "otherCatalogNumbers",
    "identificationRemarks",
    "occurrenceId",
  ],
  metadata: [
    "id",
    "assignedUsername",
    "sharingPermission",
    "state",
    "observationComments",
  ],
  location: [
    "verbatimLocality",
    "locationId",
    "locationName",
    "country",
    "locationGeoPoint",
    "decimalLatitude",
    "decimalLongitude",
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

  _lat = null;
  _lon = null;

  _showAnnotations = true;
  _openContactInfoModal = false;
  _OpenEncounterHistoryModal = false;
  __openAddPeopleModal = false;
  _openAddPeopleModal = false;

   _newPersonName = '';
   _newPersonEmail = '';
   _newPersonRole = '';

  _tags = ["erin", "test", "tag"]; // Example tags, replace with actual data

  _taxonomyOptions = [];
  _livingStatusOptions = [];
  _sexOptions = [];
  _lifeStageOptions = [];
  _behaviorOptions = [];
  _groupRoleOptions = [];
  _patterningCodeOptions = [];
  _locationIdOptions = [];

  _selectedImageIndex = 0;

  _measurementsAndTrackingSection = true;
  _editTracking = false;
  _editMeasurements = false;

  _biologicalSamplesSection = false;
  _editBiologicalSamples = false;

  _projectsSection = false;
  _editProjects = false;

  _sectionDrafts = new Map(
    Object.keys(SECTION_FIELD_PATHS).map((name) => [name, {}]),
  );

  constructor() {
    makeAutoObservable(this, {}, { autoBind: true });
  }

  get encounterData() {
    return this._encounterData;
  }
  setEncounterData(newEncounterData) {
    this._encounterData = newEncounterData;
    this._lat = newEncounterData?.locationGeoPoint?.lat || null;
    this._lon = newEncounterData?.locationGeoPoint?.lon || null;
    this.resetAllDrafts();
  }

  // Getters and setters for UI state
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

  setOpenAddPeopleModal(isOpen) {
    console.log("Setting openAddPeopleModal to:", isOpen);
    this._openAddPeopleModal = isOpen;
  }

  get openAddPeopleModal() {
    return this._openAddPeopleModal;
  }

  addNewPerson() {
    const body = new URLSearchParams({
    encounter: this._encounterData.id,
    type: this._newPersonRole,
    email: this._newPersonEmail,
  });

    axios.post("/EncounterAddUser", body, {
      headers: { "X-Requested-With": "XMLHttpRequest" }, 
    })
    .then(response => {
      console.log("New person added successfully:", response.data);
      this.setNewPersonName('');
      this.setNewPersonEmail('');
      this.setNewPersonRole('');
    })
    .catch(error => {
      console.error("Error adding new person:", error);
    });
  }

  removeContact(type, uuid) {
    const body = new URLSearchParams({
      encounter: this._encounterData.id,
      type: type,
      uuid: uuid,
    });

    axios.post("/EncounterRemoveUser", body, {
      headers: { "X-Requested-With": "XMLHttpRequest" },
    })
      .then(response => {
        console.log("Contact removed successfully:", response.data);
      })
      .catch(error => {
        console.error("Error removing contact:", error);
      });
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

  get editProjects() {
    return this._editProjects;
  }
  setEditProjects(isEditing) {
    this._editProjects = isEditing;
  }

  // image and annotation operations
  get selectedImageIndex() {
    return this._selectedImageIndex;
  }
  setSelectedImageIndex(index) {
    this._selectedImageIndex = index;
  }

  get lat() {
    return this._lat;
  }
  setLat(newLat) {
    this._lat = newLat;
  }

  get lon() {
    return this._lon;
  }
  setLon(newLon) {
    this._lon = newLon;
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

  get OpenEncounterHistoryModal() {
    return this._OpenEncounterHistoryModal;
  }
  setOpenEncounterHistoryModal(isOpen) {
    this._OpenEncounterHistoryModal = isOpen;
  }

  get tags() {
    return this._tags;
  }
  setTags(newTags) {
    this._tags = newTags;
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

  getFieldValue(sectionName, fieldPath) {
    const draftForSection = this._sectionDrafts.get(sectionName) || {};
    if (Object.prototype.hasOwnProperty.call(draftForSection, fieldPath)) {
      return draftForSection[fieldPath];
    }
    return getValueAtPath(this._encounterData, fieldPath);
  }

  setFieldValue(sectionName, fieldPath, newValue) {
    console.log(
      `Setting field value for section "${sectionName}", path "${fieldPath}" to`,
      newValue,
    );
    const draftForSection = { ...(this._sectionDrafts.get(sectionName) || {}) };
    draftForSection[fieldPath] = newValue;
    this._sectionDrafts.set(sectionName, draftForSection);
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
    console.log("Building operations for section:", JSON.stringify(operations));
    return operations;
  }

  applyPatchOperationsLocally(operations) {
    if (!Array.isArray(operations) || operations.length === 0) return;
    console.log("Applying operations locally:", operations);
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

    console.log(
      "Applied operations locally:",
      JSON.stringify(this._encounterData),
    );
  }

  /**
   * @param {string} sectionName - 'date' | 'identify' | 'metadata' | 'location' | 'attributes'
   * @param {string} encounterId
   */

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

  expandOperations(operations) {
    const base = operations.slice();
    const out = [];

    console.log("Expanding operations:", JSON.stringify(base));

    for (const op of base) {
      if (op.path === "date") {
        const p = this.parseYMDHM(op.value);
        console.log("p.month", p?.month);
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

      out.push(op);
    }

    return out;
  }

  async saveSection(sectionName, encounterId) {
    const operations = this.buildPatchOperations(sectionName);
    if (operations.length === 0) {
      this.resetSectionDraft(sectionName);
      return;
    }

    const expanded = this.expandOperations(operations);
    console.log(
      "NON-STRING values1:",
      expanded.filter((op) => "value" in op && typeof op.value !== "string"),
    );
    console.log(`Section "${sectionName}" has operations:`, operations);
    console.log(`Saving section "${sectionName}" with operations:`, expanded);

    await axios.patch(`/api/v3/encounters/${encounterId}`, expanded);
    this.applyPatchOperationsLocally(operations);
    this.resetSectionDraft(sectionName);
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
