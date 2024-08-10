import React, { Fragment, useEffect } from 'react';
import { FormattedMessage } from 'react-intl';
import Form from 'react-bootstrap/Form';
import FormGroupMultiSelect from '../Form/FormGroupMultiSelect';
import MultiSelect from '../MultiSelect';
import FormGroup from 'react-bootstrap/FormGroup';
import Description from '../Form/Description';
import { filter } from 'lodash-es';
import FormGroupText from '../Form/FormGroupText';
import Select from 'react-select';
import { useContext } from 'react';
import FilterContext from '../../FilterContextProvider';
import AndSelector from '../AndSelector';
import LabelledKeywordFilter from '../Form/LabelledKeywordFilter';

const colourStyles = {
    option: (styles) => ({
        ...styles,
        color: 'black',
    }),
    control: (styles) => ({ ...styles, backgroundColor: 'white' }),
    singleValue: (styles) => ({ ...styles, color: 'black' }),
    menuPortal: base => ({ ...base, zIndex: 1050 }),
    // menu: base => ({ ...base, maxHeight: '200px' }),
    control: base => ({ ...base, zIndex: 1 }),
};

export default function ImageLabelFilter({
    data,
    onChange,
}) {
    const keywordsOptions = data?.keyword?.map(item => {
        return {
            value: item,
            label: item
        }
    }) || [];

    const viewPointOptions = data?.annotationViewpoint?.map(item => {
        return {
            value: item,
            label: item
        }
    }) || [];

    const iaClassOptions = data?.iaClass?.map(item => {
        return {
            value: item,
            label: item
        }
    }) || [];

    const label = <FormattedMessage id="FILTER_HAS_AT_LEAST_ONE_ASSOCIATED_PHOTO_OR_VIDEO" />
    const [isChecked_photo, setIsChecked_photo] = React.useState(false);
    const [isChecked_keyword, setIsChecked_keyword] = React.useState(false);

    useEffect(() => {
        if (isChecked_photo) {
            onChange({
                filterId: "numberMediaAssets",
                clause: "filter",
                filterKey: "Number Media Assets",
                query: {
                    "range": {
                        "numberMediaAssets": {
                            "gte": 1
                        }
                    }
                },
            })
        }
        else {
            onChange(null, "numberMediaAssets");
        }

    }, [isChecked_photo]);

    return (

        <div>
            <h3><FormattedMessage id="FILTER_IMAGE_LABEL" /></h3>
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
                    }
                    }
                />
            </div>

            {
                isChecked_keyword ? <AndSelector
                    isMulti={true}
                    noLabel={true}
                    label="FILTER_KEYWORDS"
                    onChange={onChange}
                    options={keywordsOptions}
                    field="mediaAssetKeywords"
                    term="terms"
                    filterId={"mediaAssetKeywords"}
                    filterKey={"Media Asset Keywords"}
                /> : <FormGroupMultiSelect
                    isMulti={true}
                    noLabel={true}
                    label="FILTER_KEYWORDS"
                    options={keywordsOptions}
                    onChange={onChange}
                    field="mediaAssetKeywords"
                    term="terms"
                    filterKey="Media Asset Keywords"
                />
            }

            <LabelledKeywordFilter
                data={data}
                onChange={onChange}
            />            

            <FormGroupMultiSelect
                isMulti={true}
                label="FILTER_VIEWPOINT"
                options={viewPointOptions}
                filterId="viewpoint"
                term="terms"
                field={"viewpoint"}
                onChange={onChange}
                filterKey={"Viewpoint"}
            />

            <FormGroupMultiSelect
                isMulti={true}
                label="FILTER_IA_CLASS"
                options={iaClassOptions}
                filterId="iaClass"
                field={"iaClass"}
                term="terms"
                onChange={onChange}
                filterKey={"IA Class"}
            />
        </div>
    );
}