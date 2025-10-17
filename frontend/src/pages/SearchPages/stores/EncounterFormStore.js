import { makeAutoObservable } from "mobx";
import { isValid, parseISO, getWeek } from "date-fns";
import { chain, range } from "lodash-es";
import ImageModalStore from "./ImageModalStore";

class EncounterFormStore {
  _formFilters;
  _activeStep = 0;

  _hasFetchedAllEncounters = false;
  _searchResultsAll = [];
  _loadingAll = false;
  _imageCoundPerPage = 20;

  _allMediaAssets = [];
  _pageItems = [];
  _totalItems = 0;
  _totalPages = 0;
  _currentPage = 1;
  _pageSize = 10;

  _showAnnotations = true;

  _encounterData = null;

  imageModalStore;

  constructor() {
    this.formFilters = [];
    this.imageModalStore = new ImageModalStore(this);

    makeAutoObservable(
      this,
      {
        imageModal: false,
      },
      { autoBind: true },
    );
  }

  get encounterData() {
    const selectedImageIndex = this.imageModalStore.selectedImageIndex;
    const encounterId = this.currentPageItems[selectedImageIndex]?.encounterId;
    return (
      this.searchResultsAll.filter((item) => item.id === encounterId)[0] || null
    );
  }

  get formFilters() {
    return this._formFilters;
  }
  set formFilters(newFilters) {
    this._formFilters = newFilters;
  }

  get activeStep() {
    return this._activeStep;
  }
  setActiveStep(step) {
    this._activeStep = step;
  }

  get searchResultsAll() {
    return this._searchResultsAll;
  }
  setSearchResultsAll(data) {
    this._searchResultsAll = data;
  }

  get hasFetchedAllEncounters() {
    return this._hasFetchedAllEncounters;
  }
  setHasFetchedAllEncounters(value) {
    this._hasFetchedAllEncounters = value;
  }

  get loadingAll() {
    return this._loadingAll;
  }
  setLoadingAll(value) {
    this._loadingAll = value;
  }

  get imageCountPerPage() {
    return this._imageCoundPerPage;
  }
  setimageCountPerPage(count) {
    this._imageCoundPerPage = count;
  }

  get pageSize() {
    return this._pageSize;
  }
  setPageSize(size) {
    this._pageSize = size;
  }

  get currentPage() {
    return this._currentPage;
  }
  setCurrentPage(page) {
    this._currentPage = page;
  }

  get start() {
    return (this._currentPage - 1) * this.imageCountPerPage;
  }

  get allMediaAssets() {
    const src = this._searchResultsAll ?? [];
    return src
      .filter(
        (item) =>
          Array.isArray(item.mediaAssets) && item.mediaAssets.length > 0,
      )
      .flatMap((item) =>
        item.mediaAssets.map((a, idx) => ({
          ...a,
          __k: `${a.uuid ?? a.id ?? "na"}-${idx}`,
          encounterId: item.id,
          individualId: item.individualId,
          date: item.date,
          individualDisplayName: item.individualDisplayName,
          verbatimDate: item.verbatimDate,
        })),
      );
  }

  get totalItems() {
    return this.allMediaAssets.length;
  }

  get totalPages() {
    return Math.max(1, Math.ceil(this.totalItems / this.imageCountPerPage));
  }

  get currentPageItems() {
    return this.allMediaAssets.slice(
      this.start,
      this.start + this.imageCountPerPage,
    );
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
    this.formFilters = this.formFilters.filter(
      (f) => f.filterKey !== filterKey,
    );
  }

  resetFilters() {
    this.formFilters = [];
  }

  weekKey = (date) => {
    const d = parseISO(date);
    if (!isValid(d)) {
      console.warn(`Invalid date skipped: ${date}`);
      return null;
    }
    return String(getWeek(d));
  };

  calculateWeeklyDates(dates) {
    if (!Array.isArray(dates)) return [];
    const validDates = dates.filter((d) => typeof d === "string" && d.trim());

    const countsByWeek = chain(validDates)
      .map(this.weekKey)
      .filter((w) => w !== null)
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
