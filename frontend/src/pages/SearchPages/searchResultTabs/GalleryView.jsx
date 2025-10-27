import React from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import { useState, useMemo, useEffect } from "react";
import ImageGalleryModal from "./ImageGalleryModal";
import "swiper/css";
import "swiper/css/navigation";
import "swiper/css/thumbs";
import "swiper/css/zoom";
import PaginationBar from "../../../components/PaginationBar";
import FullScreenLoader from "../../../components/FullScreenLoader";

const GalleryView = observer(({ store }) => {
  useEffect(() => {
    if (store.currentPage > store.totalPages)
      store.setCurrentPage(store.totalPages);
  }, [store.currentPage, store.totalPages]);

  useEffect(() => {
    store.imageModalStore.setSelectedImageIndex(0);
  }, [store.currentPage]);

  const [imgDims, setImgDims] = useState({});
  const [imageModalOpen, setImageModalOpen] = useState(false);
  const [currentIndex, setCurrentIndex] = useState(0);
  const rects = useMemo(() => {
    return (
      store.currentPageItems[
        store.imageModalStore.selectedImageIndex
      ]?.annotations
        ?.filter((data) => !data.isTrivial)
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

  useEffect(() => {
    if (!imageModalOpen) return;
    if (currentIndex >= store.currentPageItems.length) {
      setCurrentIndex(Math.max(0, store.currentPageItems.length - 1));
    }
  }, [store.currentPageItems.length, imageModalOpen, currentIndex]);

  useEffect(() => {
    setImgDims({});
  }, [store.currentPage]);

  return (
    <div
      className="container mt-1 mb-5"
      style={{ position: "relative", color: "white" }}
    >
      {store.loadingAll && <FullScreenLoader />}
      <h1>
        <FormattedMessage id="GALLERY_VIEW" />
      </h1>
      <PaginationBar
        totalItems={store.totalItems}
        page={store.currentPage}
        pageSize={store.pageSize}
        onPageChange={(p) => store.setCurrentPage(p)}
        onPageSizeChange={(size) => {
          if (typeof store.setimageCountPerPage === "function") {
            store.setPageSize(size);
          }
        }}
        className="mb-3"
      />

      <div style={{ display: "flex", flexWrap: "wrap", gap: "20px" }}>
        {store.currentPageItems.length > 0 ? (
          store.currentPageItems.map((asset, i) => {
            const rects =
              asset.annotations
                ?.filter((d) => !d.isTrivial)
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
                        border:
                          // rect.encounterId === store.encounterData?.id
                          //   ? "2px solid red"
                          "2px dotted red",
                        transform: `rotate(${rect.rotation}rad)`,
                        cursor: "pointer",
                        backgroundColor:
                          rect.annotationId ===
                          store.imageModalStore.selectedAnnotationId
                            ? "rgba(240, 11, 11, 0.5)"
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
        setIndex={setCurrentIndex}
        imageStore={store.imageModalStore}
        rects={rects}
      />
    </div>
  );
});

export default GalleryView;
