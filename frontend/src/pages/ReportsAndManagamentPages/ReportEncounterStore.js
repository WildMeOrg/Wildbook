import { makeAutoObservable } from "mobx";
import axios from "axios";

export class ReportEncounterStore {
  _isLoggedin;
  _imageSectionSubmissionId;
  _imageRequired;
  _imageCount;
  _imageSectionError;
  _imageSectionUploadSuccess;
  _imageSectionFileNames;
  _startUpload;
  _dateTimeSection;
  _speciesSection;
  _placeSection;
  _followUpSection;
  _additionalCommentsSection;
  _success;
  _finished;

  constructor() {
    this._imageSectionSubmissionId = null;
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
      required: true,
    };
    this._placeSection = {
      value: "",
      error: false,
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
    this._imageRequired = true;
    this._imageSectionError = false;
    this._imageCount = 0;
    this._success = false;
    this._finished = false;
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

  get imageSectionUploadSuccess() {
    return this._imageSectionUploadSuccess;
  }

  get imageSectionFileNames() {
    return this._imageSectionFileNames;
  }

  get imageCount() {
    return this._imageCount;
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

  get success() {
    return this._success;
  }

  get finished() {
    return this._finished;
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

  SetImageCount(value) {
    this._imageCount = value;
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
    } else {
      console.log("Followup information validated");
    }

    console.log(this._imageSectionError);
    if (this._imageSectionError) {
      console.log(this._imageRequired, this._imageCount);

      if (this._imageRequired && this._imageCount === 0) {
        this._imageSectionError = true;
      }

      if (this._imageSectionError) {
        isValid = false;
      }

      // Uncomment the place section validation if needed
      // if (!this._placeSection.value) {
      //   this._placeSection.error = true;
      //   isValid = false;
      // } else {
      //   this._placeSection.error = false;
      // }
    }
    return isValid;
  }
  async submitReport() {
    console.log("submitting");
    const readyCaseone =
      this.validateFields() && this._imageSectionUploadSuccess;
    const readyCasetwo = this.validateFields() && !this._imageRequired;
    console.log(readyCaseone, readyCasetwo);
    if (readyCaseone || readyCasetwo) {
      console.log("Report submitted, calling api", this._speciesSection.value);
      // Call the API here
      const response = await axios.post("/api/v3/encounters", {
        submissionId: this._imageSectionSubmissionId,
        dateTime: "2001-04-30T00:00",
        taxonomy: this._speciesSection.value,
        locationId: "Mpala.North",
        // followUp: this._followUpSection.value,
        // images: this._imageSectionFileNames,
      });

      if (response.status === 200) {
        console.log("Report submitted successfully.", response);
        this._speciesSection.value = "";
        this._placeSection.value = "";
        this._followUpSection.value = "";
        this._dateTimeSection.value = "";
        this._imageSectionFileNames = [];
        this._imageSectionSubmissionId = "";
        this._imageSectionUploadSuccess = false;
        this._imageCount = 0;
        this._startUpload = false;
        this._imageSectionError = false;
        this._success = true;
        this._finished = true;

        console.log(this._finished);
      } else {
        this._finished = true;
        this._success = false;
        console.error("Report submission failed");
      }

      // Additional logic for report submission can be added here.
    } else {
      console.error("Validation failed");
    }
  }
}

export default ReportEncounterStore;
