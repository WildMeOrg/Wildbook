import React, { useState, useEffect } from "react";
import { Form, Col, Row, Container } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { FormControl } from "react-bootstrap";
import { useIntl } from "react-intl";
import { observer } from "mobx-react-lite";

const FormMeasurements = observer(({ data, filterId, store }) => {
  const [inputs, setInputs] = useState(
    data?.map((item) => ({ type: item, operator: "gte", value: "" })),
  );
  const intl = useIntl();

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
      const field = `biologicalMeasurements.${input.type}`;

      let query = null;

      if (input.operator === "term") {
        if (input.value !== "") {
          query = { term: { [field]: input.value } };
        }
      } else {
        if (input.value !== "") {
          query = { range: { [field]: { [input.operator]: input.value } } };
        }
      }
      if (query) {
        if (input.value) {
          store.addFilter(
            id,
            "filter",
            query,
            `biologicalMeasurements.${input.type}`,
          );
        }
      } else {
        store.removeFilter(id);
      }
    });
  };

  useEffect(() => {
    if (data.length > 0) {
      const formData = store.formFilters.filter((item) =>
        item.filterId.includes(filterId),
      );
      const inputs = [];
      if (formData.length > 0) {
        formData.forEach((item) => {
          const type = item.filterId.split(".")[1];
          let operator = "gte";
          let value = "";
          const rangeFilter = item.query?.range?.[item.filterId];
          const termFilter = item.query?.term?.[item.filterId];

          if (rangeFilter) {
            operator = Object.keys(rangeFilter)[0];
            value = Object.values(rangeFilter)[0];
          } else if (termFilter) {
            operator = "term";
            value = termFilter;
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

  return (
    <Container className="mt-3">
      <h5>
        <FormattedMessage id={"FILTER_BIOLOGICAL_MEASUREMENTS"} />
      </h5>
      {inputs
        .sort((a, b) => a.type.localeCompare(b.type))
        .map((input, index) => {
          return (
            <Row key={index} className="mb-3">
              <Col md={4} className="d-flex align-items-center">
                {input.type.charAt(0).toUpperCase() + input.type.slice(1)}
              </Col>
              <Col
                md={2}
                style={{
                  marginBotton: "10px",
                }}
              >
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
});

export default React.memo(FormMeasurements);
