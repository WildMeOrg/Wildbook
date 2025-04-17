
import { observer, useLocalObservable } from 'mobx-react-lite';
import { toJS } from 'mobx';
import React from 'react';
import BulkImportStore from './BulkImportStore';
import { BulkImportImage } from './BulkImportImage';
import { Container } from 'react-bootstrap';
import { FormattedMessage } from 'react-intl';
import { BulkImportProgress } from './BulkImportProgress';
import { BulkImportSpreadsheet } from './BulkImportSpreadsheet';
import { BulkImportTableReview } from './BulkImportTableReview';

const BulkImport = observer(() => {

    const store = useLocalObservable(() => new BulkImportStore());
    return (
        <Container>
            <h1 className="mt-3">
                <FormattedMessage id="BULK_IMPORT" />
            </h1>
            <BulkImportProgress  store={store}/>
            {store._activeStep === 0 && <BulkImportImage  store={store}/>}
            {store._activeStep === 1 && <BulkImportSpreadsheet store={store}/>}
            {store._activeStep === 2 && <BulkImportTableReview store={store}/>}
        </Container>
    );
});

export default BulkImport;