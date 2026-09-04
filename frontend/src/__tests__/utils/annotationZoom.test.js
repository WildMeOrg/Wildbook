import {
  annotationDisplayRect,
  computeMaxZoom,
  computeFitToAnnotation,
  computeZoomAboutPoint,
} from "../../utils/annotationZoom";

describe("computeMaxZoom", () => {
  it("lets a 4096px master be zoomed to its native pixels", () => {
    // 4096 source pixels across a 640px-wide pane is 6.4x; OpenSeadragon's
    // maxZoomPixelRatio of 1.1 is the ceiling the legacy viewer used.
    expect(computeMaxZoom({ naturalWidth: 4096, displayWidth: 640 })).toBeCloseTo(
      7.04,
      5,
    );
  });

  it("never drops below the floor for a small source image", () => {
    // 1024 / 640 * 1.1 = 1.76, which would be a worse ceiling than today's 3x.
    expect(
      computeMaxZoom({ naturalWidth: 1024, displayWidth: 640, floor: 3 }),
    ).toBe(3);
  });

  it("falls back to the floor before the image has laid out", () => {
    expect(computeMaxZoom({ naturalWidth: 0, displayWidth: 0, floor: 3 })).toBe(3);
  });
});

describe("annotationDisplayRect", () => {
  const annotation = { x: 100, y: 50, width: 400, height: 200 };

  it("scales source-pixel coordinates into displayed pixels", () => {
    expect(annotationDisplayRect(annotation, { scaleX: 2, scaleY: 2 })).toEqual({
      x: 50,
      y: 25,
      width: 200,
      height: 100,
      rotation: 0,
    });
  });

  it("applies the aspect adjustment when the asset carries rotation info", () => {
    expect(
      annotationDisplayRect(annotation, {
        scaleX: 2,
        scaleY: 2,
        originalWidth: 1000,
        originalHeight: 500,
        hasRotation: true,
      }),
    ).toEqual({ x: 100, y: 12.5, width: 400, height: 50, rotation: 0 });
  });

  it("returns null for a trivial whole-image annotation", () => {
    expect(
      annotationDisplayRect(
        { ...annotation, trivial: true },
        { scaleX: 1, scaleY: 1 },
      ),
    ).toBeNull();
  });

  it("returns null when the box has no area", () => {
    expect(
      annotationDisplayRect(
        { x: 1, y: 1, width: 0, height: 10 },
        { scaleX: 1, scaleY: 1 },
      ),
    ).toBeNull();
  });
});

describe("computeFitToAnnotation", () => {
  const container = { containerWidth: 640, containerHeight: 480 };

  it("fills the pane with the annotation and centers it", () => {
    const { zoom, pan } = computeFitToAnnotation({
      rect: { x: 50, y: 25, width: 200, height: 100 },
      ...container,
      minZoom: 1,
      maxZoom: 8,
      margin: 0.1,
    });

    // width is the binding dimension: 640 / (200 * 1.1)
    expect(zoom).toBeCloseTo(2.909090909, 6);
    expect(pan.x).toBeCloseTo(320 - 150 * 2.909090909, 5);
    expect(pan.y).toBeCloseTo(240 - 75 * 2.909090909, 5);
  });

  it("does not zoom past the ceiling for a tiny annotation", () => {
    const { zoom } = computeFitToAnnotation({
      rect: { x: 0, y: 0, width: 4, height: 4 },
      ...container,
      minZoom: 1,
      maxZoom: 8,
      margin: 0.1,
    });

    expect(zoom).toBe(8);
  });

  it("does not zoom out past the floor for an annotation that fills the frame", () => {
    const { zoom } = computeFitToAnnotation({
      rect: { x: 0, y: 0, width: 640, height: 480 },
      ...container,
      minZoom: 1,
      maxZoom: 8,
      margin: 0.1,
    });

    expect(zoom).toBe(1);
  });

  it("returns null when there is no rect to fit", () => {
    expect(
      computeFitToAnnotation({ rect: null, ...container, minZoom: 1, maxZoom: 8 }),
    ).toBeNull();
  });
});

describe("computeZoomAboutPoint", () => {
  // Where a point at unscaled image coordinate `u` is drawn, given the overlay's
  // `translate(pan) scale(zoom)` about a top-left origin.
  const screenX = (u, { pan, zoom }) => pan.x + u * zoom;

  it("holds the focal point under the same screen pixel", () => {
    const before = { pan: { x: -100, y: -50 }, zoom: 2 };
    const focal = { x: 320, y: 240 };
    const nextZoom = 2.5;

    const pan = computeZoomAboutPoint({ ...before, nextZoom, focal });

    // The image coordinate that was under the focal point before the zoom...
    const u = (focal.x - before.pan.x) / before.zoom;
    // ...is still drawn there after it.
    expect(screenX(u, { pan, zoom: nextZoom })).toBeCloseTo(focal.x, 10);
    const v = (focal.y - before.pan.y) / before.zoom;
    expect(pan.y + v * nextZoom).toBeCloseTo(focal.y, 10);
  });

  it("works from a negative pan, which is what auto-fit produces", () => {
    // fitToAnnotation pans the image up and to the left to centre the box, so
    // negative pans are the normal case on the match-results page, not an edge one.
    const before = { pan: { x: -1240.5, y: -880.25 }, zoom: 4 };
    const focal = { x: 300, y: 200 };

    const pan = computeZoomAboutPoint({ ...before, nextZoom: 5, focal });

    const u = (focal.x - before.pan.x) / before.zoom;
    expect(screenX(u, { pan, zoom: 5 })).toBeCloseTo(focal.x, 10);
  });

  it("leaves the pan alone when the focal point is the wrapper origin", () => {
    // The old behaviour -- an unchanged pan -- is only correct for u = 0, which
    // is exactly why every other point drifted.
    expect(
      computeZoomAboutPoint({
        pan: { x: -100, y: -50 },
        zoom: 2,
        nextZoom: 3,
        focal: { x: -100, y: -50 },
      }),
    ).toEqual({ x: -100, y: -50 });
  });

  it("is a no-op when the zoom did not actually change", () => {
    // Zoom clamped at the ceiling: nextZoom === zoom must not nudge the image.
    expect(
      computeZoomAboutPoint({
        pan: { x: -100, y: -50 },
        zoom: 3.5,
        nextZoom: 3.5,
        focal: { x: 320, y: 240 },
      }),
    ).toEqual({ x: -100, y: -50 });
  });

  it("round-trips: zooming out about the same point undoes zooming in", () => {
    const focal = { x: 275, y: 190 };
    const start = { pan: { x: -60, y: -35 }, zoom: 2 };

    const zoomedIn = computeZoomAboutPoint({
      ...start,
      nextZoom: 2.5,
      focal,
    });
    const backOut = computeZoomAboutPoint({
      pan: zoomedIn,
      zoom: 2.5,
      nextZoom: 2,
      focal,
    });

    expect(backOut.x).toBeCloseTo(start.pan.x, 10);
    expect(backOut.y).toBeCloseTo(start.pan.y, 10);
  });

  it("keeps the pan when the zoom is unusable", () => {
    const pan = { x: -100, y: -50 };
    const focal = { x: 10, y: 10 };

    expect(computeZoomAboutPoint({ pan, zoom: 0, nextZoom: 2, focal })).toEqual(
      pan,
    );
    expect(computeZoomAboutPoint({ pan, zoom: 2, nextZoom: 0, focal })).toEqual(
      pan,
    );
    expect(
      computeZoomAboutPoint({ pan, zoom: 2, nextZoom: NaN, focal }),
    ).toEqual(pan);
  });

  it("keeps the pan when there is no focal point to anchor", () => {
    const pan = { x: -100, y: -50 };

    expect(
      computeZoomAboutPoint({ pan, zoom: 2, nextZoom: 3, focal: null }),
    ).toEqual(pan);
    expect(
      computeZoomAboutPoint({
        pan,
        zoom: 2,
        nextZoom: 3,
        focal: { x: NaN, y: 0 },
      }),
    ).toEqual(pan);
  });

  it("falls back to the origin when the pan itself is unusable", () => {
    expect(
      computeZoomAboutPoint({
        pan: undefined,
        zoom: 2,
        nextZoom: 3,
        focal: { x: 10, y: 10 },
      }),
    ).toEqual({ x: 0, y: 0 });
  });
});
