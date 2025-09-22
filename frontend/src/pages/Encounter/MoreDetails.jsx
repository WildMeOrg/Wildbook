
import React from 'react';
import { observer } from 'mobx-react-lite';
import { Col, Container } from 'react-bootstrap';
import CardWithEditButton from '../../components/CardWithEditButton';
import TrackingIcon from '../../components/icons/TrackingIcon';
import MeasurementsIcon from '../../components/icons/MeasurementsIcon';
import { Row } from 'react-bootstrap';
import ThemeColorContext from '../../ThemeColorProvider';
import CardWithSaveAndCancelButtons from '../../components/CardWithSaveAndCancelButtons';
import ProjectsIcon from '../../components/icons/ProjectsIcon';

export const MoreDetails = observer(({
    store = {}
}) => {

    return (
        <Container style={{ padding: 0 }}>
            <Row>
                <Col md={3} sm={12} className="mt-3">
                    <div className="d-flex align-items-center justify-content-between mb-3 w-100"
                        onClick={() => {
                            store.setMeasurementsAndTrackingSection(true)
                            store.setBiologicalSamplesSection(false)
                            store.setProjectsSection(false)
                        }}
                        style={{ cursor: 'pointer', color: !store.measurementsAndTrackingSection ? 'black' : 'blue' }}
                    >
                        <p>Measurements & Tracking</p>
                        <i class="bi bi-chevron-right"></i>
                    </div>
                    <div className="d-flex align-items-center justify-content-between mb-3 w-100"
                        onClick={() => {
                            store.setBiologicalSamplesSection(true)
                            store.setMeasurementsAndTrackingSection(false)
                            store.setProjectsSection(false)
                        }}
                        style={{ cursor: 'pointer', color: !store.biologicalSamplesSection ? 'black' : 'blue' }}
                    >
                        <p>Biological Samples</p>
                        <i class="bi bi-chevron-right"></i>
                    </div>
                    <div className="d-flex align-items-center justify-content-between mb-3 w-100"
                        onClick={() => {
                            store.setProjectsSection(true)
                            store.setMeasurementsAndTrackingSection(false)
                            store.setBiologicalSamplesSection(false)
                        }}
                        style={{ cursor: 'pointer', color: !store.projectsSection ? 'black' : 'blue' }}
                    >
                        <p>Projects</p>
                        <i class="bi bi-chevron-right"></i>
                    </div>
                </Col>
                {store.measurementsAndTrackingSection &&
                    <Col md={9} sm={12} className="d-flex flex-row gap-3">
                        {!store.editTracking ?
                            <CardWithEditButton
                                icon={<TrackingIcon />}
                                title="Tracking"
                                content={
                                    <div>
                                        <p>Metal Tags</p>
                                        <p>Left: {store.encounterData?.metalTags[0]["left"]?.number}</p>
                                        <p>Right: {store.encounterData?.metalTags[0]["right"]?.number}</p>
                                        <div style={{
                                            width: '100%',
                                            height: "10px",
                                            borderBottom: '1px solid #ccc',
                                        }}></div>
                                        <p>Acoustic Tags</p>
                                        <p>Serial Number: {store.encounterData?.acousticTag?.serialNumber}</p>
                                        <p>ID: {store.encounterData?.acousticTag?.idNumber}</p>
                                        <div style={{
                                            width: '100%',
                                            height: "10px",
                                            borderBottom: '1px solid #ccc',
                                        }}></div>
                                        <p>Satellite Tags</p>
                                        <p>Name: {store.encounterData?.satelliteTag?.name}</p>
                                        <p>ID: {store.encounterData?.satelliteTag?.serialNumber}</p>
                                        <p>Argos PTT: {store.encounterData?.satelliteTag?.argosPttNumber}</p>

                                        <div style={{
                                            width: '100%',
                                            height: "10px",
                                            borderBottom: '1px solid #ccc',
                                        }}></div>
                                    </div>}
                            /> :
                            <CardWithSaveAndCancelButtons
                                icon={<TrackingIcon />}
                                title="Traching"
                                content={
                                    <div>
                                        <p>Acoustic Tags</p>
                                        <p>Serial Number: {store.encounterData?.acousticTag?.serialNumber}</p>
                                        <p>ID: {store.encounterData?.acousticTag?.idNumber}</p>
                                        <div style={{
                                            width: '100%',
                                            height: "10px",
                                            borderBottom: '1px solid #ccc',
                                        }}></div>
                                    </div>}

                            />}
                        {store.editMeasurements ?
                            <CardWithEditButton
                                icon={<MeasurementsIcon />}
                                title="Measurements"
                                content={<div>
                                    <div>Measurements: {store.measurements}</div>
                                    <div>Tracking: {store.tracking}</div>
                                </div>
                                }
                            /> :
                            <CardWithSaveAndCancelButtons
                                icon={<MeasurementsIcon />}
                                title="Measurements"
                                content={
                                    <div>
                                        <div>Measurements: {store.measurements}</div>
                                        <div>Tracking: {store.tracking}</div>
                                    </div>
                                }
                            />}
                    </Col>
                }
                {store.biologicalSamplesSection &&
                    <Col md={9} sm={12} >
                        {
                            !store.editBiologicalSamples ?
                                <CardWithEditButton
                                    icon={<ThemeColorContext.Consumer>
                                        {({ themeColor }) => <i className="bi bi-flask" style={{ color: themeColor }} />}
                                    </ThemeColorContext.Consumer>}
                                    title="Biological Samples"
                                    content={
                                        <div>
                                            <div>Samples: {store.biologicalSamples}</div>
                                        </div>
                                    }
                                /> :
                                <CardWithSaveAndCancelButtons
                                    icon={<ThemeColorContext.Consumer>
                                        {({ themeColor }) => <i className="bi bi-flask" style={{ color: themeColor }} />}
                                    </ThemeColorContext.Consumer>}
                                    title="Biological Samples"
                                    content={
                                        <div>
                                            <div>Samples: {store.biologicalSamples}</div>
                                        </div>
                                    }
                                />
                        }
                    </Col>
                }
                {store.projectsSection &&
                    <Col md={9} sm={12}>
                        {!store.editProjects ?
                            <CardWithEditButton
                                icon={<ProjectsIcon />}
                                title="Projects"
                                content={
                                    <div>
                                        <div>Projects: {store.projects}</div>
                                    </div>
                                }
                            /> :
                            <CardWithSaveAndCancelButtons
                                icon={<ProjectsIcon />}
                                title="Projects"
                                content={() => {
                                    return (
                                        <div>
                                            <div>Projects: {store.projects}</div>
                                        </div>
                                    );
                                }}
                            />}
                    </Col>
                }

            </Row>
        </Container>
    );
})