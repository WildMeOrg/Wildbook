
import React, { Fragment, useEffect, useState, useRef, useContext } from 'react';

import Text from './Text';
import { Container } from 'react-bootstrap';
import { filter, set } from 'lodash-es';
import ThemeContext from "../ThemeColorProvider";
import BrutalismButton from './BrutalismButton';
import useGetSiteSettings from '../models/useGetSiteSettings';
import FilterContext from '../FilterContextProvider';
import { Col, Row } from 'react-bootstrap';

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
  formFilters = [],
  setFormFilters = () => { },
  setFilterPanel,
  style = {},

}) {
  const [selectedChoices, setSelectedChoices] = useState({});
  const [tempFormFilters, setTempFormFilters] = useState(formFilters);
  // const { filters, updateFilter, resetFilters } = useContext(FilterContext);

  const { data } = useGetSiteSettings();
  
  const handleFilterChange = filter => {
    console.log("Filter:", filter);
    // if (filter.selectedChoice) {
    //   setSelectedChoices({
    //     ...selectedChoices,
    //     [filter.filterId]: filter.selectedChoice,
    //   });
    // }
    setFilter(filter, tempFormFilters, setTempFormFilters);
  };
  const clearFilter = filterId => {
    const newFormFilters = formFilters.filter(
      f => f.filterId !== filterId,
    );
    setTempFormFilters(newFormFilters);
  };

  const safeSchemas = schemas || [];

  function debounce(func, wait) {
    let timeout;
    return function (...args) {
      const context = this;
      clearTimeout(timeout);
      timeout = setTimeout(() => func.apply(context, args), wait);
    };
  }

  const [clicked, setClicked] = useState(safeSchemas[0]?.id);
  const theme = React.useContext(ThemeContext);

  const containerRef = useRef(null);

  const handleWheel = (event) => {
    event.preventDefault();
    if (!safeSchemas.length) return;
    const currentIndex = safeSchemas.findIndex(schema => schema.id === clicked);
    if (event.deltaY < 0) {
      if (currentIndex > 0) {
        setClicked(safeSchemas[currentIndex - 1].id);
      }
    } else {
      if (currentIndex < safeSchemas.length - 1) {
        setClicked(safeSchemas[currentIndex + 1].id);
      }
    }
  };

  const debouncedHandleWheel = debounce(handleWheel, 100);

  // useEffect(() => {
  //   const div = containerRef.current;
  //   if (div) {
  //     div.addEventListener('wheel', debouncedHandleWheel, { passive: false });
  //   }
  //   return () => {
  //     if (div) {
  //       div.removeEventListener('wheel', debouncedHandleWheel);
  //     }
  //   };
  // }, [clicked, safeSchemas.length]);


  return (
    <Container
      style={{
        ...style,
      }}>
      <Text
        className="mb-3 ms-3 fw-bold text-white"
        variant="h1"
        id="ENCOUNTER_SEARCH_FILTERS"
      />
      <Row className="p-3">   

        <Col md={3} sm={12} className='d-flex align-items-center'>
          <div ref={containerRef} className="w-100 d-flex flex-column overflow-auto rounded-3 shadow-sm p-3 text-white "
            style={{
              height: '700px',
              background: 'rgba(255, 255, 255, 0.1)',
              backdropFilter: 'blur(3px)',
              WebkitBackdropFilter: 'blur(2px)',
              fontSize: '20px',
            }}>

            {safeSchemas.map(schema => {
              return <div
                className={`d-flex justify-content-between align-items-center rounded-3 p-2 mt-2 ${clicked === schema.id ? 'bg-white' : 'text-white'} cursor-pointer`}
                style={{
                  color: clicked === schema.id ? theme.primaryColors.primary700 : 'white',
                  height: "50px",
                  cursor: 'pointer',
                }}
                onClick={() => {
                  setClicked(schema.id);
                }}
              >
                <Text
                  id={schema.labelId}
                  className="m-3"
                  style={{
                    fontWeight: '500',
                  }}
                >
                </Text>
                <span>  {" > "}   </span>
              </div>
            })}
            <div className="d-flex flex-row mt-5">
              <BrutalismButton
                color="white"
                backgroundColor={theme.primaryColors.primary700}
                borderColor={theme.primaryColors.primary700}
                onClick={() => {
                  setFormFilters(tempFormFilters);
                  setFilterPanel(false);
                }}
              >
                APPLY
              </BrutalismButton>
              <BrutalismButton style={{
                color: theme.primaryColors.primary700,

              }}
                borderColor={theme.primaryColors.primary700}
                onClick={() => {
                  setFormFilters([]);
                  setTempFormFilters([]);
                  // setFilterPanel(false);
                  // localStorage.removeItem("formData");
                  window.location.reload();
                }}>

                RESET
              </BrutalismButton>
            </div>

          </div>
        </Col>
        <Col md={9} sm={12} className='d-flex align-items-center'>
          <div className="w-100 d-flex flex-column rounded-3 p-3 text-white overflow-auto"
            style={{
              // width: '900px',
              height: '700px',
              background: 'rgba(255, 255, 255, 0.1)',
              backdropFilter: 'blur(3px)',
              WebkitBackdropFilter: 'blur(2px)',
              borderRadius: '10px',
              boxShadow: '0 4px 6px rgba(0, 0, 0, 0.1)',
              padding: '20px',
              color: 'white',
              overflow: 'auto',
            }}>
            {
              safeSchemas.map(schema => {
                return (

                  // schema.id === clicked && <schema.FilterComponent
                  //   key={schema.id}
                  //   labelId={schema.labelId}
                  //   onChange={handleFilterChange}
                  //   onClearFilter={clearFilter}
                  //   {...schema.filterComponentProps}
                  //   data={data}
                  //   filters={filters}
                  // />
                  <div
                    key={schema.id}
                    style={{
                      display: schema.id === clicked ? 'block' : 'none',
                      width: '100%',

                    }}
                  >
                    <schema.FilterComponent
                      key={schema.id}
                      labelId={schema.labelId}
                      onChange={handleFilterChange}
                      onClearFilter={clearFilter}
                      {...schema.filterComponentProps}
                      data={data}
                      tempFormFilters={tempFormFilters}
                    />
                  </div>
                );
              }
              )}

          </div>
        </Col>
      {/* </div> */}
      </Row>
    </Container>
  );
}