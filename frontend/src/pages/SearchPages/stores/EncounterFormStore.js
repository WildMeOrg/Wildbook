import { makeAutoObservable } from "mobx";
import { isValid, parseISO, getWeek } from "date-fns";
import { chain, range } from "lodash-es";
import ImageModalStore from "./ImageModalStore";
import { toJS } from "mobx";
import axios from "axios";

class EncounterFormStore {
  _formFilters;
  _activeStep = 0;

  _siteSettingsData = null;

  _hasFetchedAllEncounters = false;
  _searchResultsMediaAssets = [];
  _loadingAll = false;
  _selectedRows = [];
  _selectedProjects = [];
  //0: hide, 1: show select 2: show adding 3: show success 4: show error
  _projectBannerStatusCode = 0;
  _clearSelectedRows = false;
  _imageCoundPerPage = 20;

  _allMediaAssets = [];
  _pageItems = [];
  _totalItems = 0;
  _totalPages = 0;
  _currentPage = 1;
  _pageSize = 20;
  _start = 0;

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
      this.searchResultsMediaAssets.filter((item) => item.id === encounterId)[0] || null
    );
  }

  get siteSettingsData() {
    return this._siteSettingsData;
  }
  setSiteSettingsData(data) {
    this._siteSettingsData = data;
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

  get searchResultsMediaAssets() {
    return this._searchResultsMediaAssets;
  }
  setSearchResultsMediaAssets(data) {
    this._searchResultsMediaAssets = data;
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

  get selectedRows() {
    return this._selectedRows || [];
  }
  setSelectedRows(rows) {
    this._selectedRows = rows;
  }

  get selectedProjects() {
    return this._selectedProjects || [];
  }
  setSelectedProjects(projects) {
    this._selectedProjects = projects || [];
  }

  get projectBannerStatusCode() {
    return this._projectBannerStatusCode;
  }
  setprojectBannerStatusCode(code) {
    this._projectBannerStatusCode = code;
  }

  get clearSelectedRows() {
    return this._clearSelectedRows;
  }
  setClearSelectedRows(value) {
    this._clearSelectedRows = value;
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

  get start () {
    return this._start;
  }

  setStart(start) {
    this._start = start;
  }

  get lastEncounterId() {
    return this.currentPageItems[this.pageSize - 1]?.encounterId;
  }

  get lastEncounterIndex() {
    const encounterId = this.currentPageItems[this.pageSize - 1]?.encounterId ;
    console.log("Encounter ID:", JSON.stringify(encounterId));
    const encounterIndex = this._searchResultsMediaAssets.findIndex(
      (item) => item.id === encounterId,
    );
    if (encounterIndex !== -1) {
      return encounterIndex;
    }
    return null;
  }

  get lastMediaAssetId() {
    return this.currentPageItems[this.pageSize - 1]?.id ;
  }

  get allMediaAssets() {
    const src = this._searchResultsMediaAssets ?? [];
    return src
      .filter(
        (item) =>
          Array.isArray(item.mediaAssets) && item.mediaAssets.length > 0,
      )
      .flatMap((item) =>
        item.mediaAssets.map((a, idx) => ({
          ...a,
          __k: `${item.id}-${idx}-${a.uuid ?? a.id ?? ""}`,
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
    return Math.max(1, Math.ceil(this.totalItems / this.pageSize));
  }

  get currentPageItems() {
    const encounter = this.searchResultsMediaAssets.find(
      (item) => item.id === this.lastEncounterId,
    );
    const imageIndex = this.searchResultsMediaAssets.findIndex
    return this.allMediaAssets.slice(0, this.pageSize);
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

  async addEncountersToProject() {
    if (
      !this._selectedRows ||
      this._selectedRows.length === 0 ||
      !this._selectedProjects ||
      this._selectedProjects.length === 0
    ) {
      console.error("No project selected to add the encounter to.");
      return;
    }
    this.setprojectBannerStatusCode(2);
    const payload = {
      projects: toJS(
        this._selectedProjects.map((project) => ({
          id: project,
          encountersToAdd: this.selectedRows.map((row) => row.id),
        })),
      ),
    };
    try {
      const result = await axios.post("/ProjectUpdate", payload, {
        headers: { "Content-Type": "application/json" },
      });
      if (result.status === 200) {
        this.setSelectedProjects([]);
        this.setSelectedRows([]);
        this.setprojectBannerStatusCode(3);
        this.setClearSelectedRows(!this._clearSelectedRows);
        setTimeout(() => {
          if (
            this.selectedRows.length === 0 &&
            this.projectBannerStatusCode === 3
          ) {
            this.setprojectBannerStatusCode(0);
          }
        }, 2500);
      } else {
        this.setprojectBannerStatusCode(4);
      }
    } catch (error) {
      this.setprojectBannerStatusCode(4);
      throw error;
    }
  }
}

const globalEncounterFormStore = new EncounterFormStore();

export { globalEncounterFormStore };

export default EncounterFormStore;
