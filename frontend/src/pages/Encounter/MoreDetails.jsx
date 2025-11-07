
import React from 'react';
import { observer } from 'mobx-react-lite';
import { Col, Container } from 'react-bootstrap';
import CardWithEditButton from '../../components/CardWithEditButton';
import TrackingIcon from '../../components/icons/TrackingIcon';
import MeasurementsIcon from '../../components/icons/MeasurementsIcon';
import { Row } from 'react-bootstrap';
import CardWithSaveAndCancelButtons from '../../components/CardWithSaveAndCancelButtons';
import { TrackingReview } from './TrackingReview';
import { TrackingEdit } from './TrackingEdit';
import { ProjectsCard } from './ProjectsCard';
import { MeasurementsEdit } from './MeasurementsEdit';
import { MeasurementsReview } from './MeasurementsReview';
import { FormattedMessage } from 'react-intl';

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
                        <p><FormattedMessage id="TRACKING_AND_MEASUREMENTS" /></p>
                        <i class="bi bi-chevron-right"></i>
                    </div>
                    <div className="d-flex align-items-center justify-content-between mb-3 w-100"
                        onClick={() => {
                            store.setBiologicalSamplesSection(true)
                            store.setMeasurementsAndTrackingSection(false)
                            store.setProjectsSection(false)
                            window.location.href = '/encounters/biologicalSamples.jsp?number=' + store.encounterData?.id;
                        }}
                        style={{ cursor: 'pointer', color: !store.biologicalSamplesSection ? 'black' : 'blue' }}
                    >
                        <p><FormattedMessage id="BIOLOGICAL_SAMPLES" /></p>
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
                        <p><FormattedMessage id="PROJECTS" /></p>
                        <i class="bi bi-chevron-right"></i>
                    </div>
                </Col>
                {store.measurementsAndTrackingSection &&
                    <Col md={9} sm={12} className="d-flex flex-row gap-3">
                        {!store.editTracking ?
                            <CardWithEditButton
                                icon={<TrackingIcon />}
                                title="TRACKING"
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
                                title="TRACKING"
                                content={
                                    <TrackingEdit
                                        store={store}
                                    />}
                                onSave={() => {
                                    store.patchTracking();
                                    store.setEditTracking(false);
                                }}
                                onCancel={() => {
                                    store.setEditTracking(false);
                                }}

                            />}
                        {!store.editMeasurements ?
                            <CardWithEditButton
                                icon={<MeasurementsIcon />}
                                title="MEASUREMENTS"
                                content={
                                    <MeasurementsReview
                                        store={store}
                                    />
                                }
                                onClick={() => {
                                    store.setEditMeasurements(true);
                                }}
                            /> :
                            <CardWithSaveAndCancelButtons
                                icon={<MeasurementsIcon />}
                                title="MEASUREMENTS"
                                content={
                                    <MeasurementsEdit
                                        store={store}
                                    />}
                                onCancel={() => {
                                    store.setEditMeasurements(false);
                                    store.resetMeasurementValues();
                                }}
                                onSave={() => {
                                    store.patchMeasurements();
                                    store.refreshEncounterData();
                                    store.setEditMeasurements(false);
                                    store.resetMeasurementValues();
                                }}
                                disabled={
                                    store.measurementValues?.length === 0 ||
                                    store.errors.getFieldError('measurement')?.length > 0}
                            />}
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