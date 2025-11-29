import React from "react";
import { Modal } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { observer } from "mobx-react-lite";
import MainButton from "../../components/MainButton";
import ThemeColorContext from "../../ThemeColorProvider";
import useGetSiteSettings from "../../models/useGetSiteSettings";
import {
  defaultMaxMediaSize,
  defaultMaxMediaCount,
} from "../constants/photoUpload.js";

const BulkImportInstructionsModal = observer(({ store }) => {
  const theme = React.useContext(ThemeColorContext);
  const { data } = useGetSiteSettings();
  const maxSize = data?.maximumMediaSizeMegabytes || defaultMaxMediaSize;
  const maxImageCount =
    data?.maximumMediaCountEncounter || defaultMaxMediaCount;

  return (
    <Modal
      show={store.showInstructions}
      onHide={() => store.setShowInstructions(false)}
      size="lg"
      aria-labelledby="bulk-import-modal"
      centered
      scrollable
      id="bulk-import-instructions-modal"
    >
      <Modal.Header closeButton>
        <Modal.Title id="bulk-import-modal">
          <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_TITLE" />
        </Modal.Title>
      </Modal.Header>

      <Modal.Body>
        <p>
          <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_DESCRIPTION" />
        </p>

        <h5>
          <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_STEP0_TITLE" />
        </h5>
        <p>
          <FormattedMessage
            id="BULK_IMPORT_INSTRUCTIONS_STEP0_CONTENT"
            values={{
              helpDocsLink: (
                <a
                  href="https://wildbook.docs.wildme.org/data/bulk-import-beta.html"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_HELP_DOCS" />
                </a>
              ),
            }}
          />
        </p>

        <h5>
          <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_STEP1_TITLE" />
        </h5>
        <ul>
          <li>
            <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_STEP1_PLACE_IMAGES" />
          </li>
          <li>
            <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_STEP1_IMAGE_REQUIREMENTS" />
            <ul>
              <li>
                <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_STEP1_FORMAT" />
              </li>
              <li>
                <FormattedMessage
                  id="BULK_IMPORT_INSTRUCTIONS_STEP1_MAX_SIZE"
                  values={{
                    maxSize: maxSize,
                  }}
                />
              </li>
              <li>
                <FormattedMessage
                  id="BULK_IMPORT_INSTRUCTIONS_STEP1_MAX_COUNT"
                  values={{
                    maxImageCount: maxImageCount,
                  }}
                />
              </li>
            </ul>
          </li>
          <li>
            <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_STEP1_FILENAME_REQUIREMENTS" />
            <ul>
              <li>
                <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_STEP1_LETTERS_NUMBERS" />
              </li>
              <li>
                <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_STEP1_MATCH_ENTRIES" />
              </li>
            </ul>
          </li>
          <li>
            <FormattedMessage
              id="BULK_IMPORT_INSTRUCTIONS_STEP1_PHOTOGRAPHY_GUIDELINES"
              values={{
                link: (
                  <a
                    href="https://wildbook.docs.wildme.org/data/photography-guidelines.html"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_PHOTOGRAPHY_GUIDELINES_LINK" />
                  </a>
                ),
              }}
            />
          </li>
        </ul>

        <h5>
          <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_STEP2_TITLE" />
        </h5>
        <ul>
          <li>
            <FormattedMessage
              id="BULK_IMPORT_INSTRUCTIONS_STEP2_USE_TEMPLATE"
              values={{
                link: (
                  <a
                    href="https://wildbook.docs.wildme.org/data/bulk-import-beta.html#spreadsheet-templates-by-use-case"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_STEP2_TEMPLATE_LINK" />
                  </a>
                ),
              }}
            />
          </li>

          <li>
            <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_STEP2_MEDIA_ASSET_MATCH" />
          </li>
          <li>
            <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_STEP2_ACCEPTED_VALUES" />
          </li>
        </ul>

        <h5>
          <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_STEP3_TITLE" />
        </h5>
        <p>
          <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_STEP3_CONTENT" />
        </p>

        <h5>
          <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_STEP4_TITLE" />
        </h5>
        <p>
          <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_STEP4_CONTENT" />
        </p>

        <h5>
          <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_NEED_HELP_TITLE" />
        </h5>
        <ul>
          <li>
            <FormattedMessage
              id="BULK_IMPORT_INSTRUCTIONS_NEED_HELP_VIDEO_TUTORIAL"
              values={{
                youtubeLink: (
                  <a
                    href="https://www.youtube.com/playlist?list=PLKr_by6qM_PwaBUyzncua6481TOf6ziPk"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_YOUTUBE_LINK_TEXT" />
                  </a>
                ),
              }}
            />
          </li>
          <li>
            <FormattedMessage
              id="BULK_IMPORT_INSTRUCTIONS_NEED_HELP_DOCS"
              values={{
                wildbookDocsLink: (
                  <a
                    href="https://docs.wildme.org/"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_WILDBOOK_DOCS" />
                  </a>
                ),
              }}
            />
          </li>
        </ul>
      </Modal.Body>

      <Modal.Footer>
        <MainButton
          onClick={() => store.setShowInstructions(false)}
          backgroundColor={theme.wildMeColors.cyan700}
          color={theme.defaultColors.white}
          noArrow={true}
          style={{
            width: "auto",
            fontSize: "1rem",
          }}
        >
          <FormattedMessage id="BULK_IMPORT_INSTRUCTIONS_CLOSE_BUTTON" />
        </MainButton>
      </Modal.Footer>
    </Modal>
  );
});

export default BulkImportInstructionsModal;
