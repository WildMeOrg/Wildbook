import { makeAutoObservable } from "mobx";
import axios from "axios";

class ImageModalStore { 

  _selectedImageIndex = 0;
  _selectedAnnotationId = null;
  _showAnnotations = true;

  _addTagsFieldOpen = false;
  _selectedKeyword = null;
  _selectedLabeledKeyword = null;
  _selectedAllowedValues = null;

  constructor() {
    makeAutoObservable(
      this,      
      { autoBind: true },
    );
  }

  get selectedImageIndex() {
    return this.selectedImageIndex || 0;
  }
  setSelectedImageIndex(index) {
    return this.setSelectedImageIndex(index);
  }

  get encounterAnnotations() {
    return this.encounterAnnotations;
  }

  get selectedAnnotationId() {
    return this.selectedAnnotationId;
  }
  setSelectedAnnotationId(annotationId) {
    this.setSelectedAnnotationId(annotationId);
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
    const encounterData = this.encounterData;
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
    return Object.keys(
      this.siteSettingsData?.labeledKeyword || {},
    );
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

  get currentMediaAsset() {
    const encounterData = this.encounterData;
    return encounterData?.mediaAssets?.[this.selectedImageIndex];
  }

  get encounterData() {
    return this.encounterData;
  }

  get modals() {
    return this.modals;
  }

  async removeAnnotation(annotationId) {
    const encounterData = this.encounterData;
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
    const encounterData = this.encounterData;
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
    return this.refreshEncounterData();
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
