import React, { useState } from 'react';
import { FormattedMessage } from 'react-intl';
import { Modal, Button as BootstrapButton, FormText } from 'react-bootstrap';
import AreaMap from "../fields/filters/AreaMap"
import DeleteButton from '../DeleteButton';
import { defaultAreaBounds } from '../../constants/defaults';
import Text from '../Text';

export default function AreaInput({
  schema,
  value,
  onChange,
  minimalLabels = false,
  ...rest
}) {
  const [modalOpen, setModalOpen] = useState(false);
  const [mapArea, setMapArea] = useState(defaultAreaBounds);

  const onClose = () => setModalOpen(false);

  return (
    <div className = "mt-1 w-100 h-100">
      {/* {!value && (
        <BootstrapButton
          size="sm"
          onClick={() => setModalOpen(true)}
          {...rest}
        >
          <FormattedMessage id="CHOOSE_ON_MAP" />
        </BootstrapButton>
      )}
      {value && (
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <Text id="BOUNDING_BOX_SELECTED" />
          <DeleteButton
            onClick={() => {
              onChange(null);
            }}
          />
        </div>
      )} */}
      {schema && !minimalLabels && schema.descriptionId && (
        <FormText style={{ maxWidth: 220 }}>
          <FormattedMessage id={schema.descriptionId} />
        </FormText>
      )}
      {/* <Modal show={modalOpen} onHide={onClose}>
        <Modal.Header closeButton>
          <Modal.Title>
            <FormattedMessage id="SELECT_BOUNDING_BOX" />
          </Modal.Title>
        </Modal.Header>
        <Modal.Body> */}
          <AreaMap
            startBounds={defaultAreaBounds}
            onChange={clickedPoint => setMapArea(clickedPoint)}
          />
        {/* </Modal.Body>
        <Modal.Footer>
          <BootstrapButton variant="secondary" onClick={onClose}>
            <FormattedMessage id="CANCEL" />
          </BootstrapButton>
          <BootstrapButton
            variant="primary"
            onClick={() => {
              onChange(mapArea);
              onClose();
            }}
          >
            <FormattedMessage id="CONFIRM" />
          </BootstrapButton>
        </Modal.Footer>
      </Modal> */}
    </div>
  );
}
