import { makeAutoObservable } from "mobx";

class ModalStore {
  individualStore;

  _openHistoryModal = false;
  _openEditModal = false;

  constructor(individualStore) {
    this.individualStore = individualStore;
    makeAutoObservable(this, { individualStore: false }, { autoBind: true });
  }

  get openHistoryModal() {
    return this._openHistoryModal;
  }

  setOpenHistoryModal(isOpen) {
    this._openHistoryModal = isOpen;
  }

  get openEditModal() {
    return this._openEditModal;
  }

  setOpenEditModal(isOpen) {
    this._openEditModal = isOpen;
  }

  closeAllModals() {
    this._openHistoryModal = false;
    this._openEditModal = false;
  }
}

export default ModalStore;
