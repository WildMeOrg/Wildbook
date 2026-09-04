import React from "react";
import { render, fireEvent, act } from "@testing-library/react";
import InteractiveAnnotationOverlay from "../../components/AnnotationOverlay";

// Regression cover for issue #1747: zooming used to leave `pan` untouched, which
// -- with a top-left transform origin -- anchors the image's own top-left corner
// and slides whatever the user was looking at out of the pane.

// jsdom does no layout, so every measurement the overlay takes reads 0 unless we
// supply one. Give it a 640x480 pane showing a 4096px-wide source image, and
// leave getBoundingClientRect at jsdom's all-zero default (pane at the viewport
// origin) so a client coordinate is already in the pane's own frame.
const PANE_WIDTH = 640;
const PANE_HEIGHT = 480;
const saved = [];

const stub = (proto, prop, value) => {
  saved.push([proto, prop, Object.getOwnPropertyDescriptor(proto, prop)]);
  Object.defineProperty(proto, prop, { configurable: true, get: () => value });
};

beforeAll(() => {
  stub(HTMLElement.prototype, "clientWidth", PANE_WIDTH);
  stub(HTMLElement.prototype, "clientHeight", PANE_HEIGHT);
  stub(HTMLImageElement.prototype, "naturalWidth", 4096);
  stub(HTMLImageElement.prototype, "complete", true);
});

afterAll(() => {
  saved.forEach(([proto, prop, descriptor]) => {
    if (descriptor) Object.defineProperty(proto, prop, descriptor);
    else delete proto[prop];
  });
});

const renderOverlay = (props = {}) => {
  const ref = React.createRef();
  const { container } = render(
    <InteractiveAnnotationOverlay
      ref={ref}
      imageUrl="https://example.org/whale_master.jpg"
      originalWidth={4096}
      originalHeight={3072}
      annotations={[]}
      {...props}
    />,
  );
  const pane = container.firstChild;

  return { ref, pane, transformed: pane.firstChild };
};

describe("AnnotationOverlay zoom focal point", () => {
  it("anchors a wheel zoom on the cursor", () => {
    const { pane, transformed } = renderOverlay();

    expect(transformed.style.transform).toBe("translate(0px, 0px) scale(1)");

    fireEvent.wheel(pane, { deltaY: -100, clientX: 100, clientY: 80 });

    // pan' = focal - (focal - pan) * nextZoom / zoom
    //      = 100 - 100 * 1.25 = -25   (and 80 - 80 * 1.25 = -20)
    // Before the fix this stayed at translate(0px, 0px): the point under the
    // cursor jumped 25px left and 20px up out from under it.
    expect(transformed.style.transform).toBe(
      "translate(-25px, -20px) scale(1.25)",
    );
  });

  it("keeps the point under the cursor still across several notches", () => {
    const { pane, transformed } = renderOverlay();
    const focal = { x: 100, y: 80 };

    for (let i = 0; i < 4; i += 1) {
      fireEvent.wheel(pane, {
        deltaY: -100,
        clientX: focal.x,
        clientY: focal.y,
      });
    }

    const [, panX, panY, zoom] = transformed.style.transform
      .match(/translate\((-?[\d.]+)px, (-?[\d.]+)px\) scale\(([\d.]+)\)/)
      .map(Number);

    expect(zoom).toBeCloseTo(1.25 ** 4, 10);
    // The image coordinate that started under the cursor is still drawn there.
    const u = focal.x; // pan was 0 and zoom was 1 to begin with
    expect(panX + u * zoom).toBeCloseTo(focal.x, 6);
    expect(panY + focal.y * zoom).toBeCloseTo(focal.y, 6);
  });

  it("anchors the toolbar buttons on the middle of the pane", () => {
    const { ref, transformed } = renderOverlay();

    act(() => ref.current.zoomIn());

    // Pane centre is (320, 240): 320 - 320 * 1.25 = -80, 240 - 240 * 1.25 = -60.
    expect(transformed.style.transform).toBe(
      "translate(-80px, -60px) scale(1.25)",
    );
  });

  it("measures the cursor from the container's content box", () => {
    // A pane that is not at the viewport origin, carries a border and padding, and
    // has been scrolled: the focal point has to be taken from where the transformed
    // wrapper actually starts, not from the pane's border box.
    const rect = jest
      .spyOn(HTMLElement.prototype, "getBoundingClientRect")
      .mockReturnValue({
        left: 30,
        top: 18,
        right: 30 + PANE_WIDTH,
        bottom: 18 + PANE_HEIGHT,
        width: PANE_WIDTH,
        height: PANE_HEIGHT,
        x: 30,
        y: 18,
        toJSON: () => ({}),
      });
    const boxProps = { clientLeft: 5, clientTop: 5, scrollLeft: 7, scrollTop: 9 };
    const restore = Object.entries(boxProps).map(([prop, value]) => {
      const descriptor = Object.getOwnPropertyDescriptor(
        HTMLElement.prototype,
        prop,
      );

      Object.defineProperty(HTMLElement.prototype, prop, {
        configurable: true,
        get: () => value,
      });
      return [prop, descriptor];
    });

    try {
      const { pane, transformed } = renderOverlay({
        containerStyle: { paddingLeft: 20, paddingTop: 12 },
      });

      // The wrapper starts at 30 + 5 + 20 - 7 = 48 across and 18 + 5 + 12 - 9 = 26
      // down, so this cursor sits at (100, 80) in the wrapper's own frame -- the
      // same focal point as the zero-origin case above, and the same resulting pan.
      fireEvent.wheel(pane, { deltaY: -100, clientX: 148, clientY: 106 });

      expect(transformed.style.transform).toBe(
        "translate(-25px, -20px) scale(1.25)",
      );
    } finally {
      rect.mockRestore();
      restore.forEach(([prop, descriptor]) => {
        if (descriptor) {
          Object.defineProperty(HTMLElement.prototype, prop, descriptor);
        } else delete HTMLElement.prototype[prop];
      });
    }
  });

  it("returns to the whole photo when zoomed back out", () => {
    const { ref, transformed } = renderOverlay();

    act(() => ref.current.zoomIn());
    act(() => ref.current.zoomOut());

    expect(transformed.style.transform).toBe("translate(0px, 0px) scale(1)");
  });
});
