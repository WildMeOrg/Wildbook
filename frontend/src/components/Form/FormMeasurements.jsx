
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
        if (field === 'value' && value !== '') {
            updateQuery(updatedInputs);
        }
    };

    const updateQuery = (inputs) => {
        const must = inputs.filter(input => input.value !== '');
        must.map(input => {
            console.log("Input:", input);
            const type_field = `${filterId}.type`;
            const value_field = `${filterId}.value`;
            const id = `${filterId}.${input.type}`;
            console.log(type_field, value_field, id);
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

                            {
                                "range": {
                                    [`${filterId}.value`]: { [input.operator]: input.value }
                                }
                            }
                        ]
                    }
                }
            })
            return {
                "match": {
                    [`${field}.type`]: input.type
                },

                "range": {
                    [`${field}.value`]: { [input.operator]: [input.value] }
                }
            };
        });

        // if (must.length > 0) {
        //     onChange({
        //         filterId,
        //         clause: "nested",
        //         path: field,
        //         query: {
        //             "bool": {
        //                 "filter": must
        //             }
        //         }
        //     });
        // }
    };

    return (
        <Container className="mt-3">
            <h5><FormattedMessage id={field} /></h5>
            {inputs.map((input, index) => (
                <Row key={index} className="mb-3">
                    <Col md={2} className='d-flex align-items-center'>
                        {input.type.charAt(0).toUpperCase() + input.type.slice(1)}
                    </Col>
                    <Col md={5}>
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
                    <Col md={5}>
                        <FormControl
                            className="w-100"
                            type="number"
                            style={{
                                width: "100px",
                                marginLeft: "10px",
                                marginRight: "10px"
                            }}
                            placeholder="Type Here"
                            onChange={(e) => {
                                console.log(e.target.value);
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
