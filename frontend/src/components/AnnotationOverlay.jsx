import {
  forwardRef,
  useEffect,
  useImperativeHandle,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import useWheelZoom from "../hooks/useWheelZoom";
import {
  annotationDisplayRect,
  computeFitToAnnotation,
  computeMaxZoom,
  computeZoomAboutPoint,
} from "../utils/annotationZoom";

const VISIBLE_MARGIN_PX = 40;
// Breathing room left around an auto-fitted annotation, as a fraction of the box.
const ANNOTATION_FIT_MARGIN = 0.1;
// How long after the last wheel notch the transform transition stays off. Long
// enough to bridge the gap between notches of one scroll gesture, short enough
// that the animation is back before the next deliberate interaction.
const WHEEL_SETTLE_MS = 200;

const InteractiveAnnotationOverlay = forwardRef(
  (
    {
      imageUrl,
      annotations = [],
      originalWidth = 0,
      originalHeight = 0,
      rotationInfo = null,
      initialZoom = 1,
      minZoom = 1,
      // Floor for the zoom ceiling, not the ceiling itself: the real limit is
      // derived from the served image's own resolution, so a 4096px _master can
      // be inspected down to its native pixels instead of stopping at 3x.
      maxZoom = 3,
      zoomFactor = 1.25,
      fitToAnnotation = false,
      showAnnotations: showAnnotationsProp,
      strokeColor = "red",
      lineWidth = 2,
      containerStyle = {},
      imageStyle = {},
      overlayStyle = {},
      loadingText = "Loading image...",
      loadingOverlayStyle = {},
      alt = "Image with annotations",
    },
    ref,
  ) => {
    const outerContainerRef = useRef(null);
    const imgRef = useRef(null);

    // Zoom and pan are one piece of state, not two. Every zoom step has to move
    // the pan to keep the focal point still, and a single functional update is
    // the only way to derive both from the same previous view -- two chained
    // setState calls would compute the new pan from a zoom that may already
    // have moved on, and a setPan nested inside a setZoom updater is an impure
    // updater that StrictMode double-invokes.
    const [view, setView] = useState(() => ({
      zoom: Number.isFinite(initialZoom) ? initialZoom : 1,
      pan: { x: 0, y: 0 },
    }));
    const { zoom, pan } = view;
    const [dragging, setDragging] = useState(false);
    // While a wheel gesture is in flight the transform transition is off: the
    // focal math is computed from committed React state, so animating toward it
    // would leave the point under the cursor lagging behind the cursor itself.
    const [wheelZooming, setWheelZooming] = useState(false);
    const wheelSettleRef = useRef(null);
    const dragStartRef = useRef({ x: 0, y: 0 });
    const panStartRef = useRef({ x: 0, y: 0 });
    const [scaleX, setScaleX] = useState(1);
    const [scaleY, setScaleY] = useState(1);
    const [imageLoaded, setImageLoaded] = useState(false);
    // Pixel width of the image we were actually served, and the width it is drawn
    // at -- together these say how much detail is left to zoom into. Stamped with
    // the url they were measured from: scaleX/scaleY are set in the same batch, so
    // a matching url means every measurement below belongs to the current image.
    const [sourceSize, setSourceSize] = useState({
      natural: 0,
      display: 0,
      url: null,
    });

    const [internalShowAnn, setInternalShowAnn] = useState(true);
    const showAnn =
      typeof showAnnotationsProp === "boolean"
        ? showAnnotationsProp
        : internalShowAnn;

    const hasRotation = !!rotationInfo;

    // A new image starts from the default view -- otherwise the zoom chosen for the
    // previous prospect carries over onto the next one. Keyed on imageUrl alone, so a
    // metadata-only change to the dimensions does not throw away the user's zoom, and
    // run as a layout effect so a cached replacement cannot paint at the old transform
    // first. The auto-fit effect reframes once the new image has loaded.
    useLayoutEffect(() => {
      setView((prev) => {
        const nextZoom = Number.isFinite(initialZoom) ? initialZoom : 1;

        if (prev.zoom === nextZoom && prev.pan.x === 0 && prev.pan.y === 0) {
          return prev;
        }
        return { zoom: nextZoom, pan: { x: 0, y: 0 } };
      });
      // A wheel gesture on the outgoing image must not swallow the incoming
      // one's reset/auto-fit animation.
      if (wheelSettleRef.current) clearTimeout(wheelSettleRef.current);
      setWheelZooming(false);
      // initialZoom is the default view, not a trigger: changing it alone should not
      // yank the image out from under the user.
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [imageUrl]);

    useEffect(() => {
      if (!imgRef.current) return;

      setImageLoaded(false);

      const handleImageLoad = () => {
        if (imgRef.current) {
          const naturalWidth = Number(originalWidth);
          const naturalHeight = Number(originalHeight);
          const displayWidth = imgRef.current.clientWidth;
          const displayHeight = imgRef.current.clientHeight;

          if (naturalWidth && naturalHeight && displayWidth && displayHeight) {
            setScaleX(naturalWidth / displayWidth);
            setScaleY(naturalHeight / displayHeight);
          } else {
            setScaleX(1);
            setScaleY(1);
          }

          setSourceSize({
            natural: imgRef.current.naturalWidth,
            display: displayWidth,
            url: imageUrl,
          });
          setImageLoaded(true);
        }
      };

      const imgElement = imgRef.current;

      if (imgElement && imgElement.complete && imgElement.naturalWidth > 0) {
        handleImageLoad();
      } else if (imgElement) {
        imgElement.addEventListener("load", handleImageLoad);
      }

      return () => {
        if (imgElement) {
          imgElement.removeEventListener("load", handleImageLoad);
        }
      };
    }, [originalWidth, originalHeight, imageUrl]);

    const canRenderAnnotations = useMemo(() => {
      return (
        imageLoaded &&
        showAnn &&
        Number.isFinite(scaleX) &&
        Number.isFinite(scaleY) &&
        scaleX > 0 &&
        scaleY > 0
      );
    }, [imageLoaded, showAnn, scaleX, scaleY]);

    const visibleAnnotations = useMemo(() => {
      if (!Array.isArray(annotations)) return [];

      const isFiniteNum = (v) => Number.isFinite(Number(v));

      return annotations
        .filter((a) => a && !a.trivial && !a.isTrivial)
        .filter((a) => {
          const x = Number(a.x);
          const y = Number(a.y);
          const w = Number(a.width);
          const h = Number(a.height);

          if (![x, y, w, h].every(isFiniteNum)) return false;
          if (w <= 0 || h <= 0) return false;

          return true;
        });
    }, [annotations]);

    const effectiveMaxZoom = useMemo(
      () =>
        computeMaxZoom({
          naturalWidth: sourceSize.natural,
          displayWidth: sourceSize.display,
          floor: maxZoom,
        }),
      [sourceSize, maxZoom],
    );

    // The imperative handle is built once, so it must read the ceiling through a
    // ref -- closing over effectiveMaxZoom would pin it to the pre-load value.
    const maxZoomRef = useRef(effectiveMaxZoom);
    useLayoutEffect(() => {
      maxZoomRef.current = effectiveMaxZoom;
    }, [effectiveMaxZoom]);

    const clampZoom = (z) => Math.max(minZoom, Math.min(maxZoomRef.current, z));

    const clampPan = (nextPan, nextZoom) => {
      const container = outerContainerRef.current;
      const img = imgRef.current;

      if (!container || !img) return nextPan;

      const containerWidth = container.clientWidth;
      const containerHeight = container.clientHeight;
      const imageWidth = img.clientWidth * nextZoom;
      const imageHeight = img.clientHeight * nextZoom;

      const visibleMarginX = Math.min(VISIBLE_MARGIN_PX, containerWidth);
      const visibleMarginY = Math.min(VISIBLE_MARGIN_PX, containerHeight);

      const minX = visibleMarginX - imageWidth;
      const maxX = containerWidth - visibleMarginX;
      const minY = visibleMarginY - imageHeight;
      const maxY = containerHeight - visibleMarginY;

      return {
        x: Math.max(minX, Math.min(maxX, nextPan.x)),
        y: Math.max(minY, Math.min(maxY, nextPan.y)),
      };
    };

    // Where a screen point sits in the transformed wrapper's own unscaled
    // coordinate frame -- the frame `pan` is expressed in. Measured from the
    // container, whose box is never transformed; the wrapper's own bounding rect
    // reports an interpolated position mid-transition rather than committed pan.
    const focalFromClient = (clientX, clientY) => {
      const container = outerContainerRef.current;

      if (!container) return null;
      if (!Number.isFinite(clientX) || !Number.isFinite(clientY)) return null;

      // getBoundingClientRect gives the border box. Step in past the border
      // (clientLeft/clientTop) and then the padding to reach the content box,
      // where the wrapper -- a static, in-flow, marginless child -- has its
      // layout origin. Taking the padding from the container itself, rather than
      // the wrapper's offsetLeft, stays correct even if a caller's containerStyle
      // repositions the container out of the wrapper's offsetParent chain.
      const rect = container.getBoundingClientRect();
      const padding = window.getComputedStyle(container);
      // Minus any scroll offset: the container is overflow:hidden by default, but
      // it can still be scrolled programmatically (and containerStyle can make it
      // scrollable), which moves the wrapper without moving the container's rect.
      const originX =
        rect.left +
        container.clientLeft +
        (parseFloat(padding.paddingLeft) || 0) -
        container.scrollLeft;
      const originY =
        rect.top +
        container.clientTop +
        (parseFloat(padding.paddingTop) || 0) -
        container.scrollTop;

      return { x: clientX - originX, y: clientY - originY };
    };

    // Middle of the visible pane: the anchor for the toolbar zoom buttons, which
    // have no cursor to zoom toward. Matches the legacy OpenSeadragon viewer.
    const paneCenterFocal = () => {
      const container = outerContainerRef.current;

      if (!container) return null;
      const rect = container.getBoundingClientRect();

      return focalFromClient(
        rect.left + container.clientLeft + container.clientWidth / 2,
        rect.top + container.clientTop + container.clientHeight / 2,
      );
    };

    // One zoom step, anchored so `focal` stays under the same screen pixel.
    // `focal` is measured before the updater runs, so the updater derives the
    // whole next view from `prev` and a StrictMode double-invocation cannot
    // apply the shift twice. (clampPan reads layout, but read-only.)
    const zoomAbout = (direction, focal) => {
      setView((prev) => {
        const nextZoom = clampZoom(
          direction > 0 ? prev.zoom * zoomFactor : prev.zoom / zoomFactor,
        );

        // Already at the ceiling or the floor: leave the view exactly as it is
        // rather than nudging the pan for a zoom that did not happen.
        if (nextZoom === prev.zoom) return prev;

        // Zooming all the way out means "show me the whole photo", so drop the
        // pan rather than leaving the image parked off to one side.
        if (nextZoom <= minZoom) return { zoom: nextZoom, pan: { x: 0, y: 0 } };

        const anchored = focal
          ? computeZoomAboutPoint({
              pan: prev.pan,
              zoom: prev.zoom,
              nextZoom,
              focal,
            })
          : prev.pan;

        // Clamping wins over anchoring: near the edges the focal point does move,
        // but the image cannot be zoomed out of the pane.
        return { zoom: nextZoom, pan: clampPan(anchored, nextZoom) };
      });
    };

    const stateRef = useRef({ zoom, pan, showAnn, imageLoaded });
    useEffect(() => {
      stateRef.current = { zoom, pan, showAnn, imageLoaded };
    }, [zoom, pan, showAnn, imageLoaded]);

    // The single annotation worth framing. More than one and there is no
    // obvious subject, so we leave the whole image showing.
    const fitTarget = useMemo(
      () => (visibleAnnotations.length === 1 ? visibleAnnotations[0] : null),
      [visibleAnnotations],
    );

    // Zoom and pan to the annotation, the way the legacy OpenSeadragon viewer
    // did with viewport.fitBounds(). Returns false if there is nothing to fit.
    const fitAnnotationView = () => {
      const container = outerContainerRef.current;

      if (!container || !fitTarget) return false;
      const rect = annotationDisplayRect(fitTarget, {
        scaleX,
        scaleY,
        originalWidth,
        originalHeight,
        hasRotation,
      });
      const fit = computeFitToAnnotation({
        rect,
        containerWidth: container.clientWidth,
        containerHeight: container.clientHeight,
        minZoom,
        maxZoom: maxZoomRef.current,
        margin: ANNOTATION_FIT_MARGIN,
      });

      if (!fit) return false;
      setView({ zoom: fit.zoom, pan: clampPan(fit.pan, fit.zoom) });
      return true;
    };

    // The whole photo at the default zoom, panned back to the top-left.
    const fitImageView = () => {
      const nextZoom = clampZoom(initialZoom || 1);

      setView({ zoom: nextZoom, pan: clampPan({ x: 0, y: 0 }, nextZoom) });
    };

    // Same reason as maxZoomRef: the imperative handle is built once with empty
    // deps, so everything it calls has to be reached through a ref that holds
    // the current render's closure (current props, current clamps).
    const fitRef = useRef(() => false);
    const fitImageRef = useRef(() => {});
    const zoomAboutRef = useRef(() => {});
    const showAnnPropRef = useRef(showAnnotationsProp);
    useLayoutEffect(() => {
      fitRef.current = fitAnnotationView;
      fitImageRef.current = fitImageView;
      zoomAboutRef.current = zoomAbout;
      showAnnPropRef.current = showAnnotationsProp;
    });

    const fitSignature = useMemo(() => {
      if (!fitToAnnotation || !fitTarget) return null;
      return [
        fitTarget.id,
        fitTarget.x,
        fitTarget.y,
        fitTarget.width,
        fitTarget.height,
        fitTarget.theta,
      ].join("|");
    }, [fitToAnnotation, fitTarget]);

    useEffect(() => {
      if (!imageLoaded || !fitSignature) return;
      // A cached image re-runs this effect on the url change before the new
      // measurements are committed; fitting then would use the previous image's
      // scale. Wait for the measurements to catch up -- they will, next render.
      if (sourceSize.url !== imageUrl) return;
      fitRef.current();
      // fitRef always holds the latest closure; re-running on its identity would loop.
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [
      imageLoaded,
      imageUrl,
      fitSignature,
      effectiveMaxZoom,
      scaleX,
      scaleY,
      sourceSize.url,
      // annotationDisplayRect draws the box in a different frame when the asset
      // carries rotation, and minZoom is the fit's floor: if either arrives after
      // the image, the framing has to be recomputed.
      hasRotation,
      minZoom,
    ]);

    useImperativeHandle(ref, () => ({
      // The buttons have no cursor to zoom toward, so they anchor the middle of
      // the pane -- whatever the user centred stays centred.
      zoomIn: () => zoomAboutRef.current(1, paneCenterFocal()),
      zoomOut: () => zoomAboutRef.current(-1, paneCenterFocal()),
      // The default view: the annotation when auto-fit is on, else whole image.
      reset: () => {
        if (fitRef.current()) return;
        fitImageRef.current();
      },
      fitImage: () => fitImageRef.current(),
      fitAnnotation: () => fitRef.current(),
      toggleAnnotations: () => {
        if (typeof showAnnPropRef.current === "boolean") return;
        setInternalShowAnn((v) => !v);
      },
      setAnnotationsVisible: (v) => {
        if (typeof showAnnPropRef.current === "boolean") return;
        setInternalShowAnn(!!v);
      },
      getState: () => stateRef.current,
    }), []);

    const onMouseDown = (e) => {
      if (!imageLoaded) return;

      setDragging(true);
      dragStartRef.current = { x: e.clientX, y: e.clientY };
      panStartRef.current = { ...pan };
    };

    useEffect(() => {
      if (!dragging) return;

      const onMove = (e) => {
        const dx = e.clientX - dragStartRef.current.x;
        const dy = e.clientY - dragStartRef.current.y;

        const nextPan = {
          x: panStartRef.current.x + dx,
          y: panStartRef.current.y + dy,
        };

        setView((prev) => ({ ...prev, pan: clampPan(nextPan, prev.zoom) }));
      };

      const onUp = () => setDragging(false);

      window.addEventListener("mousemove", onMove);
      window.addEventListener("mouseup", onUp);

      return () => {
        window.removeEventListener("mousemove", onMove);
        window.removeEventListener("mouseup", onUp);
      };
    }, [dragging]);

    useEffect(() => {
      const img = imgRef.current;
      if (!img) return;

      const handleLoad = () => {
        setView((prev) => ({ ...prev, pan: clampPan(prev.pan, prev.zoom) }));
      };

      if (img.complete && img.naturalWidth > 0) {
        handleLoad();
      } else {
        img.addEventListener("load", handleLoad);
        return () => img.removeEventListener("load", handleLoad);
      }
    }, [imageUrl]);

    useEffect(() => {
      const handleResize = () => {
        setView((prev) => ({ ...prev, pan: clampPan(prev.pan, prev.zoom) }));
      };

      window.addEventListener("resize", handleResize);
      return () => {
        window.removeEventListener("resize", handleResize);
      };
    }, []);

    useEffect(
      () => () => {
        if (wheelSettleRef.current) clearTimeout(wheelSettleRef.current);
      },
      [],
    );

    // Mouse-wheel zoom, anchored on the cursor.
    const handleWheelZoom = (direction, event) => {
      setWheelZooming(true);
      if (wheelSettleRef.current) clearTimeout(wheelSettleRef.current);
      wheelSettleRef.current = setTimeout(
        () => setWheelZooming(false),
        WHEEL_SETTLE_MS,
      );

      zoomAbout(direction, focalFromClient(event?.clientX, event?.clientY));
    };
    // Only once the measurements belong to the image currently on screen: on an
    // image change imageLoaded stays true until the load effect runs, and a wheel
    // event landing in that window would zoom against the outgoing image.
    useWheelZoom(
      outerContainerRef,
      handleWheelZoom,
      imageLoaded && sourceSize.url === imageUrl,
    );

    const panZoomTransform = `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`;

    return (
      <div
        ref={outerContainerRef}
        style={{
          position: "relative",
          width: "100%",
          overflow: "hidden",
          cursor: !imageLoaded ? "default" : dragging ? "grabbing" : "grab",
          ...containerStyle,
        }}
        onMouseDown={onMouseDown}
      >
        <div
          style={{
            position: "relative",
            width: "100%",
            transform: panZoomTransform,
            transformOrigin: "top left",
            transition:
              dragging || wheelZooming ? "none" : "transform 0.15s ease",
          }}
        >
          <img
            ref={imgRef}
            src={imageUrl || ""}
            alt={alt}
            draggable={false}
            style={{
              width: "100%",
              height: "auto",
              display: "block",
              userSelect: "none",
              ...imageStyle,
            }}
          />

          {!imageLoaded && (
            <div
              style={{
                position: "absolute",
                inset: 0,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                background: "rgba(255, 255, 255, 0.6)",
                zIndex: 1,
                pointerEvents: "none",
                fontSize: 14,
                ...loadingOverlayStyle,
              }}
            >
              {loadingText}
            </div>
          )}

          {canRenderAnnotations && (
            <div
              style={{
                position: "absolute",
                top: 0,
                left: 0,
                right: 0,
                bottom: 0,
                pointerEvents: "none",
                ...overlayStyle,
              }}
            >
              {visibleAnnotations.map((a, idx) => {
                // Same helper the auto-fit uses, so the drawn box and the view
                // it zooms to can never disagree.
                const rect = annotationDisplayRect(a, {
                  scaleX,
                  scaleY,
                  originalWidth,
                  originalHeight,
                  hasRotation,
                });

                if (!rect) return null;

                const key =
                  a.id ??
                  a.annotationId ??
                  `${idx}-${Number(a.x)}-${Number(a.y)}-${Number(a.width)}-${Number(a.height)}`;

                return (
                  <div
                    key={key}
                    style={{
                      position: "absolute",
                      left: rect.x,
                      top: rect.y,
                      width: rect.width,
                      height: rect.height,
                      border: `${lineWidth}px solid ${strokeColor}`,
                      boxSizing: "border-box",
                      transform: rect.rotation
                        ? `rotate(${(rect.rotation * 180) / Math.PI}deg)`
                        : undefined,
                      transformOrigin: "center",
                    }}
                  />
                );
              })}
            </div>
          )}
        </div>
      </div>
    );
  },
);

InteractiveAnnotationOverlay.displayName = "InteractiveAnnotationOverlay";
export default InteractiveAnnotationOverlay;
