/**
 * Where to put an annotation's icon cluster (edit/delete, or the go-to-encounter
 * link) so it stays reachable when the bounding box runs off the image.
 *
 * The cluster is an absolutely-positioned child of the annotation box, so it
 * rotates with the box. Visibility is judged in the image container's frame
 * (where `overflow: hidden` clips), but the answer is expressed as CSS insets in
 * the box's own frame -- which is what makes it correct for rotated boxes too.
 *
 * Coordinates: `box` is in displayed px in the container's frame, `rotation` in
 * radians about the box centre (CSS `transform-origin: center`). Bootstrap's
 * `box-sizing: border-box` means width/height include the border, while
 * absolutely-positioned children are laid out against the padding box (inset by
 * `border` on every side). Every footprint below accounts for that.
 */

export const DEFAULT_ICON_CORNER = "top-right";

// Tried in this order; the first corner whose whole cluster is visible wins.
// The default comes first so nothing moves unless it has to.
export const ICON_CORNER_ORDER = [
  "top-right",
  "top-left",
  "bottom-right",
  "bottom-left",
];

// Guards floating-point noise only (a box that exactly spans the image can
// compute its edge as 500.0000001). Anything genuinely clipped, even by a
// fraction of a pixel, does not count as visible.
const FLOAT_EPSILON = 1e-6;

const isPositive = (value) => Number.isFinite(value) && value > 0;
const toNumber = (value) => (Number.isFinite(value) ? value : 0);
const clamp = (value, lo, hi) => Math.min(Math.max(value, lo), hi);

// CSS `rotate(theta)` acting on a vector (screen coordinates, y down).
const rotateVector = (dx, dy, theta) => {
  const cos = Math.cos(theta);
  const sin = Math.sin(theta);
  return { x: dx * cos - dy * sin, y: dx * sin + dy * cos };
};

const cornerStyle = (corner, insetX, insetY) => {
  const vertical = corner.startsWith("top")
    ? { top: insetY }
    : { bottom: insetY };
  const horizontal = corner.endsWith("right")
    ? { right: insetX }
    : { left: insetX };
  return { ...vertical, ...horizontal };
};

const defaultPlacement = (insetX, insetY) => ({
  corner: DEFAULT_ICON_CORNER,
  style: cornerStyle(DEFAULT_ICON_CORNER, insetX, insetY),
});

/**
 * @param {object} args
 * @param {{x:number,y:number,width:number,height:number,rotation?:number}} args.box
 *   The annotation box in displayed px (container frame), rotation in radians.
 * @param {{width:number,height:number}|null} args.container
 *   The clipping element's client size. Unknown (first paint) -> default placement.
 * @param {{width:number,height:number}} args.cluster
 *   Footprint of the icon cluster being placed (per overlay, not shared).
 * @param {number} [args.border=2] The box's CSS border width in px.
 * @param {{x?:number,y?:number}} [args.inset] CSS inset the overlay uses on its
 *   corner (e.g. the other-encounter link's `right: -2`); preserved on relocation.
 * @returns {{corner: string, style: object}} `style` is spread onto the cluster
 *   element in place of the old `top: 0, right: 0`.
 */
export function placeAnnotationIcons({
  box,
  container,
  cluster,
  border = 2,
  inset,
} = {}) {
  const insetX = toNumber(inset?.x);
  const insetY = toNumber(inset?.y);
  const fallback = defaultPlacement(insetX, insetY);

  if (!box || !cluster) return fallback;
  const { x, y } = box;
  const rotation = toNumber(box.rotation);
  if (![x, y, box.width, box.height].every(Number.isFinite)) return fallback;
  if (!isPositive(box.width) || !isPositive(box.height)) return fallback;

  const containerWidth = container?.width;
  const containerHeight = container?.height;
  if (!isPositive(containerWidth) || !isPositive(containerHeight)) {
    return fallback;
  }

  const clusterWidth = cluster.width;
  const clusterHeight = cluster.height;
  if (!isPositive(clusterWidth) || !isPositive(clusterHeight)) return fallback;

  const b = Math.max(0, toNumber(border));
  // A border-box element never renders smaller than its own borders.
  const w = Math.max(box.width, 2 * b);
  const h = Math.max(box.height, 2 * b);
  const cx = w / 2;
  const cy = h / 2;

  // Top-left of the cluster's footprint in the box's border-box frame, per corner.
  const anchors = {
    "top-right": { x: w - b - insetX - clusterWidth, y: b + insetY },
    "top-left": { x: b + insetX, y: b + insetY },
    "bottom-right": {
      x: w - b - insetX - clusterWidth,
      y: h - b - insetY - clusterHeight,
    },
    "bottom-left": { x: b + insetX, y: h - b - insetY - clusterHeight },
  };

  const toContainer = (px, py) => {
    const r = rotateVector(px - cx, py - cy, rotation);
    return { x: x + cx + r.x, y: y + cy + r.y };
  };
  const isInside = (p) =>
    p.x >= -FLOAT_EPSILON &&
    p.x <= containerWidth + FLOAT_EPSILON &&
    p.y >= -FLOAT_EPSILON &&
    p.y <= containerHeight + FLOAT_EPSILON;
  const footprint = (anchor) =>
    [
      [anchor.x, anchor.y],
      [anchor.x + clusterWidth, anchor.y],
      [anchor.x, anchor.y + clusterHeight],
      [anchor.x + clusterWidth, anchor.y + clusterHeight],
    ].map(([px, py]) => toContainer(px, py));

  for (const corner of ICON_CORNER_ORDER) {
    if (footprint(anchors[corner]).every(isInside)) {
      return { corner, style: cornerStyle(corner, insetX, insetY) };
    }
  }

  // No corner is fully visible: the box is bigger than the image in some
  // direction. Slide the default cluster along the box until all four of its
  // (rotated) vertices sit inside the container, then express that anchor back
  // in the box's frame so it still rotates with the box.
  const rotatedOffsets = [
    [0, 0],
    [clusterWidth, 0],
    [0, clusterHeight],
    [clusterWidth, clusterHeight],
  ].map(([dx, dy]) => rotateVector(dx, dy, rotation));
  const xs = rotatedOffsets.map((o) => o.x);
  const ys = rotatedOffsets.map((o) => o.y);
  const lo = { x: -Math.min(...xs), y: -Math.min(...ys) };
  const hi = {
    x: containerWidth - Math.max(...xs),
    y: containerHeight - Math.max(...ys),
  };
  if (lo.x > hi.x + FLOAT_EPSILON || lo.y > hi.y + FLOAT_EPSILON) {
    // The cluster itself is larger than the image; nothing can make it fit.
    return fallback;
  }

  const preferred = toContainer(anchors["top-right"].x, anchors["top-right"].y);
  const anchorInContainer = {
    x: clamp(preferred.x, lo.x, hi.x),
    y: clamp(preferred.y, lo.y, hi.y),
  };
  const local = rotateVector(
    anchorInContainer.x - x - cx,
    anchorInContainer.y - y - cy,
    -rotation,
  );
  return {
    corner: "clamped",
    style: {
      top: cy + local.y - b,
      left: cx + local.x - b,
    },
  };
}
