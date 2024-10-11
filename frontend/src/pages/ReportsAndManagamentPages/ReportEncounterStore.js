import { makeAutoObservable } from "mobx";

export class ReportEncounterStore {
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
    this._additionalCommentsSection = {
      value: "",
    };
    makeAutoObservable(this);
  }

  // Getters
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

  // Actions
  setImageSectionSubmissionId(value) {
    this._imageSectionSubmissionId = value;
  }

  setImageRequired(value) {
    this._imageRequired = value;
  }

  setImageSectionUploadSuccess(value) {
    this._imageSectionUploadSuccess = value;
  }

  setImageSectionFileNames(value) {
    this._imageSectionFileNames = value;
  }

  setStartUpload(value) {
    this._startUpload = value;
  }

  setSpeciesSectionValue(value) {
    this._speciesSection.value = value;
  }

  setSpeciesSectionError(error) {
    this._speciesSection.error = error;
  }

  setPlaceSection(value) {
    this._placeSection.value = value;
  }

  setFollowUpSection(value) {
    this._followUpSection.value = value;
  }

  setCommentsSectionValue(value) {
    this._additionalCommentsSection.value = value;
  }

  validateFields() {
    let isValid = true;

    if (!this._speciesSection.value) {
      this._speciesSection.error = true;
      isValid = false;
    } else {
      this._speciesSection.error = false;
    }

    // Uncomment the place section validation if needed
    // if (!this._placeSection.value) {
    //   this._placeSection.error = true;
    //   isValid = false;
    // } else {
    //   this._placeSection.error = false;
    // }

    return isValid;
  }

  async submitReport() {
    if (this.validateFields()) {
      console.log("Report submitted", this._speciesSection.value);
      // Additional logic for report submission can be added here.
    } else {
      console.error("Validation failed");
    }
  }
}

export default ReportEncounterStore;
