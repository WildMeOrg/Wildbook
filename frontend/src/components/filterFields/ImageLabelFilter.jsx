import React, { useEffect } from "react";
import { FormattedMessage } from "react-intl";
import Form from "react-bootstrap/Form";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import Description from "../Form/Description";
import AndSelector from "../AndSelector";
import LabelledKeywordFilter from "../Form/LabelledKeywordFilter";

export default function ImageLabelFilter({ data, onChange }) {
  const keywordsOptions =
    data?.keyword?.map((item) => {
      return {
        value: item,
        label: item,
      };
    }) || [];

  const viewPointOptions =
    data?.annotationViewpoint?.map((item) => {
      return {
        value: item,
        label: item,
      };
    }) || [];

  const iaClassOptions =
    data?.iaClass?.map((item) => {
      return {
        value: item,
        label: item,
      };
    }) || [];

  const label = (
    <FormattedMessage id="FILTER_HAS_AT_LEAST_ONE_ASSOCIATED_PHOTO_OR_VIDEO" />
  );
  const [isChecked_photo, setIsChecked_photo] = React.useState(false);
  const [isChecked_keyword, setIsChecked_keyword] = React.useState(false);

  useEffect(() => {
    if (isChecked_photo) {
      onChange({
        filterId: "numberMediaAssets",
        clause: "filter",
        filterKey: "Number Media Assets",
        query: {
          range: {
            numberMediaAssets: {
              gte: 1,
            },
          },
        },
      });
    } else {
      onChange(null, "numberMediaAssets");
    }
  }, [isChecked_photo]);

  return (
    <div>
      <h3>
        <FormattedMessage id="FILTER_IMAGE_LABEL" />
      </h3>
      <Description>
        <FormattedMessage id="FILTER_IMAGE_LABEL_DESC" />
      </Description>
      <Form>
        <Form.Check
          type="checkbox"
          id="custom-checkbox"
          label={label}
          checked={isChecked_photo}
          onChange={() => {
            setIsChecked_photo(!isChecked_photo);
          }}
        />
      </Form>

      <div className="d-flex flex-row justify-content-between mt-3">
        <Form.Label>
          <FormattedMessage id="FILTER_KEYWORDS" />
        </Form.Label>

        <Form.Check
          type="checkbox"
          id="custom-checkbox_keyword"
          label={<FormattedMessage id="USE_AND_OPERATOR" />}
          checked={isChecked_keyword}
          onChange={() => {
            setIsChecked_keyword(!isChecked_keyword);
          }}
        />
      </div>

      {isChecked_keyword ? (
        <AndSelector
          isMulti={true}
          noLabel={true}
          label="FILTER_KEYWORDS"
          onChange={onChange}
          options={keywordsOptions}
          field="mediaAssetKeywords"
          term="terms"
          filterId={"mediaAssetKeywords"}
          filterKey={"Media Asset Keywords"}
        />
      ) : (
        <FormGroupMultiSelect
          isMulti={true}
          noLabel={true}
          label="FILTER_KEYWORDS"
          options={keywordsOptions}
          onChange={onChange}
          field="mediaAssetKeywords"
          term="terms"
          filterKey="Media Asset Keywords"
        />
      )}

      <LabelledKeywordFilter data={data} onChange={onChange} />

      <FormGroupMultiSelect
        isMulti={true}
        label="FILTER_VIEWPOINT"
        noDesc={true}
        options={viewPointOptions}
        filterId="annotationViewpoints"
        term="terms"
        field={"annotationViewpoints"}
        onChange={onChange}
        filterKey={"View Point"}
      />

      <FormGroupMultiSelect
        isMulti={true}
        label="FILTER_IA_CLASS"
        noDesc={true}
        options={iaClassOptions}
        filterId="annotationIAClasses"
        field={"annotationIAClasses"}
        term="terms"
        onChange={onChange}
        filterKey={"IA Class"}
      />
    </div>
  );
}
