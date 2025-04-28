// Optimized MobX Store for Bulk Import logic
import { makeAutoObservable } from "mobx";
import Flow from "@flowjs/flow.js";
import { v4 as uuidv4 } from "uuid";

export class BulkImportStore {
  _imagePreview = [];
  _imageSectionFileNames = [];
  _imageRequired = true;
  _imageSectionError = false;
  _imageCount = 0;
  _flow = null;
  _submissionId = null;
  _spreadsheetData = [];
  _imageUploadStatus = "notStarted";
  _steps = ["Upload Image", "Upload Spreadsheet", "Review"];
  _activeStep = 0;
  _imageUploadProgress = 0;
  _spreadsheetUploadProgress = 0;
  _uploadFinished = false;
  _uploadedImages = [];
  _initialUploadFileCount = 0;
  _columnsDef = ["mediaAsset", "name", "date", "location", "submitterID"];
  _validationRules = {
    mediaAsset: {
      required: true,
    },
    name: {
      required: true,
      message: "Name must contain only letters and spaces",
    },
    date: {
      required: true,
      pattern: /^\d{4}$/,
      message: "Date must be a 4-digit year",
    },
    location: {
      required: true,
      message: "Location must contain only letters and spaces",
    },
    submitterID: {
      required: true,
      pattern: /^[a-zA-Z0-9]+$/,
      message: "Submitter ID must contain only letters and numbers",
    },
  };

  _validLocationIDs = [];
  _validSubmitterIDs = [];

  constructor() {
    makeAutoObservable(this);
  }

  get imagePreview() {
    return this._imagePreview;
  }

  get imageSectionFileNames() {
    return this._imageSectionFileNames;
  }

  get imageRequired() {
    return this._imageRequired;
  }

  get imageSectionError() {
    return this._imageSectionError;
  }

  get flow() {
    return this._flow;
  }

  get spreadsheetData() {
    return this._spreadsheetData;
  }

  get imageUploadStatus() {
    return this._imageUploadStatus;
  }

  get steps() {
    return this._steps;
  }
  get activeStep() {
    return this._activeStep;
  }
  get imageUploadProgress() {
    return this._imageUploadProgress;
  }

  get spreadsheetUploadProgress() {
    return this._spreadsheetUploadProgress;
  }

  get uploadFinished() {
    return this._uploadFinished;
  }

  get imageCount() {
    return this._imageCount;
  }

  get uploadedImages() {
    return this._uploadedImages;
  }

  get initialUploadFileCount() {
    return this._initialUploadFileCount;
  }
  get columnsDef() {
    return this._columnsDef;
  }
  get validationRules() {
    return this._validationRules;
  }

  get validLocationIDs() {
    return this._validLocationIDs;
  }
  get validSubmitterIDs() {
    return this._validSubmitterIDs;
  }

  setSpreadsheetData(data) {
    this._spreadsheetData = data;
  }

  setImageUploadStatus(status) {
    this._imageUploadStatus = status;
  }

  setActiveStep(step) {
    this._activeStep = step;
  }
  setImageUploadProgress(progress) {
    this._imageUploadProgress = progress;
  }
  setSpreadsheetUploadProgress(progress) {
    this._spreadsheetUploadProgress = progress;
  }
  setUploadFinished(finished) {
    this._uploadFinished = finished;
  }
  setImageCount(count) {
    this._imageCount = count;
  }

  setValidLocationIDs(locationIDs) {
    this._validLocationIDs = locationIDs;
  }
  setValidSubmitterIDs(submitterIDs) {
    this._validSubmitterIDs = submitterIDs;
  }

  convertToTreeData(locationData) {
    return locationData.map((location) => ({
      title: location.name,
      value: location.id,
      geospatialInfo: location.geospatialInfo,
      children:
        location.locationID?.length > 0
          ? this.convertToTreeData(location.locationID)
          : [],
    }));
  }

  setImagePreview(preview, action = "add") {
    if (action === "remove") {
      this._imagePreview = this._imagePreview.filter(
        (p) => p.fileName !== preview,
      );
    } else if (action === "update") {
      this._imagePreview = this._imagePreview.map((p) =>
        p.fileName === preview.fileName ? { ...p, ...preview } : p,
      );
    } else if (action === "replace") {
      this._imagePreview = preview;
    } else {
      this._imagePreview = [...this._imagePreview, preview];
    }
  }

  setImageSectionFileNames(fileNames, action = "add") {
    if (action === "remove") {
      this._imageSectionFileNames = this._imageSectionFileNames.filter(
        (name) => name !== fileNames,
      );
    } else {
      this._imageSectionFileNames = Array.isArray(fileNames)
        ? fileNames
        : [...this._imageSectionFileNames, fileNames];
    }
  }

  initializeFlow(fileInputRef, maxSize) {
    const submissionId = this._submissionId || uuidv4();
    this._submissionId = submissionId;

    const flowInstance = new Flow({
      target: "/ResumableUpload",
      forceChunkSize: true,
      testChunks: false,
      allowFolderDragAndDrop: true,
      maxChunkRetries: 3,
      chunkRetryInterval: 2000,
      simultaneousUploads: 6,
      chunkSize: 5 * 1024 * 1024,
      query: { submissionId },
    });

    flowInstance.opts.generateUniqueIdentifier = (file) =>
      `${file.name}-${file.size}-${file.lastModified}-${Math.random().toString(36).slice(2)}`;

    // flowInstance.on('fileError', (file, message) => {
    //   console.error(file.name, message);
    //   flowInstance.removeFile(file);
    // });

    flowInstance.assignBrowse(fileInputRef);

    flowInstance.on("fileAdded", (file) => {
      const supportedTypes = [
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/bmp",
      ];
      if (
        !supportedTypes.includes(file.file.type) ||
        file.size > maxSize * 1024 * 1024
      ) {
        return false;
      }

      const reader = new FileReader();
      reader.onload = () => {
        const img = new Image();
        img.src = reader.result;
        img.onload = () => {
          const shouldGenerateThumbnail = true;

          let thumb = null;
          if (shouldGenerateThumbnail) {
            const canvas = document.createElement("canvas");
            const ctx = canvas.getContext("2d");
            const MAX = 150;

            let [width, height] = [img.width, img.height];

            if (width > height) {
              if (width > MAX) {
                height = height * (MAX / width);
                width = MAX;
              }
            } else {
              if (height > MAX) {
                width = width * (MAX / height);
                height = MAX;
              }
            }

            canvas.width = width;
            canvas.height = height;
            ctx.drawImage(img, 0, 0, width, height);
            thumb = canvas.toDataURL("image/jpeg", 0.7);
          }

          this._imagePreview.push({
            src: thumb,
            fileName: file.name,
            fileSize: file.size,
            progress: 0,
            showThumbnail: shouldGenerateThumbnail,
          });
        };
      };
      reader.readAsDataURL(file.file);

      this._imageSectionFileNames.push(file.name);
    });

    flowInstance.on("fileProgress", (file) => {
      const percent = (file._prevUploadedSize / file.size) * 100;

      this._imagePreview = this._imagePreview.map((f) =>
        f.fileName === file.name ? { ...f, progress: percent } : f,
      );

      const totalProgress = this._imagePreview.reduce(
        (sum, f) => sum + (f.progress || 0),
        0,
      );
      const averageProgress =
        this._imagePreview.length > 0
          ? totalProgress / this._imagePreview.length
          : 0;

      this.setImageUploadProgress(averageProgress);
    });

    flowInstance.on("fileSuccess", (file) => {
      this._imagePreview = this._imagePreview.map((f) =>
        f.fileName === file.name ? { ...f, progress: 100 } : f,
      );

      this._uploadedImages.push(file.name);
    });

    flowInstance.on("fileError", (file) => {
      this._imagePreview = this._imagePreview.map((f) =>
        f.fileName === file.name ? { ...f, error: true, progress: 0 } : f,
      );
    });

    this._flow = flowInstance;
  }

  uploadFilteredFiles(maxSize) {
    if (!this._flow) {
      console.warn("Flow instance not initialized.");
      return;
    }

    const validFiles = this._flow.files.filter(
      (file) => file.size <= maxSize * 1024 * 1024,
    );

    this._initialUploadFileCount = validFiles.length;

    if (validFiles.length === 0) {
      return;
    }
    this.setImageUploadStatus("uploading");

    const currentSubmissionId = this._submissionId || uuidv4();
    if (!this._submissionId) {
      this._submissionId = currentSubmissionId;
    }
    this._flow.opts.query.submissionId = currentSubmissionId;

    validFiles.forEach((file) => {
      this._flow.upload(file);
    });
  }

  traverseFileTree(item, maxSize, path = "") {
    if (item.isFile) {
      item.file((file) => {
        if (["image/jpeg", "image/png", "image/bmp"].includes(file.type)) {
          file.relativePath = path + file.name;
          this._flow.addFile(file);
        }
      });
    } else if (item.isDirectory) {
      const reader = item.createReader();
      reader.readEntries((entries) => {
        entries.forEach((entry) =>
          this.traverseFileTree(entry, maxSize, path + item.name + "/"),
        );
      });
    }
  }

  validateSpreadsheet() {
    const errors = {};
    this._spreadsheetData.forEach((row, rowIndex) => {
      this._columnsDef.forEach((col) => {
        const value = String(row[col] ?? "");
        const rules = this._validationRules[col];
        if (!rules) return;

        let error = "";
        if (rules.required && !value.trim()) {
          error = "This field is required";
        } else if (rules.pattern && !rules.pattern.test(value)) {
          error = rules.message || "Invalid format";
        }

        if (col === "location") {
          if (!this._validLocationIDs.includes(value)) {
            error = "Invalid location ID";
          }
        }

        if (col === "submitterID") {
          if (!this._validSubmitterIDs.includes(value)) {
            error = "Invalid submitter ID";
          }
        }
        if (error) {
          if (!errors[rowIndex]) errors[rowIndex] = {};
          errors[rowIndex][col] = error;
        }
      });
    });
    return errors;
  }

  removePreview(fileName) {
    this._imagePreview = this._imagePreview.filter(
      (f) => f.fileName !== fileName,
    );
    this._imageSectionFileNames = this._imageSectionFileNames.filter(
      (n) => n !== fileName,
    );
    const file = this._flow.files.find((f) => f.name === fileName);
    if (file) {
      this._flow.removeFile(file);
      this.setImagePreview(fileName, "remove");
      this.setImageSectionFileNames(fileName, "remove");
    }
  }

  restoreFromLocalStorage() {
    const saved = JSON.parse(localStorage.getItem("uploadedFiles"));
    if (saved) this._imagePreview = saved;
  }

  handleLoginRedirect = () => {
    // console.log("Handle login redirect - save state if needed");
  };
}

export default BulkImportStore;
