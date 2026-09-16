import React, { useState, useEffect } from "react";
import "../css/projectList.css";
import axios from "axios";
import { FormattedMessage } from "react-intl";
import { useIntl } from "react-intl";

export default function ProjectList() {
  const intl = useIntl();
  const [currentUser, setCurrentUser] = useState(null);
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [sortConfig, setSortConfig] = useState({ key: null, direction: "asc" });
  const [currentPage, setCurrentPage] = useState(1);
  const [projectsPerPage, setProjectsPerPage] = useState(10);
  const [gotoPage, setGotoPage] = useState(1);

  const fetchData = async () => {
    try {
      const response = await axios.get("/api/v3/user");
      setCurrentUser(response.data.displayName);
      const projectsResponse = await axios.get("/api/v3/projects");
      setProjects(projectsResponse.data.projects);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    document.title = intl.formatMessage({ id: "PROJECT_LIST_TITLE" });
    fetchData();
  }, []);

  const sortProjects = (key) => {
    let direction = "asc";
    if (sortConfig.key === key && sortConfig.direction === "asc") {
      direction = "desc";
    }
    setSortConfig({ key, direction });

    const sortedProjects = [...projects].sort((a, b) => {
      if (a[key] < b[key]) {
        return direction === "asc" ? -1 : 1;
      }
      if (a[key] > b[key]) {
        return direction === "asc" ? 1 : -1;
      }
      return 0;
    });
    setProjects(sortedProjects);
  };

  // Determines if table headers have up or down arrow:
  const getHeaderClass = (key) => {
    if (sortConfig.key !== key) return "";
    return sortConfig.direction === "asc" ? "headerSortUp" : "headerSortDown";
  };

  const totalPages = Math.ceil(projects.length / projectsPerPage);

  const paginatedProjects = projects.slice(
    (currentPage - 1) * projectsPerPage,
    currentPage * projectsPerPage,
  );

  const handlePageChange = (page) => {
    if (page < 1 || page > totalPages) {
      alert(intl.formatMessage({ id: "INPUT_PAGE_ALERT" }, { totalPages }));
      return;
    }
    setCurrentPage(page);
  };

  return (
    <div className="projectListDiv">
      <div className="headerContainer">
        <h1 className="projectsTitle">
          <FormattedMessage id="PROJECTS_FOR" /> {currentUser}
        </h1>
        <button
          className="newProject"
          onClick={() => (window.location.href = `/projects/createProject.jsp`)}
        >
          {" "}
          <FormattedMessage id="NEW_PROJECT" />
        </button>
      </div>
      {loading ? (
        <div className="d-flex justify-content-center align-items-center" style={{ minHeight: "200px" }}>
          <div className="spinner-border text-primary" role="status">
            <span className="visually-hidden">
              <FormattedMessage id="LOADING" />
            </span>
          </div>
        </div>
      ) : projects.length === 0 ? (
        <h4>
          <FormattedMessage id="NO_PROJECTS" />
        </h4>
      ) : (
        <table>
          <thead>
            <tr>
              <th
                onClick={() => sortProjects("name")}
                className={getHeaderClass("name")}
              >
                <FormattedMessage id="FILTER_PROJECT_NAME" />
              </th>
              <th
                onClick={() => sortProjects("id")}
                className={getHeaderClass("id")}
              >
                <FormattedMessage id="PROJECTS_ID" />
              </th>
              <th
                onClick={() => sortProjects("percentComplete")}
                className={getHeaderClass("percentComplete")}
              >
                <FormattedMessage id="PERCENTAGE_IDENTIFIED" />
              </th>
              <th
                onClick={() => sortProjects("numberEncounters")}
                className={getHeaderClass("numberEncounters")}
              >
                <FormattedMessage id="ENCOUNTERS" />
              </th>
            </tr>
          </thead>
          <tbody>
            {paginatedProjects.map((project) => (
              <tr
                key={project.id}
                onClick={() =>
                  (window.location.href = `/projects/project.jsp?id=${project.id}`)
                }
              >
                <td>{project.name}</td>
                <td>{project.id}</td>
                <td>{project.percentComplete}%</td>
                <td>{project.numberEncounters}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <div className="pagination">
        <div className="itemCounter">
          <FormattedMessage id="TOTAL" /> {projects.length}{" "}
          <FormattedMessage id="ITEMS" />
        </div>
        <button
          className="previous-button"
          onClick={() => handlePageChange(currentPage - 1)}
          disabled={currentPage === 1}
        ></button>
        {Array.from({ length: totalPages }, (_, index) => (
          <button
            key={index + 1}
            onClick={() => handlePageChange(index + 1)}
            className={currentPage === index + 1 ? "active" : ""}
          >
            {index + 1}
          </button>
        ))}
        <button
          className="next-button"
          onClick={() => handlePageChange(currentPage + 1)}
          disabled={currentPage === totalPages}
        ></button>
        <div className="pagination-options">
          <label htmlFor="projectsPerPage"></label>
          <select
            id="projectsPerPage"
            value={projectsPerPage}
            onChange={(e) => setProjectsPerPage(Number(e.target.value))}
          >
            <option value={5}>
              5/
              <FormattedMessage id="PAGE" />
            </option>
            <option value={10}>
              10/
              <FormattedMessage id="PAGE" />
            </option>
            <option value={20}>
              20/
              <FormattedMessage id="PAGE" />
            </option>
            <option value={50}>
              50/
              <FormattedMessage id="PAGE" />
            </option>
          </select>
        </div>
        <div className="goto-box">
          <label htmlFor="gotoPage">
            {" "}
            <FormattedMessage id="GO_TO" />{" "}
          </label>
          <input
            id="gotoPage"
            type="text"
            value={gotoPage || ""}
            onChange={(e) => {
              const value = e.target.value;
              // Only Numeric Input
              if (!value || /^[0-9]+$/.test(value)) {
                setGotoPage(Number(value));
              }
            }}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                handlePageChange(gotoPage);
              }
            }}
          />
        </div>
      </div>
    </div>
  );
}
