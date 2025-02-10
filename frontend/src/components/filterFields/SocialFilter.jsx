import React from "react";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import { Form } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import Description from "../Form/Description";
import AndSelector from "../AndSelector";
import FormGroupText from "../Form/FormGroupText";
import { observer } from "mobx-react-lite";

const SocialFilter = observer(({ data, store }) => {
  const [isUnitChecked, setIsUnitChecked] = React.useState(false);
  const [isRoleChecked, setIsRoleChecked] = React.useState(false);
  const socialRoleOptions =
    data?.relationshipRole?.map((item) => {
      return {
        value: item,
        label: item,
      };
    }) || [];

  const socialUnitOptions =
    data?.socialUnitRole?.map((item) => {
      return {
        value: item,
        label: item,
      };
    }) || [];

  return (
    <div>
      <h4>
        <FormattedMessage id="FILTER_SOCIAL" />
      </h4>
      <Description>
        <FormattedMessage id="FILTER_SOCIAL_DESC" />
      </Description>

      <FormGroupText
        label="FILTER_GROUP_BEHAVIOR"
        noDesc={true}
        field={"occurrenceGroupBehavior"}
        term={"match"}
        filterId={"occurrenceGroupBehavior"}
        filterKey={"Group Behavior"}
        store={store}
      />
      <FormGroupText
        label="FILTER_GROUP_COMPOSITION"
        noDesc={true}
        field={"occurrenceGroupComposition"}
        term={"match"}
        filterId={"occurrenceGroupComposition"}
        filterKey={"Group Composition"}
        store={store}
      />

      <div className="d-flex flex-row justify-content-between mt-2">
        <Form.Label>
          <FormattedMessage id="FILTER_SOCIAL_UNIT" />
        </Form.Label>

        <Form.Check
          type="checkbox"
          id="custom-checkbox_unit"
          label={<FormattedMessage id="USE_AND_OPERATOR" />}
          checked={isUnitChecked}
          onChange={() => {
            setIsUnitChecked(!isUnitChecked);
          }}
        />
      </div>

      {isUnitChecked ? (
        <AndSelector
          isMulti={true}
          noLabel={true}
          noDesc={true}
          label="FILTER_SOCIAL_UNIT"
          options={socialUnitOptions}
          field="individualSocialUnits"
          term="terms"
          filterId={"individualSocialUnits"}
          filterKey={"Social Group Unit"}
          store={store}
        />
      ) : (
        <FormGroupMultiSelect
          isMulti={true}
          noLabel={true}
          noDesc={true}
          label="FILTER_SOCIAL_UNIT"
          options={socialUnitOptions}
          field="individualSocialUnits"
          term="terms"
          filterId={"individualSocialUnits"}
          filterKey={"Social Group Unit"}
          store={store}
        />
      )}

      <div className="d-flex flex-row justify-content-between mt-2">
        <Form.Label>
          <FormattedMessage id="FILTER_RELATIONSHIP_ROLE" />
        </Form.Label>

        <Form.Check
          type="checkbox"
          id="custom-checkbox_role"
          label={<FormattedMessage id="USE_AND_OPERATOR" />}
          checked={isRoleChecked}
          onChange={() => {
            setIsRoleChecked(!isRoleChecked);
          }}
        />
      </div>

      {isRoleChecked ? (
        <AndSelector
          isMulti={true}
          noDesc={true}
          noLabel={true}
          label="FILTER_RELATIONSHIP_ROLE"
          options={socialRoleOptions}
          field="individualRelationshipRoles"
          term={"terms"}
          filterId={"individualRelationshipRoles"}
          filterKey={"Relationship Role"}
          store={store}
        />
      ) : (
        <FormGroupMultiSelect
          isMulti={true}
          noDesc={true}
          noLabel={true}
          label="FILTER_RELATIONSHIP_ROLE"
          options={socialRoleOptions}
          field="individualRelationshipRoles"
          term={"terms"}
          filterId={"individualRelationshipRoles"}
          filterKey={"Relationship Role"}
          store={store}
        />
      )}
    </div>
  );
});

export default SocialFilter;
