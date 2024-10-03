import React, { useState, useEffect, useRef } from "react";
import {
  Button,
  ProgressBar,
  Image,
  Container,
  Row,
  Col,
} from "react-bootstrap";
import Flow from "@flowjs/flow.js"; // 或者使用 require 导入

const FileUploader = () => {
  const [files, setFiles] = useState([]);
  const [flow, setFlow] = useState(null);
  const [progress, setProgress] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [fileActivity, setFileActivity] = useState(false);
  const [previewSrc, setPreviewSrc] = useState("");
  const fileInputRef = useRef(null); // 使用 useRef 来获取文件选择器

  // 在组件挂载时初始化 flow.js 实例
  useEffect(() => {
    if (!flow && fileInputRef.current) {
      initializeFlow();
    }
  }, [flow, fileInputRef]);

  // 初始化 Flow.js
  const initializeFlow = () => {
    const flowInstance = new Flow({
      target: "ResumableUpload",
      forceChunkSize: true,
      testChunks: false,
    });

    flowInstance.assignBrowse(fileInputRef.current); // 确保文件选择器绑定正确
    setFlow(flowInstance);

    flowInstance.on("fileAdded", (file) => {
      const reader = new FileReader();
      reader.onloadend = () => {
        setPreviewSrc(reader.result);
      };
      reader.readAsDataURL(file.file);

      setFiles((prevFiles) => [...prevFiles, file]);
      setFileActivity(true);
    });

    flowInstance.on("fileProgress", (file) => {
      const percentage = (file._prevUploadedSize / file.size) * 100;
      setProgress(percentage);
    });

    flowInstance.on("fileSuccess", (file) => {
      setUploading(false);
      setProgress(100);
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

  const handleReselectClick = () => {
    setFiles([]);
    setPreviewSrc("");
    setProgress(0);
    setFileActivity(false);
    setUploading(false);
  };

  return (
    <Container>
      <Row>
        <Col>
          <input
            type="file"
            id="file-chooser"
            multiple
            accept="audio/*,video/*,image/*"
            ref={fileInputRef} // 使用 ref 绑定文件选择器
            style={{ display: fileActivity ? "none" : "block" }}
          />
          {fileActivity && (
            <div id="file-activity">
              {files.map((file, index) => (
                <div key={index}>
                  <div>{file.name}</div>
                  <div>{(file.size / (1024 * 1024)).toFixed(2)} MB</div>
                </div>
              ))}
            </div>
          )}
          {previewSrc && (
            <Image id="thumb" src={previewSrc} width="200" alt="Preview" />
          )}
        </Col>
      </Row>
      {fileActivity && (
        <Row>
          <Col>
            <Button
              id="reselect-button"
              variant="secondary"
              onClick={handleReselectClick}
            >
              Choose a different file
            </Button>
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
      {uploading && (
        <Row>
          <Col>
            <ProgressBar now={progress} label={`${Math.round(progress)}%`} />
          </Col>
        </Row>
      )}
    </Container>
  );
};

export default FileUploader;
