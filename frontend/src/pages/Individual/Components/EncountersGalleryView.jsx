import React, { useContext, useState, useMemo } from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import ThemeColorContext from "../../../ThemeColorProvider";
import ImageModal from "../../../components/ImageModal";

const EncountersGalleryView = observer(({ store }) => {
  const theme = useContext(ThemeColorContext);
  const [selectedImageIndex, setSelectedImageIndex] = useState(0);
  const [imageModalOpen, setImageModalOpen] = useState(false);
  const [imgDims, setImgDims] = useState({});

  const allAssets = useMemo(() => {
    return store.encounters.flatMap((enc) =>
      (enc.mediaAssets || []).map((asset) => ({
        ...asset,
        encounterId: enc.id,
        encounterDate: enc.date,
      })),
    );
  }, [store.encounters]);

  const handleImageLoad = (assetKey, e) => {
    const img = e.currentTarget;
    setImgDims((prev) => ({
      ...prev,
      [assetKey]: {
        nw: img.naturalWidth,
        nh: img.naturalHeight,
        dw: img.clientWidth,
        dh: img.clientHeight,
      },
    }));
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
        <p className="text-muted">
          <FormattedMessage id="NO_IMAGE_AVAILABLE" />
        </p>
      </div>
    );
  }

  return (
    <div>
      <h5 className="mb-3">
        <FormattedMessage id="IMAGES" />
      </h5>
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))",
          gap: "16px",
        }}
      >
        {allAssets.map((asset, index) => {
          const annotations =
            asset.annotations
              ?.filter((a) => !a.isTrivial && a.boundingBox)
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

          const assetKey = `${asset.encounterId}-${asset.id || index}`;
          const dims = imgDims[assetKey];
          const scaleX = dims ? dims.nw / dims.dw : 1;
          const scaleY = dims ? dims.nh / dims.dh : 1;

          return (
            <div
              key={assetKey}
              className="position-relative"
              style={{
                borderRadius: "8px",
                overflow: "hidden",
                cursor: "zoom-in",
                backgroundColor: theme.grayColors.gray100,
              }}
              onClick={() => {
                setSelectedImageIndex(index);
                setImageModalOpen(true);
              }}
            >
              <img
                src={asset.url}
                alt={`Encounter ${asset.encounterId}`}
                style={{
                  width: "100%",
                  height: "180px",
                  objectFit: "cover",
                  display: "block",
                }}
                loading="lazy"
                onLoad={(e) => handleImageLoad(assetKey, e)}
              />

              {/* Annotation bounding boxes */}
              {dims &&
                annotations.map((rect, rectIndex) => (
                  <div
                    key={rectIndex}
                    className="position-absolute"
                    style={{
                      left: rect.x / scaleX,
                      top: rect.y / scaleY,
                      width: rect.width / scaleX,
                      height: rect.height / scaleY,
                      border: "2px solid red",
                      pointerEvents: "none",
                    }}
                  />
                ))}

              {/* Favorite badge placeholder */}
              {asset.isFavorite && (
                <div
                  className="position-absolute"
                  style={{
                    top: "8px",
                    right: "8px",
                    width: "24px",
                    height: "24px",
                    borderRadius: "50%",
                    backgroundColor: theme.statusColors.blue500,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  <i className="bi bi-star-fill" style={{ color: "white", fontSize: "12px" }} />
                </div>
              )}
            </div>
          );
        })}
      </div>

      {imageModalOpen && allAssets.length > 0 && (
        <ImageModal
          open={imageModalOpen}
          onClose={() => setImageModalOpen(false)}
          assets={allAssets}
          index={selectedImageIndex}
          setIndex={setSelectedImageIndex}
          rects={
            allAssets[selectedImageIndex]?.annotations
              ?.filter((a) => !a.isTrivial && a.boundingBox)
              ?.map((a) => ({
                x: a.boundingBox[0],
                y: a.boundingBox[1],
                width: a.boundingBox[2],
                height: a.boundingBox[3],
                rotation: a.theta || 0,
                annotationId: a.id,
              })) || []
          }
        />
      )}
    </div>
  );
});

export default EncountersGalleryView;
