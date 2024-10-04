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
import MainButton from "./MainButton";
import { v4 as uuidv4 } from "uuid";

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
      const supportedTypes = ["image/jpeg", "image/png", "image/bmp"];

      // Check if the file's type is supported
      if (!supportedTypes.includes(file.file.type)) {
        console.error("Unsupported file type:", file.file.type);
        // Optionally show an error message to the user here
        return false; // Prevent the file from being added to the upload queue
      }

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
      return true;
    });

    flowInstance.on("fileProgress", (file) => {
      const percentage = (file._prevUploadedSize / file.size) * 100;
      setPreviewData((prevPreviewData) =>
        prevPreviewData.map((preview) =>
          preview.fileName === file.name
            ? { ...preview, progress: percentage }
            : preview,
        ),
      );
    });

    flowInstance.on("fileSuccess", (file) => {
      setUploading(false);
      setPreviewData((prevPreviewData) =>
        prevPreviewData.map((preview) =>
          preview.fileName === file.name
            ? { ...preview, progress: 100 }
            : preview,
        ),
      );
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
      const submissionId = uuidv4();
      const fileNames = files.map((file) => file.name);

      const submissionData = {
        submissionId,
        fileNames,
      };

      console.log("Submission Data:", submissionData);
      setUploading(true);
      flow.upload();
    }
  };

  return (
    <Container>
      <Row>
        <p style={{ fontWeight: "500", fontSize: "1.5rem" }}>
          <FormattedMessage id="PHOTOS_SECTION" />
        </p>
        <p>
          <FormattedMessage id="SUPPORTED_FILETYPES" />
        </p>
      </Row>
      <Row>
        {
          <Row className="mt-4">
            {previewData.map((preview, index) => (
              <Col
                key={index}
                className="mb-4 me-4 d-flex flex-column justify-content-between"
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

            <Col
              md={8}
              style={{
                width: fileActivity ? "220px" : "100%",
              }}
            >
              <div
                id="drop-area"
                className="d-flex flex-column align-items-center justify-content-center p-4"
                style={{
                  border: `1px dashed ${theme.primaryColors.primary500}`,
                  borderRadius: "8px",
                  backgroundColor: "#e8f7fc",
                  textAlign: "center",
                  cursor: "pointer",
                  height: fileActivity ? "200px" : "300px",
                }}
              >
                <div className="mb-3">
                  <i
                    className="bi bi-images"
                    style={{
                      fontSize: "2rem",
                      color: theme.wildMeColors.cyan700,
                    }}
                  ></i>
                </div>
                <p>
                  <FormattedMessage id="PHOTO_INSTRUCTION" />
                </p>
                <MainButton
                  onClick={() => fileInputRef.current.click()}
                  disabled={uploading}
                  backgroundColor={theme.wildMeColors.cyan700}
                  color={theme.defaultColors.white}
                  noArrow={true}
                >
                  <FormattedMessage id="BROWSE" />
                </MainButton>
                <input
                  type="file"
                  id="file-chooser"
                  multiple
                  accept=".jpg,.jpeg,.png,.bmp"
                  ref={fileInputRef}
                  style={{ display: "none" }}
                />
              </div>
            </Col>
          </Row>
        }
      </Row>

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
