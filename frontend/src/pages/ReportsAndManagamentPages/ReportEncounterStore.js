import { makeAutoObservable } from "mobx";
import axios from "axios";

export class ReportEncounterStore {
  _isLoggedin;
  _imageSectionSubmissionId;
  _imageRequired;
  _imageCount;
  _imagePreview;
  _imageSectionError;
  _imageSectionFileNames;
  _dateTimeSection;
  _speciesSection;
  _placeSection;
  _followUpSection;
  _additionalCommentsSection;
  _success;
  _finished;
  _signInModalShow;
  _exifDateTime;
  _showSubmissionFailedAlert;
  _error;

  constructor() {
    this._imageSectionSubmissionId = null;
    this._imageRequired = true;
    this._imageSectionFileNames = [];
    this._imageSectionError = false;
    this._imageCount = 0;
    this._imagePreview = [];
    this._dateTimeSection = {
      value: null,
      error: false,
      required: true,
    };
    this._speciesSection = {
      value: "",
      error: false,
      required: true,
    };
    this._placeSection = {
      value: "",
      error: false,
      required: true,
    };
    this._additionalCommentsSection = {
      value: "",
    };
    this._followUpSection = {
      submitter: {
        name: "",
        email: "",
      },
      photographer: {
        name: "",
        email: "",
      },
      additionalEmails: "",
      error: false,
    };
    this._success = false;
    this._finished = false;
    this._signInModalShow = false;
    this._exifDateTime = [];
    this._showSubmissionFailedAlert = false;
    this._error = {
      message: "",
      status: "",
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

  get imageSectionError() {
    return this._imageSectionError;
  }

  get imageSectionFileNames() {
    return this._imageSectionFileNames;
  }

  get imageCount() {
    return this._imageCount;
  }

  get imagePreview() {
    return this._imagePreview;
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

  get success() {
    return this._success;
  }

  get finished() {
    return this._finished;
  }

  get signInModalShow() {
    return this._signInModalShow;
  }

  get additionalCommentsSection() {
    return this._additionalCommentsSection;
  }

  get exifDateTime() {
    return this._exifDateTime;
  }

  get showSubmissionFailedAlert() {
    return this._showSubmissionFailedAlert;
  }

  get error() {
    return this._error;
  }

  // Actions
  setImageSectionSubmissionId(value) {
    this._imageSectionSubmissionId = value;
  }

  setImageRequired(value) {
    this._imageRequired = value;
  }

  setImageSectionError(value) {
    this._imageSectionError = value;
  }

  setImageCount(value) {
    this._imageCount = value;
  }

  setImagePreview(value) {
    this._imagePreview = value;
  }

  setImageSectionFileNames(fileName, action = "add") {
    if (action === "add") {
      this._imageSectionFileNames = [...this._imageSectionFileNames, fileName];
    } else if (action === "remove") {
      this._imageSectionFileNames = this._imageSectionFileNames.filter(
        (name) => name !== fileName,
      );
      // delete this._exifDateTime[fileName];
    }
  }

  setDateTimeSectionValue(value) {
    this._dateTimeSection.value = value;
  }

  setDateTimeSectionError(error) {
    this._dateTimeSection.error = error;
  }

  setExifDateTime(exifData) {
    this._exifDateTime.push(exifData);
  }

  setSpeciesSectionValue(value) {
    this._speciesSection.value = value;
  }

  setSpeciesSectionError(error) {
    this._speciesSection.error = error;
  }

  setLocationId(value) {
    this._placeSection.locationId = value;
  }

  setLocationError(error) {
    this._placeSection.error = error;
  }

  setFollowUpSection(value) {
    this._followUpSection.value = value;
  }

  setCommentsSectionValue(value) {
    this._additionalCommentsSection.value = value;
  }

  setSubmitterName(name) {
    this._followUpSection.submitter.name = name;
  }

  setSubmitterEmail(email) {
    this._followUpSection.submitter.email = email;
  }

  setPhotographerName(name) {
    this._followUpSection.photographer.name = name;
  }

  setPhotographerEmail(email) {
    this._followUpSection.photographer.email = email;
  }

  setAdditionalEmails(value) {
    this._followUpSection.additionalEmails = value;
  }

  setSignInModalShow(value) {
    this._signInModalShow = value;
  }

  setShowSubmissionFailedAlert(value) {
    this._showSubmissionFailedAlert = value;
  }

  validateEmails() {
    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

    if (this._followUpSection.submitter.email) {
      if (!emailPattern.test(this._followUpSection.submitter.email))
        return false;
    }

    if (this._followUpSection.photographer.email) {
      if (!emailPattern.test(this._followUpSection.photographer.email))
        return false;
    }

    if (this._followUpSection.additionalEmails) {
      return this._followUpSection.additionalEmails
        .split(",")
        .every((email) => {
          return emailPattern.test(email.trim());
        });
    }

    return true;
  }

  validateFields() {
    console.log("Validating fields");
    let isValid = true;

    if (!this._speciesSection.value) {
      this._speciesSection.error = true;
      isValid = false;
    } else {
      this._speciesSection.error = false;
    }

    if (!this.validateEmails()) {
      console.log("email validation failed");
      isValid = false;
    }

    if (this._imageRequired && this._imageSectionFileNames.length === 0) {
      console.log("1");
      this._imageSectionError = true;
      isValid = false;
    }
    console.log(isValid);

    if (!this._dateTimeSection.value && this._dateTimeSection.required) {
      console.log(JSON.stringify(this._dateTimeSection));
      this._dateTimeSection.error = true;
      isValid = false;
    }

    if (!this._placeSection.locationId && this._placeSection.required) {
      console.log("3");
      this._placeSection.error = true;
      isValid = false;
    }
    console.log("Validation result", isValid);
    return isValid;
  }
  async submitReport() {
    console.log("submitting");
    this._loading = true;
    const readyCaseone =
      this.validateFields() && this._imageSectionFileNames.length > 0;
    const readyCasetwo = this.validateFields() && !this._imageRequired;
    console.log(readyCaseone, readyCasetwo);
    if (readyCaseone || readyCasetwo) {
      try {
        const response = await axios.post("/api/v3/encounters", {
          submissionId: this._imageSectionSubmissionId,
          assetFilenames: this._imageSectionFileNames,
          dateTime: this._dateTimeSection.value,
          taxonomy: this._speciesSection.value,
          locationId: this._placeSection.locationId,
          comments: this._additionalCommentsSection.value,
          submitterName: this._followUpSection.submitter.name,
          submitterEmail: this._followUpSection.submitter.email,
          photographerName: this._followUpSection.photographer.name,
          photographerEmail: this._followUpSection.photographer.email,
        });

        if (response.status === 200) {
          console.log("Report submitted successfully.", response);
          this._speciesSection.value = "";
          this._placeSection.value = "";
          this._followUpSection.value = "";
          this._dateTimeSection.value = "";
          this._imageSectionFileNames = [];
          this._imageSectionSubmissionId = null;
          this._imageCount = 0;
          this._imageSectionError = false;
          this._success = true;
          this._finished = true;

          console.log(this._finished);
          return response.data;
        }
      } catch (error) {
        console.error("Error submitting report", error);
        this._showSubmissionFailedAlert = true;
        this._error.code = error.response.status;
        this._error.message = error.response.data.message;
      }
    } else {
      console.error("Validation failed");
    }
  }
}

export default ReportEncounterStore;