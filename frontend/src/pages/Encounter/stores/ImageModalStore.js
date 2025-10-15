import { makeAutoObservable } from "mobx";
import axios from "axios";

class ImageModalStore {
  encounterStore;

  _selectedImageIndex = 0;
  _selectedAnnotationId = null;
  _showAnnotations = true;

  _addTagsFieldOpen = false;
  _selectedKeyword = null;
  _selectedLabeledKeyword = null;
  _selectedAllowedValues = null;

  constructor(encounterStore) {
    this.encounterStore = encounterStore;
    makeAutoObservable(
      this,
      {
        encounterStore: false,
      },
      { autoBind: true },
    );
  }

  get selectedImageIndex() {
    return this.encounterStore.selectedImageIndex || 0;
  }
  setSelectedImageIndex(index) {
    return this.encounterStore.setSelectedImageIndex(index);
  }

  get encounterAnnotations() {
    return this.encounterStore.encounterAnnotations;
  }

  get selectedAnnotationId() {
    return this.encounterStore.selectedAnnotationId;
  }
  setSelectedAnnotationId(annotationId) {
    this.encounterStore.setSelectedAnnotationId(annotationId);
  }

  get currentAnnotation() {
    return this.encounterStore.currentAnnotation;
  }

  get showAnnotations() {
    return this._showAnnotations;
  }
  setShowAnnotations(show) {
    this._showAnnotations = show;
  }

  get tags() {
    const encounterData = this.encounterStore.encounterData;
    return (
      encounterData?.mediaAssets?.[this.selectedImageIndex]?.keywords || []
    );
  }

  get addTagsFieldOpen() {
    return this._addTagsFieldOpen;
  }
  setAddTagsFieldOpen(isOpen) {
    this._addTagsFieldOpen = isOpen;
  }

  get availableKeywords() {
    return this.encounterStore.siteSettingsData?.keyword || [];
  }

  get availableKeywordsId() {
    return this.encounterStore.siteSettingsData?.keywordId || [];
  }

  get selectedKeyword() {
    return this._selectedKeyword;
  }
  setSelectedKeyword(keyword) {
    this._selectedKeyword = keyword;
  }

  get availabelLabeledKeywords() {
    return Object.keys(
      this.encounterStore.siteSettingsData?.labeledKeyword || {},
    );
  }

  get labeledKeywordAllowedValues() {
    const siteSettings = this.encounterStore.siteSettingsData;
    return (
      siteSettings?.labeledKeywordAllowedValues?.[
      this._selectedLabeledKeyword
      ] || []
    );
  }

  get selectedLabeledKeyword() {
    return this._selectedLabeledKeyword;
  }
  setSelectedLabeledKeyword(keyword) {
    this._selectedLabeledKeyword = keyword;
  }

  get selectedAllowedValues() {
    return this._selectedAllowedValues;
  }
  setSelectedAllowedValues(allowedValues) {
    this._selectedAllowedValues = allowedValues;
  }

  setOpenMatchCriteriaModal(isOpen) {
    this.encounterStore.modals.setOpenMatchCriteriaModal(isOpen);
  }

  get matchResultClickable() {
    return this.encounterStore.matchResultClickable;
  }

  get currentMediaAsset() {
    const encounterData = this.encounterStore.encounterData;
    return encounterData?.mediaAssets?.[this.selectedImageIndex];
  }

  get encounterData() {
    return this.encounterStore.encounterData;
  }

  async removeAnnotation(annotationId) {
    const encounterData = this.encounterStore.encounterData;
    const result = await axios.patch(
      `/api/v3/encounters/${encounterData.id}`,
      [
        {
          op: "remove",
          path: "annotations",
          value: annotationId,
        },
      ],
      {
        headers: { "Content-Type": "application/json" },
      },
    );

    if (result.status === 200) {
      this.setSelectedAnnotationId(null);
      await this.refreshEncounterData();
    }

    return result;
  }

  async deleteImage() {
    const encounterData = this.encounterStore.encounterData;
    const mediaAssetId = this.currentMediaAsset?.id;

    if (!mediaAssetId) {
      throw new Error("No media asset selected");
    }

    return axios.post(
      "/MediaAssetAttach",
      {
        detach: "true",
        EncounterID: encounterData.id,
        MediaAssetID: mediaAssetId,
      },
      {
        headers: { "Content-Type": "application/json" },
      },
    );
  }

  async refreshEncounterData() {
    return this.encounterStore.refreshEncounterData();
  }

  reset() {
    this.setSelectedAnnotationId(null);
    this._addTagsFieldOpen = false;
    this._selectedKeyword = null;
    this._selectedLabeledKeyword = null;
    this._selectedAllowedValues = null;
  }
}

export default ImageModalStore;
