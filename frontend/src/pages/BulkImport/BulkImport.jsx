import { observer, useLocalObservable } from "mobx-react-lite";
import React, { useEffect } from "react";
import BulkImportStore from "./BulkImportStore";
import { BulkImportImageUpload } from "./BulkImportImageUpload";
import { Container } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { BulkImportUploadProgress } from "./BulkImportUploadProgress";
import { BulkImportSpreadsheet } from "./BulkImportSpreadsheet";
import { BulkImportTableReview } from "./BulkImportTableReview";
import { BulkImportTask } from "./BulkImportTask";
import { BulkImportSetLocation } from "./BulkImportSetLocation";
import { BulkImportContinueModal } from "./BulkImportContinueModal";
import BulkImportInstructionsModal from "./BulkImportInstructionsModal";
import DraftSaveIndicator from "./BulkImportDraftSavedIndicator";
import useGetBulkImportTask from "../../models/bulkImport/useGetBulkImportTask";
import { BulkImportUnfinishedTaskModal } from "./BulkImportUnfinishedTaskModal";

const BulkImport = observer(() => {
  const store = useLocalObservable(() => new BulkImportStore());
  const [savedSubmissionId, setSavedSubmissionId] = React.useState(null);
  const lastTask = localStorage.getItem("lastBulkImportTask") || null;
console.log("lastTask", lastTask);  
  const { task: unfinishedTask, isLoading } = useGetBulkImportTask(lastTask);

  console.log("unfinishedTask", unfinishedTask);

  useEffect(() => {
    const savedStore = JSON.parse(localStorage.getItem("BulkImportStore"));
    const submissionId = savedStore?.submissionId;
    console.log("submissionId", submissionId);
    if (submissionId) {
      setSavedSubmissionId(submissionId);
      store.hydrate(savedStore);
      store.setActiveStep(0);
      store.fetchAndApplyUploaded();
    }
  }, []);

  const firstRun = React.useRef(true);

  useEffect(() => {
    if (firstRun.current) {
      firstRun.current = false;
      return;
    }
    if (
      store.submissionId &&
      (store.imagePreview.length > 0 || store.spreadsheetData.length > 0)
    ) {
      store.saveState();
    }
  }, [
    JSON.stringify(store.imagePreview),
    JSON.stringify(store.spreadsheetData),
  ]);

  return (
    <Container>
      <div className="d-flex flex-row justify-content-between align-items-center">
        <h1 className="mt-3">
          <FormattedMessage id="BULK_IMPORT" />
        </h1>
        <DraftSaveIndicator store={store} />
      </div>

      {<BulkImportUploadProgress store={store} />}
      {store.activeStep === 0 && <BulkImportImageUpload store={store} />}
      {store.activeStep === 1 && <BulkImportSpreadsheet store={store} />}
      {store.activeStep === 2 && <BulkImportTableReview store={store} />}
      {store.activeStep === 3 && <BulkImportSetLocation store={store} />}
      {savedSubmissionId && <BulkImportContinueModal store={store} />}
      <BulkImportInstructionsModal store={store} />
      {lastTask ? <BulkImportUnfinishedTaskModal 
        taskId={lastTask}
      />: null}
      {/* <BulkImportTask
        store={store}
        taskId={store.submissionId}
        onDeleteTask={() => {
          store.deleteTask(store.submissionId);
          setSavedSubmissionId(null);
          store.resetToDefaults();
        }}
      /> */}
    </Container>
  );
});

export default BulkImport;
