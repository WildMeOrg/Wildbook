import React, { useContext } from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import ThemeColorContext from "../../../ThemeColorProvider";

const formatFileSize = (bytes) => {
  if (!bytes) return "";
  const units = ["B", "KB", "MB", "GB"];
  let size = bytes;
  let unitIndex = 0;
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex++;
  }
  return `${size.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
};

const getFileIcon = (filename) => {
  const ext = filename?.split(".").pop()?.toLowerCase();
  switch (ext) {
    case "jpg":
    case "jpeg":
    case "png":
    case "gif":
    case "bmp":
      return "bi-image";
    case "pdf":
      return "bi-file-pdf";
    case "xlsx":
    case "xls":
      return "bi-file-earmark-spreadsheet";
    case "doc":
    case "docx":
      return "bi-file-earmark-word";
    case "txt":
      return "bi-file-text";
    default:
      return "bi-file-earmark";
  }
};

const AdditionalFilesCard = observer(({ store }) => {
  const theme = useContext(ThemeColorContext);
  const files = store.additionalFiles || [];

  return (
    <div
      className="mt-3 mb-3"
      style={{
        padding: "10px",
        borderRadius: "10px",
        boxShadow: "0px 0px 10px rgba(0, 0, 0, 0.2)",
        width: "100%",
      }}
    >
      <div
        className="d-flex align-items-center w-100 mb-3"
        style={{ fontSize: "1rem", fontWeight: "bold" }}
      >
        <span>
          <FormattedMessage id="ADDITIONAL_FILES" />
        </span>
      </div>

      {files.length > 0 ? (
        <div className="d-flex flex-column gap-2">
          {files.map((file, index) => (
            <div
              key={index}
              className="d-flex align-items-center justify-content-between p-2"
              style={{
                backgroundColor: theme.grayColors.gray50,
                borderRadius: "6px",
              }}
            >
              <div className="d-flex align-items-center gap-2">
                <div
                  style={{
                    width: "32px",
                    height: "32px",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    backgroundColor: theme.primaryColors.primary50,
                    borderRadius: "4px",
                  }}
                >
                  <i
                    className={`bi ${getFileIcon(file.filename || file.name)}`}
                    style={{ color: theme.primaryColors.primary500 }}
                  />
                </div>
                <div>
                  <p className="mb-0" style={{ fontWeight: 500 }}>
                    {file.filename || file.name || "Unknown file"}
                  </p>
                  <small className="text-muted">
                    {formatFileSize(file.size || file.fileSize)}
                  </small>
                </div>
              </div>
              <div
                style={{ cursor: "pointer", padding: "4px" }}
                onClick={() => {
                  // TODO: Implement file options menu
                  console.log("File options:", file);
                }}
              >
                <i
                  className="bi bi-three-dots-vertical"
                  style={{ color: theme.grayColors.gray500 }}
                />
              </div>
            </div>
          ))}
        </div>
      ) : (
        <p className="text-muted mb-0">
          <FormattedMessage id="NO_ADDITIONAL_FILES" defaultMessage="No additional files" />
        </p>
      )}
    </div>
  );
});

export default AdditionalFilesCard;
