import { FormattedMessage } from 'react-intl';
import Map from "../Map";
import { FormGroup, FormLabel, FormControl } from 'react-bootstrap';
import { useEffect, useState } from 'react';
import Description from '../Form/Description';
import FormGroupMultiSelect from '../Form/FormGroupMultiSelect';
import _ from 'lodash';

export default function LocationFilterMap({
    onChange,
    data,
}) {    
    const [bounds, setBounds] = useState(null);

    useEffect(() => {
        if (bounds) {
            onChange({
                filterId: "locationId",
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

    return (
        <div>
            <h3><FormattedMessage id="FILTER_LOCATION_MAP" /></h3>
            <Description>
                <FormattedMessage id="FILTER_LOCATION_MAP_DESC" />
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
                            <FormLabel><FormattedMessage id={Object.keys(item)[0].replace(/_/g, " ")} /></FormLabel>
                            <FormControl
                                type="text"
                                placeholder={bounds ? bounds[Object.values(item)[0]] : ""}
                                value={bounds ? bounds[Object.values(item)[0]] : ""}
                            />
                        </FormGroup>
                    );
                }) 
                }

            </div>
            <Map
                bounds={bounds}
                setBounds={setBounds}
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
            />

        </div>
    );
}