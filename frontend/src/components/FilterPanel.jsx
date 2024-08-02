
import React, { Fragment, useEffect, useState, useRef, useContext } from 'react';

import Text from './Text';
import { Container, FormControl } from 'react-bootstrap';
import { filter, set } from 'lodash-es';
import ThemeContext from "../ThemeColorProvider";
import BrutalismButton from './BrutalismButton';
import useGetSiteSettings from '../models/useGetSiteSettings';
import FilterContext from '../FilterContextProvider';
import { Col, Row } from 'react-bootstrap';
import { FormattedMessage } from 'react-intl';
import { useNavigate } from 'react-router-dom';

function setFilter(newFilter, tempFormFilters, setTempFormFilters) {
  const matchingFilterIndex = tempFormFilters.findIndex(
    f => f.filterId === newFilter.filterId,
  );
  if (matchingFilterIndex === -1) {
    if (newFilter?.filterId?.startsWith("microsatelliteMarkers.loci")) {
      tempFormFilters.splice(0, tempFormFilters.length, newFilter, ...tempFormFilters);
    } else {
      setTempFormFilters([...tempFormFilters, newFilter]);
    }
  } else {
    //a workaround to update the microsatelliteMarkers.loci filters, since it's updating 
    //multiple filters at once, we need to update the filter in place, instead of calling setTempFormFilters
    //which is async and will cause issue
    if (newFilter?.filterId?.startsWith("microsatelliteMarkers.loci") || newFilter?.filterId?.startsWith("measurements")) {
      tempFormFilters[matchingFilterIndex] = newFilter;
    } else {
      const newFormFilters = [...tempFormFilters];
      newFormFilters[matchingFilterIndex] = newFilter;
      setTempFormFilters(newFormFilters);
    }
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
  const [tempFormFilters, setTempFormFilters] = useState([]);
  useEffect(() => {
    setTempFormFilters(formFilters);
  }, [formFilters]);
  // const { filters, updateFilter, resetFilters } = useContext(FilterContext);
  const navigate = useNavigate();
  const { data } = useGetSiteSettings();

  const handleFilterChange = (filter = null, remove) => {
    if (remove) {
      if (remove?.startsWith("microsatelliteMarkers.loci") || remove?.startsWith("measurements")) {
        tempFormFilters.splice(0, tempFormFilters.length, ...tempFormFilters.filter(filter => filter.filterId !== remove));
      } else {
        const updatedFilters = tempFormFilters.filter(filter => filter.filterId !== remove);
        setTempFormFilters(updatedFilters);
      }

    } else setFilter(filter, tempFormFilters, setTempFormFilters);
    // if (filter.selectedChoice) {
    //   setSelectedChoices({
    //     ...selectedChoices,
    //     [filter.filterId]: filter.selectedChoice,
    //   });
    // }
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
        style={{fontSize: "30px"}}
        id="ENCOUNTER_SEARCH_FILTERS"
      />
      <Row className="p-3" style={{ alignItems: 'flex-start' }}>

        <Col md={3} sm={12} className='d-flex align-items-center mb-3'>
          <div ref={containerRef} className="w-100 d-flex flex-column overflow-auto rounded-3 shadow-sm p-2 text-white "
            style={{
              height: '700px',
              background: 'rgba(255, 255, 255, 0.1)',
              backdropFilter: 'blur(3px)',
              WebkitBackdropFilter: 'blur(2px)',
              fontSize: '20px',
              // flexWrap: "wrap",
            }}>

            {safeSchemas.map((schema, index) => {
              return <div
              key={index}
                className={`d-flex justify-content-between align-items-center rounded-3 p-2 mt-2 ${clicked === schema.id ? 'bg-white' : 'text-white'} cursor-pointer`}
                style={{
                  color: clicked === schema.id ? theme.primaryColors.primary700 : 'white',
                  minHeight: "50px",
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
                    marginRight: '20px'
                  }}
                >
                </Text>
                <span>  {" > "}   </span>
              </div>
            })}
            <div
              className="mt-5 d-flex flex-wrap justify-content-center align-items-center" >
              <FormControl
                type="text"
                placeholder="Search ID"
                // className="rounded-3"
                style={{
                  width: "80px",
                  marginRight: "10px",
                  marginTop: "10px",
                }}
                onKeyDown={(e) => {
                  // console.log(e.target.value);
                  if(e.key === 'Enter') {
                    // console.log('Enter key pressed');
                    navigate(`/encounter-search${e.target.value}`);                    
                  }
                }}
              />
              <BrutalismButton
                color="white"
                backgroundColor={theme.primaryColors.primary700}
                borderColor={theme.primaryColors.primary700}
                onClick={() => {
                  const uniqueFilters = Array.from(
                    new Map(tempFormFilters.map(filter => [filter.filterId, filter])).values()
                )
                  setFormFilters(uniqueFilters);
                  setFilterPanel(false);
                }}
                noArrow={true}
                style={{
                  paddingLeft: 5,
                  paddingRight: 5,
                }}
              >
                <FormattedMessage id="APPLY" defaultMessage="Apply" />
              </BrutalismButton>
              <BrutalismButton style={{
                color: theme.primaryColors.primary700,
                paddingLeft: 5,
                paddingRight: 5,
              }}
                borderColor={theme.primaryColors.primary700}
                onClick={() => {
                  setFormFilters([]);
                  setTempFormFilters([]);
                  // setFilterPanel(false);
                  // localStorage.removeItem("formData");
                  window.location.reload();
                }}
                noArrow={true}

              >

                <FormattedMessage id="RESET" defaultMessage="Reset" />
              </BrutalismButton>
            </div>

          </div>
        </Col>
        <Col md={9} sm={12} className='d-flex align-items-center'>
          <div className="w-100 d-flex flex-column rounded-3 p-3 text-white overflow-auto"
            style={{
              // width: '900px',
              minHeight: '700px',
              background: 'rgba(255, 255, 255, 0.1)',
              backdropFilter: 'blur(3px)',
              WebkitBackdropFilter: 'blur(2px)',
              borderRadius: '10px',
              boxShadow: '0 4px 6px rgba(0, 0, 0, 0.1)',
              padding: '20px',
              color: 'white',
              // overflow: 'auto',
              overflow: "visible",
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
                      setFormFilters={setFormFilters}
                      formFilters={formFilters}
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