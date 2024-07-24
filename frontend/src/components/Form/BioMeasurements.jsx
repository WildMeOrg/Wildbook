
import React, { useState, useEffect } from 'react';
import { Form, Col, Row, Container } from 'react-bootstrap';
import { FormattedMessage } from 'react-intl';
import { FormControl } from 'react-bootstrap';

function FormMeasurements({
    data,
    onChange,
    field,
    filterId
}) {
    const [inputs, setInputs] = useState(data?.map(item => ({ type: item, operator: 'gte', value: '' })));
    useEffect(() => {
        if (data) {
            const newInputs = data.map(item => ({ type: item, operator: 'gte', value: '' }));
            setInputs(newInputs);
        }
    }, [data]);

    const handleInputChange = (index, field, value) => {
        const updatedInputs = inputs.map((input, i) => {
            if (i === index) {
                return { ...input, [field]: value };
            }
            return input;
        });
        setInputs(updatedInputs);
        const id = `${filterId}.${updatedInputs[index].type}`;
        if (field === 'value') {
            if (value !== '') {
                updateQuery(updatedInputs);
            } else {
                onChange(null, id);
            }
        }
    };

    const updateQuery = (inputs) => {

        inputs.map(input => {
            const id = `${filterId}.${input.type}`;
            const operator = input.operator;
            const value = input.value;
            const field = `biologicalMeasurements.${input.type}`;
            console.log(id, operator, value);
            if (input.value) {
                onChange({
                    filterId: id,
                    clause: "filter",
                    query: {
                        range: {
                            [field]: {
                                [operator]: value
                            },
                        }
                    }
                }
                )
            }
        });
    };

    return (
        <Container className="mt-3">
            <h5><FormattedMessage id={field} /></h5>
            {inputs.map((input, index) => (
                <Row key={index} className="mb-3">
                    <Col md={4} className='d-flex align-items-center'>
                        {input.type.charAt(0).toUpperCase() + input.type.slice(1)}
                    </Col>
                    <Col md={2}
                        style={{
                            marginBotton: "10px",
                        }}
                    >
                        <Form.Select
                            aria-label="Select operator"
                            value={input.operator}
                            onChange={e => handleInputChange(index, 'operator', e.target.value)}
                        >
                            <option value="gte">&ge;</option>
                            <option value="lte">&le;</option>
                            <option value="term">=</option>
                        </Form.Select>
                    </Col>
                    <Col md={6}>
                        <FormControl
                            className="w-100"
                            type="number"
                            style={{             
                                                  
                                marginRight: "10px"
                            }}
                            placeholder="Type Here"
                            onChange={(e) => {
                                handleInputChange(index, 'value', e.target.value);
                            }
                            }

                        />
                    </Col>
                </Row>
            ))}
        </Container>
    );
}

export default FormMeasurements;
