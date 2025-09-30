import React from "react";
import { observer } from "mobx-react-lite";
import ProjectsIcon from "../../components/icons/ProjectsIcon";
import Select from "react-select";
import MainButton from "../../components/MainButton";
import ThemeColorContext from "../../ThemeColorProvider";
import RemoveIcon from "../../components/icons/RemoveIcon";

export const ProjectsCard = observer(({ store = {}
}) => {
    const themeColor = React.useContext(ThemeColorContext);
    const allProjectsRaw = store.siteSettingsData?.projectsForUser || {};
    const allProjects = Object.entries(allProjectsRaw).map(([key, value]) => ({
        id: key,
        name: value
    }));
    const encounterProjects = store.encounterData?.projects || [];
    const currentEncounterProjects = allProjects.filter(project => encounterProjects.includes(project.id));

    const options = allProjects.map(project => ({
        value: project.id,
        label: project.name
    }));

    console.log("selectedProjects:", store.selectedProjects);

    return (
        <div className="d-flex flex-column justify-content-between mt-3 mb-3"
            style={{
                padding: "20px",
                borderRadius: "10px",
                boxShadow: `0px 0px 10px rgba(0, 0, 0, 0.2)`,
                width: "100%"
            }}>
            <div className="mb-3 d-flex align-items-center justify-content-between">

                <div className="d-flex flex-row align-items-center mb-3">
                    <ProjectsIcon
                        style={{ marginRight: "10px" }}
                    />
                    <h6>Projects</h6>
                </div>
                {/* <PlusIcon /> */}
            </div>

            <div>
                {currentEncounterProjects.length > 0 ? (
                    currentEncounterProjects.map((project, index) => (
                        <div key={index} className="mt-2 mb-2"
                            style={{ position: 'relative' }}
                        >
                            <p className="mt-2"
                                style={{
                                    fontWeight: 'bold',
                                }}
                            >{project.name}</p>
                            <p>{`Project ID: ${project.id}`}</p>

                            <div style={{
                                width: '100%',
                                height: "10px",
                                borderBottom: '1px solid #ccc',
                            }}></div>
                            <button
                                type="button"
                                className="btn p-1"
                                aria-label={`Remove ${project.id}`}
                                title="Remove"
                                style={{
                                    position: "absolute",
                                    top: 0,
                                    right: 0,
                                    padding: "5px",
                                    background: "transparent",
                                    border: "none",
                                    cursor: "pointer",
                                }}
                                onClick={(e) => {
                                    e.stopPropagation();
                                    if (window.confirm(`are you sure you want to delete encounter from ${project.name}?`)) {
                                        store.removeProjectFromEncounter(project.id);
                                    }
                                }}
                            >
                                <RemoveIcon />
                            </button>
                        </div>))
                ) : null}
            </div>
            <div>
                <p className="mb-2">Search Project</p>
                <Select
                    placeholder="Select a project"
                    name="project-select"
                    isClearable={true}
                    isSearchable={true}
                    isMulti={true}
                    options={options}
                    className="basic-multi-select"
                    classNamePrefix="select"
                    menuPlacement="auto"
                    menuPortalTarget={document.body}
                    styles={{ menuPortal: base => ({ ...base, zIndex: 9999 }) }}
                    value={(store.selectedProjects || []).map(p => ({ value: p.id, label: p.name }))}
                    onChange={(items) => {
                        const arr = items ? items.map(o => ({ id: o.value, name: o.label })) : [];
                        store.setSelectedProjects(arr);
                    }}
                />
            </div>
            <div className="d-flex justify-content-between mt-3">
                <MainButton
                    onClick={store.addEncounterToProject}
                    noArrow={true}
                    color="white"
                    backgroundColor={themeColor?.wildMeColors?.cyan700}
                    borderColor={themeColor?.wildMeColors?.cyan700}
                >
                    Add Project
                </MainButton>
                <MainButton
                    onClick={() => {
                        store.setSelectedProjects(null);
                    }}
                    noArrow={true}
                    backgroundColor="white"
                    color={themeColor?.wildMeColors?.cyan700}
                    borderColor={themeColor?.wildMeColors?.cyan700}
                >
                    Cancel
                </MainButton>
            </div>
        </div>)
}
);
