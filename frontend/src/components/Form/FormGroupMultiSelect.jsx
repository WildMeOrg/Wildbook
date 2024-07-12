
import MultiSelect from '../MultiSelect';
import Description from './Description';
import { FormattedMessage } from 'react-intl';
import { FormGroup, FormLabel } from 'react-bootstrap';

export default function FormGroupMultiSelect({ isMulti = false, label = "", options, onChange, term, field }) {
    console.log("...........",isMulti, label, options, onChange, term, field);
    return (
        <FormGroup>
            <FormLabel>{label}</FormLabel>
            <Description>
                <FormattedMessage id={`${label}_DESC`} />
            </Description>
            <MultiSelect
                options={options}
                onChange={onChange}
                isMulti={isMulti}
                term={term}
                field={field}
            ></MultiSelect>
        </FormGroup>
    );
}