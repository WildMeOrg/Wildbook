
import { FormGroup, FormLabel, FormControl } from 'react-bootstrap';
import Description from './Description';
import { FormattedMessage } from "react-intl";

export default function FormGroupText({
    noLabel = false,
    noDesc = false,
    label = ""
}) {
    return (
        <FormGroup>
            {!noLabel && <FormLabel><FormattedMessage id={label} defaultMessage="" /></FormLabel>}
            {!noDesc && <Description><FormattedMessage id={`${label}_DESC`} /></Description>}
            <FormControl type="text" placeholder="Type Here" />
        </FormGroup>
    );
}