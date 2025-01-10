import React, { useState, useContext, useRef, useEffect } from "react";
import Select from "react-select";
import Form from "react-bootstrap/Form";
import { FormattedMessage } from "react-intl";
import Container from "react-bootstrap/Container";
import MainButton from "../components/MainButton";
import ThemeColorContext from "../ThemeColorProvider";
import Slider from "../components/Slider";

export default function ManualAnnotation() {
  const theme = useContext(ThemeColorContext);
  const imgRef = useRef(null); // Reference to the image
  const canvasRef = useRef(null); // Reference to the canvas
  const [canvasSize, setCanvasSize] = useState({ width: 0, height: 0 });
  const [value, setValue] = useState(0); // Rotation angle in degrees

  // Bounding box and rotation state
  const [boundingBox, setBoundingBox] = useState({
    x: 0,
    y: 0,
    width: 0,
    height: 0,
    rotation: 0, // Rotation angle in radians
  });

  const [isDrawing, setIsDrawing] = useState(false); // Is the user drawing?
  const [startPoint, setStartPoint] = useState({ x: 0, y: 0 }); // Start point of the bounding box
  const [currentPoint, setCurrentPoint] = useState({ x: 0, y: 0 }); // Current mouse position

  useEffect(() => {
    // Synchronize the canvas size with the image size
    if (imgRef.current && canvasRef.current) {
      const img = imgRef.current;
      setCanvasSize({
        width: img.offsetWidth,
        height: img.offsetHeight,
      });

      const canvas = canvasRef.current;
      canvas.width = img.offsetWidth;
      canvas.height = img.offsetHeight;
    }
  }, [imgRef.current]);

  const handleMouseDown = (e) => {
    const canvas = canvasRef.current;
    const rect = canvas.getBoundingClientRect();

    // Start drawing
    setIsDrawing(true);
    setStartPoint({
      x: e.clientX - rect.left,
      y: e.clientY - rect.top,
    });
  };

  const handleMouseMove = (e) => {
    if (!isDrawing) return;

    const canvas = canvasRef.current;
    const rect = canvas.getBoundingClientRect();

    // Update current mouse position
    setCurrentPoint({
      x: e.clientX - rect.left,
      y: e.clientY - rect.top,
    });

    // Draw the bounding box in real-time
    const ctx = canvas.getContext("2d");
    ctx.clearRect(0, 0, canvas.width, canvas.height); // Clear previous box
    ctx.strokeStyle = "red";
    ctx.lineWidth = 2;

    const width = e.clientX - rect.left - startPoint.x;
    const height = e.clientY - rect.top - startPoint.y;
    ctx.strokeRect(startPoint.x, startPoint.y, width, height);
  };

  const handleMouseUp = () => {
    setIsDrawing(false);

    // Save the bounding box details
    const width = currentPoint.x - startPoint.x;
    const height = currentPoint.y - startPoint.y;

    setBoundingBox({
      x: startPoint.x,
      y: startPoint.y,
      width,
      height,
      rotation: 0, // Default rotation
    });
  };

  console.log("bounding box", boundingBox);

  const drawRotatedBox = (ctx, box) => {
    const { x, y, width, height, rotation } = box;

    // Translate canvas to the center of the rectangle
    const centerX = x + width / 2;
    const centerY = y + height / 2;
    ctx.translate(centerX, centerY);

    // Apply rotation
    ctx.rotate(rotation);

    // Draw the rectangle (centered at 0,0 after translate)
    ctx.strokeStyle = "red";
    ctx.lineWidth = 2;
    ctx.strokeRect(-width / 2, -height / 2, width, height);

    // Restore canvas to original state
    ctx.rotate(-rotation);
    ctx.translate(-centerX, -centerY);
  };

  useEffect(() => {
    const canvas = canvasRef.current;
    const ctx = canvas.getContext("2d");

    // Clear the canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // Draw the rotated bounding box
    if (boundingBox.width && boundingBox.height) {
      drawRotatedBox(ctx, boundingBox);
    }
  }, [boundingBox]); // Re-render when bounding box changes

  useEffect(() => {
    setBoundingBox({
      ...boundingBox,
      rotation: (value * Math.PI) / 180, // Convert degrees to radians
    });
  }, [value]);

  return (
    <Container>
      <h4 className="mt-3 mb-3">
        <FormattedMessage id="ADD_ANNOTATIONS" />
      </h4>
      <Form className="d-flex flex-row">
        <Form.Group controlId="formBasicEmail" className="me-3">
          <Form.Label>
            <FormattedMessage id="IA_CLASS" />*
          </Form.Label>
          <Select
            isMulti={true}
            options={[]}
            className="basic-multi-select"
            classNamePrefix="select"
            menuPlacement="auto"
            menuPortalTarget={document.body}
            value={[]}
            onChange={() => {}}
          />
        </Form.Group>
        <Form.Group controlId="formBasicEmail">
          <Form.Label>
            <FormattedMessage id="VIEWPOINT" />*
          </Form.Label>
          <Select
            isMulti={true}
            options={[]}
            className="basic-multi-select"
            classNamePrefix="select"
            menuPlacement="auto"
            menuPortalTarget={document.body}
          />
        </Form.Group>
      </Form>
      <div
        className="d-flex w-100 flex-column"
        style={{
          height: "80vh",
          padding: "1em",
          marginTop: "1em",
          borderRadius: "10px",
          overflow: "hidden",
          boxShadow: "0 0 10px rgba(0, 0, 0, 0.2)",
        }}
      >
        <div className="d-flex justify-content-between">
          <h6>
            <FormattedMessage id="DRAW_ANNOTATION" />
          </h6>
          <div
            style={{
              cursor: "pointer",
              color: theme.primaryColors.primary500,
            }}
          >
            <FormattedMessage id="DELETE" />
            <i className="bi bi-trash ms-2"></i>
          </div>
        </div>
        <div
          className="d-flex w-100 flex-column"
          style={{
            height: "80vh",
            padding: "1em",
            marginTop: "1em",
            borderRadius: "10px",
            overflow: "hidden",
            boxShadow: "0 0 10px rgba(0, 0, 0, 0.2)",
            position: "relative", // Key to enable stacking
          }}
        >
          <img
            ref={imgRef}
            src={`${process.env.PUBLIC_URL}/images/forest.png`}
            alt="annotationimages"
            style={{
              width: "100%",
              height: "100%",
              objectFit: "fill",
              position: "absolute", // Ensure stacking
              top: 0,
              left: 0,
            }}
          />
          <canvas
            ref={canvasRef}
            style={{
              position: "absolute", // Ensure stacking
              top: 0,
              left: 0,
              width: `${canvasSize.width}px`, // Optional, for display purposes
              height: `${canvasSize.height}px`,
              cursor: "crosshair",
            }}
            onMouseDown={handleMouseDown} // Start drawing
            onMouseMove={handleMouseMove} // Update box
            onMouseUp={handleMouseUp} // Stop drawing
          />
        </div>

        <Slider
          min={0}
          max={360}
          step={1}
          value={(boundingBox.rotation * 180) / Math.PI} // Convert radians to degrees
          // // onChange={(value) => {
          // //   console.log("changing value", value);
          // //   setBoundingBox({
          // //     ...boundingBox,
          // //     rotation: (value * Math.PI) / 180, // Convert degrees to radians
          // //   })
          // // }
          // }
          setValue={setValue}
        />
      </div>
      <MainButton
        noArrow={true}
        style={{ marginTop: "1em" }}
        backgroundColor="lightblue"
        borderColor="#303336"
      >
        <FormattedMessage id="SAVE_ANNOTATION" />
      </MainButton>
    </Container>
  );
}
