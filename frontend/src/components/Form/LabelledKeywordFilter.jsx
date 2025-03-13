import React, { useEffect } from "react";
import { Form, FormGroup } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import Description from "../Form/Description";
import Select from "react-select";
import BrutalismButton from "../BrutalismButton";

const colourStyles = {
  option: (styles) => ({
    ...styles,
    color: "black",
  }),
  singleValue: (styles) => ({ ...styles, color: "black" }),
  menuPortal: (base) => ({ ...base, zIndex: 1050 }),
  control: (base) => ({ ...base, zIndex: 1, backgroundColor: "white" }),
};

export default function LabelledKeywordFilter({ data, store }) {
  const [isChecked_keyword, setIsChecked_keyword] = React.useState(false);

  const labelledKeywordsOptions =
    Object.entries(data?.labeledKeyword || {}).map(([key, _]) => {
      return {
        value: key,
        label: key,
      };
    }) || [];

  const [labelledKeywordPairs, setLabelledKeywordPairs] = React.useState([
    { labelledKeyword: null, labelledKeywordValues: [] },
  ]);

  const addLabelledKeywordPair = () => {
    setLabelledKeywordPairs([
      ...labelledKeywordPairs,
      { labelledKeyword: null, labelledKeywordValues: [] },
    ]);
  };

  const updateLabelledKeyword = (index, selectedOption) => {
    const newPairs = [...labelledKeywordPairs];
    newPairs[index].labelledKeyword = selectedOption.value;
    setLabelledKeywordPairs(newPairs);
  };

  const updateLabelledKeywordValues = (index, selectedOptions) => {
    const newPairs = [...labelledKeywordPairs];
    const selectedValues = selectedOptions
      ? selectedOptions.map((option) => option.value)
      : [];
    newPairs[index].labelledKeywordValues = selectedValues;
    setLabelledKeywordPairs(newPairs);

    if (selectedOptions.length === 0) {
      store.removeFilter(
        `mediaAssetLabeledKeywords.${newPairs[index].labelledKeyword}`,
      );
      return;
    }

    if (isChecked_keyword) {
      const query = selectedOptions.map((option) => {
        return {
          term: {
            [`mediaAssetLabeledKeywords.${newPairs[index].labelledKeyword}`]:
              option.value,
          },
        };
      });

      store.addFilter(
        `mediaAssetLabeledKeywords.${newPairs[index].labelledKeyword}`,
        "array",
        query,
        "Media Asset Labeled Keywords",
      );
    } else {
      const query = {
        terms: {
          [`mediaAssetLabeledKeywords.${newPairs[index].labelledKeyword}`]:
            selectedValues,
        },
      };

      store.addFilter(
        `mediaAssetLabeledKeywords.${newPairs[index].labelledKeyword}`,
        "filter",
        query,
        "Media Asset Labeled Keywords",
      );
    }
  };

  const handleCheckboxChange = () => {
    const newPairs = labelledKeywordPairs.map((pair) => ({
      ...pair,
      labelledKeywordValues: [],
    }));

    newPairs.forEach((pair) => {
      if (pair.labelledKeyword) {
        store.removeFilter(`mediaAssetLabeledKeywords.${pair.labelledKeyword}`);
      }
    });

    setLabelledKeywordPairs(newPairs);
    setIsChecked_keyword(!isChecked_keyword);
  };

  const keywordsFormValueArray_1 = store.formFilters?.find((filter) =>
    filter.filterId.includes("mediaAssetLabeledKeywords"),
  )?.query?.terms;

  const keywordsFormValueArray_2 = store.formFilters?.find((filter) =>
    filter.filterId.includes("mediaAssetLabeledKeywords"),
  )?.query;

  const keywordsANDChecked =
    Array.isArray(keywordsFormValueArray_2) || isChecked_keyword;

  const paris = [];

  useEffect(() => {
    store.formFilters
      .filter((item) => item.filterId.includes("mediaAssetLabeledKeywords"))
      .forEach((item) => {
        const query = item.query;
        const name = item.filterId.replace("mediaAssetLabeledKeywords.", "");
        if (keywordsANDChecked && !!keywordsFormValueArray_2) {
          const values = query.map((data) => {
            return Object.values(data.term)[0];
          });
          paris.push({ labelledKeyword: name, labelledKeywordValues: values });
        } else if (keywordsFormValueArray_1) {
          const terms = JSON.parse(JSON.stringify(query.terms));
          paris.push({
            labelledKeyword: name,
            labelledKeywordValues: Object.values(terms)[0],
          });
        }
        setLabelledKeywordPairs(paris);
      });
  }, [store.formFilters]);

  return (
    <FormGroup className="mt-3">
      <div className="d-flex flex-row justify-content-between mt-3">
        <Form.Label>
          <FormattedMessage id="FILTER_LABELLED_KEYWORDS" />
        </Form.Label>

        <Form.Check
          type="checkbox"
          id="custom-checkbox"
          label={<FormattedMessage id="USE_AND_OPERATOR" />}
          checked={keywordsANDChecked}
          onChange={handleCheckboxChange}
        />
      </div>
      <Description>
        <FormattedMessage id={`FILTER_LABELLED_KEYWORDS_DESC`} />
      </Description>
      {labelledKeywordPairs.map((pair, index) => (
        <div key={index} className="d-flex flex-row gap-3 mb-3">
          <div className="w-50">
            <Form.Label>
              <FormattedMessage id="FILTER_LABEL" />
            </Form.Label>
            <Select
              styles={colourStyles}
              menuPlacement="auto"
              menuPortalTarget={document.body}
              onChange={(e) => updateLabelledKeyword(index, e)}
              options={labelledKeywordsOptions}
              value={labelledKeywordsOptions.find(
                (option) => option.value === pair.labelledKeyword,
              )}
            />
          </div>
          <div className="w-50">
            <Form.Label>
              <FormattedMessage id="FILTER_VALUE" />
            </Form.Label>
            <Select
              isMulti
              options={(data?.labeledKeyword[pair.labelledKeyword] || []).map(
                (item) => ({
                  value: item,
                  label: item,
                }),
              )}
              styles={colourStyles}
              menuPlacement="auto"
              menuPortalTarget={document.body}
              onChange={(selectedOptions) =>
                updateLabelledKeywordValues(index, selectedOptions)
              }
              value={pair.labelledKeywordValues.map((value) => ({
                value: value,
                label: value,
              }))}
            />
          </div>
        </div>
      ))}
      <BrutalismButton
        style={{ marginTop: "10px" }}
        borderColor="#fff"
        color="white"
        noArrow
        backgroundColor="transparent"
        onClick={addLabelledKeywordPair}
      >
        <i className="bi bi-plus-square" style={{ marginRight: "10px" }}></i>
        <FormattedMessage id="ADD_LABELLED_KEYWORD_SEARCH" />
      </BrutalismButton>
    </FormGroup>
  );
}
