import React from 'react';
import { observer } from "mobx-react-lite";
import InfoAccordion from './InfoAccordion';
import { FormattedMessage } from "react-intl";
import { toJS } from 'mobx';
export const BulkImportImageUploadInfo = observer(({ store }) => {

    console.log("uploadedImages", JSON.stringify(store.uploadedImages));

    const allPhotosShouldBeUploaded = [];
    store.spreadsheetData.forEach((row) => {
        const mediaAssets = Object.keys(row).filter((key) => key.startsWith("Encounter.mediaAsset"));
        mediaAssets.forEach((mediaAsset) => {
            if (row[mediaAsset]) {
                allPhotosShouldBeUploaded.push(row[mediaAsset]);
            }
        });
    }
    );
    const allPhotosUploading = toJS(store.imageSectionFileNames);
    const uploadedPhotos = store.uploadedImages;

    console.log("allPhotosShouldBeUploaded", allPhotosShouldBeUploaded);
    console.log("allPhotosUploading", allPhotosUploading);
    console.log("uploadedPhotos", uploadedPhotos);
    const missingPhotos = allPhotosShouldBeUploaded.filter((photo) => !uploadedPhotos.includes(photo));
    const data = [
        {
            label: <FormattedMessage id="PHOTOS_MISSING"
                defaultMessage="photos missing" />,
            value: missingPhotos.length
        },
        {
            label: <FormattedMessage id="FAILED"
                defaultMessage="photos failed" />,
            value: 123
        },
        {
            label: <FormattedMessage id="FAILED"
                defaultMessage="photos uploaded" />,
            value: uploadedPhotos.length
        },
    ];

    const title = `images uploaded: ${store.uploadedImages.length} `;

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