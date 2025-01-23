import React, { useEffect, useRef } from "react";
import { FormattedMessage } from "react-intl";

export default function AnnotationSuccessful({ encounterId, rect, imageData }) {
  const canvasRef = useRef(null);
  const imgRef = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const context = canvas.getContext("2d");
    const handleImageLoad = () => {
      if (imgRef.current) {
        const naturalWidth = imageData.width;
        const naturalHeight = imageData.height;

        const displayWidth = imgRef.current.clientWidth;
        const displayHeight = imgRef.current.clientHeight;

        const scaleX = naturalWidth / displayWidth;
        const scaleY = naturalHeight / displayHeight;

        canvas.width = imgRef.current.clientWidth;
        canvas.height = imgRef.current.clientHeight;

        const imageContainer = imgElement?.parentElement;

        if (imgRef && imageContainer) {
          imageContainer.style.height = `${imgElement.clientHeight}px`;
        }

        const scaledRect = {
          x: rect.x / scaleX,
          y: rect.y / scaleY,
          width: rect.width / scaleX,
          height: rect.height / scaleY,
        };

        context.clearRect(0, 0, canvas.width, canvas.height);

        context.strokeStyle = "red";
        context.lineWidth = 2;

        const rectCenterX = scaledRect.x + scaledRect.width / 2;
        const rectCenterY = scaledRect.y + scaledRect.height / 2;

        context.translate(rectCenterX, rectCenterY);
        context.rotate(rect.rotation);
        context.strokeRect(
          -scaledRect.width / 2,
          -scaledRect.height / 2,
          scaledRect.width,
          scaledRect.height,
        );

        // console.log("drawing line by line");

        // context.strokeStyle = "blue";
        // context.lineWidth = 2;
        // context.beginPath();
        // context.moveTo(-scaledRect.width / 2, -scaledRect.height / 2); // Top-left corner
        // context.lineTo(scaledRect.width / 2, -scaledRect.height / 2);  // Top-right corner
        // context.stroke();

        // // Draw the other borders in yellow
        // context.strokeStyle = "yellow";
        // context.lineWidth = 1;
        // context.beginPath();
        // context.moveTo(-scaledRect.width / 2, -scaledRect.height / 2); // Top-left corner
        // context.lineTo(-scaledRect.width / 2, scaledRect.height / 2);  // Bottom-left corner
        // context.lineTo(scaledRect.width / 2, scaledRect.height / 2);  // Bottom-right corner
        // context.lineTo(scaledRect.width / 2, -scaledRect.height / 2); // Top-right corner
        // context.closePath();
        // context.stroke();

        context.restore();
      }
    };

    const imgElement = imgRef.current;
    if (imgElement && imgElement.complete) {
      handleImageLoad();
    } else if (imgElement) {
      imgElement.addEventListener("load", handleImageLoad);
    }

    return () => {
      if (imgElement) {
        imgElement.removeEventListener("load", handleImageLoad);
      }
    };
  }, [rect]);

  return (
    <div className="annotation-successful mt-5">
      <h2>
        <FormattedMessage id="ANNOTATION_SAVED" />
      </h2>
      <div className="mt-3">
        <FormattedMessage id="ANNOTATION_SAVED_DESC" />
      </div>
      <div className="mt-3">
        <FormattedMessage id="ENCOUNTER_ID" />
        {": "}
        <a href={`/encounters/encounter.jsp?number=${encounterId}`}>
          {encounterId}
        </a>
      </div>
      <div
        className="image-container mt-3"
        style={{
          width: "500px",
          height: "300px",
          position: "relative",
        }}
      >
        <img
          ref={imgRef}
          style={{
            width: "100%",
            height: "auto",
            display: "block",
            position: "absolute",
            top: 0,
            left: 0,
          }}
          src={imageData.url || "https://via.placeholder.com/150"}
          alt="placeholder"
        />
        <canvas
          ref={canvasRef}
          width={imgRef.current?.clientWidth || 150}
          height={imgRef.current?.clientHeight || 150}
          style={{
            position: "absolute",
            top: 0,
            left: 0,
            pointerEvents: "none", // Prevent interaction with the canvas
          }}
        ></canvas>
      </div>
      <div className="mt-3 mb-5">
        <FormattedMessage id="CONTACT_MESSAGE" />
        <a href="https://community.wildme.org/">community.wildme.org</a>
      </div>
    </div>
  );
}
