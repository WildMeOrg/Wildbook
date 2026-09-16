import React from "react";
import { FormattedMessage } from "react-intl";
import Form from "react-bootstrap/Form";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import Description from "../Form/Description";
import AndSelector from "../AndSelector";
import LabelledKeywordFilter from "../Form/LabelledKeywordFilter";
import { observer } from "mobx-react-lite";
import ContainerWithSpinner from "../ContainerWithSpinner";

const ImageLabelFilter = observer(({ data, store }) => {
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

  const [isChecked_keyword, setIsChecked_keyword] = React.useState(false);

  const keywordsFormValue = store.formFilters?.find((filter) =>
    filter.filterId.includes("mediaAssetKeywords"),
  )?.query?.term;
  const keywordsANDChecked =
    keywordsFormValue && "mediaAssetKeywords" in keywordsFormValue
      ? true
      : isChecked_keyword;
  const formValues = store.formFilters.filter((item) =>
    item.filterId.includes("mediaAssetKeywords"),
  );
  const value = formValues?.map((item) => item.query?.term?.mediaAssetKeywords);

  return (
    <div>
      <h4>
        <FormattedMessage id="FILTER_IMAGE_LABEL" />
      </h4>
      <Description>
        <FormattedMessage id="FILTER_IMAGE_LABEL_DESC" />
      </Description>
      <Form>
        <Form.Check
          type="checkbox"
          id="custom-checkbox"
          label={label}
          checked={
            store.formFilters?.find(
              (filter) => filter.filterId === "numberMediaAssets",
            )?.query?.range?.numberMediaAssets?.gte === 1
          }
          onChange={(e) => {
            if (e.target.checked) {
              console.log(1);
              store.addFilter(
                "numberMediaAssets",
                "filter",
                {
                  range: {
                    numberMediaAssets: {
                      gte: 1,
                    },
                  },
                },
                "Number Media Assets",
              );
            } else {
              store.removeFilter("numberMediaAssets");
            }
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
          checked={keywordsANDChecked}
          onChange={(e) => {
            console.log(e.target.checked);
            if (!e.target.checked) {
              store.removeFilterByFilterKey("Media Asset Keywords");
            }
            setIsChecked_keyword(!isChecked_keyword);
          }}
        />
      </div>

      {keywordsANDChecked ? (
        <AndSelector
          isMulti={true}
          noLabel={true}
          label="FILTER_KEYWORDS"
          options={keywordsOptions}
          field="mediaAssetKeywords"
          term="terms"
          filterId={"mediaAssetKeywords"}
          filterKey={"Media Asset Keywords"}
          store={store}
          value={value}
        />
      ) : (
        <ContainerWithSpinner loading={store.siteSettingsLoading}>
          <FormGroupMultiSelect
            isMulti={true}
            noLabel={true}
            label="FILTER_KEYWORDS"
            options={keywordsOptions}
            field="mediaAssetKeywords"
            term="terms"
            filterKey="Media Asset Keywords"
            store={store}
            loading={store.siteSettingsLoading}
          />
        </ContainerWithSpinner>
      )}

      <LabelledKeywordFilter data={data} store={store} />
      <ContainerWithSpinner loading={store.siteSettingsLoading}>
        <FormGroupMultiSelect
          isMulti={true}
          label="FILTER_VIEWPOINT"
          noDesc={true}
          options={viewPointOptions}
          filterId="annotationViewpoints"
          term="terms"
          field={"annotationViewpoints"}
          filterKey={"View Point"}
          store={store}
          loading={store.siteSettingsLoading}
        />
      </ContainerWithSpinner>
      <ContainerWithSpinner loading={store.siteSettingsLoading}>
        <FormGroupMultiSelect
          isMulti={true}
          label="FILTER_IA_CLASS"
          noDesc={true}
          options={iaClassOptions}
          filterId="annotationIAClasses"
          field={"annotationIAClasses"}
          term="terms"
          filterKey={"IA Class"}
          store={store}
          loading={store.siteSettingsLoading}
        />
      </ContainerWithSpinner>
    </div>
  );
});

export default ImageLabelFilter;
