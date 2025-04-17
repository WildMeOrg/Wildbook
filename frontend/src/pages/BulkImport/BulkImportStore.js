
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
  _flow = null;
  _submissionId = null;
  _spreadsheetData = [];
  _imageUploadStatus= "notStarted"; 
  _steps = ["Upload Image", "Upload Spreadsheet", "Review"]
  _activeStep = 0;
  _imageUploadProgress = 20;
  _spreadsheetUploadProgress = 80;

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

  initializeFlow(fileInputRef, maxSize) {
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
      const supportedTypes = ["image/jpeg", "image/jpg", "image/png", "image/bmp"];
      if (!supportedTypes.includes(file.file.type) || file.size > maxSize * 1024 * 1024) return false;

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
          const formatted = f.length >= 6 ? `${f.slice(0, 3).join("-")} ${f.slice(3, 6).join(":")}` : f.join("-");
          console.log("EXIF DateTime:", formatted);
        }
      });

      this._imageSectionFileNames.push(file.name);
      this._flow.upload();
    });

    flowInstance.on("fileProgress", (file) => {
      const percent = (file._prevUploadedSize / file.size) * 100;
      this._imagePreview = this._imagePreview.map((f) =>
        f.fileName === file.name ? { ...f, progress: percent } : f
      );
    });

    flowInstance.on("fileSuccess", (file) => {
      this._imagePreview = this._imagePreview.map((f) =>
        f.fileName === file.name ? { ...f, progress: 100 } : f
      );
    });

    flowInstance.on("fileError", (file) => {
      this._imagePreview = this._imagePreview.map((f) =>
        f.fileName === file.name ? { ...f, error: true, progress: 0 } : f
      );
    });

    this._flow = flowInstance;
  }

  handleDragEnter = (e) => {
    e.preventDefault();
    e.currentTarget.style.border = "2px dashed #007BFF";
  };

  handleDragOver = (e) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = "copy";
  };

  handleDragLeave = (e) => {
    e.preventDefault();
    e.currentTarget.style.border = "1px dashed #007BFF";
  };

  async handleDrop(e, maxSize) {
    e.preventDefault();
    e.currentTarget.style.border = "1px dashed #007BFF";

    const items = e.dataTransfer.items;
    for (let i = 0; i < items.length; i++) {
      const item = items[i].webkitGetAsEntry?.();
      if (item) this.traverseFileTree(item, maxSize);
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
          this.traverseFileTree(entry, maxSize, path + item.name + "/")
        );
      });
    }
  }

  removePreview(fileName) {
    this._imagePreview = this._imagePreview.filter((f) => f.fileName !== fileName);
    this._imageSectionFileNames = this._imageSectionFileNames.filter((n) => n !== fileName);
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

