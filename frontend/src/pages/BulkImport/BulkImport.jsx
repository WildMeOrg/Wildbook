import { observer } from "mobx-react-lite";
import React, { useEffect, useState } from "react";
import BulkImportStore from "./BulkImportStore";
import { BulkImportImageUpload } from "./BulkImportImageUpload";
import { Container } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { BulkImportUploadProgress } from "./BulkImportUploadProgress";
import { BulkImportSpreadsheet } from "./BulkImportSpreadsheet";
import { BulkImportTableReview } from "./BulkImportTableReview";
import { BulkImportSetLocation } from "./BulkImportSetLocation";
import { BulkImportContinueModal } from "./BulkImportContinueModal";
import BulkImportInstructionsModal from "./BulkImportInstructionsModal";
import DraftSaveIndicator from "./BulkImportDraftSavedIndicator";
import useGetBulkImportTask from "../../models/bulkImport/useGetBulkImportTask";
import { BulkImportUnfinishedTaskModal } from "./BulkImportUnfinishedTaskModal";

const BulkImport = observer(() => {
  const store = React.useMemo(() => new BulkImportStore(), []);
  const [savedSubmissionId, setSavedSubmissionId] = React.useState(null);
  const lastTask = localStorage.getItem("lastBulkImportTask") || null;
  const { task: unfinishedTask } = useGetBulkImportTask(lastTask);
  const [mountedSteps, setMountedSteps] = useState([0]);
  const [ renderMode1, setRenderMode1 ] = useState();

  useEffect(() => {
    if (!mountedSteps.includes(store.activeStep)) {
      setMountedSteps(prev => [...prev, store.activeStep]);
    }
  }, [store.activeStep]);

  useEffect(() => {
    const savedStore = JSON.parse(localStorage.getItem("BulkImportStore"));
    const submissionId = savedStore?.submissionId;
    if (submissionId) {
      // setRenderMode1("list");
      setSavedSubmissionId(submissionId);
    }
  }, []);

  useEffect(() => {
    if (
      store.submissionId &&
      (store.uploadedImages.length > 0 || store.spreadsheetData.length > 0)
    ) {
      store.saveState();
    }
  }, [
    store.uploadedImages.length,
    store.spreadsheetData.length,
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

      {mountedSteps.includes(0) && (
        <div style={{ display: store.activeStep === 0 ? "block" : "none" }}>
          <BulkImportImageUpload store={store} renderMode1={renderMode1}/>
        </div>
      )}
      {mountedSteps.includes(1) && (
        <div style={{ display: store.activeStep === 1 ? "block" : "none" }}>
          <BulkImportSpreadsheet store={store} />
        </div>
      )}
      {mountedSteps.includes(2) && (
        <div style={{ display: store.activeStep === 2 ? "block" : "none" }}>
          <BulkImportTableReview store={store} />
        </div>
      )}
      {mountedSteps.includes(3) && (
        <div style={{ display: store.activeStep === 3 ? "block" : "none" }}>
          <BulkImportSetLocation store={store} />
        </div>
      )}

      {savedSubmissionId && <BulkImportContinueModal store={store} setRenderMode1={setRenderMode1}/>}
      <BulkImportInstructionsModal store={store} />

      {unfinishedTask && unfinishedTask.status && unfinishedTask.status !== "completed" ? <BulkImportUnfinishedTaskModal
        fileName={unfinishedTask?.sourceName || "file name"}
        dateCreated={unfinishedTask?.dateCreated || "file last edited date"}
        taskId={lastTask}
        taskStatus={unfinishedTask?.status || "status not available"}
      /> : null}

    </Container>
  );
});

export default BulkImport;
