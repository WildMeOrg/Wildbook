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
      setRectProps(
        {
          x: rect.x,
          y: rect.y,
          width: rect.width,
          height: rect.height,
          fill: null,
          stroke: "red",
          strokeWidth: 2,
          draggable: true,
        }
      );
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
    console.log("x after resizing: ", node.x());
    console.log("y after resizing:", node.y());
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

  const handleSelect = (e) => {
    transformerRef.current.nodes([rectRef.current]);
    transformerRef.current.getLayer().batchDraw();
  };

  const existingBoundingBoxes = [
    { x: 50, y: 50, width: 100, height: 80, stroke: "green" },
    { x: 200, y: 150, width: 120, height: 60, stroke: "blue" },
  ];

  return (
    <Stage width={imgWidth} height={imgHeight}>
      <Layer>
      {existingBoundingBoxes.map((box, index) => (
          <Rect
            key={index}
            x={box.x}
            y={box.y}
            width={box.width}
            height={box.height}
            rotation={box.rotation || 0}
            fill={box.fill || "transparent"}
            stroke={box.stroke || "blue"}
            strokeWidth={box.strokeWidth || 2}
            draggable={false} 
          />
        ))}
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
