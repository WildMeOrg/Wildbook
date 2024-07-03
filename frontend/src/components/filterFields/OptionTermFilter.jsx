import React, { useState, memo } from 'react';
import { get } from 'lodash-es';
import { useIntl } from 'react-intl';
import { Form, FormControl, InputGroup, DropdownButton, Dropdown } from 'react-bootstrap';
import Text from '../Text';

const OptionTermFilter = function (props) {
  const {
    label,
    labelId,
    description,
    descriptionId,
    filterId,
    onChange,
    onClearFilter,
    queryTerm,
    queryType = 'match',
    choices,
    width,
    minimalLabels = false,
    nested = false,
    style,
    ...rest
  } = props;

  const intl = useIntl();
  const [value, setValue] = useState('');

  function getLabel(object) {
    if (object?.labelId)
      return intl.formatMessage({ id: object.labelId });
    return get(object, 'label', 'Missing label');
  }

  const showDescription =
    !minimalLabels && (description || descriptionId);

  const translatedLabel = labelId
    ? intl.formatMessage({ id: labelId, defaultMessage: labelId })
    : label;

  const safeChoices = choices || [];

  return (
    <Form.Group style={style}>
      <Form.Label>{translatedLabel}</Form.Label>
      <InputGroup>
        <Form.Control
          as="select"
          id={`${queryTerm}-selector`}
          onChange={e => {
            const selectedValue = e.target.value;
            const selectedChoice = safeChoices.find(
              c => c.value === selectedValue,
            );
            const choiceLabel = getLabel(selectedChoice);
            const choiceValue = get(
              selectedChoice,
              'queryValue',
              selectedValue,
            );
            setValue(selectedValue);
            if (selectedValue === '') {
              onClearFilter(filterId);
            } else {
              onChange({
                filterId,
                nested,
                clause: get(selectedChoice, 'clause', 'filter'),
                descriptor: `${translatedLabel}: ${choiceLabel}`,
                query: {
                  [queryType]: { [queryTerm]: choiceValue },
                },
                selectedChoice,
              });
            }
          }}
          value={value}
          {...rest}
        >
          {safeChoices.map(option => (
            <option value={option.value} key={option.value}>
              {getLabel(option)}
            </option>
          ))}
        </Form.Control>
      </InputGroup>
      {showDescription ? (
        <Form.Text className="text-muted">{description}</Form.Text>
      ) : null}
    </Form.Group>
  );
};

export default memo(OptionTermFilter);
