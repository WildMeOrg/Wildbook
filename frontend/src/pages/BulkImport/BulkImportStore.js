// Optimized MobX Store for Bulk Import logic
import { makeAutoObservable } from "mobx";
import EXIF from "exif-js";
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
  _imageUploadProgress = 20;
  _spreadsheetUploadProgress = 0;
  _uploadFinished = false;

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

  setSpreadsheetData(data) {
    this._spreadsheetData = data;
    console.log("Spreadsheet data saved to store:", data);
  }

  setImageUploadStatus(status) {
    this._imageUploadStatus = status;
    console.log("Image upload status updated:", status);
  }

  setActiveStep(step) {
    this._activeStep = step;
    console.log("Active step updated:", step);
  }
  setImageUploadProgress(progress) {
    this._imageUploadProgress = progress;
    console.log("Image upload progress updated:", progress);
  }
  setSpreadsheetUploadProgress(progress) {
    this._spreadsheetUploadProgress = progress;
    console.log("Spreadsheet upload progress updated:", progress);
  }
  setUploadFinished(finished) {
    this._uploadFinished = finished;
    console.log("Upload finished status updated:", finished);
  }
  setImageCount(count) {
    this._imageCount = count;
    console.log("Image count updated:", count);
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
    console.log("Image preview updated:", this._imagePreview);
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
    console.log(
      "Image section file names updated:",
      this._imageSectionFileNames,
    );
  }

  initializeFlow(fileInputRef, maxSize) {
    console.log("Initializing Flow.js for file input:", fileInputRef);
    const submissionId = this._submissionId || uuidv4();
    this._submissionId = submissionId;

    const flowInstance = new Flow({
      target: "/ResumableUpload",
      forceChunkSize: true,
      testChunks: false,
      allowFolderDragAndDrop: true,
      query: { submissionId },
    });

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
        console.log("File rejected due to type or size.");
        return false;
      }

      const reader = new FileReader();
      reader.onload = () => {
        const img = new Image();
        img.src = reader.result;
        img.onload = () => {
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
          const thumb = canvas.toDataURL("image/jpeg", 0.7);

          this._imagePreview.push({
            src: thumb,
            fileName: file.name,
            fileSize: file.size,
            progress: 0,
          });
        };
      };
      reader.readAsDataURL(file.file);

      EXIF.getData(file.file, function () {
        const dateTime = EXIF.getTag(this, "DateTime");
        if (dateTime) {
          const f = dateTime.split(/\D+/);
          const formatted =
            f.length >= 6
              ? `${f.slice(0, 3).join("-")} ${f.slice(3, 6).join(":")}`
              : f.join("-");
          console.log("EXIF DateTime:", formatted);
        }
      });

      this._imageSectionFileNames.push(file.name);
      // this._flow.upload();
    });

    flowInstance.on("fileProgress", (file) => {
      const percent = (file._prevUploadedSize / file.size) * 100;
      this._imagePreview = this._imagePreview.map((f) =>
        f.fileName === file.name ? { ...f, progress: percent } : f,
      );
    });

    flowInstance.on("fileSuccess", (file) => {
      this._imagePreview = this._imagePreview.map((f) =>
        f.fileName === file.name ? { ...f, progress: 100 } : f,
      );
    });

    flowInstance.on("fileError", (file) => {
      this._imagePreview = this._imagePreview.map((f) =>
        f.fileName === file.name ? { ...f, error: true, progress: 0 } : f,
      );
    });

    this._flow = flowInstance;
  }

  uploadFilteredFiles(maxSize) {
    console.log("Uploading filtered files with max size:", maxSize);
    if (!this._flow) {
      console.warn("Flow instance not initialized.");
      return;
    }

    console.log("Files to upload:", this._flow.files);

    const validFiles = this._flow.files.filter(
      (file) => file.size <= maxSize * 1024 * 1024,
    );
    // .filter((file) => !this._imageSectionFileNames.includes(file.name));

    if (validFiles.length === 0) {
      console.log("No valid files to upload.");
      return;
    }

    this.setImageUploadStatus("uploading");

    const currentSubmissionId = this._submissionId || uuidv4();
    if (!this._submissionId) {
      this._submissionId = currentSubmissionId;
    }
    this._flow.opts.query.submissionId = currentSubmissionId;

    validFiles.forEach((file) => {
      const timeout = setTimeout(() => {
        console.warn(`Upload timeout for file: ${file.name}`);
        this._flow.removeFile(file);
        this.setImageSectionFileNames(file.name, "remove");
        this.setImagePreview(
          { fileName: file.name, progress: 0, error: true },
          "update",
        );
      }, 300000); // 5 minutes timeout

      const clearHandlers = () => {
        clearTimeout(timeout);
        this._flow.off("fileSuccess", successHandler);
        this._flow.off("fileError", errorHandler);
      };

      const successHandler = (uploadedFile) => {
        if (uploadedFile.name === file.name) {
          clearHandlers();
          this.checkIfUploadFinished();
        }
      };

      const errorHandler = (erroredFile) => {
        if (erroredFile.name === file.name) {
          clearHandlers();
          this.setImagePreview(
            { fileName: erroredFile.name, progress: 0, error: true },
            "update",
          );
          this.setImageUploadStatus("error");
        }
      };

      this._flow.on("fileSuccess", successHandler);
      this._flow.on("fileError", errorHandler);

      this._flow.upload(file);
    });
  }

  addFiles(fileList, maxSize) {
    const supportedTypes = [
      "image/jpeg",
      "image/jpg",
      "image/png",
      "image/bmp",
    ];

    Array.from(fileList).forEach((file) => {
      if (
        !supportedTypes.includes(file.type) ||
        file.size > maxSize * 1024 * 1024
      ) {
        console.log(`Skipping unsupported or oversized file: ${file.name}`);
        return;
      }

      const reader = new FileReader();
      reader.onload = () => {
        const img = new Image();
        img.src = reader.result;
        img.onload = () => {
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
          const thumb = canvas.toDataURL("image/jpeg", 0.7);

          this._imagePreview.push({
            src: thumb,
            fileName: file.name,
            fileSize: file.size,
            progress: 0,
          });

          // Add file to flow
          this._flow.addFile(file);

          // Ensure file name is tracked
          if (!this._imageSectionFileNames.includes(file.name)) {
            this._imageSectionFileNames.push(file.name);
          }

          // Check if we should start upload
          this.uploadFilteredFiles(maxSize);
        };
      };
      reader.readAsDataURL(file);
    });
  }

  checkIfUploadFinished() {
    const unfinished = this._imagePreview.some(
      (preview) => preview.progress < 100 && !preview.error,
    );
    if (!unfinished) {
      this.setImageUploadStatus("finished");
      this.setUploadFinished(true);
    }
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

  removePreview(fileName) {
    this._imagePreview = this._imagePreview.filter(
      (f) => f.fileName !== fileName,
    );
    this._imageSectionFileNames = this._imageSectionFileNames.filter(
      (n) => n !== fileName,
    );
    const file = this._flow.files.find((f) => f.name === fileName);
    if (file) this._flow.removeFile(file);
  }

  restoreFromLocalStorage() {
    const saved = JSON.parse(localStorage.getItem("uploadedFiles"));
    if (saved) this._imagePreview = saved;
  }

  handleLoginRedirect = () => {
    console.log("Handle login redirect - save state if needed");
  };
}

export default BulkImportStore;
