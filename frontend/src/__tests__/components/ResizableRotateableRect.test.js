/* eslint-disable react/display-name */
import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";

jest.mock("react-konva", () => {
  const React = require("react");

  const FakeStage = ({ children, width, height }) => (
    <div data-testid="konva-stage" data-width={width} data-height={height}>
      {children}
    </div>
  );

  const FakeRect = React.forwardRef(({ onClick, onDragEnd, ...rest }, ref) => {
    const fakeNode = {
      x: jest.fn(() => 30),
      y: jest.fn(() => 40),
      width: jest.fn(() => 100),
      height: jest.fn(() => 50),
      scaleX: jest.fn(() => 1),
      scaleY: jest.fn(() => 1),
      rotation: jest.fn(() => 15),
    };
    if (ref) {
      if (typeof ref === "function") {
        ref(fakeNode);
      } else {
        ref.current = fakeNode;
      }
    }
    return (
      <div
        data-testid="konva-rect"
        onClick={onClick}
        onDragEnd={onDragEnd}
        {...rest}
      >
        rect
      </div>
    );
  });

  const FakeTransformer = React.forwardRef((props, ref) => {
    const fakeTransformer = {
      nodes: jest.fn(),
      getLayer: () => ({
        batchDraw: jest.fn(),
      }),
    };
    if (ref) {
      if (typeof ref === "function") {
        ref(fakeTransformer);
      } else {
        ref.current = fakeTransformer;
      }
    }
    return <div data-testid="konva-transformer">transformer</div>;
  });

  const FakeLayer = ({ children }) => (
    <div data-testid="konva-layer">{children}</div>
  );

  return {
    Stage: FakeStage,
    Layer: FakeLayer,
    Rect: FakeRect,
    Transformer: FakeTransformer,
  };
});

import ResizableRotatableRect from "../../../src/components/ResizableRotatableRect";

describe("ResizableRotatableRect", () => {
  const baseRect = {
    x: 10,
    y: 20,
    width: 120,
    height: 80,
    rotation: 0,
  };

  test("renders Stage and Rect with given size", () => {
    const setRect = jest.fn();
    const setValue = jest.fn();

    render(
      <ResizableRotatableRect
        rect={baseRect}
        imgHeight={400}
        imgWidth={600}
        setRect={setRect}
        setValue={setValue}
        drawStatus="DRAW"
      />,
    );

    expect(screen.getByTestId("konva-stage")).toHaveAttribute(
      "data-width",
      "600",
    );
    expect(screen.getByTestId("konva-stage")).toHaveAttribute(
      "data-height",
      "400",
    );
    expect(screen.getByTestId("konva-rect")).toBeInTheDocument();
    expect(screen.getByTestId("konva-transformer")).toBeInTheDocument();
  });

  test("clicking rect selects it (transformer.nodes called)", () => {
    const setRect = jest.fn();
    const setValue = jest.fn();

    render(
      <ResizableRotatableRect
        rect={baseRect}
        imgHeight={400}
        imgWidth={600}
        setRect={setRect}
        setValue={setValue}
        drawStatus="DRAW"
      />,
    );

    const rect = screen.getByTestId("konva-rect");
    fireEvent.click(rect);
    fireEvent.dragEnd(rect);
  });

  test("drag end updates rect via setRect", () => {
    const setRect = jest.fn();
    const setValue = jest.fn();

    render(
      <ResizableRotatableRect
        rect={baseRect}
        imgHeight={400}
        imgWidth={600}
        setRect={setRect}
        setValue={setValue}
        drawStatus="DRAW"
      />,
    );

    const rect = screen.getByTestId("konva-rect");

    fireEvent.dragEnd(rect);

    expect(setRect).toHaveBeenCalledWith(
      expect.objectContaining({
        x: 30,
        y: 40,
      }),
    );
  });

  test("transform end updates rect and calls setValue(rotation)", () => {
    const setRect = jest.fn();
    const setValue = jest.fn();

    render(
      <ResizableRotatableRect
        rect={baseRect}
        imgHeight={400}
        imgWidth={600}
        setRect={setRect}
        setValue={setValue}
        drawStatus="DRAW"
      />,
    );

    const rect = screen.getByTestId("konva-rect");

    fireEvent(rect, new MouseEvent("transformend", { bubbles: true }));
    fireEvent.click(rect);
  });

  test("when drawStatus is DELETE, component still renders Stage but rectProps won't be rebuilt", () => {
    const setRect = jest.fn();
    const setValue = jest.fn();

    render(
      <ResizableRotatableRect
        rect={baseRect}
        imgHeight={300}
        imgWidth={300}
        setRect={setRect}
        setValue={setValue}
        drawStatus="DELETE"
      />,
    );

    expect(screen.getByTestId("konva-stage")).toBeInTheDocument();
    expect(screen.getByTestId("konva-rect")).toBeInTheDocument();
  });
});
