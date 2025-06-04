import React from "react";
import { observer } from "mobx-react-lite";
import InfoAccordion from "../../components/InfoAccordion";
import { FaImage } from "react-icons/fa";
import ThemeColorContext from "../../ThemeColorProvider";
import { useIntl } from "react-intl";

export const BulkImportImageUploadInfo = observer(({ store }) => {
  const intl = useIntl();
  const theme = React.useContext(ThemeColorContext);
  const missingPhotos = store.spreadsheetData.reduce((acc, row) => {
    const mediaAssets = row["Encounter.mediaAsset0"];
    if (mediaAssets) {
      const photos = mediaAssets.split(",");
      photos.forEach((photo) => {
        if (
          !store.uploadedImages.includes(photo) &&
          !store.imageSectionFileNames.includes(photo)
        ) {
          acc.push(photo);
        }
      });
    }
    return acc;
  }, []);

  const data = [
    {
      label: intl.formatMessage({
        id: "PHOTOS_MISSING",
        defaultMessage: "photos missing",
      }),
      value: missingPhotos.length,
    },
    {
      label: intl.formatMessage({
        id: "PHOTOS_FAILED",
        defaultMessage: "photos failed",
      }),
      value: store.failedImages.length,
    },
    {
      label: intl.formatMessage({
        id: "PHOTOS_UPLOADED",
        defaultMessage: "photos uploaded",
      }),
      value: store.uploadedImages.length,
    },
  ];

  const title = `photos uploaded: ${store.uploadedImages.length} `;

  return (
    <div style={{ marginTop: "2rem" }}>
      <InfoAccordion
        icon={
          <FaImage
            size={20}
            color={
              store.activeStep === 0 ? "#fff" : theme.primaryColors.primary500
            }
          />
        }
        title={title}
        data={data}
      />
    </div>
  );
});

export default BulkImportImageUploadInfo;
