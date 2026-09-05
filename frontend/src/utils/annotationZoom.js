/**
 * Zoom/pan math for the annotation overlay used by the match-results page.
 *
 * Kept as pure functions so the render path and the auto-fit path share one
 * definition of where an annotation lands on screen -- if those two ever
 * disagree, the box and the zoom drift apart.
 */

// OpenSeadragon's maxZoomPixelRatio, which is what the legacy iaResults viewer
// allowed: zoom until one source pixel covers ~1.1 screen pixels, and no further
// (past that you are magnifying blur, not revealing detail).
export const NATIVE_PIXEL_RATIO = 1.1;

const isPositiveNumber = (v) => Number.isFinite(v) && v > 0;

/**
 * Zoom ceiling for an image, derived from how much resolution it actually has.
 *
 * `naturalWidth` is the served image's own pixel width, so a 4096px `_master`
 * earns a much higher ceiling than a 1024px `_mid` of the same photo.
 */
export function computeMaxZoom({
  naturalWidth,
  displayWidth,
  floor = 3,
  pixelRatio = NATIVE_PIXEL_RATIO,
}) {
  const natural = Number(naturalWidth);
  const display = Number(displayWidth);

  if (!isPositiveNumber(natural) || !isPositiveNumber(display)) return floor;
  return Math.max(floor, (natural / display) * pixelRatio);
}

/**
 * Where an annotation's bounding box lands in *displayed* pixels.
 *
 * Bounding boxes arrive in the source asset's pixel frame; `scaleX`/`scaleY`
 * are that frame divided by the on-screen size. Assets carrying rotation info
 * need the extra aspect adjustment. Returns null for anything unusable --
 * a trivial whole-image annotation, or a box with no area.
 */
export function annotationDisplayRect(
  annotation,
  { scaleX, scaleY, originalWidth, originalHeight, hasRotation = false } = {},
) {
  if (!annotation) return null;
  if (annotation.trivial || annotation.isTrivial) return null;

  const sx = Number(scaleX);
  const sy = Number(scaleY);

  if (!isPositiveNumber(sx) || !isPositiveNumber(sy)) return null;

  const x = Number(annotation.x);
  const y = Number(annotation.y);
  const width = Number(annotation.width);
  const height = Number(annotation.height);

  if (![x, y].every(Number.isFinite)) return null;
  if (!isPositiveNumber(width) || !isPositiveNumber(height)) return null;

  let rect;
  if (hasRotation) {
    const imgW = Number(originalWidth);
    const imgH = Number(originalHeight);

    if (!isPositiveNumber(imgW) || !isPositiveNumber(imgH)) return null;
    const adjW = imgH / imgW;
    const adjH = imgW / imgH;

    rect = {
      x: x / sx / adjW,
      y: y / sy / adjH,
      width: width / sx / adjW,
      height: height / sy / adjH,
      rotation: Number(annotation.theta || 0),
    };
  } else {
    rect = {
      x: x / sx,
      y: y / sy,
      width: width / sx,
      height: height / sy,
      rotation: Number(annotation.theta || 0),
    };
  }

  if (!isPositiveNumber(rect.width) || !isPositiveNumber(rect.height)) return null;
  return rect;
}

/**
 * Zoom and pan that put `rect` in the middle of the pane at the largest size
 * that still fits, with `margin` (a fraction of the box) of breathing room --
 * the React equivalent of the legacy viewer's `viewport.fitBounds()`.
 *
 * Pan is in the same frame as the overlay's transform: `translate(pan) scale(zoom)`
 * about a top-left origin.
 */
export function computeFitToAnnotation({
  rect,
  containerWidth,
  containerHeight,
  minZoom = 1,
  maxZoom = 3,
  margin = 0.1,
}) {
  if (!rect) return null;
  const cw = Number(containerWidth);
  const ch = Number(containerHeight);

  if (!isPositiveNumber(cw) || !isPositiveNumber(ch)) return null;
  if (!isPositiveNumber(rect.width) || !isPositiveNumber(rect.height)) return null;

  const padded = 1 + Math.max(0, margin);
  const fit = Math.min(cw / (rect.width * padded), ch / (rect.height * padded));
  const zoom = Math.max(minZoom, Math.min(maxZoom, fit));

  return {
    zoom,
    pan: {
      x: cw / 2 - (rect.x + rect.width / 2) * zoom,
      y: ch / 2 - (rect.y + rect.height / 2) * zoom,
    },
  };
}
