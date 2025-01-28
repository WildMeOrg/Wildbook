
import React, { useState } from "react";
import { Col, Dropdown, FormControl } from "react-bootstrap";
import MainButton from "../MainButton";
import { FormattedMessage } from "react-intl";
import ThemeColorContext from "../../ThemeColorProvider";

export default function HeaderQuickSearch() {
    const [search, setSearch] = useState("");
    const [searchResults, setSearchResults] = useState([{
        name: "5a746580-df85-40cd-976f-ae0d53155ec4",
        species: "Species 1",
    }, {
        name: "Name 2",
        species: "Species 2",
    }, {
        name: "Name 3",
        species: "Species 3",
    },{
        name: "Name 3",
        species: "Species 3",
    },{
        name: "Name 3",
        species: "Species 3",
    },{
        name: "Name 3",
        species: "Species 3",
    },{
        name: "Name 3",
        species: "Species 3",
    },{
        name: "Name 3",
        species: "Species 3",
    },{
        name: "Name 3",
        species: "Species 3",
    },
    {
        name: "Name 3",
        species: "Species 3",
    },

]);
    const [showDropdown, setShowDropdown] = useState(false);
    const theme = React.useContext(ThemeColorContext);

    const handleInputChange = (e) => {
        setSearch(e.target.value);
    };

    const handleClearSearch = () => {
        setSearch("");
        setShowDropdown(false);
    };

    console.log("showDropdown", showDropdown);
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

                <Dropdown.Menu style={{
                    width: "300px",
                    marginTop: "10px",
                    overflow: "auto",
                    maxHeight: "400px",
                }}>
                    {searchResults.map((result, index) => (
                        <Dropdown.Item key={index}>
                            <div className="d-flex flex-row justify-content-between">
                                <div className="individual-name" style={{
                                    width: "180px",
                                    fontSize: "0.8rem",
                                    overflow: "hidden",
                                }}>
                                    <div>{result.name}</div>
                                    <div>{result.species}</div>
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
                                    <FormattedMessage id="nick_name" />
                                </MainButton>
                            </div>
                        </Dropdown.Item>
                    ))}
                </Dropdown.Menu>
            </Dropdown>
        </div>
    );
}
