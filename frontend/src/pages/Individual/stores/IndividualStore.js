import { makeAutoObservable, runInAction } from "mobx";
import axios from "axios";
import { toast } from "react-toastify";
import ModalStore from "./ModalStore";

const USE_MOCK_DATA = true;

const EMPTY_DETAILS_ERRORS = {
  taxonomy: "",
  sex: "",
  dateOfBirth: "",
  dateOfDeath: "",
  livingStatus: "",
  alternateIds: "",
  additionalComments: "",
};

const mockIndividual = {
  id: "indv-001",
  thumbnailUrl: "https://placehold.co/200x200/png?text=Avatar",
  taxonomy: "Eubalaena glacialis",
  sex: "Female",
  dateOfBirth: "2012-06-15",
  dateOfDeath: "2020",
  livingStatus: "Alive",
  identifyingScars: "Distinct scar near dorsal ridge",
  access: "write",
  names: [
    { displayName: "Phoenix" },
    { displayName: "RW-2012-15" },
    { displayName: "Catalog-A119" },
  ],
  additionalComments: "",
  additionalFiles: [
    {
      id: "file-1",
      name: "genetics-report.pdf",
      url: "#",
      type: "pdf",
    },
    {
      id: "file-2",
      name: "field-notes.docx",
      url: "#",
      type: "docx",
    },
  ],
  encounters: [
    {
      id: "enc-001",
      date: "2025-11-02",
      locationName: "Cape Cod Bay",
      verbatimLocality: "Cape Cod Bay, Massachusetts",
      locationGeoPoint: {
        lat: 41.9584,
        lon: -70.3086,
      },
      mediaAssets: [
        {
          id: "asset-1",
          url: "https://placehold.co/800x500/png?text=Encounter+1+Image+1",
          thumbnailUrl:
            "https://placehold.co/300x200/png?text=Encounter+1+Thumb+1",
        },
        {
          id: "asset-2",
          url: "https://placehold.co/800x500/png?text=Encounter+1+Image+2",
          thumbnailUrl:
            "https://placehold.co/300x200/png?text=Encounter+1+Thumb+2",
        },
      ],
    },
    {
      id: "enc-002",
      date: "2025-08-14",
      locationName: "Bay of Fundy",
      verbatimLocality: "Bay of Fundy, Canada",
      locationGeoPoint: {
        lat: 45.2217,
        lon: -64.5319,
      },
      mediaAssets: [
        {
          id: "asset-3",
          url: "https://placehold.co/800x500/png?text=Encounter+2+Image+1",
          thumbnailUrl:
            "https://placehold.co/300x200/png?text=Encounter+2+Thumb+1",
        },
      ],
    },
    {
      id: "enc-003",
      date: "2024-12-21",
      locationName: "Georges Bank",
      verbatimLocality: "Georges Bank",
      locationGeoPoint: {
        lat: 41.0,
        lon: -67.0,
      },
      mediaAssets: [],
    },
  ],
};

class IndividualStore {
  _individualData = null;
  _loading = false;
  _error = null;

  _activeTab = "encounters";
  _contentView = "table";

  _editDetailsCard = false;
  _detailsDraft = null;
  _detailsAltInput = "";
  _detailsErrors = { ...EMPTY_DETAILS_ERRORS };
  _savingDetails = false;

  _intl = null;
  _siteSettingsData = null;
  _siteSettingsLoading = false;

  _taxonomyOptions = [];
  _livingStatusOptions = [];
  _sexOptions = [];
  _lifeStageOptions = [];

  modals;

  constructor() {
    this.modals = new ModalStore(this);

    makeAutoObservable(
      this,
      {
        modals: false,
      },
      { autoBind: true },
    );
  }

  setIntl(intl) {
    this._intl = intl;
  }

  t(id, defaultMessage) {
    return (
      this._intl?.formatMessage({
        id,
        defaultMessage,
      }) || defaultMessage
    );
  }

  setSiteSettings(siteSettingsData) {
    this._siteSettingsData = siteSettingsData;

    const taxonomyOptions =
      siteSettingsData?.siteTaxonomies?.map((taxonomy) => ({
        value: taxonomy.scientificName,
        label: taxonomy.scientificName,
      })) || [];

    const livingStatusOptions =
      siteSettingsData?.livingStatus?.map((status) => ({
        value: status,
        label: status,
      })) || [];

    const sexOptions =
      siteSettingsData?.sex?.map((data) => ({
        value: data,
        label: data,
      })) || [];

    const lifeStageOptions =
      siteSettingsData?.lifeStage?.map((data) => ({
        value: data,
        label: data,
      })) || [];

    if (
      this._individualData?.taxonomy &&
      !taxonomyOptions.some(
        (option) => option.value === this._individualData.taxonomy,
      )
    ) {
      taxonomyOptions.push({
        value: this._individualData.taxonomy,
        label: this._individualData.taxonomy,
      });
    }

    if (
      this._individualData?.livingStatus &&
      !livingStatusOptions.some(
        (option) => option.value === this._individualData.livingStatus,
      )
    ) {
      livingStatusOptions.push({
        value: this._individualData.livingStatus,
        label: this._individualData.livingStatus,
      });
    }

    if (
      this._individualData?.sex &&
      !sexOptions.some((option) => option.value === this._individualData.sex)
    ) {
      sexOptions.push({
        value: this._individualData.sex,
        label: this._individualData.sex,
      });
    }

    this._taxonomyOptions = taxonomyOptions;
    this._livingStatusOptions = livingStatusOptions;
    this._sexOptions = sexOptions;
    this._lifeStageOptions = lifeStageOptions;
  }

  setSiteSettingsLoading(siteSettingsLoading) {
    this._siteSettingsLoading = siteSettingsLoading;
  }

  get individualData() {
    return this._individualData;
  }

  get loading() {
    return this._loading;
  }

  get encountersLoading() {
    return false;
  }

  get error() {
    return this._error;
  }

  get activeTab() {
    return this._activeTab;
  }

  get contentView() {
    return this._contentView;
  }

  get editDetailsCard() {
    return this._editDetailsCard;
  }

  get detailsDraft() {
    return this._detailsDraft;
  }

  get detailsAltInput() {
    return this._detailsAltInput;
  }

  get detailsErrors() {
    return this._detailsErrors;
  }

  get savingDetails() {
    return this._savingDetails;
  }

  get siteSettingsData() {
    return this._siteSettingsData;
  }

  get siteSettingsLoading() {
    return this._siteSettingsLoading;
  }

  get taxonomyOptions() {
    return this._taxonomyOptions;
  }

  get livingStatusOptions() {
    return this._livingStatusOptions;
  }

  get sexOptions() {
    return this._sexOptions;
  }

  get lifeStageOptions() {
    return this._lifeStageOptions;
  }

  get encounters() {
    return this._individualData?.encounters || [];
  }

  get displayName() {
    return (
      this._individualData?.names?.[0]?.displayName ||
      this._individualData?.id ||
      "Unknown"
    );
  }

  get alternateIds() {
    const names = this._individualData?.names || [];
    return names
      .filter(
        (name) => name.displayName && name.displayName !== this.displayName,
      )
      .map((name) => name.displayName);
  }

  get taxonomy() {
    return this._individualData?.taxonomy || "";
  }

  get sex() {
    return this._individualData?.sex || "";
  }

  get dateOfBirth() {
    return this._individualData?.dateOfBirth || "";
  }

  get status() {
    return this._individualData?.livingStatus || "Alive";
  }

  get identifyingScars() {
    return this._individualData?.identifyingScars || "";
  }

  get avatarUrl() {
    return (
      this._individualData?.thumbnailUrl ||
      `${process.env.PUBLIC_URL}/images/Avatar.png`
    );
  }

  get additionalFiles() {
    return this._individualData?.additionalFiles || [];
  }

  get allMediaAssets() {
    return this.encounters.flatMap((encounter) => encounter.mediaAssets || []);
  }

  get encounterLocations() {
    return this.encounters
      .filter(
        (encounter) =>
          encounter.locationGeoPoint?.lat != null &&
          encounter.locationGeoPoint?.lon != null,
      )
      .map((encounter) => ({
        lat: encounter.locationGeoPoint.lat,
        lon: encounter.locationGeoPoint.lon,
        date: encounter.date,
        id: encounter.id,
        locationName:
          encounter.locationName || encounter.verbatimLocality || "",
      }));
  }

  setActiveTab(tab) {
    this._activeTab = tab;
  }

  setContentView(view) {
    this._contentView = view;
  }

  setEditDetailsCard(isEditing) {
    this._editDetailsCard = isEditing;
  }

  setDetailsError(field, message) {
    this._detailsErrors = {
      ...this._detailsErrors,
      [field]: message,
    };
  }

  clearDetailsError(field) {
    if (!this._detailsErrors[field]) return;
    this._detailsErrors = {
      ...this._detailsErrors,
      [field]: "",
    };
  }

  resetDetailsErrors() {
    this._detailsErrors = { ...EMPTY_DETAILS_ERRORS };
  }

  isValidDateString(value) {
    if (!value) return true;
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;

    const date = new Date(`${value}T00:00:00`);
    return !Number.isNaN(date.getTime());
  }

  isFutureDate(value) {
    if (!value) return false;
    const inputDate = new Date(`${value}T00:00:00`);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return inputDate > today;
  }

  getAllowedOptionValues(options) {
    return new Set((options || []).map((option) => option.value));
  }

  validateDetailsField(field) {
    const draft = this._detailsDraft;
    if (!draft) return true;

    let message = "";

    if (field === "taxonomy") {
      if (!draft.taxonomy) {
        message = this.t(
          "DETAILS_ERROR_TAXONOMY_REQUIRED",
          "Taxonomy is required",
        );
      } else {
        const allowedValues = this.getAllowedOptionValues(
          this._taxonomyOptions,
        );
        if (allowedValues.size && !allowedValues.has(draft.taxonomy)) {
          message = this.t(
            "DETAILS_ERROR_TAXONOMY_INVALID",
            "Please select a valid taxonomy",
          );
        }
      }
    }

    if (field === "sex") {
      if (draft.sex) {
        const allowedValues = this.getAllowedOptionValues(this._sexOptions);
        if (allowedValues.size && !allowedValues.has(draft.sex)) {
          message = this.t(
            "DETAILS_ERROR_SEX_INVALID",
            "Please select a valid sex",
          );
        }
      }
    }

    if (field === "livingStatus") {
      if (draft.livingStatus) {
        const allowedValues = this.getAllowedOptionValues(
          this._livingStatusOptions,
        );
        if (allowedValues.size && !allowedValues.has(draft.livingStatus)) {
          message = this.t(
            "DETAILS_ERROR_STATUS_INVALID",
            "Please select a valid status",
          );
        }
      }
    }

    if (field === "dateOfBirth") {
      if (draft.dateOfBirth && !this.isValidDateString(draft.dateOfBirth)) {
        message = this.t(
          "DETAILS_ERROR_BIRTH_INVALID",
          "Please enter a valid birth date",
        );
      } else if (draft.dateOfBirth && this.isFutureDate(draft.dateOfBirth)) {
        message = this.t(
          "DETAILS_ERROR_BIRTH_FUTURE",
          "Date of birth cannot be in the future",
        );
      } else if (
        draft.dateOfBirth &&
        draft.dateOfDeath &&
        this.isValidDateString(draft.dateOfBirth) &&
        this.isValidDateString(draft.dateOfDeath) &&
        draft.dateOfDeath < draft.dateOfBirth
      ) {
        message = this.t(
          "DETAILS_ERROR_BIRTH_AFTER_DEATH",
          "Date of birth cannot be after date of death",
        );
      }
    }

    if (field === "dateOfDeath") {
      if (draft.dateOfDeath && !this.isValidDateString(draft.dateOfDeath)) {
        message = this.t(
          "DETAILS_ERROR_DEATH_INVALID",
          "Please enter a valid death date",
        );
      } else if (draft.dateOfDeath && this.isFutureDate(draft.dateOfDeath)) {
        message = this.t(
          "DETAILS_ERROR_DEATH_FUTURE",
          "Date of death cannot be in the future",
        );
      } else if (
        draft.dateOfBirth &&
        draft.dateOfDeath &&
        this.isValidDateString(draft.dateOfBirth) &&
        this.isValidDateString(draft.dateOfDeath) &&
        draft.dateOfDeath < draft.dateOfBirth
      ) {
        message = this.t(
          "DETAILS_ERROR_DEATH_BEFORE_BIRTH",
          "Date of death cannot be before date of birth",
        );
      }
    }

    if (field === "alternateIds") {
      const ids = draft.alternateIds || [];
      const normalized = ids.map((id) => id.trim()).filter(Boolean);
      const lowerSet = new Set(normalized.map((id) => id.toLowerCase()));
      if (lowerSet.size !== normalized.length) {
        message = this.t(
          "DETAILS_ERROR_ALTERNATE_IDS_DUPLICATE",
          "Alternate IDs must be unique",
        );
      }
    }

    if (field === "additionalComments") {
      if ((draft.additionalComments || "").length > 5000) {
        message = this.t(
          "DETAILS_ERROR_ADDITIONAL_COMMENTS_TOO_LONG",
          "Additional comments are too long",
        );
      }
    }

    this.setDetailsError(field, message);

    if (field === "dateOfBirth" || field === "dateOfDeath") {
      if (field === "dateOfBirth") {
        const dateOfDeathMessage = this.validateDateOfDeathCrossFieldOnly();
        this.setDetailsError("dateOfDeath", dateOfDeathMessage);
      } else {
        const dateOfBirthMessage = this.validateDateOfBirthCrossFieldOnly();
        this.setDetailsError("dateOfBirth", dateOfBirthMessage);
      }
    }

    return !message;
  }

  validateDateOfBirthCrossFieldOnly() {
    const draft = this._detailsDraft;
    if (!draft) return "";

    if (
      draft.dateOfBirth &&
      draft.dateOfDeath &&
      this.isValidDateString(draft.dateOfBirth) &&
      this.isValidDateString(draft.dateOfDeath) &&
      draft.dateOfDeath < draft.dateOfBirth
    ) {
      return this.t(
        "DETAILS_ERROR_BIRTH_AFTER_DEATH",
        "Date of birth cannot be after date of death",
      );
    }

    return "";
  }

  validateDateOfDeathCrossFieldOnly() {
    const draft = this._detailsDraft;
    if (!draft) return "";

    if (
      draft.dateOfBirth &&
      draft.dateOfDeath &&
      this.isValidDateString(draft.dateOfBirth) &&
      this.isValidDateString(draft.dateOfDeath) &&
      draft.dateOfDeath < draft.dateOfBirth
    ) {
      return this.t(
        "DETAILS_ERROR_DEATH_BEFORE_BIRTH",
        "Date of death cannot be before date of birth",
      );
    }

    return "";
  }

  validateAllDetailsFields() {
    const fields = [
      "taxonomy",
      "sex",
      "dateOfBirth",
      "dateOfDeath",
      "livingStatus",
      "alternateIds",
      "additionalComments",
    ];

    let isValid = true;

    fields.forEach((field) => {
      const fieldValid = this.validateDetailsField(field);
      if (!fieldValid) {
        isValid = false;
      }
    });

    return isValid;
  }

  setDetailsField(field, value) {
    if (!this._detailsDraft) return;

    this._detailsDraft = {
      ...this._detailsDraft,
      [field]: value,
    };

    this.validateDetailsField(field);
  }

  setDetailsAltInput(value) {
    this._detailsAltInput = value;
    this.clearDetailsError("alternateIds");
  }

  startDetailsEdit() {
    if (!this._individualData) return;

    this._detailsDraft = {
      taxonomy: this._individualData?.taxonomy || "",
      sex: this._individualData?.sex || "",
      dateOfBirth: this._individualData?.dateOfBirth || "",
      dateOfDeath: this._individualData?.dateOfDeath || "",
      livingStatus: this._individualData?.livingStatus || "",
      alternateIds: [...this.alternateIds],
      additionalComments: this._individualData?.additionalComments || "",
    };

    this._detailsAltInput = "";
    this.resetDetailsErrors();
    this._editDetailsCard = true;
  }

  cancelDetailsEdit() {
    this._editDetailsCard = false;
    this._detailsDraft = null;
    this._detailsAltInput = "";
    this.resetDetailsErrors();
  }

  addAlternateIdDraft() {
    const trimmedValue = this._detailsAltInput.trim();
    if (!this._detailsDraft) return;

    if (!trimmedValue) {
      this.setDetailsError(
        "alternateIds",
        this.t(
          "DETAILS_ERROR_ALTERNATE_IDS_EMPTY",
          "Alternate ID cannot be empty",
        ),
      );
      return;
    }

    const exists = (this._detailsDraft.alternateIds || []).some(
      (id) => id.toLowerCase() === trimmedValue.toLowerCase(),
    );

    if (exists) {
      this.setDetailsError(
        "alternateIds",
        this.t(
          "DETAILS_ERROR_ALTERNATE_IDS_DUPLICATE",
          "Alternate IDs must be unique",
        ),
      );
      this._detailsAltInput = "";
      return;
    }

    this._detailsDraft = {
      ...this._detailsDraft,
      alternateIds: [...(this._detailsDraft.alternateIds || []), trimmedValue],
    };
    this._detailsAltInput = "";
    this.validateDetailsField("alternateIds");
  }

  removeAlternateIdDraft(value) {
    if (!this._detailsDraft) return;

    this._detailsDraft = {
      ...this._detailsDraft,
      alternateIds: (this._detailsDraft.alternateIds || []).filter(
        (id) => id !== value,
      ),
    };

    this.validateDetailsField("alternateIds");
  }

  buildDetailsPayload(draft) {
    const normalizedAlternateIds = Array.from(
      new Map(
        (draft.alternateIds || [])
          .map((id) => id.trim())
          .filter(Boolean)
          .map((id) => [id.toLowerCase(), id]),
      ).values(),
    );

    return {
      taxonomy: draft.taxonomy,
      sex: draft.sex || null,
      dateOfBirth: draft.dateOfBirth || null,
      dateOfDeath: draft.dateOfDeath || null,
      livingStatus: draft.livingStatus || null,
      additionalComments: draft.additionalComments || "",
      names: [
        { displayName: this.displayName },
        ...normalizedAlternateIds.map((id) => ({
          displayName: id,
        })),
      ],
    };
  }

  async saveDetailsCard() {
    if (
      !this._detailsDraft ||
      !this._individualData?.id ||
      this._savingDetails
    ) {
      return;
    }

    const isValid = this.validateAllDetailsFields();
    if (!isValid) {
      toast.error(
        this.t(
          "DETAILS_SAVE_VALIDATION_ERROR",
          "Please fix the highlighted fields before saving",
        ),
      );
      return;
    }

    this._savingDetails = true;

    try {
      await this.updateIndividualDetails(this._detailsDraft);

      runInAction(() => {
        this._editDetailsCard = false;
        this._detailsDraft = null;
        this._detailsAltInput = "";
        this.resetDetailsErrors();
      });

      toast.success(
        this.t("DETAILS_SAVE_SUCCESS", "Details updated successfully"),
      );
    } catch (error) {
      toast.error(
        error?.response?.data?.message ||
          this.t("DETAILS_SAVE_ERROR", "Failed to update details"),
      );
    } finally {
      runInAction(() => {
        this._savingDetails = false;
      });
    }
  }

  async updateIndividualDetails(draft) {
    if (!this._individualData?.id) return null;

    const payload = this.buildDetailsPayload(draft);

    if (USE_MOCK_DATA) {
      await new Promise((resolve) => setTimeout(resolve, 300));

      runInAction(() => {
        this._individualData = {
          ...this._individualData,
          ...payload,
        };
      });

      if (this._siteSettingsData) {
        this.setSiteSettings(this._siteSettingsData);
      }

      return this._individualData;
    }

    const response = await axios.put(
      `/api/v3/individuals/${this._individualData.id}`,
      payload,
    );

    runInAction(() => {
      this._individualData = response.data;
    });

    if (this._siteSettingsData) {
      this.setSiteSettings(this._siteSettingsData);
    }

    return response.data;
  }

  async fetchIndividual(individualId) {
    this._loading = true;
    this._error = null;

    try {
      let response;

      if (USE_MOCK_DATA) {
        await new Promise((resolve) => setTimeout(resolve, 300));
        response = {
          data: {
            ...mockIndividual,
            id: individualId || mockIndividual.id,
          },
        };
      } else {
        response = await axios.get(`/api/v3/individuals/${individualId}`);
      }

      runInAction(() => {
        this._individualData = response.data;
      });

      if (this._siteSettingsData) {
        this.setSiteSettings(this._siteSettingsData);
      }

      return response.data;
    } catch (error) {
      runInAction(() => {
        this._error =
          error?.response?.data?.message || "Failed to load individual";
      });

      toast.error(
        this.t("INDIVIDUAL_LOAD_ERROR", "Failed to load individual data"),
      );

      throw error;
    } finally {
      runInAction(() => {
        this._loading = false;
      });
    }
  }

  async refreshIndividual() {
    if (!this._individualData?.id) return;
    return this.fetchIndividual(this._individualData.id);
  }

  reset() {
    this._individualData = null;
    this._loading = false;
    this._error = null;
    this._activeTab = "encounters";
    this._contentView = "table";

    this._editDetailsCard = false;
    this._detailsDraft = null;
    this._detailsAltInput = "";
    this._detailsErrors = { ...EMPTY_DETAILS_ERRORS };
    this._savingDetails = false;

    this._siteSettingsData = null;
    this._siteSettingsLoading = false;
    this._taxonomyOptions = [];
    this._livingStatusOptions = [];
    this._sexOptions = [];
    this._lifeStageOptions = [];

    this.modals.closeAllModals();
  }
}

export default IndividualStore;
