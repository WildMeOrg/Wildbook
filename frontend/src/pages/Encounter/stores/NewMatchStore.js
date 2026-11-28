import { comparer, makeAutoObservable, reaction } from "mobx";
import axios from "axios";
import {
  findNodeByValue,
  getAllDescendantValues,
  expandIds,
} from "../../../utils/treeSelectionFunction";

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

    // Keep the selection in step with what can be matched right now: drop
    // choices that are no longer offered, then fall back to the defaults when
    // nothing is left. The inputs are compared structurally so a poll that
    // refreshes the same annotations does not re-apply defaults over a
    // selection the user cleared. _algorithms is deliberately not tracked.
    reaction(
      () => ({
        annotationIds: this.annotationIds,
        offered: this.visibleIaConfig.map((d) => d.description),
        defaults: this.visibleIaConfig
          .filter((d) => d.default === true)
          .map((d) => d.description),
      }),
      ({ offered, defaults }) => {
        const kept = this._algorithms.filter((value) =>
          offered.includes(value),
        );
        this._algorithms = kept.length > 0 ? kept : defaults;
      },
      { fireImmediately: true, equals: comparer.structural },
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

  // annotations of this encounter on the selected image: what a match is run on
  get selectedAnnotations() {
    const encounterData = this.encounterStore?.encounterData;
    const allMediaAssets = encounterData?.mediaAssets || [];
    const mediaAsset =
      allMediaAssets[this.encounterStore?.selectedImageIndex] || {};
    const annotations = mediaAsset?.annotations || [];
    const encounterId = encounterData?.id || "";
    return annotations.filter(
      (annotation) => annotation?.encounterId === encounterId,
    );
  }

  get annotationIds() {
    return this.selectedAnnotations.map((d) => d.id);
  }

  get shouldHideVectorAlgorithms() {
    const hasEmbedding = this.selectedAnnotations.some((annotation) => {
      const embeddingCounts = annotation?.embeddingCounts;

      return (
        embeddingCounts &&
        typeof embeddingCounts === "object" &&
        !Array.isArray(embeddingCounts) &&
        Object.keys(embeddingCounts).length > 0
      );
    });

    return !hasEmbedding;
  }

  // The taxonomy's matchers narrowed to the IA classes of the selected
  // annotations, using the _iaClasses list site settings puts on each entry.
  // Narrowing is skipped whenever it cannot be trusted: no class on the
  // annotations, or a class that no entry lists (a class stored under a
  // _save_as name with no IA.json entry of its own still matches through the
  // taxonomy defaults, which _iaClasses does not describe).
  get eligibleIaConfig() {
    const all = this.iaConfigBasedOnTaxonomy;
    const classes = new Set(
      this.selectedAnnotations.map((a) => a?.iaClass).filter(Boolean),
    );
    if (classes.size === 0) return all;

    const advertised = new Set(
      all.flatMap((cfg) =>
        Array.isArray(cfg?._iaClasses) ? cfg._iaClasses : [],
      ),
    );
    for (const iaClass of classes) {
      if (!advertised.has(iaClass)) return all;
    }
    return all.filter(
      (cfg) =>
        !Array.isArray(cfg?._iaClasses) ||
        cfg._iaClasses.some((iaClass) => classes.has(iaClass)),
    );
  }

  // what the dialog offers: eligible matchers, minus vector ones when the
  // selected annotations have no embedding to match with
  get visibleIaConfig() {
    if (!this.shouldHideVectorAlgorithms) return this.eligibleIaConfig;
    return this.eligibleIaConfig.filter(
      (cfg) => !String(cfg?.description).toLowerCase().includes("vector"),
    );
  }

  get algorithmOptions() {
    return this.visibleIaConfig.map((d) => ({
      label: d.description,
      value: d.description,
    }));
  }

  get matchingAlgorithms() {
    const selected = new Set(this._algorithms || []);
    return this.visibleIaConfig.filter((cfg) => selected.has(cfg.description));
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
