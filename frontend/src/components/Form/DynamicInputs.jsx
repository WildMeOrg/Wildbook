import React, { useEffect, useState, useRef } from "react";
import { Form } from "react-bootstrap";
import BrutalismButton from "../BrutalismButton";
import { FormattedMessage, useIntl } from "react-intl";
import { observer } from "mobx-react-lite";

const DynamicInputs = observer(({ store }) => {
  const intl = useIntl();
  const [inputs, setInputs] = useState([]);
  const inputRefs = useRef({});

  useEffect(() => {
    const storedValues = store.formFilters
      .filter((filter) => filter.filterId.includes("dynamicProperties."))
      .map((filter) => ({
        name: filter.filterKey.replace("dynamicProperties.", ""),
        value: filter?.query?.match?.[filter.filterKey] || "",
        originalName: filter.filterKey.replace("dynamicProperties.", ""),
      }));

    setInputs((prevInputs) => {
      const updatedInputs = prevInputs.map((input) => {
        const match = storedValues.find((s) => s.name === input.name);
        return match ? { ...input, value: match.value } : input;
      });
      const newInputs = storedValues.filter(
        (s) => !prevInputs.some((p) => p.name === s.name)
      );

      return [...updatedInputs, ...newInputs];
    });
  }, [store.formFilters]);

  const addInput = () => {
    const newInput = { name: "", value: "" };
    setInputs((prev) => [...prev, newInput]);

    const index = inputs.length;
    inputRefs.current[index] = { nameRef: React.createRef(), valueRef: React.createRef() };
  };

  const handleInputChange = (index, event) => {
    const { name, value } = event.target;
    setInputs((prevInputs) =>
      prevInputs.map((input, i) =>
        i === index ? { ...input, [name]: value } : input
      )
    );
  };

  const handleBlur = (index) => {
    const input = inputs[index];

    if (input.name && input.value) {
      store.addFilter(
        `dynamicProperties.${input.name}`,
        "filter",
        {
          match: {
            [`dynamicProperties.${input.name}`]: input.value,
          },
        },
        `dynamicProperties.${input.name}`
      );
    } else if (input.originalName) {
      store.removeFilter(`dynamicProperties.${input.originalName}`);
    }
  };

  return (
    <Form>
      {inputs.map((input, index) => {
        if (!inputRefs.current[index]) {
          inputRefs.current[index] = { nameRef: React.createRef(), valueRef: React.createRef() };
        }

        return (
          <Form.Group key={index} className="mb-3 d-flex flex-row gap-3">
            <Form.Control
              type="text"
              name="name"
              placeholder={intl.formatMessage({ id: "FILTER_OBSERVATION_NAME" })}
              value={input.name}
              ref={inputRefs.current[index].nameRef}
              onChange={(e) => handleInputChange(index, e)}
              onBlur={() => handleBlur(index)}
            />
            <Form.Control
              type="text"
              name="value"
              placeholder={intl.formatMessage({ id: "FILTER_OBSERVATION_VALUE" })}
              value={input.value}
              ref={inputRefs.current[index].valueRef}
              onChange={(e) => handleInputChange(index, e)}
              onBlur={() => handleBlur(index)}
              disabled={!input.name}
            />
          </Form.Group>
        );
      })}

      <BrutalismButton
        style={{ marginTop: "10px" }}
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
});

export default DynamicInputs;

