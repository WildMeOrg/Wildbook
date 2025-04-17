// Optimized BulkImportImage component with all logic moved to MobX store
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

export const BulkImportImage = observer(({ store }) => {
  const fileInputRef = useRef(null);
  const theme = useContext(ThemeContext);
  const originalBorder = `1px dashed ${theme.primaryColors.primary500}`;
  const { data } = useGetSiteSettings();
  const maxSize = data?.maximumMediaSizeMegabytes || 40;

  useEffect(() => {
    if (!store.flow && fileInputRef.current) {
      store.initializeFlow(fileInputRef.current, maxSize);
    }
  }, [store.flow, fileInputRef]);

  useEffect(() => {
    store.restoreFromLocalStorage();
  }, []);

  return (
    <div className="p-2">
      <Row>
        <h5 style={{ fontWeight: "600" }}>
          <FormattedMessage id="PHOTOS_SECTION" /> {store.imageRequired && "*"}
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
              <BootstrapImage
                src={preview.src}
                style={{ width: "100%", height: "120px", objectFit: "fill" }}
                alt={`Preview ${index + 1}`}
              />
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

        <Col md={8} style={{ width: store.imagePreview.length ? "200px" : "100%" }}>
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
            onDragEnter={store.handleDragEnter}
            onDragOver={store.handleDragOver}
            onDragLeave={store.handleDragLeave}
            onDrop={(e) => store.handleDrop(e, maxSize)}
          // onDrop={(e) => {
          //   e.preventDefault();
          //   store.handleDropItems(e.dataTransfer.items, maxSize);
          // }}

          >
            {store.imagePreview.length ? (
              <div onClick={() => fileInputRef.current.click()}>
                <i
                  className="bi bi-images"
                  style={{ fontSize: "1rem", color: theme.wildMeColors.cyan700 }}
                ></i>
                <p>
                  <FormattedMessage id="ADD_MORE_FILES" />
                </p>
              </div>
            ) : (
              <div className="mb-3 d-flex flex-column justify-content-center">
                <i
                  className="bi bi-images"
                  style={{ fontSize: "2rem", color: theme.wildMeColors.cyan700 }}
                ></i>
                <p>
                  <FormattedMessage id="PHOTO_INSTRUCTION" />
                </p>
                <MainButton
                  onClick={() => fileInputRef.current.click()}
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
              accept=".jpg,.jpeg,.png,.bmp"
              ref={fileInputRef}
              style={{ display: "none" }}
            />
          </div>
        </Col>
      </Row>
      <Row className="mt-4">
        <MainButton
          onClick={() => {
            store.setActiveStep(1);
          }}
          backgroundColor={theme.wildMeColors.cyan700}
          color={theme.defaultColors.white}
          noArrow={true}
          style={{ width: "auto", fontSize: "1rem", margin: "0 auto" }}
        >
          <FormattedMessage id="NEXT" />
          <i className="bi bi-arrow-right ms-2"></i>
        </MainButton>
      </Row>
    </div>
  );
});
