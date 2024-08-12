import React, { useState } from 'react';
import { Form } from 'react-bootstrap';
import BrutalismButton from '../BrutalismButton';
import { FormattedMessage } from 'react-intl';
import { useIntl } from 'react-intl';

function DynamicInputs({
  onChange,
}) {
  const intl = useIntl();
  const [inputs, setInputs] = useState([{ name: '', value: '' }]);

  const addInput = () => {
    setInputs([...inputs, { name: '', value: '' }]);
  };

  const handleInputChange = (index, event) => {
    const newInputs = inputs.map((input, i) => {
      if (i === index) {
        const nameField = event.target.name === 'name' ? event.target.value : input.name;
        const valueField = event.target.name === 'value' ? event.target.value : input.value;
        const originalName = (event.target.name === 'name' && event.target.value !== '') ? event.target.value : input.originalName || input.name;

        const updatedInput = { ...input, name: nameField, value: valueField, originalName };

        if (updatedInput.name && updatedInput.value) {
          onChange({
            filterId: `dynamicProperties.${updatedInput.name}`,
            clause: "filter",
            query: {
              match: {
                [`dynamicProperties.${updatedInput.name}`]: updatedInput.value
              }
            },
            term: "match",

          });
        } else {
          if (updatedInput.originalName) {
            onChange(null, `dynamicProperties.${updatedInput.originalName}`);
          }
        }
        return updatedInput;
      }
      return input;
    });
    setInputs(newInputs);
  };

  return (
    <Form>
      {inputs.map((input, index) => (
        <Form.Group key={index} className="mb-3 d-flex flex-row gap-3" controlId={`inputGroup-${index}`}>
          <Form.Control
            type="text"
            name="name"
            placeholder={intl.formatMessage({ id: "FILTER_OBSERVATION_NAME" })}
            value={input.name}
            onChange={handleInputChange.bind(null, index)}
          />
          <Form.Control
            type="text"
            name="value"
            placeholder={intl.formatMessage({ id: 'FILTER_OBSERVATION_VALUE' })}
            value={input.value}
            onChange={handleInputChange.bind(null, index)}
            disabled={!input.name}
          />
        </Form.Group>
      ))}

      <BrutalismButton
        style={{
          marginTop: '10px'
        }}
        borderColor="#fff"
        color="white"
        noArrow
        backgroundColor="transparent"
        onClick={addInput}
      >
        <i className="bi bi-plus-square" style={{ marginRight: "10px" }}></i>
        <FormattedMessage id="FILTER_ADD_OBSERVATION_SEARCH" />
      </BrutalismButton>
    </Form>
  );
}

export default DynamicInputs;
