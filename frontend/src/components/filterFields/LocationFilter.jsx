import React from 'react';
import LocationFilterMap from './LocationFilterMap';
import LocationFilterText from './LocationFilterText';

export default function LocationFilter({
    onChange,
    onClearFilter,
    data,
}) {
    return (
        <div>
            <LocationFilterMap
                onChange={onChange}
                onClearFilter={onClearFilter}
                data={data}
            />
            <LocationFilterText
                test="test"
                onChange={onChange}
                onClearFilter={onClearFilter}
            />
        </div>
    )
}