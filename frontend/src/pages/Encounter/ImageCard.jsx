import React, { useState, useEffect, useRef, useContext } from "react";
import MailIcon from "../../components/icons/MailIcon";
import { observer } from "mobx-react-lite";
import ImageModal from "../../components/ImageModal";
import ThemeColorContext from "../../ThemeColorProvider";
import { FormattedMessage } from "react-intl";
import ImageIcon from "../../components/icons/ImageIcon";
import MatchResultIcon from "../../components/icons/MatchResultIcon";
import RefreshIcon from "../../components/icons/RefreshIcon";
import PencilIcon from "../../components/icons/PencilAnnotation";
import EyeIcon from "../../components/icons/EyeIcon";
import Tooltip from "../../components/ToolTip";

const ImageCard = observer(({ store = {} }) => {
  const imgRef = useRef(null);
  const [rects, setRects] = useState([]);
  const [scaleX, setScaleX] = useState(1);
  const [scaleY, setScaleY] = useState(1);
  const [openImageModal, setOpenImageModal] = useState(false);
  const fileInputRef = useRef(null);
  const maxSize = 10;
  const theme = useContext(ThemeColorContext);
  const boxRef = React.useRef(null);
  const [tip, setTip] = React.useState({ show: false, x: 0, y: 0, text: "" });

  const handleEnter = (text) => setTip((s) => ({ ...s, show: true, text }));
  const handleMove = (e) => {
    const r = boxRef.current.getBoundingClientRect();
    setTip((s) => ({ ...s, x: e.clientX - r.left, y: e.clientY - r.top }));
  };
  const handleLeave = () => setTip({ show: false, x: 0, y: 0, text: "" });

  useEffect(() => {
    if (
      store.encounterData &&
      store.encounterData?.mediaAssets &&
      store.encounterData?.mediaAssets?.length > 0
    ) {
      const selectedImage =
        store.encounterData.mediaAssets[store.selectedImageIndex];
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
          store.encounterData?.mediaAssets[store.selectedImageIndex]?.width;
        const naturalHeight =
          store.encounterData?.mediaAssets[store.selectedImageIndex]?.height;
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

  const handleClick = (encounterId, storeEncounterId, annotationId) => {
    if (encounterId === storeEncounterId) {
      store.setSelectedAnnotationId(annotationId);
      setOpenImageModal(true);
      store.setSelectedImageIndex(store.selectedImageIndex);
    } else {
      const url = `/react/encounter?number=${encounterId}`;
      window.open(url, "_blank");
    }
  };

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
          {store.encounterData?.mediaAssets[store.selectedImageIndex]
            ?.userFilename || ""}
        </p>
        <p>
          {store.encounterData?.mediaAssets[store.selectedImageIndex]?.keywords
            ?.length
            ? `${store.encounterData?.mediaAssets[store.selectedImageIndex]?.keywords?.length} tags`
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
              store.encounterData?.mediaAssets[store.selectedImageIndex]
                ?.rotation
            ) {
              const imgW =
                store.encounterData?.mediaAssets[store.selectedImageIndex]
                  ?.width;
              const imgH =
                store.encounterData?.mediaAssets[store.selectedImageIndex]
                  ?.height;
              const adjW = imgH / imgW;
              const adjH = imgW / imgH;
              newRect = {
                ...rect,
                x: (rect.x / scaleX) * adjW,
                width: (rect.width / scaleX) * adjW,
                y: (rect.y / scaleY) * adjH,
                height: (rect.height / scaleY) * adjH,
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

            return (
              <div
                id={`rect-${index}`}
                key={index}
                onMouseEnter={() =>
                  handleEnter(
                    `Viewpoint: ${rect.viewpoint}\nIA Class: ${rect.iaClass}`,
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
                      : "2px solid yellow",
                  transform: `rotate(${(newRect.rotation * 180) / Math.PI}deg)`,
                  transformOrigin: "center",
                  cursor: "pointer",
                  zIndex: 10,
                  backgroundColor:
                    newRect.annotationId === store.selectedAnnotationId
                      ? "rgba(240, 11, 11, 0.5)"
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
              ></div>
            );
          })}

        <img
          ref={imgRef}
          src={
            store.encounterData?.mediaAssets[store.selectedImageIndex]?.url ||
            ""
          }
          alt="No image available"
          style={{ width: "100%", height: "auto" }}
        />
        <Tooltip show={tip.show} x={tip.x} y={tip.y}>
          {tip.text}
        </Tooltip>
      </div>

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
          onClick={() => {
            if (store.selectedAnnotationId && store.matchResultClickable) {
              const taskId = store.selectedAnnotationId?.iaTaskId;
              const url = `/iaResults.jsp?taskId=${encodeURIComponent(taskId)}`;
              window.open(url, "_blank", "noopener,noreferrer");
            } else {
              alert("Please select an annotation to view match results.");
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
            if (!store.encounterData?.mediaAssets[store.selectedImageIndex]) {
              alert("No image selected.");
              return;
            }
            const number = store.encounterData?.id;
            const mediaAssetId =
              store.encounterData?.mediaAssets[store.selectedImageIndex]?.id;
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
            if (!store.encounterData?.mediaAssets[store.selectedImageIndex]) {
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
            if (!store.encounterData?.mediaAssets[store.selectedImageIndex]) {
              alert("No image selected.");
              return;
            }
            window.open(
              `/react/manual-annotation?encounterId=${store.encounterData?.id}&assetId=${store.encounterData?.mediaAssets[store.selectedImageIndex]?.id}`,
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
      <div
        className="d-flex flex-wrap align-items-center mt-2"
        style={{ gap: 8 }}
      >
        {store.encounterData?.mediaAssets.map((asset, index) => (
          <img
            key={index}
            src={asset.url}
            alt={`${asset.url} ${index}`}
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
        <div id="add-more-files" onClick={() => fileInputRef.current.click()}>
          <label
            htmlFor={"add-more-files-input"}
            style={{
              cursor: "pointer",
              display: "inline-flex",
              alignItems: "center",
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
              <ImageIcon />
              <FormattedMessage id="ADD_IMAGE" />
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
