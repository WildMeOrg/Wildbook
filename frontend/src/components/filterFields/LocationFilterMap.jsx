import { FormattedMessage } from 'react-intl';
import Map from "../Map";
import { FormGroup, FormLabel, FormControl } from 'react-bootstrap';
import { useEffect, useState } from 'react';
import Description from '../Form/Description';
import FormGroupMultiSelect from '../Form/FormGroupMultiSelect';
import _ from 'lodash';
import { useIntl } from 'react-intl';

export default function LocationFilterMap({
    onChange,
    data,
}) {    
    const [bounds, setBounds] = useState(null);
    const intl = useIntl();

    useEffect(() => {
        if (bounds) {
            onChange({
                filterId: "locationMap",
                clause: "filter",
                query: {
                    "geo_bounding_box": {
                        "locationGeoPoint": {
                            "top_left": {
                                "lat": bounds.north,
                                "lon": bounds.west
                            },
                            "bottom_right": {
                                "lat": bounds.south,
                                "lon": bounds.east
                            }
                        }
                    }
                }
            });
        }else {
            onChange(null, "locationMap");
        }
    }, [bounds]);

    function flattenLocationData(data) {
        if (!data) {
            return [];
        }
        const result = [];

        function traverse(locations, depth) {
            locations.forEach(location => {
                const newEntry = {
                    name: location.name,
                    id: location.id,
                    depth: depth
                };
                result.push(newEntry);

                if (location.locationID && location.locationID.length > 0) {
                    traverse(location.locationID, depth + 1);
                }
            });
        }
        traverse(data.locationID, 0);
        return result;
    }

    const flattenedData = flattenLocationData(data?.locationData);

    const locationIDOptions = flattenedData.map(location => {
        return {
            value: location.id,
            label: _.repeat("-", location.depth) + " " + location.name
        }
    }) || [];
    const [tempBounds, setTempBounds] = useState(bounds);

    return (
        <div>
            <h3><FormattedMessage id="FILTER_LOCATION" /></h3>
            <Description>
                <FormattedMessage id="FILTER_LOCATION_DESC" />
            </Description>
            <FormLabel><FormattedMessage id="FILTER_GPS_COORDINATES" /></FormLabel>
            <div style={{
                margin: '12px',
                display: 'flex',
                flexDirection: 'row',
            }}>
                {[{ "Northeast_Latitude": "north" },
                { "Northeast_Longitude": "east" },
                { "Southwest_Latitude": "south" },
                { "Southwest_Longitude": "west" }].map((item, index) => {

                    return (
                        <FormGroup key={index} style={{
                            marginRight: '10px',
                        }}>
                            <FormLabel><FormattedMessage id={Object.keys(item)[0]} /></FormLabel>
                            <FormControl
                                type="number"
                                placeholder={bounds ? bounds[Object.values(item)[0]] : intl.formatMessage({ id: "TYPE_NUMBER" })
                            }
                                value={bounds ? bounds[Object.values(item)[0]] : tempBounds? tempBounds[Object.values(item)[0]] : ""}
                                onChange={(e) => {
                                    const newTempBounds = {
                                        ...tempBounds,
                                        [Object.values(item)[0]]: e.target.value
                                    };
                                    setTempBounds(newTempBounds);
                                    
                                    // Check if all fields have values
                                    const allFieldsFilled = Object.values(newTempBounds).length === 4 && Object.values(newTempBounds).every(value => value !== undefined && value !== "");
                                    if (allFieldsFilled) {
                                        setBounds(newTempBounds);  
                                    }                                    
                                }}
                            />
                        </FormGroup>
                    );
                }) 
                }

            </div>
            <Map
                bounds={bounds}
                setBounds={setBounds}
                setTempBounds={setTempBounds}
                center={{ lat: -1.286389, lng: 36.817223 }} 
                zoom={5}
                onChange={onChange}
            />
            <FormGroupMultiSelect
                isMulti={true}
                label="FILTER_LOCATION_ID"
                options={locationIDOptions}
                onChange={onChange}
                term="terms"
                field="locationId"
                filterKey="Location ID"
            />

        </div>
    );
}