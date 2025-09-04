import { makeAutoObservable } from "mobx";

class EncounterStore {
  _formData;
  _encounterData;
  _overviewActive = true;
  _editDateCard = false;
  _editIdentifyCard = false;
  _editMetadataCard = false;
  _editLocationCard = false;
  _editAttributesCard = false;

  constructor() {
    this._formData = [];
    makeAutoObservable(this);
  }

  get formData() {
    return this._formData;
  }

  get encounterData() {
    return this._encounterData;
  }

  get overviewActive() {
    return this._overviewActive;
  }

  get editDateCard() {
    return this._editDateCard;
  }

  get editIdentifyCard() {
    return this._editIdentifyCard;
  }

  get editMetadataCard() {
    return this._editMetadataCard;
  }

  get editAttributesCard() {
    return this._editAttributesCard;
  }

  get editLocationCard() {
    return this._editLocationCard;
  }

  setEditDateCard(isActive) {
    this._editDateCard = isActive;
  }

  setEncounterData(newData) {
    this._encounterData = newData;
  }

  setFormData(newData) {
    this._formData = newData;
  }

  setOverviewActive(isActive) {
    this._overviewActive = isActive;
  }

  setEditIdentifyCard(isActive) {
    this._editIdentifyCard = isActive;
  }

  setEditMetadataCard(isActive) {
    this._editMetadataCard = isActive;
  }

  setEditLocationCard(isActive) {
    this._editLocationCard = isActive;
  }

  setEditAttributesCard(isActive) {
    this._editAttributesCard = isActive;
  }
}

const globalEncounterStore = new EncounterStore();

export { globalEncounterStore };

export default EncounterStore;
