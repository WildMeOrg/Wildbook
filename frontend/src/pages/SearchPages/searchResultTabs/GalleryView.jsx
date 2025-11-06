import React from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import { useState, useMemo, useEffect } from "react";
import ImageGalleryModal from "./ImageGalleryModal";
import "swiper/css";
import "swiper/css/navigation";
import "swiper/css/thumbs";
import "swiper/css/zoom";
import FullScreenLoader from "../../../components/FullScreenLoader";
import MainButton from "../../../components/MainButton";
import ThemeColorContext from "../../../ThemeColorProvider";

const GalleryView = observer(({ store, pg = {} }) => {
  const theme = React.useContext(ThemeColorContext);

  useEffect(() => {
    store.resetGallery();
    pg();

    return () => {
      store.resetGallery();
    };
  }, []);

  useEffect(() => {
    store.imageModalStore.setSelectedImageIndex(0);
  }, [store.currentPageItems]);

  const [imgDims, setImgDims] = useState({});
  const [imageModalOpen, setImageModalOpen] = useState(false);
  const rects = useMemo(() => {
    return (
      store.currentPageItems[
        store.imageModalStore.selectedImageIndex
      ]?.annotations
        ?.filter((data) => !data.isTrivial)
        ?.filter((a) => a.boundingBox)
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
        })) || []
    );
  }, [store.currentPageItems, store.imageModalStore.selectedImageIndex]);

  return (
    <div
      className="container mt-1 mb-5"
      style={{ position: "relative", color: "white" }}
    >
      {store.loadingAll && <FullScreenLoader />}

      <div className="w-100 d-flex flex-row gap-3 justify-content-center">
        <MainButton
          noArrow={true}
          style={{
            padding: "10px",
            width: "50px",
            height: "30px",
          }}
          disabled={store.currentPage <= 0}
          color="white"
          backgroundColor={theme.primaryColors.primary500}
          onClick={() => {
            if (store.currentPage <= 0) return;
            const current = store.currentPage;
            const prevPage = current - 1;
            if (store.previousPageItems[prevPage]) {
              store.setCurrentPageItems(
                store.previousPageItems[prevPage].slice(),
              );
              store.setCurrentPage(prevPage);
            }
          }}
        >
          <i className="bi bi-chevron-left"></i>
        </MainButton>
        <MainButton
          noArrow={true}
          style={{
            padding: "10px",
            width: "50px",
            height: "30px",
          }}
          disabled={store.currentPageItems.length < store.pageSize}
          color="white"
          backgroundColor={theme.primaryColors.primary500}
          onClick={async () => {
            store.setPreviousPageItems(
              store.currentPage,
              store.currentPageItems.slice(),
            );
            const current = store.currentPage;
            const nextPage = current + 1;
            if (store.previousPageItems[nextPage]) {
              store.setCurrentPageItems(
                store.previousPageItems[nextPage].slice(),
              );
            } else {
              await pg();
            }
            store.setCurrentPage(nextPage);
          }}
        >
          <i className="bi bi-chevron-right"></i>
        </MainButton>
      </div>

      <div style={{ display: "flex", flexWrap: "wrap", gap: "20px" }}>
        {store.currentPageItems.length > 0 ? (
          store.currentPageItems.map((asset, i) => {
            const rects =
              asset.annotations
                ?.filter((d) => !d.isTrivial)
                ?.filter((a) => a.boundingBox)
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
                })) || [];

            const dims = imgDims[asset.__k];
            const scaleX = dims ? dims.nw / dims.dw : 1;
            const scaleY = dims ? dims.nh / dims.dh : 1;

            return (
              <div
                key={asset.__k}
                className="position-relative"
                style={{
                  marginBottom: 20,
                  display: "inline-block",
                  width: dims?.dw,
                  height: dims?.dh,
                  overflow: "hidden",
                }}
              >
                <img
                  src={asset.url}
                  alt={`Media Asset ${asset.id ?? asset.uuid ?? ""}`}
                  style={{
                    height: 200,
                    width: "auto",
                    maxWidth: 300,
                    cursor: "zoom-in",
                    display: "block",
                  }}
                  loading="lazy"
                  decoding="async"
                  onLoad={(e) => {
                    const img = e.currentTarget;
                    setImgDims((prev) => ({
                      ...prev,
                      [asset.__k]: {
                        nw: img.naturalWidth,
                        nh: img.naturalHeight,
                        dw: img.clientWidth,
                        dh: img.clientHeight,
                      },
                    }));
                  }}
                  onClick={() => {
                    store.imageModalStore.setSelectedImageIndex(i);
                    setImageModalOpen(true);
                  }}
                />

                {dims &&
                  rects.length > 0 &&
                  rects.map((rect, index) => (
                    <div
                      key={index}
                      className="position-absolute"
                      onClick={() => {
                        store.imageModalStore.setSelectedAnnotationId(
                          rect.annotationId,
                        );
                      }}
                      style={{
                        left: rect.x / scaleX,
                        top: rect.y / scaleY,
                        width: rect.width / scaleX,
                        height: rect.height / scaleY,
                        border: "2px dotted red",
                        transform: `rotate(${rect.rotation}rad)`,
                        cursor: "pointer",
                        backgroundColor:
                          rect.annotationId ===
                          store.imageModalStore.selectedAnnotationId
                            ? "rgba(255, 0, 0, 0.3)"
                            : "transparent",
                      }}
                    />
                  ))}
              </div>
            );
          })
        ) : (
          <p>
            <FormattedMessage
              id="NO_IMAGE_AVAILABLE"
              defaultMessage="No images"
            />
          </p>
        )}
      </div>

      <ImageGalleryModal
        open={imageModalOpen}
        onClose={() => setImageModalOpen(false)}
        assets={store.currentPageItems}
        index={store.imageModalStore.selectedImageIndex}
        imageStore={store.imageModalStore}
        rects={rects}
      />
    </div>
  );
});

export default GalleryView;
