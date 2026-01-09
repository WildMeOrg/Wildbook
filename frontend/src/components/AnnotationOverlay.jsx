import React, { useRef, useEffect } from "react";

const InteractiveAnnotationOverlay = ({
  imageUrl,
  annotations = [],
  originalWidth,
  originalHeight,
  zoom = 1,
  panPosition = { x: 0, y: 0 },
  isDragging = false,
  rotationInfo = null,
  strokeColor = "yellow",
  lineWidth = 2,
  containerStyle = {},
  imageStyle = {},
  alt = "Image with annotations",
  ...imageProps
}) => {
  const imgRef = useRef(null);
  const canvasRef = useRef(null);

  useEffect(() => {
    const drawAnnotations = () => {
      if (!imgRef.current || !canvasRef.current) return;

      const img = imgRef.current;
      const canvas = canvasRef.current;
      const context = canvas.getContext("2d");

      const displayWidth = img.clientWidth;
      const displayHeight = img.clientHeight;

      canvas.width = displayWidth;
      canvas.height = displayHeight;

      const baseScaleX = displayWidth / originalWidth;
      const baseScaleY = displayHeight / originalHeight;

      context.clearRect(0, 0, canvas.width, canvas.height);

      const validAnnotations = annotations.filter(
        (annotation) => !annotation.trivial,
      );

      for (const annotation of validAnnotations) {
        let { x, y, width, height, theta = 0 } = annotation;

        let scaledRect = {
          x: x * baseScaleX,
          y: y * baseScaleY,
          width: width * baseScaleX,
          height: height * baseScaleY,
        };

        if (rotationInfo) {
          const adjW = originalHeight / originalWidth;
          const adjH = originalWidth / originalHeight;
          scaledRect.x *= adjW;
          scaledRect.width *= adjW;
          scaledRect.y *= adjH;
          scaledRect.height *= adjH;
        }

        scaledRect.x *= zoom;
        scaledRect.y *= zoom;
        scaledRect.width *= zoom;
        scaledRect.height *= zoom;

        scaledRect.x += panPosition.x;
        scaledRect.y += panPosition.y;

        const rectCenterX = scaledRect.x + scaledRect.width / 2;
        const rectCenterY = scaledRect.y + scaledRect.height / 2;

        context.save();

        context.translate(rectCenterX, rectCenterY);
        context.rotate(theta);

        context.strokeStyle = strokeColor;
        context.lineWidth = lineWidth;

        context.strokeRect(
          -scaledRect.width / 2,
          -scaledRect.height / 2,
          scaledRect.width,
          scaledRect.height,
        );

        context.restore();
      }
    };

    const imgElement = imgRef.current;

    if (imgElement && imgElement.complete) {
      drawAnnotations();
    } else if (imgElement) {
      imgElement.addEventListener("load", drawAnnotations);
    }

    return () => {
      if (imgElement) {
        imgElement.removeEventListener("load", drawAnnotations);
      }
    };
  }, [
    imageUrl,
    annotations,
    originalWidth,
    originalHeight,
    zoom,
    panPosition,
    rotationInfo,
    strokeColor,
    lineWidth,
  ]);

  return (
    <div
      style={{
        position: "relative",
        width: "100%",
        height: "100%",
        overflow: "hidden",
        ...containerStyle,
      }}
    >
      <img
        ref={imgRef}
        src={imageUrl}
        alt={alt}
        style={{
          width: "100%",
          height: "100%",
          display: "block",
          objectFit: "contain",
          transformOrigin: "center center",
          transform: `scale(${zoom}) translate(${panPosition.x}px, ${panPosition.y}px)`,
          transition: isDragging ? "none" : "transform 0.2s ease",
          cursor: isDragging ? "grabbing" : "grab",
          ...imageStyle,
        }}
        draggable={false}
        {...imageProps}
      />
      <canvas
        ref={canvasRef}
        style={{
          position: "absolute",
          top: 0,
          left: 0,
          pointerEvents: "none",
        }}
      />
    </div>
  );
};

export default InteractiveAnnotationOverlay;
