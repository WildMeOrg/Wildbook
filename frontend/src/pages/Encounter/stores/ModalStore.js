// stores/ModalStore.js
import { makeAutoObservable } from "mobx";

class ModalStore {
  encounterStore; 

  _openContactInfoModal = false;
  _openEncounterHistoryModal = false;
  _openAddPeopleModal = false;
  _openMatchCriteriaModal = false;

  constructor(encounterStore) {
    this.encounterStore = encounterStore;
    makeAutoObservable(this, { encounterStore: false }, { autoBind: true });
  }

  // Contact Info Modal
  get openContactInfoModal() {
    return this._openContactInfoModal;
  }

  setOpenContactInfoModal(isOpen) {
    console.log("Setting openContactInfoModal to", isOpen);
    this._openContactInfoModal = isOpen;
  }

  // Encounter History Modal
  get openEncounterHistoryModal() {
    return this._openEncounterHistoryModal;
  }

  setOpenEncounterHistoryModal(isOpen) {
    this._openEncounterHistoryModal = isOpen;
  }

  // Add People Modal
  get openAddPeopleModal() {
    return this._openAddPeopleModal;
  }

  setOpenAddPeopleModal(isOpen) {
    this._openAddPeopleModal = isOpen;
  }

  // Match Criteria Modal
  get openMatchCriteriaModal() {
    return this._openMatchCriteriaModal;
  }

  setOpenMatchCriteriaModal(isOpen) {
    this._openMatchCriteriaModal = isOpen;
  }

  closeAllModals() {
    this._openContactInfoModal = false;
    this._openEncounterHistoryModal = false;
    this._openAddPeopleModal = false;
    this._openMatchCriteriaModal = false;
  }
}

export default ModalStore;