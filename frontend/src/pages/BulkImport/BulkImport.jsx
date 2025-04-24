import { observer, useLocalObservable } from "mobx-react-lite";
import React from "react";
import BulkImportStore from "./BulkImportStore";
import { BulkImportImageUpload } from "./BulkImportImageUpload";
import { Container } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { BulkImportUploadProgress } from "./BulkImportUploadProgress";
import { BulkImportSpreadsheet } from "./BulkImportSpreadsheet";
import { BulkImportTableReview } from "./BulkImportTableReview";
import { BulkImportIdentification } from "./BulkImportIdentification";

const BulkImport = observer(() => {
  const store = useLocalObservable(() => new BulkImportStore());
  return (
    <Container>
      <h1 className="mt-3">
        <FormattedMessage id="BULK_IMPORT" />
      </h1>
      {!store._uploadFinished && <BulkImportUploadProgress store={store} />}
      {store._activeStep === 0 && <BulkImportImageUpload store={store} />}
      {store._activeStep === 1 && <BulkImportSpreadsheet store={store} />}
      {store._activeStep === 2 && <BulkImportTableReview store={store} />}
      {store._activeStep === 3 && store._uploadFinished && (
        <BulkImportIdentification store={store} />
      )}
    </Container>
  );
});

export default BulkImport;
