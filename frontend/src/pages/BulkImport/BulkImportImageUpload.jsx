import React, { useEffect, useRef, useContext, useState } from "react";
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
import BulkImportSeeInstructionsButton from "./BulkImportSeeInstructionsButton";
import { FixedSizeList as List } from "react-window";

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
  const maxSize = data?.maximumMediaSizeMegabytes || 300000;

  const [isProcessingDrop, setIsProcessingDrop] = useState(false);
  const [renderMode, setRenderMode] = useState("list");
  const THUMBNAIL_THRESHOLD = 200;

  store.setMaxImageCount(data?.maximumMediaCount || 5000);
  // const maxImageCount = data?.maximumMediaCount || 200;
  const currentCount = store.imagePreview.length;

  useEffect(() => {
    console.log("Files parsed++++++++++++++++:", store.filesParsed);
    if (store.filesParsed) {
      setIsProcessingDrop(false);
      setRenderMode(
        store.imagePreview.length > THUMBNAIL_THRESHOLD ? "list" : "grid",
      );
      store.generateThumbnailsForFirst200();
    }
    store.setFilesParsed(false);
  }, [store.filesParsed]);

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
      if (store.filesParsed) {
        store.uploadFilteredFiles(maxSize);
      }
    }
  }, [store.imagePreview.length]);

  const handleDrop = (e) => {
    if (currentCount >= store.maxImageCount) {
      alert(`maximum image count: ${store.maxImageCount} exceeded`);
      return;
    }
    e.preventDefault();
    setIsProcessingDrop(true);
    store.setFilesParsed(false);

    const items1 = Array.from(e.dataTransfer.items);
    const newFilesCount = items1.filter((item) => {
      const entry = item.webkitGetAsEntry?.();
      return entry ? entry.isFile : item.getAsFile() != null;
    }).length;

    if (currentCount + newFilesCount > store.maxImageCount) {
      alert(
        `you are choosing ${newFilesCount} iamges, total count exceeding ${store.maxImageCount}`,
      );
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
      <Row>
        <div className="d-flex flex-row justify-content-between">
          <div>
            <h5 style={{ fontWeight: "600" }}>
              <FormattedMessage id="BULK_IMPORT_UPLOAD_IMAGE" />{" "}
              {store.imageRequired && "*"}
            </h5>
            <p>
              <FormattedMessage
                id="BULK_IMPORT_UPLOAD_IMAGE_DESC"
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

      <Row className="mt-4 mb-4 w-100">
        {isProcessingDrop && (
          <div className="text-center p-4">
            <FormattedMessage
              id="PROCESSING_IMAGES"
              defaultMessage="Processing images..."
            />
          </div>
        )}

        {!isProcessingDrop && renderMode === "grid" && (
          <Row className="mb-4 ms-2">
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
                      top: "-10px",
                      right: "-5px",
                      cursor: "pointer",
                      fontSize: "1.2rem",
                      color: theme.primaryColors.primary500,
                      zIndex: 10,
                    }}
                    title="Remove image"
                    onClick={() => store.removePreview(preview.fileName)}
                  ></i>

                  {preview.showThumbnail && preview.src ? (
                    <BootstrapImage
                      src={preview.src}
                      style={{
                        width: "100%",
                        height: "120px",
                        objectFit: "fill",
                      }}
                      alt={`Preview ${index + 1}`}
                    />
                  ) : (
                    <div
                      style={{
                        height: "120px",
                        backgroundColor: "#f8f9fa",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                      }}
                    >
                      <i
                        className="bi bi-file-image"
                        style={{
                          fontSize: "2rem",
                          color: theme.primaryColors.primary700,
                        }}
                      ></i>
                    </div>
                  )}

                  <div
                    className="mt-2"
                    style={{
                      width: "200px",
                      wordWrap: "break-word",
                      whiteSpace: "normal",
                      fontSize: "0.9rem",
                    }}
                  >
                    <div className="text-truncate" title={preview.fileName}>
                      {preview.fileName}
                    </div>
                    {preview.error && (
                      <div style={{ color: theme.statusColors.red500 }}>
                        <FormattedMessage id="UPLOAD_FAILED" />
                      </div>
                    )}
                    <div>
                      {(preview.fileSize / (1024 * 1024)).toFixed(2)} MB
                      {(preview.fileSize / (1024 * 1024)).toFixed(2) >
                        maxSize && (
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
          </Row>
        )}

        {!isProcessingDrop &&
          store.imagePreview.length > 0 &&
          renderMode === "list" && (
            <List
              height={600}
              itemCount={store.imagePreview.length}
              itemSize={90}
              width="50%"
              itemData={store.imagePreview.slice()}
            >
              {({ index, style }) => {
                const preview = store.imagePreview[index];
                return (
                  <div
                    style={{
                      ...style,
                      boxSizing: "border-box",
                      paddingRight: "16px",
                    }}
                  >
                    <div
                      style={{
                        backgroundColor: "#fff",
                        borderRadius: "8px",
                        boxShadow: "0 1px 3px rgba(0,0,0,0.1)",
                        // padding: "12px 16px",
                        display: "flex",
                        flexDirection: "column",
                        justifyContent: "center",
                      }}
                    >
                      <div
                        style={{
                          display: "flex",
                          justifyContent: "space-between",
                          alignItems: "center",
                          marginBottom: "8px",
                        }}
                      >
                        <div
                          style={{
                            flex: 1,
                            overflow: "hidden",
                            textOverflow: "ellipsis",
                            whiteSpace: "nowrap",
                            fontWeight: 500,
                          }}
                          title={preview.fileName}
                        >
                          {preview.fileName}
                        </div>
                        <div
                          style={{
                            marginLeft: "16px",
                            minWidth: "60px",
                            textAlign: "right",
                          }}
                        >
                          {(preview.fileSize / (1024 * 1024)).toFixed(1)} MB
                        </div>
                        <div
                          style={{ marginLeft: "16px", cursor: "pointer" }}
                          onClick={() => store.removePreview(preview.fileName)}
                        >
                          <i
                            className="bi bi-trash-fill"
                            style={{ color: "#dc3545", fontSize: "1.2rem" }}
                          ></i>
                        </div>
                      </div>

                      <ProgressBar
                        now={preview.progress}
                        className="custom-progress"
                        variant="info"
                        style={{
                          height: "8px",
                          borderRadius: "4px",
                          backgroundColor: theme.primaryColors.primary50,
                        }}
                      />
                    </div>
                  </div>
                );
              }}
            </List>
          )}
      </Row>
      <Row>
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
                  }}
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
              // webkitdirectory=""
              // directory=""
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
          style={{ width: "auto", fontSize: "1rem" }}
        >
          <FormattedMessage id="NEXT" />
        </MainButton>
      </Row>
    </div>
  );
});
