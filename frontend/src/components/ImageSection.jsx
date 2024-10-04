import React, { useState, useEffect, useRef, useContext } from "react";
import {
  Button,
  ProgressBar,
  Image,
  Container,
  Row,
  Col,
} from "react-bootstrap";
import Flow from "@flowjs/flow.js";
import { FormattedMessage } from "react-intl";
import ThemeContext from "../ThemeColorProvider";

const FileUploader = () => {
  const [files, setFiles] = useState([]);
  const [flow, setFlow] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [fileActivity, setFileActivity] = useState(false);
  const [previewData, setPreviewData] = useState([]);
  const fileInputRef = useRef(null);

  const theme = useContext(ThemeContext);

  useEffect(() => {
    if (!flow && fileInputRef.current) {
      initializeFlow();
    }
  }, [flow, fileInputRef]);

  const initializeFlow = () => {
    const flowInstance = new Flow({
      target: "/ResumableUpload",
      forceChunkSize: true,
      testChunks: false,
    });

    flowInstance.assignBrowse(fileInputRef.current);
    setFlow(flowInstance);

    flowInstance.on("fileAdded", (file) => {
      const reader = new FileReader();
      reader.onloadend = () => {
        setPreviewData((prevPreviewData) => [
          ...prevPreviewData,
          {
            src: reader.result,
            fileName: file.name,
            fileSize: file.size,
            progress: 0,
          },
        ]);
      };
      reader.readAsDataURL(file.file);

      setFiles((prevFiles) => [...prevFiles, file]);
      setFileActivity(true);
    });

    flowInstance.on("fileProgress", (file) => {
      const percentage = (file._prevUploadedSize / file.size) * 100;
      setPreviewData((prevPreviewData) =>
        prevPreviewData.map((preview) =>
          preview.fileName === file.name
            ? { ...preview, progress: percentage }
            : preview
        )
      );
    });

    flowInstance.on("fileSuccess", (file) => {
      setUploading(false);
      setPreviewData((prevPreviewData) =>
        prevPreviewData.map((preview) =>
          preview.fileName === file.name
            ? { ...preview, progress: 100 }
            : preview
        )
      );
      setFiles([]);
      setPreviewData([]);
      setFileActivity(false);
      setUploading(false);
      console.log("Upload success:", file);
    });

    flowInstance.on("fileError", (file, message) => {
      setUploading(false);
      console.error("Upload error:", message);
    });
  };

  const handleUploadClick = () => {
    if (flow) {
      setUploading(true);
      flow.upload();
    }
  };

  const handleMoreFilesClick = () => {
    setFiles([]);
    setPreviewData([]);
    setFileActivity(false);
    setUploading(false);
  };

  return (
    <Container>
      <Row className="justify-content-center">
        <Col md={8} className="w-100">
          <div id="drop-area"
            className="d-flex flex-column align-items-center justify-content-center p-4 w-100"
            style={{
              border: "2px dashed #00a1e0",
              borderRadius: "8px",
              backgroundColor: "#e8f7fc",
              textAlign: "center",
            }}
          >
            <p style={{ fontWeight: "bold" }}>Photos *</p>
            <p>We support .jpg, .jpeg, .png, and .bmp. Videos can be stored, but not used for image analysis.</p>
            <div className="mb-3">
              <i className="fas fa-image" style={{ fontSize: "2rem", color: "#007bff" }}></i>
            </div>
            <p>Drag and drop your files here or browse</p>
            <Button
              variant="primary"
              onClick={() => fileInputRef.current.click()}
              disabled={uploading}
            >
              Browse
            </Button>
            <input
              type="file"
              id="file-chooser"
              multiple
              accept="audio/*,video/*,image/*"
              ref={fileInputRef}
              style={{ display: "none" }}
            />
          </div>
        </Col>
      </Row>

      {previewData.length > 0 && (
        <Row className="mt-4">
          {previewData.map((preview, index) => (
            <Col
              key={index}
              className="mb-4 me-4 d-flex flex-column justify--between"
              style={{ maxWidth: "200px" }}
            >
              <Image
                id="thumb"
                src={preview.src}
                style={{ width: "220px", height: "150px", objectFit: "fill" }}
                alt={`Preview ${index + 1}`}
                thumbnail
              />
              <div
                className="mt-2 "
                style={{
                  width: "200px",
                  wordWrap: "break-word",
                  whiteSpace: "normal",
                }}
              >
                <div>{preview.fileName}</div>
                <div>{(preview.fileSize / (1024 * 1024)).toFixed(2)} MB</div>
              </div>
              <ProgressBar
                now={preview.progress}
                label={`${Math.round(preview.progress)}%`}
                className="mt-2"
                style={{ width: "200px" }}
              />
            </Col>
          ))}
        </Row>
      )}

      {fileActivity && (
        <Row>
          <Col>

            <Button
              id="upload-button"
              variant="primary"
              onClick={handleUploadClick}
              disabled={uploading}
            >
              {uploading ? "Uploading..." : "Begin Upload"}
            </Button>
          </Col>
        </Row>
      )}
    </Container>
  );
};

export default FileUploader;
