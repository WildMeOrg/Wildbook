import { makeAutoObservable, runInAction, toJS } from 'mobx';
import Flow from "@flowjs/flow.js";
import { v4 as uuidv4 } from "uuid";
import dayjs from 'dayjs'
import customParseFormat from 'dayjs/plugin/customParseFormat'
import { extraStringCols, intRule, doubleRule, stringRule, specializedColumns } from "./BulkImportConstants";

dayjs.extend(customParseFormat);
export class BulkImportStore {
  _minimalFields = {};
  _STRING_COLS = [];
  _INT_COLS = [];
  _DOUBLE_COLS = [];
  _imagePreview = [];
  _imageSectionFileNames = [];
  _imageRequired = false;
  _imageSectionError = false;
  _flow = null;
  _submissionId = null;
  _spreadsheetData = [];
  _spreadsheetFileName = "";
  _rawData = [];
  _activeStep = 0;
  _imageUploadProgress = 0;
  _failedImages = [];
  _spreadsheetUploadProgress = 0;
  _uploadedImages = [];
  _columnsDef = [];
  _rawColumns = [];
  _maxImageCount = 200;
  _missingImages = [];
  _locationID = "";
  _worksheetInfo = {
    fileName: "",
    sheetCount: 0,
    sheetNames: "",
    columnCount: 0,
    rowCount: 0,
    uploadProgress: this._spreadsheetUploadProgress,
  }
  _submissionErrors = {};
  _showInstructions = false;
  _isSavingDraft = false;
  _lastSavedAt = null;
  _errorSummary = {};
  _cachedValidation = null;

  isValidISO(val) {
    const dt = new Date(val);
    return !isNaN(dt.getTime());
  }

  _validLocationIDs = [];
  _validSubmitterIDs = [];
  _validGenus = [];
  _validspecies = [];
  _validCountryIDs = [];
  _validLivingStatus = [];
  _validLifeStages = [];
  _validSex = [];
  _validBehavior = [];

  _validationRules = {
    "Encounter.mediaAsset0": {
      required: false,
      validate: (val) => {
        if (!val) {
          return true;
        }
        const images = val.split(",").map((img) => img.trim());
        let missing = false;
        images.forEach((img) => {
          if (!this._imageSectionFileNames.includes(img)) {
            missing = true;
          }
        });

        return !missing;
      },
      message: (val) => {
        const images = val.split(",").map((img) => img.trim());
        const missing = images.filter((img) =>
          !this._imageSectionFileNames.includes(img)
        );
        return `missing images: ${missing.join(", ")}`;
      },
    },
    "Encounter.year": {
      required: true,
      validate: (val) => {
        const FORMATS = [
          'YYYY',
          'YYYY-MM',
          'YYYY-MM-DD',
          'YYYY-MM-DDTHH:mm',
        ];
        return dayjs(val, FORMATS, true).isValid();
      },
      message:
        "Date must be “YYYY”、“YYYY-MM”、“YYYY-MM-DD” or “YYYY-MM-DDThh:mm”",
    },
    "Encounter.genus": {
      required: true,
      validate: (val) => {
        return this._validspecies.includes(val);
      },
      message: "must enter a valid species",
    },
    "Encounter.decimalLatitude": {
      required: false,
      validate: (val) => {
        if (!val) {
          return true;
        }

        const re = /^\s*([-+]?\d+(\.\d+)?)\s*,\s*([-+]?\d+(\.\d+)?)\s*$/;
        const m = re.exec(val);
        if (!m) {
          return false;
        }

        const lat = parseFloat(m[1]);
        const lon = parseFloat(m[3]);

        if (Number.isNaN(lat) || Number.isNaN(lon)) {
          return false;
        }
        if (lat < -90 || lat > 90) {
          return false;
        }
        if (lon < -180 || lon > 180) {
          return false;
        }

        return true;
      },
      message: "Must enter a valid latitude and longitude",
    },
    "Encounter.locationID": {
      required: true,
      validate: (value) => {
        if (!value) {
          return true;
        }
        return this._validLocationIDs.includes(value);
      },
      message: "invalid location ID",
    },
    "Encounter.submitterID": {
      required: false,
      validate: (value) => {
        if (!value) {
          return true;
        }
        return this._validSubmitterIDs.includes(value);
      },
      message: "invalid submitter ID",
    },
    "Encounter.country": {
      required: false,
      validate: (val) => {
        if (!val) {
          return true;
        }
        return this._validCountryIDs.includes(val);
      },
      message: "invalid country ID",
    },
    "Encounter.livingStatus": {
      required: false,
      validate: (val) => {
        if (!val) {
          return true;
        }
        return this._validLivingStatus.includes(val);
      },
      message: "invalid living status",
    },
    "Encounter.lifeStage": {
      required: false,
      validate: (val) => {
        if (!val) {
          return true;
        }
        return this._validLifeStages.includes(val);
      },
      message: "invalid life stage",
    },
    "Encounter.sex": {
      required: false,
      validate: (val) => {
        if (!val) return true;
        return this._validSex.includes(val);
      },
      message: "invalid sex",
    },
    "Encounter.behavior": {
      required: false,
      validate: (val) => {
        if (!val) {
          return true;
        }
        return this._validBehavior.includes(val);
      },
      message: "invalid behavior",
    },
    "Encounter.photographer0.emailAddress": {
      required: false,
      validate: (val) => {
        if (!val) {
          return true;
        }
        const re = /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$/;
        return re.test(val);
      },
      message: "invalid email address",
    },
    "Encounter.informOther0.emailAddress": {
      required: false,
      validate: (val) => {
        if (!val) {
          return true;
        }
        const re = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
        return re.test(val);
      },
      message: "invalid email address",
    },
    "Encounter.dateInMilliseconds": {
      required: false,
      validate: (val) => {
        if (!val) {
          return true;
        }
        const dt = new Date(val);
        return !isNaN(dt.getTime());
      },
      message: "must be a valid date in milliseconds",
    }
  };

  constructor() {
    makeAutoObservable(this);

    this._validationRules = {
      ...this._validationRules,
      ...Object.fromEntries(
        this._INT_COLS.map((col) => [col, intRule])
      ),
      ...Object.fromEntries(
        this._DOUBLE_COLS.map((col) => [col, doubleRule])
      ),
      ...Object.fromEntries(
        this._STRING_COLS.map((col) => [col, { stringRule }])
      ),
    };
  }

  get stateSnapshot() {

    return {
      submissionId: this._submissionId,

      rawData: toJS(this._rawData),
      rawColumns: toJS(this._rawColumns),
      columnsDef: toJS(this._columnsDef),

      imagePreview: toJS(this._imagePreview),
      imageSectionFileNames: toJS(this._imageSectionFileNames),
      imageUploadProgress: this._imageUploadProgress,
      uploadedImages: toJS(this._uploadedImages),

      spreadsheetData: toJS(this._spreadsheetData),
      spreadsheetUploadProgress: this._spreadsheetUploadProgress,
      spreadsheetFileName: this._spreadsheetFileName,

      worksheetInfo: toJS(this._worksheetInfo),
      lastSavedAt: this._lastSavedAt,
    };
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

  get rawData() {
    return this._rawData;
  }

  get activeStep() {
    return this._activeStep;
  }

  get imageUploadProgress() {
    const previews = this._imagePreview;
    if (previews.length === 0) return 0;
    const total = previews.reduce((sum, f) => sum + (f.progress || 0), 0);
    return total / previews.length;
  }

  get spreadsheetUploadProgress() {
    return this._spreadsheetUploadProgress;
  }

  get uploadedImages() {
    return this._uploadedImages;
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

  get rawColumns() {
    return this._rawColumns;
  }

  get submissionId() {
    return this._submissionId;
  }

  get maxImageCount() {
    return this._maxImageCount;
  }

  get worksheetInfo() {
    return this._worksheetInfo;
  }

  get submissionErrors() {
    return this._submissionErrors;
  }

  get failedImages() {
    return this._failedImages;
  }

  get showInstructions() {
    return this._showInstructions;
  }

  get isSavingDraft() {
    return this._isSavingDraft;
  }
  get lastSavedAt() {
    return this._lastSavedAt;
  }

  get errorSummary() {
    console.log("errrrrrrrrrrrrrrrrrrrrrrrrrr")
    let error = 0, missingField = 0, emptyField = 0, imgVerifyPending = 0;
    // const { errors } = this.validateSpreadsheet();
const { errors = {} } = this.validateSpreadsheet() || {};

    console.log("Spreadsheet validation errors+++++++++++++++", errors);
    const uploadingSet = new Set(
      this._imagePreview
        .filter(p => p.progress > 0 && p.progress < 100)
        .map(p => p.fileName)
    );

    this._spreadsheetData.forEach((row, rowIdx) => {
      let rowHasPendingUpload = false;
      this._columnsDef.forEach(col => {
        const rules = this._validationRules[col] ?? {};
        const value = String(row[col] ?? "").trim();
        // const errMsg = this._submissionErrors[rowIdx]?.[col]
        //   ?? this.validateSpreadsheet()[rowIdx]?.[col];
        const errMsg = this._submissionErrors[rowIdx]?.[col] ?? errors[rowIdx]?.[col];

        if (errMsg) {
          error += 1;
          if (/required/i.test(errMsg)) missingField += 1;
          return;
        }

        if (!value) {
          if (rules.required) missingField += 1;
          else emptyField += 1;
        }

        if (col.startsWith("Encounter.mediaAsset") && value) {
          const imgs = value.split(/\s*,\s*/);
          if (imgs.some(img => uploadingSet.has(img))) rowHasPendingUpload = true;
        }
      });

      if (rowHasPendingUpload) imgVerifyPending += 1;
    });

    return { error, missingField, emptyField, imgVerifyPending };
  }

  get spreadsheetFileName() {
    return this._spreadsheetFileName;
  }

  get locationID() {
    return this._locationID;
  }

  setSpreadsheetData(data) {
    this._spreadsheetData = [...data];
    this.invalidateValidation();
    this.updateRawFromNormalizedRow(); 
  }

  setRawData(data) {
    this._rawData = data;
  }

  setActiveStep(step) {
    this._activeStep = step;
  }

  setSpreadsheetUploadProgress(progress) {
    this._spreadsheetUploadProgress = progress;
  }

  setValidLocationIDs(locationIDs) {
    this._validLocationIDs = locationIDs;
  }
  setValidSubmitterIDs(submitterIDs) {
    this._validSubmitterIDs = submitterIDs;
  }

  setValidSpecies(species) {
    this._validspecies = species;
  }

  setValidCountryIDs(countryIDs) {
    this._validCountryIDs = countryIDs;
  }

  setValidLivingStatus(livingStatus) {
    this._validLivingStatus = livingStatus;
  }

  setValidLifeStages(lifeStages) {
    this._validLifeStages = lifeStages;
  }

  setValidSex(sex) {
    this._validSex = sex;
  }

  setValidBehavior(behavior) {
    this._validBehavior = behavior;
  }

  setColumnsDef(columns) {
    this._columnsDef = columns;
  }

  setRawColumns(columns) {
    this._rawColumns = columns;
  }

  setMaxImageCount(maxImageCount) {
    this._maxImageCount = maxImageCount;
  }

  setWorksheetInfo(sheetCount, sheetNames, columnCount, rowCount, fileName) {
    this._worksheetInfo.sheetCount = sheetCount;
    this._worksheetInfo.sheetNames = sheetNames;
    this._worksheetInfo.columnCount = columnCount;
    this._worksheetInfo.rowCount = rowCount;
    this._worksheetInfo.uploadProgress = this._spreadsheetUploadProgress;
    this._worksheetInfo.fileName = fileName || "";
  }

  setLocationID(locationID) {
    this._locationID = locationID;
  }

  setSubmissionErrors(errors) {
    this._submissionErrors = errors;
  }

  setFailedImages(image) {
    this._failedImages.push(image);
  }

  setShowInstructions(show) {
    this._showInstructions = show;
  }

  setMinimalFields(minimalFields) {
    runInAction(() => {
      this._minimalFields = minimalFields;

      this._STRING_COLS = Object.keys(minimalFields)
        .filter(k => minimalFields[k] === "string")
        .concat(extraStringCols);
      this._INT_COLS = Object.keys(minimalFields)
        .filter(k => minimalFields[k] === "int");
      this._DOUBLE_COLS = Object.keys(minimalFields)
        .filter(k => minimalFields[k] === "double");

      this._validationRules = {
        ...this._validationRules,
        ...Object.fromEntries(this._INT_COLS.map(c => [c, intRule])),
        ...Object.fromEntries(this._DOUBLE_COLS.map(c => [c, doubleRule])),
        ...Object.fromEntries(this._STRING_COLS.map(c => [c, stringRule])),
      };
    });
  }

  setSpreadsheetFileName(fileName) {
    this._spreadsheetFileName = fileName;
  }

  hydrate(state) {
    runInAction(() => {
      Object.entries(state).forEach(([key, value]) => {
        const field = `_${key}`;
        if (field in this) this[field] = value;
      });
    });
  }

  clearSubmissionErrors() {
    this._submissionErrors = {};
  }

  resetToDefaults() {
    runInAction(() => {
      this._submissionErrors = {};
      this._submissionId = null;
      this._imagePreview = [];
      this._imageSectionFileNames = [];
      this._imageUploadProgress = 0;
      this._spreadsheetUploadProgress = 0;
      this._activeStep = 0;
      this._uploadedImages = [];
      this._rawData = [];
      this._spreadsheetData = [];
      this._columnsDef = [];
      this._rawColumns = [];
      this._worksheetInfo = {
        sheetCount: 0,
        sheetNames: '',
        columnCount: 0,
        rowCount: 0,
        uploadProgress: 0,
      };
    });

    window.localStorage.removeItem('BulkImportStore');
  }

  saveState() {
    try {
      runInAction(() => {
        this._isSavingDraft = true;
        this._lastSavedAt = Date.now();
      });

      const json = JSON.stringify(this.stateSnapshot);
      window.localStorage.setItem('BulkImportStore', json);
    } catch (e) {
      console.error('saving as draft failed', e);
    } finally {
      setTimeout(
        () => runInAction(() => { this._isSavingDraft = false; }),
        700
      );
    }
  }

  applyServerUploadStatus(uploaded = []) {
    const uploadedFileNames = uploaded.map(p => p[0]);
    runInAction(() => {
      this._uploadedImages = uploaded;
      this._imagePreview = this._imagePreview.map(p => ({
        ...p,
        progress: uploadedFileNames.includes(p.fileName) ? 100 : 0
      }));
      this._imagePreview.sort((a, b) => {
        if (a.progress === 0 && b.progress !== 0) return -1;
        if (a.progress !== 0 && b.progress === 0) return 1;
        return 0;
      });
    });
  }

  async fetchAndApplyUploaded() {
    if (!this._submissionId) return;
    const resp = await fetch(
      `/api/v3/bulk-import/${this._submissionId}/files`
    );

    if (!resp.ok) {
      console.error("Unexpected response", resp.status);
      return;
    }

    const data = await resp.json();
    this.applyServerUploadStatus(data.files);
  }

  getOptionsForSelectCell(col) {
    if (col === "Encounter.locationID") {
      return this._validLocationIDs.map((id) => ({
        value: id,
        label: id,
      }));
    } else if (col === "Encounter.genus") {
      return this._validspecies.map((species) => ({
        value: species,
        label: species,
      }));
    } else if (col === "Encounter.country") {
      return this._validCountryIDs.map((id) => ({
        value: id,
        label: id,
      }));
    } else if (col === "Encounter.livingStatus") {
      return this._validLivingStatus.map((status) => ({
        value: status,
        label: status,
      }));
    }
    else if (col === "Encounter.lifeStage") {
      return this._validLifeStages.map((stage) => ({
        value: stage,
        label: stage,
      }));
    } else if (col === "Encounter.sex") {
      return this._validSex.map((data) => (
        { value: data, label: data }
      ))
    } else if (col === "Encounter.behavior") {
      return this._validBehavior.map((data) => (
        {
          value: data,
          label: data
        }
      ))
    }


    return [];
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

  setSubmissionId(submissionId) {
    this._submissionId = submissionId;
  }

  initializeFlow(fileInputRef, maxSize) {
    const submissionId = this._submissionId || uuidv4();
    this._submissionId = submissionId;
    const flowInstance = new Flow({
      target: "/ResumableUpload",
      forceChunkSize: true,
      testChunks: false,
      allowFolderDragAndDrop: true,
      maxChunkRetries: 10,
      chunkRetryInterval: 2000,
      simultaneousUploads: 6,
      chunkSize: 5 * 1024 * 1024,
      query: { submissionId: this._submissionId },
    });

    flowInstance.opts.generateUniqueIdentifier = (file) =>
      `${file.name}-${file.size}-${file.lastModified}-${Math.random().toString(36).slice(2)}`;

    flowInstance.assignBrowse(fileInputRef);

    flowInstance.on("fileAdded", (file) => {
      if (this._imageSectionFileNames.length >= this._maxImageCount) {
        console.warn(`maximum ${this._maxImageCount} discard`, file.name);
        return false;
      }
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

    flowInstance.opts.createXhr = () => {
      const xhr = new XMLHttpRequest();
      xhr.timeout = 30_000;
      xhr.ontimeout = () => {
        console.warn('XHR timeout, retrying...');
        this._flow.retry();
      };
      return xhr;
    };

    flowInstance.on('fileRetry', (file, chunk) => {
      console.log(
        `file ${file.name} chunk #${chunk.offset} is retrying # ${chunk.retries} `
      );
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
    });

    flowInstance.on("fileSuccess", (file) => {
      runInAction(() => {
        this._imagePreview = this._imagePreview.map(f =>
          f.fileName === file.name ? { ...f, progress: 100 } : f
        );
        this._uploadedImages.push(file.name);
      });
    });

    window.addEventListener('offline', () => {
      console.warn('no internet connection, pausing upload');
      flowInstance.pause();
    });

    window.addEventListener('online', () => {
      console.info('internet connection restored, resuming upload');
      flowInstance.resume();
      // flowInstance.upload()
    });

    flowInstance.on("fileError", (file, chunk) => {
      // if (!navigator.onLine) {
      //   console.log(`Chunk uploading failed due to no internet connection`, file.name, chunk.offset);
      //   return;
      // }
      this._imagePreview = this._imagePreview.map((f) =>
        f.fileName === file.name ? { ...f, error: true, progress: 0 } : f,
      );

      this.setFailedImages(file.name);
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

    if (validFiles.length === 0) {
      return;
    }

    const currentSubmissionId = this._submissionId || uuidv4();
    if (!this._submissionId) {
      this._submissionId = currentSubmissionId;
    }
    this._flow.opts.query.submissionId = currentSubmissionId;

    validFiles.forEach((file) => {
      this._flow.upload(file);
    });
  }

  updateRawFromNormalizedRow() {

    this._spreadsheetData.forEach((_, rowIndex) => {

      const norm = this._spreadsheetData[rowIndex];
      const raw = this._rawData[rowIndex];
      runInAction(() => {
        if (norm["Encounter.year"]) {
          const val = norm["Encounter.year"];
          if (val) {
            if (/^\d{4}$/.test(val)) {
              const y = Number(val);
              raw["Encounter.year"] = y;
              raw["Encounter.month"] = "";
              raw["Encounter.day"] = "";
              raw["Encounter.hour"] = "";
              raw["Encounter.minutes"] = "";
            }
            else if (/^\d{4}-\d{2}$/.test(val)) {
              const [y, m] = val.split("-").map(Number);
              raw["Encounter.year"] = y;
              raw["Encounter.month"] = m;
              raw["Encounter.day"] = "";
              raw["Encounter.hour"] = "";
              raw["Encounter.minutes"] = "";
            }
            else if (/^\d{4}-\d{2}-\d{2}$/.test(val)) {
              const [y, m, d] = val.split("-").map(Number);
              raw["Encounter.year"] = y;
              raw["Encounter.month"] = m;
              raw["Encounter.day"] = d;
              raw["Encounter.hour"] = "";
              raw["Encounter.minutes"] = "";
            }
            else {
              const dt = new Date(val);
              raw["Encounter.year"] = dt.getFullYear();
              raw["Encounter.month"] = dt.getMonth() + 1;
              raw["Encounter.day"] = dt.getDate();
              raw["Encounter.hour"] = dt.getHours();
              raw["Encounter.minutes"] = dt.getMinutes();
            }
          }
        }

        if (norm["Encounter.genus"] != null) {
          const [g, s = ""] = norm["Encounter.genus"].split(" ");
          raw["Encounter.genus"] = g;
          raw["Encounter.specificEpithet"] = s;
        }

        if (norm["Encounter.mediaAsset0"] != null) {
          Object.keys(raw).forEach((key) => {
            if (key.startsWith("Encounter.mediaAsset")) {
              delete raw[key];
            }
          });
          const assets = norm["Encounter.mediaAsset0"]
            .split(/\s*,\s*/)
            .filter((v) => v !== "");
          assets.forEach((val, idx) => {
            raw[`Encounter.mediaAsset${idx}`] = val;
          });
        }

        if (norm["Encounter.decimalLatitude"] != null) {
          const m = /^\s*([^,]+)\s*,\s*([^,]+)\s*$/.exec(
            norm["Encounter.decimalLatitude"]
          );
          raw["Encounter.decimalLatitude"] = m ? m[1] : "";
          raw["Encounter.decimalLongitude"] = m ? m[2] : "";
        }

      });
    }

    );


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
    console.log("Validating spreadsheet 11111", JSON.stringify(this._cachedValidation));
    if (this._cachedValidation) {
      return this._cachedValidation;
    }
    const errors = {};
    const warnings = {};

    this._spreadsheetData.forEach((row, rowIndex) => {

      this._columnsDef.forEach((col) => {

        const value = String(row[col] ?? "");
        const rules = this._validationRules[col];
        const isKnown =
          col in this._minimalFields ||
          extraStringCols.includes(col) ||
          specializedColumns.includes(col);

        if (!isKnown) {
          if (!warnings[rowIndex]) warnings[rowIndex] = {};
          warnings[rowIndex][col] = "Unknown column — may not be processed";
          return;
        }

        if (!rules) return;

        let error = "";
        if (rules.required && !value.trim()) {
          error = "This field is required";
        } else if (rules.validate && !rules.validate(value)) {
          error =
            typeof rules.message === "function"
              ? rules.message(value)
              : rules.message || "Invalid format";
        }

        if (error) {
          if (!errors[rowIndex]) errors[rowIndex] = {};
          errors[rowIndex][col] = error;
        }
      });
    });
    this._cachedValidation = { errors, warnings };
    console.log("Spreadsheet validation 22222", JSON.stringify(this._cachedValidation));
    return this._cachedValidation;
  }

  invalidateValidation() {
    this._cachedValidation = null;
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
      runInAction(() => {
        this._uploadedImages = this._uploadedImages.filter(
          (n) => n !== fileName,
        );
      });

    }
  }

}

export default BulkImportStore;

