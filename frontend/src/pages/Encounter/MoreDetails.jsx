
import React from 'react';
import { observer } from 'mobx-react-lite';
import { Col, Container } from 'react-bootstrap';
import CardWithEditButton from '../../components/CardWithEditButton';
import TrackingIcon from '../../components/icons/TrackingIcon';
import MeasurementsIcon from '../../components/icons/MeasurementsIcon';
import { Row } from 'react-bootstrap';
import ThemeColorContext from '../../ThemeColorProvider';
import CardWithSaveAndCancelButtons from '../../components/CardWithSaveAndCancelButtons';
import { TrackingReview } from './TrackingReview';
import { TrackingEdit } from './TrackingEdit';
import { ProjectsCard } from './ProjectsCard';

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
                            console.log('Biological Samples Section Clicked');
                            // Redirect to the biological samples page
                            window.location.href = '/encounters/biologicalSamples.jsp?number=' + store.encounterData?.id;
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
                                content={<TrackingReview 
                                    store={store}
                                />
                                }
                                onClick={() => {
                                    store.setEditTracking(true);
                                }}
                            /> :
                            <CardWithSaveAndCancelButtons
                                icon={<TrackingIcon />}
                                title="Tracking"
                                content={
                                    <TrackingEdit 
                                        store={store}
                                    />}
                                onSave ={() => {
                                    store.patchTracking();
                                    store.setEditTracking(false);
                                }}
                                onCancel ={() => {
                                    store.setEditTracking(false);
                                }}                           

                            />}
                        {!store.editMeasurements ?
                            <CardWithEditButton
                                icon={<MeasurementsIcon />}
                                title="Measurements"
                                content={<div>
                                    <div>Measurements: {store.measurements}</div>
                                    <div>Tracking: {store.tracking}</div>
                                </div>
                                }
                                onClick={() => {
                                    store.setEditMeasurements(true);
                                }}
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
                                    onClick={() => {
                                        store.setEditBiologicalSamples(true);
                                    }}
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
                            <ProjectsCard
                                store={store}
                            />
                    </Col>
                }
            </Row>
        </Container>
    );
});