import React from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import { useState, useMemo, useEffect } from "react";
import ImageModal from "../../../components/ImageModal1";
import "swiper/css";
import "swiper/css/navigation";
import "swiper/css/thumbs";
import "swiper/css/zoom";
import PaginationBar from "../../../components/PaginationBar";
import FullScreenLoader from "../../../components/FullScreenLoader";

const GalleryView = observer(({ store }) => {

  useEffect(() => {
    if (store.currentPage > store.totalPages) store.setCurrentPage(store.totalPages);
  }, [store.currentPage, store.totalPages]);

  const [imageModalOpen, setImageModalOpen] = useState(false);
  const [currentIndex, setCurrentIndex] = useState(0);
  const rects = useMemo(() => {
    return store.currentPageItems[store.imageModalStore.selectedImageIndex]?.annotations?.filter((data) => !data.isTrivial)
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
  }, [store.currentPageItems, store.imageModalStore.selectedImageIndex]);

console.log("encounterAnnotations", JSON.stringify(store.imageModalStore.encounterAnnotations));
  useEffect(() => {
    if (!imageModalOpen) return;
    if (currentIndex >= store.currentPageItems.length) {
      setCurrentIndex(Math.max(0, store.currentPageItems.length - 1));
    }
  }, [store.currentPageItems.length, imageModalOpen, currentIndex]);

  // const listKey = `${store.currentPage}-${pageSize}-${totalItems}`;

  return (
    <div
      className="container mt-1 mb-5"
      style={{ position: "relative", color: "white" }}
    >
      {store.loadingAll && <FullScreenLoader />}
      <h1>
        <FormattedMessage
          id="galleryView.title"
          defaultMessage="Gallery View"
        />
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

      <div
        // key={listKey}
        style={{ display: "flex", flexWrap: "wrap", gap: "20px" }}
      >
        {store.currentPageItems.length > 0 ? (
          store.currentPageItems.map((asset, i) => (
            <div key={asset.__k} style={{ marginBottom: "20px" }}>
              <img
                src={asset.url}
                alt={`Media Asset ${asset.id ?? asset.uuid ?? ""}`}
                style={{
                  height: 200,
                  width: "auto",
                  maxWidth: 300,
                  cursor: "zoom-in",
                }}
                loading="lazy"
                decoding="async"
                onClick={() => {
                  store.imageModalStore.setSelectedImageIndex(i);
                  setImageModalOpen(true);
                }}
              />
            </div>
          ))
        ) : (
          <p>
            <FormattedMessage
              id="NO_IMAGE_AVAILABLE"
              defaultMessage="No images"
            />
          </p>
        )}
      </div>

      <ImageModal
        open={imageModalOpen}
        onClose={() => setImageModalOpen(false)}
        assets={store.currentPageItems}
        index={currentIndex}
        setIndex={setCurrentIndex}
        imageStore={store.imageModalStore}
        rects={rects?.filter(
              (data) => data.encounterId === store.encounterData?.id,
            ) || []}
      />
    </div>
  );
});

export default GalleryView;
