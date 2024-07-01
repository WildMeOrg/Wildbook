import React from 'react';
import { Button } from 'react-bootstrap';
import { Trash } from 'react-bootstrap-icons';

export default function DeleteButton({ ...rest }) {
  return (
    <Button variant="outline-danger" size="sm" {...rest}>
      <Trash />
    </Button>
  );
}
