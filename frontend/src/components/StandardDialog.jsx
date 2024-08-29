import React from 'react';
import { Modal, Button } from 'react-bootstrap';
import { X } from 'react-bootstrap-icons';
import Text from './Text';

export default function StandardDialog({
  open,
  onClose,
  title,
  titleId,
  children,
  ...rest
}) {
  return (
    <Modal show={open} onHide={onClose} {...rest}>
      <Modal.Header>
        <Modal.Title>
          <Text id={titleId} style={{ marginRight: 60 }}>
            {title}
          </Text>
        </Modal.Title>
        <Button
          variant="close"
          aria-label="close"
          onClick={onClose}
          style={{ position: 'absolute', top: 4, right: 12 }}
        >
          <X />
        </Button>
      </Modal.Header>
      <Modal.Body>{children}</Modal.Body>
    </Modal>
  );
}
