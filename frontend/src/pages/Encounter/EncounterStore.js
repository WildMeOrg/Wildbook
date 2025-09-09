
import { makeAutoObservable } from "mobx";
import axios from "axios";

const SECTION_FIELD_PATHS = {
  date: ["encounterDate", "verbatimLocality"],
  identify: ["individualDisplayName", "matchedBy", "alternateID"],
  metadata: ["assignedUsername", "sharingPermission", "state", "observationComments"],
  location: ["locationName", "country", "decimalLatitude", "decimalLongitude"],
  attributes: [
    "taxonomy",
    "livingStatus",
    "sex",
    "distinguishingScar",
    "behavior",
    "groupRole",
    "patterningCode",
    "lifeStage",
    "observationComments",
  ],
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


  _taxonomyOptions = [];
  _livingStatusOptions = [];
  _sexOptions = [];
  _lifeStageOptions = [];
  _behaviorOptions = [];
  _groupRoleOptions = [];
  _patterningCodeOptions = [];

  _sectionDrafts = new Map(Object.keys(SECTION_FIELD_PATHS).map((name) => [name, {}]));

  constructor() {
    makeAutoObservable(this, {}, { autoBind: true });
  }

  get encounterData() { return this._encounterData; }
  setEncounterData(newEncounterData) {
    this._encounterData = newEncounterData;
    this.resetAllDrafts();
  }

  // Getters and setters for UI state

  get overviewActive() { return this._overviewActive; }
  setOverviewActive(isActive) { this._overviewActive = isActive; }

  get editDateCard() { return this._editDateCard; }
  setEditDateCard(isEditing) { this._editDateCard = isEditing; }

  get editIdentifyCard() { return this._editIdentifyCard; }
  setEditIdentifyCard(isEditing) { this._editIdentifyCard = isEditing; }

  get editMetadataCard() { return this._editMetadataCard; }
  setEditMetadataCard(isEditing) { this._editMetadataCard = isEditing; }

  get editLocationCard() { return this._editLocationCard; }
  setEditLocationCard(isEditing) { this._editLocationCard = isEditing; }

  get editAttributesCard() { return this._editAttributesCard; }
  setEditAttributesCard(isEditing) { this._editAttributesCard = isEditing; }


  get taxonomyOptions() { return this._taxonomyOptions; }
  get livingStatusOptions() { return this._livingStatusOptions; }
  get sexOptions() { return this._sexOptions; }
  get lifeStageOptions() { return this._lifeStageOptions; }
  get behaviorOptions() { return this._behaviorOptions; }
  get groupRoleOptions() { return this._groupRoleOptions; }
  get patterningCodeOptions() { return this._patterningCodeOptions; }


  getFieldValue(sectionName, fieldPath) {
    const draftForSection = this._sectionDrafts.get(sectionName) || {};
    if (Object.prototype.hasOwnProperty.call(draftForSection, fieldPath)) {
      return draftForSection[fieldPath];
    }
    return getValueAtPath(this._encounterData, fieldPath);
  }

  setFieldValue(sectionName, fieldPath, newValue) {
    const draftForSection = { ...(this._sectionDrafts.get(sectionName) || {}) };
    draftForSection[fieldPath] = newValue;
    this._sectionDrafts.set(sectionName, draftForSection);
  }

  get siteSettingsData() { return this._siteSettingsData; }

  setSiteSettings(siteSettingsData) {
    this._siteSettingsData = siteSettingsData;
    this._taxonomyOptions = siteSettingsData.siteTaxonomies?.map((taxonomy) => ({
      value: taxonomy.scientificName,
      label: taxonomy.scientificName,
    }));
    this._livingStatusOptions = siteSettingsData.livingStatus?.map((status) => ({
      value: status,
      label: status,
    }));
    this._sexOptions = siteSettingsData.sex?.map((data) => ({
      value: data,
      label: data,
    }));
    this._lifeStageOptions = siteSettingsData.lifeStage?.map((data) => ({
      value: data,
      label: data,
    }));
    this._behaviorOptions = siteSettingsData.behavior?.map((data) => ({
      value: data,
      label: data,
    }));
    this._groupRoleOptions = siteSettingsData.groupRoles?.map((data) => ({
      value: data,
      label: data,
    }));
    this._patterningCodeOptions = siteSettingsData.patterningCode?.map((data) => ({
      value: data,
      label: data,
    })
    )
  }

  resetSectionDraft(sectionName) {
    this._sectionDrafts.set(sectionName, {});
  }

  resetAllDrafts() {
    Object.keys(SECTION_FIELD_PATHS).forEach((name) => this._sectionDrafts.set(name, {}));
  }

  buildPatchOperations(sectionName) {
    const fieldPaths = SECTION_FIELD_PATHS[sectionName] || [];
    const draftForSection = this._sectionDrafts.get(sectionName) || {};
    const operations = [];

    for (const fieldPath of fieldPaths) {
      const oldValue = getValueAtPath(this._encounterData, fieldPath);
      const hasUserEdited = Object.prototype.hasOwnProperty.call(draftForSection, fieldPath);
      const newValue = hasUserEdited ? draftForSection[fieldPath] : oldValue;

      const isSame = JSON.stringify(newValue) === JSON.stringify(oldValue);
      if (isSame) continue;

      const isEmptyNewValue = newValue === "" || newValue == null;

      if (isEmptyNewValue) {
        if (oldValue !== undefined) operations.push({ op: "remove", path: fieldPath });
      } else if (oldValue === undefined) {
        operations.push({ op: "add", path: fieldPath, value: newValue });
      } else {
        operations.push({ op: "replace", path: fieldPath, value: newValue });
      }
    }
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

    console.log("Applied operations locally:", JSON.stringify(this._encounterData));
  }

  /**
   * @param {string} sectionName - 'date' | 'identify' | 'metadata' | 'location' | 'attributes'
   * @param {string} encounterId
   */

  async saveSection(sectionName, encounterId) {
    const operations = this.buildPatchOperations(sectionName);
    if (operations.length === 0) {
      this.resetSectionDraft(sectionName);
      return;
    }

    console.log(`Saving section "${sectionName}" with operations:`, operations);

    await axios.patch(`/api/v3/encounters/${encounterId}`, operations);

    this.applyPatchOperationsLocally(operations);
    this.resetSectionDraft(sectionName);
  }

  async setEncounterState(newState) {    
    const operations = [{ op: "replace", path: "state", value: newState }];
    this.applyPatchOperationsLocally(operations);
    await axios.patch(`/api/v3/encounters/${this._encounterData?.id}`, operations);
  }
}

export default EncounterStore;
