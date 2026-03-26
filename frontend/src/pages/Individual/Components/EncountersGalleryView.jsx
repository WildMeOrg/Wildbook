import React, { useContext, useMemo, useState } from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import ThemeColorContext from "../../../ThemeColorProvider";
import ImageModal from "../../../components/ImageModal";

const EncountersGalleryView = observer(({ store }) => {
  const theme = useContext(ThemeColorContext);
  const [imgDims, setImgDims] = useState({});

  const allAssets = useMemo(() => {
    return store.encounters.flatMap((encounter) =>
      (encounter.mediaAssets || []).map((asset, assetIndex) => ({
        ...asset,
        encounterId: encounter.id,
        encounterDate: encounter.date,
        encounterData: {
          ...encounter,
          individualDisplayName: store.displayName,
        },
        tableID: `${encounter.id}-${asset.id || assetIndex}`,
      })),
    );
  }, [store.encounters, store.displayName]);

  const handleImageLoad = (assetKey, event) => {
    const img = event.currentTarget;

    setImgDims((prev) => ({
      ...prev,
      [assetKey]: {
        naturalWidth: img.naturalWidth,
        naturalHeight: img.naturalHeight,
        displayWidth: img.clientWidth,
        displayHeight: img.clientHeight,
      },
    }));
  };

  const openImageModal = (index) => {
    store.imageModalStore.openModal({
      assets: allAssets,
      selectedImageIndex: index,
      access: store.individualData?.access || "read",
    });
  };

  if (store.encountersLoading) {
    return (
      <div
        className="d-flex justify-content-center align-items-center"
        style={{ minHeight: "300px" }}
      >
        <div className="spinner-border text-primary" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  if (allAssets.length === 0) {
    return (
      <div
        className="d-flex justify-content-center align-items-center"
        style={{ minHeight: "300px" }}
      >
        <p className="text-muted mb-0">
          <FormattedMessage
            id="NO_IMAGE_AVAILABLE"
            defaultMessage="No image available"
          />
        </p>
      </div>
    );
  }

  return (
    <>
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))",
          gap: "16px",
        }}
      >
        {allAssets.map((asset, index) => {
          const assetKey = asset.tableID;
          const dims = imgDims[assetKey];

          const annotations =
            asset.annotations?.filter(
              (annotation) => !annotation.isTrivial && annotation.boundingBox,
            ) || [];

          const scaleX =
            dims?.naturalWidth && dims?.displayWidth
              ? dims.naturalWidth / dims.displayWidth
              : 1;

          const scaleY =
            dims?.naturalHeight && dims?.displayHeight
              ? dims.naturalHeight / dims.displayHeight
              : 1;

          return (
            <div
              key={assetKey}
              style={{
                backgroundColor: theme.defaultColors.white,
                borderRadius: "10px",
                overflow: "hidden",
                boxShadow: "0 2px 8px rgba(0,0,0,0.08)",
              }}
            >
              <div
                role="button"
                tabIndex={0}
                onClick={() => openImageModal(index)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    openImageModal(index);
                  }
                }}
                style={{
                  position: "relative",
                  cursor: "pointer",
                  outline: "none",
                }}
              >
                <img
                  src={asset.thumbnailUrl || asset.url}
                  alt={asset.encounterId || "Encounter image"}
                  onLoad={(event) => handleImageLoad(assetKey, event)}
                  style={{
                    width: "100%",
                    height: "220px",
                    objectFit: "cover",
                    display: "block",
                    backgroundColor: theme.grayColors.gray100,
                  }}
                />

                {dims &&
                  annotations.map((annotation) => {
                    const [x, y, width, height] = annotation.boundingBox;

                    return (
                      <div
                        key={annotation.id}
                        style={{
                          position: "absolute",
                          left: x / scaleX,
                          top: y / scaleY,
                          width: width / scaleX,
                          height: height / scaleY,
                          border: `2px solid ${theme.primaryColors.primary500}`,
                          pointerEvents: "none",
                          boxSizing: "border-box",
                        }}
                      />
                    );
                  })}
              </div>
            </div>
          );
        })}
      </div>

      {store.imageModalStore.open &&
        store.imageModalStore.assets.length > 0 && (
          <ImageModal
            open={store.imageModalStore.open}
            onClose={store.imageModalStore.closeModal}
            assets={store.imageModalStore.assets}
            index={store.imageModalStore.selectedImageIndex}
            setIndex={store.imageModalStore.setSelectedImageIndex}
            rects={store.imageModalStore.currentRects}
            imageStore={store.imageModalStore}
          />
        )}
    </>
  );
});

export default EncountersGalleryView;
