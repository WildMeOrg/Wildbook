import React, {
  forwardRef,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from "react";

const InteractiveAnnotationOverlay = forwardRef(
  (
    {
      imageUrl,
      annotations = [],
      originalWidth,
      originalHeight,
      rotationInfo = null,
      initialZoom = 1,
      minZoom = 0.5,
      maxZoom = 3,
      zoomStep = 0.25,
      showAnnotations: showAnnotationsProp,
      strokeColor = "yellow",
      lineWidth = 2,
      containerStyle = {},
      imageStyle = {},
      overlayStyle = {},
      alt = "Image with annotations",
    },
    ref,
  ) => {
    const containerRef = useRef(null);
    const [box, setBox] = useState({ w: 0, h: 0 });
    const [zoom, setZoom] = useState(
      Number.isFinite(initialZoom) ? initialZoom : 1,
    );
    const [pan, setPan] = useState({ x: 0, y: 0 });
    const [dragging, setDragging] = useState(false);
    const dragStartRef = useRef({ x: 0, y: 0 });
    const panStartRef = useRef({ x: 0, y: 0 });

    const [internalShowAnn, setInternalShowAnn] = useState(true);
    const showAnn =
      typeof showAnnotationsProp === "boolean"
        ? showAnnotationsProp
        : internalShowAnn;

    useEffect(() => {
      const el = containerRef.current;
      if (!el) return;

      const update = () => {
        const r = el.getBoundingClientRect();
        setBox({ w: r.width, h: r.height });
      };

      update();

      let ro;
      if (typeof ResizeObserver !== "undefined") {
        ro = new ResizeObserver(update);
        ro.observe(el);
      } else {
        window.addEventListener("resize", update);
      }

      return () => {
        if (ro) ro.disconnect();
        window.removeEventListener("resize", update);
      };
    }, []);

    const fit = useMemo(() => {
      const cw = box.w;
      const ch = box.h;
      const iw = Number(originalWidth) || 0;
      const ih = Number(originalHeight) || 0;

      if (!cw || !ch || !iw || !ih) {
        return { scale: 1, offsetX: 0, offsetY: 0, renderW: cw, renderH: ch };
      }

      const scale = Math.min(cw / iw, ch / ih);
      const renderW = iw * scale;
      const renderH = ih * scale;
      const offsetX = (cw - renderW) / 2;
      const offsetY = (ch - renderH) / 2;

      return { scale, offsetX, offsetY, renderW, renderH };
    }, [box.w, box.h, originalWidth, originalHeight]);

    const visibleAnnotations = useMemo(() => {
      if (!Array.isArray(annotations)) return [];
      return annotations.filter((a) => a && !a.trivial && !a.isTrivial);
    }, [annotations]);

    const clampZoom = (z) => Math.max(minZoom, Math.min(maxZoom, z));

    const zoomIn = () => setZoom((z) => clampZoom(z + zoomStep));
    const zoomOut = () => setZoom((z) => clampZoom(z - zoomStep));
    const reset = () => {
      setZoom(clampZoom(initialZoom || 1));
      setPan({ x: 0, y: 0 });
    };
    const toggleAnnotations = () => {
      if (typeof showAnnotationsProp === "boolean") return;
      setInternalShowAnn((v) => !v);
    };
    const setAnnotationsVisible = (v) => {
      if (typeof showAnnotationsProp === "boolean") return;
      setInternalShowAnn(!!v);
    };

    useImperativeHandle(ref, () => ({
      zoomIn,
      zoomOut,
      reset,
      toggleAnnotations,
      setAnnotationsVisible,
      getState: () => ({ zoom, pan, showAnn }),
    }));

    const onMouseDown = (e) => {
      setDragging(true);
      dragStartRef.current = { x: e.clientX, y: e.clientY };
      panStartRef.current = { ...pan };
    };

    useEffect(() => {
      if (!dragging) return;

      const onMove = (e) => {
        const dx = e.clientX - dragStartRef.current.x;
        const dy = e.clientY - dragStartRef.current.y;
        setPan({
          x: panStartRef.current.x + dx,
          y: panStartRef.current.y + dy,
        });
      };

      const onUp = () => setDragging(false);

      window.addEventListener("mousemove", onMove);
      window.addEventListener("mouseup", onUp);
      return () => {
        window.removeEventListener("mousemove", onMove);
        window.removeEventListener("mouseup", onUp);
      };
    }, [dragging, pan]);

    const panZoomTransform = `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`;

    return (
      <div
        ref={containerRef}
        style={{
          position: "relative",
          width: "100%",
          height: "100%",
          overflow: "hidden",
          cursor: dragging ? "grabbing" : "grab",
          ...containerStyle,
        }}
        onMouseDown={onMouseDown}
      >
        <div
          style={{
            position: "absolute",
            inset: 0,
            transform: panZoomTransform,
            transformOrigin: "center center",
            transition: dragging ? "none" : "transform 0.15s ease",
          }}
        >
          <img
            src={imageUrl || ""}
            alt={alt}
            draggable={false}
            style={{
              width: "100%",
              height: "100%",
              display: "block",
              objectFit: "contain",
              userSelect: "none",
              ...imageStyle,
            }}
          />

          {showAnn && (
            <div
              style={{
                position: "absolute",
                inset: 0,
                pointerEvents: "none",
                ...overlayStyle,
              }}
            >
              {visibleAnnotations.map((a, idx) => {
                const x0 = a.x;
                const y0 = a.y;
                const w0 = a.width;
                const h0 = a.height;

                const x = fit.offsetX + x0 * fit.scale;
                const y = fit.offsetY + y0 * fit.scale;
                const w = w0 * fit.scale;
                const h = h0 * fit.scale;

                const theta = Number(a.theta || 0);

                const key =
                  a.id ??
                  a.annotationId ??
                  `${idx}-${x0}-${y0}-${w0}-${h0}`;

                return (
                  <div
                    key={key}
                    style={{
                      position: "absolute",
                      left: x,
                      top: y,
                      width: w,
                      height: h,
                      border: `${lineWidth}px solid ${strokeColor}`,
                      boxSizing: "border-box",
                      transform: theta ? `rotate(${theta}rad)` : undefined,
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

export default InteractiveAnnotationOverlay;
