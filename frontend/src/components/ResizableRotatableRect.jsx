import React, { useEffect, useRef, useState } from "react";
import { Stage, Layer, Rect, Transformer } from "react-konva";

const ResizableRotatableRect = ({
  rect,
  imgHeight,
  imgWidth,
  setRect,
  setValue,
  drawStatus,
}) => {
  const [rectProps, setRectProps] = useState({});

  useEffect(() => {
    if (drawStatus !== "DELETE") {
      setRectProps({
        x: rect.x,
        y: rect.y,
        width: rect.width,
        height: rect.height,
        fill: null,
        stroke: "red",
        strokeWidth: 2,
        draggable: true,
      });
    }
  }, [rect.x, rect.y, rect.width, rect.height, drawStatus]);

  const rectRef = useRef(null);
  const transformerRef = useRef(null);

  const handleTransform = () => {
    const node = rectRef.current;
    const scaleX = node.scaleX();
    const scaleY = node.scaleY();

    const newWidth = Math.max(5, node.width() * scaleX);
    const newHeight = Math.max(5, node.height() * scaleY);

    const updatedRect = {
      x: node.x(),
      y: node.y(),
      width: newWidth,
      height: newHeight,
      rotation: node.rotation(),
    };
    setRectProps({
      ...rectProps,
      ...updatedRect,
    });

    setRect({
      ...rect,
      ...updatedRect,
    });

    node.scaleX(1);
    node.scaleY(1);
    setValue(node.rotation());
  };

  const handleDragEnd = () => {
    const node = rectRef.current;
    const updatedRect = {
      x: node.x(),
      y: node.y(),
    };

    setRectProps({
      ...rectProps,
      ...updatedRect,
    });

    setRect({
      ...rectProps,
      ...updatedRect,
    });
  };

  const handleSelect = () => {
    transformerRef.current.nodes([rectRef.current]);
    transformerRef.current.getLayer().batchDraw();
  };

  return (
    <Stage width={imgWidth} height={imgHeight}>
      <Layer>
        <Rect
          {...rectProps}
          ref={rectRef}
          onClick={handleSelect}
          onDragStart={handleSelect}
          onDragEnd={handleDragEnd}
          onTransformEnd={handleTransform}
          draggable={true}
        />
        <Transformer
          ref={transformerRef}
          rotateEnabled={true}
          resizeEnabled={true}
          keepRatio={false}
        />
      </Layer>
    </Stage>
  );
};

export default ResizableRotatableRect;
