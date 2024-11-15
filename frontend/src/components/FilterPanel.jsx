import React, { useEffect, useState, useRef } from "react";

import Text from "./Text";
import { Container } from "react-bootstrap";
import ThemeContext from "../ThemeColorProvider";
import BrutalismButton from "./BrutalismButton";
import useGetSiteSettings from "../models/useGetSiteSettings";
import { Col, Row } from "react-bootstrap";
import { FormattedMessage } from "react-intl";

function setFilter(newFilter, tempFormFilters, setTempFormFilters) {
  const matchingFilterIndex = tempFormFilters.findIndex(
    (f) => f.filterId === newFilter.filterId,
  );
  if (matchingFilterIndex === -1) {
    if (newFilter?.filterId?.startsWith("microsatelliteMarkers.loci")) {
      tempFormFilters.splice(
        0,
        tempFormFilters.length,
        newFilter,
        ...tempFormFilters,
      );
    } else {
      setTempFormFilters([...tempFormFilters, newFilter]);
    }
  } else {
    if (
      newFilter?.filterId?.startsWith("microsatelliteMarkers.loci") ||
      newFilter?.filterId?.startsWith("measurements")
    ) {
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
  setFormFilters = () => {},
  setFilterPanel,
  style = {},
  handleSearch = () => {},
  setSearchParams = () => {},
  setQueryID = "",
}) {
  const [tempFormFilters, setTempFormFilters] = useState([]);
  const { data } = useGetSiteSettings();
  const safeSchemas = schemas || [];
  const [clicked, setClicked] = useState(safeSchemas[0]?.id);
  const theme = React.useContext(ThemeContext);
  const containerRef = useRef(null);
  const schemaRefs = useRef([]);
  const isScrollingByClick = useRef(false);
  const scrollTimeout = useRef(null);

  console.log("safeSchemas", safeSchemas);

  useEffect(() => {
    safeSchemas.forEach((schema, index) => {
      if (!schemaRefs.current[index]) {
        schemaRefs.current[index] = React.createRef();
      }
    });
  }, [safeSchemas]);

  const handleClick = (id) => {
    clearTimeout(scrollTimeout.current);
    setClicked(id);
    isScrollingByClick.current = true;

    const index = safeSchemas.findIndex((schema) => schema.id === id);
    if (schemaRefs.current[index]) {
      schemaRefs.current[index].current.scrollIntoView({
        behavior: "smooth",
        block: "start",
        scrollMode: "if-needed",
      });
    }

    scrollTimeout.current = setTimeout(() => {
      isScrollingByClick.current = false;
    }, 500);
  };

  const handleScroll = () => {
    if (isScrollingByClick.current) return;

    schemaRefs.current.forEach((ref, index) => {
      if (ref.current) {
        const rect = ref.current.getBoundingClientRect();
        if (rect.top >= 0 && rect.top < window.innerHeight / 2) {
          setClicked(safeSchemas[index].id);
        }
      }
    });
  };

  useEffect(() => {
    setTempFormFilters(formFilters);
  }, [formFilters]);

  useEffect(() => {}, [tempFormFilters]);

  const handleFilterChange = (filter = null, remove) => {
    if (remove) {
      setTempFormFilters((prevFilters) => {
        const newFilters = prevFilters.filter((f) => f.filterId !== remove);
        return newFilters;
      });
    } else {
      setFilter(filter, tempFormFilters, setTempFormFilters);
    }
  };

  const clearFilter = (filterId) => {
    const newFormFilters = formFilters.filter((f) => f.filterId !== filterId);
    setTempFormFilters(newFormFilters);
  };

  return (
    <Container
      style={{
        ...style,
      }}
    >
      <Text
        className="mb-3 ms-3 fw-bold text-white"
        style={{ fontSize: "30px" }}
        id="ENCOUNTER_SEARCH_FILTERS"
      />
      <Row className="pt-2" style={{ alignItems: "flex-start" }}>
        <Col md={3} sm={12} className="d-flex align-items-center mb-3">
          <div
            ref={containerRef}
            className="w-100 d-flex flex-column overflow-auto rounded-3 shadow-sm p-2 text-white "
            style={{
              minHeight: "600px",
              height: "70vh",
              background: "rgba(255, 255, 255, 0.1)",
              backdropFilter: "blur(3px)",
              WebkitBackdropFilter: "blur(2px)",
              fontSize: "20px",
              // flexWrap: "wrap",
            }}
          >
            {safeSchemas.map((schema, index) => {
              return (
                <div
                  key={index}
                  className={`d-flex justify-content-between align-items-center rounded-3 p-2 mt-2 ${clicked === schema.id ? "bg-white" : "text-white"} cursor-pointer`}
                  style={{
                    color:
                      clicked === schema.id
                        ? theme.primaryColors.primary700
                        : "white",
                    minHeight: "50px",
                    cursor: "pointer",
                  }}
                  onClick={() => {
                    setClicked(schema.id);
                    handleClick(schema.id);
                  }}
                >
                  <Text
                    id={schema.labelId}
                    className="m-3"
                    style={{
                      fontWeight: "500",
                      marginRight: "20px",
                    }}
                  ></Text>
                  <span>
                    {" "}
                    <i
                      className="bi bi-chevron-right"
                      style={{ fontSize: "14px" }}
                    ></i>{" "}
                  </span>
                </div>
              );
            })}
            <div className="mt-2 d-flex flex-wrap justify-content-center align-items-center w-100 gap-3">
              <BrutalismButton
                color="white"
                backgroundColor={theme.primaryColors.primary700}
                borderColor={theme.primaryColors.primary700}
                onClick={() => {
                  const uniqueFilters = Array.from(
                    new Map(
                      tempFormFilters.map((filter) => [
                        filter.filterId,
                        filter,
                      ]),
                    ).values(),
                  );
                  setFormFilters(uniqueFilters);
                  setFilterPanel(false);
                  handleSearch();
                  setQueryID(null);
                  setSearchParams((prevSearchParams) => {
                    const newSearchParams = new URLSearchParams(
                      prevSearchParams,
                    );
                    newSearchParams.delete("searchQueryId");
                    return newSearchParams;
                  });
                }}
                noArrow={true}
                style={{
                  paddingLeft: 5,
                  paddingRight: 5,
                }}
              >
                <FormattedMessage id="APPLY" defaultMessage="Apply" />
              </BrutalismButton>
              <BrutalismButton
                style={{
                  color: theme.primaryColors.primary700,
                  paddingLeft: 5,
                  paddingRight: 5,
                }}
                borderColor={theme.primaryColors.primary700}
                onClick={() => {
                  setFormFilters([]);
                  setTempFormFilters([]);
                  setSearchParams(new URLSearchParams());
                  window.location.reload();
                }}
                noArrow={true}
              >
                <FormattedMessage id="RESET" defaultMessage="Reset" />
              </BrutalismButton>
            </div>
          </div>
        </Col>
        <Col md={9} sm={12} className="d-flex align-items-center">
          <div
            className="w-100 d-flex flex-column rounded-3 p-3 text-white overflow-auto"
            style={{
              height: "70vh",
              minHeight: "600px",
              background: "rgba(255, 255, 255, 0.1)",
              backdropFilter: "blur(3px)",
              WebkitBackdropFilter: "blur(2px)",
              borderRadius: "10px",
              boxShadow: "0 4px 6px rgba(0, 0, 0, 0.1)",
              padding: "20px",
              color: "white",
              overflow: "auto",
            }}
            onScroll={handleScroll}
          >
            {safeSchemas.map((schema, index) => {
              return (
                <div
                  className="mb-3"
                  key={index}
                  ref={schemaRefs.current[index]}
                >
                  <schema.FilterComponent
                    // key={schema.id}
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
            })}
          </div>
        </Col>
      </Row>
    </Container>
  );
}
