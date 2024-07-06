import {FormattedMessage} from 'react-intl';
import Map from "../Map";


export default function LocationFilterMap() {

    return (
        <div>
        <h1>Location Map</h1>
        <FormattedMessage id="LOCATION_MAP_DESC"/>
        <Map />
        </div>
    );
    }