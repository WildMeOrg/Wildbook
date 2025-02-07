import { makeAutoObservable } from "mobx";

class FormStore {
  formFilters = [];

  constructor() {
    makeAutoObservable(this);
  }

  setFilters(newFilters) {
    this.formFilters = newFilters;
  }

  addFilter(newFilter) {
    const existingIndex = this.formFilters.findIndex(
      (f) => f.filterId === newFilter.filterId,
    );
    if (existingIndex === -1) {
      this.formFilters.push(newFilter);
    } else {
      this.formFilters[existingIndex] = newFilter;
    }
  }

  removeFilter(filterId) {
    this.formFilters = this.formFilters.filter((f) => f.filterId !== filterId);
  }

  resetFilters() {
    this.formFilters = [];
  }
}

const formStore = new FormStore();
export default formStore;
