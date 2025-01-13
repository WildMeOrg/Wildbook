import React, { useEffect, useRef, useState } from 'react';
import { Stage, Layer, Rect, Transformer } from 'react-konva';

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

    const [rectProps, setRectProps] = useState({
        x: 100,
        y: 100,
        width: 100,
        height: 100,
        fill: null,
        stroke: 'red',
        strokeWidth: 2,
        rotation: 0,
        draggable: false,

    });

    useEffect(() => {
        setRectProps({
            x,
            y,
            width,
            height,
            fill: null,
            stroke: 'red',
            strokeWidth: 2,
            rotation: angle,
            draggable: false,

        });
    }, [angle]);

    useEffect(() => {
        setRectProps({
            x,
            y,
            width,
            height,
            fill: null,
            stroke: 'red',
            strokeWidth: 2,
            rotation: 0,
            draggable: false,
        });
    }, [x, y, width, height]);

    const rectRef = useRef(null);
    const transformerRef = useRef(null);

    const handleTransform = () => {
        const node = rectRef.current;

        const scaleX = node.scaleX();
        const scaleY = node.scaleY();

        setRectProps({
            ...rectProps,
            x: node.x(),
            y: node.y(),
            width: Math.max(5, node.width() * scaleX),
            height: Math.max(5, node.height() * scaleY),
            rotation: node.rotation(),
        });

        setRect({
            x: node.x(),
            y: node.y(),
            width: Math.max(5, node.width() * scaleX),
            height: Math.max(5, node.height() * scaleY),
        })

        // 
        node.scaleX(1);
        node.scaleY(1);
    };

    const handleDragEnd = () => {
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
                    draggable={false}
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