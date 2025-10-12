import React, { useEffect, useRef, useState } from "react";
import { Swiper, SwiperSlide } from "swiper/react";
import "swiper/css";
import { observer } from "mobx-react-lite";
import {
  addExistingKeyword,
  addNewKeywordText,
  removeKeyword,
  addExistingLabeledKeyword,
} from "../utils/keywordsFunctions";
import PillWithButton from "./PillWithButton";
import { FormattedMessage } from "react-intl";
import MainButton from "../components/MainButton";
import ThemeColorContext from "../ThemeColorProvider";

export const ImageModal = observer(
  ({
    open,
    onClose,
    assets = [],
    index = 0,
    setIndex,
    rects = [],
    imageStore = {},
  }) => {
    const themeColor = React.useContext(ThemeColorContext);
    const thumbsRef = useRef(null);
    const imgRef = useRef(null);
    const [scaleX, setScaleX] = useState(1);
    const [scaleY, setScaleY] = useState(1);
    const currentAnnotation =
      rects.filter(
        (a) => a.annotationId === imageStore.selectedAnnotationId,
      )?.[0] || null;
    const [editAnnotationParams, setEditAnnotationParams] = useState({});

    useEffect(() => {
      if (!currentAnnotation) return;
      setEditAnnotationParams({
        x: currentAnnotation.x || 0,
        y: currentAnnotation.y || 0,
        width: currentAnnotation.width || 0,
        height: currentAnnotation.height || 0,
        theta: currentAnnotation.rotation || 0,
      });
    }, [currentAnnotation]);

    const annotationParam = encodeURIComponent(
      JSON.stringify(editAnnotationParams),
    );
    const [tagText, setTagText] = useState("");

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
      if (!open) return;

      const onKey = (e) => {
        if (e.key === "Escape") onClose?.();
        if (e.key === "ArrowLeft")
          setIndex?.((p) =>
            Math.max(0, (typeof p === "number" ? p : index) - 1),
          );
        if (e.key === "ArrowRight")
          setIndex?.((p) =>
            Math.min(
              assets.length - 1,
              (typeof p === "number" ? p : index) + 1,
            ),
          );
      };
      const prevOverflow = document.body.style.overflow;
      document.body.style.overflow = "hidden";
      window.addEventListener("keydown", onKey);
      return () => {
        document.body.style.overflow = prevOverflow || "";
        window.removeEventListener("keydown", onKey);
      };
    }, [open, onClose, setIndex, index, assets.length]);

    if (!open || !assets.length) return null;

    const safeIndex = Math.min(Math.max(index, 0), assets.length - 1);
    const a = assets[safeIndex] || {};

    // const canPrev = safeIndex > 0;
    // const canNext = safeIndex < assets.length - 1;

    // const goPrev = () => {
    //     setIndex?.((p) => {
    //         const cur = typeof p === "number" ? p : safeIndex;
    //         return Math.max(0, cur - 1);
    //     });
    // };

    // const goNext = () => {
    //     setIndex?.((p) => {
    //         const cur = typeof p === "number" ? p : safeIndex;
    //         return Math.min(assets.length - 1, cur + 1);
    //     });
    // };

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
          style={{ minHeight: 0 }}
        >
          {/* <div
                    className="d-flex align-items-center text-white"
                    style={{ flex: "0 0 56px" }}
                >
                    <span className="text-white-50 ms-2">{safeIndex + 1}/{assets.length}</span>
                    <div className="ms-auto d-flex gap-2 me-2">
                        <button className="btn btn-sm" onClick={onClose}>Close</button>
                    </div>
                </div> */}

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

                {/* <div className="ms-auto d-flex gap-2 me-2">
                                <button className="btn btn-sm" onClick={onClose}>Close</button>
                            </div> */}
              </div>

              <div
                id="image-modal-image"
                className="d-flex justify-content-center position-relative overflow-hidden"
                style={{ flex: "1 1 auto", minHeight: 0 }}
              >
                {/* <button
                                type="button"
                                aria-label="Previous image"
                                className={`btn btn-sm btn-outline-light rounded-circle position-absolute top-50 start-0 translate-middle-y ms-2 ${canPrev ? "" : "opacity-50 pe-none"}`}
                                onClick={(e) => { e.stopPropagation(); goPrev(); }}
                                onMouseDown={(e) => e.preventDefault()}
                                disabled={!canPrev}
                            >
                                <i className="bi bi-chevron-left" />
                            </button> */}
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
                        onClick={() => {
                          imageStore.setSelectedAnnotationId(rect.annotationId);
                        }}
                        style={{
                          left: rect.x / scaleX,
                          top: rect.y / scaleY,
                          width: rect.width / scaleX,
                          height: rect.height / scaleY,
                          border: "2px solid red",
                          transform: `rotate(${rect.rotation}rad)`,
                          cursor: "pointer",
                          backgroundColor:
                            rect.annotationId ===
                            imageStore.selectedAnnotationId
                              ? "rgba(240, 11, 11, 0.5)"
                              : "transparent",
                        }}
                      />
                    ))}
                </div>

                {/* <button
                                type="button"
                                aria-label="Next image"
                                className={`btn btn-sm btn-outline-light rounded-circle position-absolute top-50 end-0 translate-middle-y me-2 ${canNext ? "" : "opacity-50 pe-none"}`}
                                onClick={(e) => { e.stopPropagation(); goNext(); }}
                                onMouseDown={(e) => e.preventDefault()}
                                disabled={!canNext}
                            >
                                <i className="bi bi-chevron-right" />
                            </button> */}
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
                    style={{ objectFit: "cover", overFlow: "hidden" }}
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
                    {imageStore.encounterData?.date}
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
                {(imageStore.tags ?? []).map((tag) => (
                  <PillWithButton
                    key={tag.id}
                    text={tag.displayName || tag.name}
                    onClose={async () => {
                      const data = await removeKeyword(
                        imageStore.encounterData?.mediaAssets[
                          imageStore.selectedImageIndex
                        ]?.id,
                        tag.id,
                      );
                      if (data?.success === true) {
                        await imageStore.refreshEncounterData();
                      } else {
                        console.error("Failed to remove tag:", data);
                      }
                    }}
                  />
                ))}
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
                  + <FormattedMessage id="ADD_TAG" />
                </button>
                {imageStore.addTagsFieldOpen && (
                  <div>
                    <p>
                      <FormattedMessage id="ADD_NEW_KEYWORD" />
                    </p>
                    <div className="input-group mb-3">
                      <input
                        type="text"
                        className="form-control"
                        placeholder="New tag"
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
                              imageStore.encounterData?.mediaAssets[
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
                              console.error("Failed to add new tag:", result);
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
                          Select existing keyword...
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
                          color: imageStore.selectedKeyword ? "white" : "black",
                        }}
                        onClick={async () => {
                          if (imageStore.selectedKeyword) {
                            const result = await addExistingKeyword(
                              imageStore.encounterData?.mediaAssets[
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
                              console.error(
                                "Failed to add existing tag:",
                                result,
                              );
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
                        className="form-select"
                        onChange={async (e) => {
                          const selectedValue = e.target.value;
                          imageStore.setSelectedLabeledKeyword(selectedValue);
                        }}
                        value={imageStore.selectedLabeledKeyword || ""}
                      >
                        <option value="" disabled>
                          Select existing labeled keyword...
                        </option>
                        {(imageStore.availabelLabeledKeywords || []).map(
                          (keyword) => (
                            <option key={keyword} value={keyword}>
                              {keyword}
                            </option>
                          ),
                        )}
                      </select>
                      <div className="input-group mt-3">
                        <select
                          className="form-select"
                          onChange={async (e) => {
                            const selectedValue = e.target.value;
                            imageStore.setSelectedAllowedValues(selectedValue);
                          }}
                          defaultValue=""
                          value={imageStore.selectedAllowedValues || ""}
                        >
                          <option value="" disabled>
                            Select Allowed Valus...
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
                              imageStore.setSelectedLabeledKeyword &&
                              imageStore.selectedAllowedValues
                                ? themeColor?.wildMeColors?.cyan700
                                : "lightgray",
                            color: imageStore.selectedKeyword
                              ? "white"
                              : "black",
                          }}
                          onClick={async () => {
                            if (
                              imageStore.setSelectedLabeledKeyword &&
                              imageStore.selectedAllowedValues
                            ) {
                              const result = await addExistingLabeledKeyword(
                                imageStore.encounterData?.mediaAssets[
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
                                console.error(
                                  "Failed to add existing tag:",
                                  result,
                                );
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
                  <FormattedMessage id="INDIVIDUAL_ID" />
                </dt>
                <dd className="col-7 mb-0">
                  {imageStore.encounterData.individualId ?? "—"}
                </dd>
                <dt className="col-5">
                  <FormattedMessage id="LOCATION_ID" />
                </dt>
                <dd className="col-7 mb-0">
                  {imageStore.encounterData.locationId ?? "—"}
                </dd>
                <dt className="col-5">
                  <FormattedMessage id="VERBATIM_EVENT_DATE" />
                </dt>
                <dd className="col-7 mb-0">
                  {imageStore.encounterData.verbatimEventDate ?? "—"}
                </dd>
              </dl>

              <div className="d-grid gap-2">
                <MainButton
                  noArrow={true}
                  color="white"
                  backgroundColor={themeColor?.wildMeColors?.cyan700}
                  borderColor={themeColor?.wildMeColors?.cyan700}
                  target={true}
                  disabled={!imageStore.matchResultClickable}
                  onClick={() => {
                    window.open(
                      `/iaResults.jsp?taskId=${currentAnnotation?.iaTaskId}`,
                      "_blank",
                    );
                  }}
                >
                  <FormattedMessage id="MATCH_RESULTS" />
                </MainButton>

                <MainButton
                  noArrow={true}
                  color="white"
                  backgroundColor={themeColor?.wildMeColors?.cyan700}
                  borderColor={themeColor?.wildMeColors?.cyan700}
                  target={true}
                  onClick={() => {
                    if (
                      !imageStore.encounterData?.mediaAssets[
                        imageStore.selectedImageIndex
                      ]
                    ) {
                      return;
                    }
                    const number = imageStore.encounterData?.id;
                    const mediaAssetId =
                      imageStore.encounterData?.mediaAssets[
                        imageStore.selectedImageIndex
                      ]?.id;
                    const url = `/encounters/encounterVM.jsp?number=${encodeURIComponent(number)}&mediaAssetId=${encodeURIComponent(mediaAssetId)}`;
                    window.open(url, "_blank");
                  }}
                >
                  <FormattedMessage id="VISUAL_MATCHER" />
                </MainButton>

                <MainButton
                  noArrow={true}
                  color="white"
                  backgroundColor={themeColor?.wildMeColors?.cyan700}
                  borderColor={themeColor?.wildMeColors?.cyan700}
                  target={true}
                  onClick={() => {
                    if (
                      !imageStore.encounterData?.mediaAssets[
                        imageStore.selectedImageIndex
                      ]
                    ) {
                      return;
                    }
                    imageStore.modals.setOpenMatchCriteriaModal(true);
                  }}
                >
                  <FormattedMessage id="NEW_MATCH" />
                </MainButton>

                <MainButton
                  noArrow={true}
                  backgroundColor="white"
                  color={themeColor?.wildMeColors?.cyan700}
                  borderColor={themeColor?.wildMeColors?.cyan700}
                  target={true}
                  onClick={() => {
                    if (
                      !imageStore.encounterData?.mediaAssets[
                        imageStore.selectedImageIndex
                      ]
                    ) {
                      return;
                    }
                    window.open(
                      `/react/manual-annotation?encounterId=${imageStore.encounterData?.id}&assetId=${assets[index]?.id}`,
                      "_blank",
                    );
                  }}
                >
                  <FormattedMessage id="ADD_ANNOTATION" />
                </MainButton>
                <MainButton
                  noArrow={true}
                  disabled={!currentAnnotation?.annotationId}
                  backgroundColor="white"
                  color={themeColor?.wildMeColors?.cyan700}
                  borderColor={themeColor?.wildMeColors?.cyan700}
                  target={true}
                  onClick={() => {
                    if (
                      !imageStore.encounterData?.mediaAssets[
                        imageStore.selectedImageIndex
                      ] ||
                      !annotationParam ||
                      !assets[index]?.id
                    ) {
                      return;
                    }
                    window.open(
                      `/react/edit-annotation?encounterId=${imageStore.encounterData?.id}&assetId=${assets[index]?.id}&annotation=${annotationParam}&annotationId=${currentAnnotation?.annotationId}`,
                      "_blank",
                    );
                  }}
                >
                  <FormattedMessage id="EDIT_ANNOTATION" />
                </MainButton>
                <MainButton
                  noArrow={true}
                  backgroundColor="white"
                  disabled={!currentAnnotation?.annotationId}
                  color={themeColor?.wildMeColors?.cyan700}
                  borderColor={themeColor?.wildMeColors?.cyan700}
                  target={true}
                  onClick={async () => {
                    if (
                      window.confirm(
                        "Are you sure you want to delete this annotation?",
                      )
                    ) {
                      await imageStore.removeAnnotation(
                        currentAnnotation?.annotationId,
                      );
                      // window.location.reload();
                      imageStore.setSelectedAnnotationId(null);
                      imageStore.refreshEncounterData();
                    }
                  }}
                >
                  <FormattedMessage id="DELETE_ANNOTATION" />
                </MainButton>
                <h5 className="text-danger mt-3">
                  <FormattedMessage id="DANGER_ZONE" />
                </h5>
                <MainButton
                  onClick={async () => {
                    if (
                      window.confirm(
                        "Are you sure you want to delete this image?",
                      )
                    ) {
                      await imageStore.deleteImage();
                      window.location.reload();
                    }
                  }}
                  shadowColor={themeColor.statusColors.red500}
                  color={themeColor.statusColors.red500}
                  noArrow={true}
                  style={{
                    width: "auto",
                    height: "40px",
                    fontSize: "1rem",
                    border: `1px solid ${themeColor.statusColors.red500}`,
                    marginTop: "1rem",
                    marginBottom: "2rem",
                  }}
                >
                  <FormattedMessage id="DELETE_IMAGE" />
                </MainButton>
              </div>
            </aside>
          </div>
        </div>
      </div>
    );
  },
);

export default ImageModal;
