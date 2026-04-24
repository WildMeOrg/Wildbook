import React from "react";
import { Modal } from "react-bootstrap";
import ZoomInIcon from "../icons/ZoomInIcon";
import ZoomOutIcon from "../icons/ZoomOutIcon";
import InteractiveAnnotationOverlay from "../../../components/AnnotationOverlay";

const styles = {
  body: {
    padding: 12,
    background: "#111",
    height: "100vh",
  },
  grid: {
    height: "calc(100vh - 24px)",
    display: "flex",
    gap: 12,
  },
  panel: {
    flex: 1,
    minWidth: 0,
    borderRadius: 10,
    overflow: "hidden",
    background: "#1a1a1a",
    position: "relative",
    boxShadow: "0 2px 14px rgba(0,0,0,0.35)",
  },
  imageWrap: {
    position: "relative",
    width: "100%",
    height: "100%",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    background: "#111",
  },
  topRight: {
    position: "absolute",
    top: 10,
    right: 10,
    zIndex: 80,
    display: "flex",
    gap: 8,
  },
  iconBtn: {
    width: 34,
    height: 34,
    borderRadius: 10,
    background: "rgba(255,255,255,0.92)",
    border: "1px solid rgba(0,0,0,0.10)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    cursor: "pointer",
    boxShadow: "0 2px 10px rgba(0,0,0,0.25)",
  },
  overlayHost: {
    width: "100%",
    height: "100%",
  },
};

const CloseIcon = () => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    width="14"
    height="14"
    viewBox="0 0 14 14"
    fill="none"
  >
    <path
      d="M13 1 1 13M1 1l12 12"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
    />
  </svg>
);

export default function InspectorModal({
  show,
  onHide,
  imageUrl,
  originalWidth,
  originalHeight,
}) {
  const overlayRef = React.useRef(null);

  React.useEffect(() => {
    if (!show) return;
    const t = setTimeout(() => overlayRef.current?.reset?.(), 0);
    return () => clearTimeout(t);
  }, [show, imageUrl]);

  return (
    <Modal
      show={show}
      onHide={onHide}
      fullscreen
      centered={false}
      keyboard
      contentClassName="border-0 rounded-0"
      id="inspector-modal"
      data-testid="inspector-modal"
    >
      <div
        style={styles.body}
        id="inspector-modal-body"
        data-testid="inspector-modal-body"
      >
        <div
          style={styles.grid}
          id="inspector-modal-grid"
          data-testid="inspector-modal-grid"
        >
          <div
            style={styles.panel}
            id="inspector-modal-panel"
            data-testid="inspector-modal-panel"
          >
            <div
              style={styles.imageWrap}
              id="inspector-modal-image-wrap"
              data-testid="inspector-modal-image-wrap"
            >
              <div
                style={styles.topRight}
                id="inspector-modal-toolbar"
                data-testid="inspector-modal-toolbar"
              >
                <div
                  style={styles.iconBtn}
                  title="Zoom In"
                  onClick={() => overlayRef.current?.zoomIn?.()}
                  id="inspector-modal-zoom-in"
                  data-testid="inspector-modal-zoom-in"
                >
                  <ZoomInIcon />
                </div>

                <div
                  style={styles.iconBtn}
                  title="Zoom Out"
                  onClick={() => overlayRef.current?.zoomOut?.()}
                  id="inspector-modal-zoom-out"
                  data-testid="inspector-modal-zoom-out"
                >
                  <ZoomOutIcon />
                </div>

                <div
                  style={styles.iconBtn}
                  title="Close"
                  onClick={onHide}
                  id="inspector-modal-close"
                  data-testid="inspector-modal-close"
                >
                  <CloseIcon />
                </div>
              </div>

              <div
                style={styles.overlayHost}
                id="inspector-modal-overlay-scroll"
                data-testid="inspector-modal-overlay-scroll"
              >
                <InteractiveAnnotationOverlay
                  ref={overlayRef}
                  imageUrl={imageUrl}
                  originalWidth={originalWidth}
                  originalHeight={originalHeight}
                  annotations={[]}
                  showAnnotations={false}
                  containerStyle={{ width: "100%", height: "100%" }}
                  imageStyle={{
                    width: "100%",
                    height: "100%",
                    objectFit: "contain",
                  }}
                />
              </div>
            </div>
          </div>
        </div>
      </div>
    </Modal>
  );
}
