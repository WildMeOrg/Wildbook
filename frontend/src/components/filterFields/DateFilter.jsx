import { FormattedMessage } from "react-intl";
import Description from "../Form/Description";
import { FormGroup, FormLabel, FormControl } from "react-bootstrap";

export default function DateFilter() {
    return (
        <div>
            <h3><FormattedMessage id="DATE" /></h3>
            <Description>
                <FormattedMessage id="DATE_DESC" />
            </Description>
            <FormLabel><FormattedMessage id="SIGHTING_DATES" /></FormLabel>

            <div className="d-flex flex-row w-100">
                <FormGroup className="w-50" style={{
                    marginRight: '10px',
                
                }}>
                    <p>
                        <FormattedMessage id="FROM" defaultMessage="From" />
                    </p>
                    <FormControl type="date" />
                </FormGroup>
                <FormGroup className="w-50">
                    <p>
                        <FormattedMessage id="TO" defaultMessage="To" />
                    </p>
                    <FormControl type="date" />
                </FormGroup>
            </div>
            <FormLabel><FormattedMessage id="ENCOUNTER_SUBMISSION_DATES" /></FormLabel>

            <div className="d-flex flex-row w-100">
                <FormGroup className="w-50" style={{
                    marginRight: '10px',
                
                }}>
                    <p>
                        <FormattedMessage id="FROM" defaultMessage="From" />
                    </p>
                    <FormControl type="date" />
                </FormGroup>
                <FormGroup className="w-50">
                    <p>
                        <FormattedMessage id="TO" defaultMessage="To" />
                    </p>
                    <FormControl type="date" />
                </FormGroup>
            </div>


        </div>
    );
}