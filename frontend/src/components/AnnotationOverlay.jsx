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
} from "../utils/annotationZoom";

const VISIBLE_MARGIN_PX = 40;
// Breathing room left around an auto-fitted annotation, as a fraction of the box.
const ANNOTATION_FIT_MARGIN = 0.1;

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

    const [zoom, setZoom] = useState(
      Number.isFinite(initialZoom) ? initialZoom : 1,
    );
    const [pan, setPan] = useState({ x: 0, y: 0 });
    const [dragging, setDragging] = useState(false);
    const dragStartRef = useRef({ x: 0, y: 0 });
    const panStartRef = useRef({ x: 0, y: 0 });
    const [scaleX, setScaleX] = useState(1);
    const [scaleY, setScaleY] = useState(1);
    const [imageLoaded, setImageLoaded] = useState(false);
    // Pixel width of the image we were actually served, and the width it is
    // drawn at -- together these say how much detail is left to zoom into.
    const [sourceSize, setSourceSize] = useState({ natural: 0, display: 0 });

    const [internalShowAnn, setInternalShowAnn] = useState(true);
    const showAnn =
      typeof showAnnotationsProp === "boolean"
        ? showAnnotationsProp
        : internalShowAnn;

    const hasRotation = !!rotationInfo;

    useEffect(() => {
      if (!imgRef.current) return;

      setImageLoaded(false);
      // A new image starts from the default view -- otherwise the zoom chosen for
      // the previous prospect carries over onto the next one. The auto-fit effect
      // reframes once this image has loaded and its scale is known.
      setZoom(Number.isFinite(initialZoom) ? initialZoom : 1);
      setPan({ x: 0, y: 0 });

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

    const clampPan = (nextPan, nextZoom = zoom) => {
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
      setZoom(fit.zoom);
      setPan(clampPan(fit.pan, fit.zoom));
      return true;
    };

    // Same reason as maxZoomRef: the imperative handle needs the current closure,
    // and it must be in place before a parent effect can call reset().
    const fitRef = useRef(() => false);
    useLayoutEffect(() => {
      fitRef.current = fitAnnotationView;
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
      fitRef.current();
      // fitRef always holds the latest closure; re-running on its identity would loop.
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [imageLoaded, imageUrl, fitSignature, effectiveMaxZoom, scaleX, scaleY]);

    useImperativeHandle(ref, () => ({
      zoomIn: () => {
        setZoom((z) => {
          const nextZoom = clampZoom(z * zoomFactor);
          setPan((prev) => clampPan(prev, nextZoom));
          return nextZoom;
        });
      },
      zoomOut: () => {
        setZoom((z) => {
          const nextZoom = clampZoom(z / zoomFactor);
          // Zooming all the way out means "show me the whole photo", so drop the
          // pan rather than leaving the image parked off to one side.
          setPan((prev) =>
            nextZoom <= minZoom ? { x: 0, y: 0 } : clampPan(prev, nextZoom),
          );
          return nextZoom;
        });
      },
      // The default view: the annotation when auto-fit is on, else whole image.
      reset: () => {
        if (fitRef.current()) return;
        const nextZoom = clampZoom(initialZoom || 1);
        setZoom(nextZoom);
        setPan(clampPan({ x: 0, y: 0 }, nextZoom));
      },
      fitImage: () => {
        const nextZoom = clampZoom(initialZoom || 1);
        setZoom(nextZoom);
        setPan(clampPan({ x: 0, y: 0 }, nextZoom));
      },
      fitAnnotation: () => fitRef.current(),
      toggleAnnotations: () => {
        if (typeof showAnnotationsProp === "boolean") return;
        setInternalShowAnn((v) => !v);
      },
      setAnnotationsVisible: (v) => {
        if (typeof showAnnotationsProp === "boolean") return;
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

        setPan(clampPan(nextPan));
      };

      const onUp = () => setDragging(false);

      window.addEventListener("mousemove", onMove);
      window.addEventListener("mouseup", onUp);

      return () => {
        window.removeEventListener("mousemove", onMove);
        window.removeEventListener("mouseup", onUp);
      };
    }, [dragging, zoom]);

    useEffect(() => {
      const img = imgRef.current;
      if (!img) return;

      const handleLoad = () => {
        setPan((prev) => clampPan(prev, zoom));
      };

      if (img.complete && img.naturalWidth > 0) {
        handleLoad();
      } else {
        img.addEventListener("load", handleLoad);
        return () => img.removeEventListener("load", handleLoad);
      }
    }, [imageUrl, zoom]);

    useEffect(() => {
      const handleResize = () => {
        setPan((prev) => clampPan(prev, zoom));
      };

      window.addEventListener("resize", handleResize);
      return () => {
        window.removeEventListener("resize", handleResize);
      };
    }, [zoom]);

    // Mouse-wheel zoom mirrors the zoomIn/zoomOut imperative-handle behavior.
    const handleWheelZoom = (direction) => {
      setZoom((z) => {
        const nextZoom = clampZoom(
          direction > 0 ? z * zoomFactor : z / zoomFactor,
        );
        setPan((prev) =>
          nextZoom <= minZoom ? { x: 0, y: 0 } : clampPan(prev, nextZoom),
        );
        return nextZoom;
      });
    };
    useWheelZoom(outerContainerRef, handleWheelZoom, imageLoaded);

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
            transition: dragging ? "none" : "transform 0.15s ease",
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
