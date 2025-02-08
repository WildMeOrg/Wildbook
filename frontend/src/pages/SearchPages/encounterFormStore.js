import { makeAutoObservable } from "mobx";

class EncounterFormStore {
  _formFilters;

  constructor() {
    this.formFilters = [];
    makeAutoObservable(this);
  }

  get formFilters() {
    return this._formFilters;
  }

  set formFilters(newFilters) {
    this._formFilters = newFilters;
  }

  addFilter(field, value, filterKey, term, filterId) {

    const existingIndex = this.formFilters.findIndex(
      (f) => f.filterId === filterId,
    );
    if (existingIndex === -1) {
      console.log(1);
      this.formFilters.push({
        filterId: filterId,
        clause: "filter",
        query: {
          [term]: {
            [field]: value,
          },
        },
        filterKey: filterKey,
      });
    } else {
      this.formFilters[existingIndex] = {
        filterId: filterId,
        clause: "filter",
        query: {
          [term]: {
            [field]: value,
          },
        },
        filterKey: filterKey
      }
    }
    console.log("----------------------", JSON.stringify(this.formFilters));
  }

  removeFilter(filterId) {
    this.formFilters = this.formFilters.filter((f) => f.filterId !== filterId);
  }

  resetFilters() {
    this.formFilters = [];
  }
}

const globalEncounterFormStore = new EncounterFormStore();

export { globalEncounterFormStore };

export default EncounterFormStore;
