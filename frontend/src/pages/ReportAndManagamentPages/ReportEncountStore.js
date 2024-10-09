import { makeAutoObservable } from "mobx";

export class ReportEncountStore {
  _imageSectionSubmissionId;
  _imageRequired;
  _imageSectionUploadSuccess;
  _imageSectionFileNames;
  _startUpload;
  _dateTimeSection;
  _speciesSection;
  _placeSection;
  _followUpSection;
  _additionalCommentsSection;
  constructor() {
    this._imageSectionSubmissionId = "";
    this._imageRequired = true;
    this._imageSectionUploadSuccess = false;
    this._imageSectionFileNames = [];
    this._startUpload = false;
    this._dateTimeSection = {
      value: "",
      error: false,
    };

    this._speciesSection = {
      value: "",
      error: false,
    };

    this._placeSection = {
      value: "",
      error: false,
    };
    this._followUpSection = {
      value: "",
      error: false,
    };
    makeAutoObservable(this);
  }

  get imageSectionSubmissionId() {
    return this._imageSectionSubmissionId;
  }

  get imageRequired() {
    return this._imageRequired;
  }

  get imageSectionUploadSuccess() {
    return this._imageSectionUploadSuccess;
  }

  get imageSectionFileNames() {
    return this._imageSectionFileNames;
  }

  get startUpload() {
    return this._startUpload;
  }

  get dateTimeSection() {
    return this._dateTimeSection;
  }

  get speciesSection() {
    return this._speciesSection;
  }

  get placeSection() {
    return this._placeSection;
  }

  get followUpSection() {
    return this._followUpSection;
  }

  set imageSectionSubmissionId(value) {
    this._imageSectionSubmissionId = value;
  }

  set imageRequired(value) {
    this._imageRequired = value;
  }

  set imageSectionUploadSuccess(value) {
    this._imageSectionUploadSuccess = value;
  }

  set imageSectionFileNames(value) {
    this._imageSectionFileNames = value;
  }

  set startUpload(value) {
    this._startUpload = value;
  }

  set speciesSection(value) {
    this._speciesSection = value;
  }

  set placeSection(value) {
    this._placeSection.value = value;
  }

  set followUpSection(value) {
    this._followUpSection.value = value;
  }

  validateFields() {
    let isValid = true;
    if (!this.speciesSection.value) {
      this.speciesSection.error = true;
      isValid = false;
    }

    // if(!this.placeSection.value) {
    //   this.placeSection.error = true;
    //   isValid = false;
    // }

    return isValid;
  }

  async submitReport() {
    console.log("Report submitted", this.speciesSection.value);
  }
}
