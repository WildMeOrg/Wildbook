
import React, { Fragment, useEffect, useState, useRef } from 'react';

import Text from './Text';
import { Container } from 'react-bootstrap';
import { set } from 'lodash-es';
import ThemeContext from "../ThemeColorProvider";
import BrutalismButton from './BrutalismButton';

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
}) {
  const [selectedChoices, setSelectedChoices] = useState({});
  const handleFilterChange = filter => {
    if (filter.selectedChoice) {
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

  useEffect(() => {
    const div = containerRef.current;
    if (div) {
      div.addEventListener('wheel', debouncedHandleWheel, { passive: false });
    }
    return () => {
      if (div) {
        div.removeEventListener('wheel', debouncedHandleWheel);
      }
    };
  }, [clicked, safeSchemas.length]);

  return (
    <Container>
      <Text
        variant="h1"
        style={{ margin: '16px 0 16px 16px', fontWeight: '500', color: '#fff' }}
        id="ENCOUNTER_SEARCH_FILTERS"
      />
      <div
        style={{
          display: 'flex',
          flexDirection: 'row',
          padding: '8px 16px 16px',
        }}
      >

        <div ref={containerRef}
          style={{
            width: '300px',
            height: '700px',
            overflow: 'auto',
            background: 'rgba(255, 255, 255, 0.1)',
            backdropFilter: 'blur(3px)',
            WebkitBackdropFilter: 'blur(2px)',
            borderRadius: '10px',
            boxShadow: '0 4px 6px rgba(0, 0, 0, 0.1)',
            padding: '20px',
            color: 'white',
            marginRight: '20px',
            fontSize: '20px',
          }}>

          {safeSchemas.map(schema => {
            return <div
              className='d-flex justify-content-between align-items-center'
              style={{
                width: "100%",
                height: "50px",
                borderRadius: '10px',
                padding: '10px',
                marginTop: '10px',
                backgroundColor: clicked === schema.id ? 'white' : 'transparent',
                color: clicked === schema.id ? theme.primaryColors.primary700 : 'white',
                cursor: 'pointer',
              }}
              onClick={() => {
                setClicked(schema.id);
              }}
            >
              <Text
                id={schema.labelId}
                style={{
                  margin: '16px 0 16px 0',
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
              backgroundColor= {theme.primaryColors.primary700}
              borderColor={theme.primaryColors.primary700}>
              APPLY
            </BrutalismButton>
            <BrutalismButton style={{
              color: theme.primaryColors.primary700,

            }}
              borderColor={theme.primaryColors.primary700}>
              RESET
            </BrutalismButton>
          </div>

        </div>
        <div style={{
          width: '900px',
          maxHeight: '700px',
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
                schema.id === clicked && <schema.FilterComponent
                  key={schema.id}
                  labelId={schema.labelId}
                  onChange={handleFilterChange}
                  onClearFilter={clearFilter}
                  {...schema.filterComponentProps}
                />
              );
            }
            )}

        </div>
      </div>
    </Container>
  );
}