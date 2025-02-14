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

  addFilter(filterId, clause, query, filterKey, path = "") {
    const existingIndex = this.formFilters.findIndex(
      (f) => f.filterId === filterId,
    );
    if (existingIndex === -1) {
      this.formFilters.push({
        filterId: filterId,
        clause: clause,
        query: query,
        filterKey: filterKey,
        path: path,
      });
    } else {
      this.formFilters[existingIndex] = {
        filterId: filterId,
        clause: clause,
        query: query,
        filterKey: filterKey,
        path: path,
      };
    }    
  }

  removeFilter(filterId) {
    this.formFilters = this.formFilters.filter((f) => f.filterId !== filterId);
  }

  removeFilterByFilterKey(filterKey) {
    this.formFilters = this.formFilters.filter((f) => f.filterKey !== filterKey);
  }

  resetFilters() {
    this.formFilters = [];
  }
}

const globalEncounterFormStore = new EncounterFormStore();

export { globalEncounterFormStore };

export default EncounterFormStore;
