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
    makeAutoObservable(this, {
      encounterStore: false,
    }, { autoBind: true });
  }

  get selectedImageIndex() {
    return this._selectedImageIndex;
  }
  setSelectedImageIndex(index) {
    console.log("Setting selected image index:", index);
    this._selectedImageIndex = index;
    this._selectedAnnotationId = null;
  }

  get encounterAnnotations() {
    const encounterData = this.encounterStore.encounterData;
    return encounterData?.mediaAssets?.[this._selectedImageIndex]?.annotations?.filter(
      data => data.encounterId === encounterData.id
    ) || [];
  }

  get selectedAnnotationId() {
    return this._selectedAnnotationId;
  }

  get currentAnnotation() {
    return this.encounterAnnotations.find(
      annotation => annotation.id === this._selectedAnnotationId
    ) || null;
  }

  get showAnnotations() {
    return this._showAnnotations;
  }
  setShowAnnotations(show) {
    console.log("Setting showAnnotations to:", show);
    this._showAnnotations = show;
  }

  get tags() {
    const encounterData = this.encounterStore.encounterData;
    return encounterData?.mediaAssets?.[this._selectedImageIndex]?.keywords || [];
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
    return Object.keys(this.encounterStore.siteSettingsData?.labeledKeyword || {});
  }

  get labeledKeywordAllowedValues() {
    const siteSettings = this.encounterStore.siteSettingsData;
    return siteSettings?.labeledKeywordAllowedValues?.[this._selectedLabeledKeyword] || [];
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
      annotation => annotation.id === this._selectedAnnotationId
    );
    
    if (!selectedAnnotation) return false;

    const iaTaskId = !!selectedAnnotation?.iaTaskId;
    const skipId = !!selectedAnnotation?.iaTaskParameters?.skipIdent;
    const identActive = iaTaskId && !skipId;
    
    const encounterData = this.encounterStore.encounterData;
    const detectionComplete = encounterData?.mediaAssets?.[this._selectedImageIndex]?.detectionStatus === "complete";
    const identificationStatus = selectedAnnotation?.identificationStatus === "complete" || 
                                 selectedAnnotation?.identificationStatus === "pending";

    return identActive && (detectionComplete || identificationStatus);
  }

  get currentMediaAsset() {
    const encounterData = this.encounterStore.encounterData;
    return encounterData?.mediaAssets?.[this._selectedImageIndex];
  }

  get encounterData() {
    return this.encounterStore.encounterData;
  }

  get modals() {
    return this.encounterStore.modals;
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
      }
    );
    
    if (result.status === 200) {
      this._selectedAnnotationId = null;
      await this.encounterStore.refreshEncounterData();
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
      }
    );
  }

  // Reset state when modal closes
  reset() {
    this._selectedAnnotationId = null;
    this._addTagsFieldOpen = false;
    this._selectedKeyword = null;
    this._selectedLabeledKeyword = null;
    this._selectedAllowedValues = null;
  }
}

export default ImageModalStore;