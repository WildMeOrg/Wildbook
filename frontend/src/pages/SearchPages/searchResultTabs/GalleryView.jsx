import React from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import { useState, useMemo, useEffect } from "react";
import ImageModal from "./ImageModal";
import "swiper/css";
import "swiper/css/navigation";
import "swiper/css/thumbs";
import "swiper/css/zoom";
import PaginationBar from "../../../components/PaginationBar";
import FullScreenLoader from "../../../components/FullScreenLoader";

const GalleryView = observer(({ store }) => {
    
    const allMediaAssets = useMemo(() => {
        const src = store.searchResultsAll ?? [];
        return src
            .filter((item) => Array.isArray(item.mediaAssets) && item.mediaAssets.length > 0)
            .flatMap((item) =>
                item.mediaAssets.map((a, idx) => ({
                    ...a,
                    __k: `${a.uuid ?? a.id ?? "na"}-${idx}`,
                }))
            );
    }, [store.searchResultsAll]);

    const [page, setPage] = useState(1);
    const pageSize = store.imageCountPerPage || 20;
    const totalItems = allMediaAssets.length;
    const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
    const start = (page - 1) * pageSize;

    const pageItems = useMemo(
        () => allMediaAssets.slice(start, start + pageSize),
        [allMediaAssets, start, pageSize]
    );

    useEffect(() => { setPage(1); }, [pageSize, store.searchResultsAll]);
    useEffect(() => { if (page > totalPages) setPage(totalPages); }, [page, totalPages]);

    const [imageModalOpen, setImageModalOpen] = useState(false);
    const [currentIndex, setCurrentIndex] = useState(0);

    useEffect(() => {
        if (!imageModalOpen) return;
        if (currentIndex >= pageItems.length) {
            setCurrentIndex(Math.max(0, pageItems.length - 1));
        }
    }, [pageItems.length, imageModalOpen, currentIndex]);

    const listKey = `${page}-${pageSize}-${totalItems}`;

    return (
        <div 
            className="container mt-1 mb-5"
        style={{ position: "relative", color: "white" }}>
            { store.loadingAll && <FullScreenLoader/>}
            <h1>
                <FormattedMessage id="galleryView.title" defaultMessage="Gallery View" />
            </h1>

            <PaginationBar
                totalItems={totalItems}
                page={page}
                pageSize={pageSize}
                onPageChange={(p) => setPage(p)}
                onPageSizeChange={(size) => {
                    if (typeof store.setimageCountPerPage === "function") {
                        store.setimageCountPerPage(size);
                    }
                }}
                className="mb-3"
            />

            <div key={listKey} style={{ display: "flex", flexWrap: "wrap", gap: "20px" }}>
                {pageItems.length > 0 ? (
                    pageItems.map((asset, i) => (
                        <div key={asset.__k} style={{ marginBottom: "20px" }}>
                            <img
                                src={asset.url}
                                alt={`Media Asset ${asset.id ?? asset.uuid ?? ""}`}
                                style={{ height: 200, width: "auto", maxWidth: 300, cursor: "zoom-in" }}
                                loading="lazy"
                                decoding="async"
                                onClick={() => {
                                    setCurrentIndex(i);
                                    setImageModalOpen(true);
                                }}
                            />
                        </div>
                    ))
                ) : (
                    <p><FormattedMessage id="NO_IMAGE_AVAILABLE" defaultMessage="No images" /></p>
                )}
            </div>

            <ImageModal
                open={imageModalOpen}
                onClose={() => setImageModalOpen(false)}
                assets={pageItems}
                index={currentIndex}
                setIndex={setCurrentIndex}
            />
        </div>
    );
});

export default GalleryView;