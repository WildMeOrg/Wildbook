import React, { useState } from "react";
import { Dropdown, FormControl, Spinner } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import usePostHeaderQuickSearch from "../../models/usePostHeaderQuickSearch";

export default function HeaderQuickSearch() {
  const [search, setSearch] = useState("");
  const [showDropdown, setShowDropdown] = useState(false);

  const { searchResults, loading } = usePostHeaderQuickSearch(search);

  const handleInputChange = (e) => {
    setSearch(e.target.value);
  };

  const handleClearSearch = () => {
    setSearch("");
    setShowDropdown(false);
  };

  return (
    <div className="header-quick-search" data-testid="header-quick-search">
      <Dropdown show={showDropdown} onBlur={() => setShowDropdown(false)}>
        <div className="d-flex">
          <FormControl
            type="text"
            placeholder="Search Individuals"
            value={search}
            onChange={handleInputChange}
            onFocus={() => setShowDropdown(true)}
            style={{
              height: "35px",
              width: "150px",
              paddingLeft: "10px",
              backgroundColor: "transparent",
              border: "1px solid white",
              borderRadius: "20px 0px 0px 20px",
              color: "white",
              borderRight: "none",
            }}
            className="header-quick-search-input"
          />
          <button
            className="header-quick-search-button"
            style={{
              height: "35px",
              backgroundColor: "transparent",
              border: "1px solid white",
              borderRadius: "0px 20px 20px 0px",
              color: "white",
              borderLeft: "none",
            }}
            onClick={handleClearSearch}
          >
            {search ? (
              <i className="bi bi-x"></i>
            ) : (
              <i className="bi bi-search"></i>
            )}
          </button>
        </div>

        <Dropdown.Menu
          style={{
            width: "300px",
            marginTop: "10px",
            overflow: "auto",
            maxHeight: "400px",
            minHeight: "100px",
          }}
        >
          {loading && (
            <Dropdown.Item className="text-center">
              <Spinner animation="border" size="sm" />
              <span className="ms-2">
                <FormattedMessage id="LOADING" />
              </span>
            </Dropdown.Item>
          )}
          {!loading && searchResults.length === 0 && search.trim() === "" && (
            <Dropdown.Item>
              <FormattedMessage id="SEARCH_RESULT_DISPLAY" />
            </Dropdown.Item>
          )}
          {!loading && searchResults.length === 0 && search.trim() !== "" && (
            <Dropdown.Item>
              <FormattedMessage id="NO_MATCHING_RESULTS" />
            </Dropdown.Item>
          )}
          {!loading &&
            searchResults.map((result, index) => {
              let value =
                result.displayName ||
                (result.names?.length ? result.names.join(" | ") : null) ||
                result.id;

              return (
                <React.Fragment key={index}>
                  <Dropdown.Item
                    key={index}
                    as="button"
                    target="_blank"
                    rel="noopener noreferrer"
                    onMouseDown={(e) => {
                      e.preventDefault();
                      window.open(`/individuals.jsp?id=${result.id}`);
                    }}
                  >
                    <div
                      className="d-flex flex-row justify-content-between"
                      style={{
                        height: "40px",
                      }}
                    >
                      <div
                        className="individual-name w-100"
                        style={{
                          fontSize: "0.8rem",
                          overflow: "hidden",
                        }}
                      >
                        <div>{value}</div>
                        <div>{result.taxonomy}</div>
                      </div>
                    </div>
                    {index < searchResults.length - 1 && <Dropdown.Divider />}
                  </Dropdown.Item>
                </React.Fragment>
              );
            })}
        </Dropdown.Menu>
      </Dropdown>
    </div>
  );
}
