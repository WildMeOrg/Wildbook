// import React, { createContext, useState } from 'react';

// const FilterContext = createContext();

// const FilterProvider = ({ children }) => {
//   const [filters, setFilters] = useState({});

//   const updateFilter = (filterName, value) => {
//     console.log("1111111111111111FilterName:", filterName);
//     console.log("2222222222222222Value:", value);
//     setFilters(prevFilters => ({
//       ...prevFilters,
//       [filterName]: value
//     }));
//   };


//   return (
//     <FilterContext.Provider value={{ filters, updateFilter }}>
//       {children}
//     </FilterContext.Provider>
//   );
// };

// export { FilterContext, FilterProvider };

import { createContext } from "react";

const FilterContext = createContext(true);
export default FilterContext;

