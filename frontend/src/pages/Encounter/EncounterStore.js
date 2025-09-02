import { makeAutoObservable } from "mobx";

class EncounterStore {
  _formData;
  _encounterData;

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

  setEncounterData(newData) {
    this._encounterData = newData;
  }

  setFormData(newData) {
    this._formData = newData;
  }
}

const globalEncounterStore = new EncounterStore();

export { globalEncounterStore };

export default EncounterStore;
