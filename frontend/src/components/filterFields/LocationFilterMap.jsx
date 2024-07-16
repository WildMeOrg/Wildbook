import { FormattedMessage } from 'react-intl';
import Map from "../Map";
import { FormGroup, FormLabel, FormControl } from 'react-bootstrap';
import { useState } from 'react';
import Description from '../Form/Description';
import FormGroupMultiSelect from '../Form/FormGroupMultiSelect';
export default function LocationFilterMap({
    onChange,
    data,
}) {
    const [bounds, setBounds] = useState(null);
    const locationIDOptions = data?.location;
    return (
        <div>
            <h3><FormattedMessage id="FILTER_LOCATION_MAP" /></h3>
            <Description>
                <FormattedMessage id="FILTER_LOCATION_MAP_DESC" />
            </Description>

            <FormGroupMultiSelect
                isMulti={true}
                label="FILTER_LOCATION_ID"
                options={locationIDOptions}
                onChange={onChange}
                term="terms"
                field="id"
            />
           <FormLabel><FormattedMessage id="FILTER_GPS_COORDINATES" /></FormLabel>
            <div style={{
                margin: '12px',
                display: 'flex',
                flexDirection: 'row',
            }}>
                {["Northeast_Latitude",
                    "Northwest_Latitude",
                    "Southeast_Latitude",
                    "Southwest_Latitude"].map((item, index) => {
                        return (
                            <FormGroup key={index} style={{
                                marginRight: '10px',
                            }}>
                                <FormLabel><FormattedMessage id={item.replace(/_/g, " ")} /></FormLabel>
                                <FormControl
                                    type="text"
                                    placeholder={bounds?.south}
                                />
                            </FormGroup>
                        );
                    })
                }

            </div>
            <Map
                bounds={bounds}
                setBounds={setBounds}
            />
            <FormattedMessage id="FILTER_GPS_COORDINATES" />

        </div>
    );
}