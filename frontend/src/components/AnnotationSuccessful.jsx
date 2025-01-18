import React, { useEffect, useRef } from "react";
import { FormattedMessage } from "react-intl";

export default function AnnotationSuccessful({
  annotationId,
  encounterId,
  rect,
  imageData,
}) {
  const canvasRef = useRef(null);
  const imgRef = useRef(null);

  console.log("rect: ", JSON.stringify(rect));

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

        const scaledRect = {
          x: rect.x / scaleX,
          y: rect.y / scaleY,
          width: rect.width / scaleX,
          height: rect.height / scaleY,
        };

        console.log("scaledRect: ", JSON.stringify(scaledRect));

        // Clear the canvas before drawing
        context.clearRect(0, 0, canvas.width, canvas.height);

        // Set bounding box styles
        context.strokeStyle = "red"; // Set the color of the bounding box
        context.lineWidth = 2; // Set the line width

        const rectCenterX = scaledRect.x + scaledRect.width / 2;
        const rectCenterY = scaledRect.y + scaledRect.height / 2;

        // // 将原点移动到矩形中心
        context.translate(rectCenterX, rectCenterY);

        console.log("rect.rotation: ", rect.rotation);
        // // 按照传入的弧度旋转
        context.rotate(rect.rotation);

        // 绘制旋转后的矩形 (以原点为中心，所以要调整位置)
        context.strokeRect(
          -scaledRect.width / 2,
          -scaledRect.height / 2,
          scaledRect.width,
          scaledRect.height,
        );

        context.restore();

        // Draw the bounding box
        // context.strokeRect(scaledRect.x, scaledRect.y, scaledRect.width, scaledRect.height);
        // context.strokeRect(100, 100, 100, 100);
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
    <div>
      <h4>
        <FormattedMessage id="ANNOTATION_SAVED" />
      </h4>
      <div>
        <FormattedMessage id="ANNOTATION_SUCCESSFUL" />
        <a href="">{annotationId}</a>
      </div>
      <div>
        <FormattedMessage id="ANNOTATION_SUCCESSFUL" />
        <a href="">{encounterId}</a>
      </div>
      <div
        className="image"
        style={{
          width: "70%",
          height: "auto",
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
          width={imgRef.current?.clientWidth || 150} // Adjust dynamically based on the image
          height={imgRef.current?.clientHeight || 150} // Adjust dynamically based on the image
          style={{
            position: "absolute",
            top: 0,
            left: 0,
            pointerEvents: "none", // Prevent interaction with the canvas
          }}
        ></canvas>
      </div>
      <div>
        <FormattedMessage id="CONTACT_MESSAGE" />
        <a href="https://community.wildme.org/">community.wildme.org</a>
      </div>
    </div>
  );
}
