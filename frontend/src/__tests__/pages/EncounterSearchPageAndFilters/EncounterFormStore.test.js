import EncounterFormStore from "../../../pages/SearchPages/stores/EncounterFormStore";

describe("EncounterFormStore", () => {
  let store;
  let mockStorage = {};

  beforeAll(() => {
    jest
      .spyOn(Storage.prototype, "getItem")
      .mockImplementation((key) => mockStorage[key] || null);
    jest
      .spyOn(Storage.prototype, "setItem")
      .mockImplementation((key, value) => {
        mockStorage[key] = value ? value.toString() : "";
      });
    jest.spyOn(Storage.prototype, "removeItem").mockImplementation((key) => {
      delete mockStorage[key];
    });
    jest.spyOn(Storage.prototype, "clear").mockImplementation(() => {
      mockStorage = {};
    });
  });

  beforeEach(() => {
    store = new EncounterFormStore();
    mockStorage = {};
    jest.clearAllMocks();
  });

  afterAll(() => {
    jest.restoreAllMocks();
  });

  test("initializes with empty formFilters", () => {
    expect(store.formFilters).toEqual([]);
  });

  test("set and get formFilters", () => {
    const filters = [
      { filterId: "abc", clause: "AND", query: "test", filterKey: "key1" },
    ];
    store.formFilters = filters;
    expect(store.formFilters).toStrictEqual(filters);
  });

  test("addFilter adds a new filter if not existing", () => {
    store.addFilter("filter1", "AND", "someQuery", "key1", "some/path");
    expect(store.formFilters).toHaveLength(1);
    expect(store.formFilters[0]).toEqual({
      filterId: "filter1",
      clause: "AND",
      query: "someQuery",
      filterKey: "key1",
      path: "some/path",
    });
  });

  test("addFilter updates an existing filter with same filterId", () => {
    store.addFilter("filter1", "AND", "query1", "key1");
    store.addFilter("filter1", "OR", "query2", "key2", "new/path");

    expect(store.formFilters).toHaveLength(1);
    expect(store.formFilters[0]).toEqual({
      filterId: "filter1",
      clause: "OR",
      query: "query2",
      filterKey: "key2",
      path: "new/path",
    });
  });

  test("removeFilter removes the filter by filterId", () => {
    store.addFilter("filter1", "AND", "q1", "key1");
    store.addFilter("filter2", "OR", "q2", "key2");
    store.removeFilter("filter1");

    expect(store.formFilters).toHaveLength(1);
    expect(store.formFilters[0].filterId).toBe("filter2");
  });

  test("removeFilterByFilterKey removes filters by filterKey", () => {
    store.addFilter("f1", "AND", "q1", "k1");
    store.addFilter("f2", "OR", "q2", "k2");
    store.addFilter("f3", "AND", "q3", "k1");

    store.removeFilterByFilterKey("k1");

    expect(store.formFilters).toHaveLength(1);
    expect(store.formFilters[0].filterKey).toBe("k2");
  });

  test("resetFilters clears all filters", () => {
    store.addFilter("f1", "AND", "q1", "k1");
    store.addFilter("f2", "OR", "q2", "k2");

    store.resetFilters();

    expect(store.formFilters).toEqual([]);
    expect(store.appliedFilters).toEqual([]);
    expect(window.sessionStorage.removeItem).toHaveBeenCalledWith("formData");
  });

  test("applyFilters deep copies formFilters to appliedFilters and saves to storage", () => {
    const mockFilter = {
      filterId: "f1",
      clause: "AND",
      query: "q1",
      filterKey: "k1",
      path: "",
    };
    store.addFilter(
      mockFilter.filterId,
      mockFilter.clause,
      mockFilter.query,
      mockFilter.filterKey,
    );

    store.applyFilters();

    expect(store.appliedFilters).toHaveLength(1);
    expect(store.appliedFilters[0]).toEqual(mockFilter);

    expect(store.appliedFilters).not.toBe(store.formFilters);

    expect(window.sessionStorage.setItem).toHaveBeenCalledWith(
      store.FILTER_STORAGE_KEY,
      JSON.stringify([mockFilter]),
    );
  });

  test("getFiltersFromStorage loads valid JSON into formFilters and appliedFilters", () => {
    const mockFilter = {
      filterId: "f1",
      clause: "AND",
      query: "q1",
      filterKey: "k1",
      path: "",
    };
    const savedData = JSON.stringify([mockFilter]);
    window.sessionStorage.setItem("formData", savedData);

    store.getFiltersFromStorage();

    expect(window.sessionStorage.getItem).toHaveBeenCalledTimes(1);

    expect(store.formFilters).toHaveLength(1);
    expect(store.appliedFilters).toHaveLength(1);
  });

  test("getFiltersFromStorage ignores strings/non-arrays and clears storage", () => {
    window.sessionStorage.setItem("formData", JSON.stringify("abc"));
    store.getFiltersFromStorage();

    expect(store.formFilters).toEqual([]);
    expect(window.sessionStorage.removeItem).toHaveBeenCalledWith("formData");
  });

  test("setFiltersInSessionStorage clears the storage data when called with no data", () => {
    const mockFilter = {
      filterId: "f1",
      clause: "AND",
      query: "q1",
      filterKey: "k1",
      path: "",
    };
    window.sessionStorage.setItem("formData", JSON.stringify(mockFilter));
    store.setFiltersInSessionStorage();

    expect(store.formFilters).toEqual([]);
    expect(window.sessionStorage.removeItem).toHaveBeenCalledWith("formData");
  });

  test("getFiltersFromStorage handles malformed JSON gracefully and clears storage", () => {
    const consoleSpy = jest
      .spyOn(console, "error")
      .mockImplementation(() => {});
    window.sessionStorage.setItem("formData", "{ bad_json ]");

    store.getFiltersFromStorage();

    expect(store.formFilters).toEqual([]);
    expect(consoleSpy).toHaveBeenCalled();
    expect(window.sessionStorage.removeItem).toHaveBeenCalledWith("formData");

    consoleSpy.mockRestore();
  });
});
