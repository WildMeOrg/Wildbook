import {
  placeAnnotationIcons,
  DEFAULT_ICON_CORNER,
} from "../../utils/annotationIconPlacement";

// Container is the clipping element (the image box); everything is in displayed px.
const container = { width: 500, height: 300 };
const editDelete = { width: 20, height: 40 }; // two 20x20 icons stacked
const link = { width: 18, height: 18 }; // single 18x18 icon

const place = (box, overrides = {}) =>
  placeAnnotationIcons({
    box: { rotation: 0, ...box },
    container,
    cluster: editDelete,
    border: 2,
    ...overrides,
  });

// Mirror of CSS `rotate(theta)` about the box centre, written independently
// of the helper so the property tests are not tautological.
const rotateAbout = (px, py, cx, cy, theta) => {
  const dx = px - cx;
  const dy = py - cy;
  return {
    x: cx + dx * Math.cos(theta) - dy * Math.sin(theta),
    y: cy + dx * Math.sin(theta) + dy * Math.cos(theta),
  };
};

// Where the four vertices of a cluster placed at CSS `{top, left}` (padding-box
// coordinates) land in container space once the box's rotation is applied.
const clusterVerticesInContainer = (box, style, cluster, border) => {
  const w = Math.max(box.width, 2 * border);
  const h = Math.max(box.height, 2 * border);
  const localLeft = border + style.left;
  const localTop = border + style.top;
  return [
    [localLeft, localTop],
    [localLeft + cluster.width, localTop],
    [localLeft, localTop + cluster.height],
    [localLeft + cluster.width, localTop + cluster.height],
  ].map(([lx, ly]) => {
    const r = rotateAbout(lx, ly, w / 2, h / 2, box.rotation || 0);
    return { x: r.x + box.x, y: r.y + box.y };
  });
};

// Only floating-point noise is allowed outside the container.
const FLOAT_SLACK = 1e-6;
const expectInside = (points, { width, height }) => {
  for (const p of points) {
    expect(p.x).toBeGreaterThanOrEqual(-FLOAT_SLACK);
    expect(p.x).toBeLessThanOrEqual(width + FLOAT_SLACK);
    expect(p.y).toBeGreaterThanOrEqual(-FLOAT_SLACK);
    expect(p.y).toBeLessThanOrEqual(height + FLOAT_SLACK);
  }
};

describe("placeAnnotationIcons", () => {
  describe("default position is preserved", () => {
    test("a box fully inside the image keeps the icons at the top-right corner", () => {
      const result = place({ x: 100, y: 50, width: 150, height: 80 });
      expect(result.corner).toBe("top-right");
      expect(result.style).toEqual({ top: 0, right: 0 });
      expect(DEFAULT_ICON_CORNER).toBe("top-right");
    });

    test("a box overflowing only the LEFT edge keeps the top-right corner", () => {
      const result = place({ x: -50, y: 50, width: 150, height: 80 });
      expect(result.corner).toBe("top-right");
    });

    test("a box overflowing only the BOTTOM edge keeps the top-right corner", () => {
      const result = place({ x: 100, y: 250, width: 150, height: 80 });
      expect(result.corner).toBe("top-right");
    });

    test("a tiny box (smaller than its own border) still gets a sane top-right placement", () => {
      const result = place({ x: 100, y: 50, width: 3, height: 3 });
      expect(result.corner).toBe("top-right");
      expect(result.style).toEqual({ top: 0, right: 0 });
    });
  });

  describe("unknown or invalid measurements fall back to the default (first paint)", () => {
    test.each([
      ["null container", null],
      ["zero container", { width: 0, height: 0 }],
      ["NaN container", { width: NaN, height: 200 }],
      ["negative container", { width: -10, height: 200 }],
    ])("%s", (_label, badContainer) => {
      const result = place(
        { x: 400, y: 50, width: 150, height: 80 },
        { container: badContainer },
      );
      expect(result.corner).toBe("top-right");
      expect(result.style).toEqual({ top: 0, right: 0 });
    });

    test("non-finite box coordinates fall back to the default", () => {
      const result = place({ x: NaN, y: 50, width: 150, height: 80 });
      expect(result.corner).toBe("top-right");
      expect(result.style).toEqual({ top: 0, right: 0 });
    });

    test("missing cluster size falls back to the default", () => {
      const result = place(
        { x: 400, y: 50, width: 150, height: 80 },
        { cluster: null },
      );
      expect(result.corner).toBe("top-right");
    });

    test("a container smaller than the icon cluster leaves the default alone", () => {
      const result = place(
        { x: 0, y: 0, width: 100, height: 100 },
        { container: { width: 15, height: 15 } },
      );
      expect(result.corner).toBe("top-right");
      expect(result.style).toEqual({ top: 0, right: 0 });
    });
  });

  describe("axis-aligned boxes move to the nearest visible corner", () => {
    test("overflowing the RIGHT edge moves the icons to the top-left corner", () => {
      const result = place({ x: 400, y: 50, width: 150, height: 80 });
      expect(result.corner).toBe("top-left");
      expect(result.style).toEqual({ top: 0, left: 0 });
    });

    test("overflowing the TOP edge moves the icons to the bottom-right corner", () => {
      const result = place({ x: 100, y: -30, width: 150, height: 80 });
      expect(result.corner).toBe("bottom-right");
      expect(result.style).toEqual({ bottom: 0, right: 0 });
    });

    test("overflowing TOP and RIGHT moves the icons to the bottom-left corner", () => {
      const result = place({ x: 400, y: -30, width: 150, height: 80 });
      expect(result.corner).toBe("bottom-left");
      expect(result.style).toEqual({ bottom: 0, left: 0 });
    });

    test("negative bounding-box coordinates are handled like any other overflow", () => {
      const result = place({ x: -10, y: -10, width: 100, height: 60 });
      expect(result.corner).toBe("bottom-right");
    });
  });

  describe("CSS box model: the cluster sits inside the border", () => {
    // Right edge of the box lands at x=501. With a 2px border the cluster's
    // right edge is at 499 (inside), with no border it would be at 501 (clipped).
    const box = { x: 351, y: 50, width: 150, height: 80 };

    test("with a 2px border the top-right cluster is still fully visible", () => {
      expect(place(box, { border: 2 }).corner).toBe("top-right");
    });

    test("with no border the same box would need the top-left corner", () => {
      expect(place(box, { border: 0 }).corner).toBe("top-left");
    });
  });

  describe("per-overlay inset (the other-encounter link uses right:-2)", () => {
    const inset = { x: -2, y: 0 };

    test("default placement keeps the -2 horizontal inset", () => {
      const result = place(
        { x: 350, y: 50, width: 150, height: 80 },
        { cluster: link, inset },
      );
      expect(result.corner).toBe("top-right");
      expect(result.style).toEqual({ top: 0, right: -2 });
    });

    test("the inset counts toward overflow, and is preserved on the relocated corner", () => {
      const result = place(
        { x: 351, y: 50, width: 150, height: 80 },
        { cluster: link, inset },
      );
      expect(result.corner).toBe("top-left");
      expect(result.style).toEqual({ top: 0, left: -2 });
    });
  });

  describe("the cluster footprint is per overlay, not a shared constant", () => {
    // Box top at y=270 of a 300px-tall image: a 18px-tall link fits at the top,
    // a 40px-tall edit/delete stack does not.
    const box = { x: 100, y: 270, width: 150, height: 80 };

    test("the single 18x18 link icon fits at the top-right", () => {
      expect(place(box, { cluster: link }).corner).toBe("top-right");
    });

    test("the 20x40 edit/delete stack cannot use any corner and is clamped instead", () => {
      const result = place(box, { cluster: editDelete });
      expect(result.corner).toBe("clamped");
      expectInside(
        clusterVerticesInContainer(
          { ...box, rotation: 0 },
          result.style,
          editDelete,
          2,
        ),
        container,
      );
    });
  });

  describe("no corner visible: clamp the cluster into the visible part of the box", () => {
    test("a box wider than the image puts the icons at the top-right of the visible strip", () => {
      const box = { x: -50, y: 10, width: 600, height: 100 };
      const result = place(box);
      expect(result.corner).toBe("clamped");
      // Padding box starts at x=-48; a cluster whose left edge is 528px into it
      // spans container x in [480, 500] — flush with the right edge of the image.
      expect(result.style).toEqual({ top: 0, left: 528 });
    });

    test("a box taller and wider than the image is clamped into the image on both axes", () => {
      const box = { x: -50, y: -40, width: 600, height: 400 };
      const result = place(box);
      expect(result.corner).toBe("clamped");
      const vertices = clusterVerticesInContainer(
        { ...box, rotation: 0 },
        result.style,
        editDelete,
        2,
      );
      expectInside(vertices, container);
      // Flush with the top-right of the image, not floating somewhere inside it.
      expect(Math.max(...vertices.map((v) => v.x))).toBeCloseTo(500, 5);
      expect(Math.min(...vertices.map((v) => v.y))).toBeCloseTo(0, 5);
    });
  });

  describe("rotated boxes are judged in container space but placed in box space", () => {
    const quarterTurn = Math.PI / 2;

    test("a box whose top pokes out but whose local top-right lands inside keeps top-right", () => {
      // Unrotated, the top-right corner would be off the top of the image. After a
      // 90° turn the local top-right corner is at the visible lower-right instead.
      const result = place({
        x: 100,
        y: -10,
        width: 200,
        height: 60,
        rotation: quarterTurn,
      });
      expect(result.corner).toBe("top-right");
      expect(result.style).toEqual({ top: 0, right: 0 });
    });

    test("a box whose local top-right lands off the bottom moves to a local corner that is visible", () => {
      const box = {
        x: 100,
        y: 200,
        width: 200,
        height: 60,
        rotation: quarterTurn,
      };
      const result = place(box);
      expect(result.corner).toBe("top-left");
      expect(result.style).toEqual({ top: 0, left: 0 });
    });

    test("an obliquely rotated box wider than the image is clamped with all four vertices inside", () => {
      const box = { x: -50, y: 100, width: 600, height: 60, rotation: 0.2 };
      const result = place(box);
      expect(result.corner).toBe("clamped");
      expect(Number.isFinite(result.style.top)).toBe(true);
      expect(Number.isFinite(result.style.left)).toBe(true);
      expectInside(
        clusterVerticesInContainer(box, result.style, editDelete, 2),
        container,
      );
    });

    test("a rotated cluster that cannot fit anywhere leaves the default alone", () => {
      const result = place(
        { x: 0, y: 0, width: 100, height: 100, rotation: 0.3 },
        { container: { width: 30, height: 30 } },
      );
      expect(result.corner).toBe("top-right");
    });
  });

  describe("edge precision", () => {
    test("a cluster clipped by a fraction of a pixel does not count as visible", () => {
      // The box's right edge lands at 502.4, so the cluster (inside the 2px
      // border) ends at 500.4 -- 0.4px past the image.
      const result = place({ x: 350.4, y: 50, width: 152, height: 80 });
      expect(result.corner).toBe("top-left");
    });

    test("floating-point noise at an exact edge is tolerated", () => {
      const result = place({ x: 352 + 1e-9, y: 50, width: 150, height: 80 });
      expect(result.corner).toBe("top-right");
    });

    test("a clamped anchor is exact: the cluster ends flush with the image edge", () => {
      const box = { x: -50.3, y: 10, width: 600.7, height: 100 };
      const result = place(box);
      expect(result.corner).toBe("clamped");
      const vertices = clusterVerticesInContainer(
        { ...box, rotation: 0 },
        result.style,
        editDelete,
        2,
      );
      expectInside(vertices, container);
      expect(Math.max(...vertices.map((v) => v.x))).toBeCloseTo(500, 9);
    });
  });
});
