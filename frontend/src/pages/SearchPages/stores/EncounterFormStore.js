import { makeAutoObservable } from "mobx";
import ImageModalStore from "./ImageModalStore";
import { toJS } from "mobx";
import axios from "axios";
import { action } from "mobx";

class EncounterFormStore {
  _formFilters;
  _activeStep = 0;

  _siteSettingsData = null;
  _siteSettingsLoading = true;

  _loadingAll = false;
  _selectedRows = [];
  _selectedProjects = [];
  //0: hide, 1: show select 2: show adding 3: show success 4: show error
  _projectBannerStatusCode = 0;
  _clearSelectedRows = false;

  _currentPageItems = [];
  _previousPageItems = [];
  _currentPage = 0;
  _pageSize = 20;
  _start = 0;
  _assetOffset = 0;

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

  get siteSettingsData() {
    return this._siteSettingsData;
  }
  setSiteSettingsData(data) {
    this._siteSettingsData = data;
  }

  get siteSettingsLoading() {
    return this._siteSettingsLoading;
  }
  setSiteSettingsLoading(loading) {
    this._siteSettingsLoading = loading;
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

  resetGallery = action(() => {
    this.setCurrentPageItems([]);
    this.setStart(0);
    this.setAssetOffset(0);
    this.clearPreviousPageItems();
    this.setCurrentPage?.(0);
  });

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
    if (page < 0) {
      page = 0;
    }
    this._currentPage = page;
  }

  get assetOffset() {
    return this._assetOffset || 0;
  }
  setAssetOffset(offset) {
    this._assetOffset = offset;
  }

  get mediaAssetsSearchQuery() {
    const filterOnMediaAssets = {
      filterId: "numberMediaAssets",
      clause: "filter",
      query: { range: { numberMediaAssets: { gte: 1 } } },
      filterKey: "Number Media Assets",
      path: "",
    };
    const base = this.formFilters || [];
    const has = base.some((f) => f.filterId === "numberMediaAssets");
    if (!has) {
      return [...base, filterOnMediaAssets];
    } else {
      return base;
    }
  }

  get start() {
    return this._start;
  }

  setStart(start) {
    this._start = start;
  }

  get currentPageItems() {
    return this._currentPageItems || [];
  }
  setCurrentPageItems(items) {
    this._currentPageItems = items;
  }

  get previousPageItems() {
    return this._previousPageItems;
  }
  setPreviousPageItems(index, data) {
    if (index < 0) {
      return;
    }
    this._previousPageItems[index] = Array.isArray(data) ? data.slice() : [];
  }

  clearPreviousPageItems() {
    this._previousPageItems = [];
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
