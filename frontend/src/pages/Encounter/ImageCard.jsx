import React, { useState, useEffect, useRef, useContext } from "react";
import MailIcon from "../../components/icons/MailIcon";
import { observer } from "mobx-react-lite";
import ImageModal from "../../components/ImageModal";
import ThemeColorContext from "../../ThemeColorProvider";
import { FormattedMessage } from "react-intl";
import ImageIcon from "../../components/icons/ImageIcon";
import FullscreenIcon from "../../components/icons/FullscreenIcon";
import MatchResultIcon from "../../components/icons/MatchResultIcon";
import RefreshIcon from "../../components/icons/RefreshIcon";
import PencilIcon from "../../components/icons/PencilIcon";
import EyeIcon from "../../components/icons/EyeIcon";
import Tooltip from "../../components/ToolTip";
import axios from "axios";
import { useIntl } from "react-intl";

const ImageCard = observer(({ store = {} }) => {
  const imgRef = useRef(null);
  const [rects, setRects] = useState([]);
  const [scaleX, setScaleX] = useState(1);
  const [scaleY, setScaleY] = useState(1);
  const [openImageModal, setOpenImageModal] = useState(false);
  const fileInputRef = useRef(null);
  const maxSize = store.siteSettingsData?.maximumMediaSizeMegabytes || 3;
  const theme = useContext(ThemeColorContext);
  const boxRef = React.useRef(null);
  const [tip, setTip] = React.useState({ show: false, x: 0, y: 0, text: "" });
  const [clickedAnnotation, setClickedAnnotation] = useState(null);
  const [editAnnotationParams, setEditAnnotationParams] = useState({});
  const intl = useIntl();

  useEffect(() => {
    store.setIntl(intl);
  }, [store, intl]);

  const currentAnnotation =
    store.encounterAnnotations.filter(
      (a) => a.id === store.imageModal.selectedAnnotationId,
    )?.[0] || null;

  useEffect(() => {
    if (!currentAnnotation) return;
    setEditAnnotationParams({
      x: currentAnnotation.boundingBox[0] || 0,
      y: currentAnnotation.boundingBox[1] || 0,
      width: currentAnnotation.boundingBox[2] || 0,
      height: currentAnnotation.boundingBox[3] || 0,
      theta: currentAnnotation.theta || 0,
      viewpoint: currentAnnotation.viewpoint || "",
      iaClass: currentAnnotation.iaClass || "",
    });
  }, [currentAnnotation]);

  const annotationParam = encodeURIComponent(
    JSON.stringify(editAnnotationParams),
  );

  const handleEnter = (text) => setTip((s) => ({ ...s, show: true, text }));
  const handleMove = (e) => {
    const r = boxRef.current.getBoundingClientRect();
    const x = e.clientX - r.left;
    const y = e.clientY - r.top;

    const tooltipWidth = 150;
    const tooltipHeight = 60;
    const padding = 30;

    let finalX = x + padding;
    let finalY = y + padding;

    if (x + tooltipWidth + padding > r.width) {
      finalX = x - tooltipWidth - padding;
    }
    if (y + tooltipHeight + padding > r.height) {
      finalY = y - tooltipHeight - padding;
    }
    if (finalX < 0) {
      finalX = padding;
    }
    if (finalY < 0) {
      finalY = padding;
    }

    setTip((s) => ({ ...s, x: finalX, y: finalY }));
  };
  const handleLeave = () => setTip({ show: false, x: 0, y: 0, text: "" });

  useEffect(() => {
    if (
      store.encounterData &&
      store.encounterData?.mediaAssets &&
      store.encounterData?.mediaAssets?.length > 0
    ) {
      const selectedImage =
        store.encounterData.mediaAssets?.[store.selectedImageIndex];
      const annotations = selectedImage?.annotations;
      if (annotations?.length > 0) {
        const anns = selectedImage?.annotations || [];
        setRects(
          anns
            .filter((data) => !data.isTrivial)
            ?.map((a) => ({
              x: a.boundingBox[0],
              y: a.boundingBox[1],
              width: a.boundingBox[2],
              height: a.boundingBox[3],
              rotation: a.theta || 0,
              annotationId: a.id,
              encounterId: a.encounterId,
              viewpoint: a.viewpoint,
              iaClass: a.iaClass,
            })),
        );
      } else {
        setRects([]);
      }
    } else {
      setRects([]);
    }
  }, [store.encounterData, store.selectedImageIndex]);

  useEffect(() => {
    if (!imgRef.current) return;
    const handleImageLoad = () => {
      if (imgRef.current) {
        const naturalWidth =
          store.encounterData?.mediaAssets?.[store.selectedImageIndex]?.width;
        const naturalHeight =
          store.encounterData?.mediaAssets?.[store.selectedImageIndex]?.height;
        const displayWidth = imgRef.current.clientWidth;
        const displayHeight = imgRef.current.clientHeight;

        setScaleX(naturalWidth / displayWidth);
        setScaleY(naturalHeight / displayHeight);
      }
    };

    const imgElement = imgRef.current;
    if (imgElement && imgElement.complete) {
      handleImageLoad();
    } else if (imgElement) {
      imgElement.addEventListener("load", handleImageLoad);
    }

    return () => {
      if (imgElement) {
        imgElement.removeEventListener("load", handleImageLoad);
      }
    };
  }, [rects, store.selectedImageIndex, store.encounterData]);

  useEffect(() => {
    const ref = fileInputRef.current;
    if (!ref) return;

    if (!store.flow) {
      store.initializeFlow(ref, maxSize);
    } else {
      store.flow.assignBrowse(ref);
    }
  }, [store, maxSize]);

  useEffect(() => {
    const handleClickOutside = (event) => {
      const imageBox = boxRef.current;
      if (!imageBox || !imageBox.contains(event.target)) {
        return;
      }
      const clickedOnAnnotation = rects.some((_, index) => {
        const rectElement = document.getElementById(`rect-${index}`);
        return rectElement && rectElement.contains(event.target);
      });

      if (!clickedOnAnnotation) {
        setClickedAnnotation(null);
        store.setSelectedAnnotationId(null);
      }
    };

    document.addEventListener("mousedown", handleClickOutside);

    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [rects, store]);

  const handleClick = (encounterId, storeEncounterId, annotationId) => {
    setClickedAnnotation({
      encounterId,
      id: annotationId,
    });
    if (encounterId === storeEncounterId) {
      store.setSelectedAnnotationId(annotationId);
      store.setSelectedImageIndex(store.selectedImageIndex);
    } else {
      store.setSelectedAnnotationId(null);
    }
  };

  const maxArea = React.useMemo(() => {
    return rects.reduce(
      (max, r) => Math.max(max, (r.width || 0) * (r.height || 0)),
      1,
    );
  }, [rects]);

  return (
    <div
      className="d-flex flex-column justify-content-between mt-3 position-relative mb-3"
      style={{
        padding: "10px",
        borderRadius: "10px",
        boxShadow: `0px 0px 10px rgba(0, 0, 0, 0.2)`,
        width: "100%",
        height: "auto",
      }}
    >
      <div className="mb-3 ms-1 d-flex flex-row">
        <MailIcon />
        <span
          style={{ marginLeft: "10px", fontSize: "1rem", fontWeight: "bold" }}
        >
          <FormattedMessage id="IMAGES" />
        </span>
      </div>
      <div className="mb-2 d-flex flex-row align-items-center justify-content-between">
        <p>
          {store.encounterData?.mediaAssets?.[store.selectedImageIndex]
            ?.userFilename || ""}
        </p>
        <p>
          {store.encounterData?.mediaAssets?.[store.selectedImageIndex]
            ?.keywords?.length
            ? `${store.encounterData?.mediaAssets?.[store.selectedImageIndex]?.keywords?.length} ${intl.formatMessage({ id: "KEYWORDS" })}`
            : ""}
        </p>
      </div>
      <div
        ref={boxRef}
        style={{
          width: "100%",
          position: "relative",
          cursor: "pointer",
          overflow: "hidden",
        }}
        onClick={() => setOpenImageModal(true)}
      >
        {rects.length > 0 &&
          rects.map((rect, index) => {
            let newRect = { ...rect };
            if (
              store.encounterData?.mediaAssets?.[store.selectedImageIndex]
                ?.rotationInfo
            ) {
              const imgW =
                store.encounterData?.mediaAssets?.[store.selectedImageIndex]
                  ?.width;
              const imgH =
                store.encounterData?.mediaAssets?.[store.selectedImageIndex]
                  ?.height;
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
              rect.annotationId === clickedAnnotation?.id ? 2000 : baseZ;

            return (
              <div
                id={`rect-${index}`}
                key={index}
                onMouseEnter={() =>
                  handleEnter(
                    `${newRect.encounterId === store.encounterData.id ? `${intl.formatMessage({ id: "THIS_ENCOUNTER" })}` : `encounter ${newRect.encounterId}`}\nViewpoint: ${newRect.viewpoint}\nIA Class: ${newRect.iaClass}`,
                  )
                }
                onMouseMove={handleMove}
                onMouseLeave={handleLeave}
                style={{
                  position: "absolute",
                  top: newRect.y,
                  left: newRect.x,
                  width: newRect.width,
                  height: newRect.height,
                  border:
                    newRect.encounterId === store.encounterData.id
                      ? "2px solid red"
                      : "2px dotted red",
                  transform: `rotate(${(newRect.rotation * 180) / Math.PI}deg)`,
                  transformOrigin: "center",
                  cursor: "pointer",
                  zIndex: finalZ,
                  backgroundColor:
                    newRect.annotationId === clickedAnnotation?.id
                      ? "rgba(240, 11, 11, 0.3)"
                      : "transparent",
                }}
                onClick={(e) => {
                  e.stopPropagation();
                  handleClick(
                    newRect.encounterId,
                    store.encounterData.id,
                    newRect.annotationId,
                  );
                }}
              >
                {store.access === "write" &&
                  newRect.annotationId === clickedAnnotation?.id &&
                  (newRect.encounterId === store.encounterData.id ? (
                    <div
                      className="d-flex flex-column"
                      style={{
                        position: "absolute",
                        top: 0,
                        right: 0,
                        zIndex: 20,
                      }}
                      onClick={(e) => e.stopPropagation()}
                    >
                      <div
                        onMouseEnter={(e) => {
                          e.stopPropagation();
                          handleEnter(
                            intl.formatMessage({ id: "EDIT_ANNOTATION" }),
                          );
                        }}
                        onMouseMove={(e) => {
                          e.stopPropagation();
                          handleMove(e);
                        }}
                        onMouseLeave={(e) => {
                          e.stopPropagation();
                          handleLeave();
                        }}
                        className="d-flex align-items-center justify-content-center"
                        style={{
                          width: "20px",
                          height: "20px",
                          backgroundColor: "red",
                          cursor: "pointer",
                          color: "white",
                        }}
                        onClick={() => {
                          if (
                            !store.imageModal.encounterData?.mediaAssets?.[
                              store.imageModal.selectedImageIndex
                            ] ||
                            !annotationParam
                          ) {
                            return;
                          }
                          const assetId =
                            store.encounterData?.mediaAssets?.[
                              store.selectedImageIndex
                            ]?.id;
                          window.open(
                            `/react/edit-annotation?encounterId=${newRect.encounterId}&assetId=${assetId}&annotation=${annotationParam}&annotationId=${newRect?.annotationId}`,
                            "_blank",
                          );
                        }}
                      >
                        <svg
                          width="20"
                          height="20"
                          viewBox="0 0 20 20"
                          fill="none"
                          xmlns="http://www.w3.org/2000/svg"
                        >
                          <path
                            d="M2.49896 17.501H5.62396L14.8406 8.28438L11.7156 5.15938L2.49896 14.376V17.501ZM4.16563 15.0677L11.7156 7.51771L12.4823 8.28438L4.9323 15.8344H4.16563V15.0677Z"
                            fill="white"
                          />
                          <path
                            d="M15.3073 2.74271C14.9823 2.41771 14.4573 2.41771 14.1323 2.74271L12.6073 4.26771L15.7323 7.39271L17.2573 5.86771C17.5823 5.54271 17.5823 5.01771 17.2573 4.69271L15.3073 2.74271Z"
                            fill="white"
                          />
                        </svg>
                      </div>
                      <div
                        onMouseEnter={(e) => {
                          e.stopPropagation();
                          handleEnter(
                            intl.formatMessage({ id: "DELETE_ANNOTATION" }),
                          );
                        }}
                        onMouseMove={(e) => {
                          e.stopPropagation();
                          handleMove(e);
                        }}
                        onMouseLeave={(e) => {
                          e.stopPropagation();
                          handleLeave();
                        }}
                        className="d-flex align-items-center justify-content-center"
                        style={{
                          width: "20px",
                          height: "20px",
                          backgroundColor: "red",
                          cursor: "pointer",
                          color: "white",
                        }}
                        onClick={async () => {
                          if (
                            window.confirm(
                              intl.formatMessage({
                                id: "CONFIRM_DELETE_ANNOTATION",
                              }),
                            )
                          ) {
                            await store.imageModal.removeAnnotation(
                              newRect.annotationId,
                            );
                            store.imageModal.setSelectedAnnotationId(null);
                            store.imageModal.refreshEncounterData();
                          }
                        }}
                      >
                        <svg
                          width="12"
                          height="16"
                          viewBox="0 0 12 16"
                          fill="none"
                          xmlns="http://www.w3.org/2000/svg"
                        >
                          <path
                            d="M9.33335 5.5V13.8333H2.66669V5.5H9.33335ZM8.08335 0.5H3.91669L3.08335 1.33333H0.166687V3H11.8334V1.33333H8.91669L8.08335 0.5ZM11 3.83333H1.00002V13.8333C1.00002 14.75 1.75002 15.5 2.66669 15.5H9.33335C10.25 15.5 11 14.75 11 13.8333V3.83333Z"
                            fill="white"
                          />
                        </svg>
                      </div>
                    </div>
                  ) : (
                    <div
                      onMouseEnter={(e) => {
                        e.stopPropagation();
                        handleEnter(
                          intl.formatMessage({ id: "GO_TO_ENCOUNTER_PAGE" }),
                        );
                      }}
                      onMouseMove={(e) => {
                        e.stopPropagation();
                        handleMove(e);
                      }}
                      onMouseLeave={(e) => {
                        e.stopPropagation();
                        handleLeave();
                      }}
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
                          const url = `/react/encounter?number=${newRect.encounterId}`;
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
                  ))}
              </div>
            );
          })}

        {store.encounterData?.mediaAssets.length > 0 ? (
          <img
            ref={imgRef}
            src={
              store.encounterData?.mediaAssets?.[store.selectedImageIndex]
                ?.url || ""
            }
            alt="encounter image"
            style={{ width: "100%", height: "auto" }}
          />
        ) : (
          <p>
            <FormattedMessage id="NO_IMAGE_AVAILABLE" />
          </p>
        )}
        <Tooltip show={tip.show} x={tip.x} y={tip.y}>
          {tip.text}
        </Tooltip>
        {store.encounterData?.mediaAssets.length > 0 && (
          <div style={{ position: "absolute", top: 5, right: 5 }}>
            <FullscreenIcon />
          </div>
        )}
      </div>
      {store.access === "write" &&
        store.encounterData?.mediaAssets.length > 0 && (
          <div
            className="d-flex flex-row justify-content-between align-items-center w-100 align-items-center"
            style={{
              backgroundColor: "#303336",
              color: "white",
              height: "70px",
              padding: "10px",
            }}
          >
            <div
              className="d-flex align-items-center justify-content-center flex-column"
              style={{ cursor: "pointer", paddingTop: "20px" }}
              onClick={async () => {
                if (store.matchResultClickable) {
                  const taskId = currentAnnotation?.iaTaskId;
                  const url = `/iaResults.jsp?taskId=${encodeURIComponent(taskId)}`;
                  window.open(url, "_blank", "noopener,noreferrer");
                } else if (
                  clickedAnnotation &&
                  clickedAnnotation.encounterId !== store.encounterData?.id
                ) {
                  const encounterId = clickedAnnotation.encounterId;
                  const result = await axios.get(
                    `/api/v3/encounters/${encounterId}`,
                  );
                  const encounterData = result.data;
                  const allAnnotations = (
                    encounterData.mediaAssets || []
                  ).flatMap((a) => a.annotations || []);
                  const selectedAnnotation = allAnnotations.find(
                    (annotation) => annotation.id === clickedAnnotation?.id,
                  );
                  const mediaAsset = encounterData.mediaAssets.find(
                    (data) =>
                      Array.isArray(data.annotations) &&
                      data.annotations.some(
                        (a) => a.id === clickedAnnotation?.id,
                      ),
                  );
                  const iaTaskId = !!selectedAnnotation?.iaTaskId;
                  const skipId =
                    !!selectedAnnotation?.iaTaskParameters?.skipIdent;
                  const identActive = iaTaskId && !skipId;
                  const detectionComplete =
                    mediaAsset?.detectionStatus === "complete";
                  const identificationStatus =
                    selectedAnnotation?.identificationStatus === "complete" ||
                    selectedAnnotation?.identificationStatus === "pending";

                  if (
                    identActive &&
                    (detectionComplete || identificationStatus)
                  ) {
                    const url = `/iaResults.jsp?taskId=${encodeURIComponent(selectedAnnotation.iaTaskId)}`;
                    window.open(url, "_blank", "noopener,noreferrer");
                  } else {
                    alert("No match results available for this annotation.");
                  }
                } else if (clickedAnnotation?.id) {
                  alert("No match results available for this annotation.");
                } else {
                  alert("Select an annotation to view match results.");
                }
              }}
            >
              <MatchResultIcon />
              <p>
                <FormattedMessage id="MATCH_RESULTS" />
              </p>
            </div>
            <div
              className="d-flex align-items-center justify-content-center flex-column"
              style={{ cursor: "pointer", paddingTop: "20px" }}
              onClick={() => {
                if (
                  !store.encounterData?.mediaAssets?.[store.selectedImageIndex]
                ) {
                  alert("No image selected.");
                  return;
                }
                const number = store.encounterData?.id;
                const mediaAssetId =
                  store.encounterData?.mediaAssets?.[store.selectedImageIndex]
                    ?.id;
                const url = `/encounters/encounterVM.jsp?number=${encodeURIComponent(number)}&mediaAssetId=${encodeURIComponent(mediaAssetId)}`;
                window.open(url, "_blank");
              }}
            >
              <EyeIcon />
              <p>
                <FormattedMessage id="VISUAL_MATCHER" />
              </p>
            </div>
            <div
              className="d-flex align-items-center justify-content-center flex-column"
              onClick={() => {
                if (
                  !store.encounterData?.mediaAssets?.[store.selectedImageIndex]
                ) {
                  alert("No image selected.");
                  return;
                }
                store.modals.setOpenMatchCriteriaModal(true);
              }}
              style={{ cursor: "pointer", paddingTop: "20px" }}
            >
              <RefreshIcon />
              <p>
                <FormattedMessage id="NEW_MATCH" />
              </p>
            </div>
            <div
              className="d-flex align-items-center justify-content-center flex-column"
              style={{ cursor: "pointer", paddingTop: "20px" }}
              onClick={() => {
                if (
                  !store.encounterData?.mediaAssets?.[store.selectedImageIndex]
                ) {
                  alert("No image selected.");
                  return;
                }
                window.open(
                  `/react/manual-annotation?encounterId=${store.encounterData?.id}&assetId=${store.encounterData?.mediaAssets?.[store.selectedImageIndex]?.id}`,
                  "_blank",
                );
              }}
            >
              <PencilIcon />
              <p>
                <FormattedMessage id="ADD_ANNOTATION" />
              </p>
            </div>
          </div>
        )}
      <div
        className="d-flex flex-wrap align-items-center mt-2"
        style={{ gap: 8, overflowY: "auto", maxHeight: 200 }}
      >
        {store.encounterData?.mediaAssets.map((asset, index) => (
          <img
            key={index}
            src={asset.url}
            alt={`media-${index}`}
            style={{
              width: 100,
              height: "auto",
              borderRadius: 5,
              cursor: "pointer",
              border:
                store.selectedImageIndex === index
                  ? "2px solid blue"
                  : "2px solid transparent",
            }}
            onClick={() => store.setSelectedImageIndex(index)}
          />
        ))}
        {store.access === "write" && (
          <div id="add-more-files">
            <label
              htmlFor={"add-more-files-input"}
              style={{
                cursor: store.isUploading ? "not-allowed" : "pointer",
                display: "inline-flex",
                alignItems: "center",
                opacity: store.isUploading ? 0.6 : 1,
              }}
            >
              <div
                className="d-flex flex-column align-items-center justify-content-center"
                style={{
                  width: 100,
                  height: 70,
                  borderRadius: 5,
                  cursor: "pointer",
                  border: `2px dashed ${theme.primaryColors.primary500}`,
                  backgroundColor: `${theme.primaryColors.primary50}`,
                  display: "flex",
                  justifyContent: "center",
                  alignItems: "center",
                  flexDirection: "column",
                }}
              >
                {store.isUploading ? (
                  <>
                    <div
                      className="spinner-border spinner-border-sm"
                      role="status"
                      style={{ color: theme.primaryColors.primary500 }}
                    >
                      <span className="visually-hidden">Loading...</span>
                    </div>
                    <small style={{ marginTop: 5 }}>
                      {store.uploadProgress}%
                    </small>
                  </>
                ) : (
                  <>
                    <ImageIcon />
                    <FormattedMessage id="ADD_IMAGE" />
                  </>
                )}
              </div>
            </label>

            <input
              id={"add-more-files-input"}
              type="file"
              ref={fileInputRef}
              accept="image/jpeg,image/png,image/bmp"
              style={{ display: "none" }}
            />
          </div>
        )}
      </div>
      {openImageModal && (
        <ImageModal
          open={openImageModal}
          onClose={() => setOpenImageModal(false)}
          assets={store.encounterData?.mediaAssets || []}
          index={store.selectedImageIndex}
          setIndex={(index) => store.setSelectedImageIndex(index)}
          rects={
            rects?.filter(
              (data) => data.encounterId === store.encounterData?.id,
            ) || []
          }
          imageStore={store.imageModal}
        />
      )}
    </div>
  );
});

export default ImageCard;
