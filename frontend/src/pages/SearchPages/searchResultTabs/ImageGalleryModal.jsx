import React, { useEffect, useRef, useState } from "react";
import "swiper/css";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";

export const ImageGalleryModal = observer(
  ({ open, onClose, assets = [], index = 0, rects = [], imageStore = {} }) => {
    const thumbsRef = useRef(null);
    const imgRef = useRef(null);
    const [scaleX, setScaleX] = useState(1);
    const [scaleY, setScaleY] = useState(1);

    const encounterData = assets[index] || {};

    useEffect(() => {
      const s = thumbsRef.current;
      if (!s || s.destroyed) return;
      const target = Math.max(0, Math.min(index - 1, assets.length - 1));
      s.slideTo(target, 250);
      const naturalWidth = assets[safeIndex]?.width;
      const naturalHeight = assets[safeIndex]?.height;
      const displayWidth = imgRef.current.clientWidth;
      const displayHeight = imgRef.current.clientHeight;

      setScaleX(naturalWidth / displayWidth);
      setScaleY(naturalHeight / displayHeight);
    }, [index, assets.length]);

    if (!open || !assets.length) return null;

    const safeIndex = Math.min(Math.max(index, 0), assets.length - 1);
    const a = assets[safeIndex] || {};

    return (
      <div
        id="image-modal"
        role="dialog"
        aria-modal="true"
        className="position-fixed top-0 start-0 w-100 h-100"
        style={{
          background: "rgba(0, 0, 0, 0.6)",
          backdropFilter: "blur(2px)",
          WebkitBackdropFilter: "blur(2px)",
          color: "white",
          zIndex: 1080,
        }}
      >
        <div
          id="image-modal-content"
          className="container-fluid h-100 d-flex flex-column"
          style={{ minHeight: 0, paddingRight: "0" }}
        >
          <div
            id="image-modal-body"
            className="d-flex"
            style={{ flex: "1 1 auto", minHeight: 0 }}
          >
            <div
              id="image-modal-left"
              className="d-flex flex-column flex-grow-1 w-100 position-relative"
              style={{ minWidth: 0, minHeight: 0 }}
            >
              <div
                className="w-100 d-flex flex-row align-items-center text-white p-2
                            justify-content-between"
              >
                <div className="text-white-50 ms-2">
                  {safeIndex + 1}/{assets.length}
                </div>
                <div
                  className="m-2"
                  style={{
                    marginLeft: "auto",
                  }}
                >
                  <button
                    type="button"
                    className="btn btn-sm rounded-circle"
                    style={{
                      backgroundColor: "rgba(255, 255, 255, 0.5)",
                      marginRight: "8px",
                    }}
                    onClick={() => {
                      const cur = assets[safeIndex];
                      if (!cur?.url) return;
                      const a = document.createElement("a");
                      a.href = cur.url;
                      a.download =
                        cur.filename ||
                        `encounter-image-${cur.id || index}.jpg`;
                      a.click();
                    }}
                    aria-label="Download"
                    title="Download"
                  >
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      width="15"
                      height="18"
                      viewBox="0 0 15 18"
                      fill="none"
                    >
                      <path
                        d="M12.5783 6.5H10.9883V1.5C10.9883 0.95 10.5383 0.5 9.98828 0.5H5.98828C5.43828 0.5 4.98828 0.95 4.98828 1.5V6.5H3.39828C2.50828 6.5 2.05828 7.58 2.68828 8.21L7.27828 12.8C7.66828 13.19 8.29828 13.19 8.68828 12.8L13.2783 8.21C13.9083 7.58 13.4683 6.5 12.5783 6.5ZM0.988281 16.5C0.988281 17.05 1.43828 17.5 1.98828 17.5H13.9883C14.5383 17.5 14.9883 17.05 14.9883 16.5C14.9883 15.95 14.5383 15.5 13.9883 15.5H1.98828C1.43828 15.5 0.988281 15.95 0.988281 16.5Z"
                        fill="white"
                      />
                    </svg>
                  </button>
                  <button
                    type="button"
                    className="btn btn-sm rounded-circle"
                    onClick={onClose}
                    style={{
                      backgroundColor: "rgba(255, 255, 255, 0.5)",
                    }}
                    aria-label="Close"
                  >
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      width="15"
                      height="15"
                      viewBox="0 0 25 24"
                      fill="none"
                    >
                      <path
                        d="M19.288 5.70973C18.898 5.31973 18.268 5.31973 17.878 5.70973L12.988 10.5897L8.09801 5.69973C7.70801 5.30973 7.07801 5.30973 6.68801 5.69973C6.29801 6.08973 6.29801 6.71973 6.68801 7.10973L11.578 11.9997L6.68801 16.8897C6.29801 17.2797 6.29801 17.9097 6.68801 18.2997C7.07801 18.6897 7.70801 18.6897 8.09801 18.2997L12.988 13.4097L17.878 18.2997C18.268 18.6897 18.898 18.6897 19.288 18.2997C19.678 17.9097 19.678 17.2797 19.288 16.8897L14.398 11.9997L19.288 7.10973C19.668 6.72973 19.668 6.08973 19.288 5.70973Z"
                        fill="white"
                      />
                    </svg>
                  </button>
                </div>
              </div>

              <div
                id="image-modal-image"
                className="d-flex justify-content-center position-relative overflow-hidden"
                style={{ flex: "1 1 auto", minHeight: 0 }}
              >
                <div
                  className="position-relative"
                  style={{ maxWidth: "90vw", maxHeight: "80vh" }}
                >
                  <img
                    id="image-modal-main-image"
                    src={a.url}
                    ref={imgRef}
                    alt={`asset-${a.id ?? safeIndex}`}
                    className="img-fluid"
                    style={{
                      display: "block",
                      maxWidth: "100%",
                      maxHeight: "80vh",
                      width: "auto",
                      height: "auto",
                      objectFit: "contain",
                      margin: "0 auto",
                    }}
                    onLoad={() => {
                      const iw = imgRef.current?.clientWidth || 1;
                      const ih = imgRef.current?.clientHeight || 1;
                      setScaleX((assets[safeIndex]?.width || iw) / iw);
                      setScaleY((assets[safeIndex]?.height || ih) / ih);
                    }}
                  />
                  {imageStore.showAnnotations &&
                    rects.length > 0 &&
                    rects.map((rect, index) => (
                      <div
                        key={index}
                        className="position-absolute"
                        style={{
                          left: rect.x / scaleX,
                          top: rect.y / scaleY,
                          width: rect.width / scaleX,
                          height: rect.height / scaleY,
                          border: "2px dotted red",
                          transform: `rotate(${rect.rotation}rad)`,
                          cursor: "pointer",
                        }}
                      >
                        <div
                          className="d-flex"
                          style={{
                            position: "absolute",
                            top: 0,
                            right: -2,
                            zIndex: 20,
                          }}
                          onClick={(e) => e.stopPropagation()}
                        >
                          <div
                            className="d-flex align-items-center justify-content-center"
                            style={{
                              width: "18px",
                              height: "18px",
                              backgroundColor: "red",
                              cursor: "pointer",
                              color: "white",
                            }}
                            onClick={() => {
                              if (!rect.encounterId) return;
                              const url = `/react/encounter?number=${rect.encounterId}`;
                              window.open(url, "_blank");
                            }}
                          >
                            <svg
                              width="16"
                              height="16"
                              viewBox="0 0 16 16"
                              fill="none"
                              xmlns="http://www.w3.org/2000/svg"
                            >
                              <path
                                d="M13.8333 13.8333H2.16667V2.16667H8V0.5H2.16667C1.24167 0.5 0.5 1.25 0.5 2.16667V13.8333C0.5 14.75 1.24167 15.5 2.16667 15.5H13.8333C14.75 15.5 15.5 14.75 15.5 13.8333V8H13.8333V13.8333ZM9.66667 0.5V2.16667H12.6583L4.46667 10.3583L5.64167 11.5333L13.8333 3.34167V6.33333H15.5V0.5H9.66667Z"
                                fill="white"
                              />
                            </svg>
                          </div>
                        </div>
                      </div>
                    ))}
                </div>
              </div>
            </div>

            <aside
              id="image-modal-right"
              className="bg-white text-black ps-3 pe-3 pt-2"
              style={{
                flex: "0 0 360px",
                minHeight: 0,
                overflowY: "auto",
                overflowX: "auto",
              }}
            >
              <div className="d-flex align-items-center gap-2 mb-2">
                {a.url ? (
                  <img
                    src={a.url}
                    alt="thumbnail"
                    className="rounded-circle"
                    width={36}
                    height={36}
                    style={{ objectFit: "cover", overflow: "hidden" }}
                  />
                ) : (
                  <div
                    className="rounded-circle bg-light"
                    style={{ width: 36, height: 36 }}
                  />
                )}
                <div>
                  <div className="fw-semibold">
                    <FormattedMessage id="ENCOUNTER" />{" "}
                    <span>
                      {encounterData?.individualDisplayName
                        ? `of ${encounterData?.individualDisplayName}`
                        : "Unassigned "}
                    </span>
                    <br />
                    {encounterData?.date}
                  </div>
                </div>
              </div>
              <div></div>
              <div className="form-check form-switch mb-3">
                <input
                  className="form-check-input"
                  type="checkbox"
                  id="show-annotations-switch"
                  checked={imageStore.showAnnotations}
                  onChange={(e) =>
                    imageStore.setShowAnnotations(e.target.checked)
                  }
                />
                <label
                  className="form-check-label"
                  htmlFor="show-annotations-switch"
                >
                  <FormattedMessage id="SHOW_ANNOTATIONS" />
                </label>
              </div>

              <dl className="row g-2 mb-3">
                <dt className="col-5">
                  <FormattedMessage id="ENCOUNTER" />
                </dt>
                <dd className="col-7 mb-0">
                  {encounterData.encounterId ? (
                    <a
                      href={`/react/encounter?number=${encounterData.encounterId}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-decoration-none"
                    >
                      {encounterData.encounterId}
                    </a>
                  ) : (
                    "—"
                  )}
                </dd>
                <dt className="col-5">
                  <FormattedMessage id="INDIVIDUAL_ID" />
                </dt>
                <dd className="col-7 mb-0">
                  {encounterData?.individualId ? (
                    <a
                      href={`/individuals.jsp?id=${encounterData?.individualId}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-decoration-none"
                    >
                      {encounterData?.individualId}
                    </a>
                  ) : (
                    "—"
                  )}
                </dd>
                <dt className="col-5">
                  <FormattedMessage id="LOCATION_ID" />
                </dt>
                <dd className="col-7 mb-0">
                  {encounterData?.locationId ?? "—"}
                </dd>
                <dt className="col-5">
                  <FormattedMessage id="VERBATIM_EVENT_DATE" />
                </dt>
                <dd className="col-7 mb-0">
                  {encounterData?.verbatimEventDate ?? "—"}
                </dd>
              </dl>
            </aside>
          </div>
        </div>
      </div>
    );
  },
);

export default ImageGalleryModal;
