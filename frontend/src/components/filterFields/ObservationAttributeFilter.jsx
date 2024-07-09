import Description from "../Description";
import { FormattedMessage } from "react-intl";
import { Form, FormGroup, FormLabel, FormControl, FormSelect } from "react-bootstrap";
import BrutalismButton from "../BrutalismButton";
import MultiSelect from "../MultiSelect";

export default function ObservationAttributeFilter() {
    
    const sexOptions = [
        {
            id: 1,
            value: "female",
            name: "Female"
        },
        {
            id: 2,
            value: "male",
            name: "Male"
        },
        {
            id: 3,
            value: "unknown",
            name: "Unknown"
        },
    ];
    const lifeStatusOptions = [];
    const genusAndSpeciesOptions = [];


    const options = [
        { value: '1', label: 'Behaviour 1'},
        { value: '2', label: 'Behaviour 2'},
        { value: '3', label: 'Behaviour 3'}
    ];


    return (
        <div>
            <h3><FormattedMessage id="FILTER_OBSERVATION_ATTRIBUTE" /></h3>
            <Description>
                <FormattedMessage id="FILTER_OBSERVATION_ATTRIBUTE_DESC" />
            </Description>
            <FormGroup>
                <FormLabel><FormattedMessage id="FILTER_SEX" /></FormLabel>
                <Description>
                    <FormattedMessage id="FILTER_SEX_DESC" />
                </Description>

                <FormSelect>
                    {sexOptions.map((location) => (
                        <option key={location.id} value={location.id}>
                            {location.name}
                        </option>
                    ))}
                </FormSelect>

            </FormGroup>

            <FormGroup>
                <FormLabel><FormattedMessage id="FILTER_LIFE_STATUS" /></FormLabel>
                <Description>
                    <FormattedMessage id="FILTER_LIFE_STATUS_DESC" />
                </Description>
                <FormSelect>
                    {lifeStatusOptions.map((location) => (
                        <option key={location.id} value={location.id}>
                            {location.name}
                        </option>
                    ))}
                </FormSelect>

            </FormGroup>

            <FormGroup>
                <FormLabel><FormattedMessage id="FILTER_GENUS_AND_SPECIES" /></FormLabel>
                <Description>
                    <FormattedMessage id="FILTER_GENUS_AND_SPECIES_DESC" />
                </Description>
                <FormSelect>
                    {genusAndSpeciesOptions.map((location) => (
                        <option key={location.id} value={location.id}>
                            {location.name}
                        </option>
                    ))}
                </FormSelect>

            </FormGroup>

            <FormGroup>
                <FormLabel><FormattedMessage id="FILTER_OBSERVATION_SEARCH" /></FormLabel>
                <Description>
                    <FormattedMessage id="FILTER_OBSERVATION_SEARCH_DESC" />
                </Description>
                <div style={{
                    display: 'flex',
                    flexDirection: 'row',
                    justifyContent: 'space-between',
                    gap: '10px',
                    marginBottom: '10px',
                }}>
                    <div className="w-50">
                        <FormLabel><FormattedMessage id="FILTER_OBSERVATION_NAME" /></FormLabel>
                        <FormControl type="text" placeholder="Type Here" />
                    </div>
                    <div className="w-50">
                        <FormLabel><FormattedMessage id="FILTER_OBSERVATION_VALUE" /></FormLabel>
                        <FormControl type="text" placeholder="Type Here" />
                    </div>

                </div>
                <div style={{
                    display: 'flex',
                    flexDirection: 'row',
                    justifyContent: 'space-between',
                    gap: '10px',
                }}>
                    <div className="w-50">
                        <FormLabel><FormattedMessage id="FILTER_OBSERVATION_NAME" /></FormLabel>
                        <FormControl type="text" placeholder="Type Here" />
                    </div>
                    <div className="w-50">
                        <FormLabel><FormattedMessage id="FILTER_OBSERVATION_VALUE" /></FormLabel>
                        <FormControl type="text" placeholder="Type Here" />
                    </div>

                </div>
            </FormGroup>

            <BrutalismButton style={{
                marginTop: '10px'
            }}
                borderColor="#fff"
                color="white"
                backgroundColor="transparent"
            >
                <i className="bi bi-plus-square" style={{ marginRight: "10px" }}></i>
                <FormattedMessage id="FILTER_ADD_OBSERVATION_SEARCH" />
            </BrutalismButton>

            <FormGroup>
                <FormLabel><FormattedMessage id="FILTER_OBSERVATION_COMMENTS_INCLUDE" /></FormLabel>
                <Description>
                    <FormattedMessage id="FILTER_OBSERVATION_COMMENTS_INCLUDE_DESC" />
                </Description>
                <FormControl type="text" placeholder="Type Here" />

            </FormGroup>

            <FormGroup>
                <FormLabel><FormattedMessage id="FILTER_DEMONSTRATED_BEHAVIOUR" /></FormLabel>
                <Description>
                    <FormattedMessage id="FILTER_DEMONSTRATED_BEHAVIOUR_DESC" />
                </Description>

                <MultiSelect
                    options={options}
                />

            </FormGroup>

        </div>
    );
}