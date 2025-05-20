import React, { useState } from 'react';
import { Form } from 'react-bootstrap';

function FormDualInputs({
    label,
    label1,
    label2,
    onChange,  
}) {
  const [inputs, setInputs] = useState([{name: '', value: ''}]);

  const handleInputChange = (index, event) => {
    const newInputs = inputs.map((input, i) => {
      if (i === index) {
        const updatedInput = { ...input, [event.target.name]: event.target.value };
        onChange({
          filterId: `${label}.${updatedInput.name || input.name}`,  // Use input.name if updatedInput.name is empty
          clause: "filter",
          query: {
            match: {
              [`dynamicProperties.${updatedInput.name || input.name}`]: updatedInput.value || input.value  // Ensure not to send empty if one value is filled
            }
          },
          term: "match",
        });
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
            placeholder={label1}  
            value={input.name}
            onChange={e => handleInputChange(index, e)}
          />
          <Form.Control
            type="text"
            name="value"
            placeholder={label2}  
            value={input.value}
            onChange={e => handleInputChange(index, e)}
          />
        </Form.Group>
      ))}
    </Form>
  );
}

export default FormDualInputs;
