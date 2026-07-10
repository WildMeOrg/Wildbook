import {
  DEFAULT_MAX_RESULT_WINDOW,
  browsableItemCount,
  pageCount,
} from "../../utils/resultWindow";

describe("pageCount", () => {
  it("computes pages over the browsable range only", () => {
    // 147k results at 20/page must offer 500 pages, not 7354
    expect(pageCount(147080, 10000, 20)).toBe(500);
  });

  it("never offers a final page that straddles the window", () => {
    // at 30/page, page 334 would request from=9990&size=30 (=10020 > 10000);
    // only 333 full pages fit inside the window
    expect(pageCount(147080, 10000, 30)).toBe(333);
  });

  it("keeps the partial last page for result sets inside the window", () => {
    expect(pageCount(45, 10000, 20)).toBe(3);
    // exactly at the window: ceil is safe because from+size never exceeds it
    expect(pageCount(10000, 10000, 30)).toBe(334);
  });

  it("respects a per-index window larger than the default", () => {
    expect(pageCount(147080, 12004, 20)).toBe(600);
  });

  it("falls back to the default window when window is missing or invalid", () => {
    expect(pageCount(20000, undefined, 20)).toBe(500);
    expect(pageCount(20000, 0, 20)).toBe(500);
    expect(pageCount(20000, -1, 20)).toBe(500);
    expect(pageCount(20000, NaN, 20)).toBe(500);
  });

  it("returns 0 for invalid perPage", () => {
    expect(pageCount(100, 10000, 0)).toBe(0);
    expect(pageCount(100, 10000, undefined)).toBe(0);
  });
});

describe("browsableItemCount", () => {
  it("returns totalItems when under the window", () => {
    expect(browsableItemCount(500, 10000, 20)).toBe(500);
  });

  it("reports the actually reachable item count when capped", () => {
    expect(browsableItemCount(147080, 10000, 20)).toBe(10000);
    // at 30/page only 333 pages x 30 = 9990 items are reachable
    expect(browsableItemCount(147080, 10000, 30)).toBe(9990);
  });

  it("respects a per-index window larger than the default", () => {
    expect(browsableItemCount(147080, 12004, 20)).toBe(12000);
  });

  it("tolerates missing totalItems and invalid perPage", () => {
    expect(browsableItemCount(undefined, 10000, 20)).toBe(0);
    expect(browsableItemCount(0, 10000, 20)).toBe(0);
    expect(browsableItemCount(20000, 10000, 0)).toBe(10000);
  });

  it("exports the OpenSearch default window", () => {
    expect(DEFAULT_MAX_RESULT_WINDOW).toBe(10000);
  });
});
