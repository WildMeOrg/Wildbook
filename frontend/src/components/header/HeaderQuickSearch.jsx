import React, { useState, useContext } from "react";
import { Dropdown, FormControl, Spinner } from "react-bootstrap";
import MainButton from "../MainButton";
import { FormattedMessage } from "react-intl";
import ThemeColorContext from "../../ThemeColorProvider";
import usePostHeaderQuickSearch from "../../models/usePostHeaderQuickSearch";

export default function HeaderQuickSearch() {
  const [search, setSearch] = useState("");
  const [showDropdown, setShowDropdown] = useState(false);
  const theme = useContext(ThemeColorContext);

  const { searchResults, loading } = usePostHeaderQuickSearch(search);

  const handleInputChange = (e) => {
    setSearch(e.target.value);
  };

  const handleClearSearch = () => {
    setSearch(""); //
    setShowDropdown(false);
  };

  return (
    <div className="header-quick-search">
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
            <i className="bi bi-x"></i>
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
            searchResults.map((result, index) => (
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
                  <div className="d-flex flex-row justify-content-between">
                    <div
                      className="individual-name"
                      style={{
                        width: "180px",
                        fontSize: "0.8rem",
                        overflow: "hidden",
                      }}
                    >
                      <div>{search}</div>
                      <div>{result.taxonomy}</div>
                    </div>
                    <MainButton
                      noArrow={true}
                      style={{
                        width: "80px",
                        height: "30px",
                        color: "white",
                        fontSize: "0.8rem",
                        marginRight: 0,
                      }}
                      backgroundColor={theme.primaryColors.primary500}
                    >
                      <FormattedMessage
                        id={
                          result?.id
                            ?.toLowerCase()
                            .includes(search.toLowerCase())
                            ? "SYSTEM_ID"
                            : result?.names?.some((name) =>
                                  name
                                    .toLowerCase()
                                    .includes(search.toLowerCase()),
                                )
                              ? "FILTER_NAME"
                              : "UNKNOWN"
                        }
                      />
                    </MainButton>
                  </div>
                  {index < searchResults.length - 1 && <Dropdown.Divider />}
                </Dropdown.Item>
              </React.Fragment>
            ))}
        </Dropdown.Menu>
      </Dropdown>
    </div>
  );
}
