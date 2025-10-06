
import React, { useState, useEffect, useRef, useContext } from "react";
import MailIcon from "../../components/icons/MailIcon";
import { observer } from "mobx-react-lite";
import ImageModal from "../../components/ImageModal";
import ThemeColorContext from "../../ThemeColorProvider";
import { FormattedMessage } from "react-intl";
import ImageIcon from "../../components/icons/ImageIcon";

const ImageCard = observer(({ store = {} }) => {
  const imgRef = useRef(null);
  const [rects, setRects] = useState([]);
  const [scaleX, setScaleX] = useState(1);
  const [scaleY, setScaleY] = useState(1);
  const [openImageModal, setOpenImageModal] = useState(false);
  const fileInputRef = useRef(null);
  const maxSize = 10;
  const theme = useContext(ThemeColorContext);

  useEffect(() => {
    if (
      store.encounterData &&
      store.encounterData?.mediaAssets &&
      store.encounterData?.mediaAssets?.length > 0
    ) {
      const selectedImage = store.encounterData.mediaAssets[store.selectedImageIndex];
      const annotations = selectedImage?.annotations;
      if (annotations?.length > 0) {
        const anns = selectedImage?.annotations || [];
        setRects(
          anns.filter(data => !data.isTrivial)?.map((a) => ({
            x: a.boundingBox[0],
            y: a.boundingBox[1],
            width: a.boundingBox[2],
            height: a.boundingBox[3],
            rotation: a.theta || 0,
            annotationId: a.id,
            encounterId: a.encounterId,
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
      store.setSelectedAnnotationId(annotationId)
      setOpenImageModal(true);
      store.setSelectedImageIndex(store.selectedImageIndex);
    } else {
      const url = `/react/encounter?number=${encounterId}`;
      window.open(url, "_blank");
    }
  }

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
      <div className="mb-3 ms-1 d-flex flex-col align-items-center">
        <MailIcon />
        <span
          style={{ marginLeft: "10px", fontSize: "1rem", fontWeight: "bold" }}
        >
          Images
        </span>
      </div>
      <div className="mb-2 d-flex flex-row align-items-center justify-content-between">
        <p>
          {store.encounterData?.mediaAssets[store.selectedImageIndex]?.userFilename || "No image selected"}
        </p>
        <p>
          {store.encounterData?.mediaAssets[store.selectedImageIndex]?.keywords?.length ? `${store.encounterData?.mediaAssets[store.selectedImageIndex]?.keywords?.length} tags` : ""}
        </p>
      </div >


      <div
        style={{
          width: "100%",
          position: "relative",
          cursor: "pointer",
          overflow: "hidden",
        }}
        onClick={() => setOpenImageModal(true)}
      >

        {rects.length > 0 && rects.map((rect, index) => {
          let newRect = { ...rect };

          if (store.encounterData?.mediaAssets[store.selectedImageIndex]?.rotation) {
            const imgW = store.encounterData?.mediaAssets[store.selectedImageIndex]?.width;
            const imgH = store.encounterData?.mediaAssets[store.selectedImageIndex]?.height;
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
              style={{
                position: "absolute",
                top: newRect.y,
                left: newRect.x,
                width: newRect.width,
                height: newRect.height,
                border: newRect.encounterId === store.encounterData.id ? "2px solid red" : "2px solid yellow",
                transform: `rotate(${(newRect.rotation * 180) / Math.PI}deg)`,
                transformOrigin: "center",
                cursor: "pointer",
                zIndex: 10,
                backgroundColor: newRect.annotationId === store.selectedAnnotationId ? "rgba(240, 11, 11, 0.5)" : "transparent",
              }}
              onClick={() => handleClick(newRect.encounterId, store.encounterData.id, newRect.annotationId)}
            >
            </div>
          )
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
        <div className="d-flex align-items-center justify-content-center flex-column"
          onClick={() => {
            if (store.selectedAnnotationId && store.matchResultClickable) {
              const taskId = store.selectedAnnotationId?.iaTaskId;
              const url = `/iaResults.jsp?taskId=${encodeURIComponent(taskId)}`;
              window.open(url, "_blank", "noopener,noreferrer");
            } else {
              alert("Please select an annotation to view match results.");
            }
          }}
          style={{ cursor: "pointer" }}
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="19"
            height="23"
            viewBox="0 0 19 23"
            fill="none"
          >
            <path
              d="M7.5 2.25L2.5 2.25C1.4 2.25 0.5 3.15 0.5 4.25L0.5 18.25C0.5 19.35 1.4 20.25 2.5 20.25H7.5L7.5 21.25C7.5 21.8 7.95 22.25 8.5 22.25C9.05 22.25 9.5 21.8 9.5 21.25L9.5 1.25C9.5 0.7 9.05 0.25 8.5 0.25C7.95 0.25 7.5 0.7 7.5 1.25V2.25ZM7.5 17.25H2.5L7.5 11.25L7.5 17.25ZM16.5 2.25L11.5 2.25V4.25L15.5 4.25C16.05 4.25 16.5 4.7 16.5 5.25L16.5 17.25L11.5 11.25L11.5 20.25H16.5C17.6 20.25 18.5 19.35 18.5 18.25L18.5 4.25C18.5 3.15 17.6 2.25 16.5 2.25Z"
              fill="white"
            />
          </svg>
          <p>Match Results</p>
        </div>
        <div className="d-flex align-items-center justify-content-center flex-column"
          style={{ cursor: "pointer" }}
          onClick={() => {
            if (!store.encounterData?.mediaAssets[store.selectedImageIndex]) {
              return;
            }
            const number = store.encounterData?.id;
            const mediaAssetId = store.encounterData?.mediaAssets[store.selectedImageIndex]?.id;
            const url = `/encounters/encounterVM.jsp?number=${encodeURIComponent(number)}&mediaAssetId=${encodeURIComponent(mediaAssetId)}`;
            window.open(url, "_blank");
          }}
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="25"
            height="25"
            viewBox="0 0 25 25"
            fill="none"
          >
            <path
              d="M12.1665 4.75C7.1665 4.75 2.8965 7.86 1.1665 12.25C2.8965 16.64 7.1665 19.75 12.1665 19.75C17.1665 19.75 21.4365 16.64 23.1665 12.25C21.4365 7.86 17.1665 4.75 12.1665 4.75ZM12.1665 17.25C9.4065 17.25 7.1665 15.01 7.1665 12.25C7.1665 9.49 9.4065 7.25 12.1665 7.25C14.9265 7.25 17.1665 9.49 17.1665 12.25C17.1665 15.01 14.9265 17.25 12.1665 17.25ZM12.1665 9.25C10.5065 9.25 9.1665 10.59 9.1665 12.25C9.1665 13.91 10.5065 15.25 12.1665 15.25C13.8265 15.25 15.1665 13.91 15.1665 12.25C15.1665 10.59 13.8265 9.25 12.1665 9.25Z"
              fill="white"
            />
          </svg>
          <p>Visual Matcher</p>
        </div>
        <div className="d-flex align-items-center justify-content-center flex-column"
          onClick={() => {
            if (!store.encounterData?.mediaAssets[store.selectedImageIndex]) {
              return;
            }
            store.modals.setOpenMatchCriteriaModal(true);
          }}
          style={{ cursor: "pointer" }}
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="25"
            height="25"
            viewBox="0 0 25 25"
            fill="none"
          >
            <path
              d="M12.834 5.54056V3.75056C12.834 3.30056 12.294 3.08056 11.984 3.40056L9.18399 6.19056C8.98399 6.39056 8.98399 6.70056 9.18399 6.90056L11.974 9.69056C12.294 10.0006 12.834 9.78056 12.834 9.33056L12.834 7.54056C16.144 7.54056 18.834 10.2306 18.834 13.5406C18.834 16.2606 17.004 18.5606 14.524 19.2906C14.104 19.4106 13.834 19.8106 13.834 20.2406C13.834 20.8906 14.454 21.4006 15.084 21.2106C18.404 20.2406 20.834 17.1806 20.834 13.5406C20.834 9.12056 17.254 5.54056 12.834 5.54056Z"
              fill="white"
            />
            <path
              d="M6.83398 13.5406C6.83398 12.2006 7.27398 10.9606 8.02398 9.95056C8.32398 9.55056 8.28398 9.00056 7.93398 8.64056C7.51398 8.22056 6.79398 8.26056 6.43398 8.74056C5.43398 10.0806 4.83398 11.7406 4.83398 13.5406C4.83398 17.1806 7.26398 20.2406 10.584 21.2106C11.214 21.4006 11.834 20.8906 11.834 20.2406C11.834 19.8106 11.564 19.4106 11.144 19.2906C8.66398 18.5606 6.83398 16.2606 6.83398 13.5406Z"
              fill="white"
            />
          </svg>
          <p>New Match</p>
        </div>
        <div className="d-flex align-items-center justify-content-center flex-column"
          style={{ cursor: "pointer" }}
          onClick={() => {
            if (!store.encounterData?.mediaAssets[store.selectedImageIndex]) {
              return;
            }
            window.open(`/react/manual-annotation?encounterId=${store.encounterData?.id}&assetId=${store.encounterData?.mediaAssets[store.selectedImageIndex]?.id}`, "_blank");
          }}
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="18"
            height="19"
            viewBox="0 0 18 19"
            fill="none"
          >
            <path
              d="M16.1029 7.64L17.1629 6.58C17.9429 5.8 17.9429 4.53 17.1629 3.75L15.7529 2.34C14.9729 1.56 13.7029 1.56 12.9229 2.34L11.8629 3.4L16.1029 7.64ZM10.4429 4.81L1.39293 13.86C1.30293 13.95 1.25293 14.08 1.25293 14.21L1.25293 17.75C1.25293 18.03 1.47293 18.25 1.75293 18.25H5.29293C5.42293 18.25 5.55293 18.2 5.64293 18.1L14.6929 9.05L10.4429 4.81ZM16.2529 14.75C16.2529 16.94 13.7129 18.25 11.2529 18.25C10.7029 18.25 10.2529 17.8 10.2529 17.25C10.2529 16.7 10.7029 16.25 11.2529 16.25C12.7929 16.25 14.2529 15.52 14.2529 14.75C14.2529 14.28 13.7729 13.88 13.0229 13.55L14.5029 12.07C15.5729 12.7 16.2529 13.54 16.2529 14.75ZM1.83293 10.6C0.86293 10.04 0.25293 9.31 0.25293 8.25C0.25293 6.45 2.14293 5.62 3.81293 4.89C4.84293 4.43 6.25293 3.81 6.25293 3.25C6.25293 2.84 5.47293 2.25 4.25293 2.25C2.99293 2.25 2.45293 2.86 2.42293 2.89C2.07293 3.3 1.44293 3.35 1.02293 3.01C0.61293 2.67 0.53293 2.06 0.87293 1.63C0.98293 1.49 2.01293 0.25 4.25293 0.25C6.49293 0.25 8.25293 1.57 8.25293 3.25C8.25293 5.12 6.32293 5.97 4.61293 6.72C3.67293 7.13 2.25293 7.75 2.25293 8.25C2.25293 8.56 2.68293 8.85 3.32293 9.11L1.83293 10.6Z"
              fill="white"
            />
          </svg>
          <p>Add Annotation</p>
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
        <div
          id="add-more-files"
          onClick={() => fileInputRef.current.click()}
        >
          <label
            htmlFor={"add-more-files-input"}
            id="add-more-files"
            style={{ cursor: "pointer", display: "inline-flex", alignItems: "center" }}
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
              }} >
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
          rects={rects?.filter(data => data.encounterId === store.encounterData?.id) || []}
          store={store}
        />
      )}
    </div>
  );
});

export default ImageCard;
