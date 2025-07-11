import React from "react";
import { observer } from "mobx-react-lite";
import InfoAccordion from "../../components/InfoAccordion";
import { FaImage } from "react-icons/fa";
import ThemeColorContext from "../../ThemeColorProvider";
import { useIntl } from "react-intl";

export const BulkImportImageUploadInfo = observer(({ store, expanded }) => {
  const intl = useIntl();
  const theme = React.useContext(ThemeColorContext);

  const data = [
    {
      label: intl.formatMessage({
        id: "PHOTOS_MISSING",
        defaultMessage: "photos missing",
      }),
      value: store.missingPhotos.length,
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

  const title = intl.formatMessage(
    { id: "PHOTOS_UPLOADED_TITLE", defaultMessage: "photos uploaded: {count}" },
    { count: store.uploadedImages.length },
  );

  return (
    <div style={{ marginTop: "2rem" }} id="bulk-import-image-upload-info">
      <InfoAccordion
        icon={<FaImage size={20} color={theme.primaryColors.primary500} />}
        title={title}
        data={data}
        expanded={expanded}
      />
    </div>
  );
});

export default BulkImportImageUploadInfo;
