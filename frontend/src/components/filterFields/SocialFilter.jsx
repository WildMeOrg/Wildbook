import React from "react";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import { Form } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import Description from "../Form/Description";
import FormGroupText from "../Form/FormGroupText";
import Select from "react-select";

const colourStyles = {
  option: (styles) => ({
      ...styles,
      color: 'black', 
  }),
  control: (styles) => ({ ...styles, backgroundColor: 'white' }),
  singleValue: (styles) => ({ ...styles, color: 'black' }), 
};


export default function SocialFilter({
  data,
  onChange,
}) {
  const [isChecked, setIsChecked] = React.useState(false);
  const socialRoleOptions = data?.socialUnitRole?.map((item) => {
    return {
      value: item,
      label: item
    };
  }) || [];

  const socialUnitOptions = data?.socialUnitName.map((item) => {
    return {
      value: item,
      label: item
    };
  }) || [];

  return (
    <div>
      <h3><FormattedMessage id="FILTER_SOCIAL" /></h3>
      <Description>
        <FormattedMessage id="FILTER_SOCIAL_DESC" />
      </Description>

      <FormGroupMultiSelect
        isMulti={true}
        noDesc={true}
        label="FILTER_SOCIAL_UNIT"
        onChange={onChange}
        options = {socialUnitOptions}
        field="socialUnitName"
        term="terms"
        filterId={"socialUnitName"}
      />
      <div className="d-flex flex-row justify-content-between mt-2">
        <Form.Label>
          <FormattedMessage id="FILTER_RELATIONSHIP_ROLE" />
        </Form.Label>

        <Form.Check
          type="checkbox"
          id="custom-checkbox"
          label={<FormattedMessage id="USE_AND_OPERATOR" />}
          checked={isChecked}
          onChange={() => {
            setIsChecked(!isChecked);
          }
          }
        />
      </div>
          
      <FormGroupMultiSelect
        isMulti={isChecked}
        noDesc={true}
        noLabel={true}
        label="FILTER_RELATIONSHIP_ROLE"
        options={socialRoleOptions}
        onChange={onChange}
        field="socialRole"
        term={ isChecked? "terms" : "match"}
        filterId={"socialRole"}
      />
    </div>
  );
}