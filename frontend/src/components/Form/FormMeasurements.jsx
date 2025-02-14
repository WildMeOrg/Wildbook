import React, { useState, useEffect } from "react";
import { Form, Col, Row, Container } from "react-bootstrap";
import { FormControl } from "react-bootstrap";
import { useIntl } from "react-intl";

function FormMeasurements({ data, field, filterId, store }) {
  const [inputs, setInputs] = useState(
    data?.map((item) => ({ type: item, operator: "gte", value: "" })),
  );
  const intl = useIntl();

  useEffect(() => {
    if (data.length > 0) {
      const formData = store.formFilters.filter((item) =>
        item.filterId.includes(filterId),
      );

      const inputs = [];
      if (formData.length > 0) {
        formData.forEach((item) => {
          const type = item.filterId.split(".")[1];
          const filters = item.query?.bool?.filter;
          const range = filters.find((filter) => "range" in filter)?.range[
            `${field}.value`
          ];
          const term = filters.find((filter) => "term" in filter)?.term[
            `${field}.value`
          ];
          let operator = "gte";
          let value = "";
          if (range) {
            operator = Object.keys(range)[0];
            value = Object.values(range)[0];
          } else if (term) {
            operator = "term";
            value = term;
          }

          inputs.push({
            type: type,
            operator: operator,
            value: value,
          });
        });
        const newInputs = data
          .filter((item) => !inputs.some((input) => input.type === item))
          .map((item) => ({
            type: item,
            operator: "gte",
            value: "",
          }));
        setInputs([...inputs, ...newInputs]);
      } else {
        const newInputs = data.map((item) => ({
          type: item,
          operator: "gte",
          value: "",
        }));
        setInputs(newInputs);
      }
    }
  }, [
    store.formFilters.find((item) => item.filterId.includes(filterId)),
    data,
  ]);

  const handleInputChange = (index, field, value) => {
    const updatedInputs = inputs.map((input, i) => {
      if (i === index) {
        return { ...input, [field]: value };
      }

      return input;
    });
    setInputs(updatedInputs);
    const id = `${filterId}.${updatedInputs[index].type}`;
    if (field === "value" || field === "operator") {
      if (value !== "") {
        updateQuery(updatedInputs);
      } else {
        store.removeFilter(id);
      }
    }
  };

  const updateQuery = (inputs) => {
    inputs.map((input) => {
      const id = `${filterId}.${input.type}`;
      const query =
        input.operator === "term"
          ? { term: { [`${field}.value`]: input.value } }
          : {
            range: { [`${field}.value`]: { [input.operator]: input.value } },
          };
      if (input.value) {
        store.addFilter(id, "nested", {
          bool: {
            filter: [
              {
                match: {
                  [`${field}.type`]: input.type,
                },
              },
              query,
            ],
          },

        }, id,
          field,);
      }
      return {
        match: {
          [`${field}.type`]: input.type,
        },
        range: {
          [`${field}.value`]: { [input.operator]: [input.value] },
        },
      };
    });
  };

  return (
    <Container className="mt-3">
      {inputs.map((input, index) => {
        return (
          <Row key={index} className="mb-3">
            <Col md={4} className="d-flex align-items-center">
              {input.type.charAt(0).toUpperCase() + input.type.slice(1)}
            </Col>
            <Col md={2}>
              <Form.Select
                aria-label="Select operator"
                value={input.operator}
                onChange={(e) =>
                  handleInputChange(index, "operator", e.target.value)
                }
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
                  marginRight: "10px",
                }}
                placeholder={intl.formatMessage({ id: "TYPE_NUMBER" })}
                onChange={(e) => {
                  handleInputChange(index, "value", e.target.value);
                }}
                value={input.value}
              />
            </Col>
          </Row>
        );
      })}
    </Container>
  );
}

export default FormMeasurements;
