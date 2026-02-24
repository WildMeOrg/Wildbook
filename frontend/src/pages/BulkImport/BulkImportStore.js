import { makeAutoObservable, runInAction, toJS } from "mobx";
import Flow from "@flowjs/flow.js";
import { v4 as uuidv4 } from "uuid";
import dayjs from "dayjs";
import customParseFormat from "dayjs/plugin/customParseFormat";
import {
  allRequiredColumns,
  extraStringCols,
  intRule,
  doubleRule,
  stringRule,
  allColumns,
  latlongRule,
  parseEncounterDateString,
} from "./BulkImportConstants";
import { defaultMaxMediaSize } from "../../constants/photoUpload.js";
import { toast } from "react-toastify";

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
  _imageCountGenerateThumbnail = 50;
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
  _maxImageSizeMB = defaultMaxMediaSize;
  _rejectedTooLarge = [];
  _rejectedToastTimer = null;
  _pageSize = 10;
  _locationID = [];
  _locationIDOptions = [];
  _worksheetInfo = {
    fileName: "",
    sheetCount: 0,
    sheetNames: "",
    columnCount: 0,
    rowCount: 0,
    uploadProgress: this._spreadsheetUploadProgress,
  };
  _submissionErrors = {};
  _showInstructions = false;
  _isSavingDraft = false;
  _lastSavedAt = null;
  _errorSummary = {};
  _cachedValidation = null;
  _filesParsed = false;
  _filesParsingCount = 0;
  _pendingReadCount = 0;
  _imageFileMap = new Map();
  _pendingDropFileCount = 0;
  _hasWarnedDropLimit = false;
  _collectedValidFiles = [];
  _MAX_DROP_FILE_COUNT = 1000;
  _skipDetection = false;
  _skipIdentification = false;

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
  _validState = [];
  _synonymFields = [];
  _labeledKeywordAllowedKeys = [];
  _labeledKeywordAllowedPairs = [];
  _applyToAllRowModalShow = false;
  _validationErrors = {};
  _validationWarnings = {};
  _emptyFieldCount = 0;

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
          if (!this._uploadedImages.includes(img)) {
            missing = true;
          }
        });
        return !missing;
      },
      message: (val) => {
        const images = val.split(",").map((img) => img.trim());
        const missing = images.filter(
          (img) => !this._uploadedImages.includes(img),
        );
        return `MISSING${missing.join(", ")}`;
      },
    },
    "Encounter.year": {
      required: true,
      validate: (val) => {
        if (val == null) return false;
        const FLEXIBLE_DATE_RE =
          /^(\d{4})(?:-(\d{1,2})(?:-(\d{1,2})(?:T(\d{1,2})(?::(\d{1,2}))?)?)?)?$/;

        const str = String(val).trim();
        if (!str) return false;

        const m = str.match(FLEXIBLE_DATE_RE);
        if (!m) {
          return false;
        }

        const [, year, month, day, hour, minute] = m;

        let normalized = year;
        let format = "YYYY";

        if (month !== undefined) {
          normalized += "-" + month.padStart(2, "0");
          format += "-MM";
        }

        if (day !== undefined) {
          normalized += "-" + day.padStart(2, "0");
          format += "-DD";
        }

        if (hour !== undefined) {
          normalized += "T" + hour.padStart(2, "0");
          format += "[T]HH";
        }

        if (minute !== undefined) {
          normalized += ":" + minute.padStart(2, "0");
          format += ":mm";
        }
        const parsed = dayjs(normalized, format, true);
        return parsed.isValid() && !parsed.isAfter(dayjs());
      },
      message: "BULKIMPORT_ERROR_INVALID_DATEFORMAT",
    },

    "Sighting.year": {
      required: false,
      validate: (val) => {
        if (!val) {
          return true;
        }
        const FORMATS = ["YYYY", "YYYY-MM", "YYYY-MM-DD", "YYYY-MM-DDTHH:mm"];
        const parsed = dayjs(val, FORMATS, true);
        return parsed.isValid() && !parsed.isAfter(dayjs());
      },
      message: "BULKIMPORT_ERROR_INVALID_DATEFORMAT",
    },
    "Encounter.genus": {
      required: true,
      validate: (val) => {
        return this._validspecies.includes(val);
      },
      message: "BULKIMPORT_ERROR_INVALID_INVALIDSPECIES",
    },
    "Encounter.decimalLatitude": latlongRule,
    "Encounter.latitude": latlongRule,
    "Sighting.decimalLatitude": latlongRule,

    "Sighting.dateInMilliseconds": {
      required: false,
      validate: (val) => {
        if (!val) {
          return true;
        }
        const num = Number(val);
        if (isNaN(num)) return false;
        const dt = new Date(num);
        return !isNaN(dt.getTime());
      },
      message: "BULKIMPORT_ERROR_INVALID_INVALIDDATEMILLISECONDS",
    },
    "Encounter.locationID": {
      required: true,
      validate: (value) => {
        if (!value) {
          return true;
        }
        return this._validLocationIDs.includes(value);
      },
      message: "BULKIMPORT_ERROR_INVALID_INVALIDLOCATIONID",
    },
    "Encounter.submitterID": {
      required: false,
      validate: (value) => {
        if (!value) {
          return true;
        }
        return this._validSubmitterIDs.includes(value);
      },
      message: "BULKIMPORT_ERROR_INVALID_INVALIDSUBMITTERID",
    },
    "Encounter.country": {
      required: false,
      validate: (val) => {
        if (!val) {
          return true;
        }
        return this._validCountryIDs.includes(val);
      },
      message: "BULKIMPORT_ERROR_INVALID_INVALIDCOUNTRYID",
    },
    "Encounter.state": {
      required: false,
      validate: (val) => {
        if (!val) {
          return true;
        }
        return this._validState.includes(val);
      },
      message: "BULKIMPORT_ERROR_INVALID_INVALIDSTATE",
    },
    "Encounter.livingStatus": {
      required: false,
      validate: (val) => {
        if (!val) {
          return true;
        }
        return this._validLivingStatus.includes(val);
      },
      message: "BULKIMPORT_ERROR_INVALID_INVALIDLIVINGSTATUS",
    },
    "Encounter.lifeStage": {
      required: false,
      validate: (val) => {
        if (!val) {
          return true;
        }
        return this._validLifeStages.includes(val);
      },
      message: "BULKIMPORT_ERROR_INVALID_INVALIDLIFESTAGE",
    },
    "Encounter.sex": {
      required: false,
      validate: (val) => {
        if (!val) return true;
        return this._validSex.includes(val);
      },
      message: "BULKIMPORT_ERROR_INVALID_INVALIDSEX",
    },
    "Encounter.photographer0.emailAddress": {
      required: false,
      validate: (val) => {
        if (!val) {
          return true;
        }
        const re =
          /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$/;
        return re.test(val);
      },
      message: "BULKIMPORT_ERROR_INVALID_INVALIDEAMILADDRESS",
    },
    "Encounter.informOther0.emailAddress": {
      required: false,
      validate: (val) => {
        if (!val) {
          return true;
        }
        const re =
          /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$/;
        return re.test(val);
      },
      message: "BULKIMPORT_ERROR_INVALID_INVALIDEAMILADDRESS",
    },
    "Encounter.dateInMilliseconds": {
      required: false,
      validate: (val) => {
        if (!val) {
          return true;
        }
        const num = Number(val);
        if (isNaN(num)) return false;
        const dt = new Date(num);
        return !isNaN(dt.getTime());
      },
      message: "BULKIMPORT_ERROR_INVALID_INVALIDDATEMILLISECONDS",
    },

    "Sighting.millis": {
      required: false,
      validate: (val) => {
        if (!val) {
          return true;
        }
        const num = Number(val);
        if (isNaN(num)) return false;
        const dt = new Date(num);
        return !isNaN(dt.getTime());
      },
      message: "BULKIMPORT_ERROR_INVALID_INVALIDDATEMILLISECONDS",
    },
  };

  constructor() {
    makeAutoObservable(this);

    this._validationRules = {
      ...this._validationRules,
      ...Object.fromEntries(this._INT_COLS.map((col) => [col, intRule])),
      ...Object.fromEntries(this._DOUBLE_COLS.map((col) => [col, doubleRule])),
      ...Object.fromEntries(
        this._STRING_COLS.map((col) => [col, { stringRule }]),
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
      imageUploadProgress: "100%",
      uploadedImages: toJS(this._uploadedImages),

      spreadsheetData: toJS(this._spreadsheetData),
      spreadsheetUploadProgress: this._spreadsheetUploadProgress,
      spreadsheetFileName: this._spreadsheetFileName,

      worksheetInfo: toJS(this._worksheetInfo),
      lastSavedAt: this._lastSavedAt,
      validationErrors: toJS(this._validationErrors),
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

  get maxImageSizeMB() {
    return this._maxImageSizeMB;
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

  get applyToAllRowModalShow() {
    return this._applyToAllRowModalShow;
  }

  get validationErrors() {
    return this._validationErrors;
  }

  get validationWarnings() {
    return this._validationWarnings;
  }

  get missingRequiredColumns() {
    return allRequiredColumns.filter((col) => !this._columnsDef.includes(col));
  }

  get synonymFields() {
    return this._synonymFields;
  }

  get errorPages() {
    const pageSet = new Set();

    Object.entries(this.validationErrors).forEach(([rowIndexStr, errorMap]) => {
      if (errorMap && Object.keys(errorMap).length > 0) {
        const rowIndex = Number(rowIndexStr);
        const pageIndex = Math.floor(rowIndex / this._pageSize);
        pageSet.add(pageIndex);
      }
    });

    return pageSet;
  }

  get missingPhotos() {
    const missing = new Set();

    this._spreadsheetData.forEach((row) => {
      const mediaAssets = row["Encounter.mediaAsset0"];
      if (!mediaAssets) return;
      mediaAssets.split(",").forEach((name) => {
        if (!this._uploadedImages.includes(name)) {
          missing.add(name);
        }
      });
    });
    return Array.from(missing);
  }

  get emptyFieldCount() {
    let count = 0;
    this._spreadsheetData.forEach((row) => {
      Object.values(row).forEach((cell) => {
        if (String(cell ?? "").trim() === "") {
          count++;
        }
      });
    });
    return count;
  }

  get errorSummary() {
    let error = 0,
      missingField = 0,
      emptyField = 0,
      imgVerifyPending = 0;
    const { errors = {} } = this.validateSpreadsheet() || {};

    const uploadingSet = new Set(
      this._imagePreview
        .filter((p) => p.progress > 0 && p.progress < 100)
        .map((p) => p.fileName),
    );

    this._spreadsheetData.forEach((row, rowIdx) => {
      let rowHasPendingUpload = false;
      this._columnsDef.forEach((col) => {
        const rules = this._validationRules[col] ?? {};
        const value = String(row[col] ?? "").trim();
        const errMsg =
          this._submissionErrors[rowIdx]?.[col] ?? errors[rowIdx]?.[col];

        if (errMsg) {
          error += 1;
          if (/required/i.test(errMsg)) missingField += 1;
          return;
        }

        if (!value) {
          if (rules.required) missingField += 1;
          else emptyField += 1;
        }

        if (
          col.startsWith("Encounter.mediaAsset") &&
          value &&
          col.split(".").length === 2
        ) {
          const imgs = value.split(/\s*,\s*/);
          if (imgs.some((img) => uploadingSet.has(img)))
            rowHasPendingUpload = true;
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

  get filesParsed() {
    return this._filesParsed;
  }

  get skipDetection() {
    return this._skipDetection;
  }

  get skipIdentification() {
    return this._skipIdentification;
  }

  get locationIDOptions() {
    return this._locationIDOptions;
  }

  setLabeledKeywordAllowedKeys(keys) {
    this._labeledKeywordAllowedKeys = keys;
  }

  setLabeledKeywordAllowedPairs(values) {
    this._labeledKeywordAllowedPairs = values;
  }

  setSpreadsheetData(data) {
    const filtered = data.filter((row) =>
      Object.values(row).some((val) => String(val ?? "").trim() !== ""),
    );
    this._spreadsheetData = filtered;
    this.invalidateValidation();
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

  setValidStates(states) {
    this._validState = states;
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

  setMaxImageSizeMB(mb) {
    this._maxImageSizeMB = Number(mb) || defaultMaxMediaSize;
  }

  setLocationIDOptions(options) {
    this._locationIDOptions = options;
  }

  setSynonymFields(synonymFields) {
    this._synonymFields = synonymFields;
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

  setFilesParsed(filesParsed) {
    this._filesParsed = filesParsed;
  }

  setApplyToAllRowModalShow(show) {
    this._applyToAllRowModalShow = show;
  }

  setValidationErrors(errors) {
    this._validationErrors = errors;
  }

  setValidationWarnings(warnings) {
    this._validationWarnings = warnings;
  }

  setSkipDetection(skip) {
    this._skipDetection = skip;
  }

  setSkipIdentification(skip) {
    this._skipIdentification = skip;
  }

  setMinimalFields(minimalFields) {
    runInAction(() => {
      this._minimalFields = minimalFields;

      this._STRING_COLS = Object.keys(minimalFields)
        .filter((k) => minimalFields[k] === "string")
        .concat(extraStringCols);
      this._INT_COLS = Object.keys(minimalFields).filter(
        (k) => minimalFields[k] === "int",
      );
      this._DOUBLE_COLS = Object.keys(minimalFields).filter(
        (k) => minimalFields[k] === "double",
      );

      this._validationRules = {
        ...this._validationRules,
        ...Object.fromEntries(this._INT_COLS.map((c) => [c, intRule])),
        ...Object.fromEntries(this._DOUBLE_COLS.map((c) => [c, doubleRule])),
        ...Object.fromEntries(this._STRING_COLS.map((c) => [c, stringRule])),
      };
    });
  }

  setSpreadsheetFileName(fileName) {
    this._spreadsheetFileName = fileName;
  }

  mergeValidationError(rowIndex, columnId, errorMessage) {
    runInAction(() => {
      if (!this._validationErrors[rowIndex]) {
        this._validationErrors[rowIndex] = {};
      }

      if (errorMessage && errorMessage !== "") {
        this._validationErrors[rowIndex][columnId] = errorMessage;
      } else {
        delete this._validationErrors[rowIndex][columnId];
        if (Object.keys(this._validationErrors[rowIndex]).length === 0) {
          delete this._validationErrors[rowIndex];
        }
      }
    });
  }

  mergeValidationWarning(rowIndex, columnId, warningMessage) {
    if (!this._validationWarnings[rowIndex]) {
      this._validationWarnings[rowIndex] = {};
    }

    if (warningMessage) {
      this._validationWarnings[rowIndex][columnId] = warningMessage;
    } else {
      delete this._validationWarnings[rowIndex][columnId];
      if (Object.keys(this._validationWarnings[rowIndex]).length === 0) {
        delete this._validationWarnings[rowIndex];
      }
    }
  }

  validateMediaAsset0ColumnOnly() {
    const errors = {};
    const warnings = {};

    const col = "Encounter.mediaAsset0";
    const rule = this._validationRules[col];

    if (!rule) return { errors, warnings };

    this._spreadsheetData.forEach((row, rowIndex) => {
      const value = String(row[col] ?? "").trim();
      let error = "";

      if (rule.required && !value) {
        error = "BULKIMPORT_ERROR_INVALID_REQUIREDFIELD";
      } else if (rule.validate && !rule.validate.call(this, value)) {
        error =
          typeof rule.message === "function"
            ? rule.message.call(this, value)
            : rule.message || "Invalid format";
      }

      if (error) {
        errors[rowIndex] = { [col]: error };
      }
    });
    return { errors, warnings };
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
        sheetNames: "",
        columnCount: 0,
        rowCount: 0,
        uploadProgress: 0,
      };
    });

    window.localStorage.removeItem("BulkImportStore");
  }

  saveState() {
    try {
      runInAction(() => {
        this._isSavingDraft = true;
        this._lastSavedAt = Date.now();
      });

      const json = JSON.stringify(this.stateSnapshot);
      window.localStorage.setItem("BulkImportStore", json);
    } catch (e) {
      console.error("saving as draft failed", e);
    } finally {
      setTimeout(
        () =>
          runInAction(() => {
            this._isSavingDraft = false;
          }),
        700,
      );
    }
  }

  applyServerUploadStatus(uploaded = []) {
    const uploadedFileNames = uploaded.map((p) => p[0]);

    runInAction(() => {
      this._uploadedImages = this._uploadedImages.filter((img) =>
        uploadedFileNames.includes(img),
      );

      this._imageSectionFileNames = this._imageSectionFileNames.filter((name) =>
        uploadedFileNames.includes(name.trim()),
      );

      this._imagePreview = this._imagePreview
        .filter((preview) =>
          uploadedFileNames.includes(preview.fileName.trim()),
        )
        .map((preview) => ({
          ...preview,
          progress: 100,
          src: null,
        }));
    });
  }

  async fetchAndApplyUploaded() {
    if (!this._submissionId) return;
    try {
      const resp = await fetch(
        `/api/v3/bulk-import/${this._submissionId}/files`,
      );

      if (!resp.ok) {
        console.error("Unexpected response", resp.status);
        return;
      }

      const data = await resp.json();
      this.applyServerUploadStatus(data.files);
    } catch (error) {
      console.error("Failed to fetch uploaded files", error);
    }
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
    } else if (col === "Encounter.lifeStage") {
      return this._validLifeStages.map((stage) => ({
        value: stage,
        label: stage,
      }));
    } else if (col === "Encounter.sex") {
      return this._validSex.map((data) => ({ value: data, label: data }));
    } else if (col === "Encounter.behavior") {
      return this._validBehavior.map((data) => ({
        value: data,
        label: data,
      }));
    } else if (col === "Encounter.state") {
      return this._validState.map((state) => ({
        value: state,
        label: state,
      }));
    }
    return [];
  }

  convertToTreeData(locationData) {
    return locationData.map((location) => ({
      title: location.id,
      value: location.id,
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

  initializeFlow(fileInputRef) {
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
      query: () => ({ submissionId: this._submissionId }),
    });

    flowInstance.opts.generateUniqueIdentifier = (file) =>
      `${file.name}-${file.size}-${file.lastModified}-${Math.random().toString(36).slice(2)}`;

    flowInstance.assignBrowse(fileInputRef);
    let hasShownImageLimitAlert = false;
    flowInstance.on("fileAdded", (file) => {
      const currentCount = this._imagePreview.length;
      const totalCount = currentCount + 1;
      const maxSizeMB = this._maxImageSizeMB;
      const isTooLarge = file.size > maxSizeMB * 1024 * 1024;

      if (isTooLarge) {
        this._notifyTooLarge(file);
        return false;
      }

      if (totalCount > this._maxImageCount) {
        if (!hasShownImageLimitAlert) {
          alert(`You can only upload up to ${this._maxImageCount} images.`);
          hasShownImageLimitAlert = true;

          setTimeout(() => {
            hasShownImageLimitAlert = false;
          }, 3000);
        }

        return false;
      }

      this._imageFileMap.set(file.name, file.file);

      this._imagePreview.push({
        src: null,
        fileName: file.name,
        fileSize: file.size,
        progress: 0,
        showThumbnail: false,
      });
      this._imageSectionFileNames.push(file.name);
    });

    flowInstance.opts.createXhr = () => {
      const xhr = new XMLHttpRequest();
      xhr.timeout = 30_000;
      xhr.ontimeout = () => {
        console.warn("XHR timeout, retrying...");
        this._flow.retry();
      };
      return xhr;
    };

    flowInstance.on("fileRetry", (file, chunk) => {
      console.warn(
        `file ${file.name} chunk #${chunk.offset} is retrying # ${chunk.retries} `,
      );
    });

    flowInstance.on("fileProgress", (file) => {
      const percent = Math.floor(file.progress() * 100);

      this._imagePreview = this._imagePreview.map((f) =>
        f.fileName === file.name ? { ...f, progress: percent } : f,
      );
    });

    flowInstance.on("fileSuccess", (file) => {
      runInAction(() => {
        this._imagePreview = this._imagePreview.map((f) =>
          f.fileName === file.name ? { ...f, progress: 100 } : f,
        );
        this._uploadedImages.push(file.name);
      });
    });

    window.addEventListener("offline", () => {
      console.warn("no internet connection, pausing upload");
      flowInstance.pause();
    });

    window.addEventListener("online", async () => {
      alert("Internet restored, checking upload status...");

      this._flow?.resume();

      const resp = await fetch(
        `/api/v3/bulk-import/${this._submissionId}/files`,
      );
      if (!resp.ok) return;

      const serverFiles = await resp.json();

      const uploadedMap = new Map(
        serverFiles.files.map(([name, size]) => [name.toLowerCase(), size]),
      );

      this._flow.files.forEach((file) => {
        const uploadedSize = uploadedMap.get(file.name.toLowerCase());

        if (uploadedSize === file.size) {
          console.info(`Skipping already-uploaded file: ${file.name}`);
          file.cancel();
        } else {
          console.info(`Uploading remaining file: ${file.name}`);
          this._flow.upload(file);
        }
      });
    });

    flowInstance.on("fileError", (file, chunk) => {
      console.error(`Upload failed: ${file.name}, chunk: ${chunk.offset}`);
      this._imagePreview = this._imagePreview.map((f) =>
        f.fileName === file.name ? { ...f, error: true, progress: 0 } : f,
      );

      this.setFailedImages(file.name);
    });

    this._flow = flowInstance;
  }

  _notifyTooLarge(file) {
    const maxSizeMB = this._maxImageSizeMB;
    this._rejectedTooLarge.push(file.name);

    if (this._rejectedToastTimer) return;

    this._rejectedToastTimer = setTimeout(() => {
      const names = this._rejectedTooLarge;
      this._rejectedTooLarge = [];
      this._rejectedToastTimer = null;

      const firstFew = names.slice(0, 5).join(", ");
      const more = names.length > 5 ? ` (+${names.length - 5} more)` : "";

      toast.error(
        `Some files exceed the ${maxSizeMB}MB limit: ${firstFew}${more}`,
        { autoClose: 6000 },
      );
    }, 3000);
  }

  generateThumbnailsForFirst50() {
    const previews = this._imagePreview;
    if (previews.length > this._imageCountGenerateThumbnail)
      return Promise.resolve();

    return new Promise((resolve) => {
      let index = 0;
      const next = () => {
        if (index >= previews.length) return resolve();

        const item = previews[index];
        index++;

        if (item.src) {
          next();
          return;
        }

        const file = this._imageFileMap.get(item.fileName);
        if (!file) {
          next();
          return;
        }

        const reader = new FileReader();
        reader.onload = () => {
          const img = new Image();
          img.onload = () => {
            const canvas = document.createElement("canvas");
            const ctx = canvas.getContext("2d");
            const MAX = 150;
            let [w, h] = [img.width, img.height];

            if (w > h) {
              if (w > MAX) {
                h *= MAX / w;
                w = MAX;
              }
            } else {
              if (h > MAX) {
                w *= MAX / h;
                h = MAX;
              }
            }

            canvas.width = w;
            canvas.height = h;
            ctx.drawImage(img, 0, 0, w, h);
            const thumb = canvas.toDataURL("image/jpeg", 0.7);

            runInAction(() => {
              this._imagePreview[index - 1] = {
                ...item,
                src: thumb,
                showThumbnail: true,
              };
            });

            setTimeout(next, 5);
          };
          img.src = reader.result;
        };
        reader.readAsDataURL(file);
      };

      next();
    });
  }

  uploadFilteredFiles() {
    if (!this._flow) {
      console.warn("Flow instance not initialized.");
      return;
    }
    const maxSize = this._maxImageSizeMB;

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
    runInAction(() => {
      [
        "Encounter.year",
        "Encounter.month",
        "Encounter.day",
        "Encounter.hour",
        "Encounter.minutes",
        "Encounter.seconds",
      ].forEach((col) => {
        if (!this._rawColumns.includes(col)) {
          this._rawColumns.push(col);
        }
      });
    });

    this._spreadsheetData.forEach((_, rowIndex) => {
      const raw = this._rawData[rowIndex];
      const norm = this._spreadsheetData[rowIndex];

      runInAction(() => {
        if (norm["Encounter.year"]) {
          const val = norm["Encounter.year"];
          parseEncounterDateString("Encounter", val, raw);
        }

        if (norm["Sighting.year"]) {
          const val = norm["Sighting.year"];
          parseEncounterDateString("Sighting", val, raw);
        }

        if (norm["Encounter.genus"] != null) {
          const val = norm["Encounter.genus"].trim();
          let g = val;
          let s = "";
          const i = val.indexOf(" ");
          if (i !== -1) {
            g = val.substring(0, i).trim();
            s = val.substring(i + 1).trim();
          }
          raw["Encounter.genus"] = g;
          raw["Encounter.specificEpithet"] = s;
        }

        if (norm["Encounter.mediaAsset0"] != null) {
          Object.keys(raw).forEach((key) => {
            if (
              key.startsWith("Encounter.mediaAsset") &&
              key.split(".").length === 2
            ) {
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
            norm["Encounter.decimalLatitude"],
          );
          raw["Encounter.decimalLatitude"] = m ? m[1] : "";
          raw["Encounter.decimalLongitude"] = m ? m[2] : "";
        }
        if (norm["Encounter.latitude"] != null) {
          const m = /^\s*([^,]+)\s*,\s*([^,]+)\s*$/.exec(
            norm["Encounter.latitude"],
          );
          raw["Encounter.latitude"] = m ? m[1] : "";
          raw["Encounter.longitude"] = m ? m[2] : "";
        }
        if (norm["Sighting.decimalLatitude"] != null) {
          const m = /^\s*([^,]+)\s*,\s*([^,]+)\s*$/.exec(
            norm["Sighting.decimalLatitude"],
          );
          raw["Sighting.decimalLatitude"] = m ? m[1] : "";
          raw["Sighting.decimalLongitude"] = m ? m[2] : "";
        }
      });
    });
  }

  traverseFileTree(entry) {
    this._incrementPending();

    if (entry.isDirectory) {
      const reader = entry.createReader();
      reader.readEntries((entries) => {
        entries.forEach((ent) => this.traverseFileTree(ent));
        this._decrementPending();
      });
    } else if (entry.isFile) {
      entry.file((file) => {
        const supportedTypes = [
          "image/jpeg",
          "image/jpg",
          "image/png",
          // "image/bmp",
        ];
        const isValid = supportedTypes.includes(file.type);

        const maxSizeMB = this._maxImageSizeMB;
        const isTooLarge = file.size > maxSizeMB * 1024 * 1024;

        if (isTooLarge) {
          this._notifyTooLarge({ name: file.name, size: file.size });
          this._decrementPending();
          return;
        }

        if (isValid && !this._imageSectionFileNames.includes(file.name)) {
          this._collectedValidFiles.push(file);
          this._pendingDropFileCount++;
        } else {
          console.warn("Skipped duplicate during folder scan:", file.name);
        }

        this._decrementPending();
      });
    } else {
      this._decrementPending();
    }
  }

  _incrementPending() {
    this._pendingReadCount++;
  }

  _decrementPending() {
    this._pendingReadCount--;
    if (this._pendingReadCount === 0) {
      this._onAllFilesParsed();
    }
  }

  triggerUploadAfterFileInput() {
    if (this._imagePreview.length > 0) {
      this._onAllFilesParsed();
    }
  }

  applyToAllRows(col, value) {
    this._applyToAllRowModalShow = true;
    this._spreadsheetData.forEach((row, rowIndex) => {
      if (col in row) {
        row[col] = value;
      } else {
        console.warn(`Column ${col} does not exist in row ${rowIndex}`);
      }
    });
    this._rawData.forEach((row, rowIndex) => {
      if (col in row) {
        row[col] = value;
      } else {
        console.warn(`Column ${col} does not exist in row ${rowIndex}`);
      }
    });

    this.invalidateValidation();
  }

  _onAllFilesParsed() {
    runInAction(() => {
      if (this._pendingDropFileCount > this._maxImageCount) {
        alert(
          `You can only upload a maximum of ${this._maxImageCount} images.`,
        );
        this._collectedValidFiles = [];
        this._pendingDropFileCount = 0;
        this.setFilesParsed(true);
        return;
      }

      this._collectedValidFiles.forEach((file) => {
        this._imageFileMap.set(file.name, file);
        this.flow.addFile(file);
      });

      this._collectedValidFiles = [];

      this.setFilesParsed(true);
      this.flow.upload();

      if (this._imagePreview.length <= this._imageCountGenerateThumbnail) {
        this.generateThumbnailsForFirst50();
      }
    });
  }

  isDynamicKnownColumn(col) {
    const mediaAssetMatch = col.match(/^Encounter\.mediaAsset(\d+)\.(\w+)$/);
    if (mediaAssetMatch) {
      const suffix = mediaAssetMatch[2];
      if (suffix === "keywords") {
        return true;
      } else {
        return this._labeledKeywordAllowedKeys.includes(suffix);
      }
    }

    return (
      /^Encounter\.keyword\d+$/.test(col) ||
      /^Encounter\.mediaAsset\d+\.keywords$/.test(col) ||
      /^Encounter\.photographer\d+\.emailAddress$/.test(col) ||
      /^Encounter\.photographer\d+\.fullName$/.test(col) ||
      /^Encounter\.photographer\d+\.affiliation$/.test(col) ||
      /^Encounter\.submitter\d+\.emailAddress$/.test(col) ||
      /^Encounter\.submitter\d+\.fullName$/.test(col) ||
      /^Encounter\.submitter\d+\.affiliation$/.test(col) ||
      /^Encounter\.informOther\d+\.emailAddress$/.test(col) ||
      /^Encounter\.informOther\d+\.fullName$/.test(col) ||
      /^Encounter\.informOther\d+\.affiliation$/.test(col) ||
      /^Encounter\.project\d+\.projectIdPrefix$/.test(col) ||
      /^Encounter\.project\d+\.researchProjectName$/.test(col) ||
      /^Encounter\.project\d+\.ownerUsername$/.test(col)
    );
  }

  applyDynamicValidationRules() {
    const isEmail = (val) => {
      if (!val) return true;
      const re =
        /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$/;
      return re.test(val);
    };

    const isString = (val) => {
      return typeof val === "string" || val instanceof String;
    };

    const isInLabeledKeywordAllowedValues = (col, val) => {
      const allowedValues = this._labeledKeywordAllowedPairs[col];
      return Array.isArray(allowedValues) && allowedValues.includes(val);
    };

    this._columnsDef.forEach((col) => {
      if (/^Encounter\.keyword\d+$/.test(col)) {
        this._validationRules[col] = {
          required: false,
          validate: isString,
          message: "invalid value. must be a string",
        };
      }

      if (/^Encounter\.mediaAsset\d+\.keywords$/.test(col)) {
        this._validationRules[col] = {
          required: false,
          validate: isString,
          message: "invalid value. must be a string",
        };
      }

      const mediaAssetLabeledMatch = col.match(
        /^Encounter\.mediaAsset\d+\.(\w+)$/,
      );
      if (mediaAssetLabeledMatch) {
        const field = mediaAssetLabeledMatch[1];
        if (field !== "keywords") {
          this._validationRules[col] = {
            required: false,
            validate: (val) => isInLabeledKeywordAllowedValues(col, val),
            message: "invalid value. must match an allowed label",
          };
        }
      }

      const personFieldMatch = col.match(
        /^Encounter\.(photographer|submitter|informOther)\d+\.(emailAddress|fullName|affiliation)$/,
      );
      if (personFieldMatch) {
        const fieldType = personFieldMatch[2];
        if (fieldType === "emailAddress") {
          this._validationRules[col] = {
            required: false,
            validate: isEmail,
            message: "invalid email address",
          };
        } else {
          this._validationRules[col] = {
            required: false,
            validate: isString,
            message: "invalid value. must be a string",
          };
        }
      }
    });
  }

  updateCellValue(rowIndex, columnId, value) {
    runInAction(() => {
      if (this._spreadsheetData[rowIndex]) {
        this._spreadsheetData[rowIndex][columnId] = value;
      }
      if (this._rawData[rowIndex]) {
        this._rawData[rowIndex][columnId] = value;
      }
    });
  }

  validateRow(rowIndex) {
    const row = this._spreadsheetData[rowIndex];
    const errors = {};
    const warnings = {};

    this._columnsDef.forEach((col) => {
      const value = String(row[col] ?? "");
      const rules = this._validationRules[col];

      if (!rules) return;

      let error = "";
      if (rules.required && !value.trim()) {
        error = "BULKIMPORT_ERROR_INVALID_REQUIREDFIELD";
      } else if (rules.validate && !rules.validate(value)) {
        error =
          typeof rules.message === "function"
            ? rules.message(value)
            : rules.message || "Invalid format";
      }

      if (error) errors[col] = error;
    });

    return { errors, warnings };
  }

  validateSpreadsheet() {
    if (this._cachedValidation) {
      return this._cachedValidation;
    }
    this.applyDynamicValidationRules();
    const errors = {};
    const warnings = {};
    const knownColumnCache = {};
    this._spreadsheetData.forEach((row, rowIndex) => {
      this._columnsDef.forEach((col) => {
        if (!(col in knownColumnCache)) {
          knownColumnCache[col] =
            col in this._minimalFields ||
            extraStringCols.includes(col) ||
            allColumns.includes(col) ||
            this.isDynamicKnownColumn(col);
        }

        if (
          col.startsWith("Encounter.mediaAsset") &&
          this._labeledKeywordAllowedKeys.includes(col.split(".")[2])
        ) {
          const columnName = col.split(".")[2];
          const value = row[col];
          if (
            value &&
            !this._labeledKeywordAllowedPairs[columnName].includes(value)
          ) {
            if (!errors[rowIndex]) errors[rowIndex] = {};
            errors[rowIndex][col] = {
              id: "BULKIMPORT_ERROR_INVALID_LABELEDKEYWORDINVALIDVALUE",
              values: {
                col: col,
                allowedValues:
                  this._labeledKeywordAllowedPairs[columnName].join(", "),
              },
            };
          }
          return;
        }

        this._synonymFields.forEach((group) => {
          const present = group.filter((col) => col in row);

          if (present.length > 1) {
            if (!errors[rowIndex]) errors[rowIndex] = {};
            present.forEach((col) => {
              errors[rowIndex][col] = {
                id: "BULKIMPORT_ERROR_INVALID_SYNONYMFIELDS",
                values: {
                  list: group.join(", "),
                },
              };
            });
          }
        });

        const isKnown = knownColumnCache[col];
        const value = String(row[col] ?? "");
        const rules = this._validationRules[col];

        if (!isKnown) {
          if (!warnings[rowIndex]) warnings[rowIndex] = {};
          warnings[rowIndex][col] = "BULKIMPORT_ERROR_INVALID_UNKNOWNCOLUMN";
          return;
        }

        if (!rules) return;

        let error = "";
        if (rules.required && !value.trim()) {
          error = "BULKIMPORT_ERROR_INVALID_REQUIREDFIELD";
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
    const savedFile = this._uploadedImages.find((f) => f === fileName);
    if (file || savedFile) {
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
