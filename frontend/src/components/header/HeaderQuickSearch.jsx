import { set } from "date-fns";
import React, { useState } from "react";

export default function HeaderQuickSearch() {
    const [search, setSearch] = useState("");
    const [searchResults, setSearchResults] = useState(["Result 1", "Result 2", "Result 3"]);

    return (
        <div className="header-quick-search d-flex flex-column" >
            <div className="header-quick-search d-flex flex-row" >
                <input
                    type="text"
                    placeholder="Search"
                    className="header-quick-search-input"
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
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
                    onClick={() => {
                        setSearch("");
                    }}
                >
                    <i class="bi bi-x"></i>
                </button>
            </div>
            <div className="header-quick-search-results">
                {searchResults.map((result) => (
                    <div className="header-quick-search-result">{result}</div>
                ))}
            </div>
        </div>
    );
}