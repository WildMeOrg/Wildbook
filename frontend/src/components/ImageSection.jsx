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
import useGetSiteSettings from "../models/useGetSiteSettings";
import { observer } from "mobx-react-lite";

export const FileUploader = observer((reportEncounterStore) => {
  const [files, setFiles] = useState([]);
  const [flow, setFlow] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [fileActivity, setFileActivity] = useState(false);
  const [previewData, setPreviewData] = useState([]);
  const fileInputRef = useRef(null);
  const [fileNames, setFileNames] = useState([]);
  const { data } = useGetSiteSettings();
  const maxSize = data?.maximumMediaSizeMegabytes || 40;

  useEffect(() => {
    setFileNames(previewData.map((preview) => preview.fileName));
    if (previewData?.length > 0) {
      const submissionId = uuidv4();
    } else {
    }
  }, [previewData]);

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
    });

    flowInstance.on("fileError", (file, message) => {
      setUploading(false);
      setPreviewData((prevPreviewData) =>
        prevPreviewData.map((preview) =>
          preview.fileName === file.name

            ? { ...preview, progress: 0 }
            : preview,
        ),
      );
      console.error("Upload error:", message);
    });
  };

  const handleUploadClick = () => {
    // remove files that are larger than the max size
    const files = flow.files.filter((file) => file.size <= maxSize * 1024 * 1024);

    if (files) {
      setUploading(true);
      files.forEach((file) => {
        flow.upload(file);
      });
    }
  };

  console.log("reportEncounterStore", reportEncounterStore.speciesSection);

  return (
    <Container>
      <Row>
        <p style={{ fontWeight: "500", fontSize: "1.5rem" }}>
          <FormattedMessage id="PHOTOS_SECTION" /> {reportEncounterStore.imageRequired && "*"}
        </p>
        <p>
          <FormattedMessage id="SUPPORTED_FILETYPES" />{`${" "}${maxSize} MB`}
        </p>
      </Row>
      <Row>
        {
          <Row className="mt-4">
            {previewData.map((preview, index) => (
              <Col
                key={index}
                className="mb-4 me-4 d-flex flex-column justify-content-between"
                style={{
                  maxWidth: "200px",
                  position: "relative",
                }}
              >
                <div style={{ position: "relative" }}>
                  <i class="bi bi-x-circle-fill"
                    style={{
                      position: "absolute",
                      top: "0",
                      right: "5px",
                      cursor: "pointer",
                      color: "white",
                    }}
                    onClick={() => {
                      console.log("Delete file");
                      setPreviewData((prevPreviewData) =>
                        prevPreviewData.filter(
                          (previewData) => previewData.fileName !== preview.fileName,
                        ),
                      );

                      flow.removeFile(files.find(f => f.name === preview.fileName));
                      setFiles((prevFiles) => prevFiles.filter((file) => file.name !== preview.fileName));
                    }}
                  ></i>
                  <Image
                    id="thumb"
                    src={preview.src}
                    style={{ width: "100%", height: "120px", objectFit: "fill" }}
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
                    {(preview.fileSize / (1024 * 1024)).toFixed(2) > 0.1 && (
                      <div style={{ color: "red" }}>
                        <FormattedMessage id="FILE_SIZE_EXCEEDED" />
                      </div>
                    )}
                  </div>
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
                width: fileActivity ? "200px" : "100%",
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
                  height: fileActivity ? "120px" : "300px",
                }}
              >
                {fileActivity ? <div
                  onClick={() => fileInputRef.current.click()}
                ><i
                  className="bi bi-images"
                  style={{
                    fontSize: "1rem",
                    color: theme.wildMeColors.cyan700,
                  }}
                ></i>
                  <p><FormattedMessage id="ADD_MORE_FILES" /></p>
                </div> : <div className="mb-3 d-flex flex-column justify-content-center">
                  <i
                    className="bi bi-images"
                    style={{
                      fontSize: "2rem",
                      color: theme.wildMeColors.cyan700,
                    }}
                  ></i>
                  <p>
                    <FormattedMessage id="PHOTO_INSTRUCTION" />
                  </p>

                  <MainButton
                    onClick={() => fileInputRef.current.click()}
                    disabled={uploading}
                    backgroundColor={theme.wildMeColors.cyan700}
                    color={theme.defaultColors.white}
                    noArrow={true}
                    style={{
                      width: "auto",
                      fontSize: "1rem",
                      margin: "0 auto",
                    }}
                  >
                    <FormattedMessage id="BROWSE" />
                  </MainButton>
                </div>}
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
});

export default FileUploader;
