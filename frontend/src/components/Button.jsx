import React from 'react';
import { FormattedMessage } from 'react-intl';
import { Button } from 'react-bootstrap';

export default function CustomButton (props) {
  return <Button {...props}>
    {/* <FormattedMessage id={props.id} /> */}
    Welcome Home
  </Button>;
}