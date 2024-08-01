
import React, { useState, useEffect } from 'react';
import { Form, Col, Row, Container } from 'react-bootstrap';
import { FormattedMessage } from 'react-intl';
import { FormControl } from 'react-bootstrap';
import { useIntl } from 'react-intl';

function FormMeasurements({
    data,
    onChange,
    field,
    filterId
}) {
    const [inputs, setInputs] = useState(data?.map(item => ({ type: item, operator: 'gte', value: '' })));
    const intl = useIntl();
    useEffect(() => {

        if (data) {
            const newInputs = data.map(item => ({ type: item, operator: 'gte', value: '' }));
            setInputs(prevInputs => {
                // Only update if data has changed
                if (JSON.stringify(prevInputs.map(input => input.type)) !== JSON.stringify(data)) {
                    return newInputs;
                }
                return prevInputs;
            });
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
        if (field === 'value' || field === 'operator') {
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
            const query = input.operator === 'term' ? { term: { [`${field}.value`]: input.value } } : { range: { [`${field}.value`]: { [input.operator]: input.value } } };
            if (input.value) {
                onChange({
                    filterId: id,
                    clause: "nested",
                    path: field,
                    query: {
                        "bool": {
                            "filter": [

                                {
                                    "match": {
                                        [`${filterId}.type`]: input.type
                                    },
                                },

                                query
                            ]
                        }
                    }
                })
            }

            return {
                "match": {
                    [`${field}.type`]: input.type
                },

                "range": {
                    [`${field}.value`]: { [input.operator]: [input.value] }
                }
            };
        });

    };

    return (
        <Container className="mt-3">
            {inputs.map((input, index) => (
                <Row key={index} className="mb-3">
                    <Col md={4} className='d-flex align-items-center'>
                        {input.type.charAt(0).toUpperCase() + input.type.slice(1)}
                    </Col>
                    <Col md={2}
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
                            placeholder={intl.formatMessage({ id: "TYPE_HERE" })}
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
