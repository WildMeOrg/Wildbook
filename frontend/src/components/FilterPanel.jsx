// import React from 'react';
// import 'bootstrap/dist/css/bootstrap.min.css';
// import AreaInput from '../../components/inputs/AreaInput';


// const FilterPanel = () => {
//   return (
//     <div className="container-fluid">
//       <div className="row">
//         <div className="col-md-4">
//           <div className="card mb-4">
//             <div className="card-body">
//               <h2 className="card-title">Location Map</h2>
//               <label>Choose in map</label>
//               <input type="text" className="form-control mb-2" placeholder="Northeast Latitude" />
//               <input type="text" className="form-control mb-2" placeholder="Longitude" />
//               <input type="text" className="form-control mb-2" placeholder="Southwest Latitude" />
//               <input type="text" className="form-control mb-2" placeholder="Longitude" />
//               <div className="map-placeholder d-flex align-items-center justify-content-center" 
//               style={{ 
//                 height: '400px', 
//                 borderRadius: '5px', 
//                 border: '1px solid #ccc', 
//                 boxSizing: "border-box",
//                 overflow: "hidden"  }}>
//                 <AreaInput />
//               </div>
//             </div>
//           </div>

//           <div className="card mb-4">
//             <div className="card-body">
//               <h2 className="card-title">Location</h2>
//               <label>Location name contains</label>
//               <input type="text" className="form-control mb-2" placeholder="example" />
//               <label>Location ID</label>
//               <input type="text" className="form-control" placeholder="example" />
//             </div>
//           </div>
//         </div>

//         <div className="col-md-4">
//           <div className="card mb-4">
//             <div className="card-body">
//               <h2 className="card-title">Image Label Filters</h2>
//               <label>
//                 <input type="checkbox" /> Has at least one associated photo or video
//               </label>
//               <label>Keyword Filters</label>
//               <input type="text" className="form-control mb-2" placeholder="example" />
//               <label>
//                 <input type="checkbox" /> Use OR operator rather than AND operator for keyword matching.
//               </label>
//               <label>Viewpoint</label>
//               <select className="form-control mb-2">
//                 <option>Select viewpoint</option>
//               </select>
//               <label>Class</label>
//               <select className="form-control">
//                 <option>Select class</option>
//               </select>
//             </div>
//           </div>
//         </div>

//         <div className="col-md-4">
//           <div className="card mb-4">
//             <div className="card-body">
//               <h2 className="card-title">Date</h2>
//               <label>Sighting Dates</label>
//               <input type="text" className="form-control mb-2" placeholder="From" />
//               <input type="text" className="form-control mb-2" placeholder="To" />
//               <label>Encounter Submission Date</label>
//               <input type="text" className="form-control mb-2" placeholder="From" />
//               <input type="text" className="form-control" placeholder="To" />
//             </div>
//           </div>

//           <div className="card mb-4">
//             <div className="card-body">
//               <h2 className="card-title">Biological Sample & Analysis</h2>
//               <label>
//                 <input type="checkbox" /> Has Biological Sample
//               </label>
//               <label>Biological Sample ID contains</label>
//               <input type="text" className="form-control mb-2" placeholder="Select country" />
//               <label>Biological Chemical Measurements</label>
//               <input type="text" className="form-control mb-2" placeholder="13C is" />
//               <input type="text" className="form-control mb-2" placeholder="15N is" />
//               <input type="text" className="form-control" placeholder="34S is" />
//             </div>
//           </div>
//         </div>
//       </div>
//     </div>
//   );
// };

// export default FilterPanel;



import React, { Fragment, useState } from 'react';

import Text from './Text';

function setFilter(newFilter, formFilters, setFormFilters) {
  const matchingFilterIndex = formFilters.findIndex(
    f => f.filterId === newFilter.filterId,
  );
  if (matchingFilterIndex === -1) {
    setFormFilters([...formFilters, newFilter]);
  } else {
    const newFormFilters = [...formFilters];
    newFormFilters[matchingFilterIndex] = newFilter;
    setFormFilters(newFormFilters);
  }
}

export default function FilterPanel({
  schemas,
  formFilters=[],
  setFormFilters=() => {},
}) {
  const [selectedChoices, setSelectedChoices] = useState({});
  const handleFilterChange = filter => {
    if(filter.selectedChoice) {
      setSelectedChoices({
        ...selectedChoices,
        [filter.filterId]: filter.selectedChoice,
      });
    }
    setFilter(filter, formFilters, setFormFilters);
  };
  const clearFilter = filterId => {
    const newFormFilters = formFilters.filter(
      f => f.filterId !== filterId,
    );
    setFormFilters(newFormFilters);
  };

  const safeSchemas = schemas || [];

  return (
    <div>
      <Text
        variant="h5"
        style={{ margin: '16px 0 16px 16px' }}
        id="FILTERS"
      />
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          padding: '8px 16px 16px',
        }}
        >        
        {safeSchemas.map(schema => {
          if(schema.dependency) {

            const dependencyChioce = selectedChoices[schema.dependency];
            if(dependencyChioce) {
              let choices = [];
              switch(schema.id) {
                case 'relationshipRoles':
                  choices = dependencyChioce.roles?.map(data => {
                    return {
                      label: data.label,
                      value: data.guid
                    }
                  }) || [];
                  break;
              }
              const componentProps = {
                ...schema.filterComponentProps,
                choices,
              };
              return (
                <schema.FilterComponent
                    key={`${schema.id}-${selectedChoices[schema.dependency]?.value || ''}`}
                    labelId={schema.labelId}
                    onChange={handleFilterChange}
                    onClearFilter={clearFilter}
                    {...componentProps}
                  />                
              )
            } else {
              return <Fragment key={`${schema.id}-${selectedChoices[schema.dependency]?.value || ''}`} />;
            }
          }
          return (
            <schema.FilterComponent
              key={schema.id}
              labelId={schema.labelId}
              onChange={handleFilterChange}
              onClearFilter={clearFilter}
              {...schema.filterComponentProps}
            />
          );
        })}
      </div>
    </div>
  );
}