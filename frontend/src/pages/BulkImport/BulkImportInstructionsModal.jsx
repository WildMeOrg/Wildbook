import React from 'react';
import PropTypes from 'prop-types';
import { Modal, Button } from 'react-bootstrap';
import { FormattedMessage } from 'react-intl';

const BulkImportInstructionsModal = ({ show, onHide }) => (
  <Modal
    show={show}
    onHide={onHide}
    size="lg"
    aria-labelledby="bulk-import-modal"
    centered
    scrollable
  >
    <Modal.Header >
      <Modal.Title id="bulk-import-modal">
        <FormattedMessage
          id="bulkImport.instructions.title"
          defaultMessage="Bulk Import Instructions"
        />
      </Modal.Title>
    </Modal.Header>

    <Modal.Body>
      <p>
        <FormattedMessage
          id="bulkImport.instructions.description"
          defaultMessage="Bulk Import helps you upload many records at once. You’ll go through 4 upload steps, but there's one important step before you begin:"
        />
      </p>

      <h5>
        <FormattedMessage
          id="bulkImport.instructions.step0.title"
          defaultMessage="Step 0: Preparation"
        />
      </h5>
      <p>
        <FormattedMessage
          id="bulkImport.instructions.step0.content"
          defaultMessage="Prepare your spreadsheet and images using the guidance in our {helpDocsLink}."
          values={{
            helpDocsLink: (
              <a href="/help/docs" target="_blank" rel="noopener noreferrer">
                <FormattedMessage
                  id="bulkImport.instructions.helpDocs"
                  defaultMessage="help docs"
                />
              </a>
            ),
          }}
        />
      </p>

      <h5>
        <FormattedMessage
          id="bulkImport.instructions.step1.title"
          defaultMessage="Step 1: Upload Images"
        />
      </h5>
      <ul>
        <li>
          <FormattedMessage
            id="bulkImport.instructions.step1.placeImages"
            defaultMessage="Place all your images in a single folder"
          />
        </li>
        <li>
          <FormattedMessage
            id="bulkImport.instructions.step1.imageRequirements"
            defaultMessage="Images must be:"
          />
          <ul>
            <li>
              <FormattedMessage
                id="bulkImport.instructions.step1.format"
                defaultMessage="JPEG, JPG or PNG format"
              />
            </li>
            <li>
              <FormattedMessage
                id="bulkImport.instructions.step1.maxSize"
                defaultMessage="Under 3MB"
              />
            </li>
            <li>
              <FormattedMessage
                id="bulkImport.instructions.step1.maxCount"
                defaultMessage="Less than 200 per upload"
              />
            </li>
          </ul>
        </li>
        <li>
          <FormattedMessage
            id="bulkImport.instructions.step1.filenameRequirements"
            defaultMessage="Filenames must"
          />
          <ul>
            <li>
              <FormattedMessage
                id="bulkImport.instructions.step1.lettersNumbers"
                defaultMessage="Use only English letters, numbers, periods and spaces"
              />
            </li>
            <li>
              <FormattedMessage
                id="bulkImport.instructions.step1.matchEntries"
                defaultMessage="Exactly match entries in Encounter.mediaAsset column of your spreadsheet"
              />
            </li>
          </ul>
        </li>
        <li>
          <FormattedMessage
            id="bulkImport.instructions.step1.photographyGuidelines"
            defaultMessage="View {link}"
            values={{
              link: (
                <a href="/photography-guidelines" target="_blank" rel="noopener noreferrer">
                  <FormattedMessage
                    id="bulkImport.instructions.photographyGuidelinesLink"
                    defaultMessage="Photography Guidelines"
                  />
                </a>
              ),
            }}
          />
        </li>
      </ul>

      <h5>
        <FormattedMessage
          id="bulkImport.instructions.step2.title"
          defaultMessage="Step 2: Upload Spreadsheet"
        />
      </h5>
      <ul>
        <li>
          <FormattedMessage
            id="bulkImport.instructions.step2.useTemplate"
            defaultMessage="Use Wildbook Standard Format Spreadsheet"
            values={{
              link: (
                <a href="/spreadsheet-template.xlsx" target="_blank" rel="noopener noreferrer">
                  <FormattedMessage
                    id="bulkImport.instructions.step2.templateLink"
                    defaultMessage="Wildbook Standard Format Spreadsheet"
                  />
                </a>
              ),
            }}
          />
        </li>
        <li>
          <FormattedMessage
            id="bulkImport.instructions.step2.mediaAssetMatch"
            defaultMessage="Make sure Encounter.mediaAsset values match your filenames exactly."
          />
        </li>
        <li>
          <FormattedMessage
            id="bulkImport.instructions.step2.acceptedValues"
            defaultMessage="Encounter.genus and Encounter.specificEpithet should use accepted values."
          />
        </li>
      </ul>

      <h5>
        <FormattedMessage
          id="bulkImport.instructions.step3.title"
          defaultMessage="Step 3: Review"
        />
      </h5>
      <p>
        <FormattedMessage
          id="bulkImport.instructions.step3.content"
          defaultMessage="Review Spreadsheets and fix errors directly on the site - no need to reupload for small changes."
        />
      </p>

      <h5>
        <FormattedMessage
          id="bulkImport.instructions.step4.title"
          defaultMessage="Step 4: Set Location ID for Identification"
        />
      </h5>
      <p>
        <FormattedMessage
          id="bulkImport.instructions.step4.content"
          defaultMessage="Select the Location ID during import. This triggers automatic detection and identification after import — no manual steps needed later."
        />
      </p>

      <h5>
        <FormattedMessage
          id="bulkImport.instructions.needHelp.title"
          defaultMessage="Need Help?"
        />
      </h5>
      <ul>
        <li>
          <FormattedMessage
            id="bulkImport.instructions.needHelp.videoTutorial"
            defaultMessage="Watch our step by step {youtubeLink}"
            values={{
              youtubeLink: (
                <a href="https://www.youtube.com/watch?v=..." target="_blank" rel="noopener noreferrer">
                  <FormattedMessage
                    id="bulkImport.instructions.youtubeLinkText"
                    defaultMessage="video tutorial in YouTube"
                  />
                </a>
              ),
            }}
          />
        </li>
        <li>
          <FormattedMessage
            id="bulkImport.instructions.needHelp.docs"
            defaultMessage="Check the {wildbookDocsLink} for troubleshooting or contact support"
            values={{
              wildbookDocsLink: (
                <a href="https://docs.wildbook.org" target="_blank" rel="noopener noreferrer">
                  <FormattedMessage
                    id="bulkImport.instructions.wildbookDocs"
                    defaultMessage="Wildbook Docs"
                  />
                </a>
              ),
            }}
          />
        </li>
      </ul>
    </Modal.Body>

    <Modal.Footer>
      <Button variant="secondary" onClick={onHide}>
        <FormattedMessage
          id="bulkImport.instructions.closeButton"
          defaultMessage="Close"
        />
      </Button>
    </Modal.Footer>
  </Modal>
);

BulkImportInstructionsModal.propTypes = {
  show: PropTypes.bool.isRequired,
  onHide: PropTypes.func.isRequired,
};

export default BulkImportInstructionsModal;
