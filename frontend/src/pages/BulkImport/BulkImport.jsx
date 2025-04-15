
import { observer, useLocalObservable } from 'mobx-react-lite';
import { toJS } from 'mobx';
import React from 'react';
import BulkImportStore from './BulkImportStore';
import { BulkImportImage } from './BulkImportImage';
import { Container } from 'react-bootstrap';
import { FormattedMessage } from 'react-intl';
import { BulkImportProgress } from './BulkImportProgress';
import { BulkImportSpreadsheet } from './BulkImportSpreadsheet';
import EditableDataTable from '../../components/EditableDataTable';

const BulkImport = observer(() => {

    const store = useLocalObservable(() => new BulkImportStore());
    return (
        <Container>
            <h1 className="mt-3">
                <FormattedMessage id="BULK_IMPORT" />
            </h1>
            <BulkImportProgress  store={store}/>
            <BulkImportImage  store={store}/>
            <BulkImportSpreadsheet store={store}/>
            <EditableDataTable store={store}/>
        </Container>
    );
});

export default BulkImport;