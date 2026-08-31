import {
  annotationDisplayRect,
  computeMaxZoom,
  computeFitToAnnotation,
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
