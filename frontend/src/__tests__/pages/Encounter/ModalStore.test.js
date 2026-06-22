import ModalStore from "../../../pages/Encounter/stores/ModalStore";

describe("ModalStore", () => {
  test("initial state should have all modals closed", () => {
    const store = new ModalStore(null);

    expect(store.openContactInfoModal).toBe(false);
    expect(store.openEncounterHistoryModal).toBe(false);
    expect(store.openAddPeopleModal).toBe(false);
    expect(store.openMatchCriteriaModal).toBe(false);
  });

  test("setOpenContactInfoModal toggles contact modal only", () => {
    const store = new ModalStore(null);

    store.setOpenContactInfoModal(true);
    expect(store.openContactInfoModal).toBe(true);
    expect(store.openEncounterHistoryModal).toBe(false);
    expect(store.openAddPeopleModal).toBe(false);
    expect(store.openMatchCriteriaModal).toBe(false);

    store.setOpenContactInfoModal(false);
    expect(store.openContactInfoModal).toBe(false);
  });

  test("setOpenEncounterHistoryModal toggles encounter history modal only", () => {
    const store = new ModalStore(null);

    store.setOpenEncounterHistoryModal(true);
    expect(store.openEncounterHistoryModal).toBe(true);
    expect(store.openContactInfoModal).toBe(false);
    expect(store.openAddPeopleModal).toBe(false);
    expect(store.openMatchCriteriaModal).toBe(false);
  });

  test("setOpenAddPeopleModal toggles add people modal only", () => {
    const store = new ModalStore(null);

    store.setOpenAddPeopleModal(true);
    expect(store.openAddPeopleModal).toBe(true);
    expect(store.openContactInfoModal).toBe(false);
    expect(store.openEncounterHistoryModal).toBe(false);
    expect(store.openMatchCriteriaModal).toBe(false);
  });

  test("setOpenMatchCriteriaModal toggles match criteria modal only", () => {
    const store = new ModalStore(null);

    store.setOpenMatchCriteriaModal(true);
    expect(store.openMatchCriteriaModal).toBe(true);
    expect(store.openContactInfoModal).toBe(false);
    expect(store.openEncounterHistoryModal).toBe(false);
    expect(store.openAddPeopleModal).toBe(false);
  });

  test("closeAllModals closes everything", () => {
    const store = new ModalStore(null);

    store.setOpenContactInfoModal(true);
    store.setOpenEncounterHistoryModal(true);
    store.setOpenAddPeopleModal(true);
    store.setOpenMatchCriteriaModal(true);

    store.closeAllModals();

    expect(store.openContactInfoModal).toBe(false);
    expect(store.openEncounterHistoryModal).toBe(false);
    expect(store.openAddPeopleModal).toBe(false);
    expect(store.openMatchCriteriaModal).toBe(false);
  });

  test("can be constructed with an encounterStore (not observable-tested here)", () => {
    const fakeEncounterStore = { foo: "bar" };
    const store = new ModalStore(fakeEncounterStore);

    expect(store.encounterStore).toBe(fakeEncounterStore);
  });
});
