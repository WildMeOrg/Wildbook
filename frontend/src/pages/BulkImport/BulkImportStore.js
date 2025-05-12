import { makeAutoObservable, runInAction, toJS, reaction } from 'mobx';
import Flow from "@flowjs/flow.js";
import { v4 as uuidv4, validate } from "uuid";
import { client } from "../../api/client";
import dayjs from 'dayjs'
import customParseFormat from 'dayjs/plugin/customParseFormat'
import { makePersistable, stopPersisting, clearPersistedStore  } from 'mobx-persist-store';

dayjs.extend(customParseFormat);

export class BulkImportStore {
  _imagePreview = [];
  _imageSectionFileNames = [];
  _imageRequired = true;
  _imageSectionError = false;
  _imageCount = 0;
  _flow = null;
  _submissionId = null;
  _spreadsheetData = [];
  _rawData = [];
  _imageUploadStatus = "notStarted";
  _activeStep = 0;
  _imageUploadProgress = 0;
  _spreadsheetUploadProgress = 0;
  _uploadFinished = false;
  _uploadedImages = [];
  _initialUploadFileCount = 0;
  _columnsDef = [];
  _rawColumns = [];
  _maxImageCount = 200;
  _worksheetInfo = {
    sheetCount: 0,
    sheetNames: "",
    columnCount: 0,
    rowCount: 0,
    uploadProgress: this._spreadsheetUploadProgress,
  }
  _submissionErrors = {};
  _specifiedColumns = [
    "Encounter.mediaAsset0",
    "Encounter.year",
    "Encounter.genus",
    "Encounter.decimalLatitude",
    "Encounter.locationID",
    "Encounter.country",
    "Encounter.occurrenceID",
    "MarkedIndividual.individualID",
    "Encounter.sex",
    "Encounter.lifeStage",
    "Encounter.livingStatus",
    "Encounter.behavior",
    "Encounter.submitterID",
    "Encounter.occurrenceRemarks",
    "Encounter.verbatimLocality",
    "Encounter.dateInMilliseconds",
    "Encounter.researcherComments",
    "Encounter.photographer0.emailAddress",
    "Encounter.informOther0.emailAddress",
    "TissueSample.sampleID",
    "SexAnalysis.sex",
  ];

  _removedColumns = [
    "Encounter.month",
    "Encounter.day",
    "Encounter.hour",
    "Encounter.minutes",
    "Encounter.decimalLongitude",
    "Encounter.specificEpithet",
  ];

  _tableHeaderMapping = {
    "Encounter.mediaAsset0": "Media Assets",
    "Encounter.genus": "Species",
    "MarkedIndividual.individualID": "Individual name",
    "Encounter.occurrenceID": "occurrence ID",
    "Encounter.occurrenceRemarks": "occurrence Remarks",
    "Encounter.locationID": "location",
    "Encounter.country": "country",
    "Encounter.decimalLatitude": "Lat, long (DD)",
    "Encounter.year": "date",
    "Encounter.sex": "sex",
    "Encounter.lifeStage": "life Stage",
    "Encounter.livingStatus": "living Status",
    "Encounter.behavior": "behavior",
    "Encounter.researcherComments": "researcher Comments",
    "Encounter.submitterID": "submitterID",
    "Encounter.photographer0.emailAddress": "photographer Email",
    "Encounter.informOther0.emailAddress": "informOther Email",
    "TissueSample.sampleID": "sample ID",
  };

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
  _columnsUseSelectCell = ["Encounter.genus", "Encounter.locationID", "Encounter.submitterID", "Encounter.country", "Encounter.lifeStage", "Encounter.livingStatus", "Encounter.sex", "Encounter.behavior"];
  _validationRules = {
    "Encounter.mediaAsset0": {
      required: true,
    },
    "Encounter.year": {
      required: true,
      validate: (val) => {
        const FORMATS = [
          'YYYY',
          'YYYY-MM',
          'YYYY-MM-DD',
          'YYYY-MM-DDTHH:mm:ss.SSS',
        ]
        return dayjs(val, FORMATS, true).isValid()
      },
      message:
        "Date must be “YYYY”、“YYYY-MM”、“YYYY-MM-DD” or full ISO datetime “YYYY-MM-DDThh:mm:ss.sss”",
    },
    "Encounter.genus": {
      required: true,
      validate: (val) => {
        return this._validspecies.includes(val);
      },
      message: "Must enter a valid species",
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
        return this._validLocationIDs.includes(value);
      },
      message: "Must enter a valid location ID",
    },
    "Encounter.submitterID": {
      required: true,
      validate: (value) => {
        return this._validSubmitterIDs.includes(value);
      },
      message: "Submitter ID must be a valid submitter ID",
    },
    "Encounter.country": {
      required: false,
      validate: (val) => {
        return this._validCountryIDs.includes(val);
      },
      message: "must be a valid country ID",
    },
    "Encounter.livingStatus": {
      required: false,
      validate: (val) => {
        return this._validLivingStatus.includes(val);
      },
      message: "must be a valid living status",
    },
    "Encounter.lifeStage": {
      required: false,
      validate: (val) => {
        return this._validLifeStages.includes(val);
      },
      message: "must be a valid life stage",
    },
    "Encounter.sex": {
      required: false,
      validate: (val) => {
        return this._validSex.includes(val);
      },
      message: "must be a valid sex",
    },
    "Encounter.behavior": {
      required: false,
      validate: (val) => {
        return this._validBehavior.includes(val);
      },
      message: "must be a valid behavior",
    },
  };

  constructor() {
    makeAutoObservable(this);
    makePersistable(this, {
      name: 'BulkImportStore',
      properties: [
        '_submissionId',
        '_imagePreview',
        '_imageSectionFileNames',
        '_imageUploadStatus',
        '_imageUploadProgress',
        '_spreadsheetUploadProgress',
        '_activeStep',
        '_uploadFinished',
        '_imageCount',
        '_uploadedImages',
        '_initialUploadFileCount',
        '_rawData',
        '_spreadsheetData',
        '_columnsDef',
        '_rawColumns',
        '_worksheetInfo',
        '_submissionErrors',
      ],
      storage: window.localStorage,
    },
      {
        delay: 200,
        fireImmediately: true,
      }
    );
    reaction(
      () => this.stateSnapshot,
      snapshot => localStorage.setItem('BulkImportStore', JSON.stringify(snapshot)),
      { fireImmediately: false }
    );
  }

  get stateSnapshot() {
    return {
      submissionId: this._submissionId,
      imagePreview: toJS(this._imagePreview),
      imageSectionFileNames: toJS(this._imageSectionFileNames),
      imageUploadStatus: this._imageUploadStatus,
      imageUploadProgress: this._imageUploadProgress,
      spreadsheetUploadProgress: this._spreadsheetUploadProgress,
      activeStep: this._activeStep,
      uploadFinished: this._uploadFinished,
      imageCount: this._imageCount,
      uploadedImages: toJS(this._uploadedImages),
      initialUploadFileCount: this._initialUploadFileCount,
      rawData: toJS(this._rawData),
      spreadsheetData: toJS(this._spreadsheetData),
      columnsDef: toJS(this._columnsDef),
      rawColumns: toJS(this._rawColumns),
      worksheetInfo: toJS(this._worksheetInfo),
      submissionErrors: toJS(this._submissionErrors),
    };
  }

  hydrate(state) {
    runInAction(() => {
      Object.entries(state).forEach(([key, value]) => {
        const field = `_${key}`;
        if (field in this) this[field] = value;
      });
    });
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

  get imageUploadStatus() {
    return this._imageUploadStatus;
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

  get tableHeaderMapping() {
    return this._tableHeaderMapping;
  }

  get columnsUseSelectCell() {
    return this._columnsUseSelectCell;
  }

  get columnsDef() {
    return this._columnsDef;
  }

  get specifiedColumns() {
    return this._specifiedColumns;
  }

  get removedColumns() {
    return this._removedColumns;
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

  setSpreadsheetData(data) {
    this._spreadsheetData = data;
  }

  setRawData(data) {
    this._rawData = data;
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

  setWorksheetInfo(sheetCount, sheetNames, columnCount, rowCount) {
    this._worksheetInfo.sheetCount = sheetCount;
    this._worksheetInfo.sheetNames = sheetNames;
    this._worksheetInfo.columnCount = columnCount;
    this._worksheetInfo.rowCount = rowCount;
    this._worksheetInfo.uploadProgress = this._spreadsheetUploadProgress;
  }

  setSubmissionErrors(errors) {
    this._submissionErrors = errors;
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
      this._imageUploadStatus = 'notStarted';
      this._imageUploadProgress = 0;
      this._spreadsheetUploadProgress = 0;
      this._activeStep = 0;
      this._uploadFinished = false;
      this._imageCount = 0;
      this._uploadedImages = [];
      this._initialUploadFileCount = 0;
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
  }

  getOptionsForSelectCell(col) {
    // console.log("getOptionsForSelectCell", col);
    if (col === "Encounter.locationID") {
      return this._validLocationIDs.map((id) => ({
        value: id,
        label: id,
      }));
    } else if (col === "Encounter.submitterID") {
      return this._validSubmitterIDs.map((id) => ({
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
    console.log("setSubmissionId", submissionId);
  }

  initializeFlow(fileInputRef, maxSize) {
    console.log("initializeFlow", fileInputRef, maxSize);
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
      // this._imagePreview = this._imagePreview.map((f) =>
      //   f.fileName === file.name ? { ...f, progress: 100 } : f,
      // );

      // this._uploadedImages.push(file.name);

      runInAction(() => {
        // update both preview and uploadedImages inside an action
        this._imagePreview = this._imagePreview.map(f =>
          f.fileName === file.name ? { ...f, progress: 100 } : f
        );
        this._uploadedImages.push(file.name);
      });
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

  updateRawFromNormalizedRow(rowIndex) {
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
        } else if (rules.validate && !rules.validate(value)) {
          error = rules.message || "Invalid format";
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

}

export default BulkImportStore;
