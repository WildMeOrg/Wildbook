import React, { useState } from "react";
import { FormattedMessage } from "react-intl";
import Description from "../Form/Description";
import { FormGroup, FormLabel, FormControl, Button } from "react-bootstrap";

export default function DateFilter({
    onChange,
}) {
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');
    const [submissionStartDate, setSubmissionStartDate] = useState('');
    const [submissionEndDate, setSubmissionEndDate] = useState('');
    const [verbatimDate, setVerbatimDate] = useState('');

    const updateQuery1 = () => {

        if (startDate || endDate) {
            const query = {
                range: {
                    sightingDate: {}
                }
            };

            if (startDate) {
                query.range.sightingDate.gte = startDate + "T00:00:00Z";
            }

            if (endDate) {
                query.range.sightingDate.lte = endDate + "T23:59:59Z";
            }
            onChange({
                filterId: "date",
                clause: "filter",
                query: query
            }
            )
        }

    }

    const updateQuery2 = () => {
        if (submissionStartDate || submissionEndDate) {
            const query = {
                range: {
                    submissionDate: {}
                }
            };

            if (submissionStartDate) {
                query.range.submissionDate.gte = submissionStartDate + "T00:00:00Z";
            }

            if (submissionEndDate) {
                query.range.submissionDate.lte = submissionEndDate + "T23:59:59Z";
            }
            onChange({
                filterId: "dateSubmitted",
                clause: "filter",
                query: query
            }
            )
        }
    }

    return (
        <div>
            <h3><FormattedMessage id="DATE" /></h3>
            <Description>
                <FormattedMessage id="DATE_DESC" />
            </Description>
            <>
                <FormLabel><FormattedMessage id="SIGHTING_DATES" /></FormLabel>
                <div className="d-flex flex-row w-100 mb-2">
                    <FormGroup className="w-50" style={{ marginRight: '10px' }}>
                        <p>
                            <FormattedMessage id="FROM" defaultMessage="From" />
                        </p>
                        <FormControl type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
                    </FormGroup>
                    <FormGroup className="w-50">
                        <p>
                            <FormattedMessage id="TO" defaultMessage="To" />
                        </p>
                        <FormControl type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
                    </FormGroup>
                </div>
            </>

            <Button onClick={updateQuery1} variant="primary">
                <FormattedMessage id="CONFIRM" />
            </Button>
            <><p><FormLabel class="mt-3"><FormattedMessage id="ENCOUNTER_SUBMISSION_DATES" /></FormLabel></p>
                
                <div className="d-flex flex-row w-100 mb-2">
                
                    <FormGroup className="w-50" style={{ marginRight: '10px' }}>
                        <p>
                            <FormattedMessage id="FROM" defaultMessage="From" />
                        </p>
                        <FormControl type="date" value={submissionStartDate} onChange={(e) => setSubmissionStartDate(e.target.value)} />
                    </FormGroup>
                    <FormGroup className="w-50">
                        <p>
                            <FormattedMessage id="TO" defaultMessage="To" />
                        </p>
                        <FormControl type="date" value={submissionEndDate} onChange={(e) => setSubmissionEndDate(e.target.value)} />
                    </FormGroup>
                </div>
            </>

            <Button onClick={updateQuery2} variant="primary">
                <FormattedMessage id="CONFIRM" />
            </Button>
        </div>
    );
}
