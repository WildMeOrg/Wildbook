import { observer, useLocalObservable } from "mobx-react-lite";
import React, { useEffect } from "react";
import BulkImportStore from "./BulkImportStore";
import { BulkImportImageUpload } from "./BulkImportImageUpload";
import { Container } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { BulkImportUploadProgress } from "./BulkImportUploadProgress";
import { BulkImportSpreadsheet } from "./BulkImportSpreadsheet";
import { BulkImportTableReview } from "./BulkImportTableReview";
import { BulkImportIdentification } from "./BulkImportIdentification";
import { BulkImportTask } from "./BulkImportTask";
import { BulkImportSetLocation } from "./BulkImportSetLocation";
import { BulkImportContinueModal } from "./BulkImportContinueModal";
const BulkImport = observer(() => {
  const store = useLocalObservable(() => new BulkImportStore());
  const [savedSubmissionId, setSavedSubmissionId] = React.useState(null);

  useEffect(() => {
    const savedStore = JSON.parse(localStorage.getItem("BulkImportStore"));
    const submissionId = savedStore?.submissionId;
    console.log("submissionId", submissionId);
    if (submissionId) {
      setSavedSubmissionId(submissionId);
      store.hydrate(savedStore);
      store.setActiveStep(0);
    }
  }, []);

  useEffect(() => {
    if (store.submissionId && (store.imagePreview.length > 0 || store.spreadsheetData.length > 0)) {
      console.log("Saving store to localStorage", store.submissionId);
      store.saveState();
    }
  }, [JSON.stringify(store.imagePreview), JSON.stringify(store.spreadsheetData)])

  return (
    <Container>
      <h1 className="mt-3">
        <FormattedMessage id="BULK_IMPORT" />
      </h1>
      {<BulkImportUploadProgress store={store} />}
      {store.activeStep === 0 && <BulkImportImageUpload store={store} />}
      {store.activeStep === 1 && <BulkImportSpreadsheet store={store} />}
      {store.activeStep === 2 && <BulkImportTableReview store={store} />}
      {store.activeStep === 3 && (
        <BulkImportSetLocation store={store} />
      )}
      {
        savedSubmissionId && (
          <BulkImportContinueModal store={store} />
        )
      }
    </Container>
  );
});

export default BulkImport;
