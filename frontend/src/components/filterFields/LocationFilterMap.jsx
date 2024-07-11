import { FormattedMessage } from 'react-intl';
import Map from "../Map";
import { FormGroup, FormLabel, FormControl } from 'react-bootstrap';
import { useState } from 'react';
import Description from '../Form/Description';


export default function LocationFilterMap() {

    const [bounds, setBounds] = useState(null);


    return (
        <div>
            <h3><FormattedMessage id="FILTER_LOCATION_MAP" /></h3>
            <Description>
                <FormattedMessage id="FILTER_LOCATION_MAP_DESC" />
            </Description>

            <div style={{
                margin: '12px',
                display: 'flex',
                flexDirection: 'row',
            }}>
                {["Northeast_Latitude",
                    "Northeast_Latitude",
                    "Northeast_Latitude",
                    "Northeast_Latitude"].map((item, index) => {
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