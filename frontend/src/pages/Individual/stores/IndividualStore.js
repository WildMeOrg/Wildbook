import { makeAutoObservable } from "mobx";
import axios from "axios";
import { toast } from "react-toastify";
import ModalStore from "./ModalStore";

const USE_MOCK_DATA = true;

const mockIndividual = {
  id: "indv-001",
  thumbnailUrl: "https://placehold.co/200x200/png?text=Avatar",
  taxonomy: "Eubalaena glacialis",
  sex: "Female",
  dateOfBirth: "2012-06-15",
  livingStatus: "Alive",
  identifyingScars: "Distinct scar near dorsal ridge",
  names: [
    { displayName: "Phoenix" },
    { displayName: "RW-2012-15" },
    { displayName: "Catalog-A119" },
  ],
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

  _intl = null;
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

  get individualData() {
    return this._individualData;
  }

  get encounters() {
    return this._individualData?.encounters || [];
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
      .filter((n) => n.displayName && n.displayName !== this.displayName)
      .map((n) => n.displayName);
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
    return this.encounters.flatMap((enc) => enc.mediaAssets || []);
  }

  get encounterLocations() {
    return this.encounters
      .filter(
        (enc) =>
          enc.locationGeoPoint?.lat != null &&
          enc.locationGeoPoint?.lon != null,
      )
      .map((enc) => ({
        lat: enc.locationGeoPoint.lat,
        lon: enc.locationGeoPoint.lon,
        date: enc.date,
        id: enc.id,
        locationName: enc.locationName || enc.verbatimLocality || "",
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

  async fetchIndividual(individualId) {
    this._loading = true;
    this._error = null;

    try {
      if (USE_MOCK_DATA) {
        await new Promise((resolve) => setTimeout(resolve, 300));
        this._individualData = {
          ...mockIndividual,
          id: individualId || mockIndividual.id,
        };
        return this._individualData;
      }

      const response = await axios.get(`/api/v3/individuals/${individualId}`);
      this._individualData = response.data;
      return response.data;
    } catch (error) {
      this._error = error.response?.data?.message || "Failed to load individual";

      if (this._intl) {
        toast.error(
          this._intl.formatMessage({
            id: "INDIVIDUAL_LOAD_ERROR",
            defaultMessage: "Failed to load individual data",
          }),
        );
      }

      throw error;
    } finally {
      this._loading = false;
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
    this.modals.closeAllModals();
  }
}

export default IndividualStore;