import React from "react";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import { Form } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import Description from "../Form/Description";
import FormGroupText from "../Form/FormGroupText";
import Select from "react-select";
import AndSelector from "../AndSelector";
import OrSelector from "../OrSelector";

export default function SocialFilter({
  data,
  onChange,
}) {
  const [isUnitChecked, setIsUnitChecked] = React.useState(false);
  const [isRoleChecked, setIsRoleChecked] = React.useState(false);
  const socialRoleOptions = data?.socialUnitRole?.map((item) => {
    return {
      value: item,
      label: item
    };
  }) || [];

  const socialUnitOptions = data?.socialUnitName?.map((item) => {
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

      <div className="d-flex flex-row justify-content-between mt-2">
        <Form.Label>
          <FormattedMessage id="FILTER_RELATIONSHIP_ROLE" />
        </Form.Label>

        <Form.Check
          type="checkbox"
          id="custom-checkbox"
          label={<FormattedMessage id="USE_AND_OPERATOR" />}
          checked={isUnitChecked}
          onChange={() => {
            setIsUnitChecked(!isUnitChecked);
          }
          }
        />
      </div>

      {
        isUnitChecked ? <AndSelector
          isMulti={true}
          noDesc={true}
          label="FILTER_SOCIAL_UNIT"
          onChange={onChange}
          options={socialUnitOptions}
          field="individualSocialUnits"
          term="terms"
          filterId={"individualSocialUnits"}
          filterKey={"Social Group Unit"}
        /> : <FormGroupMultiSelect
          isMulti={true}
          noLabel={true}
          noDesc={true}
          label="FILTER_SOCIAL_UNIT"
          onChange={onChange}
          options={socialUnitOptions}
          field="individualSocialUnits"
          term="terms"
          filterId={"individualSocialUnits"}
          filterKey={"Social Group Unit"}
        />

      }


      <div className="d-flex flex-row justify-content-between mt-2">
        <Form.Label>
          <FormattedMessage id="FILTER_RELATIONSHIP_ROLE" />
        </Form.Label>

        <Form.Check
          type="checkbox"
          id="custom-checkbox"
          label={<FormattedMessage id="USE_AND_OPERATOR" />}
          checked={isRoleChecked}
          onChange={() => {
            setIsRoleChecked(!isRoleChecked);
          }
          }
        />
      </div>

      {
        isRoleChecked ? <AndSelector
          isMulti={true}
          noDesc={true}
          noLabel={true}
          label="FILTER_RELATIONSHIP_ROLE"
          options={socialRoleOptions}
          onChange={onChange}
          field="individualRelationshipRoles"
          term={"terms"}
          filterId={"individualRelationshipRoles"}
          filterKey={"Relationship Role"}
        /> : <FormGroupMultiSelect
          isMulti={true}
          noDesc={true}
          noLabel={true}
          label="FILTER_RELATIONSHIP_ROLE"
          options={socialRoleOptions}
          onChange={onChange}
          field="individualRelationshipRoles"
          term={"terms"}
          filterId={"individualRelationshipRoles"}
          filterKey={"Relationship Role"}
        />
      }

      {/* <FormGroupMultiSelect
        isMulti={true}
        noDesc={true}
        noLabel={true}
        label="FILTER_RELATIONSHIP_ROLE"
        options={socialRoleOptions}
        onChange={onChange}
        field="socialRole"
        term={"terms"}
        filterId={"socialRole"}
        filterKey={"Relationship Role"}
      /> */}
    </div>
  );
}