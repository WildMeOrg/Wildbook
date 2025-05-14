
import React from "react";
import { Modal, Button } from "react-bootstrap";
import { FormattedMessage } from "react-intl";

export const BulkImportContinueModal = ({ store }) => {

    const [show, setShow] = React.useState(!!store.submissionId);

    React.useEffect(() => {
        setShow(Boolean(store.submissionId));
    }, [store.submissionId]);

    const handleContinue = () => {
        setShow(false);
    };

    const handleDelete = () => {
        store.resetToDefaults();
        localStorage.removeItem("BulkImportStore");
        window.location.reload();
    }

    return (
        <Modal show={show} onHide={() => { }} centered>
            <Modal.Header closeButton>
                <Modal.Title>
                    <FormattedMessage id="BULK_IMPORT_CONTINUE_MODAL_TITLE" />
                </Modal.Title>
            </Modal.Header>
            <Modal.Body>
                <FormattedMessage id="BULK_IMPORT_CONTINUE_MODAL_BODY" />
            </Modal.Body>
            <Modal.Footer>
                <Button variant="secondary" onClick={handleDelete}>
                    <FormattedMessage id="BULK_IMPORT_DELETE_IMPORT_TASK" />
                </Button>
                <Button variant="primary" onClick={handleContinue}>
                    <FormattedMessage id="CONTINUE" />
                </Button>
            </Modal.Footer>
        </Modal>
    );
}
export default BulkImportContinueModal;