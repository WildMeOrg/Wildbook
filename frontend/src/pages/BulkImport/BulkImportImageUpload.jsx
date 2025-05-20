import React, { useEffect, useRef, useContext } from "react";
import {
  ProgressBar,
  Image as BootstrapImage,
  Row,
  Col,
  Alert,
} from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import ThemeContext from "../../ThemeColorProvider";
import MainButton from "../../components/MainButton";
import useGetSiteSettings from "../../models/useGetSiteSettings";
import { observer } from "mobx-react-lite";
import BulkImportSeeInstructionsButton from "./BulkImportSeeInstructionsButton"

const handleDragOver = (e) => {
  e.preventDefault();
  e.dataTransfer.dropEffect = "copy";
};

const handleDragLeave = (e) => {
  e.preventDefault();
  e.currentTarget.style.border = "1px dashed #007BFF";
};

export const BulkImportImageUpload = observer(({ store }) => {
  const fileInputRef = useRef(null);
  const theme = useContext(ThemeContext);
  const originalBorder = `1px dashed ${theme.primaryColors.primary500}`;
  const { data } = useGetSiteSettings();
  const maxSize = data?.maximumMediaSizeMegabytes || 3000;

  store.setMaxImageCount(data?.maximumMediaCount || 200);
  // const maxImageCount = data?.maximumMediaCount || 200;
  const currentCount = store.imagePreview.length;

  const handleDragEnter = (e) => {
    e.preventDefault();
    e.currentTarget.style.border = "2px dashed #007BFF";
    // e.dataTransfer.style.backgroundColor = theme.primaryColors.primary100;
  };

  useEffect(() => {
    if (!fileInputRef.current) return;

    if (!store.flow) {
      store.initializeFlow(fileInputRef.current, maxSize);
    } else {
      store.flow.assignBrowse(fileInputRef.current);
    }

    // return () => {
    //   if (store.flow) {
    //     store.flow.cancel();
    //   }
    // };
  }, [store.flow, fileInputRef.current, maxSize]);

  useEffect(() => {
    if (store.imagePreview.length > 0) {
      store.setImageSectionFileNames(
        store.imagePreview.map((preview) => preview.fileName),
      );
      store.uploadFilteredFiles(maxSize);
    }
  }, [JSON.stringify(store.imagePreview), store.imageUploadStatus]);

  const handleDrop = (e) => {
    if (currentCount >= store.maxImageCount) {
      alert(`maximum image count: ${store.maxImageCount} exceeded`);
      return;
    }
    e.preventDefault();

    const items1 = Array.from(e.dataTransfer.items);
    const newFilesCount = items1.filter(item => {
      const entry = item.webkitGetAsEntry?.();
      return entry
        ? entry.isFile
        : item.getAsFile() != null;
    }).length;

    if (currentCount + newFilesCount > store.maxImageCount) {
      alert(`you are choosing ${newFilesCount} iamges, total count exceeding ${store.maxImageCount}`);
      return;
    }
    e.currentTarget.style.border = "1px dashed #007BFF";
    const items = e.dataTransfer.items;
    for (let i = 0; i < items.length; i++) {
      const item = items[i].webkitGetAsEntry?.();
      if (item) {
        store.traverseFileTree(item, maxSize);
      } else {
        const file = items[i].getAsFile();
        if (
          file &&
          ["image/jpeg", "image/png", "image/bmp"].includes(file.type)
        ) {
          store.flow.addFile(file);
        }
      }
    }

    store.uploadFilteredFiles(maxSize);
  };

  return (
    <div className="mt-4">
      <Row >
        <div className="d-flex flex-row justify-content-between">
          <div>
            <h5 style={{ fontWeight: "600" }}>
              <FormattedMessage id="BULK_IMPORT_UPLOAD_IMAGE" /> {store.imageRequired && "*"}
            </h5>
            <p>
              <FormattedMessage id="BULK_IMPORT_UPLOAD_IMAGE_DESC"
                values={{
                  maxSize: maxSize,
                  maxImageCount: store.maxImageCount,
                }}
              />

            </p>
          </div>
          <BulkImportSeeInstructionsButton store={store} />
        </div>
      </Row>

      <Row>
        {store.imageSectionError && (
          <Alert
            variant="danger"
            className="w-100 mt-1 mb-1 ms-2 me-4"
            style={{ border: "none" }}
          >
            <i
              className="bi bi-info-circle-fill"
              style={{ marginRight: "8px", color: theme.statusColors.red800 }}
            ></i>
            <FormattedMessage id="IMAGES_REQUIRED_ANON_WARNING" />{" "}
            <a
              href={`${process.env.PUBLIC_URL}/login?redirect=%2Freport`}
              onClick={store.handleLoginRedirect}
            >
              <FormattedMessage id="LOGIN_SIGN_IN" />
            </a>
          </Alert>
        )}
      </Row>

      <Row className="mt-4 w-100">
        {store.imagePreview.map((preview, index) => (
          <Col
            key={index}
            className="mb-4 me-4 d-flex flex-column justify-content-between"
            style={{ maxWidth: "200px", position: "relative" }}
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
                onClick={() => store.removePreview(preview.fileName)}
              ></i>
              {/* <BootstrapImage
                src={preview.src}
                style={{ width: "100%", height: "120px", objectFit: "fill" }}
                alt={`Preview ${index + 1}`}
              /> */}
              {preview.showThumbnail && preview.src ? (
                <BootstrapImage
                  src={preview.src}
                  style={{ width: "100%", height: "120px", objectFit: "fill" }}
                  alt={`Preview ${index + 1}`}
                />
              ) : (
                <div style={{ height: "120px", display: "flex", alignItems: "center", justifyContent: "center" }}>
                  <i className="bi bi-file-image" style={{ fontSize: "2rem", color: theme.primaryColors.primary700 }}></i>
                </div>
              )}

              <div
                className="mt-2"
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
                <div>
                  {(preview.fileSize / (1024 * 1024)).toFixed(2)} MB
                  {(preview.fileSize / (1024 * 1024)).toFixed(2) > maxSize && (
                    <div style={{ color: theme.statusColors.red500 }}>
                      <FormattedMessage id="FILE_SIZE_EXCEEDED" />
                    </div>
                  )}
                </div>
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

        <Col
          md={8}
          style={{ width: store.imagePreview.length ? "200px" : "100%" }}
        >
          <div
            id="drop-area"
            className="d-flex flex-column align-items-center justify-content-center p-4"
            style={{
              border: originalBorder,
              borderRadius: "8px",
              backgroundColor: theme.primaryColors.primary50,
              textAlign: "center",
              cursor: "pointer",
              height: store.imagePreview.length ? "120px" : "300px",
              boxSizing: "border-box",
            }}
            onDragEnter={handleDragEnter}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={(e) => handleDrop(e)}
          >
            {store.imagePreview.length ? (
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
                  <FormattedMessage id="BULK_IMPORT_PHOTO_INSTRUCTION" />
                </p>
                <MainButton
                  onClick={() => {
                    if (currentCount >= store.maxImageCount) {
                      alert(`exceeding ${store.maxImageCount}`);
                    } else {
                      fileInputRef.current.click();
                    }
                    // fileInputRef.current.click()
                  }
                  }
                  backgroundColor={theme.wildMeColors.cyan700}
                  color={theme.defaultColors.white}
                  noArrow={true}
                  style={{ width: "auto", fontSize: "1rem", margin: "0 auto" }}
                >
                  <FormattedMessage id="BROWSE" />
                </MainButton>
              </div>
            )}
            <input
              type="file"
              id="file-chooser"
              multiple
              // webkitdirectory="true"
              webkitdirectory=""
              directory=""
              accept=".jpg,.jpeg,.png,.bmp"
              ref={fileInputRef}
              style={{ display: "none" }}
              onChange={(e) => {
                // Handle file selection
                e.target.value = "";
              }}
            />
          </div>
        </Col>
      </Row>
      <Row className="mt-4 justify-content-end">
        <MainButton
          onClick={() => {
            store.setActiveStep(1);
          }}
          backgroundColor={theme.wildMeColors.cyan700}
          color={theme.defaultColors.white}
          noArrow={true}
          style={{ width: "auto", fontSize: "1rem", }}
        >
          <FormattedMessage id="BULK_IMPORT_NEXT" />
        </MainButton>
      </Row>
    </div>
  );
});
