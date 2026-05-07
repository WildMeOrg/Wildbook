import {
  forwardRef,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from "react";

const VISIBLE_MARGIN_PX = 40;

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
      maxZoom = 3,
      zoomStep = 0.25,
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

    const [internalShowAnn, setInternalShowAnn] = useState(true);
    const showAnn =
      typeof showAnnotationsProp === "boolean"
        ? showAnnotationsProp
        : internalShowAnn;

    const hasRotation = !!rotationInfo;

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

    const clampZoom = (z) => Math.max(minZoom, Math.min(maxZoom, z));

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

    useImperativeHandle(ref, () => ({
      zoomIn: () => {
        setZoom((z) => {
          const nextZoom = clampZoom(z + zoomStep);
          setPan((prev) => clampPan(prev, nextZoom));
          return nextZoom;
        });
      },
      zoomOut: () => {
        setZoom((z) => {
          const nextZoom = clampZoom(z - zoomStep);
          setPan((prev) => clampPan(prev, nextZoom));
          return nextZoom;
        });
      },
      reset: () => {
        const nextZoom = clampZoom(initialZoom || 1);
        setZoom(nextZoom);
        setPan(clampPan({ x: 0, y: 0 }, nextZoom));
      },
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
                let rect = {
                  x: Number(a.x),
                  y: Number(a.y),
                  width: Number(a.width),
                  height: Number(a.height),
                  rotation: Number(a.theta || 0),
                };

                if (hasRotation) {
                  const imgW = Number(originalWidth);
                  const imgH = Number(originalHeight);
                  const adjW = imgH / imgW;
                  const adjH = imgW / imgH;

                  rect = {
                    x: rect.x / scaleX / adjW,
                    width: rect.width / scaleX / adjW,
                    y: rect.y / scaleY / adjH,
                    height: rect.height / scaleY / adjH,
                    rotation: rect.rotation,
                  };
                } else {
                  rect = {
                    x: rect.x / scaleX,
                    y: rect.y / scaleY,
                    width: rect.width / scaleX,
                    height: rect.height / scaleY,
                    rotation: rect.rotation,
                  };
                }

                if (
                  !Number.isFinite(rect.width) ||
                  !Number.isFinite(rect.height) ||
                  rect.width <= 0 ||
                  rect.height <= 0
                ) {
                  return null;
                }

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
