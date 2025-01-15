import React, { useEffect, useRef, useState } from "react";
import { Stage, Layer, Rect, Transformer } from "react-konva";

const ResizableRotatableRect = ({
  rect,
  imgHeight,
  imgWidth,
  setRect,
  angle = 0,
  drawStatus,
}) => {
  const [rectProps, setRectProps] = useState({});

  useEffect(() => {

    if (drawStatus !== "DELETE") {
      console.log("executing useEffect");
      setRectProps(
        {
          x: rect.x,
          y: rect.y,
          width: rect.width,
          height: rect.height,
          fill: null,
          stroke: "red",
          strokeWidth: 2,
          rotation: angle,
          draggable: true,
        }
      );
      
    }
  }, [rect.x, rect.y, rect.width, rect.height, drawStatus]);

  useEffect(() => {
    console.log("executing useEffect for angle");
    setRectProps(
      {
        x: rect.x,
        y: rect.y,
        width: rect.width,
        height: rect.height,
        fill: null,
        stroke: "red",
        strokeWidth: 2,
        rotation: angle,
        draggable: true,
      }
    );

    setRect({
      x: rect.x,
      y: rect.y,
      width: rect.width,
      height: rect.height,
    })


  }, [angle]);

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

    setRect({
      ...rectProps,
      ...updatedRect,
    });

    node.scaleX(1);
    node.scaleY(1);
  };

  const handleDragEnd = () => {
    console.log("handle drag end");
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
    // e.cancelBubble = true;
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
          rotateEnabled={false}
          resizeEnabled={true}
          keepRatio={false}
        />
      </Layer>
    </Stage>
  );
};

export default ResizableRotatableRect;
