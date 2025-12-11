/* eslint-disable no-unused-vars */
import { makeAutoObservable } from "mobx";
import axios from "axios";

class ImageModalStore {
  encounterFormStore;

  _encounterData = null;

  _selectedImageIndex = 0;
  _selectedAnnotationId = null;
  _showAnnotations = true;

  _addTagsFieldOpen = false;
  _selectedKeyword = null;
  _selectedLabeledKeyword = null;
  _selectedAllowedValues = null;

  constructor(encounterFormStore) {
    this.encounterStore = encounterFormStore;
    makeAutoObservable(
      this,
      {
        encounterStore: false,
      },
      { autoBind: true },
    );
  }

  get encounterData() {
    return this.encounterStore.encounterData;
  }

  get selectedImageIndex() {
    return this._selectedImageIndex || 0;
  }
  setSelectedImageIndex(index) {
    this._selectedImageIndex = index;
  }

  get encounterAnnotations() {
    return (
      (this.encounterData?.mediaAssets || []).flatMap(
        (asset) => asset.annotations || [],
      ) || []
    );
  }

  get selectedAnnotationId() {
    return this._selectedAnnotationId;
  }
  setSelectedAnnotationId(annotationId) {
    this._selectedAnnotationId = annotationId;
  }

  get currentAnnotation() {
    return this.currentAnnotation;
  }

  get showAnnotations() {
    return this._showAnnotations;
  }
  setShowAnnotations(show) {
    this._showAnnotations = show;
  }

  get tags() {
    return (
      this.encounterStore.currentPageItems?.[this.selectedImageIndex]
        ?.mediaAssetKeywords || []
    );
  }

  get addTagsFieldOpen() {
    return this._addTagsFieldOpen;
  }
  setAddTagsFieldOpen(isOpen) {
    this._addTagsFieldOpen = isOpen;
  }

  get availableKeywords() {
    return this.siteSettingsData?.keyword || [];
  }

  get availableKeywordsId() {
    return this.siteSettingsData?.keywordId || [];
  }

  get selectedKeyword() {
    return this._selectedKeyword;
  }
  setSelectedKeyword(keyword) {
    this._selectedKeyword = keyword;
  }

  get availabelLabeledKeywords() {
    return Object.keys(this.siteSettingsData?.labeledKeyword || {});
  }

  get labeledKeywordAllowedValues() {
    const siteSettings = this.siteSettingsData;
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

  get matchResultClickable() {
    const selectedAnnotation = this.encounterAnnotations.find(
      (annotation) => annotation.id === this.selectedAnnotationId,
    );

    if (!selectedAnnotation) return false;

    const iaTaskId = !!selectedAnnotation?.iaTaskId;
    const skipId = !!selectedAnnotation?.iaTaskParameters?.skipIdent;
    const identActive = iaTaskId && !skipId;

    const encounterData = this.encounterData;
    const detectionComplete =
      encounterData?.mediaAssets?.[this.selectedImageIndex]?.detectionStatus ===
      "complete";
    const identificationStatus =
      selectedAnnotation?.identificationStatus === "complete" ||
      selectedAnnotation?.identificationStatus === "pending";

    return identActive && (detectionComplete || identificationStatus);
  }

  get modals() {
    return this.modals;
  }

  async removeAnnotation(annotationId, encounterId) {
    const result = await axios.patch(
      `/api/v3/encounters/${encounterId}`,
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

  setOpenMatchCriteriaModal(isOpen) {
    // this.encounterStore.modals.setOpenMatchCriteriaModal(isOpen);
  }

  async deleteImage() {
    const encounterData = this.encounterData;
    const mediaAssetId = this.currentPageItems[this.selectedImageIndex]?.id;

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
    const encounterId = this.encounterData?.id;

    try {
      const response = await axios.get(`/api/v3/encounters/${encounterId}`);
      if (response.status === 200 && response.data) {
        const currentImageIndex = this._selectedImageIndex;
        //re fretch the encounter data
        // this.setEncounterData(response.data);
        if (currentImageIndex < (response.data.mediaAssets?.length || 0)) {
          this.setSelectedImageIndex(currentImageIndex);
        }
        return response.data;
      }
    } catch (error) {
      console.error("Failed to refresh encounter data:", error);
      throw error;
    }
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
