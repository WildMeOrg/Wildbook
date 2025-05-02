
import React from "react";
import { BulkImportIdentificationProgress } from "./BulkImportIdentificationProgress";
import { observer } from "mobx-react-lite";

export const BulkImportIdentification = observer(({ store }) => {
    return (
        <div>
            {/* <BulkImportIdentificationProgress store={store}/> */}
            <h2>Identification</h2>
        </div>
    );
});