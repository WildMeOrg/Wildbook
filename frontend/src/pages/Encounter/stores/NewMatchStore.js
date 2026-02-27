import { makeAutoObservable } from "mobx";
import axios from "axios";
import {
  findNodeByValue,
  getAllDescendantValues,
  expandIds,
} from "../../../utils/treeSelectionFunction";
import { reaction } from "mobx";

class NewMatchStore {
  encounterStore;

  _locationId = [];
  _fastlane = true;
  _algorithms = [];
  _owner = "";

  constructor(encounterStore) {
    this.encounterStore = encounterStore;
    makeAutoObservable(this, { encounterStore: false }, { autoBind: true });
    reaction(
      () => this.encounterStore?.encounterData?.locationId,
      (locationId) => {
        if (locationId && this._locationId.length === 0) {
          this._locationId = [locationId];
        }
      },
      { fireImmediately: true },
    );

    // Auto-select default algorithms when iaConfig becomes available
    reaction(
      () => this.iaConfigBasedOnTaxonomy,
      (iaConfig) => {
        if (this._algorithms.length === 0 && iaConfig?.length > 0) {
          const defaults = iaConfig
            .filter((d) => d.default === true)
            .map((d) => d.description);
          if (defaults.length > 0) {
            this._algorithms = defaults;
          }
        }
      },
      { fireImmediately: true },
    );
  }

  get locationId() {
    return this._locationId;
  }
  setLocationID(ids) {
    const values = (ids || [])
      .map((item) => {
        if (!item) return null;
        return typeof item === "object" && item.value ? item.value : item;
      })
      .filter(Boolean);

    const uniq = Array.from(new Set(values));
    this._locationId = uniq;
  }

  get algorithms() {
    return this._algorithms;
  }
  setAlgorithm(vals) {
    this._algorithms = Array.isArray(vals) ? vals : [];
  }

  get owner() {
    return this._owner;
  }
  setOwner(owner) {
    this._owner = owner || "";
  }

  get iaConfigs() {
    return this.encounterStore?.siteSettingsData?.iaConfig || {};
  }

  get iaConfigBasedOnTaxonomy() {
    return this.iaConfigs[this.encounterStore?.encounterData?.taxonomy] || [];
  }

  get annotationIds() {
    const encounterData = this.encounterStore?.encounterData;
    const allMediaAssets = encounterData?.mediaAssets || [];
    const mediaAsset =
      allMediaAssets[this.encounterStore.selectedImageIndex] || {};
    const annotations = mediaAsset?.annotations || [];
    const encounterId = encounterData?.id || "";
    return annotations
      .filter((d) => d.encounterId === encounterId)
      .map((d) => d.id);
  }

  get algorithmOptions() {
    return (
      this.iaConfigBasedOnTaxonomy?.map((d) => ({
        label: d.description,
        value: d.description,
      })) || []
    );
  }

  get matchingAlgorithms() {
    const selected = new Set(this._algorithms || []);
    return (
      this.iaConfigBasedOnTaxonomy?.filter((cfg) =>
        selected.has(cfg.description),
      ) || []
    );
  }

  // initFromPrevious(previousIds = []) {
  //     this.setLocationID(expandIds(this.encounterStore.locationOptions, previousIds));
  // }

  handleStrictChange(checkedValues, _labels, extra) {
    const valueStrings = (checkedValues || []).map((item) =>
      typeof item === "object" ? item.value : item,
    );
    if (!extra || !("triggerValue" in extra)) {
      this.setLocationID(
        expandIds(this.encounterStore.locationIdOptions, valueStrings || []),
      );
      return;
    }
    const triggerId =
      typeof extra.triggerValue === "object"
        ? extra.triggerValue.value
        : extra.triggerValue;
    const checked = !!extra.checked;
    const set = new Set(valueStrings);
    const node = findNodeByValue(
      this.encounterStore.locationIdOptions,
      triggerId,
    );

    if (checked) {
      set.add(triggerId);
      if (node) {
        getAllDescendantValues(node).forEach((v) => set.add(v));
      }
    } else {
      set.delete(triggerId);
      if (node) {
        getAllDescendantValues(node).forEach((v) => set.delete(v));
      }
    }
    this.setLocationID(Array.from(set));
  }

  async buildNewMatchPayload() {
    const locVals = (this._locationId || [])
      .map((v) => (typeof v === "object" ? v.value : v))
      .filter(Boolean);

    const matchingSetFilter = {
      ...(this._owner === "mydata" ? { owner: ["me"] } : {}),
      ...(locVals.length > 0 ? { locationIds: locVals } : {}),
    };

    const payload = {
      v2: true,
      taskParameters: {
        matchingSetFilter,
        matchingAlgorithms: this.matchingAlgorithms,
      },
      annotationIds: this.annotationIds,
      fastlane: this._fastlane,
    };

    const response = await axios.post("/ia", payload, {
      headers: { "Content-Type": "application/json" },
    });
    return response;
  }
}

export default NewMatchStore;
