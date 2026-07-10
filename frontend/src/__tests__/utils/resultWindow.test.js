import {
  DEFAULT_MAX_RESULT_WINDOW,
  browsableItemCount,
  pageCount,
} from "../../utils/resultWindow";

describe("browsableItemCount", () => {
  it("returns totalItems when under the window", () => {
    expect(browsableItemCount(500, 10000)).toBe(500);
  });

  it("caps totalItems at the window", () => {
    // the production case: 147k hits, only the first window is fetchable
    expect(browsableItemCount(147080, 10000)).toBe(10000);
  });

  it("respects a per-index window larger than the default", () => {
    expect(browsableItemCount(147080, 12004)).toBe(12004);
  });

  it("falls back to the default window when window is missing or invalid", () => {
    expect(browsableItemCount(20000, undefined)).toBe(DEFAULT_MAX_RESULT_WINDOW);
    expect(browsableItemCount(20000, 0)).toBe(DEFAULT_MAX_RESULT_WINDOW);
    expect(browsableItemCount(20000, -1)).toBe(DEFAULT_MAX_RESULT_WINDOW);
    expect(browsableItemCount(20000, NaN)).toBe(DEFAULT_MAX_RESULT_WINDOW);
  });

  it("tolerates missing totalItems", () => {
    expect(browsableItemCount(undefined, 10000)).toBe(0);
    expect(browsableItemCount(0, 10000)).toBe(0);
  });
});

describe("pageCount", () => {
  it("computes pages over the browsable range only", () => {
    // 147k results at 20/page must offer 500 pages, not 7354
    expect(pageCount(147080, 10000, 20)).toBe(500);
  });

  it("computes pages normally for small result sets", () => {
    expect(pageCount(45, 10000, 20)).toBe(3);
  });

  it("returns 0 for invalid perPage", () => {
    expect(pageCount(100, 10000, 0)).toBe(0);
    expect(pageCount(100, 10000, undefined)).toBe(0);
  });
});
