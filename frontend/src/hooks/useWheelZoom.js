import { useEffect, useRef } from "react";

/**
 * Attaches a native, non-passive "wheel" listener to targetRef.current so that
 * mouse-wheel scrolling zooms the image instead of scrolling the page. A native
 * listener (rather than React's synthetic onWheel) is required because React
 * registers wheel handlers passively, which cannot call preventDefault().
 *
 * The hook does not own any zoom state; the caller supplies onZoom and keeps its
 * existing zoom/pan/clamp semantics.
 *
 * @param {React.RefObject} targetRef element the listener is attached to
 * @param {(direction: number, event: WheelEvent) => void} onZoom called with
 *        direction = +1 to zoom in (wheel up / deltaY < 0) and -1 to zoom out
 *        (wheel down / deltaY > 0)
 * @param {boolean} [enabled=true] when false, no listener is attached
 */
export default function useWheelZoom(targetRef, onZoom, enabled = true) {
  // Keep the latest callback in a ref so the listener is not re-subscribed on
  // every render (and callers do not need to memoize onZoom).
  const onZoomRef = useRef(onZoom);
  useEffect(() => {
    onZoomRef.current = onZoom;
  }, [onZoom]);

  useEffect(() => {
    const el = targetRef.current;
    if (!el || !enabled) return undefined;

    const handleWheel = (e) => {
      if (e.deltaY === 0) return;
      e.preventDefault();
      onZoomRef.current(e.deltaY < 0 ? 1 : -1, e);
    };

    el.addEventListener("wheel", handleWheel, { passive: false });
    return () => el.removeEventListener("wheel", handleWheel);
  }, [targetRef, enabled]);
}
