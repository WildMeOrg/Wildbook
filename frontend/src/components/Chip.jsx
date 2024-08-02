import React from 'react';
import useGetSiteSettings from '../models/useGetSiteSettings';

function Chip({ children }) {

    const {data} = useGetSiteSettings();

    function renderFilter(filter) {     
        
        function getLabelById(options, id) {
            const option = options.find(opt => opt.value === id);
            let label = "";
            if(!option.label || option.label.startsWith("Anonymous")) {
                label = "Anonymous User";
            }else {
                label = option.label;
            }
            return label;
        }

        const organizationOptions =  Object.entries(data?.organizations||{})?.map((item) => {
            return {
              value: item[0],
              label: item[1]
            };
          }
          ) || [];
        
          const projectOptions = Object.entries(data?.projectsForUser||{})?.map((item) => {
            return {
              value: item[0],
              label: item[1]
            };
          }
          ) || [];
        
          const assignedUserOptions = data?.users?.map((item) => {
            return {
              value: item.id,
              label: item.username
            };
          }
          ) || [];  
        const entries = [];
        const { clause, filterId, query } = filter;
        if (clause === "nested") {
            entries.push(`Nested filter: ${filterId}`);
        }
        if (query?.geo_bounding_box) {
            const { top_left, bottom_right } = query.geo_bounding_box['locationGeoPoint'];
            entries.push(`Location within bounding box: top_left: ${top_left.lat}, ${top_left.lon}, bottom_right: ${bottom_right.lat}, ${bottom_right.lon}`);
        }

        if (query?.range) {
            Object.entries(query.range).forEach(([key, range]) => {
                const parts = [];
                if (range.gte || range.gte === 0) parts.push(`from "${range.gte}"`);
                if (range.lte || range.lte === 0) parts.push(`to "${range.lte}"`);
                entries.push(`${key} ${parts.join(' ')}`);
            });
        } else if (query?.match) {
            Object.entries(query.match).forEach(([key, value]) => {
                entries.push(`"${key}" matches "${value}"`);
            });
        } else if (query?.exists) {
            Object.entries(query.exists).forEach(([key, value]) => {
                entries.push(`"${value}" filter is set`);
            });
        } else if (query?.term) {
            Object.entries(query.term).forEach(([key, value]) => {
                entries.push(`${key} is "${value}"`);
            });
        } else if (query?.terms) {
            const labels = Object.values(query.terms[filterId]).map(val => {
                if (filterId === "organizations") {
                    return getLabelById(organizationOptions, val);
                } else if (filterId === "projectsForUser") {
                    return getLabelById(projectOptions, val);
                } else if (filterId === "assignedUsername") {
                    return getLabelById(assignedUserOptions, val);
                } else {
                    return val;
                }
            });
            const uniqueLabels = [...new Set(labels)];
            entries.push(`${filterId} is any of [${uniqueLabels.join(', ')}]`)
        } else if (query?.biologicalMeasurements) {
            Object.entries(query).forEach(([key, value]) => {
                entries.push(`${key} filter is set`);
            });
        }

        if (query?.bool) {
            if (query.bool.must) {
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
