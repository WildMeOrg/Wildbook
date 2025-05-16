import React from 'react';
import { observer } from "mobx-react-lite";
import InfoAccordion from "../../components/InfoAccordion";
import { FormattedMessage } from "react-intl";
import { toJS } from 'mobx';
export const BulkImportImageUploadInfo = observer(({ store }) => {

    const allPhotosUploading = toJS(store.imageSectionFileNames);
    const uploadedPhotos = store.uploadedImages;

    const missingPhotos = store.spreadsheetData.reduce((acc, row) => {
        const mediaAssets = Object.keys(row).filter((key) => key.startsWith("Encounter.mediaAsset0"));
        console.log("mediaAssets", mediaAssets);
        mediaAssets.forEach((mediaAsset) => {
            if (row[mediaAsset]) {
                const photo = row[mediaAsset].split(",");
                if (!uploadedPhotos.includes(photo) && !allPhotosUploading.includes(photo)) {
                    acc.push(...photo);
                }
            }
        });
        return acc;
    }, [])?.filter((photo) => !uploadedPhotos.includes(photo));

    console.log("missingPhotos", missingPhotos);

    const data = [
        {
            label: <FormattedMessage id="PHOTOS_MISSING"
                defaultMessage="photos missing" />,
            value: missingPhotos.length
        },
        {
            label: <FormattedMessage id="FAILED"
                defaultMessage="photos failed" />,
            value: store.failedImages.length
        },
        {
            label: <FormattedMessage id="FAILED"
                defaultMessage="photos uploaded" />,
            value: uploadedPhotos.length
        },
    ];

    const title = `photos uploaded: ${store.uploadedImages.length} `;

    return (
        <div style={{ marginTop: '2rem' }}>
            <InfoAccordion
                icon={<span className="bi bi-file-earmark-image" style={{ fontSize: '1.5rem' }} />}
                title={title}
                data={data}
            />
        </div>
    );
}
);

export default BulkImportImageUploadInfo;