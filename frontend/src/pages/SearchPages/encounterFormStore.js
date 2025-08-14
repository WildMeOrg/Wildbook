import { makeAutoObservable } from "mobx";
import { isValid, parseISO, getWeek } from "date-fns";
import { chain, range } from 'lodash-es'

class EncounterFormStore {
  _formFilters;
  _activeStep = 0;
  _hasFetchedAllEncounters = false;
  _searchResultsAll = [];
  _loadingAll = false;
  _imageCoundPerPage = 20;

  constructor() {
    this.formFilters = [];
    makeAutoObservable(this);
  }

  get formFilters() {
    return this._formFilters;
  }

  get activeStep() {
    return this._activeStep;
  }

  get searchResultsAll() {
    return this._searchResultsAll;
  }

  get hasFetchedAllEncounters() {
    return this._hasFetchedAllEncounters;
  }

  get loadingAll() {
    return this._loadingAll;
  }

  get imageCountPerPage() {
    return this._imageCoundPerPage;
  }

  set formFilters(newFilters) {
    this._formFilters = newFilters;
  }

  setHasFetchedAllEncounters(value) {
    this._hasFetchedAllEncounters = value;
  }

  setLoadingAll(value) {
    this._loadingAll = value;
  }

  setActiveStep(step) {
    this._activeStep = step;
  }

  setimageCountPerPage(count) {
    this._imageCoundPerPage = count;
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

  setSearchResultsAll(data) {
    this._searchResultsAll = data;
  }

  weekKey = (date) => {
    const d = parseISO(date);
    if (!isValid(d)) {
      console.warn(`Invalid date skipped: ${date}`);
      return null;
    }
    return String(getWeek(d));
  }

  calculateWeeklyDates(dates) {
    if (!Array.isArray(dates)) return [];
    const validDates = dates.filter(d => typeof d === 'string' && d.trim());

    const countsByWeek = chain(validDates)
      .map(this.weekKey)
      .filter(w => w !== null)
      .countBy()
      .value();

    const result = range(1, 54).map((week) => {
      const weekKey = String(week);
      return {
        week: weekKey,
        count: countsByWeek[weekKey] || 0,
        // date: format(startOfWeek(new Date(), { weekStartsOn: 1 }), 'yyyy-MM-dd'),
      };
    });
    return result;
  }
}

const globalEncounterFormStore = new EncounterFormStore();

export { globalEncounterFormStore };

export default EncounterFormStore;
