import React from 'react';

function Chip({ text, children }) {
    function renderFilter(filter) {
        const entries = [];
        const { clause, filterId, query } = filter;
        if(clause === "nested") {
            entries.push(`Nested filter: ${filterId}`);
        }
        if (query?.geo_bounding_box) {
            const { top_left, bottom_right } = query.geo_bounding_box['locationGeoPoint'];
            entries.push(`Location within bounding box: top_left: ${top_left.lat}, ${top_left.lon}, bottom_right: ${bottom_right.lat}, ${bottom_right.lon}`);
        }        


        if (query?.range) {
            Object.entries(query.range).forEach(([key, range]) => {
                const parts = [];
                if (range.gte) parts.push(`from "${range.gte}"`);
                if (range.lte) parts.push(`to "${range.lte}"`);
                entries.push(`${key} ${parts.join(' ')}`);
            });
        }
        if (query?.match) {
            Object.entries(query.match).forEach(([key, value]) => {
                entries.push(`"${key}" matches "${value}"`);
            });
        }
        if (query?.exists) {
            Object.entries(query.exists).forEach(([key, value]) => {
                entries.push(`"${value}" exists`);
            });
        }
        if (query?.term) {
            Object.entries(query.term).forEach(([key, value]) => {
                entries.push(`${key} is "${value}"`);
            });
        }
        if (query?.terms) {
            Object.entries(query.terms).forEach(([key, values]) => {
                if (Array.isArray(values)) {
                    entries.push(`${key} is any of [${values.join(', ')}]`);
                } else {
                    entries.push(`${key} is "${values}"`);
                }
            });
        }

        if(query?.bool) {
            if(query.bool.must) {
                query.bool.must.forEach((item) => {
                    Object.entries(item).forEach(([key, value]) => {
                        entries.push(`${key} is "${value}"`);
                    });
                });
            }
        }

        return entries.length > 0 ? `${entries.join(', ')}` : `No filters set`;
    }

    return (
        <div
            style={{
                backgroundColor: '#e5f6ff',
                color: 'black',
                borderRadius: '15px',
                padding: '10px 20px',
                boxShadow: '0px 2px 5px rgba(0, 0, 0, 0.2)',
                marginTop: '10px',
            }}
        >
            {renderFilter(children)}
        </div>
    );
}

export default Chip;
