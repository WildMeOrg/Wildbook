import EncounterFormStore from "../../../pages/SearchPages/stores/EncounterFormStore";

describe("EncounterFormStore", () => {
  let store;

  beforeEach(() => {
    store = new EncounterFormStore();
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
  });
});
