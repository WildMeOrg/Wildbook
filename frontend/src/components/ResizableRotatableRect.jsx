import React, { useEffect, useRef, useState } from "react";
import { Stage, Layer, Rect, Transformer } from "react-konva";

const ResizableRotatableRect = ({
  x = 0,
  y = 0,
  width = 0,
  height = 0,
  imgHeight,
  imgWidth,
  setIsDraggingRect,
  setRect,
  angle = 0,
}) => {
  const [rectProps, setRectProps] = useState({});

  useEffect(() => {
    setRectProps({
      x,
      y,
      width,
      height,
      fill: null,
      stroke: "red",
      strokeWidth: 2,
      rotation: angle,
      draggable: true,
    });
  }, [x, y, width, height, angle]);

  const rectRef = useRef(null);
  const transformerRef = useRef(null);
  const handleTransform = () => {
    const node = rectRef.current;

    const scaleX = node.scaleX();
    const scaleY = node.scaleY();

    const updatedRect = {
      x: node.x(),
      y: node.y(),
      width: Math.max(5, node.width() * scaleX),
      height: Math.max(5, node.height() * scaleY),
      rotation: node.rotation(),
    };

    setRectProps({
      ...rectProps,
      ...updatedRect,
    });

    setRect(updatedRect);

    node.scaleX(1);
    node.scaleY(1);
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
    setIsDraggingRect(false);
  };

  const handleSelect = (e) => {
    e.cancelBubble = true;
    setIsDraggingRect(true);
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
          // offsetX={width / 2}
          // offsetY={height / 2}
        />
        <Transformer
          ref={transformerRef}
          rotateEnabled={false}
          resizeEnabled={true}
          keepRatio={false}
          // anchorSize={10}
        />
      </Layer>
    </Stage>
  );
};

export default ResizableRotatableRect;
