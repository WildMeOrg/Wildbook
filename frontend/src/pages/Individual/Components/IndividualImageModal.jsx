import React, { useEffect, useRef, useState } from "react";
import { Swiper, SwiperSlide } from "swiper/react";
import "swiper/css";
import { observer } from "mobx-react-lite";
import {
  addExistingKeyword,
  addNewKeywordText,
  removeKeyword,
  addExistingLabeledKeyword,
} from "../../../utils/keywordsFunctions";
import PillWithButton from "../../../components/PillWithButton";
import { FormattedMessage } from "react-intl";
import MainButton from "../../../components/MainButton";
import ThemeColorContext from "../../../ThemeColorProvider";
import { useIntl } from "react-intl";
import Tooltip from "../../../components/ToolTip";

export const IndividualImageModal = observer(
  ({
    onClose,
    assets = [],
    index = 0,
    setIndex,
    rects = [],
    imageStore = {},
  }) => {
    if (!assets || !assets.length) return null;
    const intl = useIntl();
    const themeColor = React.useContext(ThemeColorContext);
    const thumbsRef = useRef(null);
    const imgRef = useRef(null);
    const [scaleX, setScaleX] = useState(1);
    const [scaleY, setScaleY] = useState(1);

    const safeIndex = Math.min(Math.max(index, 0), assets.length - 1);
    const a = assets[safeIndex] || {};

    const [zoom, setZoom] = useState(1);
    const [pan, setPan] = useState({ x: 0, y: 0 });
    const [dragStart, setDragStart] = useState(null);

    useEffect(() => {
      setPan({ x: 0, y: 0 });
    }, [zoom, safeIndex]);

    const onMouseDown = (e) => {
      if (e.button !== 0) return;
      if (zoom <= 1) return;
      e.preventDefault();
      setDragStart({ x: e.clientX, y: e.clientY, startPan: pan });
    };

    useEffect(() => {
      if (!dragStart) return;

      const handleMouseMove = (e) => {
        const dx = e.clientX - dragStart.x;
        const dy = e.clientY - dragStart.y;
        setPan({
          x: dragStart.startPan.x + dx,
          y: dragStart.startPan.y + dy,
        });
      };

      const handleMouseUp = () => {
        setDragStart(null);
      };

      window.addEventListener("mousemove", handleMouseMove);
      window.addEventListener("mouseup", handleMouseUp);
      document.body.style.userSelect = "none";

      return () => {
        window.removeEventListener("mousemove", handleMouseMove);
        window.removeEventListener("mouseup", handleMouseUp);
        document.body.style.userSelect = "";
      };
    }, [dragStart]);

    const canPrev = safeIndex > 0;
    const canNext = safeIndex < assets.length - 1;
    const goPrev = () => {
      setIndex?.(safeIndex - 1);
    };
    const goNext = () => {
      setIndex?.(safeIndex + 1);
    };

    const [tagText, setTagText] = useState("");
    const [errorMsg, setErrorMsg] = useState("");

    useEffect(() => {
      const s = thumbsRef.current;
      if (!s || s.destroyed) return;
      const target = Math.max(0, Math.min(index - 1, assets.length - 1));
      s.slideTo(target, 250);
      const naturalWidth = assets[index]?.width;
      const naturalHeight = assets[index]?.height;
      const displayWidth = imgRef.current.clientWidth;
      const displayHeight = imgRef.current.clientHeight;

      setScaleX(naturalWidth / displayWidth);
      setScaleY(naturalHeight / displayHeight);
    }, [index, assets.length]);

    useEffect(() => {
      const handleClickOutside = (event) => {
        const imageBox = document.getElementById("image-modal-image-box");
        if (!imageBox || !imageBox.contains(event.target)) {
          return;
        }
        const clickedOnAnnotation = rects.some((_, index) => {
          const rectElement = document.getElementById(
            `annotation-rect-${index}`,
          );
          return rectElement && rectElement.contains(event.target);
        });

        if (!clickedOnAnnotation) {
          imageStore.setSelectedAnnotationId(null);
        }
      };

      document.addEventListener("mousedown", handleClickOutside);

      return () => {
        document.removeEventListener("mousedown", handleClickOutside);
      };
    }, [rects, imageStore]);

    const boxRef = React.useRef(null);
    const handleEnter = (text) => setTip((s) => ({ ...s, show: true, text }));
    const handleMove = (e) => {
      if (dragStart) return;
      const el = boxRef.current;
      if (!el) return;
      const r = el.getBoundingClientRect();
      const relX = e.clientX - r.left;
      const relY = e.clientY - r.top;
      setTip((s) => ({ ...s, x: relX / zoom, y: relY / zoom }));
    };
    const handleLeave = () => setTip({ show: false, x: 0, y: 0, text: "" });
    const [tip, setTip] = React.useState({ show: false, x: 0, y: 0, text: "" });

    const maxArea = React.useMemo(() => {
      return rects.reduce(
        (max, r) => Math.max(max, (r.width || 0) * (r.height || 0)),
        1,
      );
    }, [rects]);

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
          style={{ minHeight: 0, padding: 0 }}
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
                      color: "white",
                    }}
                    onClick={() => setZoom((z) => Math.min(3, z + 0.25))}
                    title="Zoom In"
                  >
                    <i className="bi bi-zoom-in"></i>
                  </button>

                  <button
                    type="button"
                    className="btn btn-sm rounded-circle"
                    style={{
                      backgroundColor: "rgba(255, 255, 255, 0.5)",
                      marginRight: "8px",
                      color: "white",
                    }}
                    onClick={() => setZoom(1)}
                    title="Reset Zoom"
                  >
                    <i className="bi bi-zoom-out"></i>
                  </button>

                  <button
                    type="button"
                    className="btn btn-sm rounded-circle"
                    style={{
                      backgroundColor: "rgba(255, 255, 255, 0.5)",
                      marginRight: "8px",
                    }}
                    onClick={() => {
                      const cur = assets[index];
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
                <button
                  type="button"
                  style={{ zIndex: 100 }}
                  aria-label="Previous image"
                  className={`btn btn-sm btn-outline-light rounded-circle position-absolute top-50 start-0 translate-middle-y ms-2 ${canPrev ? "" : "opacity-50 pe-none"}`}
                  onClick={(e) => {
                    e.stopPropagation();
                    goPrev();
                  }}
                  onMouseDown={(e) => e.preventDefault()}
                  disabled={!canPrev}
                >
                  <i className="bi bi-chevron-left" />
                </button>
                <div
                  id="image-modal-image-container"
                  className="position-relative d-flex justify-content-center align-items-center"
                  style={{
                    width: "100%",
                    height: "100%",
                    maxHeight: "100vh",
                    overflow: "hidden",
                  }}
                >
                  <div
                    id="image-modal-image-box"
                    ref={boxRef}
                    onMouseDown={onMouseDown}
                    style={{
                      transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`,
                      transformOrigin: "center center",
                      transition: dragStart ? "none" : "transform 0.2s ease",
                      position: "relative",
                      display: "inline-block",
                      overflow: "hidden",
                      cursor:
                        zoom > 1
                          ? dragStart
                            ? "grabbing"
                            : "grab"
                          : "default",
                    }}
                  >
                    <img
                      id="image-modal-main-image"
                      draggable={false}
                      onDragStart={(e) => e.preventDefault()}
                      src={a.url}
                      ref={imgRef}
                      alt={`asset-${a.id ?? safeIndex}`}
                      className="img-fluid"
                      style={{
                        display: "block",
                        maxWidth: "100%",
                        maxHeight: "90vh",
                        width: "auto",
                        height: "auto",
                        objectFit: "contain",
                        margin: "auto",
                      }}
                      onLoad={() => {
                        const iw = imgRef.current?.clientWidth || 1;
                        const ih = imgRef.current?.clientHeight || 1;
                        setScaleX((assets[safeIndex]?.width || iw) / iw);
                        setScaleY((assets[safeIndex]?.height || ih) / ih);
                      }}
                    />
                    <Tooltip show={tip.show} x={tip.x} y={tip.y}>
                      {tip.text}
                    </Tooltip>
                    {imageStore.showAnnotations &&
                      rects.length > 0 &&
                      rects.map((rect, index) => {
                        let newRect = { ...rect };
                        if (
                          imageStore.encounterData?.mediaAssets?.[
                            imageStore.selectedImageIndex
                          ]?.rotationInfo
                        ) {
                          const imgW =
                            imageStore.encounterData?.mediaAssets?.[
                              imageStore.selectedImageIndex
                            ]?.width;
                          const imgH =
                            imageStore.encounterData?.mediaAssets?.[
                              imageStore.selectedImageIndex
                            ]?.height;
                          const adjW = imgH / imgW;
                          const adjH = imgW / imgH;
                          newRect = {
                            ...rect,
                            x: rect.x / scaleX / adjW,
                            width: rect.width / scaleX / adjW,
                            y: rect.y / scaleY / adjH,
                            height: rect.height / scaleY / adjH,
                          };
                        } else {
                          newRect = {
                            ...rect,
                            x: rect.x / scaleX,
                            y: rect.y / scaleY,
                            width: rect.width / scaleX,
                            height: rect.height / scaleY,
                          };
                        }

                        const area = (rect.width || 0) * (rect.height || 0);
                        const score = 1 - area / maxArea;
                        const baseZ = 10 + Math.round(score * 1000);
                        const finalZ =
                          rect.annotationId === imageStore.selectedAnnotationId
                            ? 2000
                            : baseZ;

                        return (
                          <div
                            id={`annotation-rect-${index}`}
                            key={index}
                            onMouseEnter={() =>
                              handleEnter(
                                `${newRect.encounterId === imageStore.encounterData.id ? `${intl.formatMessage({ id: "THIS_ENCOUNTER" })}` : `encounter ${newRect.encounterId}`}\nViewpoint: ${newRect.viewpoint}\nIA Class: ${newRect.iaClass}`,
                              )
                            }
                            onMouseMove={handleMove}
                            onMouseLeave={handleLeave}
                            className="position-absolute"
                            onClick={() => {
                              imageStore.setSelectedAnnotationId(
                                rect.annotationId,
                              );
                            }}
                            style={{
                              left: newRect.x,
                              top: newRect.y,
                              width: newRect.width,
                              height: newRect.height,
                              border: "2px solid red",
                              transform: `rotate(${rect.rotation}rad)`,
                              cursor: "pointer",
                              zIndex: finalZ,
                              backgroundColor:
                                rect.annotationId ===
                                imageStore.selectedAnnotationId
                                  ? "rgba(240, 11, 11, 0.2)"
                                  : "transparent",
                            }}
                          ></div>
                        );
                      })}
                  </div>
                </div>

                <button
                  type="button"
                  style={{ zIndex: 100 }}
                  aria-label="Next image"
                  className={`btn btn-sm btn-outline-light rounded-circle position-absolute top-50 end-0 translate-middle-y me-2 ${canNext ? "" : "opacity-50 pe-none"}`}
                  onClick={(e) => {
                    e.stopPropagation();
                    goNext();
                  }}
                  onMouseDown={(e) => e.preventDefault()}
                  disabled={!canNext}
                >
                  <i className="bi bi-chevron-right" />
                </button>
              </div>

              <div style={{ flex: "0 0 110px" }}>
                <Swiper
                  slidesPerView="auto"
                  spaceBetween={8}
                  style={{ padding: "8px 12px" }}
                  onSwiper={(s) => (thumbsRef.current = s)}
                >
                  {assets.map((item, i) => (
                    <SwiperSlide
                      key={item.uuid ?? item.id ?? i}
                      style={{ width: 84 }}
                    >
                      <img
                        src={item.url}
                        alt={item.filename ?? ""}
                        onClick={() => {
                          setIndex?.(i);
                          thumbsRef.current?.slideTo(Math.max(0, i - 1), 250);
                        }}
                        style={{
                          width: 72,
                          height: 72,
                          objectFit: "cover",
                          cursor: "pointer",
                          borderRadius: 6,
                          border:
                            i === safeIndex
                              ? "2px solid #fff"
                              : "2px solid transparent",
                        }}
                      />
                    </SwiperSlide>
                  ))}
                </Swiper>
              </div>
            </div>

            <aside
              id="image-modal-right"
              className="bg-white text-black ps-3 pe-3 pt-2 d-flex flex-column h-100"
              style={{ flex: "0 0 360px", minHeight: 0, overflowY: "auto" }}
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
                    Encounter{" "}
                    {imageStore.encounterData?.individualDisplayName
                      ? `of ${imageStore.encounterData?.individualDisplayName}`
                      : "Unassigned "}
                    <p>{imageStore.encounterData?.date}</p>
                  </div>
                  <div className="text-muted small">{a.date ?? ""}</div>
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
              <div className="d-flex flex-wrap gap-2 mb-3">
                {errorMsg && (
                  <div className="alert alert-danger" role="alert">
                    {errorMsg}
                  </div>
                )}

                {(imageStore.tags ?? []).map((tag) => (
                  <PillWithButton
                    key={tag.id}
                    text={tag.displayName || tag.name}
                    onClose={async () => {
                      const data = await removeKeyword(
                        imageStore.encounterData?.mediaAssets?.[
                          imageStore.selectedImageIndex
                        ]?.id,
                        tag.id,
                      );
                      if (data?.success === true) {
                        await imageStore.refreshEncounterData();
                      } else {
                        setErrorMsg("Failed to remove tag:");
                      }
                    }}
                  />
                ))}
                {imageStore.access === "write" && (
                  <button
                    className="btn btn-sm"
                    style={{
                      cursor: "pointer",
                      backgroundColor: themeColor?.wildMeColors?.cyan700,
                      color: "white",
                      borderRadius: "20px",
                    }}
                    onClick={async () => {
                      imageStore.setAddTagsFieldOpen(
                        !imageStore.addTagsFieldOpen,
                      );
                    }}
                  >
                    + <FormattedMessage id="ADD_KEYWORD" />
                  </button>
                )}
                {imageStore.access === "write" &&
                  imageStore.addTagsFieldOpen && (
                    <div>
                      <p>
                        <FormattedMessage id="ADD_NEW_KEYWORD" />
                      </p>
                      <div className="input-group mb-3">
                        <input
                          type="text"
                          className="form-control"
                          placeholder={intl.formatMessage({
                            id: "NEW_KEYWORD",
                          })}
                          onChange={(e) => {
                            const text = e.target.value.trim();
                            setTagText(text);
                          }}
                          value={tagText}
                        />

                        <button
                          className="btn"
                          style={{
                            cursor: "pointer",
                            backgroundColor: tagText
                              ? themeColor?.wildMeColors?.cyan700
                              : "lightgray",
                            color: tagText ? "white" : "black",
                          }}
                          onClick={async () => {
                            if (tagText) {
                              const result = await addNewKeywordText(
                                imageStore.encounterData?.mediaAssets?.[
                                  imageStore.selectedImageIndex
                                ]?.id,
                                tagText,
                              );
                              if (result?.success === true) {
                                imageStore.setAddTagsFieldOpen(false);
                                setTagText("");
                                imageStore.setSelectedKeyword(null);
                                imageStore.setAddTagsFieldOpen(false);
                                await imageStore.refreshEncounterData();
                              } else {
                                setErrorMsg("Failed to add new tag");
                              }
                            }
                          }}
                        >
                          <FormattedMessage id="ADD" />
                        </button>
                      </div>
                      <p className="muted">
                        <FormattedMessage id="SELECT_KEYWORD" />
                      </p>
                      <div className="input-group mb-3">
                        <select
                          className="form-select"
                          onChange={async (e) => {
                            const selectedValue = e.target.value;
                            imageStore.setSelectedKeyword(selectedValue);
                          }}
                          value={imageStore.selectedKeyword || ""}
                        >
                          <option value="" disabled>
                            <FormattedMessage id="SELECT_EXISTING_KEYWORD" />
                          </option>
                          {(imageStore.availableKeywords || []).map(
                            (keyword, index) => (
                              <option
                                key={index}
                                value={imageStore.availableKeywordsId[index]}
                              >
                                {keyword}
                              </option>
                            ),
                          )}
                        </select>
                        <button
                          className="btn"
                          disabled={!imageStore.selectedKeyword}
                          style={{
                            cursor: "pointer",
                            backgroundColor: imageStore.selectedKeyword
                              ? themeColor?.wildMeColors?.cyan700
                              : "lightgray",
                            color: imageStore.selectedKeyword
                              ? "white"
                              : "black",
                          }}
                          onClick={async () => {
                            if (imageStore.selectedKeyword) {
                              const result = await addExistingKeyword(
                                imageStore.encounterData?.mediaAssets?.[
                                  imageStore.selectedImageIndex
                                ]?.id,
                                imageStore.selectedKeyword,
                              );
                              if (result?.success === true) {
                                imageStore.setAddTagsFieldOpen(false);
                                imageStore.setSelectedKeyword(null);
                                setTagText("");
                                imageStore.setAddTagsFieldOpen(false);
                                await imageStore.refreshEncounterData();
                              } else {
                                setErrorMsg("Failed to add existing tag:");
                              }
                            }
                          }}
                        >
                          <FormattedMessage id="ADD" />
                        </button>
                      </div>
                      <p>
                        <FormattedMessage id="SELECT_LABELED_KEYWORD" />
                      </p>
                      <div className="mb-3">
                        <select
                          className="form-select mb-2"
                          onChange={async (e) => {
                            const selectedValue = e.target.value;
                            imageStore.setSelectedLabeledKeyword(selectedValue);
                          }}
                          value={imageStore.selectedLabeledKeyword || ""}
                        >
                          <option value="" disabled>
                            <FormattedMessage id="SELECT_EXISTING_LABELLED_KEYWORD" />
                          </option>
                          {(imageStore.availabelLabeledKeywords || []).map(
                            (keyword) => (
                              <option key={keyword} value={keyword}>
                                {keyword}
                              </option>
                            ),
                          )}
                        </select>
                        <p>
                          <FormattedMessage id="SELECT_LABELED_KEYWORD_VALUE" />
                        </p>
                        <div className="input-group">
                          <select
                            className="form-select"
                            onChange={async (e) => {
                              const selectedValue = e.target.value;
                              imageStore.setSelectedAllowedValues(
                                selectedValue,
                              );
                            }}
                            defaultValue=""
                            value={imageStore.selectedAllowedValues || ""}
                          >
                            <option value="" disabled>
                              <FormattedMessage id="SELECT_ALLOWED_VALUES" />
                            </option>
                            {(imageStore.labeledKeywordAllowedValues || []).map(
                              (keyword) => (
                                <option key={keyword} value={keyword}>
                                  {keyword}
                                </option>
                              ),
                            )}
                          </select>
                          <button
                            className="btn"
                            disabled={
                              !imageStore.selectedLabeledKeyword ||
                              !imageStore.selectedAllowedValues
                            }
                            style={{
                              cursor: "pointer",
                              backgroundColor:
                                imageStore.selectedLabeledKeyword &&
                                imageStore.selectedAllowedValues
                                  ? themeColor?.wildMeColors?.cyan700
                                  : "lightgray",
                              color: imageStore.selectedKeyword
                                ? "white"
                                : "black",
                            }}
                            onClick={async () => {
                              if (
                                imageStore.selectedLabeledKeyword &&
                                imageStore.selectedAllowedValues
                              ) {
                                const result = await addExistingLabeledKeyword(
                                  imageStore.encounterData?.mediaAssets?.[
                                    imageStore.selectedImageIndex
                                  ]?.id,
                                  imageStore.selectedLabeledKeyword,
                                  imageStore.selectedAllowedValues,
                                );
                                if (result?.success === true) {
                                  imageStore.setAddTagsFieldOpen(false);
                                  imageStore.setSelectedLabeledKeyword(null);
                                  imageStore.setSelectedAllowedValues(null);
                                  setTagText("");
                                  imageStore.setSelectedKeyword(null);
                                  imageStore.setAddTagsFieldOpen(false);
                                  await imageStore.refreshEncounterData();
                                } else {
                                  setErrorMsg("Failed to add existing tag:");
                                }
                              }
                            }}
                          >
                            <FormattedMessage id="ADD" />
                          </button>
                        </div>
                      </div>
                    </div>
                  )}
              </div>

              <dl className="row g-2 mb-3">
                <dt className="col-5">
                  <FormattedMessage id="ENCOUNTER" />
                </dt>
                <dd className="col-7 mb-0">
                  {imageStore.encounterData.id ?? "—"}
                </dd>

                <dt className="col-5">
                  <FormattedMessage id="LOCATION_ID" />
                </dt>
                <dd className="col-7 mb-0">
                  {imageStore.encounterData.locationId ?? "—"}
                </dd>
                <dt className="col-5">
                  <FormattedMessage id="SIGHTING_DATE" />
                </dt>
                <dd className="col-7 mb-0">
                  {imageStore.encounterData.sightingDate ?? "—"}
                </dd>
                <dt className="col-5">
                  <FormattedMessage id="VERBATIM_EVENT_DATE" />
                </dt>
                <dd className="col-7 mb-0">
                  {imageStore.encounterData.verbatimEventDate ?? "—"}
                </dd>
              </dl>

              <div className="mb-4">
                <div
                  className="mb-3"
                  style={{ fontSize: "1rem", fontWeight: 700, lineHeight: 1.2 }}
                >
                  Co-occurrences
                </div>

                {(imageStore.coOccurrences || []).map((item, idx) => (
                  <div
                    key={item.id || idx}
                    className="d-flex align-items-start gap-3 mb-4"
                  >
                    <img
                      src={item.thumbnailUrl}
                      alt={item.name || item.encounterId || "co-occurrence"}
                      style={{
                        width: 84,
                        height: 84,
                        borderRadius: "50%",
                        objectFit: "cover",
                        flexShrink: 0,
                      }}
                    />

                    <div style={{ minWidth: 0, lineHeight: 1.25 }}>
                      <div style={{ marginTop: 6, marginBottom: 6 }}>
                        {item.name ? (
                          <a
                            href={item.href || "#"}
                            className="text-decoration-underline"
                            style={{
                              fontSize: "1.25rem",
                              fontWeight: 400,
                              color: "#0077a8",
                            }}
                            onClick={(e) => {
                              if (!item.href) e.preventDefault();
                            }}
                          >
                            {item.name}
                          </a>
                        ) : (
                          <span
                            style={{ fontSize: "1.25rem", fontWeight: 600 }}
                          >
                            Encounter of{" "}
                            <a
                              href={item.href || "#"}
                              className="text-decoration-underline"
                              style={{
                                color: "#0077a8",
                                fontWeight: 400,
                              }}
                              onClick={(e) => {
                                if (!item.href) e.preventDefault();
                              }}
                            >
                              Unknown
                            </a>
                          </span>
                        )}
                      </div>

                      <div
                        style={{
                          fontSize: "1.15rem",
                          color: "#3f3f3f",
                          wordBreak: "break-word",
                        }}
                      >
                        {item.encounterId}
                      </div>
                    </div>
                  </div>
                ))}
              </div>

              {imageStore.access === "write" && (
                <div className="d-grid mt-auto pt-2 pb-3">
                  <MainButton
                    style={{
                      margin: "5px 0",
                    }}
                    noArrow={true}
                    backgroundColor="white"
                    color={themeColor?.wildMeColors?.cyan700}
                    borderColor={themeColor?.wildMeColors?.cyan700}
                    target={true}
                    onClick={() => {
                      window.open(
                        `/react/encounter?number=${imageStore.encounterData?.id}`,
                        "_blank",
                      );
                    }}
                  >
                    <FormattedMessage id="SEE_ENCOUNTER_DETAILS" />
                  </MainButton>
                  <MainButton
                    style={{
                      margin: "5px 0",
                    }}
                    noArrow={true}
                    backgroundColor="white"
                    color={themeColor?.wildMeColors?.cyan700}
                    borderColor={themeColor?.wildMeColors?.cyan700}
                    target={true}
                    onClick={() => {
                      window.open(
                        `/react/sighting?number=${imageStore.encounterData?.id}`,
                        "_blank",
                      );
                    }}
                  >
                    <FormattedMessage id="SEE_SIGHTING_DETAILS" />
                  </MainButton>
                </div>
              )}
            </aside>
          </div>
        </div>
      </div>
    );
  },
);

export default IndividualImageModal;
