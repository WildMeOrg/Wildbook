import { makeAutoObservable } from "mobx";

class ImageModalStore {
  _open = false;
  _selectedImageIndex = 0;
  _showAnnotations = true;
  _selectedAnnotationId = null;
  _assets = [];
  _access = "read";

  constructor() {
    makeAutoObservable(this, {}, { autoBind: true });
  }

  get open() {
    return this._open;
  }

  get selectedImageIndex() {
    return this._selectedImageIndex;
  }

  get showAnnotations() {
    return this._showAnnotations;
  }

  get selectedAnnotationId() {
    return this._selectedAnnotationId;
  }

  get assets() {
    return this._assets;
  }

  get access() {
    return this._access;
  }

  get selectedAsset() {
    return this._assets[this._selectedImageIndex] || null;
  }

  get encounterData() {
    return this.selectedAsset?.encounterData || {};
  }

  get currentRects() {
    return (
      this.selectedAsset?.annotations
        ?.filter(
          (annotation) => !annotation.isTrivial && annotation.boundingBox,
        )
        ?.map((annotation) => ({
          x: annotation.boundingBox[0],
          y: annotation.boundingBox[1],
          width: annotation.boundingBox[2],
          height: annotation.boundingBox[3],
          rotation: annotation.theta || 0,
          annotationId: annotation.id,
          encounterId:
            annotation.encounterId || this.selectedAsset?.encounterId,
          viewpoint: annotation.viewpoint,
          iaClass: annotation.iaClass,
        })) || []
    );
  }

  setOpen(value) {
    this._open = value;
  }

  setSelectedImageIndex(value) {
    this._selectedImageIndex = value;
    this._selectedAnnotationId = null;
  }

  setShowAnnotations(value) {
    this._showAnnotations = value;
  }

  setSelectedAnnotationId(value) {
    this._selectedAnnotationId = value;
  }

  setAssets(assets) {
    this._assets = assets || [];
  }

  setAccess(value) {
    this._access = value || "read";
  }

  openModal({ assets = [], selectedImageIndex = 0, access = "read" }) {
    this._assets = assets;
    this._selectedImageIndex = selectedImageIndex;
    this._access = access;
    this._selectedAnnotationId = null;
    this._open = true;
  }

  closeModal() {
    this._open = false;
    this._selectedAnnotationId = null;
  }

  reset() {
    this._open = false;
    this._selectedImageIndex = 0;
    this._showAnnotations = true;
    this._selectedAnnotationId = null;
    this._assets = [];
    this._access = "read";
  }
}

export default ImageModalStore;
