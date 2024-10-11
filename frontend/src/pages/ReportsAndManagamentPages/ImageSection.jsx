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
import ThemeContext from "../../ThemeColorProvider";
import MainButton from "../../components/MainButton";
import { v4 as uuidv4 } from "uuid";
import useGetSiteSettings from "../../models/useGetSiteSettings";
import { observer } from "mobx-react-lite";
import { Alert } from "react-bootstrap";
import ReportEncounterStore from "./ReportEncounterStore";
import ReportEncounter from "./ReportEncounter";


export const FileUploader = observer(({ reportEncounterStore }) => {
  const [files, setFiles] = useState([]);
  const [flow, setFlow] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [fileActivity, setFileActivity] = useState(false);
  const [previewData, setPreviewData] = useState([]);
  const fileInputRef = useRef(null);
  const [fileNames, setFileNames] = useState([]);
  const { data } = useGetSiteSettings();
  const maxSize = data?.maximumMediaSizeMegabytes || 40;
  const theme = useContext(ThemeContext);
  const originalBorder = `1px dashed ${theme.primaryColors.primary500}`;
  const updatedBorder = `2px dashed ${theme.primaryColors.primary500}`;

  const [count, setCount] = useState(0);

  useEffect(() => {
    setFileNames(previewData.map((preview) => preview.fileName));
    if (count === previewData.length && count > 0) {

      reportEncounterStore.setImageSectionUploadSuccess(true);
      console.log("All files uploaded successfully.");
    }

    reportEncounterStore.SetImageCount(previewData);
    
  }, [previewData, count]);

  

  useEffect(() => {
    if (reportEncounterStore.startUpload) {
      console.log("Start upload");
      handleUploadClick();
    }
  }, [reportEncounterStore.startUpload]);

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
      query: {
        submissionId: reportEncounterStore.imageSectionSubmissionId,
        // Add any additional query parameters here
      }
    });

    flowInstance.assignBrowse(fileInputRef.current);
    setFlow(flowInstance);

    flowInstance.on("fileAdded", (file) => {
      const supportedTypes = ["image/jpeg", "image/png", "image/bmp"];
      if (!supportedTypes.includes(file.file.type)) {
        console.error("Unsupported file type:", file.file.type);
        flowInstance.removeFile(file);
        return false;
      }

      // Check if the file already exists in the files state
      const fileExists = files.some(
        (f) => f.name === file.name && f.size === file.size
      );
      if (fileExists) {
        console.warn("File already exists:", file.name);
        flowInstance.removeFile(file);
        return false;
      }

      // Add file to the state if not already present
      setFiles((prevFiles) => [
        ...prevFiles.filter((f) => f.name !== file.name),
        file,
      ]);

      const reader = new FileReader();
      reader.onloadend = () => {
        // Update preview data, avoiding duplicates
        setPreviewData((prevPreviewData) => [
          ...prevPreviewData.filter((p) => p.fileName !== file.name),
          {
            src: reader.result,
            fileName: file.name,
            fileSize: file.size,
            progress: 0,
          },
        ]);
      };
      reader.readAsDataURL(file.file);
      setFileActivity(true);
      return true;
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
      setCount((prevCount) => prevCount + 1);
      setPreviewData((prevPreviewData) =>
        prevPreviewData.map((preview) =>
          preview.fileName === file.name
            ? { ...preview, progress: 100 }
            : preview
        )
      );

    });

    flowInstance.on("fileError", (file, message) => {
      setUploading(false);
      setPreviewData((prevPreviewData) =>
        prevPreviewData.map((preview) =>
          preview.fileName === file.name ? { ...preview, progress: 0 } : preview
        )
      );
      console.error("Upload error:", message);
      reportEncounterStore.setImageSectionUploadSuccess(false);
    });

    setupDragAndDropListeners(flowInstance);
  };

  const setupDragAndDropListeners = (flowInstance) => {
    const dropArea = document.getElementById("drop-area");
    if (dropArea && flowInstance) {
      dropArea.addEventListener("dragenter", handleDragEnter);
      dropArea.addEventListener("dragover", handleDragOver);
      dropArea.addEventListener("dragleave", handleDragLeave);
      dropArea.addEventListener("drop", (e) => handleDrop(e, flowInstance));
    }
  };

  const handleDragEnter = (e) => {
    e.preventDefault();
    e.stopPropagation();
    // e.target.style.border = "2px dashed red";

    if (e.currentTarget.id === "drop-area") {
      e.currentTarget.style.border = updatedBorder;
    }
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    e.stopPropagation();
    e.dataTransfer.dropEffect = "copy";
    if (e.currentTarget.id === "drop-area") {
      e.currentTarget.style.border = updatedBorder;
    }
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.currentTarget.id === "drop-area") {
      e.currentTarget.style.border = originalBorder;
    }
  };

  const handleDrop = (e, flowInstance) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.currentTarget.id === "drop-area") {
      e.currentTarget.style.border = originalBorder;
    }
    if (flowInstance && e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      const filesArray = Array.from(e.dataTransfer.files);
      filesArray.forEach((file) => {
        flowInstance.addFile(file); // Let flow handle the file addition and trigger fileAdded
      });
      e.dataTransfer.clearData();
    }
  };

  const handleUploadClick = () => {
    console.log("Uploading files:", files);
    const validFiles = flow.files.filter(
      (file) => file.size <= 1 * 1024 * 1024
    );

    

    if (validFiles.length > 0) {
      setUploading(true);
      const submissionId = uuidv4();
      reportEncounterStore.setImageSectionSubmissionId(submissionId);
      flow.opts.query.submissionId = submissionId;
      validFiles.forEach((file) => {
        // console.log("Uploading file:", file);
        flow.upload(file);
      });
    }
  };

  console.log(reportEncounterStore.imageSectionError);
  return (
    <div>
      <div>
        <h5 style={{ fontWeight: "600" }}>
          <FormattedMessage id="PHOTOS_SECTION" />{" "}
          {reportEncounterStore.imageRequired && "*"}
        </h5>
        <p>
          <FormattedMessage id="SUPPORTED_FILETYPES" />{`${" "}${maxSize} MB`}
        </p>
      </div>
      <Row>
        {reportEncounterStore.imageSectionError && (
          <Alert
            variant="danger"
            style={{
              marginTop: "10px",
            }}
          >
            <i
              className="bi bi-info-circle-fill"
              style={{ marginRight: "8px", color: "#560f14" }}
            ></i>
            you have to upload at least one image
            {/* <FormattedMessage id="EMPTY_REQUIRED_WARNING" /> */}
          </Alert>
        )}
      </Row>

      <Row className="mt-4 w-100">
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
              <i
                className="bi bi-x-circle-fill"
                style={{
                  position: "absolute",
                  top: "0",
                  right: "5px",
                  cursor: "pointer",
                  color: "white",
                }}
                onClick={() => {
                  setPreviewData((prevPreviewData) =>
                    prevPreviewData.filter(
                      (previewData) =>
                        previewData.fileName !== preview.fileName
                    )
                  );

                  flow.removeFile(
                    files.find((f) => f.name === preview.fileName)
                  );
                  setFiles((prevFiles) =>
                    prevFiles.filter((file) => file.name !== preview.fileName)
                  );
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
                <div>
                  {(preview.fileSize / (1024 * 1024)).toFixed(2)} MB
                </div>
                {(preview.fileSize / (1024 * 1024)).toFixed(2) > 1 && (
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

        <Col md={8} style={{ width: fileActivity ? "200px" : "100%" }}>
          <div
            id="drop-area"
            className="d-flex flex-column align-items-center justify-content-center p-4"
            style={{
              border: originalBorder,
              borderRadius: "8px",
              backgroundColor: "#e8f7fc",
              textAlign: "center",
              cursor: "pointer",
              height: fileActivity ? "120px" : "300px",
              boxSizing: "border-box",
            }}
          >
            {fileActivity ? (
              <div onClick={() => fileInputRef.current.click()}>
                <i
                  className="bi bi-images"
                  style={{
                    fontSize: "1rem",
                    color: theme.wildMeColors.cyan700,
                  }}
                ></i>
                <p>
                  <FormattedMessage id="ADD_MORE_FILES" />
                </p>
              </div>
            ) : (
              <div className="mb-3 d-flex flex-column justify-content-center">
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
              </div>
            )}
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


      {/* {fileActivity && (
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
      )} */}
    </div>
  );
});

export default FileUploader;
