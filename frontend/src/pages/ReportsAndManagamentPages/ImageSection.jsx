import React, { useState, useEffect, useRef, useContext } from "react";
import { ProgressBar, Image, Row, Col } from "react-bootstrap";
import Flow from "@flowjs/flow.js";
import { FormattedMessage } from "react-intl";
import ThemeContext from "../../ThemeColorProvider";
import MainButton from "../../components/MainButton";
import { v4 as uuidv4 } from "uuid";
import useGetSiteSettings from "../../models/useGetSiteSettings";
import { observer } from "mobx-react-lite";
import { Alert } from "react-bootstrap";
import EXIF from "exif-js";

export const FileUploader = observer(({ store }) => {
  const [files, setFiles] = useState([]);
  const [flow, setFlow] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [previewData, setPreviewData] = useState([]);
  const fileInputRef = useRef(null);
  const { data } = useGetSiteSettings();
  const maxSize = data?.maximumMediaSizeMegabytes || 40;
  const theme = useContext(ThemeContext);
  const originalBorder = `1px dashed ${theme.primaryColors.primary500}`;
  const updatedBorder = `2px dashed ${theme.primaryColors.primary500}`;

  const submissionId = useRef(uuidv4()).current;

  useEffect(() => {
    store.setImageCount(
      previewData.filter((file) => file.fileSize <= maxSize * 1024 * 1024)
        .length,
    );
    const data = previewData.filter(
      (file) => file.fileSize <= maxSize * 1024 * 1024,
    );
    store.setImagePreview(data);
    // store.setImageSectionError(
    //   store.imageRequired &&
    //   previewData.length === 0
    //     ? true
    //     : false,
    // );
    handleUploadClick();
  }, [previewData]);

  useEffect(() => {
    if (!flow && fileInputRef.current) {
      initializeFlow();
    }
  }, [flow, fileInputRef]);

  useEffect(() => {
    const savedFiles = JSON.parse(localStorage.getItem("uploadedFiles"));
    if (savedFiles) {
      setPreviewData(savedFiles);
    }
    localStorage.getItem("submissionId") &&
      store.setImageSectionSubmissionId(
        localStorage.getItem("submissionId"),
      );
    localStorage.removeItem("submissionId");
    localStorage.removeItem("uploadedFiles");
  }, []);

  const initializeFlow = () => {
    const flowInstance = new Flow({
      target: "/ResumableUpload",
      forceChunkSize: true,
      testChunks: false,
      query: {
        submissionId: store.imageSectionSubmissionId,
      },
    });

    flowInstance.assignBrowse(fileInputRef.current);
    setFlow(flowInstance);

    flowInstance.on("fileAdded", (file) => {
      const supportedTypes = [
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/bmp",
      ];
      if (!supportedTypes.includes(file.file.type)) {
        console.error("Unsupported file type:", file.file.type);
        flowInstance.removeFile(file);
        return false;
      }

      if (file.size > maxSize * 1024 * 1024) {
        console.warn("File size exceeds limit:", file.name);
        return false;
      }

      // Add file to the state if not already present
      setFiles((prevFiles) => [
        ...prevFiles.filter((f) => f.name !== file.name),
        file,
      ]);

      const reader = new FileReader();
      reader.onloadend = () => {
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

      EXIF.getData(file.file, function () {
        const exifData = EXIF.getAllTags(this);
        const dateTime = exifData.DateTime;
        // const latitude = EXIF.getTag(this, "GPSLatitude");
        // const longitude = EXIF.getTag(this, "GPSLongitude");

        if (dateTime) {
          const f = dateTime.split(/\D+/);
          let datetime1;
          if (f.length == 3) datetime1 = f.join('-');
          if ((f.length == 5) || (f.length == 6)) datetime1 = f.slice(0, 3).join('-') + ' ' + f.slice(3, 6).join(':');
          store.setExifDateTime(datetime1);
            // geo: latitude && longitude ? { latitude, longitude } : null,
          
        } else {
          console.warn("EXIF data not available for:", file.name);
        }
      });
      reader.readAsDataURL(file.file);      
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
      store.setImageSectionFileNames(file.name, "add");
      setPreviewData((prevPreviewData) =>
        prevPreviewData.map((preview) =>
          preview.fileName === file.name
            ? { ...preview, progress: 100 }
            : preview,
        ),
      );
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
    if (
      flowInstance &&
      e.dataTransfer.files &&
      e.dataTransfer.files.length > 0
    ) {
      const filesArray = Array.from(e.dataTransfer.files);
      filesArray.forEach((file) => {
        flowInstance.addFile(file);
      });
      e.dataTransfer.clearData();
    }
  };

  const handleUploadClick = () => {
    const validFiles = flow?.files
      ?.filter((file) => file.size <= maxSize * 1024 * 1024)
      .filter(
        (file) =>
          !store.imageSectionFileNames?.includes(file.name),
      );

    if (validFiles?.length > 0) {
      setUploading(true);
      if (store.imageSectionSubmissionId) {
        flow.opts.query.submissionId =
          store.imageSectionSubmissionId;
      } else {
        store.setImageSectionSubmissionId(submissionId);
        flow.opts.query.submissionId = submissionId;
      }
      validFiles.forEach((file) => {
        const timeout = setTimeout(() => {
          flow.removeFile(file);
          store.setImageSectionFileNames(file.name, "remove");
          setPreviewData((prevPreviewData) =>
            prevPreviewData.map((preview) =>
              preview.fileName === file.name
                ? { ...preview, progress: 0, error: true }
                : preview,
            ),
          );
          console.error(`File upload timed out: ${file.name}`);
        }, 300000);

        flow.on("fileSuccess", (uploadedFile) => {
          if (uploadedFile.name === file.name) {
            clearTimeout(timeout);
          }
        });

        flow.on("fileError", (erroredFile) => {
          if (erroredFile.name === file.name) {
            clearTimeout(timeout);
          }
        });
        flow.upload(file);
      });
    }
  };

  return (
    <div className="p-2">
      <Row>
        <h5 style={{ fontWeight: "600" }}>
          <FormattedMessage id="PHOTOS_SECTION" />{" "}
          {store.imageRequired && "*"}
        </h5>
        <p>
          <FormattedMessage id="SUPPORTED_FILETYPES" />
          {`${" "}${maxSize} MB`}
        </p>
      </Row>
      <Row>
        {store.imageSectionError && (
          <Alert
            variant="danger"
            className="w-100 mt-1 mb-1 ms-2 me-4"
            style={{
              border: "none",
            }}
          >
            <i
              className="bi bi-info-circle-fill"
              style={{ marginRight: "8px", color: theme.statusColors.red800 }}
            ></i>
            <FormattedMessage id="IMAGES_REQUIRED_ANON_WARNING" />{" "}
            <a
              href={`${process.env.PUBLIC_URL}/login?redirect=%2Freport`}
              onClick={() => {
                localStorage.setItem("species", store.speciesSection.value);
                  localStorage.setItem(
                    "followUpSection.submitter.name",
                    store.followUpSection.submitter.name,
                  );
                  localStorage.setItem(
                    "followUpSection.submitter.email",
                    store.followUpSection.submitter.email,
                  );
                  localStorage.setItem(
                    "followUpSection.photographer.name",
                    store.followUpSection.photographer.name,
                  );
                  localStorage.setItem(
                    "followUpSection.photographer.email",
                    store.followUpSection.photographer.email,
                  );
                  localStorage.setItem(
                    "followUpSection.additionalEmails",
                    store.followUpSection.additionalEmails,
                  );
                  localStorage.setItem(
                    "additionalCommentsSection",
                    store.additionalCommentsSection.value,
                  );
                  localStorage.setItem(
                    "uploadedFiles",
                    JSON.stringify(store.imagePreview),
                  );
                  // localStorage.setItem("dateTimeSection", store.dateTimeSection.value);
                  // localStorage.setItem("placeSection", store.placeSection.value);
                  localStorage.setItem(
                    "submissionId",
                    store.imageSectionSubmissionId,
                  );
                  localStorage.setItem(
                    "fileNames",
                    JSON.stringify(store.imageSectionFileNames),
                  );
                  localStorage.setItem(
                    "datetime",
                    store.dateTimeSection.value?.toISOString(),
                  );
                  localStorage.setItem("exifDateTime", store.exifDateTime);
              }}
            >
              <FormattedMessage id="LOGIN_SIGN_IN" />
            </a>
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
                  color: theme.defaultColors.white,
                }}
                onClick={() => {
                  setPreviewData((prevPreviewData) =>
                    prevPreviewData.filter(
                      (previewData) =>
                        previewData.fileName !== preview.fileName,
                    ),
                  );

                  flow.removeFile(
                    files.find((f) => f.name === preview.fileName),
                  );

                  store.setImageSectionFileNames(
                    preview.fileName,
                    "remove",
                  );
                }}
              ></i>
              <Image
                id="thumb"
                src={preview.src}
                style={{ width: "100%", height: "120px", objectFit: "fill" }}
                alt={`Preview ${index + 1}`}
              // thumbnail
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
                  {preview.error && (
                    <span style={{ color: theme.statusColors.red500 }}>
                      <FormattedMessage id="UPLOAD_FAILED" />
                    </span>
                  )}
                </div>
                <div>{(preview.fileSize / (1024 * 1024)).toFixed(2)} MB</div>
                {(preview.fileSize / (1024 * 1024)).toFixed(2) > maxSize && (
                  <div style={{ color: theme.statusColors.red500 }}>
                    <FormattedMessage id="FILE_SIZE_EXCEEDED" />
                  </div>
                )}
              </div>
            </div>
            <ProgressBar
              now={preview.progress}
              label={`${Math.round(preview.progress)}%`}
              className="mt-2"
              style={{
                width: "100%",
                backgroundColor: theme.primaryColors.primary50,
              }}
            />
          </Col>
        ))}

        <Col md={8} style={{ width: previewData.length ? "200px" : "100%" }}>
          <div
            id="drop-area"
            className="d-flex flex-column align-items-center justify-content-center p-4"
            style={{
              border: originalBorder,
              borderRadius: "8px",
              backgroundColor: theme.primaryColors.primary50,
              textAlign: "center",
              cursor: "pointer",
              height: previewData.length ? "120px" : "300px",
              boxSizing: "border-box",
            }}
          >
            {previewData.length ? (
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
                  // disabled={uploading}
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
    </div>
  );
});

export default FileUploader;
